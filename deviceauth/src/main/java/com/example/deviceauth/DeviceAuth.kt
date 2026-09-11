package com.example.deviceauth

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceAuth private constructor(
    private val config: DeviceAuthConfig,
    private val context: Context
) {
    private val fingerprintCollector = FingerprintCollector(context)
    private val keyStorage = KeyStorage(context, config.prefsName)
    private val cryptoManager = CryptoManager(keyStorage)
    private val networkClient = NetworkClient(config.serviceUrl, config.appId)
    private val prefs: SharedPreferences =
        context.getSharedPreferences(config.prefsName, Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var instance: DeviceAuth? = null

        fun init(context: Context, config: DeviceAuthConfig): DeviceAuth {
            return instance ?: synchronized(this) {
                instance ?: DeviceAuth(config, context.applicationContext).also { instance = it }
            }
        }
    }

    suspend fun register(userId: String): DeviceAuthResult = withContext(Dispatchers.IO) {
        try {
            val fingerprint = fingerprintCollector.collect()
            val publicKeyHex = cryptoManager.getPublicKeyHex()

            val registerResponse = networkClient.registerDevice(userId, fingerprint, publicKeyHex)
            val signatureHex = cryptoManager.signHex(registerResponse.challenge.toByteArray(Charsets.UTF_8))

            val verifyResponse = networkClient.verifySignature(
                deviceId = registerResponse.deviceId,
                signatureHex = signatureHex,
                flow = "register"
            )

            saveDeviceId(registerResponse.deviceId)

            DeviceAuthResult.Success(
                status = verifyResponse.status,
                userId = userId
            )
        } catch (e: ServiceException) {
            mapServiceError(e)
        } catch (e: Exception) {
            DeviceAuthResult.Failure(e.message)
        }
    }

    suspend fun authenticate(userId: String? = null): DeviceAuthResult = withContext(Dispatchers.IO) {
        try {
            val fingerprint = fingerprintCollector.collect()

            val challengeResponse = networkClient.getChallenge(fingerprint, userId)
            val signatureHex = cryptoManager.signHex(challengeResponse.challenge.toByteArray(Charsets.UTF_8))

            val verifyResponse = networkClient.verifySignature(
                deviceId = challengeResponse.deviceId,
                signatureHex = signatureHex,
                flow = "auth"
            )

            saveDeviceId(challengeResponse.deviceId)

            DeviceAuthResult.Success(
                status = verifyResponse.status,
                userId = verifyResponse.userId ?: userId ?: ""
            )
        } catch (e: ServiceException) {
            mapServiceError(e)
        } catch (e: Exception) {
            DeviceAuthResult.Failure(e.message)
        }
    }

    fun hasValidSession(): Boolean {
        return getValidDeviceId() != null
    }

    fun logout() {
        prefs.edit()
            .remove("device_id")
            .remove("device_id_saved_at")
            .apply()
    }

    private fun saveDeviceId(deviceId: String) {
        prefs.edit()
            .putString("device_id", deviceId)
            .putLong("device_id_saved_at", System.currentTimeMillis())
            .apply()
    }

    private fun getValidDeviceId(): String? {
        val deviceId = prefs.getString("device_id", null) ?: return null
        val savedAt = prefs.getLong("device_id_saved_at", 0)
        if (savedAt == 0L) return null

        val ttlMillis = config.sessionTtlMinutes * 60_000L
        if (System.currentTimeMillis() - savedAt > ttlMillis) {
            logout()
            return null
        }
        return deviceId
    }

    private fun mapServiceError(e: ServiceException): DeviceAuthResult {
        return when (e.code) {
            "multiple_devices" -> DeviceAuthResult.MultipleDevices
            "device_not_registered" -> DeviceAuthResult.Failure("device_not_registered")
            else -> DeviceAuthResult.Failure(e.message)
        }
    }
}