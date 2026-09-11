package com.example.deviceauth

sealed class DeviceAuthResult {
    data class Success(
        val status: String,
        val userId: String
    ) : DeviceAuthResult()

    object MultipleDevices : DeviceAuthResult()

    data class Failure(
        val message: String? = null
    ) : DeviceAuthResult()
}