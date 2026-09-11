package com.example.deviceauth

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("app_id") val appId: String,
    @SerializedName("fingerprint") val fingerprint: String,
    @SerializedName("public_key") val publicKey: String
)

data class AuthChallengeRequest(
    @SerializedName("app_id") val appId: String,
    @SerializedName("fingerprint") val fingerprint: String,
    @SerializedName("user_id") val userId: String? = null
)

data class VerifyRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("signature") val signature: String,
    @SerializedName("flow") val flow: String
)

data class RegisterResponse(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("challenge") val challenge: String
)

data class AuthChallengeResponse(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("challenge") val challenge: String
)

data class VerifyResponse(
    @SerializedName("status") val status: String,
    @SerializedName("user_id") val userId: String? = null
)

data class ErrorResponse(
    @SerializedName("error") val error: String,
    @SerializedName("message") val message: String? = null
)