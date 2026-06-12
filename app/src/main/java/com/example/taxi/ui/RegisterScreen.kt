package com.example.taxi.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.example.taxi.model.AuthModels
import com.example.taxi.model.UserRole
import com.example.taxi.viewmodel.AuthViewModel

// ─── Paleta compartida ────────────────────────────────────────────────────────
private val RegYellow     = Color(0xFFFFC107)
private val RegYellowDark = Color(0xFFE6A800)
private val RegDark       = Color(0xFF1A1A2E)
private val RegCard       = Color(0xFF1F2B47)
private val RegCardLight  = Color(0xFF253050)
private val RegText       = Color(0xFFF0F4FF)
private val RegSubText    = Color(0xFF8B9BC8)
private val RegError      = Color(0xFFFF6B6B)
private val RegSuccess    = Color(0xFF51CF66)

// ─── Pasos del flujo ──────────────────────────────────────────────────────────
enum class RegStep {
    PHONE_INPUT,        // 1. Ingresar teléfono
    PHONE_OTP,          // 2. Verificar OTP teléfono
    PERSONAL_DATA,      // 3. Nombre, cédula, fecha nacimiento
    EMAIL_INPUT,        // 4. Ingresar correo
    EMAIL_OTP,          // 5. Verificar OTP email
    PHOTO_KYC           // 6. Selfie + foto cédula
}

