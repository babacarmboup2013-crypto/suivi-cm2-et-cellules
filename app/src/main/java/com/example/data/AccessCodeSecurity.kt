package com.example.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class HashedAccessCode(
    val hashHex: String,
    val saltHex: String
)

data class GeneratedSchoolCode(
    val schoolCode: String,
    val schoolName: String,
    val accessCode: String
)

/**
 * Cryptographic helper for per-school confidential access codes.
 * Stores only salted PBKDF2WithHmacSHA256 digests in Firestore (never plaintext codes).
 */
object AccessCodeSecurity {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    // Unambiguous uppercase characters (excludes 0/O and 1/I/L to avoid reading errors)
    private const val CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    private val secureRandom = SecureRandom()

    fun normalizeCode(raw: String): String = raw.trim().uppercase()

    fun isValidFormat(raw: String): Boolean {
        val clean = normalizeCode(raw)
        return clean.length in 4..16 && clean.all { it.isLetterOrDigit() }
    }

    fun generateAccessCode(length: Int = 6): String {
        val sb = StringBuilder(length)
        repeat(length) {
            sb.append(CODE_ALPHABET[secureRandom.nextInt(CODE_ALPHABET.length)])
        }
        return sb.toString()
    }

    fun hashAccessCode(rawCode: String): HashedAccessCode {
        val clean = normalizeCode(rawCode)
        require(isValidFormat(clean)) {
            "Le code d'accès doit contenir entre 4 et 16 caractères alphanumériques."
        }
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        val hash = pbkdf2(clean.toCharArray(), salt)
        return HashedAccessCode(
            hashHex = hash.toHexString(),
            saltHex = salt.toHexString()
        )
    }

    fun verifyAccessCode(rawInput: String, storedHashHex: String, storedSaltHex: String): Boolean {
        val clean = normalizeCode(rawInput)
        if (clean.isEmpty() || storedHashHex.isBlank() || storedSaltHex.isBlank()) return false
        val salt = storedSaltHex.fromHexStringOrNull() ?: return false
        val expected = storedHashHex.fromHexStringOrNull() ?: return false
        val actual = pbkdf2(clean.toCharArray(), salt)
        return MessageDigest.isEqual(actual, expected)
    }

    private fun pbkdf2(chars: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(chars, salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHexStringOrNull(): ByteArray? {
        if (length % 2 != 0) return null
        return try {
            ByteArray(length / 2) { idx ->
                substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
