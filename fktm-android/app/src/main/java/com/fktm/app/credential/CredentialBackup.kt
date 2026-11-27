package com.fktm.app.credential

import org.json.JSONException
import org.json.JSONObject
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

    // Eight magic bytes, one version byte, 16-byte salt, 12-byte nonce, ciphertext + tag.
    // Both versions authenticate the header and use a fixed KDF cost. V1 is a lock array;
    // V2 wraps the lock array and campus session in an object.
    private val magic = "TMKEYBAK".toByteArray(Charsets.US_ASCII)
    private const val LOCKS_VERSION: Byte = 1
    private const val SESSION_VERSION: Byte = 2
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

    class Contents(val locks: List<LockCredential>, val campusSession: Map<String, String>? = null)

    fun encode(
        locks: List<LockCredential>,
        password: CharArray,
        campusSession: Map<String, String>? = null,
    ): ByteArray {
        require(password.size >= MIN_PASSWORD_LENGTH)
        validate(Contents(locks, campusSession))
        val version = if (campusSession == null) LOCKS_VERSION else SESSION_VERSION
        val json = if (campusSession == null) LockCredentialJson.encode(locks) else JSONObject()
            .put("locks", LockCredentialJson.toArray(locks))
            .put("campusSession", JSONObject(campusSession))
            .toString()
        val plain = json.toByteArray(Charsets.UTF_8)
        try {
            if (plain.size > MAX_FILE_BYTES - headerBytes - TAG_BYTES) throw InvalidFile()
            val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
            val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
            val header = magic + byteArrayOf(version) + salt + nonce
            return header + cipher(Cipher.ENCRYPT_MODE, password, salt, nonce, header).doFinal(plain)
        } finally {
            plain.fill(0)
        }
    }

    fun decode(file: ByteArray, password: CharArray): Contents {
        if (file.size !in (magic.size + 1)..MAX_FILE_BYTES ||
            !file.copyOfRange(0, magic.size).contentEquals(magic)
        ) throw InvalidFile()
        val version = file[magic.size]
        if (version != LOCKS_VERSION && version != SESSION_VERSION) throw UnsupportedVersion()
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
            val json = plain.toString(Charsets.UTF_8)
            val contents = if (version == LOCKS_VERSION) Contents(LockCredentialJson.decode(json)) else {
                val root = JSONObject(json)
                val session = root.getJSONObject("campusSession")
                val cookies = linkedMapOf<String, String>()
                session.keys().forEach { name ->
                    cookies[name] = session.get(name) as? String ?: throw InvalidCredentials()
                }
                Contents(LockCredentialJson.decode(root.getJSONArray("locks")), cookies)
            }
            contents.also(::validate)
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

    private fun validate(contents: Contents) {
        val session = contents.campusSession
        if (contents.locks.isEmpty() && session == null) throw InvalidCredentials()
        if (session != null && session["cookie2"].isNullOrBlank()) throw InvalidCredentials()
        for (lock in contents.locks) {
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
