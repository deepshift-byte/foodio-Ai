package com.sandpause.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2-HMAC-SHA256 hashing for the parent PIN and the recovery answer.
 *
 * Stored format: `pbkdf2_sha256$<iterations>$<saltBase64>$<hashBase64>`.
 * The PIN is never stored in plain text.
 */
object PinHasher {
    const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val PREFIX = "pbkdf2_sha256"
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    private val random = SecureRandom()

    fun hash(secret: String, iterations: Int = ITERATIONS, salt: ByteArray = newSalt()): String {
        val derived = derive(secret, salt, iterations)
        val enc = Base64.getEncoder()
        return listOf(PREFIX, iterations.toString(), enc.encodeToString(salt), enc.encodeToString(derived))
            .joinToString("$")
    }

    /** Constant-time check of [secret] against a value produced by [hash]. */
    fun verify(secret: String, stored: String?): Boolean {
        if (stored.isNullOrBlank()) return false
        val parts = stored.split("$")
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        if (iterations < 1_000) return false
        val dec = Base64.getDecoder()
        val salt = runCatching { dec.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { dec.decode(parts[3]) }.getOrNull() ?: return false
        val actual = derive(secret, salt, iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { random.nextBytes(it) }

    private fun derive(secret: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
