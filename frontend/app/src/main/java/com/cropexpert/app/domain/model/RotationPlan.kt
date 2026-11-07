package com.cropexpert.app.domain.model

data class RotationPlan(
    val id: String,
    val title: String,
    val summary: String,
    val crops: List<CropRecommendation> = emptyList(),
    val farmerReportedSequence: String? = null,
    val isBaseline: Boolean = false
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

    val baseline = RotationPlan(
        id = "baseline",
        title = if (profile.recentCrops.isBlank()) "Current rotation not provided" else "Your current rotation",
        summary = if (profile.recentCrops.isBlank()) {
            "No existing crop sequence was assumed."
        } else {
            "Farmer-reported history"
        },
        farmerReportedSequence = profile.recentCrops.takeIf(String::isNotBlank),
        isBaseline = true
    )
    val topCandidates = climateCandidates.take(2)
    val topPlan = RotationPlan(
        id = "top_candidates",
        title = "Highest climate-fit candidates",
        summary = "Top crops from Crop Advisor's climate shortlist",
        crops = topCandidates
    )
    val categoryCandidates = climateCandidates
        .distinctBy { it.category.lowercase() }
        .take(3)
    val categoryMatchesTop = (
        categoryCandidates.map { it.cropId ?: it.cropName } ==
        topCandidates.map { it.cropId ?: it.cropName }
    )
    val diverseCandidates = if (categoryMatchesTop) {
        climateCandidates.drop(1).take(2)
    } else {
        categoryCandidates.ifEmpty { topCandidates }
    }
    val diversePlan = RotationPlan(
        id = "crop_mix",
        title = when {
            diverseCandidates.isEmpty() -> "More candidates needed"
            diverseCandidates.size > 2 -> "Broader crop-category mix"
            else -> "Alternative crop candidates"
        },
        summary = if (diverseCandidates.any { candidate ->
                topCandidates.none { top -> (top.cropId ?: top.cropName) == (candidate.cropId ?: candidate.cropName) }
            }) {
            "Alternative high climate-ranked crops; category variety used where available"
        } else if (diverseCandidates.isEmpty()) {
            "Crop Advisor returned only one climate-ranked candidate; no second option is invented"
        } else {
            "Next climate-ranked candidates available from Crop Advisor"
        },
        crops = diverseCandidates
    )
    return listOf(baseline, topPlan, diversePlan)
}
