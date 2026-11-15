package com.gracefultools.astrid

import androidx.test.platform.app.InstrumentationRegistry
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.CoreSession
import java.net.InetAddress
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import uniffi.astrid_uniffi.CredentialStore

/**
 * AATD-100: the core checks certificates with the DEVICE's trust store, through
 * rustls-platform-verifier and Android's trust manager, not with a Mozilla bundle compiled in.
 *
 * A local TLS server presents a `localhost` certificate issued by a throwaway root
 * (`androidTest/assets/aatd100`). Whether the server READ an HTTP request tells us whether the
 * core's TLS handshake succeeded. The server's 401 response doesn't matter.
 *
 * - Without that root on the device, the request must never arrive.
 * - With it installed as a user CA (`scripts/install-test-ca.sh`, which needs a rootable
 *   emulator), the request must arrive, as iOS trusts a profile root the user has enabled. That
 *   test skips when the root isn't installed.
 * - astrid.cc must be reached. Its certificate has no OCSP URL, so this also proves the
 *   revocation check can fetch the CRL (the verifier's CRL hosts, app/tls).
 */
class PlatformTlsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var server: SSLServerSocket
    private val connected = AtomicBoolean(false)
    private val requestArrived = AtomicBoolean(false)

    @Before fun serve() {
        // The app's own start is what initialises TLS, so wait for it as the activity does.
        val app = instrumentation.targetContext.applicationContext as AstridApplication
        runBlocking { withTimeout(30_000) { app.awaitGraph() } }

        val keys = KeyStore.getInstance("PKCS12").apply {
            instrumentation.context.assets.open("aatd100/server.p12").use { load(it, PASSWORD) }
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys, PASSWORD) }
        val tls = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
        server = tls.serverSocketFactory.createServerSocket(0, 8, InetAddress.getByName("127.0.0.1")) as SSLServerSocket
        thread(isDaemon = true, name = "aatd100-tls-server") {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                connected.set(true)
                thread(isDaemon = true) {
                    socket.use {
                        runCatching {
                            val line = it.getInputStream().bufferedReader().readLine()
                            if (line != null && line.contains("HTTP/")) requestArrived.set(true)
                            it.getOutputStream().write(
                                "HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(),
                            )
                            it.getOutputStream().flush()
                        }
                    }
                }
            }
        }
    }

    @After fun close() { server.close() }

    /** One request from a fresh core, signed in with a stand-in cookie; the core's failure, if any. */
    private fun request(baseUrl: String): Throwable? {
        val credentials = object : CredentialStore {
            val map = hashMapOf("astrid.session-cookie" to "next-auth.session-token=aatd-100", "astrid.current-user-id" to "u-me")
            override fun get(key: String) = map[key]
            override fun set(key: String, value: String): Boolean { map[key] = value; return true }
            override fun delete(key: String): Boolean { map.remove(key); return true }
        }
        val core = CoreSession.start(":memory:", credentials, background = false, baseUrl = baseUrl)
        return runBlocking {
            runCatching { withTimeout(20_000) { core.run(CoreJson.parseToJsonElement("""{"kind":"refreshSettings"}""").jsonObject) } }
        }.exceptionOrNull().also { core.stop() }
    }

    // `localhost` rather than the address, so the name is checked against the certificate.
    private fun requestTheLocalServer(): Throwable? = request("https://localhost:${server.localPort}")

    /**
     * The everyday case. Before the init, every request fails offline (`TlsNotInitialized`), so
     * nothing ever syncs. Once it runs, astrid.cc's public certificate is verified and the server
     * answers. With a made-up cookie that answer is a refusal, but it is not "offline".
     */
    @Test fun aServerTheSystemTrustsIsReached_AATD_100() {
        val failure = request(TestAstridApplication.LIVE_BASE_URL)
        assertTrue(
            "the core could not reach astrid.cc over TLS: $failure",
            failure == null || (failure is com.gracefultools.astrid.core.CoreFailure && !failure.isStillPending),
        )
    }

    @Test fun aRootTheDeviceDoesNotTrustIsRefused_AATD_100() {
        assumeTrue("the test root is installed on this device; this case needs a device without it", !testRootInstalled())
        val failure = requestTheLocalServer()
        // Reaching the server at all is what makes the refusal mean something.
        assertTrue("the core never connected to the local server: $failure", connected.get())
        assertFalse("the core finished a TLS handshake with a server whose root the device does not trust", requestArrived.get())
    }

    @Test fun aUserInstalledRootIsHonoured_AATD_100() {
        assumeTrue("the test root isn't installed: run scripts/install-test-ca.sh on a rootable emulator", testRootInstalled())
        val failure = requestTheLocalServer()
        assertTrue("the core refused a server whose root the user installed on the device: $failure", requestArrived.get())
    }

    /** Whether the device's own trust store (system plus user CAs) holds the throwaway root. */
    private fun testRootInstalled(): Boolean {
        val root = instrumentation.context.assets.open("aatd100/ca.pem").use {
            java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(it)
        }
        val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        return store.getCertificateAlias(root) != null
    }

    private companion object {
        val PASSWORD = "aatd100".toCharArray()
    }
}
