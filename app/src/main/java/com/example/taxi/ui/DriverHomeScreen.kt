package com.example.taxi.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import com.example.taxi.model.AuthModels
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.example.taxi.viewmodel.DriverViewModel
import com.example.taxi.viewmodel.DriverTripState
import android.media.RingtoneManager
import android.net.Uri
import kotlinx.coroutines.launch

// ─── Paleta ──────────────────────────────────────────────────────────────────
private val DrvDark    = Color(0xFF0A1628)
private val DrvCard    = Color(0xFF132033)
private val DrvBorder  = Color(0xFF1E3050)
private val DrvYellow  = Color(0xFFFFC107)
private val DrvYellowD = Color(0xFFE6A800)
private val DrvText    = Color(0xFFF0F6FF)
private val DrvSub     = Color(0xFF7A90B0)
private val DrvGreen   = Color(0xFF00C853)
private val DrvRed     = Color(0xFFFF5252)
private val DrvBlue    = Color(0xFF4FC3F7)

fun playDriverNotificationSound(context: android.content.Context) {
    try {
        val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ringtone = RingtoneManager.getRingtone(context, uri)
        ringtone.play()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun DriverHomeScreen(
    driver: AuthModels.UserData,
    viewModel: DriverViewModel,
    onLogout: () -> Unit
) {
    val context    = LocalContext.current
    val tripState  by viewModel.tripState.collectAsState()
    val isOnline   by viewModel.isOnline.collectAsState()
    
    // Play sound when trip is assigned
    LaunchedEffect(tripState) {
        if (tripState is DriverTripState.TripAssigned) {
            while (true) {
                playDriverNotificationSound(context)
                kotlinx.coroutines.delay(5000)
            }
        }
    }
    val queuePos   by viewModel.queuePosition.collectAsState()

    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { locationGranted = it }

    LaunchedEffect(Unit) {
        if (!locationGranted) permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        viewModel.init(driver.id)
    }

    var isNavigating by remember { mutableStateOf(false) }
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(10.5, -66.9), 14f)
    }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(tripState) {
        if (tripState is DriverTripState.Idle || tripState is DriverTripState.TripAssigned) {
            isNavigating = false
        }
    }

    // Subir ubicación cada 5 segundos si está online y manejar navegación
    LaunchedEffect(isOnline, locationGranted, isNavigating) {
        if (isOnline && locationGranted) {
            while (true) {
                fusedClient.lastLocation.addOnSuccessListener { loc ->
                    loc?.let { 
                        viewModel.updateLocation(driver.id, it.latitude, it.longitude) 
                        if (isNavigating) {
                            val pos = CameraPosition.Builder()
                                .target(LatLng(it.latitude, it.longitude))
                                .zoom(19f)
                                .tilt(60f)
                                .also { b -> if (it.hasBearing()) b.bearing(it.bearing) }
                                .build()
                            coroutineScope.launch {
                                cameraState.animate(CameraUpdateFactory.newCameraPosition(pos), 1000)
                            }
                        }
                    }
                }
                kotlinx.coroutines.delay(if (isNavigating) 3000L else 5000L)
            }
        }
    }

    var showChatDialog by remember { mutableStateOf(false) }
    if (showChatDialog && tripState is DriverTripState.TripActive) {
        val activeTrip = tripState as DriverTripState.TripActive
        TripChatDialog(
            tripId = activeTrip.tripId,
            currentUserId = driver.id,
            onDismiss = { showChatDialog = false }
        )
    }

    // Centrar mapa en mi ubicación al inicio
    LaunchedEffect(locationGranted) {
        if (locationGranted) {
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let { cameraState.move(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f)) }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DrvDark)
    ) {
        // ── MAPA ──────────────────────────────────────────────────────────────
        val driverToOriginRoute by viewModel.driverToOriginRoute.collectAsState()
        val originToDestRoute   by viewModel.originToDestRoute.collectAsState()

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(isMyLocationEnabled = locationGranted),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, compassEnabled = true)
        ) {
            // Ruta conductor → origen (azul)
            if (driverToOriginRoute.isNotEmpty()) {
                Polyline(points = driverToOriginRoute, color = DrvBlue, width = 12f, geodesic = true)
                Polyline(points = driverToOriginRoute, color = DrvBlue.copy(alpha = 0.2f), width = 22f, geodesic = true)
            }
            // Ruta origen → destino (amarillo)
            if (originToDestRoute.isNotEmpty()) {
                Polyline(points = originToDestRoute, color = DrvYellow, width = 12f, geodesic = true)
                Polyline(points = originToDestRoute, color = DrvYellow.copy(alpha = 0.2f), width = 22f, geodesic = true)
            }
            // Ajustar cámara cuando hay rutas
            LaunchedEffect(originToDestRoute, driverToOriginRoute) {
                val activeRoute = if (driverToOriginRoute.isNotEmpty()) driverToOriginRoute
                                  else if (originToDestRoute.isNotEmpty()) originToDestRoute
                                  else return@LaunchedEffect
                if (activeRoute.size >= 2 && !isNavigating) {
                    val bounds = com.google.android.gms.maps.model.LatLngBounds.builder()
                        .also { b -> activeRoute.forEach { b.include(it) } }
                        .build()
                    cameraState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120), 900)
                }
            }
        }

        // ── BARRA SUPERIOR ────────────────────────────────────────────────────
        var showHistory by remember { mutableStateOf(false) }
        var showSupportChat by remember { mutableStateOf(false) }

        if (showHistory) {
            TripHistoryDialog(userId = driver.id, isDriver = true) {
                showHistory = false
            }
        }

        if (showSupportChat && tripState is DriverTripState.TripActive) {
            val activeTrip = tripState as DriverTripState.TripActive
            SupportChatDialog(
                userId = driver.id,
                tripId = activeTrip.tripId,
                onDismiss = { showSupportChat = false }
            )
        }

        if (tripState !is DriverTripState.TripActive) {
            TopBar(
                driverName = driver.fullName ?: driver.phone ?: "Conductor",
                isOnline = isOnline,
                queuePos = queuePos,
                onToggleOnline = { viewModel.toggleOnline(driver.id) },
                onLogout = onLogout,
                onShowHistory = { showHistory = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                val activeTrip = tripState as DriverTripState.TripActive
                TripRouteCard(
                    origin = activeTrip.originAddress,
                    dest = activeTrip.destAddress,
                    distance = activeTrip.distanceKm
                )
            }
        }

        // ── PANEL INFERIOR ────────────────────────────────────────────────────
        AnimatedContent(
            targetState = tripState,
            modifier = Modifier.align(Alignment.BottomCenter),
            transitionSpec = {
                slideInVertically { it } togetherWith slideOutVertically { it }
            },
            label = "bottom_panel"
        ) { state ->
            when (state) {
                is DriverTripState.Idle -> IdlePanel(
                    isOnline = isOnline,
                    queuePos = queuePos,
                    onToggle = { viewModel.toggleOnline(driver.id) }
                )
                is DriverTripState.TripAssigned -> TripIncomingPanel(
                    trip = state,
                    onAccept = {
                        // Obtener ubicación actual del conductor para calcular ruta y pasarla
                        if (locationGranted) {
                            fusedClient.lastLocation.addOnSuccessListener { loc ->
                                val lat = loc?.latitude ?: 0.0
                                val lng = loc?.longitude ?: 0.0
                                viewModel.calculateDriverToOrigin(lat, lng, state.originLat, state.originLng)
                                viewModel.acceptTrip(state.tripId, lat, lng)
                            }
                        } else {
                            viewModel.acceptTrip(state.tripId, 0.0, 0.0)
                        }
                    },
                    onReject = { viewModel.rejectTrip(state.tripId, driver.id) }
                )
                is DriverTripState.TripActive -> TripActivePanel(
                    trip = state,
                    isNavigating = isNavigating,
                    onNotifyArrival = { viewModel.notifyArrival(state.tripId) },
                    onFinish = { viewModel.finishTrip(state.tripId, driver.id) },
                    onChat = { showChatDialog = true },
                    onToggleNavigation = { isNavigating = !isNavigating },
                    onSupportChat = { showSupportChat = true },
                    driverId = driver.id
                )
            }
        }

        // ── FAB MI UBICACIÓN ─────────────────────────────────────────────────
        FloatingActionButton(
            onClick = {
                if (locationGranted) {
                    fusedClient.lastLocation.addOnSuccessListener { loc ->
                        loc?.let { cameraState.move(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 16f)) }
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 280.dp)
                .size(46.dp),
            containerColor = DrvCard,
            contentColor = DrvYellow,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(6.dp)
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

// ─── Barra superior ───────────────────────────────────────────────────────────
@Composable
private fun TopBar(
    driverName: String,
    isOnline: Boolean,
    queuePos: Int?,
    onToggleOnline: () -> Unit,
    onLogout: () -> Unit,
    onShowHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Nombre + estado
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(DrvCard.copy(alpha = 0.95f))
                .border(1.dp, DrvBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) DrvGreen else DrvSub)
            )
            Column {
                Text(driverName, fontWeight = FontWeight.Bold, color = DrvText, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (isOnline) "En línea" else "Fuera de línea",
                    color = if (isOnline) DrvGreen else DrvSub, fontSize = 11.sp)
            }
            if (queuePos != null && isOnline) {
                VerticalDivider(color = DrvBorder, modifier = Modifier.height(28.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TURNO", fontSize = 9.sp, color = DrvSub, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                    Text("#$queuePos", fontSize = 16.sp, color = DrvYellow, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        // Botones acción
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallIconBtn(
                icon = Icons.Filled.History,
                tint = DrvYellow,
                onClick = onShowHistory
            )
            SmallIconBtn(
                icon = if (isOnline) Icons.Filled.PowerSettingsNew else Icons.Outlined.PowerSettingsNew,
                tint = if (isOnline) DrvGreen else DrvSub,
                onClick = onToggleOnline
            )
            SmallIconBtn(icon = Icons.Filled.Logout, tint = DrvRed, onClick = onLogout)
        }
    }
}

@Composable
private fun SmallIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(DrvCard.copy(alpha = 0.95f))
            .border(1.dp, DrvBorder, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

// ─── Panel: Esperando (Idle) ──────────────────────────────────────────────────
@Composable
private fun IdlePanel(isOnline: Boolean, queuePos: Int?, onToggle: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "idle_anim")
    val glowAlpha by infiniteTransition.animateFloat(
        0.3f, 0.8f,
        animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(DrvCard)
            .border(1.dp, DrvBorder, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Handle
        Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(DrvBorder))

        if (!isOnline) {
            // Estado offline
            Icon(Icons.Filled.PowerSettingsNew, null, tint = DrvSub, modifier = Modifier.size(48.dp))
            Text("Estás fuera de línea", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = DrvText)
            Text("Actívate para recibir solicitudes de viaje", color = DrvSub, fontSize = 13.sp)

            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(DrvGreen, Color(0xFF00E676))), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color(0xFF001A00), modifier = Modifier.size(22.dp))
                        Text("Activarme", fontWeight = FontWeight.ExtraBold, color = Color(0xFF001A00), fontSize = 16.sp)
                    }
                }
            }
        } else {
            // Estado online - esperando
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                Box(
                    Modifier.size(80.dp).clip(CircleShape)
                        .background(DrvGreen.copy(alpha = glowAlpha * 0.15f))
                )
                Box(
                    Modifier.size(60.dp).clip(CircleShape)
                        .background(DrvGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.DirectionsCar, null, tint = DrvGreen, modifier = Modifier.size(32.dp))
                }
            }

            Text("Esperando solicitudes...", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = DrvText)

            if (queuePos != null) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DrvYellow.copy(alpha = 0.08f))
                        .border(1.dp, DrvYellow.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("#$queuePos", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = DrvYellow)
                    Column {
                        Text("Tu posición en la cola", fontWeight = FontWeight.SemiBold, color = DrvText)
                        Text("Cuando llegues al turno #1 el sistema te asignará el próximo viaje",
                            fontSize = 12.sp, color = DrvSub)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, DrvRed.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Filled.Logout, null, tint = DrvRed, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Salir de Cola (Viaje Externo)", color = DrvRed, fontWeight = FontWeight.Bold)
                }
            }

            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = DrvYellow,
                strokeWidth = 2.5.dp
            )
        }
    }
}

