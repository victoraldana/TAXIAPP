package com.example.taxi.network

import com.example.taxi.model.AuthModels
import com.example.taxi.model.CreateTripRequest
import com.example.taxi.model.CreateTripResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {

    // ── OTP ───────────────────────────────────────────────────────────────────

    @POST("api/auth/otp/send")
    suspend fun sendOtp(
        @Body request: AuthModels.OtpSendRequest
    ): Response<AuthModels.OtpResponse>

    @POST("api/auth/otp/verify")
    suspend fun verifyOtp(
        @Body request: AuthModels.OtpVerifyRequest
    ): Response<AuthModels.OtpResponse>

    // ── Auth ──────────────────────────────────────────────────────────────────

    @POST("api/auth/register")
    suspend fun register(
        @Body request: AuthModels.RegisterRequest
    ): Response<AuthModels.AuthResponse>

    /** Login clásico por email */
    @POST("api/auth/login")
    suspend fun login(
        @Body request: AuthModels.LoginRequest
    ): Response<AuthModels.AuthResponse>

    /** Login principal por teléfono */
    @POST("api/auth/login/phone")
    suspend fun loginByPhone(
        @Body request: AuthModels.LoginByPhoneRequest
    ): Response<AuthModels.AuthResponse>

    @POST("api/auth/refresh")
    suspend fun refreshToken(
        @Body request: AuthModels.RefreshTokenRequest
    ): Response<AuthModels.RefreshResponse>

    @POST("api/auth/logout")
    suspend fun logout(
        @Body request: AuthModels.LogoutRequest
    ): Response<AuthModels.SimpleResponse>

    @GET("api/auth/me")
    suspend fun getMe(
        @Header("Authorization") token: String
    ): Response<AuthModels.MeResponse>

    // ── Viajes ────────────────────────────────────────────────────────────────

    @POST("api/admin/trips")
    suspend fun createTrip(
        @Body request: CreateTripRequest
    ): Response<CreateTripResponse>
}
