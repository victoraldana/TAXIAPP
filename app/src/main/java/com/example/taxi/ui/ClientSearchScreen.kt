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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.example.taxi.model.LocationPoint
import com.example.taxi.model.PlacePrediction
import com.example.taxi.viewmodel.TaxiUiState
import com.example.taxi.viewmodel.TaxiViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.maps.android.compose.*
import android.widget.Toast
import com.example.taxi.ui.AssignedDriverInfo
import com.example.taxi.ui.DriverAssignedScreen
import com.example.taxi.viewmodel.TripState
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.example.taxi.R

// ─── Paleta ──────────────────────────────────────────────────────────────────
private val MapDark      = Color(0xFF0F1923)
private val MapCard      = Color(0xFF1A2535)
private val MapCardLight = Color(0xFF1E2D40)
private val MapBorder    = Color(0xFF253348)
private val MapText      = Color(0xFFF0F6FF)
private val MapSubText   = Color(0xFF7A90B0)
private val MapYellow    = Color(0xFFFFC107)
private val MapYellowDk  = Color(0xFFE6A800)
private val MapGreen     = Color(0xFF4CAF50)
private val MapRed       = Color(0xFFEF5350)
private val MapBlue      = Color(0xFF4FC3F7)

// ─── Funciones de Sonido y Marcador ──────────────────────────────────────────
fun playNotificationSound(context: android.content.Context) {
    try {
        val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ringtone = RingtoneManager.getRingtone(context, uri)
        ringtone.play()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun playClaxonSound(context: android.content.Context) {
    try {
        val mp = MediaPlayer.create(context, R.raw.claxon)
        mp.setOnCompletionListener { it.release() }
        mp.start()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun bitmapDescriptorFromVector(context: android.content.Context, vectorResId: Int): BitmapDescriptor? {
    return ContextCompat.getDrawable(context, vectorResId)?.run {
        setBounds(0, 0, intrinsicWidth, intrinsicHeight)
        val bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap))
        BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}

fun distanceBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
    val results = FloatArray(1)
    android.location.Location.distanceBetween(lat1, lng1, lat2, lng2, results)
    return results[0]
}

enum class MapSelectionMode { ORIGIN, DESTINATION }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@SuppressLint("MissingPermission")
@Composable
fun ClientSearchScreen(viewModel: TaxiViewModel, clientId: String, onTripFinished: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    val tripState by viewModel.tripState.collectAsState()

    val context = LocalContext.current

    // ── Pantalla de calificación cuando el viaje se completó ──────────────────
    if (tripState is TripState.Completed) {
        val state = tripState as TripState.Completed
        RatingScreen(
            driverName  = state.driverName,
            tripId      = state.tripId,
            onRatingSubmitted = { rating, comment ->
                viewModel.rateDriver(state.tripId, rating, comment) {
                    viewModel.resetTrip()
                    onTripFinished()
                }
            },
            onSkip = {
                viewModel.resetTrip()
                onTripFinished()
            }
        )
        return
    }

    // ── Errores y sin conductor disponible ────────────────────────────────────
    var hasAnnouncedArrival by remember { mutableStateOf(false) }
    var hasAnnouncedAssigned by remember { mutableStateOf(false) }
    var showArrivalDialog by remember { mutableStateOf(false) }
    var showChatDialog by remember { mutableStateOf(false) }

    LaunchedEffect(clientId) {
        viewModel.initClient(clientId)
    }

    LaunchedEffect(tripState) {
        when (val state = tripState) {
            is TripState.Success -> {
                if (state.driver == null) {
                    // El servidor nos asignó un conductor pero estamos esperando que acepte
                    // No reseteamos el viaje, solo notificamos
                } else {
                    if (!hasAnnouncedAssigned) {
                        playNotificationSound(context)
                        hasAnnouncedAssigned = true
                    }
                    if (state.hasArrived && !hasAnnouncedArrival) {
                        playClaxonSound(context)
                        showArrivalDialog = true
                        hasAnnouncedArrival = true
                    }
                }
            }
            is TripState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetTrip()
            }
            is TripState.Idle -> {
                hasAnnouncedArrival = false
                hasAnnouncedAssigned = false
                showArrivalDialog = false
            }
            else -> {}
        }
    }
    
    // ── Detectar si el taxi llegó al origen por cercanía (Fallback) ──────────
    val drvLoc by viewModel.driverLocation.collectAsState()
    val pkp = uiState.pickupPoint
    LaunchedEffect(drvLoc) {
        if (drvLoc != null && pkp != null && tripState is TripState.Success && !hasAnnouncedArrival) {
            val dist = distanceBetween(drvLoc!!.latitude, drvLoc!!.longitude, pkp.latitude, pkp.longitude)
            if (dist < 50f) { // Menos de 50 metros
                playClaxonSound(context) // Claxon local
                showArrivalDialog = true
                hasAnnouncedArrival = true
            }
        }
    }
    var showSupportChat by remember { mutableStateOf(false) }

    if (showArrivalDialog) {
        AlertDialog(
            onDismissRequest = { showArrivalDialog = false },
            title = { Text("¡Tu taxi ha llegado!", fontWeight = FontWeight.ExtraBold) },
            text = { Text("El conductor ya se encuentra en el punto de encuentro. Por favor, acércate al vehículo.") },
            confirmButton = {
                TextButton(onClick = { showArrivalDialog = false }) {
                    Text("Aceptar", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MapCard,
            titleContentColor = MapText,
            textContentColor = MapSubText
        )
    }

    if (showChatDialog && tripState is TripState.Success) {
        TripChatDialog(
            tripId = (tripState as TripState.Success).tripId,
            currentUserId = clientId,
            onDismiss = { showChatDialog = false }
        )
    }

    if (showSupportChat && tripState is TripState.Success) {
        SupportChatDialog(
            userId = clientId,
            tripId = (tripState as TripState.Success).tripId,
            onDismiss = { showSupportChat = false }
        )
    }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Inicializar Places
    LaunchedEffect(Unit) {
        viewModel.setPlacesClient(Places.createClient(context))
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 15f)
    }

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
    }

    LaunchedEffect(locationGranted) {
        if (locationGranted) {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let {
                    val ll = LatLng(it.latitude, it.longitude)
                    viewModel.setCurrentLocationForBias(ll)
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(ll, 16f))
                }
            }
        }
    }

    // Ajustar cámara a la ruta
    LaunchedEffect(uiState.routePoints) {
        if (uiState.routePoints.isNotEmpty()) {
            val builder = com.google.android.gms.maps.model.LatLngBounds.builder()
            uiState.routePoints.forEach { builder.include(it) }
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(builder.build(), 120), 900
            )
        }
    }

    // Al restaurar un viaje activo, centrar cámara en el origen del viaje
    LaunchedEffect(tripState) {
        if (tripState is TripState.Success) {
            val pickup = uiState.pickupPoint
            if (pickup != null) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(pickup.latitude, pickup.longitude), 15f), 800
                )
            }
        }
    }

    // Estado del bottom sheet
    val sheetState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
    )

    var mapSelectionMode by remember { mutableStateOf<MapSelectionMode?>(null) }

    val hasRoute = uiState.routePoints.isNotEmpty()
    val hasDestination = uiState.destinationPoint != null
    val hasPickup = uiState.pickupPoint != null
    val isTripActive = tripState is TripState.Success && (tripState as TripState.Success).driver != null
    // Cualquier estado activo (esperando o en viaje) bloquea el flujo de búsqueda
    val isInActiveTrip = tripState is TripState.Success || tripState is TripState.Loading

    val isImeVisible = WindowInsets.isImeVisible
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Auto-expandir el bottom sheet cuando el cliente tiene un viaje activo
    LaunchedEffect(isInActiveTrip) {
        if (isInActiveTrip) {
            sheetState.bottomSheetState.expand()
        }
    }

    BottomSheetScaffold(
        scaffoldState = sheetState,
        sheetPeekHeight = if (mapSelectionMode != null) 0.dp else if (isImeVisible) screenHeight else if (isInActiveTrip) 240.dp else if (hasRoute) 450.dp else 200.dp,
        sheetDragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MapBorder)
                )
            }
        },
        sheetContainerColor = MapCard,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color.Transparent,
        sheetContent = {
            BottomSheetContent(
                clientId = clientId,
                uiState = uiState,
                tripState = tripState,
                locationGranted = locationGranted,
                onPickupQueryChange = { viewModel.updatePickupQuery(it) },
                onDestinationQueryChange = { viewModel.updateDestinationQuery(it) },
                onPickupPredictionSelect = { viewModel.selectPickupPrediction(it, context) },
                onDestinationPredictionSelect = { viewModel.selectDestinationPrediction(it, context) },
                onSelectOnMap = { mapSelectionMode = it },
                onUseMyLocation = {
                    if (locationGranted) {
                        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                            loc?.let {
                                viewModel.setPointFromMap(LatLng(it.latitude, it.longitude), context, isPickup = true)
                            }
                        }
                    } else {
                        permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                onConfirm = { paymentMethod -> 
                    if (hasRoute) viewModel.confirmTrip(clientId, paymentMethod)
                },
                onCancelTrip = { viewModel.resetTrip() },
                onChat = { showChatDialog = true },
                onSupportChat = { showSupportChat = true }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── MAPA a pantalla completa ──────────────────────────────────────
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = locationGranted,
                    mapType = MapType.NORMAL
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false,
                    compassEnabled = true,
                    scrollGesturesEnabled = !isInActiveTrip,
                    zoomGesturesEnabled = !isInActiveTrip,
                    tiltGesturesEnabled = !isInActiveTrip
                ),
                onMapClick = { if (!isInActiveTrip) viewModel.onMapClick(it) }
            ) {
                // Marcador origen
                uiState.pickupPoint?.let { pt ->
                    Marker(
                        state = MarkerState(LatLng(pt.latitude, pt.longitude)),
                        title = "Origen",
                        snippet = pt.address
                    )
                }
                // Marcador destino
                uiState.destinationPoint?.let { pt ->
                    Marker(
                        state = MarkerState(LatLng(pt.latitude, pt.longitude)),
                        title = "Destino",
                        snippet = pt.address
                    )
                }
                // Ruta
                if (uiState.routePoints.isNotEmpty()) {
                    Polyline(
                        points = uiState.routePoints,
                        color = MapYellow,
                        width = 14f,
                        geodesic = true
                    )
                    // Sombra de la ruta
                    Polyline(
                        points = uiState.routePoints,
                        color = MapYellow.copy(alpha = 0.25f),
                        width = 22f,
                        geodesic = true
                    )
                }

                // ── Marcador del taxi en tiempo real ──
                val drvLoc by viewModel.driverLocation.collectAsState()
                drvLoc?.let { loc ->
                    val carIcon = bitmapDescriptorFromVector(context, R.drawable.ic_car_white)
                    Marker(
                        state = MarkerState(loc),
                        title = "Tu Taxi",
                        icon = carIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)
                    )
                    // Update camera to follow taxi slightly
                    LaunchedEffect(loc) {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLng(loc), 500)
                    }
                }
            }

            // ── Botón Mi Ubicación flotante ───────────────────────────────────
            FloatingActionButton(
                onClick = {
                    if (locationGranted) {
                        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                            loc?.let {
                                cameraPositionState.move(
                                    CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 16f)
                                )
                            }
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 12.dp, end = 12.dp)
                    .size(44.dp)
                    .zIndex(10f),
                containerColor = MapCard,
                contentColor = MapYellow,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Mi ubicación", modifier = Modifier.size(20.dp))
            }

            if (isTripActive) {
                val d = (tripState as TripState.Success).driver!!
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth()
                        .zIndex(10f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MapCard.copy(alpha = 0.96f)),
                    border = BorderStroke(1.dp, MapBorder),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Foto real del vehículo del conductor
                        if (!d.vehiclePhotoUrl.isNullOrEmpty()) {
                            coil.compose.AsyncImage(
                                model = d.vehiclePhotoUrl,
                                contentDescription = "Foto del vehículo",
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                modifier = Modifier
                                    .size(80.dp, 54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Vehicle3DView(
                                vehicleType = d.vehicleType ?: "sedan",
                                color = d.vehicleColor ?: "gris",
                                modifier = Modifier.size(80.dp, 54.dp)
                            )
                        }
                        // Info unidad
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("UNIDAD ${d.unitNumber}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = MapYellow, letterSpacing = 0.5.sp)
                                val statusStr = when ((tripState as TripState.Success).status) {
                                    "in_progress" -> "EN VIAJE"
                                    "arrived"     -> "LLEGANDO"
                                    else          -> "EN CAMINO"
                                }
                                val statusColor = if (statusStr == "EN VIAJE") MapBlue else if (statusStr == "LLEGANDO") MapYellow else MapGreen
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(statusColor.copy(0.15f))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) { Text(statusStr, fontSize = 9.sp, color = statusColor, fontWeight = FontWeight.Bold) }
                            }
                            Text(
                                "${d.vehicleMake ?: ""} ${d.vehicleModel ?: ""} ${d.vehicleYear ?: ""}".trim(),
                                fontSize = 13.sp, color = MapText, fontWeight = FontWeight.SemiBold, maxLines = 1
                            )
                            Text(d.vehiclePlate, fontSize = 12.sp, color = MapSubText, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                        // Chip de color
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(parseClientVehicleColor(d.vehicleColor ?: "gris"))
                                .border(2.dp, MapBorder, CircleShape)
                        )
                    }
                }
            } else {
                // Indicadores de puntos normales cuando no hay viaje
                AnimatedVisibility(
                    visible = hasPickup || hasDestination,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically(),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 16.dp, end = 72.dp)
                        .zIndex(10f)
                ) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MapCard.copy(alpha = 0.95f)),
                        border = BorderStroke(1.dp, MapBorder)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            if (hasPickup) {
                                RoutePointRow(
                                    color = MapGreen,
                                    label = uiState.pickupPoint?.address ?: "Origen",
                                    icon = Icons.Filled.RadioButtonChecked
                                )
                            }
                            if (hasPickup && hasDestination) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 9.dp)
                                        .width(2.dp)
                                        .height(10.dp)
                                        .background(MapBorder)
                                )
                            }
                            if (hasDestination) {
                                RoutePointRow(
                                    color = MapRed,
                                    label = uiState.destinationPoint?.address ?: "Destino",
                                    icon = Icons.Filled.LocationOn
                                )
                            }
                        }
                    }
                }
            }

            // ── Overlay para Selección en Mapa ───────────────────────────────
            AnimatedVisibility(
                visible = mapSelectionMode != null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = "Marcador de selección",
                    tint = if (mapSelectionMode == MapSelectionMode.ORIGIN) MapGreen else MapRed,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(bottom = 24.dp) // Offset para que la punta señale el centro exacto
                )
            }

            AnimatedVisibility(
                visible = mapSelectionMode != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .padding(horizontal = 24.dp)
            ) {
                Button(
                    onClick = {
                        val target = cameraPositionState.position.target
                        viewModel.setPointFromMap(
                            target, 
                            context, 
                            isPickup = mapSelectionMode == MapSelectionMode.ORIGIN
                        )
                        mapSelectionMode = null
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MapDark),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(8.dp)
                ) {
                    Text("Confirmar ubicación", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ─── Fila de punto de ruta ─────────────────────────────────────────────────
@Composable
private fun RoutePointRow(
    color: Color,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MapText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp)
        )
    }
}

