package com.example.taxi.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taxi.model.DriverTripInfo
import com.example.taxi.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

// ─── Estados del viaje para el conductor ─────────────────────────────────────
sealed class DriverTripState {
    object Idle : DriverTripState()

    data class TripAssigned(
        val tripId: String,
        val clientName: String,
        val originAddress: String,
        val destAddress: String,
        val distanceKm: Double?,
        val estimatedFare: Double?
    ) : DriverTripState()

    data class TripActive(
        val tripId: String,
        val clientName: String,
        val originAddress: String,
        val destAddress: String,
        val distanceKm: Double?
    ) : DriverTripState()
}

class DriverViewModel : ViewModel() {

    private val _tripState    = MutableStateFlow<DriverTripState>(DriverTripState.Idle)
    val tripState: StateFlow<DriverTripState> = _tripState.asStateFlow()

    private val _isOnline     = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _queuePosition = MutableStateFlow<Int?>(null)
    val queuePosition: StateFlow<Int?> = _queuePosition.asStateFlow()

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
                        _queuePosition.value = res.body()?.data?.queuePosition
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
    fun acceptTrip(tripId: String) {
        val current = _tripState.value
        if (current is DriverTripState.TripAssigned) {
            _tripState.value = DriverTripState.TripActive(
                tripId        = current.tripId,
                clientName    = current.clientName,
                originAddress = current.originAddress,
                destAddress   = current.destAddress,
                distanceKm    = current.distanceKm
            )
        }
    }

    // ── Rechazar viaje (vuelve a la cola) ─────────────────────────────────────
    fun rejectTrip(tripId: String, driverId: String) {
        viewModelScope.launch {
            try {
                RetrofitClient.apiService.rejectTrip(tripId)
                // Volver a ponerse al final de la cola
                RetrofitClient.apiService.addDriverToQueue(driverId)
            } catch (e: Exception) {
                Log.e("DriverViewModel", "rejectTrip error: ${e.message}")
            }
            _tripState.value = DriverTripState.Idle
        }
    }

    // ── Finalizar viaje (vuelve a la cola) ────────────────────────────────────
    fun finishTrip(tripId: String, driverId: String) {
        viewModelScope.launch {
            try {
                RetrofitClient.apiService.finishTrip(tripId)
                RetrofitClient.apiService.addDriverToQueue(driverId)
                val res = RetrofitClient.apiService.getDriverQueueStatus(driverId)
                _queuePosition.value = res.body()?.data?.queuePosition
            } catch (e: Exception) {
                Log.e("DriverViewModel", "finishTrip error: ${e.message}")
            }
            _tripState.value = DriverTripState.Idle
        }
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
                                    estimatedFare = trip.estimatedFare
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
