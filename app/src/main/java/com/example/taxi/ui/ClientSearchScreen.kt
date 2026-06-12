package com.example.taxi.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.taxi.model.LocationPoint
import com.example.taxi.viewmodel.TaxiViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.MyLocation
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.libraries.places.api.Places

@SuppressLint("MissingPermission")
@Composable
fun ClientSearchScreen(
    viewModel: TaxiViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    // Initialize places client in viewModel
    LaunchedEffect(Unit) {
        val placesClient = Places.createClient(context)
        viewModel.setPlacesClient(placesClient)
    }

    // Initial camera position
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 15f)
    }

    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            locationPermissionGranted = isGranted
        }
    )

    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Move camera to current location when permission is granted or at start
    LaunchedEffect(locationPermissionGranted) {
        if (locationPermissionGranted) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val userLatLng = LatLng(it.latitude, it.longitude)
                    viewModel.setCurrentLocationForBias(userLatLng) // Pass to ViewModel
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
                }
            }
        }
    }

    // Auto-adjust camera to fit the route
    LaunchedEffect(uiState.routePoints) {
        if (uiState.routePoints.isNotEmpty()) {
            val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.builder()
            uiState.routePoints.forEach { boundsBuilder.include(it) }
            val bounds = boundsBuilder.build()
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(bounds, 100),
                durationMs = 1000
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Google Map
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = locationPermissionGranted),
            onMapClick = { viewModel.onMapClick(it) },
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = true
            )
        ) {
            uiState.pickupPoint?.let {
                Marker(
                    state = MarkerState(position = LatLng(it.latitude, it.longitude)),
                    title = "Origen",
                    snippet = it.address
                )
            }
            uiState.destinationPoint?.let {
                Marker(
                    state = MarkerState(position = LatLng(it.latitude, it.longitude)),
                    title = "Destino",
                    snippet = it.address
                )
            }
            if (uiState.routePoints.isNotEmpty()) {
                Polyline(
                    points = uiState.routePoints,
                    color = Color.Blue,
                    width = 10f
                )
            }
        }

        // Search Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Card(
                elevation = CardDefaults.cardElevation(8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "¿A dónde vamos?",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Pickup Field
                    OutlinedTextField(
                        value = uiState.pickupSearchQuery,
                        onValueChange = { viewModel.updatePickupQuery(it) },
                        label = { Text("Punto de partida") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Blue) },
                        trailingIcon = {
                            IconButton(onClick = {
                                if (locationPermissionGranted) {
                                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                        location?.let {
                                            viewModel.

                                            setPickupPoint(LocationPoint(it.latitude, it.longitude, "Mi ubicación"))
                                        }
                                    }
                                } else {
                                    launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            }) {
                                Icon(Icons.Default.MyLocation, contentDescription = "Geolocalizar", tint = Color.Blue)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )

                    if (uiState.pickupPredictions.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            tonalElevation = 4.dp,
                            shadowElevation = 4.dp
                        ) {
                            LazyColumn {
                                items(uiState.pickupPredictions) { prediction ->
                                    ListItem(
                                        headlineContent = { Text(prediction.primaryText) },
                                        supportingContent = { Text(prediction.secondaryText) },
                                        modifier = Modifier.clickable { viewModel.selectPrediction(prediction, isPickup = true) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Destination Field
                    OutlinedTextField(
                        value = uiState.destinationSearchQuery,
                        onValueChange = { viewModel.updateDestinationQuery(it) },
                        label = { Text("¿A dónde vas?") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Red) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )

                    if (uiState.destinationPredictions.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            tonalElevation = 4.dp,
                            shadowElevation = 4.dp
                        ) {
                            LazyColumn {
                                items(uiState.destinationPredictions) { prediction ->
                                    ListItem(
                                        headlineContent = { Text(prediction.primaryText) },
                                        supportingContent = { Text(prediction.secondaryText) },
                                        modifier = Modifier.clickable { viewModel.selectPrediction(prediction, isPickup = false) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (uiState.travelDistance != null) {
                        Text(
                            text = "Distancia: ${uiState.travelDistance}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.DarkGray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    Button(
                        onClick = { 
                            // Aquí podrías navegar a una pantalla de seguimiento o mostrar un mensaje
                            println("Viaje confirmado: ${uiState.travelDistance}")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.pickupPoint != null && uiState.destinationPoint != null
                    ) {
                        Text("Confirmar Taxi")
                    }
                }
            }
        }
    }
}