// ─── Contenido del Bottom Sheet ───────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomSheetContent(
    clientId: String,
    uiState: TaxiUiState,
    tripState: TripState,
    locationGranted: Boolean,
    onPickupQueryChange: (String) -> Unit,
    onDestinationQueryChange: (String) -> Unit,
    onPickupPredictionSelect: (PlacePrediction) -> Unit,
    onDestinationPredictionSelect: (PlacePrediction) -> Unit,
    onSelectOnMap: (MapSelectionMode) -> Unit,
    onUseMyLocation: () -> Unit,
    onConfirm: (String) -> Unit,
    onCancelTrip: () -> Unit,
    onChat: () -> Unit = {},
    onSupportChat: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val destFocusRequester = remember { FocusRequester() }
    var focusedField by remember { mutableStateOf<MapSelectionMode?>(null) }

    if (tripState is TripState.Success) {
        val d = tripState.driver
        if (d != null) {
            // ── Panel compacto en el bottom sheet ───────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Ruta origen → destino
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MapCardLight)
                        .border(1.dp, MapBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(MapGreen))
                            Text(
                                uiState.pickupPoint?.address ?: "Origen",
                                fontSize = 12.sp, color = MapText, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(Modifier.padding(start = 3.dp).width(2.dp).height(10.dp).background(MapBorder))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(MapRed))
                            Text(
                                uiState.destinationPoint?.address ?: "Destino",
                                fontSize = 12.sp, color = MapText, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // Costo
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                        Text("COSTO", fontSize = 9.sp, color = MapSubText, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (uiState.travelDistance != null) "Bs ${uiState.travelDistance}" else "—",
                            fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MapYellow
                        )
                    }
                }

                // Info conductor + botones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Avatar
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        if (!d.avatarUrl.isNullOrEmpty()) {
                            coil.compose.AsyncImage(
                                model = d.avatarUrl,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.size(44.dp).clip(CircleShape)
                                    .border(2.dp, MapYellow, CircleShape)
                            )
                        } else {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape)
                                    .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(MapYellow, Color(0xFFE6A800)))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(d.fullName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                    fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0D0D0D))
                            }
                        }
                    }
                    // Nombre y rating
                    Column(modifier = Modifier.weight(1f)) {
                        Text(d.fullName, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = MapText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Star, null, tint = MapYellow, modifier = Modifier.size(12.dp))
                            Text("${d.rating}", fontSize = 12.sp, color = MapText, fontWeight = FontWeight.SemiBold)
                            Text("· ${d.totalTrips} viajes", fontSize = 12.sp, color = MapSubText)
                        }
                    }
                    // Botón Chat
                    IconButton(
                        onClick = onChat,
                        modifier = Modifier.size(44.dp).clip(CircleShape)
                            .background(MapCardLight)
                            .border(1.dp, MapBorder, CircleShape)
                    ) {
                        Icon(Icons.Filled.Chat, null, tint = MapYellow, modifier = Modifier.size(20.dp))
                    }
                    // Botón Llamar
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${d.phone}"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(44.dp).clip(CircleShape)
                            .background(MapGreen)
                    ) {
                        Icon(Icons.Filled.Phone, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                // Fila de botones secundarios: Cancelar y SOS
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onSupportChat,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MapBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MapSubText)
                    ) {
                        Text("Cancelar / Soporte", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = {
                            // Acción SOS
                            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                            scope.kotlinx.coroutines.launch {
                                val req = SupportMessageRequest(
                                    message = "🚨 ALERTA SOS 🚨 El cliente ha activado el botón de emergencia.",
                                    senderRole = "client",
                                    tripId = (tripState as TripState.Success).tripId,
                                    type = "sos"
                                )
                                try {
                                    RetrofitClient.apiService.sendSupportMessage(clientId, req)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            onSupportChat()
                        },
                        modifier = Modifier.width(80.dp).height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MapRed)
                    ) {
                        Text("SOS", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            }
            return
        } else {
            // Mostrar UI de "Esperando confirmación del conductor..."
            Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(color = MapYellow)
                Text("Esperando que el conductor acepte...", color = MapText, fontWeight = FontWeight.Bold)
                OutlinedButton(
                    onClick = onCancelTrip,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MapRed)
                ) {
                    Text("Cancelar viaje", color = MapRed)
                }
            }
            return
        }
    }

    val canSearch = uiState.pickupPoint != null && uiState.destinationPoint != null
    val hasRoute = uiState.routePoints.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Título
        Text(
            text = if (hasRoute) "Ruta confirmada" else "¿A dónde vamos?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MapText
        )

        AnimatedVisibility(visible = focusedField != null && !hasRoute) {
            TextButton(
                onClick = { 
                    focusedField?.let { onSelectOnMap(it) }
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MapBlue)
            ) {
                Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Seleccionar en el mapa", fontWeight = FontWeight.Bold)
            }
        }

        // ── Campo Origen ──────────────────────────────────────────────────────
        Column {
            MapSearchField(
                value = uiState.pickupSearchQuery,
                onValueChange = onPickupQueryChange,
                placeholder = "Punto de partida",
                leadingDot = MapGreen,
                enabled = !hasRoute,
                modifier = Modifier.onFocusChanged { if (it.isFocused && !hasRoute) focusedField = MapSelectionMode.ORIGIN },
                trailingContent = {
                    if (!hasRoute) {
                        IconButton(
                            onClick = onUseMyLocation,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.MyLocation,
                                contentDescription = "Mi ubicación",
                                tint = MapBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            )
            AnimatedVisibility(visible = uiState.pickupSearchQuery.isNotEmpty()) {
                if (uiState.pickupPredictions.isNotEmpty()) {
                    PredictionsList(
                        predictions = uiState.pickupPredictions,
                        dotColor = MapGreen,
                        onSelect = {
                            onPickupPredictionSelect(it)
                            destFocusRequester.requestFocus()
                        }
                    )
                }
            }
        }

    

        // ── Campo Destino ─────────────────────────────────────────────────────
        Column {
            MapSearchField(
                value = uiState.destinationSearchQuery,
                onValueChange = onDestinationQueryChange,
                placeholder = "¿A dónde vas?",
                leadingDot = MapRed,
                enabled = !hasRoute,
                modifier = Modifier
                    .focusRequester(destFocusRequester)
                    .onFocusChanged { if (it.isFocused && !hasRoute) focusedField = MapSelectionMode.DESTINATION }
            )
            AnimatedVisibility(visible = uiState.destinationSearchQuery.isNotEmpty()) {
                if (uiState.destinationPredictions.isNotEmpty()) {
                    PredictionsList(
                        predictions = uiState.destinationPredictions,
                        dotColor = MapRed,
                        onSelect = {
                            onDestinationPredictionSelect(it)
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    )
                }
            }
        }

        // ── Info de ruta ──────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = hasRoute && uiState.travelDistance != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MapCardLight),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MapYellow.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Route, null, tint = MapYellow, modifier = Modifier.size(18.dp))
                        Column {
                            Text("Distancia estimada", style = MaterialTheme.typography.labelSmall, color = MapSubText)
                            Text(
                                uiState.travelDistance ?: "",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MapText
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.DirectionsCar, null, tint = MapBlue, modifier = Modifier.size(18.dp))
                        Column {
                            Text("Precio estimado", style = MaterialTheme.typography.labelSmall, color = MapSubText)
                            Text("$2.50", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MapText)
                        }
                    }
                }
            }
        }

        // ── Selección de método de pago ───────────────────────────────────────
        var selectedPaymentMethod by remember { mutableStateOf("Efectivo Bs") }
        val paymentMethods = listOf("Pago móvil", "Efectivo Bs", "Transferencia", "Divisas", "Otros")
        var expandedPayment by remember { mutableStateOf(false) }

        AnimatedVisibility(
            visible = hasRoute,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            ExposedDropdownMenuBox(
                expanded = expandedPayment,
                onExpandedChange = { expandedPayment = !expandedPayment },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedPaymentMethod,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Método de pago", color = MapSubText) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPayment) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MapYellow,
                        unfocusedBorderColor = MapBorder,
                        focusedTextColor = MapText,
                        unfocusedTextColor = MapText,
                        focusedContainerColor = MapCardLight,
                        unfocusedContainerColor = MapCardLight
                    ),
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                ExposedDropdownMenu(
                    expanded = expandedPayment,
                    onDismissRequest = { expandedPayment = false },
                    modifier = Modifier.background(MapCardLight)
                ) {
                    paymentMethods.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption, color = MapText) },
                            onClick = {
                                selectedPaymentMethod = selectionOption
                                expandedPayment = false
                            }
                        )
                    }
                }
            }
        }

        // ── Botón confirmar ───────────────────────────────────────────────────
        val isLoading = tripState is TripState.Loading
        Button(
            onClick = { onConfirm(selectedPaymentMethod) },
            enabled = canSearch && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
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
                        if (canSearch && !isLoading)
                            Brush.horizontalGradient(listOf(MapYellow, MapYellowDk))
                        else
                            Brush.horizontalGradient(listOf(MapBorder, MapBorder)),
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (canSearch && !isLoading) Icons.Filled.DirectionsCar else Icons.Outlined.DirectionsCar,
                        contentDescription = null,
                        tint = if (canSearch && !isLoading) MapDark else MapSubText,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isLoading) "Confirmando..." else if (hasRoute) "Confirmar viaje" else "Buscar taxi",
                        fontWeight = FontWeight.Bold,
                        color = if (canSearch && !isLoading) MapDark else MapSubText,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

