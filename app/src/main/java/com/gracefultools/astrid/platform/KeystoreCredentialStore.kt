package com.gracefultools.astrid.platform

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Log
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import com.gracefultools.astrid.core.CoreJson
import uniffi.astrid_android.CredentialStore
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Seals bytes for this device and this app only. A seam so the store is tested without a keystore. */
interface Sealer {
    fun seal(plain: ByteArray): ByteArray
    /** `null` when the bytes cannot be opened — another key, or a truncated file. */
    fun open(sealed: ByteArray): ByteArray?
}

/**
 * The session credential at rest — Android's Keychain (ASTRID.md §2).
 *
 * Every key lives in one sealed file: the session `Cookie` header and the user id the core keeps
 * beside it. Read-modify-write is serialised, so two writes cannot lose one of them — and the one
 * most likely to be lost would be the cookie, rewritten on every renewal.
 *
 * Unreadable means signed out, not a crash: a file sealed by a key the keystore no longer has (a
 * restored backup, a cleared lock screen) starts the person at sign-in, which they can recover from.
 */
class KeystoreCredentialStore(
    private val file: File,
    private val sealer: Sealer = KeystoreSealer(),
) : CredentialStore {

    private val lock = Any()
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    override fun get(key: String): String? = synchronized(lock) { readAll()[key] }

    override fun set(key: String, value: String): Boolean = synchronized(lock) {
        writeAll(readAll() + (key to value))
    }

    override fun delete(key: String): Boolean = synchronized(lock) {
        val all = readAll()
        if (key !in all) true else writeAll(all - key)
    }

    private fun readAll(): Map<String, String> {
        if (!file.exists()) return emptyMap()
        val plain = runCatching { sealer.open(AtomicFile(file).readFully()) }.getOrNull()
        if (plain == null) {
            Log.w(TAG, "the stored credential could not be opened; starting signed out")
            return emptyMap()
        }
        return runCatching { CoreJson.decodeFromString(serializer, plain.decodeToString()) }.getOrDefault(emptyMap())
    }

    private fun writeAll(entries: Map<String, String>): Boolean {
        val atomic = AtomicFile(file)
        val sealed = runCatching { sealer.seal(CoreJson.encodeToString(serializer, entries).encodeToByteArray()) }
            .getOrElse { return false }
        file.parentFile?.mkdirs()
        val stream = runCatching { atomic.startWrite() }.getOrElse { return false }
        return try {
            stream.write(sealed)
            atomic.finishWrite(stream)
            true
        } catch (error: java.io.IOException) {
            atomic.failWrite(stream)
            false
        }
    }

    private companion object { const val TAG = "AstridCredentials" }
}

/** AES-256-GCM with a key that never leaves AndroidKeyStore. The 12-byte IV leads the ciphertext. */
class KeystoreSealer(private val alias: String = "astrid.session") : Sealer {

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun open(sealed: ByteArray): ByteArray? {
        if (sealed.size <= IV_BYTES) return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_BYTES))
            cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
        }.getOrNull()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
