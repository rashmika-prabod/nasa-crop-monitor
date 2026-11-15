package com.cropexpert.app.domain.model

data class RotationPlan(
    val id: String,
    val title: String,
    val summary: String,
    val crops: List<CropRecommendation> = emptyList(),
    val farmerReportedSequence: String? = null,
    val isBaseline: Boolean = false,
    val sequence: List<String> = emptyList(),
    val selectionReason: String = ""
)

fun buildRotationPlans(
    profile: FarmProfile,
    recommendations: List<CropRecommendation>
): List<RotationPlan> {
    val climateCandidates = recommendations
        .filter {
            it.rankingMode.equals("climate", ignoreCase = true) ||
                it.rankingMode.equals("rotation", ignoreCase = true)
        }
        .distinctBy { it.cropId ?: it.cropName }
    val rankedClimateCandidates = rankRotationCandidates(profile, climateCandidates)

    val previousCrop = profile.lastSeasonCropValue()
    val previousFamily = cropFamilyFor(previousCrop, climateCandidates)
    val familyDiverseCandidates = if (previousFamily == null) {
        emptyList()
    } else {
        rankedClimateCandidates.filter {
            !it.cropFamily.isNullOrBlank() &&
                !it.cropFamily.equals(previousFamily, ignoreCase = true)
        }
    }
    val nextSeasonCandidates = if (familyDiverseCandidates.isNotEmpty()) {
        familyDiverseCandidates
    } else {
        rankedClimateCandidates
    }
    val nextCrop = nextSeasonCandidates.firstOrNull()
    val sameFamilyOnly = previousFamily != null &&
        familyDiverseCandidates.isEmpty() &&
        rankedClimateCandidates.any {
            it.cropFamily.equals(previousFamily, ignoreCase = true)
        }
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
    val topPlan = RotationPlan(
        id = "short_term",
        title = "Next season · provisional crop choice",
        summary = nextCrop?.let {
            "Crop Advisor screened this crop for the current location and planting window. The family check is a simple diversity heuristic, not a validated pest or soil-health rotation model."
        } ?: "Run Crop Advisor to find a candidate crop for the next season.",
        crops = listOfNotNull(nextCrop),
        sequence = if (previousCrop.isBlank() || nextCrop == null) {
            emptyList()
        } else {
            listOf(previousCrop, nextCrop.cropName)
        },
        selectionReason = when {
            nextCrop == null -> ""
            previousFamily != null && !nextCrop.cropFamily.isNullOrBlank() &&
                !nextCrop.cropFamily.equals(previousFamily, ignoreCase = true) ->
                "Preferred a climate-screened crop from a different known family than last season, then ranked by climate fit."
            sameFamilyOnly ->
                "No climate-screened crop from a different known family was available, so the highest-ranked candidate is shown without a family break."
            previousCrop.isNotBlank() && previousFamily == null ->
                "Last season's crop family could not be matched exactly; this candidate is ranked by climate fit only."
            else -> "No previous crop was recorded; this candidate is ranked by climate fit only."
        } + priorityTieBreakNote(profile)
    )

    val laterWindowCandidates = nextCrop?.let { selected ->
        nextSeasonCandidates.drop(1)
            .filter { it.cropId != selected.cropId }
            .filter { isAfterHarvest(it.plantIn, selected.harvestBy) }
    }.orEmpty()
    val laterCrop = nextCrop?.cropFamily?.takeIf(String::isNotBlank)?.let { selectedFamily ->
        laterWindowCandidates.firstOrNull {
            !it.cropFamily.isNullOrBlank() &&
                !it.cropFamily.equals(selectedFamily, ignoreCase = true)
        }
    }
    val diversePlan = RotationPlan(
        id = "return_to_previous",
        title = "Following season · reassess after harvest",
        summary = if (nextCrop == null) {
            "After choosing and recording a crop, refresh Crop Advisor to plan the following season."
        } else if (laterCrop != null) {
            "A later planting window is present in the current forecast. Recheck it after harvest before deciding; it is not a fixed multi-year recommendation."
        } else if (laterWindowCandidates.isNotEmpty()) {
            "Later planting windows exist, but no following crop with a different known family is supported. Record the harvest and reassess before deciding what to plant next."
        } else {
            "The current screened windows do not support another crop after this harvest. Record the harvest and refresh Crop Advisor before deciding what to plant next."
        },
        crops = listOfNotNull(laterCrop),
        sequence = if (nextCrop != null && laterCrop != null) {
            listOf(nextCrop.cropName, laterCrop.cropName)
        } else {
            emptyList()
        },
        selectionReason = if (laterCrop != null) {
            "This crop has a different known family and a Crop Advisor planting window after ${nextCrop.harvestBy}; confirm updated field conditions after harvest."
        } else if (laterWindowCandidates.isNotEmpty()) {
            "Later windows exist, but family information does not support a different-family follow-on candidate yet."
        } else {
            "No later crop is shown without a supported post-harvest planting window."
        }
    )
    return listOf(baseline, topPlan, diversePlan)
}

