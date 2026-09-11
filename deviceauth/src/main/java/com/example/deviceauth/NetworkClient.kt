package com.example.deviceauth

import com.google.gson.Gson
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

class NetworkClient(baseUrl: String, private val appId: String) {

    private val gson = Gson()

    private val api: DeviceAuthApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(DeviceAuthApi::class.java)

    suspend fun registerDevice(
        userId: String,
        fingerprint: String,
        publicKeyHex: String
    ): RegisterResponse {
        val request = RegisterRequest(
            userId = userId,
            appId = appId,
            fingerprint = fingerprint,
            publicKey = publicKeyHex
        )
        val response = api.register(request)
        return handleResponse(response)
    }

    suspend fun getChallenge(
        fingerprint: String,
        userId: String? = null
    ): AuthChallengeResponse {
        val request = AuthChallengeRequest(
            appId = appId,
            fingerprint = fingerprint,
            userId = userId
        )
        val response = api.getChallenge(request)
        return handleResponse(response)
    }

    suspend fun verifySignature(
        deviceId: String,
        signatureHex: String,
        flow: String
    ): VerifyResponse {
        val request = VerifyRequest(
            deviceId = deviceId,
            signature = signatureHex,
            flow = flow
        )
        val response = api.verify(request)
        return handleResponse(response)
    }

    private fun <T> handleResponse(response: Response<T>): T {
        if (response.isSuccessful) {
            return response.body() ?: throw ServiceException("empty_body", "Пустое тело ответа")
        } else {
            val errorBody = response.errorBody()?.string()
            val errorResponse = try {
                gson.fromJson(errorBody, ErrorResponse::class.java)
            } catch (e: Exception) {
                null
            }
            val code = errorResponse?.error ?: "unknown_error"
            val message = errorResponse?.message ?: "HTTP ${response.code()}"
            throw ServiceException(code, message)
        }
    }

    interface DeviceAuthApi {
        @POST("/api/devices/register")
        suspend fun register(@Body body: RegisterRequest): Response<RegisterResponse>

        @POST("/api/auth/challenge")
        suspend fun getChallenge(@Body body: AuthChallengeRequest): Response<AuthChallengeResponse>

        @POST("/api/auth/verify")
        suspend fun verify(@Body body: VerifyRequest): Response<VerifyResponse>
    }
}

class ServiceException(val code: String, message: String) : Exception(message)