package com.example.deviceauth

/**
 * Конфигурация
 *
 * @property serviceUrl базовый URL сервиса аутентификации
 * @property appId идентификатор приложения, передаётся в запросы
 * @property challengeTimeoutSeconds таймаут HTTP-запросов в секундах
 * @property prefsName имя SharedPreferences для хранения device_id
 */
data class DeviceAuthConfig(
    val serviceUrl: String,
    val appId: String,
    val sessionTtlMinutes: Long = 2,
    val prefsName: String = "device_auth_prefs"
) {
    init {
        require(serviceUrl.isNotBlank()) { "serviceUrl не может быть пустым" }
        require(appId.isNotBlank()) { "appId не может быть пустым" }
    }
}