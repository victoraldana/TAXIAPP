package com.example.taxi.viewmodel

import androidx.lifecycle.ViewModel
import com.example.taxi.model.LocationPoint
import com.example.taxi.model.PlacePrediction
import com.example.taxi.model.UserRole
import com.example.taxi.model.toPlacePrediction
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.model.Place
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TaxiUiState(
    val selectedRole: UserRole? = null,
    val pickupPoint: LocationPoint? = null,
    val destinationPoint: LocationPoint? = null,
    val pickupSearchQuery: String = "",
    val destinationSearchQuery: String = "",
    val pickupPredictions: List<PlacePrediction> = emptyList(),
    val destinationPredictions: List<PlacePrediction> = emptyList(),
    val routePoints: List<LatLng> = emptyList(),
    val isSearching: Boolean = false
)

class TaxiViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TaxiUiState())
    val uiState: StateFlow<TaxiUiState> = _uiState.asStateFlow()

    private var placesClient: PlacesClient? = null

    fun setPlacesClient(client: PlacesClient) {
        placesClient = client
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
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .build()

        placesClient?.findAutocompletePredictions(request)?.addOnSuccessListener { response ->
            val predictions = response.autocompletePredictions.map { it.toPlacePrediction() }
            if (isPickup) {
                _uiState.value = _uiState.value.copy(pickupPredictions = predictions)
            } else {
                _uiState.value = _uiState.value.copy(destinationPredictions = predictions)
            }
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
        
        if (pickup != null && dest != null) {
            val points = listOf(
                LatLng(pickup.latitude, pickup.longitude),
                LatLng(dest.latitude, dest.longitude)
            )
            _uiState.value = _uiState.value.copy(routePoints = points)
        }
    }
}
