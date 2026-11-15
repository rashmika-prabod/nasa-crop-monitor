package com.cropexpert.app.domain.model

data class AgroLocationProfile(
    val district: String,
    val agroZone: String,
    val elevationMeters: Double?,
    val source: String
)
