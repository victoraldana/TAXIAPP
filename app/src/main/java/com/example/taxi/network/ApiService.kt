package com.example.taxi.network

import com.example.taxi.model.AuthModels
import com.example.taxi.model.CreateTripRequest
import com.example.taxi.model.CreateTripResponse
import com.example.taxi.model.DriverLocationResponse
import com.example.taxi.model.DriverQueueStatusResponse
import com.example.taxi.model.QueueAddResponse
import com.example.taxi.model.PendingTripResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

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

    @POST("api/auth/login")
    suspend fun login(
        @Body request: AuthModels.LoginRequest
    ): Response<AuthModels.AuthResponse>

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

    // ── Viajes (Cliente) ──────────────────────────────────────────────────────

    @POST("api/admin/trips")
    suspend fun createTrip(
        @Body request: CreateTripRequest
    ): Response<CreateTripResponse>

    @GET("api/admin/drivers/{id}/location")
    suspend fun getDriverLocation(
        @Path("id") id: String
    ): Response<DriverLocationResponse>

    // ── Conductor: cola y ubicación ───────────────────────────────────────────

    @GET("api/admin/drivers/{id}/queue-status")
    suspend fun getDriverQueueStatus(
        @Path("id") id: String
    ): Response<DriverQueueStatusResponse>

    @POST("api/admin/queue/{driverId}")
    suspend fun addDriverToQueue(
        @Path("driverId") driverId: String
    ): Response<QueueAddResponse>

    @DELETE("api/admin/queue/{driverId}")
    suspend fun removeDriverFromQueue(
        @Path("driverId") driverId: String
    ): Response<AuthModels.SimpleResponse>

    @PATCH("api/admin/drivers/{id}/location")
    suspend fun updateDriverLocation(
        @Path("id") id: String,
        @Body location: Map<String, Double>
    ): Response<AuthModels.SimpleResponse>

    // ── Conductor: viaje asignado ─────────────────────────────────────────────

    @GET("api/admin/drivers/{id}/pending-trip")
    suspend fun getPendingTrip(
        @Path("id") id: String
    ): Response<PendingTripResponse>

    @PATCH("api/admin/trips/{tripId}/accept")
    suspend fun acceptTrip(
        @Path("tripId") tripId: String
    ): Response<AuthModels.SimpleResponse>

    @PATCH("api/admin/trips/{tripId}/reject")
    suspend fun rejectTrip(
        @Path("tripId") tripId: String
    ): Response<AuthModels.SimpleResponse>

    @PATCH("api/admin/trips/{tripId}/finish")
    suspend fun finishTrip(
        @Path("tripId") tripId: String
    ): Response<AuthModels.SimpleResponse>

    @PATCH("api/admin/trips/{tripId}/arrive")
    suspend fun notifyArrival(
        @Path("tripId") tripId: String
    ): Response<AuthModels.SimpleResponse>

    @GET("api/admin/trips/{tripId}/status")
    suspend fun getTripStatus(
        @Path("tripId") tripId: String
    ): Response<com.example.taxi.model.TripStatusResponse>

    @POST("api/admin/trips/{tripId}/rate")
    suspend fun rateDriver(
        @Path("tripId") tripId: String,
        @Body request: com.example.taxi.model.RatingRequest
    ): Response<com.example.taxi.model.RatingResponse>

    // ── Historial ─────────────────────────────────────────────────────────────

    @GET("api/admin/trips/client/{clientId}/active")
    suspend fun getActiveTripForClient(
        @Path("clientId") clientId: String
    ): Response<PendingTripResponse>

    @GET("api/admin/trips/client/{clientId}")
    suspend fun getClientTrips(
        @Path("clientId") clientId: String
    ): Response<com.example.taxi.model.TripHistoryResponse>

    @GET("api/admin/trips/driver/{driverId}")
    suspend fun getDriverTrips(
        @Path("driverId") driverId: String
    ): Response<com.example.taxi.model.TripHistoryResponse>
}

