package com.example.deviceauth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature

class CryptoManager(
    private val keyStorage: KeyStorage,
    private val keyAlias: String = "device_auth_key"
) {

    private var softwarePrivateKey: Ed25519PrivateKeyParameters? = null
    private val useKeystore: Boolean by lazy { probeKeystore() }

    private fun probeKeystore(): Boolean {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (ks.containsAlias(keyAlias) && canSign(ks)) {
                return true
            }
            if (ks.containsAlias(keyAlias)) {
                ks.deleteEntry(keyAlias)
            }
            val kpg = KeyPairGenerator.getInstance("Ed25519", "AndroidKeyStore")
            kpg.initialize(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                ).build()
            )
            kpg.generateKeyPair()
            val reloaded = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            canSign(reloaded)
        } catch (_: Exception) {
            false
        }
    }

    private fun canSign(ks: KeyStore): Boolean {
        return try {
            val entry = ks.getEntry(keyAlias, null) as? KeyStore.PrivateKeyEntry ?: return false
            keystoreSignature().run {
                initSign(entry.privateKey)
                update(PROBE_MESSAGE)
                sign()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun keystoreSignature(): Signature {
        return try {
            Signature.getInstance("Ed25519", "AndroidKeyStore")
        } catch (_: Exception) {
            Signature.getInstance("Ed25519")
        }
    }

    private fun ensureSoftwareKey(): Ed25519PrivateKeyParameters {
        softwarePrivateKey?.let { return it }
        val stored = keyStorage.getPrivateKey()
        val priv = if (stored != null) {
            Ed25519PrivateKeyParameters(stored.hexToBytes(), 0)
        } else {
            val gen = Ed25519KeyPairGenerator()
            gen.init(Ed25519KeyGenerationParameters(SecureRandom()))
            val pair = gen.generateKeyPair()
            val p = pair.private as Ed25519PrivateKeyParameters
            keyStorage.savePrivateKey(p.encoded.toHexString())
            p
        }
        softwarePrivateKey = priv
        return priv
    }

    fun getPublicKeyHex(): String {
        return if (useKeystore) {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val entry = ks.getEntry(keyAlias, null) as KeyStore.PrivateKeyEntry
            rawEd25519PublicKey(entry.certificate.publicKey.encoded).toHexString()
        } else {
            ensureSoftwareKey().generatePublicKey().encoded.toHexString()
        }
    }

    fun signHex(message: ByteArray): String {
        return if (useKeystore) {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val entry = ks.getEntry(keyAlias, null) as KeyStore.PrivateKeyEntry
            keystoreSignature().run {
                initSign(entry.privateKey)
                update(message)
                sign().toHexString()
            }
        } else {
            val signer = Ed25519Signer()
            signer.init(true, ensureSoftwareKey())
            signer.update(message, 0, message.size)
            signer.generateSignature().toHexString()
        }
    }

    companion object {
        private val PROBE_MESSAGE = byteArrayOf(0x01)
        fun rawEd25519PublicKey(encoded: ByteArray): ByteArray {
            if (encoded.size == 32) return encoded
            if (encoded.size > 32) return encoded.copyOfRange(encoded.size - 32, encoded.size)
            return encoded
        }
    }
}

fun ByteArray.toHexString(): String =
    joinToString("") { "%02x".format(it) }

fun String.hexToBytes(): ByteArray {
    val out = ByteArray(length / 2)
    for (i in out.indices) {
        out[i] = ((this[i * 2].digitToInt(16) shl 4) +
                  this[i * 2 + 1].digitToInt(16)).toByte()
    }
    return out
}
