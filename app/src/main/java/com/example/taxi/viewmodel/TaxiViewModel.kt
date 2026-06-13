package com.example.taxi.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taxi.model.CreateTripRequest
import com.example.taxi.model.DriverData
import com.example.taxi.model.LocationPoint
import com.example.taxi.model.PlacePrediction
import com.example.taxi.model.UserRole
import com.example.taxi.model.toPlacePrediction
import com.example.taxi.model.DirectionsService
import com.example.taxi.model.decodePolyline
import com.example.taxi.BuildConfig
import com.example.taxi.network.RetrofitClient
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

import com.google.android.libraries.places.api.model.RectangularBounds

data class TaxiUiState(
    val selectedRole: UserRole? = null,
    val pickupPoint: LocationPoint? = null,
    val destinationPoint: LocationPoint? = null,
    val pickupSearchQuery: String = "",
    val destinationSearchQuery: String = "",
    val pickupPredictions: List<PlacePrediction> = emptyList(),
    val destinationPredictions: List<PlacePrediction> = emptyList(),
    val routePoints: List<LatLng> = emptyList(),
    val travelDistance: String? = null,
    val isSearching: Boolean = false
)

sealed class TripState {
    object Idle    : TripState()
    object Loading : TripState()
    data class Success(val driver: DriverData?, val tripId: String, val hasArrived: Boolean = false) : TripState()
    data class Completed(val tripId: String, val driverName: String?) : TripState()
    data class Error(val message: String) : TripState()
}

class TaxiViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TaxiUiState())
    val uiState: StateFlow<TaxiUiState> = _uiState.asStateFlow()

    private val _tripState = MutableStateFlow<TripState>(TripState.Idle)
    val tripState: StateFlow<TripState> = _tripState.asStateFlow()

    private val _driverLocation = MutableStateFlow<LatLng?>(null)
    val driverLocation: StateFlow<LatLng?> = _driverLocation.asStateFlow()
    
    private var locationPollingJob: Job? = null
    private var tripStatusPollingJob: Job? = null

    private var placesClient: PlacesClient? = null
    private var sessionToken: AutocompleteSessionToken? = null
    private val apiKey = BuildConfig.MAPS_API_KEY
    
    // Store current location for search bias
    private var currentLocation: LatLng? = null

    fun setCurrentLocationForBias(latLng: LatLng) {
        currentLocation = latLng
    }

    private val directionsService = Retrofit.Builder()
        .baseUrl("https://maps.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DirectionsService::class.java)

    fun setPlacesClient(client: PlacesClient) {
        placesClient = client
        sessionToken = AutocompleteSessionToken.newInstance()
    }

    fun onMapClick(latLng: LatLng) {
        if (_uiState.value.pickupPoint == null) {
            setPickupPoint(LocationPoint(latLng.latitude, latLng.longitude, "Punto en el mapa"))
        } else {
            setDestinationPoint(LocationPoint(latLng.latitude, latLng.longitude, "Destino en el mapa"))
        }
    }

    fun selectRole(role: UserRole) {
        _uiState.value = _uiState.value.copy(selectedRole = role)
    }

    fun updatePickupQuery(query: String) {
        _uiState.value = _uiState.value.copy(pickupSearchQuery = query)
        if (query.length > 2) {
            getPredictions(query, isPickup = true)
        } else {
            _uiState.value = _uiState.value.copy(pickupPredictions = emptyList())
        }
    }

    fun updateDestinationQuery(query: String) {
        _uiState.value = _uiState.value.copy(destinationSearchQuery = query)
        if (query.length > 2) {
            getPredictions(query, isPickup = false)
        } else {
            _uiState.value = _uiState.value.copy(destinationPredictions = emptyList())
        }
    }

    private fun getPredictions(query: String, isPickup: Boolean) {
        val requestBuilder = FindAutocompletePredictionsRequest.builder()
            .setSessionToken(sessionToken)
            .setQuery(query)

        // Add location bias if we have the current location
        currentLocation?.let { loc ->
            // Create a roughly 50km boundary around the user
            val offset = 0.5 // roughly 50km
            val bounds = RectangularBounds.newInstance(
                LatLng(loc.latitude - offset, loc.longitude - offset),
                LatLng(loc.latitude + offset, loc.longitude + offset)
            )
            requestBuilder.setLocationBias(bounds)
        }

        val request = requestBuilder.build()

        placesClient?.findAutocompletePredictions(request)
            ?.addOnSuccessListener { response ->
                val predictions = response.autocompletePredictions.map { it.toPlacePrediction() }
                if (isPickup) {
                    _uiState.value = _uiState.value.copy(pickupPredictions = predictions)
                } else {
                    _uiState.value = _uiState.value.copy(destinationPredictions = predictions)
                }
            }
            ?.addOnFailureListener { exception ->
                Log.e("TaxiViewModel", "Places API Error: ${exception.message}", exception)
            }
    }

    fun selectPrediction(prediction: PlacePrediction, isPickup: Boolean) {
        val placeFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
        val request = FetchPlaceRequest.builder(prediction.placeId, placeFields).build()

        placesClient?.fetchPlace(request)?.addOnSuccessListener { response ->
            val place = response.place
            val latLng = place.latLng
            if (latLng != null) {
                val point = LocationPoint(latLng.latitude, latLng.longitude, place.name ?: "")
                if (isPickup) {
                    setPickupPoint(point)
                } else {
                    setDestinationPoint(point)
                }
            }
        }
    }

    fun setPickupPoint(point: LocationPoint) {
        _uiState.value = _uiState.value.copy(
            pickupPoint = point,
            pickupSearchQuery = point.address,
            pickupPredictions = emptyList()
        )
        calculateRoute()
    }

    fun setDestinationPoint(point: LocationPoint) {
        _uiState.value = _uiState.value.copy(
            destinationPoint = point,
            destinationSearchQuery = point.address,
            destinationPredictions = emptyList()
        )
        calculateRoute()
    }

    private fun calculateRoute() {
        val uiState = _uiState.value
        val pickup = uiState.pickupPoint
        val dest = uiState.destinationPoint
        
        Log.d("TaxiViewModel", "calculateRoute called: pickup=$pickup, dest=$dest")
        Log.d("TaxiViewModel", "Using API Key: ${if (apiKey.isNotEmpty()) "Present (starts with ${apiKey.take(5)}...)" else "EMPTY"}")

        if (pickup != null && dest != null) {
            val origin = "${pickup.latitude},${pickup.longitude}"
            val destination = "${dest.latitude},${dest.longitude}"

            Log.d("TaxiViewModel", "Fetching directions from $origin to $destination")

            directionsService.getDirections(origin, destination, apiKey)
                .enqueue(object : Callback<com.example.taxi.model.DirectionsResponse> {
                    override fun onResponse(
                        call: Call<com.example.taxi.model.DirectionsResponse>,
                        response: Response<com.example.taxi.model.DirectionsResponse>
                    ) {
                        if (response.isSuccessful) {
                            val body = response.body()
                            Log.d("TaxiViewModel", "Directions API Status: ${body?.status}")
                            if (body?.status == "OK") {
                                val route = body.routes?.firstOrNull()
                                val points = route?.overview_polyline?.points
                                val distance = route?.legs?.firstOrNull()?.distance?.text
                                
                                Log.d("TaxiViewModel", "Route points found: ${points != null}, Distance: $distance")

                                if (points != null) {
                                    val decodedPoints = decodePolyline(points)
                                    _uiState.value = _uiState.value.copy(
                                        routePoints = decodedPoints,
                                        travelDistance = distance
                                    )
                                }
                            } else {
                                Log.e("TaxiViewModel", "Directions API Error: ${body?.status} - ${body?.error_message}")
                                _uiState.value = _uiState.value.copy(travelDistance = "Error: ${body?.status}")
                            }
                        } else {
                            Log.e("TaxiViewModel", "Directions API HTTP Error: ${response.code()} - ${response.message()}")
                            _uiState.value = _uiState.value.copy(travelDistance = "Error HTTP: ${response.code()}")
                        }
                    }

                    override fun onFailure(call: Call<com.example.taxi.model.DirectionsResponse>, t: Throwable) {
                        Log.e("TaxiViewModel", "Directions API Network Error: ${t.message}", t)
                        _uiState.value = _uiState.value.copy(travelDistance = "Error de red")
                    }
                })
        } else {
            Log.d("TaxiViewModel", "Cannot calculate route: missing pickup or destination")
        }
    }

    // ── Confirmar viaje → backend ─────────────────────────────────────────────
    fun confirmTrip(clientId: String, paymentMethod: String) {
        val pickup = _uiState.value.pickupPoint ?: return
        val dest   = _uiState.value.destinationPoint ?: return

        _tripState.value = TripState.Loading

        viewModelScope.launch {
            try {
                val request = CreateTripRequest(
                    clientId      = clientId,
                    originAddress = pickup.address,
                    originLat     = pickup.latitude,
                    originLng     = pickup.longitude,
                    destAddress   = dest.address,
                    destLat       = dest.latitude,
                    destLng       = dest.longitude,
                    distanceKm    = _uiState.value.travelDistance
                        ?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull(),
                    paymentMethod = paymentMethod
                )

                val response = RetrofitClient.apiService.createTrip(request)
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()!!.data
                    _tripState.value = TripState.Success(
                        driver = data?.driver,
                        tripId = data?.tripId ?: ""
                    )
                    
                    // Start polling driver location
                    data?.driver?.id?.let { dId ->
                        startPollingLocation(dId)
                    }
                    // Start polling trip status to detect completion
                    data?.tripId?.let { tId ->
                        val driverName = data.driver?.fullName
                        startPollingTripStatus(tId, driverName)
                    }
                } else {
                    val msg = response.body()?.message ?: "Error al confirmar el viaje"
                    _tripState.value = TripState.Error(msg)
                }
            } catch (e: Exception) {
                Log.e("TaxiViewModel", "confirmTrip error: ${e.message}", e)
                _tripState.value = TripState.Error("Error de red: ${e.message}")
            }
        }
    }

    private fun startPollingLocation(driverId: String) {
        locationPollingJob?.cancel()
        locationPollingJob = viewModelScope.launch {
            while(true) {
                try {
                    val res = RetrofitClient.apiService.getDriverLocation(driverId)
                    if (res.isSuccessful) {
                        val loc = res.body()?.data
                        if (loc != null && loc.lat != 0.0 && loc.lng != 0.0) {
                            _driverLocation.value = LatLng(loc.lat, loc.lng)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TaxiViewModel", "Error polling location: ${e.message}")
                }
                delay(3000)
            }
        }
    }

    private fun startPollingTripStatus(tripId: String, driverName: String?) {
        tripStatusPollingJob?.cancel()
        tripStatusPollingJob = viewModelScope.launch {
            while (true) {
                delay(5000)
                try {
                    val res = RetrofitClient.apiService.getTripStatus(tripId)
                    if (res.isSuccessful) {
                        val status = res.body()?.data?.status
                        if (status == "completed") {
                            locationPollingJob?.cancel()
                            _driverLocation.value = null
                            _tripState.value = TripState.Completed(
                                tripId     = tripId,
                                driverName = res.body()?.data?.driverName ?: driverName
                            )
                            break
                        } else if (status == "arrived") {
                            val currentState = _tripState.value
                            if (currentState is TripState.Success && !currentState.hasArrived) {
                                _tripState.value = currentState.copy(hasArrived = true)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TaxiViewModel", "Error polling trip status: ${e.message}")
                }
            }
        }
    }

    fun rateDriver(tripId: String, rating: Int, comment: String? = null, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                RetrofitClient.apiService.rateDriver(
                    tripId,
                    com.example.taxi.model.RatingRequest(rating, comment)
                )
            } catch (e: Exception) {
                Log.e("TaxiViewModel", "rateDriver error: ${e.message}")
            } finally {
                onDone()
            }
        }
    }

    fun resetTrip() {
        locationPollingJob?.cancel()
        tripStatusPollingJob?.cancel()
        locationPollingJob    = null
        tripStatusPollingJob  = null
        _driverLocation.value = null
        _tripState.value      = TripState.Idle
        _uiState.value        = TaxiUiState()
    }
}
