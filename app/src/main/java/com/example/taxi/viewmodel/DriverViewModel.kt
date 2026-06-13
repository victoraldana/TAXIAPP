package com.example.taxi.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taxi.BuildConfig
import com.example.taxi.model.DriverTripInfo
import com.example.taxi.model.DirectionsService
import com.example.taxi.model.decodePolyline
import com.example.taxi.network.RetrofitClient
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// ─── Estados del viaje para el conductor ─────────────────────────────────────
sealed class DriverTripState {
    object Idle : DriverTripState()

    data class TripAssigned(
        val tripId: String,
        val clientName: String,
        val originAddress: String,
        val destAddress: String,
        val distanceKm: Double?,
        val estimatedFare: Double?,
        val paymentMethod: String?,
        val originLat: Double = 0.0,
        val originLng: Double = 0.0,
        val destLat: Double = 0.0,
        val destLng: Double = 0.0
    ) : DriverTripState()

    data class TripActive(
        val tripId: String,
        val clientName: String,
        val originAddress: String,
        val destAddress: String,
        val distanceKm: Double?,
        val paymentMethod: String?,
        val originLat: Double = 0.0,
        val originLng: Double = 0.0,
        val destLat: Double = 0.0,
        val destLng: Double = 0.0,
        val hasArrived: Boolean = false
    ) : DriverTripState()
}

class DriverViewModel : ViewModel() {

    private val _tripState    = MutableStateFlow<DriverTripState>(DriverTripState.Idle)
    val tripState: StateFlow<DriverTripState> = _tripState.asStateFlow()

    private val _isOnline     = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _queuePosition = MutableStateFlow<Int?>(null)
    val queuePosition: StateFlow<Int?> = _queuePosition.asStateFlow()

    // ── Rutas del conductor en el mapa ────────────────────────────────────────
    private val _driverToOriginRoute = MutableStateFlow<List<LatLng>>(emptyList())
    val driverToOriginRoute: StateFlow<List<LatLng>> = _driverToOriginRoute.asStateFlow()

    private val _originToDestRoute = MutableStateFlow<List<LatLng>>(emptyList())
    val originToDestRoute: StateFlow<List<LatLng>> = _originToDestRoute.asStateFlow()