// ─── Pantalla principal de Registro ──────────────────────────────────────────
@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    selectedRole: UserRole,
    onRegistrationComplete: () -> Unit,
    onBack: () -> Unit
) {
    var step by remember { mutableStateOf(RegStep.PHONE_INPUT) }

    // Datos acumulados
    var phone     by remember { mutableStateOf("") }
    var fullName  by remember { mutableStateOf("") }
    var cedula    by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }
    var email     by remember { mutableStateOf("") }
    var selfieUri by remember { mutableStateOf<Uri?>(null) }
    var idDocUri  by remember { mutableStateOf<Uri?>(null) }

    val authState by viewModel.authState.collectAsState()
    val otpState  by viewModel.otpState.collectAsState()

    // Escuchar registro exitoso
    LaunchedEffect(authState) {
        if (authState is AuthModels.AuthResult.Success) {
            viewModel.clearAuthState()
            onRegistrationComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RegDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(56.dp))

            // Header con progreso
            RegHeader(
                step = step,
                onBack = {
                    when (step) {
                        RegStep.PHONE_INPUT   -> onBack()
                        RegStep.PHONE_OTP     -> { step = RegStep.PHONE_INPUT; viewModel.clearOtpState() }
                        RegStep.PERSONAL_DATA -> step = RegStep.PHONE_OTP
                        RegStep.EMAIL_INPUT   -> step = RegStep.PERSONAL_DATA
                        RegStep.EMAIL_OTP     -> { step = RegStep.EMAIL_INPUT; viewModel.clearOtpState() }
                        RegStep.PHOTO_KYC     -> step = RegStep.EMAIL_OTP
                    }
                }
            )

            Spacer(Modifier.height(32.dp))

            // Contenido por paso
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith
                    slideOutHorizontally { -it } + fadeOut()
                },
                label = "step_anim"
            ) { currentStep ->
                when (currentStep) {
                    RegStep.PHONE_INPUT -> StepPhoneInput(
                        phone = phone,
                        onPhoneChange = { phone = it },
                        devMode = viewModel.DEV_MODE,
                        isLoading = otpState is AuthModels.OtpResult.Loading,
                        error = (otpState as? AuthModels.OtpResult.Error)?.message,
                        onNext = {
                            viewModel.clearOtpState()
                            viewModel.sendOtp(phone, "phone")
                        }
                    )
                    RegStep.PHONE_OTP -> StepOtpVerify(
                        title = "Verifica tu teléfono",
                        subtitle = "Código enviado a $phone",
                        devCode = if (viewModel.DEV_MODE) viewModel.DEV_OTP_CODE else null,
                        isLoading = otpState is AuthModels.OtpResult.Loading,
                        error = (otpState as? AuthModels.OtpResult.Error)?.message,
                        onVerify = { code -> viewModel.verifyOtp(phone, "phone", code) },
                        onResend = { viewModel.sendOtp(phone, "phone") }
                    )
                    RegStep.PERSONAL_DATA -> StepPersonalData(
                        fullName = fullName, onFullNameChange = { fullName = it },
                        cedula = cedula, onCedulaChange = { cedula = it },
                        birthDate = birthDate, onBirthDateChange = { birthDate = it },
                        onNext = { step = RegStep.EMAIL_INPUT }
                    )
                    RegStep.EMAIL_INPUT -> StepEmailInput(
                        email = email,
                        onEmailChange = { email = it },
                        isLoading = otpState is AuthModels.OtpResult.Loading,
                        error = (otpState as? AuthModels.OtpResult.Error)?.message,
                        onNext = {
                            viewModel.clearOtpState()
                            viewModel.sendOtp(email, "email")
                        }
                    )
                    RegStep.EMAIL_OTP -> StepOtpVerify(
                        title = "Verifica tu correo",
                        subtitle = "Código enviado a $email",
                        devCode = if (viewModel.DEV_MODE) viewModel.DEV_OTP_CODE else null,
                        isLoading = otpState is AuthModels.OtpResult.Loading,
                        error = (otpState as? AuthModels.OtpResult.Error)?.message,
                        onVerify = { code -> viewModel.verifyOtp(email, "email", code) },
                        onResend = { viewModel.sendOtp(email, "email") }
                    )
                    RegStep.PHOTO_KYC -> StepPhotoKyc(
                        selfieUri = selfieUri,
                        onSelfieCapture = { selfieUri = it },
                        idDocUri = idDocUri,
                        onIdDocCapture = { idDocUri = it },
                        isLoading = authState is AuthModels.AuthResult.Loading,
                        error = (authState as? AuthModels.AuthResult.Error)?.message,
                        onFinish = {
                            viewModel.register(
                                phone = phone,
                                password = "temp_${phone.takeLast(4)}",
                                fullName = fullName,
                                cedula = cedula,
                                birthDate = birthDate,
                                email = email,
                                selfieUrl = selfieUri?.toString(),
                                idDocUrl = idDocUri?.toString(),
                                role = selectedRole
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Avanzar step al verificar OTP
    LaunchedEffect(otpState) {
        if (otpState is AuthModels.OtpResult.Verified) {
            when (step) {
                RegStep.PHONE_OTP -> { step = RegStep.PERSONAL_DATA; viewModel.clearOtpState() }
                RegStep.EMAIL_OTP -> { step = RegStep.PHOTO_KYC; viewModel.clearOtpState() }
                else -> {}
            }
        }
        if (otpState is AuthModels.OtpResult.Sent) {
            when (step) {
                RegStep.PHONE_INPUT -> step = RegStep.PHONE_OTP
                RegStep.EMAIL_INPUT -> step = RegStep.EMAIL_OTP
                else -> {}
            }
        }
    }
}

// ─── Header con barra de progreso ────────────────────────────────────────────
@Composable
private fun RegHeader(step: RegStep, onBack: () -> Unit) {
    val totalSteps = RegStep.values().size
    val currentIndex = step.ordinal + 1
    val progress = currentIndex.toFloat() / totalSteps

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás", tint = RegText)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stepTitle(step),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = RegText
            )
        }

        // Barra de progreso
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(RegCardLight)
        ) {
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(400),
                label = "progress"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(listOf(RegYellow, RegYellowDark))
                    )
            )
        }

        Text(
            text = "Paso $currentIndex de $totalSteps",
            style = MaterialTheme.typography.labelSmall,
            color = RegSubText
        )
    }
}

private fun stepTitle(step: RegStep) = when (step) {
    RegStep.PHONE_INPUT   -> "Tu teléfono"
    RegStep.PHONE_OTP     -> "Verificación"
    RegStep.PERSONAL_DATA -> "Tus datos"
    RegStep.EMAIL_INPUT   -> "Tu correo"
    RegStep.EMAIL_OTP     -> "Verificación"
    RegStep.PHOTO_KYC     -> "Verificación KYC"
}

