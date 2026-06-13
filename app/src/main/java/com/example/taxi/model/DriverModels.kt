package com.example.taxi.model

import com.google.gson.annotations.SerializedName

// ─── Respuesta de estado de cola del conductor ────────────────────────────────
data class DriverQueueStatusResponse(
    val success: Boolean,
    val data: DriverQueueData? = null
)

data class DriverQueueData(
    @SerializedName("in_queue")       val inQueue: Boolean,
    @SerializedName("queue_position") val queuePosition: Int?,
    @SerializedName("is_active")      val isActive: Boolean
)

// ─── Respuesta de agregar a la cola ──────────────────────────────────────────
data class QueueAddResponse(
    val success: Boolean,
    val message: String,
    val position: Int? = null
)

// ─── Respuesta de viaje pendiente para conductor ──────────────────────────────
data class PendingTripResponse(
    val success: Boolean,
    val data: DriverTripInfo? = null
)

data class DriverTripInfo(
    @SerializedName("trip_id")         val tripId: String,
    @SerializedName("client_name")     val clientName: String?,
    @SerializedName("origin_address")  val originAddress: String,
    @SerializedName("dest_address")    val destAddress: String,
    @SerializedName("origin_lat")      val originLat: Double = 0.0,
    @SerializedName("origin_lng")      val originLng: Double = 0.0,
    @SerializedName("dest_lat")        val destLat: Double = 0.0,
    @SerializedName("dest_lng")        val destLng: Double = 0.0,
    @SerializedName("distance_km")     val distanceKm: Double?,
    @SerializedName("estimated_fare")  val estimatedFare: Double?,
    val status: String
)