    private val directionsService = Retrofit.Builder()
        .baseUrl("https://maps.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DirectionsService::class.java)

    private var pollingActive = false

    // ── Inicializar: cargar estado del conductor y empezar polling ────────────
    fun init(driverId: String) {
        viewModelScope.launch {
            try {
                val res = RetrofitClient.apiService.getDriverQueueStatus(driverId)
                if (res.isSuccessful) {
                    val data = res.body()?.data
                    _isOnline.value      = data?.inQueue == true
                    _queuePosition.value = data?.queuePosition
                }
            } catch (e: Exception) {
                Log.e("DriverViewModel", "init error: ${e.message}")
            }
            startTripPolling(driverId)
        }
    }

    // ── Activar / Desactivar conductor ────────────────────────────────────────
    fun toggleOnline(driverId: String) {
        viewModelScope.launch {
            if (_isOnline.value) {
                // Salir de la cola
                try {
                    RetrofitClient.apiService.removeDriverFromQueue(driverId)
                    _isOnline.value      = false
                    _queuePosition.value = null
                } catch (e: Exception) {
                    Log.e("DriverViewModel", "removeFromQueue error: ${e.message}")
                }
            } else {
                // Entrar a la cola
                try {
                    val res = RetrofitClient.apiService.addDriverToQueue(driverId)
                    if (res.isSuccessful) {
                        _isOnline.value = true
                        _queuePosition.value = res.body()?.position
                    }
                } catch (e: Exception) {
                    Log.e("DriverViewModel", "addToQueue error: ${e.message}")
                }
            }
        }
    }

    // ── Subir ubicación en tiempo real ────────────────────────────────────────
    fun updateLocation(driverId: String, lat: Double, lng: Double) {
        viewModelScope.launch {
            try {
                RetrofitClient.apiService.updateDriverLocation(driverId, mapOf("lat" to lat, "lng" to lng))
            } catch (e: Exception) {
                Log.e("DriverViewModel", "updateLocation error: ${e.message}")
            }
        }
    }

    // ── Aceptar viaje ─────────────────────────────────────────────────────────
    fun acceptTrip(tripId: String, driverLat: Double, driverLng: Double) {
        viewModelScope.launch {
            try {
                val res = RetrofitClient.apiService.acceptTrip(tripId)
                if (res.isSuccessful) {
                    val current = _tripState.value
                    if (current is DriverTripState.TripAssigned) {
                        // Calcular ruta origen → destino
                        fetchRoute(
                            origin = "${current.originLat},${current.originLng}",
                            dest   = "${current.destLat},${current.destLng}",
                            forDriverLeg = false
                        )
                        // Limpiar ruta conductor → origen
                        _driverToOriginRoute.value = emptyList()

                        _tripState.value = DriverTripState.TripActive(
                            tripId        = current.tripId,
                            clientName    = current.clientName,
                            originAddress = current.originAddress,
                            destAddress   = current.destAddress,
                            distanceKm    = current.distanceKm,
                            paymentMethod = current.paymentMethod,
                            originLat     = current.originLat,
                            originLng     = current.originLng,
                            destLat       = current.destLat,
                            destLng       = current.destLng
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("DriverViewModel", "acceptTrip error: ${e.message}")
            }
        }
    }

    // ── Rechazar viaje (vuelve a la cola) ─────────────────────────────────────
    fun rejectTrip(tripId: String, driverId: String) {
        viewModelScope.launch {
            try {
                RetrofitClient.apiService.rejectTrip(tripId)
                // Volver a ponerse al final de la cola
                val res = RetrofitClient.apiService.addDriverToQueue(driverId)
                if (res.isSuccessful) {
                    _queuePosition.value = res.body()?.position
                }
            } catch (e: Exception) {
                Log.e("DriverViewModel", "rejectTrip error: ${e.message}")
            }
            _tripState.value = DriverTripState.Idle
        }
    }

    // ── Avisar Llegada ────────────────────────────────────────────────────────
    fun notifyArrival(tripId: String) {
        val current = _tripState.value
        if (current is DriverTripState.TripActive) {
            viewModelScope.launch {
                try {
                    RetrofitClient.apiService.notifyArrival(tripId)
                    _tripState.value = current.copy(hasArrived = true)
                } catch (e: Exception) {
                    Log.e("DriverViewModel", "notifyArrival error: ${e.message}")
                }
            }
        }
    }

    // ── Finalizar viaje (vuelve a la cola) ────────────────────────────────────
    fun finishTrip(tripId: String, driverId: String) {
        viewModelScope.launch {
            try {
                RetrofitClient.apiService.finishTrip(tripId)
                val res = RetrofitClient.apiService.addDriverToQueue(driverId)
                if (res.isSuccessful) {
                    _queuePosition.value = res.body()?.position
                }
            } catch (e: Exception) {
                Log.e("DriverViewModel", "finishTrip error: ${e.message}")
            }
            _driverToOriginRoute.value = emptyList()
            _originToDestRoute.value   = emptyList()
            _tripState.value           = DriverTripState.Idle
        }
    }

    // ── Calcular ruta via Directions API ─────────────────────────────────────
    private fun fetchRoute(origin: String, dest: String, forDriverLeg: Boolean) {
        val apiKey = BuildConfig.MAPS_API_KEY
        directionsService.getDirections(origin, dest, apiKey)
            .enqueue(object : retrofit2.Callback<com.example.taxi.model.DirectionsResponse> {
                override fun onResponse(
                    call: retrofit2.Call<com.example.taxi.model.DirectionsResponse>,
                    response: retrofit2.Response<com.example.taxi.model.DirectionsResponse>
                ) {
                    val points = response.body()?.routes?.firstOrNull()?.overview_polyline?.points
                    if (points != null) {
                        val decoded = decodePolyline(points)
                        if (forDriverLeg) _driverToOriginRoute.value = decoded
                        else             _originToDestRoute.value   = decoded
                    }
                }
                override fun onFailure(call: retrofit2.Call<com.example.taxi.model.DirectionsResponse>, t: Throwable) {
                    Log.e("DriverViewModel", "fetchRoute error: ${t.message}")
                }
            })
    }

    // ── Calcular ruta conductor → origen cuando llega un viaje ────────────────
    fun calculateDriverToOrigin(driverLat: Double, driverLng: Double, originLat: Double, originLng: Double) {
        fetchRoute(
            origin = "$driverLat,$driverLng",
            dest   = "$originLat,$originLng",
            forDriverLeg = true
        )
    }

    // ── Polling: verificar si hay viaje asignado ──────────────────────────────
    private fun startTripPolling(driverId: String) {
        if (pollingActive) return
        pollingActive = true
        viewModelScope.launch {
            while (true) {
                try {
                    if (_isOnline.value && _tripState.value is DriverTripState.Idle) {
                        val res = RetrofitClient.apiService.getPendingTrip(driverId)
                        if (res.isSuccessful) {
                            val trip = res.body()?.data
                            if (trip != null) {
                                _tripState.value = DriverTripState.TripAssigned(
                                    tripId        = trip.tripId,
                                    clientName    = trip.clientName ?: "Cliente",
                                    originAddress = trip.originAddress,
                                    destAddress   = trip.destAddress,
                                    distanceKm    = trip.distanceKm,
                                    estimatedFare = trip.estimatedFare,
                                    paymentMethod = trip.paymentMethod,
                                    originLat     = trip.originLat,
                                    originLng     = trip.originLng,
                                    destLat       = trip.destLat,
                                    destLng       = trip.destLng
                                )
                                // Pre-calcular también la ruta origen→destino
                                fetchRoute(
                                    origin = "${trip.originLat},${trip.originLng}",
                                    dest   = "${trip.destLat},${trip.destLng}",
                                    forDriverLeg = false
                                )
                            }
                            // Actualizar posición en cola
                            val queueRes = RetrofitClient.apiService.getDriverQueueStatus(driverId)
                            _queuePosition.value = queueRes.body()?.data?.queuePosition
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DriverViewModel", "polling error: ${e.message}")
                }
                delay(4000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingActive = false
    }
}