// ─── Paso 1: Ingreso de teléfono ──────────────────────────────────────────────
@Composable
private fun StepPhoneInput(
    phone: String,
    onPhoneChange: (String) -> Unit,
    devMode: Boolean,
    isLoading: Boolean,
    error: String?,
    onNext: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            text = "Ingresa tu número de teléfono para comenzar el registro.",
            style = MaterialTheme.typography.bodyMedium,
            color = RegSubText
        )

        if (devMode) DevModeBanner()

        RegTextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = "Número de teléfono",
            icon = Icons.Outlined.Phone,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); onNext() }),
            error = error
        )

        RegButton(
            text = "Enviar código",
            isLoading = isLoading,
            enabled = phone.trim().length >= 7,
            onClick = { focusManager.clearFocus(); onNext() }
        )
    }
}

// ─── Paso 2/5: Verificar OTP ──────────────────────────────────────────────────
@Composable
private fun StepOtpVerify(
    title: String,
    subtitle: String,
    devCode: String?,
    isLoading: Boolean,
    error: String?,
    onVerify: (String) -> Unit,
    onResend: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = RegSubText)

        if (devCode != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = RegYellow.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.DeveloperMode, null, tint = RegYellow, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Modo Desarrollo — Código: $devCode",
                        color = RegYellow,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        RegTextField(
            value = code,
            onValueChange = { if (it.length <= 6) code = it },
            label = "Código de verificación",
            icon = Icons.Outlined.Lock,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { if (code.length >= 4) onVerify(code) }),
            error = error
        )

        RegButton(
            text = "Verificar",
            isLoading = isLoading,
            enabled = code.length >= 4,
            onClick = { onVerify(code) }
        )

        TextButton(onClick = onResend, modifier = Modifier.fillMaxWidth()) {
            Text("¿No recibiste el código? Reenviar", color = RegSubText, fontSize = 13.sp)
        }
    }
}

// ─── Paso 3: Datos personales ─────────────────────────────────────────────────
@Composable
private fun StepPersonalData(
    fullName: String, onFullNameChange: (String) -> Unit,
    cedula: String, onCedulaChange: (String) -> Unit,
    birthDate: String, onBirthDateChange: (String) -> Unit,
    onNext: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Necesitamos tus datos para verificar tu identidad.",
            style = MaterialTheme.typography.bodyMedium,
            color = RegSubText
        )

        RegTextField(
            value = fullName,
            onValueChange = onFullNameChange,
            label = "Nombre completo",
            icon = Icons.Outlined.Person,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) })
        )

        RegTextField(
            value = cedula,
            onValueChange = onCedulaChange,
            label = "Número de cédula",
            icon = Icons.Outlined.Badge,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) })
        )

        RegTextField(
            value = birthDate,
            onValueChange = onBirthDateChange,
            label = "Fecha de nacimiento (YYYY-MM-DD)",
            icon = Icons.Outlined.DateRange,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )

        RegButton(
            text = "Continuar",
            isLoading = false,
            enabled = fullName.trim().length >= 3 && cedula.trim().length >= 6 && birthDate.trim().length >= 8,
            onClick = onNext
        )
    }
}

// ─── Paso 4: Ingreso de email ─────────────────────────────────────────────────
@Composable
private fun StepEmailInput(
    email: String,
    onEmailChange: (String) -> Unit,
    isLoading: Boolean,
    error: String?,
    onNext: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            text = "Ingresa tu correo electrónico para recibir notificaciones.",
            style = MaterialTheme.typography.bodyMedium,
            color = RegSubText
        )

        RegTextField(
            value = email,
            onValueChange = onEmailChange,
            label = "Correo electrónico",
            icon = Icons.Outlined.Email,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); onNext() }),
            error = error
        )

        RegButton(
            text = "Enviar código",
            isLoading = isLoading,
            enabled = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches(),
            onClick = { focusManager.clearFocus(); onNext() }
        )
    }
}

