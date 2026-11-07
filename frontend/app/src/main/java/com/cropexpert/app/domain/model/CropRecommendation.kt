package com.cropexpert.app.domain.model

import com.google.gson.annotations.SerializedName

data class CropRecommendationResponse(
    val district: String,
    val agroZone: String,
    val plantingWindow: String,
    val rainfallMaeMm: Double?,
    val rainfallSkillVsClimatology: Double?,
    val rainfallModel: String,
    val temperatureMaeC: Double?,
    val temperatureSkillVsClimatology: Double?,
    val temperatureModel: String,
    val suitabilityRankCorrelation: Double?,
    val topFiveOverlap: Int?,
    val recommendations: List<CropRecommendation> = emptyList()
)

data class CropRecommendation(
    val cropName: String,
    val localName: String?,
    val category: String,
    val suitabilityScore: Int,
    val expectedProfit: Double?,
    val profitBasis: String,
    val profitConfidence: String,
    val confidenceLevel: String?,
    val imageUrl: String?,
    val waterRequirement: String,
    val waterRequirementVsPaddy: String?,
    val durationDays: Int,
    val suitabilityReason: String,
    val plantIn: String,
    val harvestBy: String,
    val forecastRainMm: Double,
    val idealRainMinMm: Double,
    val idealRainMaxMm: Double,
    val forecastTemperatureC: Double,
    val idealTemperatureMinC: Double,
    val idealTemperatureMaxC: Double,
    val rank: Int,
    @SerializedName("suitabilityRankCorrelation")
    val suitabilityRankCorrelation: Double?,
    val topFiveOverlap: Int?,
    val rankingMode: String = "climate",
    val profitPerDay: Double? = null,
    val cropId: String? = null
)
