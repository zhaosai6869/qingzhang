package com.qingxi.ledger.util

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Crypto {

    private const val AES = "AES"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private const val SALT_SIZE = 12
    private const val PBKDF2_ROUNDS = 210_000
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_BITS = 256
    private const val STREAM_MAGIC = "QJBAK1"

    private val random = SecureRandom()

    fun encryptStream(password: String, source: ByteArray): ByteArray {
        val salt = ByteArray(SALT_SIZE).also { random.nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        val body = cipher.doFinal(source)
        val magic = STREAM_MAGIC.encodeToByteArray()
        val payload = ByteArray(magic.size + SALT_SIZE + IV_SIZE + body.size)
        magic.copyInto(payload, 0)
        salt.copyInto(payload, magic.size)
        iv.copyInto(payload, magic.size + SALT_SIZE)
        body.copyInto(payload, magic.size + SALT_SIZE + IV_SIZE)
        return payload
    }

    fun decryptStream(password: String, payload: ByteArray): ByteArray? = try {
        val magic = STREAM_MAGIC.encodeToByteArray()
        if (payload.size <= magic.size + SALT_SIZE + IV_SIZE) return null
        if (!payload.copyOfRange(0, magic.size).contentEquals(magic)) return null
        val salt = payload.copyOfRange(magic.size, magic.size + SALT_SIZE)
        val iv = payload.copyOfRange(magic.size + SALT_SIZE, magic.size + SALT_SIZE + IV_SIZE)
        val body = payload.copyOfRange(magic.size + SALT_SIZE + IV_SIZE, payload.size)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.doFinal(body)
    } catch (t: Throwable) {
        null
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ROUNDS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        return SecretKeySpec(factory.generateSecret(spec).encoded, AES)
    }
}
