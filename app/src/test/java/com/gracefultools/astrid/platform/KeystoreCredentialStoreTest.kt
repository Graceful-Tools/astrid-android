package com.gracefultools.astrid.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A reversible stand-in for the keystore, so the store's own logic is what is tested (AATD-5). */
private class XorSealer(private val key: Byte = 0x5A) : Sealer {
    override fun seal(plain: ByteArray) = byteArrayOf(1) + plain.map { (it.toInt() xor key.toInt()).toByte() }
    override fun open(sealed: ByteArray) =
        if (sealed.firstOrNull() != 1.toByte()) null else sealed.drop(1).map { (it.toInt() xor key.toInt()).toByte() }.toByteArray()
}

// A plain Application: the real one starts the native core, which a JVM test has no business doing.
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class KeystoreCredentialStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private fun store(sealer: Sealer = XorSealer()) = KeystoreCredentialStore(folder.root.resolve("creds.bin"), sealer)

    @Test
    fun `AATD-5 a written credential reads back after a restart`() {
        assertTrue(store().set("astrid.session-cookie", "next-auth.session-token=abc"))
        assertEquals("next-auth.session-token=abc", store().get("astrid.session-cookie"))
    }

    @Test
    fun `AATD-5 writing one key keeps the others`() {
        val store = store()
        store.set("astrid.session-cookie", "c")
        store.set("astrid.current-user-id", "u1")
        assertEquals("c", store.get("astrid.session-cookie"))
        assertEquals("u1", store.get("astrid.current-user-id"))
    }

    @Test
    fun `AATD-5 deleting removes only that key, and deleting nothing succeeds`() {
        val store = store()
        store.set("a", "1"); store.set("b", "2")
        assertTrue(store.delete("a"))
        assertNull(store.get("a"))
        assertEquals("2", store.get("b"))
        assertTrue(store.delete("never-there"))
    }

    @Test
    fun `AATD-5 the file on disk is sealed, not the plain cookie`() {
        store().set("astrid.session-cookie", "next-auth.session-token=secret")
        val bytes = folder.root.resolve("creds.bin").readBytes().decodeToString()
        assertFalse(bytes.contains("secret"))
    }

    @Test
    fun `AATD-5 a file another key sealed reads as signed out, not a crash`() {
        store().set("astrid.session-cookie", "c")
        val foreign = object : Sealer {
            override fun seal(plain: ByteArray) = plain
            override fun open(sealed: ByteArray): ByteArray? = null
        }
        assertNull(store(foreign).get("astrid.session-cookie"))
    }
}
