package com.example.taxi.model

import com.google.gson.annotations.SerializedName

/**
 * Modelos de datos para la capa de autenticación (Network <-> Domain)
 */
object AuthModels {

    // ── OTP ───────────────────────────────────────────────────────────────────

    data class OtpSendRequest(
        val target: String,   // phone number or email
        val type: String      // "phone" | "email"
    )

    data class OtpVerifyRequest(
        val target: String,
        val type: String,
        val code: String
    )

    data class OtpResponse(
        val success: Boolean,
        val message: String,
        @SerializedName("dev_code") val devCode: String? = null,
        @SerializedName("dev_mode") val devMode: Boolean? = null
    )

    // ── Requests ──────────────────────────────────────────────────────────────

    data class LoginByPhoneRequest(
        val phone: String,
        val password: String
    )

    data class LoginRequest(
        val email: String,
        val password: String
    )

    /**
     * Registro por pasos (flujo telefónico + KYC).
     * Todos los campos excepto phone son opcionales porque el registro
     * se hace por pasos desde la app.
     */
    data class RegisterRequest(
        val phone: String,
        val password: String? = null,
        @SerializedName("full_name")           val fullName: String? = null,
        val email: String? = null,
        val cedula: String? = null,
        @SerializedName("birth_date")          val birthDate: String? = null,   // "YYYY-MM-DD"
        @SerializedName("selfie_url")          val selfieUrl: String? = null,
        @SerializedName("id_doc_url")          val idDocUrl: String? = null,
        val role: String = "client",                                             // "client" | "driver"
        // Pago Móvil (solo conductores)
        @SerializedName("pago_movil_cedula")   val pagoMovilCedula: String? = null,
        @SerializedName("pago_movil_telefono") val pagoMovilTelefono: String? = null,
        @SerializedName("pago_movil_banco")    val pagoMovilBanco: String? = null
    )

    data class RefreshTokenRequest(
        @SerializedName("refresh_token") val refreshToken: String
    )

    data class LogoutRequest(
        @SerializedName("refresh_token") val refreshToken: String? = null
    )

    // ── Responses ─────────────────────────────────────────────────────────────

    data class SimpleResponse(
        val success: Boolean,
        val message: String
    )

    data class TokenData(
        @SerializedName("access_token")  val accessToken: String,
        @SerializedName("refresh_token") val refreshToken: String,
        @SerializedName("token_type")    val tokenType: String,
        @SerializedName("expires_in")    val expiresIn: String
    )

    data class DriverProfile(
        @SerializedName("vehicle_make")         val vehicleMake: String?,
        @SerializedName("vehicle_model")        val vehicleModel: String?,
        @SerializedName("vehicle_plate")        val vehiclePlate: String?,
        @SerializedName("vehicle_color")        val vehicleColor: String?,
        @SerializedName("vehicle_type")         val vehicleType: String?,
        @SerializedName("is_available")         val isAvailable: Boolean?,
        @SerializedName("is_approved")          val isApproved: Boolean?,
        val rating: Double?,
        @SerializedName("total_trips")          val totalTrips: Int?,
        // Pago Móvil
        @SerializedName("pago_movil_cedula")    val pagoMovilCedula: String?,
        @SerializedName("pago_movil_telefono")  val pagoMovilTelefono: String?,
        @SerializedName("pago_movil_banco")     val pagoMovilBanco: String?
    )

    data class ClientProfile(
        @SerializedName("preferred_payment") val preferredPayment: String?,
        val rating: Double?,
        @SerializedName("total_trips")       val totalTrips: Int?
    )

    data class UserData(
        val id: String,
        val email: String?,
        @SerializedName("full_name")   val fullName: String?,
        val phone: String?,
        @SerializedName("avatar_url")  val avatarUrl: String?,
        val role: String,
        @SerializedName("is_verified") val isVerified: Boolean,
        @SerializedName("kyc_status")  val kycStatus: String? = null,
        val profile: Any? = null
    )

    data class AuthResponseData(
        val user: UserData,
        val tokens: TokenData
    )

    data class AuthResponse(
        val success: Boolean,
        val message: String,
        val data: AuthResponseData?,
        val code: String?,
        @SerializedName("dev_mode") val devMode: Boolean? = null,
        val errors: List<FieldError>?
    )

    data class RefreshResponseData(
        val tokens: TokenData
    )

    data class RefreshResponse(
        val success: Boolean,
        val message: String,
        val data: RefreshResponseData?,
        val code: String?
    )

    data class MeResponseData(
        val user: UserData
    )

    data class MeResponse(
        val success: Boolean,
        val data: MeResponseData?
    )

    data class FieldError(
        val field: String,
        val message: String
    )

    // ── Estados UI ─────────────────────────────────────────────────────────────

    sealed class AuthResult {
        data class Success(val user: UserData, val tokens: TokenData) : AuthResult()
        data class Error(val message: String, val code: String? = null) : AuthResult()
        object Loading : AuthResult()
    }

    sealed class OtpResult {
        data class Sent(val devCode: String? = null) : OtpResult()
        object Verified : OtpResult()
        data class Error(val message: String) : OtpResult()
        object Loading : OtpResult()
    }
}
