package com.gracefultools.astrid.core

import android.content.Context

/**
 * The core's TLS init (AATD-100). On Android the core checks certificates with
 * `rustls-platform-verifier`, which asks Android's own trust manager to check against the device's
 * trust store (`AndroidCAStore`: system roots the user hasn't disabled, plus user-installed ones),
 * not a root bundle compiled into the library. Its revocation check fetches over the app's own
 * networking, so the app's network-security config must allow it (app/tls). The verifier reaches
 * the JVM through the [Context] handed over here.
 *
 * Call [initialize] once, before the first request. Until then every request fails offline with the
 * core's `TlsNotInitialized`. A second call is harmless.
 *
 * A raw JNI function in the same library JNA loads (core's `crates/astrid-uniffi/src/lib.rs`), because it
 * needs the caller's `JNIEnv`, which UniFFI over JNA can't pass. The verifier's own Kotlin half is
 * the `org.rustls:rustls-platform-verifier` artifact (corebridge/build.gradle.kts).
 */
object PlatformTls {
    init { System.loadLibrary("astrid_uniffi") }

    /**
     * Hand the verifier [context], which it keeps for the life of the process, so pass the
     * application context, never an activity. Throws a `RuntimeException` if the verifier refuses it.
     */
    @JvmStatic external fun initialize(context: Context)

    /** Whether the core can verify a certificate yet. */
    val isReady: Boolean get() = uniffi.astrid_uniffi.platformTlsReady()
}
