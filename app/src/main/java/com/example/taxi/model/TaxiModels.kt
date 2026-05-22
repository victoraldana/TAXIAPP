package com.example.taxi.model

enum class UserRole {
    CLIENT,
    DRIVER
}

data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val address: String = ""
)