// ─── Panel: Viaje entrante ────────────────────────────────────────────────────
@Composable
private fun TripIncomingPanel(
    trip: DriverTripState.TripAssigned,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "incoming")
    val borderAlpha by infiniteTransition.animateFloat(
        0.3f, 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "border_pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(DrvCard)
            .border(1.dp, DrvYellow.copy(alpha = borderAlpha), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(DrvYellow.copy(0.5f)).align(Alignment.CenterHorizontally))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(DrvYellow.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Notifications, null, tint = DrvYellow, modifier = Modifier.size(22.dp))
            }
            Column {
                Text("¡Nueva solicitud de viaje!", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = DrvText)
                Text("Responde antes de que expire", fontSize = 12.sp, color = DrvSub)
            }
        }

        // Origen - Destino
        TripRouteCard(origin = trip.originAddress, dest = trip.destAddress, distance = trip.distanceKm)

        // Precio estimado y método de pago
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DrvGreen.copy(0.08f))
                .border(1.dp, DrvGreen.copy(0.3f), RoundedCornerShape(12.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.AttachMoney, null, tint = DrvGreen, modifier = Modifier.size(20.dp))
                    Text("Ingreso estimado", color = DrvSub, fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Payments, null, tint = DrvYellow, modifier = Modifier.size(20.dp))
                    Text(trip.paymentMethod ?: "Efectivo Bs", color = DrvText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                trip.estimatedFare?.let { "$${"%.2f".format(it)}" } ?: "Acordar",
                fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = DrvGreen
            )
        }

        // Botones
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onReject,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, DrvRed.copy(0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DrvRed)
            ) {
                Icon(Icons.Filled.Close, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Rechazar", fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = onAccept,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(DrvYellow, DrvYellowD)), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, null, tint = DrvDark, modifier = Modifier.size(18.dp))
                        Text("Aceptar", fontWeight = FontWeight.ExtraBold, color = DrvDark, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

// ─── Panel: Viaje activo ──────────────────────────────────────────────────────
@Composable
private fun TripActivePanel(
    trip: DriverTripState.TripActive,
    isNavigating: Boolean,
    onNotifyArrival: () -> Unit,
    onFinish: () -> Unit,
    onChat: () -> Unit,
    onToggleNavigation: () -> Unit,
    onSupportChat: () -> Unit,
    driverId: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(DrvCard)
            .border(1.dp, DrvGreen.copy(0.4f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(DrvBorder).align(Alignment.CenterHorizontally))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Client info with profile picture placeholder
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(DrvBorder),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Person, contentDescription = "Cliente", tint = DrvSub, modifier = Modifier.size(24.dp))
                }
                Column {
                    Text(trip.clientName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DrvText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
                    Text("Viaje en curso", fontSize = 12.sp, color = DrvGreen, fontWeight = FontWeight.SemiBold)
                }
            }

            // Método de pago
            Column(horizontalAlignment = Alignment.End) {
                Text("Pago", color = DrvSub, fontSize = 11.sp)
                Text(
                    trip.paymentMethod ?: "Efectivo Bs",
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DrvYellow
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Botón SOS / Soporte
            Button(
                onClick = {
                    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                    scope.launch {
                        val req = com.example.taxi.model.SupportMessageRequest(
                            message = "🚨 ALERTA SOS CONDUCTOR 🚨",
                            senderRole = "driver",
                            tripId = trip.tripId,
                            type = "sos"
                        )
                        try {
                            com.example.taxi.network.RetrofitClient.apiService.sendSupportMessage(driverId, req)
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    onSupportChat()
                },
                modifier = Modifier.size(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DrvRed.copy(alpha=0.2f)),
                border = BorderStroke(1.dp, DrvRed),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("SOS", color = DrvRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            // Botón Chat
            Button(
                onClick = onChat,
                modifier = Modifier.size(54.dp), // cuadrado
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DrvCard.copy(alpha=0.5f)),
                border = BorderStroke(1.dp, DrvBorder),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Filled.Chat, "Chat", tint = DrvYellow, modifier = Modifier.size(24.dp))
            }

            // Botón Navegar
            Button(
                onClick = onToggleNavigation,
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isNavigating) DrvDark else Color.Transparent),
                border = if (isNavigating) BorderStroke(1.dp, DrvYellow) else null,
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .background(
                            if (isNavigating) SolidColor(Color.Transparent) 
                            else Brush.horizontalGradient(listOf(DrvYellow, DrvYellowD)), 
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Navigation, null, tint = if (isNavigating) DrvYellow else DrvDark, modifier = Modifier.size(20.dp))
                        Text(if (isNavigating) "Detener" else "Navegar", fontWeight = FontWeight.ExtraBold, color = if (isNavigating) DrvYellow else DrvDark, fontSize = 14.sp, maxLines = 1)
                    }
                }
            }

            // Botón Acción
            if (!trip.hasArrived) {
                Button(
                    onClick = onNotifyArrival,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DrvGreen),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Llegué", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 14.sp)
                }
            } else {
                Button(
                    onClick = onFinish,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DrvBlue),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Finalizar", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

// ─── Componente de ruta (compartido) ─────────────────────────────────────────
@Composable
private fun TripRouteCard(origin: String, dest: String, distance: Double?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DrvDark.copy(0.7f))
            .border(1.dp, DrvBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.RadioButtonChecked, null, tint = DrvGreen, modifier = Modifier.size(16.dp).padding(top = 2.dp))
            Column {
                Text("ORIGEN", fontSize = 9.sp, color = DrvSub, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                Text(origin, fontSize = 13.sp, color = DrvText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.padding(start = 7.dp).width(2.dp).height(12.dp).background(DrvBorder))
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.LocationOn, null, tint = DrvRed, modifier = Modifier.size(16.dp).padding(top = 2.dp))
            Column {
                Text("DESTINO", fontSize = 9.sp, color = DrvSub, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                Text(dest, fontSize = 13.sp, color = DrvText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (distance != null) {
            HorizontalDivider(color = DrvBorder)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Straighten, null, tint = DrvBlue, modifier = Modifier.size(14.dp))
                Text("${"%.1f".format(distance)} km", fontSize = 12.sp, color = DrvBlue, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
