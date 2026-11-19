package com.example.crypto

import android.content.Context
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
    val ciphertextBase64: String,
    val ivBase64: String
)

data class IdentityKeys(
    val publicKeyBase64: String,
    val privateKey: PrivateKey,
    val publicKey: PublicKey,
    val keyFingerprint: String
)

/**
 * End-to-End Encryption manager using EC P-256 (secp256r1) ECDH key agreement
 * and AES-256-GCM authenticated encryption.
 */
class E2eeCryptoManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    fun getOrCreateIdentityKeys(uid: String): IdentityKeys {
        val privKeyKey = "ec_priv_$uid"
        val pubKeyKey = "ec_pub_$uid"
        val storedPriv = prefs.getString(privKeyKey, null)
        val storedPub = prefs.getString(pubKeyKey, null)

        val keyPair: KeyPair = if (!storedPriv.isNullOrBlank() && !storedPub.isNullOrBlank()) {
            try {
                val kf = KeyFactory.getInstance("EC")
                val privBytes = Base64.decode(storedPriv, Base64.NO_WRAP)
                val pubBytes = Base64.decode(storedPub, Base64.NO_WRAP)
                val priv = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val pub = kf.generatePublic(X509EncodedKeySpec(pubBytes))
                KeyPair(pub, priv)
            } catch (_: Exception) {
                generateAndStoreKeyPair(privKeyKey, pubKeyKey)
            }
        } else {
            generateAndStoreKeyPair(privKeyKey, pubKeyKey)
        }

        val pubBase64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        val fingerprint = computeFingerprint(keyPair.public.encoded)
        return IdentityKeys(
            publicKeyBase64 = pubBase64,
            privateKey = keyPair.private,
            publicKey = keyPair.public,
            keyFingerprint = fingerprint
        )
    }

    private fun generateAndStoreKeyPair(privKeyKey: String, pubKeyKey: String): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        val kp = kpg.generateKeyPair()
        val privB64 = Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP)
        val pubB64 = Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
        prefs.edit()
            .putString(privKeyKey, privB64)
            .putString(pubKeyKey, pubB64)
            .apply()
        return kp
    }

    fun generateRoomAesKey(): ByteArray {
        val kg = KeyGenerator.getInstance("AES")
        kg.init(256, secureRandom)
        return kg.generateKey().encoded
    }

    fun wrapRoomKeyForParticipant(
        senderIdentity: IdentityKeys,
        recipientPublicKeyBase64: String,
        roomKeyBytes: ByteArray
    ): String {
        val recipientPubBytes = Base64.decode(recipientPublicKeyBase64, Base64.NO_WRAP)
        val recipientPub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(recipientPubBytes))
        val wrappingSecret = deriveEcdhAesKey(senderIdentity.privateKey, recipientPub)
        val encrypted = encryptBytes(wrappingSecret, roomKeyBytes)
        return "${senderIdentity.publicKeyBase64}|${encrypted.ivBase64}|${encrypted.ciphertextBase64}"
    }

    fun unwrapRoomKey(
        myIdentity: IdentityKeys,
        wrappedToken: String
    ): ByteArray? {
        return try {
            val parts = wrappedToken.split("|")
            if (parts.size != 3) return null
            val senderPubBytes = Base64.decode(parts[0], Base64.NO_WRAP)
            val senderPub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(senderPubBytes))
            val wrappingSecret = deriveEcdhAesKey(myIdentity.privateKey, senderPub)
            decryptBytes(wrappingSecret, parts[2], parts[1])
        } catch (_: Exception) {
            null
        }
    }

    private fun deriveEcdhAesKey(privateKey: PrivateKey, peerPublicKey: PublicKey): SecretKey {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(peerPublicKey, true)
        val sharedSecret = agreement.generateSecret()
        val digest = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
        return SecretKeySpec(digest, "AES")
    }

    fun encryptText(roomKeyBytes: ByteArray, plaintext: String): EncryptedPayload {
        val key = SecretKeySpec(roomKeyBytes, "AES")
        return encryptBytes(key, plaintext.toByteArray(Charsets.UTF_8))
    }

    fun decryptText(roomKeyBytes: ByteArray, ciphertextBase64: String, ivBase64: String): String? {
        return try {
            val key = SecretKeySpec(roomKeyBytes, "AES")
            val plainBytes = decryptBytes(key, ciphertextBase64, ivBase64)
            String(plainBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun encryptBytes(secretKey: SecretKey, plainBytes: ByteArray): EncryptedPayload {
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherBytes = cipher.doFinal(plainBytes)
        return EncryptedPayload(
            ciphertextBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    private fun decryptBytes(secretKey: SecretKey, ciphertextBase64: String, ivBase64: String): ByteArray {
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val cipherBytes = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherBytes)
    }

    fun computeFingerprint(keyBytes: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(keyBytes)
        return hash.take(12).joinToString("") { "%02X".format(it) }
            .chunked(4)
            .joinToString(" ")
    }

    fun deriveCallSasCode(roomKeyBytes: ByteArray, seed: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(roomKeyBytes)
        md.update(seed.toByteArray(Charsets.UTF_8))
        val digest = md.digest()
        val num = ((digest[0].toInt() and 0xFF) shl 16) or
            ((digest[1].toInt() and 0xFF) shl 8) or
            (digest[2].toInt() and 0xFF)
        val code = (num % 900000) + 100000
        return "${code / 1000}-${code % 1000}"
    }

    fun cacheRoomKey(chatId: String, roomKeyBytes: ByteArray) {
        prefs.edit()
            .putString("room_key_$chatId", Base64.encodeToString(roomKeyBytes, Base64.NO_WRAP))
            .apply()
    }

    fun getCachedRoomKey(chatId: String): ByteArray? {
        val b64 = prefs.getString("room_key_$chatId", null) ?: return null
        return try {
            Base64.decode(b64, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "kinchat_e2ee_keystore"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
    }
}
