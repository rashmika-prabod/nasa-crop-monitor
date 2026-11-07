package com.cropexpert.app.domain.model

data class FarmProfile(
    val recentCrops: String = "",
    val rotationYears: String = "",
    val waterAvailability: String = "",
    val soilPH: String = "",
    val soilTestResults: String = "",
    val soilTestDate: String = "",
    val soilTestSource: String = "",
    val constraints: String = "",
    val priorities: List<String> = emptyList()
)

fun FarmProfile.soilPHValue(): Double? =
    soilPH.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..14.0 }

val farmPriorities = listOf(
    "Soil health",
    "Conserve water",
    "Climate resilience",
    "Income"
)
