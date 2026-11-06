package com.cropexpert.app.domain.repository

import com.cropexpert.app.domain.model.AgroTelemetry
import kotlinx.coroutines.flow.Flow

interface WeatherRepository {
    /**
     * Fetches the latest agro-climatic conditions for a specific location.
     * Handles NASA SMAP, GPM, and SRTM data streams via the backend API.
     */
    fun getLatestConditions(lat: Double, lon: Double): Flow<Result<AgroTelemetry>>
}
