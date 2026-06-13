package com.example.taxi.model

import com.google.gson.annotations.SerializedName

// ── Request ───────────────────────────────────────────────────────────────────
data class CreateTripRequest(
    @SerializedName("client_id")      val clientId: String,
    @SerializedName("origin_address") val originAddress: String,
    @SerializedName("origin_lat")     val originLat: Double,
    @SerializedName("origin_lng")     val originLng: Double,
    @SerializedName("dest_address")   val destAddress: String,
    @SerializedName("dest_lat")       val destLat: Double,
    @SerializedName("dest_lng")       val destLng: Double,
    @SerializedName("estimated_fare") val estimatedFare: Double? = null,
    @SerializedName("distance_km")    val distanceKm: Double? = null,
    @SerializedName("payment_method") val paymentMethod: String = "cash",
)

// ── Response ──────────────────────────────────────────────────────────────────
data class CreateTripResponse(
    val success: Boolean,
    val message: String,
    val data: TripData? = null,
)

data class TripData(
    @SerializedName("trip_id") val tripId: String,
    val driver: DriverData? = null,
)

data class DriverData(
    val id: String,
    @SerializedName("full_name")      val fullName: String,
    val phone: String,
    @SerializedName("avatar_url")     val avatarUrl: String? = null,
    @SerializedName("unit_number")    val unitNumber: String,
    @SerializedName("vehicle_make")   val vehicleMake: String? = null,
    @SerializedName("vehicle_model")  val vehicleModel: String? = null,
    @SerializedName("vehicle_year")   val vehicleYear: Int? = null,
    @SerializedName("vehicle_plate")  val vehiclePlate: String,
    @SerializedName("vehicle_color")  val vehicleColor: String? = null,
    @SerializedName("vehicle_type")   val vehicleType: String? = null,
    @SerializedName("vehicle_photo_url") val vehiclePhotoUrl: String? = null,
    val rating: Float = 5.0f,
    @SerializedName("total_trips")    val totalTrips: Int = 0,
)

data class DriverLocationResponse(
    val success: Boolean,
    val data: LocationData? = null
)

data class LocationData(
    val lat: Double,
    val lng: Double
)

// ─── Trip status (cliente hace polling) ──────────────────────────────────────
data class TripStatusResponse(
    val success: Boolean,
    val data: TripStatusData? = null
)

data class TripStatusData(
    val id: String,
    val status: String,          // "pending" | "accepted" | "completed" | "cancelled"
    @SerializedName("driver_name")   val driverName: String?,
    @SerializedName("driver_rating") val driverRating: Double?
)

// ─── Calificación del conductor ───────────────────────────────────────────────
data class RatingRequest(
    val rating: Int,
    val comment: String? = null
)

data class RatingResponse(
    val success: Boolean,
    val message: String
)

