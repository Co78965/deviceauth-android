package com.example.deviceauth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class KeyStorage(private val context: Context, private val prefsName: String = "device_auth_prefs") {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private val secretPrefs: SharedPreferences by lazy { createSecretPrefs() }

    private fun createSecretPrefs(): SharedPreferences {
        val encryptedName = "${prefsName}_encrypted"
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                encryptedName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            try {
                context.deleteSharedPreferences(encryptedName)
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    encryptedName,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (_: Exception) {
                context.getSharedPreferences("${prefsName}_software_keys", Context.MODE_PRIVATE)
            }
        }
    }

    fun saveDeviceId(deviceId: String) {
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()    }

    fun getDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)

    fun savePrivateKey(hex: String) {
        secretPrefs.edit().putString(KEY_PRIVATE_KEY, hex).apply()
    }

    fun getPrivateKey(): String? = secretPrefs.getString(KEY_PRIVATE_KEY, null)

    fun clear() {
        prefs.edit().clear().apply()
        secretPrefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_PRIVATE_KEY = "private_key_fallback"
    }
}
