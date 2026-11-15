package com.cropexpert.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RotationPlanTest {
    @Test
    fun prefersDifferentKnownFamilyBeforeHigherClimateFit() {
        val profile = FarmProfile(lastSeasonCrop = "Tomato")
        val recommendations = listOf(
            candidate("Tomato", "Solanaceae", 95, rank = 1),
            candidate("Cabbage", "Brassicaceae", 82, rank = 2)
        )

        val plan = buildRotationPlans(profile, recommendations)
            .first { it.id == "short_term" }

        assertEquals("Cabbage", plan.crops.single().cropName)
        assertTrue(plan.selectionReason.contains("different known family"))
    }

    @Test
    fun keepsClimateCandidateWhenNoDifferentFamilyIsAvailable() {
        val profile = FarmProfile(lastSeasonCrop = "Tomato")
        val recommendations = listOf(
            candidate("Tomato", "Solanaceae", 95, rank = 1),
            candidate("Bell Pepper", "Solanaceae", 82, rank = 2)
        )

        val plan = buildRotationPlans(profile, recommendations)
            .first { it.id == "short_term" }

        assertEquals("Tomato", plan.crops.single().cropName)
        assertTrue(plan.selectionReason.contains("No climate-screened crop"))
    }

    @Test
    fun unknownPreviousCropDoesNotPretendFamilyWasChecked() {
        val profile = FarmProfile(lastSeasonCrop = "Unmapped crop")
        val recommendations = listOf(
            candidate("Cabbage", "Brassicaceae", 82, rank = 2),
            candidate("Tomato", "Solanaceae", 95, rank = 1)
        )

        val plan = buildRotationPlans(profile, recommendations)
            .first { it.id == "short_term" }

        assertEquals("Tomato", plan.crops.single().cropName)
        assertTrue(plan.selectionReason.contains("could not be matched exactly"))
    }

    @Test
    fun usesClimateCandidatesOnly() {
        val rotationCandidate = candidate("Cabbage", "Brassicaceae", 80, rank = 1)
            .copy(rankingMode = "rotation")
        val profitCandidate = candidate("Tomato", "Solanaceae", 99, rank = 1)
            .copy(rankingMode = "profit")

        val plan = buildRotationPlans(
            FarmProfile(),
            listOf(rotationCandidate, profitCandidate)
        ).first { it.id == "short_term" }

        assertEquals("Cabbage", plan.crops.single().cropName)
    }

    @Test
    fun usesIncomePriorityOnlyToBreakClimateFitTies() {
        val profile = FarmProfile(priorities = listOf("Income"))
        val recommendations = listOf(
            candidate("Tomato", "Solanaceae", 90, rank = 1, profit = 100.0),
            candidate("Cabbage", "Brassicaceae", 90, rank = 2, profit = 200.0),
            candidate("Carrot", "Apiaceae", 95, rank = 3, profit = 50.0)
        )

        val ranked = rankRotationCandidates(profile, recommendations)

        assertEquals("Carrot", ranked.first().cropName)
        assertEquals("Cabbage", ranked[1].cropName)
    }

    @Test
    fun usesConserveWaterPriorityOnlyToBreakClimateFitTies() {
        val profile = FarmProfile(priorities = listOf("Conserve water"))
        val recommendations = listOf(
            candidate("Ash Plantain", "Musaceae", 90, rank = 1, water = "High; regular water"),
            candidate("Sorghum", "Poaceae", 90, rank = 2, water = "Low to moderate; drought tolerant"),
            candidate("Cabbage", "Brassicaceae", 95, rank = 3, water = "Moderate; consistent moisture")
        )

        val ranked = rankRotationCandidates(profile, recommendations)

        assertEquals("Cabbage", ranked.first().cropName)
        assertEquals("Sorghum", ranked[1].cropName)
    }

    @Test
    fun onlyShowsLaterCandidateWhenWindowFollowsHarvest() {
        val profile = FarmProfile(lastSeasonCrop = "Maize")
        val recommendations = listOf(
            candidate("Cabbage", "Brassicaceae", 95, "Jan 2027", "Apr 2027", rank = 1),
            candidate("Carrot", "Apiaceae", 90, "Feb 2027", "May 2027", rank = 2),
            candidate("Tomato", "Solanaceae", 85, "Jun 2027", "Sep 2027", rank = 3)
        )

        val laterPlan = buildRotationPlans(profile, recommendations)
            .first { it.id == "return_to_previous" }

        assertEquals("Tomato", laterPlan.crops.single().cropName)
        assertTrue(laterPlan.selectionReason.contains("after Apr 2027"))
    }

    @Test
    fun requestsReassessmentWhenNoSupportedLaterWindowExists() {
        val profile = FarmProfile(lastSeasonCrop = "Maize")
        val recommendations = listOf(
            candidate("Cabbage", "Brassicaceae", 95, "Jan 2027", "Apr 2027", rank = 1),
            candidate("Carrot", "Apiaceae", 90, "Feb 2027", "May 2027", rank = 2)
        )

        val laterPlan = buildRotationPlans(profile, recommendations)
            .first { it.id == "return_to_previous" }

        assertTrue(laterPlan.crops.isEmpty())
        assertTrue(laterPlan.summary.contains("Record the harvest"))
        assertFalse(laterPlan.sequence.isNotEmpty())
    }

    @Test
    fun doesNotSuggestSameFamilyAsFollowingCrop() {
        val profile = FarmProfile(lastSeasonCrop = "Unmapped crop")
        val recommendations = listOf(
            candidate("Cabbage", "Brassicaceae", 95, "Jan 2027", "Apr 2027", rank = 1),
            candidate("Radish", "Brassicaceae", 90, "Jun 2027", "Sep 2027", rank = 2)
        )

        val laterPlan = buildRotationPlans(profile, recommendations)
            .first { it.id == "return_to_previous" }

        assertTrue(laterPlan.crops.isEmpty())
        assertTrue(laterPlan.summary.contains("different known family"))
    }

    private fun candidate(
        name: String,
        family: String?,
        fit: Int,
        plantIn: String = "Jan 2027",
        harvestBy: String = "Apr 2027",
        rank: Int,
        profit: Double? = null,
        water: String = "Moderate"
    ) = CropRecommendation(
        cropName = name,
        localName = null,
        category = "Vegetable",
        suitabilityScore = fit,
        expectedProfit = profit,
        profitBasis = "test",
        profitConfidence = "low",
        confidenceLevel = null,
        imageUrl = null,
        waterRequirement = water,
        waterRequirementVsPaddy = null,
        durationDays = 100,
        suitabilityReason = "test",
        plantIn = plantIn,
        harvestBy = harvestBy,
        forecastRainMm = 100.0,
        idealRainMinMm = 50.0,
        idealRainMaxMm = 200.0,
        forecastTemperatureC = 25.0,
        idealTemperatureMinC = 20.0,
        idealTemperatureMaxC = 30.0,
        rank = rank,
        suitabilityRankCorrelation = null,
        topFiveOverlap = null,
        rankingMode = "climate",
        netProfitPerHa = profit,
        cropId = name.lowercase().replace(" ", "_"),
        cropFamily = family
    )
}
