package com.example.taxi.ui

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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.example.taxi.model.AuthModels
import com.example.taxi.model.UserRole
import com.example.taxi.viewmodel.AuthViewModel
import kotlin.math.cos
import kotlin.math.sin

// ─── Paleta ──────────────────────────────────────────────────────────────────
private val TaxiYellow     = Color(0xFFFFC107)
private val TaxiYellowDark = Color(0xFFE6A800)
private val TaxiBlack      = Color(0xFF0D0D0D)
private val TaxiDark       = Color(0xFF1A1A2E)
private val TaxiCard       = Color(0xFF1F2B47)
private val TaxiCardLight  = Color(0xFF253050)
private val TaxiText       = Color(0xFFF0F4FF)
private val TaxiSubText    = Color(0xFF8B9BC8)
private val TaxiError      = Color(0xFFFF6B6B)

// ─── Pantalla de Login ────────────────────────────────────────────────────────
@Composable
fun LoginScreen(
    onLoginSuccess: (AuthModels.UserData, AuthModels.TokenData) -> Unit,
    onGoToRegister: () -> Unit,
    viewModel: AuthViewModel
) {
    var phone     by remember { mutableStateOf("") }
    var password  by remember { mutableStateOf("") }
    var showPass  by remember { mutableStateOf(false) }
    var phoneErr  by remember { mutableStateOf<String?>(null) }
    var passErr   by remember { mutableStateOf<String?>(null) }

    val authState by viewModel.authState.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(authState) {
        if (authState is AuthModels.AuthResult.Success) {
            val user = (authState as AuthModels.AuthResult.Success).user
            val tokens = (authState as AuthModels.AuthResult.Success).tokens
            onLoginSuccess(user, tokens)
            viewModel.clearAuthState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TaxiDark)
    ) {
        AnimatedBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))
            LogoHeader()
            Spacer(Modifier.height(40.dp))

            // Modo desarrollo banner
            if (viewModel.DEV_MODE) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2000)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, TaxiYellow.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.DeveloperMode, null, tint = TaxiYellow, modifier = Modifier.size(18.dp))
                        Column {
                            Text("MODO DESARROLLO", color = TaxiYellow, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text("Tel: 042412345678  |  Clave: 1212", color = TaxiYellow.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Tarjeta de login
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = TaxiCard),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Iniciar Sesión",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TaxiText
                    )

                    // Campo teléfono
                    LoginTextField(
                        value = phone,
                        onValueChange = { phone = it; phoneErr = null },
                        label = "Número de teléfono",
                        icon = Icons.Outlined.Phone,
                        error = phoneErr,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) })
                    )

                    // Campo contraseña
                    LoginTextField(
                        value = password,
                        onValueChange = { password = it; passErr = null },
                        label = "Contraseña",
                        icon = Icons.Outlined.Lock,
                        error = passErr,
                        isPassword = true,
                        showPassword = showPass,
                        onTogglePassword = { showPass = !showPass },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            doLogin(phone, password, viewModel,
                                onPhoneError = { phoneErr = it },
                                onPassError = { passErr = it }
                            )
                        })
                    )

                    // Error servidor
                    AnimatedVisibility(
                        visible = authState is AuthModels.AuthResult.Error,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        val msg = (authState as? AuthModels.AuthResult.Error)?.message ?: ""
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(TaxiError.copy(alpha = 0.12f))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Warning, null, tint = TaxiError, modifier = Modifier.size(18.dp))
                            Text(msg, color = TaxiError, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // Botón login
                    val isLoading = authState is AuthModels.AuthResult.Loading
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            doLogin(phone, password, viewModel,
                                onPhoneError = { phoneErr = it },
                                onPassError = { passErr = it }
                            )
                        },
                        enabled = !isLoading,
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
                                    if (!isLoading) Brush.horizontalGradient(listOf(TaxiYellow, TaxiYellowDark))
                                    else Brush.horizontalGradient(listOf(TaxiCardLight, TaxiCardLight)),
                                    RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = TaxiYellow, strokeWidth = 2.dp)
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.Login, null, tint = TaxiBlack, modifier = Modifier.size(20.dp))
                                    Text("Ingresar", fontWeight = FontWeight.Bold, color = TaxiBlack, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Ir a registro
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("¿No tienes cuenta? ", color = TaxiSubText, fontSize = 14.sp)
                TextButton(onClick = onGoToRegister) {
                    Text(
                        "Regístrate aquí",
                        color = TaxiYellow,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(
                text = "Al continuar aceptas los Términos de Servicio\ny la Política de Privacidad",
                style = MaterialTheme.typography.bodySmall,
                color = TaxiSubText,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun doLogin(
    phone: String,
    password: String,
    viewModel: AuthViewModel,
    onPhoneError: (String) -> Unit,
    onPassError: (String) -> Unit
) {
    var valid = true
    if (phone.trim().length < 7) {
        onPhoneError("Ingresa un número de teléfono válido")
        valid = false
    }
    if (password.length < 4) {
        onPassError("La contraseña es demasiado corta")
        valid = false
    }
    if (!valid) return
    viewModel.loginByPhone(phone.trim(), password)
}

// ─── Componentes ──────────────────────────────────────────────────────────────
@Composable
private fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    error: String? = null,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, color = if (error != null) TaxiError else TaxiSubText, fontSize = 14.sp) },
            leadingIcon = { Icon(icon, null, tint = if (error != null) TaxiError else TaxiSubText, modifier = Modifier.size(20.dp)) },
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { onTogglePassword?.invoke() }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = null,
                            tint = TaxiSubText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else null,
            visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
            isError = error != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TaxiYellow,
                unfocusedBorderColor = TaxiCardLight,
                errorBorderColor = TaxiError,
                focusedLabelColor = TaxiYellow,
                cursorColor = TaxiYellow,
                focusedTextColor = TaxiText,
                unfocusedTextColor = TaxiText,
                focusedContainerColor = TaxiCardLight,
                unfocusedContainerColor = TaxiCardLight
            )
        )
        if (error != null) {
            Text(error, color = TaxiError, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
        }
    }
}

@Composable
private fun LogoHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(TaxiYellow, TaxiYellowDark), radius = 120f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.DirectionsCar, "Logo", tint = TaxiBlack, modifier = Modifier.size(44.dp))
        }
        Text("TaxiApp", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = TaxiText, fontSize = 32.sp)
        Text("Tu viaje, tu forma", style = MaterialTheme.typography.bodyMedium, color = TaxiSubText)
    }
}

@Composable
private fun AnimatedBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart),
        label = "offset"
    )
    Box(modifier = Modifier.fillMaxSize().drawBehind { drawBg(offset) })
}

private fun DrawScope.drawBg(angleDeg: Float) {
    val a = Math.toRadians(angleDeg.toDouble())
    drawCircle(
        color = Color(0xFFFFC107).copy(alpha = 0.05f), radius = size.width * 0.8f,
        center = Offset(size.width * 0.15f + (size.width * 0.05f * cos(a)).toFloat(), size.height * 0.1f + (size.height * 0.03f * sin(a)).toFloat())
    )
    drawCircle(color = Color(0xFF6C63FF).copy(alpha = 0.06f), radius = size.width * 0.6f, center = Offset(size.width * 0.9f, size.height * 0.85f))
}
