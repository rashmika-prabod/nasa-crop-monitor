package com.cropexpert.app.data.repository

import com.cropexpert.app.data.api.RetrofitClient
import com.cropexpert.app.domain.model.CropRecommendationResponse

class CropAdvisorRepository {
    suspend fun getRecommendations(
        latitude: Double,
        longitude: Double,
        baseUrl: String,
        apiKey: String
    ): CropRecommendationResponse =
        RetrofitClient.createApi(baseUrl).getCropRecommendations(latitude, longitude, apiKey)
}
