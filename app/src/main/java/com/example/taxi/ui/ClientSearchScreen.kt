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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class)
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

    LaunchedEffect(tripState) {
        when (val state = tripState) {
            is TripState.Success -> {
                if (state.driver == null) {
                    Toast.makeText(context, "Buscando conductor...", Toast.LENGTH_SHORT).show()
                    viewModel.resetTrip()
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

    // Estado del bottom sheet
    val sheetState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
    )

    val hasRoute = uiState.routePoints.isNotEmpty()
    val hasDestination = uiState.destinationPoint != null
    val hasPickup = uiState.pickupPoint != null
    val isTripActive = tripState is TripState.Success && (tripState as TripState.Success).driver != null

    BottomSheetScaffold(
        scaffoldState = sheetState,
        sheetPeekHeight = if (isTripActive) 450.dp else if (hasRoute) 260.dp else 200.dp,
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
                uiState = uiState,
                tripState = tripState,
                locationGranted = locationGranted,
                onPickupQueryChange = viewModel::updatePickupQuery,
                onDestinationQueryChange = viewModel::updateDestinationQuery,
                onPickupPredictionSelect = { viewModel.selectPrediction(it, isPickup = true) },
                onDestinationPredictionSelect = { viewModel.selectPrediction(it, isPickup = false) },
                onUseMyLocation = {
                    if (locationGranted) {
                        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                            loc?.let {
                                viewModel.setPickupPoint(
                                    LocationPoint(it.latitude, it.longitude, "Mi ubicación")
                                )
                            }
                        }
                    } else {
                        permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                onConfirm = { 
                    if (hasRoute) viewModel.confirmTrip(clientId)
                },
                onCancelTrip = { viewModel.resetTrip() }
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
                    compassEnabled = true
                ),
                onMapClick = { viewModel.onMapClick(it) }
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
                    .padding(top = 16.dp, end = 16.dp)
                    .size(44.dp)
                    .zIndex(10f),
                containerColor = MapCard,
                contentColor = MapYellow,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Mi ubicación", modifier = Modifier.size(20.dp))
            }

            // ── Indicadores de puntos seleccionados (top) ────────────────────
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
@Composable
private fun BottomSheetContent(
    uiState: TaxiUiState,
    tripState: TripState,
    locationGranted: Boolean,
    onPickupQueryChange: (String) -> Unit,
    onDestinationQueryChange: (String) -> Unit,
    onPickupPredictionSelect: (PlacePrediction) -> Unit,
    onDestinationPredictionSelect: (PlacePrediction) -> Unit,
    onUseMyLocation: () -> Unit,
    onConfirm: () -> Unit,
    onCancelTrip: () -> Unit
) {
    val context = LocalContext.current

    if (tripState is TripState.Success) {
        val d = tripState.driver
        if (d != null) {
            DriverAssignedScreen(
                driver = AssignedDriverInfo(
                    driverId = d.id,
                    fullName = d.fullName,
                    phone = d.phone,
                    avatarUrl = d.avatarUrl,
                    unitNumber = d.unitNumber,
                    vehicleMake = d.vehicleMake ?: "",
                    vehicleModel = d.vehicleModel ?: "",
                    vehicleYear = d.vehicleYear,
                    vehiclePlate = d.vehiclePlate,
                    vehicleColor = d.vehicleColor ?: "gris",
                    vehicleType = d.vehicleType ?: "sedan",
                    vehiclePhotoUrl = d.vehiclePhotoUrl,
                    rating = d.rating,
                    totalTrips = d.totalTrips
                ),
                originAddress = uiState.pickupPoint?.address ?: "",
                destAddress = uiState.destinationPoint?.address ?: "",
                onCancel = onCancelTrip,
                onContact = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${d.phone}"))
                    context.startActivity(intent)
                },
                isBottomSheet = true
            )
            return
        }
    }

    val canSearch = uiState.pickupPoint != null && uiState.destinationPoint != null
    val hasRoute = uiState.routePoints.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Título
        Text(
            text = if (hasRoute) "Ruta confirmada" else "¿A dónde vamos?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MapText
        )

        // ── Campo Origen ──────────────────────────────────────────────────────
        Column {
            MapSearchField(
                value = uiState.pickupSearchQuery,
                onValueChange = onPickupQueryChange,
                placeholder = "Punto de partida",
                leadingDot = MapGreen,
                trailingContent = {
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
            )
            AnimatedVisibility(visible = uiState.pickupPredictions.isNotEmpty()) {
                PredictionsList(
                    predictions = uiState.pickupPredictions,
                    dotColor = MapGreen,
                    onSelect = onPickupPredictionSelect
                )
            }
        }

        // Línea conectora
        Row(
            modifier = Modifier.padding(start = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(5) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(MapSubText.copy(alpha = 0.4f))
                )
                Spacer(Modifier.width(2.dp))
            }
        }

        // ── Campo Destino ─────────────────────────────────────────────────────
        Column {
            MapSearchField(
                value = uiState.destinationSearchQuery,
                onValueChange = onDestinationQueryChange,
                placeholder = "¿A dónde vas?",
                leadingDot = MapRed
            )
            AnimatedVisibility(visible = uiState.destinationPredictions.isNotEmpty()) {
                PredictionsList(
                    predictions = uiState.destinationPredictions,
                    dotColor = MapRed,
                    onSelect = onDestinationPredictionSelect
                )
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

        // ── Botón confirmar ───────────────────────────────────────────────────
        val isLoading = tripState is TripState.Loading
        Button(
            onClick = onConfirm,
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
    trailingContent: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
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
        trailingIcon = trailingContent,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
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
        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
            items(predictions) { prediction ->
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
