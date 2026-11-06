package com.cropexpert.app.data.api

import com.cropexpert.app.domain.model.AgroTelemetry
import retrofit2.http.GET
import retrofit2.http.Query

interface NasaApiService {
    @GET("analyze-farm")
    suspend fun getFarmAnalysis(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): AgroTelemetry
}