fun rankRotationCandidates(
    profile: FarmProfile,
    candidates: List<CropRecommendation>
): List<CropRecommendation> {
    val previousFamily = cropFamilyFor(profile.lastSeasonCropValue(), candidates)
    val familyDiverse = if (previousFamily == null) {
        emptyList()
    } else {
        candidates.filter {
            !it.cropFamily.isNullOrBlank() &&
                !it.cropFamily.equals(previousFamily, ignoreCase = true)
        }
    }
    val familyDiverseIds = familyDiverse.mapTo(mutableSetOf()) { it.cropId ?: it.cropName }
    val incomePriority = profile.priorities.any { it.equals("Income", ignoreCase = true) }
    val waterPriority = profile.priorities.any { it.equals("Conserve water", ignoreCase = true) }
    val comparator = compareByDescending<CropRecommendation> { it.suitabilityScore }
        .thenByDescending {
            if (incomePriority) it.estimatedNetProfitPerHa ?: Double.NEGATIVE_INFINITY
            else Double.NEGATIVE_INFINITY
        }
        .thenBy {
            if (waterPriority) waterNeedRank(it.waterRequirement) ?: Int.MAX_VALUE
            else Int.MAX_VALUE
        }
        .thenBy { it.rank }
        .thenBy { it.cropName }
    return if (familyDiverse.isEmpty()) {
        candidates.sortedWith(comparator)
    } else {
        familyDiverse.sortedWith(comparator) +
            candidates.filterNot { (it.cropId ?: it.cropName) in familyDiverseIds }
                .sortedWith(comparator)
    }
}

private fun cropFamilyFor(cropName: String, candidates: List<CropRecommendation>): String? {
    val normalizedName = normalizeCropName(cropName)
    if (normalizedName.isBlank()) return null
    return candidates.firstOrNull { candidate ->
        sequenceOf(candidate.cropName, candidate.localName, candidate.cropId)
            .filterNotNull()
            .any { normalizeCropName(it) == normalizedName }
    }?.cropFamily?.takeIf(String::isNotBlank)
}

private fun normalizeCropName(value: String): String =
    value.lowercase().filter(Char::isLetterOrDigit)

private fun waterNeedRank(description: String): Int? {
    val value = description.trim().lowercase()
    return when {
        value.startsWith("very high") -> 4
        value.startsWith("high") -> 3
        value.startsWith("moderate to high") -> 3
        value.startsWith("moderate") -> 2
        value.startsWith("low to moderate") -> 1
        value.startsWith("low") -> 0
        else -> null
    }
}

private fun priorityTieBreakNote(profile: FarmProfile): String = buildString {
    if (profile.priorities.any { it.equals("Income", ignoreCase = true) }) {
        append(" For equal climate-fit scores, the Income priority uses historical net return per hectare as a tie-breaker, not a future guarantee.")
    }
    if (profile.priorities.any { it.equals("Conserve water", ignoreCase = true) }) {
        append(" For equal climate-fit scores, the water priority uses the crop's stated water-need band as a tie-breaker.")
    }
    if (profile.priorities.any {
            it.equals("Soil health", ignoreCase = true) ||
                it.equals("Climate resilience", ignoreCase = true)
        }
    ) {
        append(" Soil-health and climate-resilience priorities are recorded but are not scored by this fallback.")
    }
}

private fun isAfterHarvest(plantIn: String, harvestBy: String): Boolean {
    val formatter = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.ENGLISH).apply {
        isLenient = false
    }
    val plantingMonth = formatter.parse(plantIn) ?: return false
    val harvestMonth = formatter.parse(harvestBy) ?: return false
    return plantingMonth.after(harvestMonth)
}