// ─── Paso 6: Foto KYC ────────────────────────────────────────────────────────
@Composable
private fun StepPhotoKyc(
    selfieUri: Uri?,
    onSelfieCapture: (Uri?) -> Unit,
    idDocUri: Uri?,
    onIdDocCapture: (Uri?) -> Unit,
    isLoading: Boolean,
    error: String?,
    onFinish: () -> Unit
) {
    val galleryLauncherSelfie = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> onSelfieCapture(uri) }

    val galleryLauncherDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> onIdDocCapture(uri) }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            text = "Necesitamos verificar tu identidad. Sube una selfie y foto de tu cédula.",
            style = MaterialTheme.typography.bodyMedium,
            color = RegSubText
        )

        // Selfie
        PhotoPickerCard(
            label = "Selfie",
            subtitle = "Foto de tu cara mirando a la cámara",
            icon = Icons.Outlined.Face,
            hasPhoto = selfieUri != null,
            onClick = { galleryLauncherSelfie.launch("image/*") }
        )

        // Foto cédula
        PhotoPickerCard(
            label = "Cédula / Documento de identidad",
            subtitle = "Foto clara del frente de tu cédula",
            icon = Icons.Outlined.Badge,
            hasPhoto = idDocUri != null,
            onClick = { galleryLauncherDoc.launch("image/*") }
        )

        if (error != null) {
            Text(
                text = error,
                color = RegError,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(RegError.copy(alpha = 0.1f))
                    .padding(12.dp)
            )
        }

        RegButton(
            text = "Completar registro",
            isLoading = isLoading,
            enabled = selfieUri != null && idDocUri != null && !isLoading,
            onClick = onFinish
        )

        TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
            Text("Omitir por ahora", color = RegSubText, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PhotoPickerCard(
    label: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    hasPhoto: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasPhoto) RegSuccess.copy(alpha = 0.1f) else RegCard
        ),
        border = BorderStroke(
            1.dp,
            if (hasPhoto) RegSuccess.copy(alpha = 0.5f) else RegCardLight
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasPhoto) RegSuccess.copy(alpha = 0.2f)
                        else RegCardLight
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasPhoto) Icons.Filled.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (hasPhoto) RegSuccess else RegSubText,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (hasPhoto) RegSuccess else RegText
                )
                Text(
                    text = if (hasPhoto) "✓ Foto cargada" else subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasPhoto) RegSuccess.copy(alpha = 0.8f) else RegSubText
                )
            }
            Icon(
                imageVector = Icons.Filled.AddAPhoto,
                contentDescription = null,
                tint = if (hasPhoto) RegSuccess else RegYellow,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─── Banner modo desarrollo ───────────────────────────────────────────────────
@Composable
private fun DevModeBanner() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2000)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, RegYellow.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.DeveloperMode, null, tint = RegYellow, modifier = Modifier.size(18.dp))
            Text(
                text = "MODO DESARROLLO — OTP omitido automáticamente",
                color = RegYellow,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─── Campo de texto reutilizable ──────────────────────────────────────────────
@Composable
private fun RegTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, color = if (error != null) RegError else RegSubText, fontSize = 14.sp) },
            leadingIcon = {
                Icon(icon, null, tint = if (error != null) RegError else RegSubText, modifier = Modifier.size(20.dp))
            },
            isError = error != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RegYellow,
                unfocusedBorderColor = RegCardLight,
                errorBorderColor = RegError,
                focusedLabelColor = RegYellow,
                cursorColor = RegYellow,
                focusedTextColor = RegText,
                unfocusedTextColor = RegText,
                focusedContainerColor = RegCardLight,
                unfocusedContainerColor = RegCardLight
            )
        )
        if (error != null) {
            Text(
                text = error,
                color = RegError,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }
    }
}

// ─── Botón principal ──────────────────────────────────────────────────────────
@Composable
private fun RegButton(
    text: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (enabled && !isLoading) Brush.horizontalGradient(listOf(RegYellow, RegYellowDark))
                    else Brush.horizontalGradient(listOf(RegCardLight, RegCardLight)),
                    RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = RegYellow,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = text,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) Color(0xFF0D0D0D) else RegSubText,
                    fontSize = 15.sp
                )
            }
        }
    }
}
