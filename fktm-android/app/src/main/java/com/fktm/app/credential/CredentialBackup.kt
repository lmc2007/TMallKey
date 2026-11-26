package com.fktm.app.credential

import org.json.JSONException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable backup of the lock list. The device's Keystore key never leaves the device. */
internal object CredentialBackup {
    const val MIN_PASSWORD_LENGTH = 12
    const val MAX_FILE_BYTES = 1_048_576

    // V1: eight magic bytes, one version byte, 16-byte salt, 12-byte nonce, ciphertext + tag.
    // The entire header is authenticated. V1 fixes the KDF cost so a file cannot supply it.
    private val magic = "TMKEYBAK".toByteArray(Charsets.US_ASCII)
    private const val VERSION = 1
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BYTES = 16
    private const val PBKDF2_ITERATIONS = 600_000
    private val saltOffset = magic.size + 1
    private val nonceOffset = saltOffset + SALT_BYTES
    private val headerBytes = nonceOffset + NONCE_BYTES
    private val random = SecureRandom()
    private val mac = Regex("(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}")
    private val vocKey = Regex("[0-9a-fA-F]{32}")

    sealed class Error : Exception()
    class InvalidFile : Error()
    class UnsupportedVersion : Error()
    class InvalidCredentials : Error()
    class AuthenticationFailed : Error()

    fun encode(locks: List<LockCredential>, password: CharArray): ByteArray {
        require(password.size >= MIN_PASSWORD_LENGTH)
        validate(locks)
        val plain = LockCredentialJson.encode(locks).toByteArray(Charsets.UTF_8)
        try {
            if (plain.size > MAX_FILE_BYTES - headerBytes - TAG_BYTES) throw InvalidFile()
            val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
            val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
            val header = magic + byteArrayOf(VERSION.toByte()) + salt + nonce
            return header + cipher(Cipher.ENCRYPT_MODE, password, salt, nonce, header).doFinal(plain)
        } finally {
            plain.fill(0)
        }
    }

    fun decode(file: ByteArray, password: CharArray): List<LockCredential> {
        if (file.size !in (magic.size + 1)..MAX_FILE_BYTES ||
            !file.copyOfRange(0, magic.size).contentEquals(magic)
        ) throw InvalidFile()
        if (file[magic.size] != VERSION.toByte()) throw UnsupportedVersion()
        if (file.size <= headerBytes + TAG_BYTES) throw InvalidFile()
        val cipher = cipher(
            Cipher.DECRYPT_MODE,
            password,
            file.copyOfRange(saltOffset, nonceOffset),
            file.copyOfRange(nonceOffset, headerBytes),
            file.copyOfRange(0, headerBytes),
        )
        val plain = try {
            cipher.doFinal(file, headerBytes, file.size - headerBytes)
        } catch (_: AEADBadTagException) {
            throw AuthenticationFailed()
        }
        return try {
            LockCredentialJson.decode(plain.toString(Charsets.UTF_8)).also(::validate)
        } catch (_: JSONException) {
            throw InvalidCredentials()
        } finally {
            plain.fill(0)
        }
    }

    fun read(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return out.toByteArray()
            if (out.size() + count > MAX_FILE_BYTES) throw InvalidFile()
            out.write(buffer, 0, count)
        }
    }

    /** Prefer local records on conflicts and leave the first lock (the primary) in place. */
    fun merge(existing: List<LockCredential>, imported: List<LockCredential>): List<LockCredential> {
        val seen = existing.mapTo(HashSet()) { it.address.uppercase(Locale.ROOT) }
        return existing + imported.filter { seen.add(it.address.uppercase(Locale.ROOT)) }
    }

    private fun validate(locks: List<LockCredential>) {
        if (locks.isEmpty()) throw InvalidCredentials()
        for (lock in locks) {
            val validKey = when (lock.type) {
                LockCredential.TYPE_VOC -> vocKey.matches(lock.activationKey)
                LockCredential.TYPE_MV -> lock.activationKey.isEmpty()
                else -> false
            }
            if (!mac.matches(lock.address) || !validKey) throw InvalidCredentials()
        }
    }

    private fun cipher(mode: Int, password: CharArray, salt: ByteArray, nonce: ByteArray, header: ByteArray): Cipher {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, 256)
        val key = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
                updateAAD(header)
            }
        } finally {
            key.fill(0)
        }
    }
}
