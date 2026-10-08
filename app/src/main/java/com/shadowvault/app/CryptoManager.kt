package com.shadowvault.app

import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM с ключом в Android Keystore.
 * Ключ не покидает TEE/StrongBox — извлечь его невозможно даже с root
 * (на устройствах с StrongBox/TEE).
 */
object CryptoManager {

    private const val KEY_ALIAS = "vault_master_key_v1"
    private const val KEY_STORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_SIZE = 12 // 96 бит, рекомендация NIST для GCM

    private fun getKey(): SecretKey {
        val ks = KeyStore.getInstance(KEY_STORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val gen = KeyGenerator.getInstance("AES", KEY_STORE)
        gen.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                // Без привязки к биометрии: жест — единственный фактор.
                // Если нужен биометрический фактор — добавить setUserAuthenticationRequired(true)
                .build()
        )
        return gen.generateKey()
    }

    /** Возвращает IV (12 байт) + шифртекст (+16 байт GCM-Tag). */
    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain)
        return iv + ct
    }

    fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > IV_SIZE) { "corrupt blob" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(GCM_TAG_BITS, blob, 0, IV_SIZE))
        return cipher.doFinal(blob, IV_SIZE, blob.size - IV_SIZE)
    }
}
