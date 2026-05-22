package com.example.taxi.model

import com.google.android.libraries.places.api.model.AutocompletePrediction

data class PlacePrediction(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String
)

fun AutocompletePrediction.toPlacePrediction() = PlacePrediction(
    placeId = this.placeId,
    primaryText = this.getPrimaryText(null).toString(),
    secondaryText = this.getSecondaryText(null).toString()
)
