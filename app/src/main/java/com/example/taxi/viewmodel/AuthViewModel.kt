package com.example.taxi.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taxi.model.AuthModels
import com.example.taxi.model.UserRole
import com.example.taxi.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    // ── Estado de autenticación ───────────────────────────────────────────────
    private val _authState = MutableStateFlow<AuthModels.AuthResult?>(null)
    val authState: StateFlow<AuthModels.AuthResult?> = _authState.asStateFlow()

    // ── Estado de OTP ─────────────────────────────────────────────────────────
    private val _otpState = MutableStateFlow<AuthModels.OtpResult?>(null)
    val otpState: StateFlow<AuthModels.OtpResult?> = _otpState.asStateFlow()

    // ── Rol seleccionado ──────────────────────────────────────────────────────
    private val _selectedRole = MutableStateFlow<UserRole?>(null)
    val selectedRole: StateFlow<UserRole?> = _selectedRole.asStateFlow()

    // ── Modo desarrollo (mostrar en UI) ───────────────────────────────────────
    val DEV_MODE = true
    val DEV_OTP_CODE = "0000"

    fun selectRole(role: UserRole) {
        _selectedRole.value = role
    }

    // ── Usuario logueado (persiste tras clearAuthState) ───────────────────────
    private val _loggedUser = MutableStateFlow<AuthModels.UserData?>(null)
    val loggedUser: StateFlow<AuthModels.UserData?> = _loggedUser.asStateFlow()

    fun setLoggedUser(user: AuthModels.UserData?) {
        _loggedUser.value = user
    }

    fun clearAuthState() {
        _authState.value = null
    }

    fun clearOtpState() {
        _otpState.value = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OTP: Enviar
    // ─────────────────────────────────────────────────────────────────────────
    fun sendOtp(target: String, type: String) {
        _otpState.value = AuthModels.OtpResult.Loading
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.sendOtp(
                    AuthModels.OtpSendRequest(target = target.trim(), type = type)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    _otpState.value = AuthModels.OtpResult.Sent(
                        devCode = response.body()?.devCode
                    )
                } else {
                    _otpState.value = AuthModels.OtpResult.Error(
                        response.body()?.message ?: "Error al enviar el código"
                    )
                }
            } catch (e: Exception) {
                _otpState.value = AuthModels.OtpResult.Error(
                    "No se pudo conectar al servidor."
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OTP: Verificar
    // ─────────────────────────────────────────────────────────────────────────
    fun verifyOtp(target: String, type: String, code: String) {
        _otpState.value = AuthModels.OtpResult.Loading
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.verifyOtp(
                    AuthModels.OtpVerifyRequest(
                        target = target.trim(),
                        type = type,
                        code = code.trim()
                    )
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    _otpState.value = AuthModels.OtpResult.Verified
                } else {
                    _otpState.value = AuthModels.OtpResult.Error(
                        response.body()?.message ?: "Código inválido o expirado"
                    )
                }
            } catch (e: Exception) {
                _otpState.value = AuthModels.OtpResult.Error(
                    "No se pudo conectar al servidor."
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Login por teléfono (flujo principal)
    // ─────────────────────────────────────────────────────────────────────────
    fun loginByPhone(phone: String, password: String) {
        _authState.value = AuthModels.AuthResult.Loading
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.loginByPhone(
                    AuthModels.LoginByPhoneRequest(
                        phone = phone.trim(),
                        password = password
                    )
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        _loggedUser.value = body.data.user
                        _authState.value = AuthModels.AuthResult.Success(
                            user = body.data.user,
                            tokens = body.data.tokens
                        )
                    } else {
                        _authState.value = AuthModels.AuthResult.Error(
                            message = body?.message ?: "Error al iniciar sesión",
                            code = body?.code
                        )
                    }
                } else {
                    val errorMsg = when (response.code()) {
                        401 -> "Teléfono o contraseña incorrectos."
                        423 -> "Cuenta bloqueada. Inténtalo más tarde."
                        403 -> "Tu cuenta está desactivada."
                        429 -> "Demasiados intentos. Espera unos minutos."
                        else -> "Error del servidor (${response.code()})"
                    }
                    _authState.value = AuthModels.AuthResult.Error(message = errorMsg)
                }
            } catch (e: Exception) {
                _authState.value = AuthModels.AuthResult.Error(
                    message = "No se pudo conectar al servidor. Verifica tu conexión.",
                    code = "NETWORK_ERROR"
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Login clásico por email (compatibilidad)
    // ─────────────────────────────────────────────────────────────────────────
    fun login(email: String, password: String) {
        _authState.value = AuthModels.AuthResult.Loading
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.login(
                    AuthModels.LoginRequest(email = email.trim(), password = password)
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        _loggedUser.value = body.data.user
                        _authState.value = AuthModels.AuthResult.Success(
                            user = body.data.user,
                            tokens = body.data.tokens
                        )
                    } else {
                        _authState.value = AuthModels.AuthResult.Error(
                            message = body?.message ?: "Error al iniciar sesión",
                            code = body?.code
                        )
                    }
                } else {
                    val errorMsg = when (response.code()) {
                        401 -> "Correo o contraseña incorrectos."
                        423 -> "Cuenta bloqueada. Inténtalo más tarde."
                        403 -> "Tu cuenta está desactivada."
                        429 -> "Demasiados intentos. Espera unos minutos."
                        else -> "Error del servidor (${response.code()})"
                    }
                    _authState.value = AuthModels.AuthResult.Error(message = errorMsg)
                }
            } catch (e: Exception) {
                _authState.value = AuthModels.AuthResult.Error(
                    message = "No se pudo conectar al servidor. Verifica tu conexión.",
                    code = "NETWORK_ERROR"
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Registro completo (todos los datos acumulados del flujo multi-paso)
    // ─────────────────────────────────────────────────────────────────────────
    fun register(
        phone: String,
        password: String,
        fullName: String,
        cedula: String,
        birthDate: String,
        email: String,
        selfieUrl: String? = null,
        idDocUrl: String? = null,
        role: UserRole = UserRole.CLIENT,
        pagoMovilCedula: String? = null,
        pagoMovilTelefono: String? = null,
        pagoMovilBanco: String? = null
    ) {
        _authState.value = AuthModels.AuthResult.Loading
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.register(
                    AuthModels.RegisterRequest(
                        phone = phone.trim(),
                        password = password,
                        fullName = fullName.trim(),
                        email = email.trim().takeIf { it.isNotEmpty() },
                        cedula = cedula.trim().takeIf { it.isNotEmpty() },
                        birthDate = birthDate.trim().takeIf { it.isNotEmpty() },
                        selfieUrl = selfieUrl,
                        idDocUrl = idDocUrl,
                        role = if (role == UserRole.CLIENT) "client" else "driver",
                        pagoMovilCedula = pagoMovilCedula?.trim()?.takeIf { it.isNotEmpty() },
                        pagoMovilTelefono = pagoMovilTelefono?.trim()?.takeIf { it.isNotEmpty() },
                        pagoMovilBanco = pagoMovilBanco?.trim()?.takeIf { it.isNotEmpty() }
                    )
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        _authState.value = AuthModels.AuthResult.Success(
                            user = body.data.user,
                            tokens = body.data.tokens
                        )
                    } else {
                        _authState.value = AuthModels.AuthResult.Error(
                            message = body?.message ?: "Error al registrarse",
                            code = body?.code
                        )
                    }
                } else {
                    val errorMsg = when (response.code()) {
                        409 -> "Ya existe una cuenta con ese teléfono o correo."
                        400 -> "Datos inválidos. Verifica la información."
                        429 -> "Demasiados intentos. Espera unos minutos."
                        else -> "Error del servidor (${response.code()})"
                    }
                    _authState.value = AuthModels.AuthResult.Error(message = errorMsg)
                }
            } catch (e: Exception) {
                _authState.value = AuthModels.AuthResult.Error(
                    message = "No se pudo conectar al servidor. Verifica tu conexión.",
                    code = "NETWORK_ERROR"
                )
            }
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────
    fun logout() {
        _loggedUser.value = null
        _authState.value  = null
        _otpState.value   = null
    }
}