// ─── Campo de búsqueda del mapa ───────────────────────────────────────────────
@Composable
private fun MapSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingDot: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        placeholder = {
            Text(placeholder, color = MapSubText, fontSize = 14.sp)
        },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(leadingDot)
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (enabled && value.isNotEmpty()) {
                    IconButton(
                        onClick = { onValueChange("") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Borrar",
                            tint = MapSubText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (trailingContent != null) {
                    trailingContent()
                }
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = leadingDot.copy(alpha = 0.7f),
            unfocusedBorderColor = MapBorder,
            focusedTextColor = MapText,
            unfocusedTextColor = MapText,
            cursorColor = leadingDot,
            focusedContainerColor = MapCardLight,
            unfocusedContainerColor = MapCardLight,
            focusedPlaceholderColor = MapSubText,
            unfocusedPlaceholderColor = MapSubText
        ),
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = MapText)
    )
}

// ─── Lista de predicciones ────────────────────────────────────────────────────
@Composable
private fun PredictionsList(
    predictions: List<PlacePrediction>,
    dotColor: Color,
    onSelect: (PlacePrediction) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MapCardLight),
        border = BorderStroke(1.dp, MapBorder)
    ) {
        Column(modifier = Modifier.heightIn(max = 220.dp)) {
            predictions.forEach { prediction ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(prediction) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(dotColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Place,
                            contentDescription = null,
                            tint = dotColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = prediction.primaryText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MapText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = prediction.secondaryText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MapSubText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (predictions.last() != prediction) {
                    HorizontalDivider(color = MapBorder, thickness = 0.5.dp)
                }
            }
        }
    }
}

private fun parseClientVehicleColor(color: String): androidx.compose.ui.graphics.Color = when (color.lowercase().trim()) {
    "blanco", "white"           -> androidx.compose.ui.graphics.Color(0xFFF5F5F5)
    "negro", "black"            -> androidx.compose.ui.graphics.Color(0xFF263238)
    "rojo", "red"               -> androidx.compose.ui.graphics.Color(0xFFE53935)
    "azul", "blue"              -> androidx.compose.ui.graphics.Color(0xFF1E88E5)
    "gris", "gray", "grey"      -> androidx.compose.ui.graphics.Color(0xFF78909C)
    "plata", "silver"           -> androidx.compose.ui.graphics.Color(0xFFB0BEC5)
    "verde", "green"            -> androidx.compose.ui.graphics.Color(0xFF43A047)
    else                        -> androidx.compose.ui.graphics.Color(0xFF78909C)
}
