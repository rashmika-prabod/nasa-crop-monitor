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
    val topCandidates = climateCandidates.take(2)
    val bridgeCrop = topCandidates.firstOrNull()
    val alternativeBridgeCrop = topCandidates.getOrNull(1)
    val longTermBridgeCrop = alternativeBridgeCrop ?: bridgeCrop
    val topPlan = RotationPlan(
        id = "short_term",
        title = "Short-term · try a candidate crop",
        summary = bridgeCrop?.let {
            "Use Crop Advisor's top climate-ranked candidate after last season's crop."
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
        title = "Longer-term · plan a return to the same crop",
        summary = alternativeBridgeCrop?.let {
            "Illustrative sequence with another Crop Advisor candidate; a second-season fit is not evaluated."
        } ?: if (bridgeCrop == null) {
            "Run Crop Advisor to explore a possible bridge crop before returning to the previous crop."
        } else {
            "Illustrative sequence using the top candidate; a second-season fit is not evaluated."
        },
        crops = listOfNotNull(longTermBridgeCrop),
        sequence = if (previousCrop.isBlank() || longTermBridgeCrop == null) {
            emptyList()
        } else {
            listOf(previousCrop, longTermBridgeCrop.cropName, previousCrop)
        }
    )
    return listOf(baseline, topPlan, diversePlan)
}
