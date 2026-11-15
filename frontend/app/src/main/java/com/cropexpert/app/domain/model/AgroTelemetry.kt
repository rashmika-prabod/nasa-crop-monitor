package com.cropexpert.app.domain.model

import com.google.gson.annotations.SerializedName

data class AgroTelemetry(
    val district: String,
    @SerializedName("agroZone")
    val zone: String,
    val elevation: String,
    val soilMoisture: String,
    val soilPH: String,
    val ambientTemp: String,
    val upcomingRain: String,
    val sourceMetadata: String,
    val soilMoistureObservedAt: Long? = null,
    val ambientTempObservedAt: Long? = null,
    val forecastIssuedAt: Long? = null,
    val soilMoistureHistory: List<Float>? = emptyList(),
    val soilPHDepthProfile: List<Float>? = emptyList(),
    val ambientTempHistory: List<Float>? = emptyList(),
    val rainForecast: List<Float>? = emptyList()
)
