package com.cropexpert.app.data.api

import com.cropexpert.app.domain.model.AgroTelemetry
import com.cropexpert.app.domain.model.AgroLocationProfile
import com.cropexpert.app.domain.model.CropRecommendationResponse
import com.cropexpert.app.domain.model.FarmTerrainRequest
import com.cropexpert.app.domain.model.FarmTerrainResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface NasaApiService {
    @GET("location-profile")
    suspend fun getLocationProfile(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): AgroLocationProfile

    @GET("analyze-farm")
    suspend fun getFarmAnalysis(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): AgroTelemetry

    @GET("recommend-crops")
    suspend fun getCropRecommendations(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("soil_ph") soilPH: Double?,
        @Query("field_boundary") fieldBoundary: String?,
        @Header("X-API-Key") apiKey: String
    ): CropRecommendationResponse

    @POST("farm-terrain")
    suspend fun getFarmTerrain(
        @Body request: FarmTerrainRequest
    ): FarmTerrainResponse
}
