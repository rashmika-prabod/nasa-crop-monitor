package com.cropexpert.app.domain.model

data class RotationPlan(
    val id: String,
    val title: String,
    val summary: String,
    val crops: List<CropRecommendation> = emptyList(),
    val farmerReportedSequence: String? = null,
    val isBaseline: Boolean = false,
    val sequence: List<String> = emptyList()
)

fun buildRotationPlans(
    profile: FarmProfile,
    recommendations: List<CropRecommendation>
): List<RotationPlan> {
    val climateCandidates = recommendations
        .filter { it.rankingMode.equals("climate", ignoreCase = true) }
        .distinctBy { it.cropId ?: it.cropName }
        .sortedWith(
            compareByDescending<CropRecommendation> { it.suitabilityScore }
                .thenBy { it.rank }
        )

    val previousCrop = profile.lastSeasonCropValue()
    val baseline = RotationPlan(
        id = "baseline",
        title = if (previousCrop.isBlank()) "Your last-season crop is not set" else "Repeat the last crop",
        summary = if (previousCrop.isBlank()) {
            "Choose the crop you grew last season to build rotation sequences."
        } else {
            "Baseline for comparison; repeating a crop is not presented as a soil-recovery strategy."
        },
        farmerReportedSequence = previousCrop.takeIf(String::isNotBlank),
        isBaseline = true,
        sequence = if (previousCrop.isBlank()) emptyList() else listOf(previousCrop, previousCrop)
    )
    val bridgeCrop = climateCandidates.firstOrNull()
    val topPlan = RotationPlan(
        id = "short_term",
        title = "Next season · location-fit candidate",
        summary = bridgeCrop?.let {
            "Crop Advisor found this crop suitable for the current location and planting window. Nutrient recovery and rotation fit are not yet assessed, so this is not a rotation recommendation."
        } ?: "Run Crop Advisor to find a candidate crop for the next season.",
        crops = listOfNotNull(bridgeCrop),
        sequence = if (previousCrop.isBlank() || bridgeCrop == null) {
            emptyList()
        } else {
            listOf(previousCrop, bridgeCrop.cropName)
        }
    )
    val diversePlan = RotationPlan(
        id = "return_to_previous",
        title = "Following seasons · plan after harvest",
        summary = "Later-season crops are not projected yet. Reassess after recording the next crop, harvest, soil results, and nutrient inputs.",
        crops = emptyList(),
        sequence = emptyList()
    )
    return listOf(baseline, topPlan, diversePlan)
}
