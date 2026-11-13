package com.cropexpert.app.ui.rotation

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.cropexpert.app.domain.model.FarmProfile
import com.cropexpert.app.domain.model.CropRecommendationResponse
import com.cropexpert.app.domain.model.RotationPlan
import com.cropexpert.app.domain.model.buildRotationPlans
import com.cropexpert.app.domain.model.farmPriorities
import com.cropexpert.app.domain.model.lastSeasonCropValue
import com.cropexpert.app.domain.model.soilPHValue
import com.cropexpert.app.ui.theme.ForestGreen
import com.cropexpert.app.ui.theme.LightMint
import com.cropexpert.app.ui.theme.Typography
import java.text.NumberFormat

val FarmProfileSaver = Saver<FarmProfile, List<String>>(
    save = { profile ->
        listOf(
            profile.lastSeasonCrop,
            profile.harvestLevel,
            profile.residueManagement,
            profile.nutrientInputs,
            profile.recentCrops,
            profile.rotationYears,
            profile.waterAvailability,
            profile.soilPH,
            profile.soilTestResults,
            profile.soilTestDate,
            profile.soilTestSource,
            profile.constraints,
            profile.priorities.joinToString("|")
        )
    },
    restore = { values ->
        if (values.size != 9 && values.size != 13) {
            null
        } else {
            val offset = if (values.size == 13) 4 else 0
            FarmProfile(
                lastSeasonCrop = if (offset == 4) values[0] else "",
                harvestLevel = if (offset == 4) values[1] else "",
                residueManagement = if (offset == 4) values[2] else "",
                nutrientInputs = if (offset == 4) values[3] else "",
                recentCrops = values[offset],
                rotationYears = values[offset + 1],
                waterAvailability = values[offset + 2],
                soilPH = values[offset + 3],
                soilTestResults = values[offset + 4],
                soilTestDate = values[offset + 5],
                soilTestSource = values[offset + 6],
                constraints = values[offset + 7],
                priorities = values[offset + 8].split("|").filter(String::isNotBlank)
            )
        }
    }
)

private val supportedRotationCrops = listOf(
    "Ash Plantain", "Beetroot", "Big Onion", "Bitter Gourd", "Black Gram",
    "Brinjal", "Cabbage", "Carrot", "Cassava (Manioc)", "Cowpea",
    "Cucumber", "Foxtail Millet", "Green Gram (Mung Bean)",
    "Green & Dried Chilies", "Gotukola", "Groundnut (Peanut)", "Innala",
    "Kangkung", "Kiri Ala", "Kodo Millet", "Knol Khol", "Leeks",
    "Long Beans (Yard-long bean)", "Maize", "Mukunuwenna", "Okra",
    "Potato", "Pumpkin", "Radish", "Raja Ala", "Red Onion", "Ridge Gourd",
    "Snake Gourd", "Sorghum", "Soybean", "Sweet Potato", "Tomato",
    "Winged Beans", "Bell Pepper (Capsicum)", "Finger Millet (Kurakkan)"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmProfileScreen(
    profile: FarmProfile,
    onSave: (FarmProfile) -> Unit,
    onOpenCropAdvisor: () -> Unit
) {
    var currentProfile by remember(profile) { mutableStateOf(profile) }
    var cropHistoryExpanded by remember { mutableStateOf(false) }
    var soilTestExpanded by remember { mutableStateOf(false) }
    var constraintsExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightMint)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(
            eyebrow = "ROTATION START",
            title = "What did you grow last season?",
            subtitle = "Start with one crop. The extra details are optional."
        )
        CompactInfoBanner(
            text = "Crop Advisor will find climate-ranked crops to follow it. Soil benefits are not assessed until crop-trait data are added."
        )

        ExpandableProfileSection(
            title = "Last season's crop",
            summary = currentProfile.lastSeasonCrop.ifBlank {
                currentProfile.recentCrops
                    .split("→", ",", "->")
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .lastOrNull()
                    ?: "Choose a crop · other details optional"
            },
            expanded = cropHistoryExpanded,
            onClick = { cropHistoryExpanded = !cropHistoryExpanded },
            icon = Icons.Default.Landscape
        )
        if (cropHistoryExpanded) {
            CropSelector(
                selectedCrop = currentProfile.lastSeasonCrop,
                onCropSelected = { currentProfile = currentProfile.copy(lastSeasonCrop = it) }
            )
            Text(
                "Optional details help describe what happened in the field; skip any you don't know.",
                color = Color.Gray,
                fontSize = 12.sp
            )
            ChoiceChips(
                title = "Harvest amount",
                options = listOf("Not sure", "Low", "Average", "High"),
                selected = currentProfile.harvestLevel,
                onSelected = { currentProfile = currentProfile.copy(harvestLevel = it) }
            )
            ChoiceChips(
                title = "What happened to crop residues?",
                options = listOf("Not sure", "Mostly left", "Partly removed", "Removed"),
                selected = currentProfile.residueManagement,
                onSelected = { currentProfile = currentProfile.copy(residueManagement = it) }
            )
            ChoiceChips(
                title = "Fertilizer or organic inputs used?",
                options = listOf("Not sure", "Fertilizer", "Manure / compost", "Both", "None"),
                selected = currentProfile.nutrientInputs,
                onSelected = { currentProfile = currentProfile.copy(nutrientInputs = it) }
            )
            ProfileTextField(
                label = "Earlier crops (optional)",
                value = currentProfile.recentCrops,
                onValueChange = { currentProfile = currentProfile.copy(recentCrops = it) },
                placeholder = "e.g. rice, green gram",
                minLines = 1
            )
        }

        ExpandableProfileSection(
            title = "Soil & water",
            summary = when {
                currentProfile.soilTestResults.isNotBlank() -> "Soil test added"
                currentProfile.soilPH.isNotBlank() -> "Soil pH ${currentProfile.soilPH}"
                currentProfile.waterAvailability.isNotBlank() -> currentProfile.waterAvailability
                else -> "Optional · test results and water access"
            },
            expanded = soilTestExpanded,
            onClick = { soilTestExpanded = !soilTestExpanded },
            icon = Icons.Default.Science
        )
        if (soilTestExpanded) {
            ProfileTextField(
                label = "Water availability",
                value = currentProfile.waterAvailability,
                onValueChange = { currentProfile = currentProfile.copy(waterAvailability = it) },
                placeholder = "Rain-fed, irrigation access, seasonal limits"
            )
            ProfileTextField(
                label = "Soil pH (optional)",
                value = currentProfile.soilPH,
                onValueChange = { currentProfile = currentProfile.copy(soilPH = it) },
                placeholder = "From a soil test, 0-14",
                keyboardType = KeyboardType.Decimal,
                isError = currentProfile.soilPH.isNotBlank() && currentProfile.soilPHValue() == null,
                supportingText = if (currentProfile.soilPH.isNotBlank() && currentProfile.soilPHValue() == null) {
                    "Enter a pH value from 0 to 14."
                } else {
                    "Only pH is currently used by Crop Advisor; other nutrients are recorded but not scored."
                }
            )
            ProfileTextField(
                label = "Soil test results and units",
                value = currentProfile.soilTestResults,
                onValueChange = { currentProfile = currentProfile.copy(soilTestResults = it) },
                placeholder = "Copy values from a local soil report",
                minLines = 2
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileTextField(
                    modifier = Modifier.weight(1f),
                    label = "Test date",
                    value = currentProfile.soilTestDate,
                    onValueChange = { currentProfile = currentProfile.copy(soilTestDate = it) },
                    placeholder = "If known"
                )
                ProfileTextField(
                    modifier = Modifier.weight(1f),
                    label = "Test source",
                    value = currentProfile.soilTestSource,
                    onValueChange = { currentProfile = currentProfile.copy(soilTestSource = it) },
                    placeholder = "Lab / source"
                )
            }
        }

        ExpandableProfileSection(
            title = "Farm constraints",
            summary = currentProfile.constraints.ifBlank { "Optional · markets, labor, equipment" },
            expanded = constraintsExpanded,
            onClick = { constraintsExpanded = !constraintsExpanded },
            icon = Icons.Default.WaterDrop
        )
        if (constraintsExpanded) {
            ProfileTextField(
                label = "What could limit your crop choices?",
                value = currentProfile.constraints,
                onValueChange = { currentProfile = currentProfile.copy(constraints = it) },
                placeholder = "Markets, labor, equipment, or other needs",
                minLines = 2
            )
        }

        Text("What matters most?", fontWeight = FontWeight.Bold, color = ForestGreen)
        farmPriorities.chunked(2).forEach { rowPriorities ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowPriorities.forEach { priority ->
                    FilterChip(
                        selected = priority in currentProfile.priorities,
                        onClick = {
                            val updated = if (priority in currentProfile.priorities) {
                                currentProfile.priorities - priority
                            } else {
                                currentProfile.priorities + priority
                            }
                            currentProfile = currentProfile.copy(priorities = updated)
                        },
                        label = { Text(priority) }
                    )
                }
            }
        }

        val invalidSoilPH = currentProfile.soilPH.isNotBlank() && currentProfile.soilPHValue() == null
        Button(
            onClick = {
                onSave(currentProfile.normalized())
                onOpenCropAdvisor()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !invalidSoilPH,
            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
        ) {
            Text("Find crops to follow", color = Color.White)
        }
        Text(
            "You can change this information later. Soil tests are not required.",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = Color.Gray,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun RotationPlansScreen(
    profile: FarmProfile,
    advisorResponse: CropRecommendationResponse?,
    onEditProfile: () -> Unit,
    onOpenCropAdvisor: () -> Unit,
    onOpenPlan: (String) -> Unit
) {
    val plans = remember(profile, advisorResponse) {
        buildRotationPlans(profile, advisorResponse?.recommendations.orEmpty())
    }
    val climateCandidateCount = advisorResponse?.recommendations
        ?.count { it.rankingMode.equals("climate", ignoreCase = true) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightMint)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(
            eyebrow = "FROM LAST SEASON TO THE NEXT",
            title = "Plan what follows",
            subtitle = profile.lastSeasonCropValue().takeIf(String::isNotBlank)?.let {
                "Starting with ${it}, explore a candidate crop and a possible return."
            } ?: "Choose last season's crop in Farm Profile to build A → B → A ideas."
        )
        if (profile.lastSeasonCropValue().isBlank()) {
            CompactInfoBanner("Add the crop you grew last season in Farm Profile. The app can still show Crop Advisor candidates, but it won't invent the previous crop.")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("Last season: ${profile.lastSeasonCropValue()}", Color(0xFFE2F2E6), ForestGreen)
                climateCandidateCount?.let {
                    StatusPill("$it climate candidates", Color(0xFFFFF0D5), Color(0xFF805900))
                }
            }
        }

        StrategyCard(
            number = "BASELINE",
            plan = plans[0],
            onDetails = { onOpenPlan(plans[0].id) }
        )
        StrategyCard(
            number = "NEXT SEASON",
            plan = plans[1],
            onDetails = { onOpenPlan(plans[1].id) }
        )
        StrategyCard(
            number = "FOLLOWING SEASON",
            plan = plans[2],
            onDetails = { onOpenPlan(plans[2].id) }
        )

        if (advisorResponse == null) {
            CompactInfoBanner("Run Crop Advisor to fill in candidate B using its climate-ranked results. No replacement crops are invented here.")
        } else {
            Text(
                "Candidate B is screened for the next forecast planting window. The return to A is illustrative and has not been forecast for that later season.",
                color = Color.DarkGray,
                fontSize = 12.sp
            )
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Your focus", color = ForestGreen, fontWeight = FontWeight.Bold)
                if (profile.priorities.isEmpty()) {
                    Text("No priorities selected", color = Color.Gray, fontSize = 13.sp)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        profile.priorities.take(2).forEach { StatusPill(it, LightMint, ForestGreen) }
                    }
                    Text("Saved as context; candidate ranking does not yet score these priorities.", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
        OutlinedButton(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) {
            Text("Edit farm profile", color = ForestGreen)
        }
        Button(
            onClick = onOpenCropAdvisor,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
        ) {
            Text("Refresh crop candidates", color = Color.White)
        }
        CompactInfoBanner("Soil recovery and nutrient effects are not assessed yet. Crop-trait data are not connected, and exact nutrient changes cannot be inferred from crop name alone.")
        CompactInfoBanner("These are exploratory sequences, not validated rotations. Calendar fit, crop-family breaks, pest cycles, residue handling, and local practice still need review.")
        CompactInfoBanner("Prototype scope: Uma Oya, Sri Lanka. Crop dataset and economics may not represent other regions.")
    }
}

@Composable
fun RotationPlanDetailScreen(
    planId: String,
    profile: FarmProfile,
    advisorResponse: CropRecommendationResponse?,
    onBack: () -> Unit,
    onEditProfile: () -> Unit
) {
    val plan = buildRotationPlans(profile, advisorResponse?.recommendations.orEmpty())
        .firstOrNull { it.id == planId }
        ?: buildRotationPlans(profile, emptyList()).first()
    val baselineDetails = listOf(
        "Previous crop" to profile.lastSeasonCropValue().ifBlank { "Not provided" },
        "Baseline sequence" to if (plan.sequence.isEmpty()) {
            "Choose last season's crop to compare a repeat-crop baseline."
        } else {
            plan.sequence.joinToString(" → ")
        },
        "What this means" to "A repeat is included only as a farmer-context baseline. It is not presented as a soil-recovery strategy."
    )
    val alternativeDetails = listOf(
        "How candidates were chosen" to plan.summary,
        "Sequence" to if (plan.sequence.isEmpty()) {
            "Select last season's crop and get Crop Advisor results to complete this sequence."
        } else {
            plan.sequence.joinToString(" → ")
        },
        "Nutrient effects" to "Not assessed yet. Crop nutrient and residue-trait data are not connected; no nitrogen restoration is claimed.",
        "Important limitation" to "Crop Advisor screens candidate B for the next forecast window only. Crop A's return-season fit, crop-family breaks, pest cycles, and calendar compatibility are not validated."
    )
    val details = if (plan.isBaseline) baselineDetails else alternativeDetails
    val title = plan.title
    val candidates = plan.crops
    val statusText = if (plan.isBaseline) "Comparison baseline" else "Crop Advisor candidate"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightMint)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(
            eyebrow = "PLAN DETAILS",
            title = title,
            subtitle = plan.summary
        )
        StatusPill(statusText, Color(0xFFFFF0D5), Color(0xFF805900))
        details.forEach { (heading, body) ->
            DetailSection(heading, body)
        }
        candidates.forEach { candidate ->
            CandidateDetail(candidate)
        }
        if (!plan.isBaseline && candidates.isEmpty()) {
            CompactInfoBanner(plan.summary)
        }
        DetailSection(
            "Farmer context",
            buildString {
                append("Last season: ${profile.lastSeasonCropValue().ifBlank { "not provided" }}.")
                profile.harvestLevel.takeIf(String::isNotBlank)?.let { append(" Harvest: $it.") }
                profile.residueManagement.takeIf(String::isNotBlank)?.let { append(" Residues: $it.") }
                profile.nutrientInputs.takeIf(String::isNotBlank)?.let { append(" Inputs: $it.") }
                if (profile.soilPH.isNotBlank() || profile.soilTestResults.isNotBlank()) {
                    append(" Optional soil information provided.")
                }
            }
        )
        if (advisorResponse != null) {
            DetailSection(
                "Soil pH used by Crop Advisor",
                if (advisorResponse.soilPHUsed == null) {
                    "Not reported by the API. Farmer-entered pH may not have been applied."
                } else {
                    "${advisorResponse.soilPHSource ?: "Source not reported"} · pH ${"%.1f".format(advisorResponse.soilPHUsed)}"
                }
            )
        }
        CompactInfoBanner("Harvest, input, and residue details are optional context only; they do not yet change the candidate score.")
        OutlinedButton(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) {
            Text("Edit farm profile", color = ForestGreen)
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to comparison", color = ForestGreen)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StrategyCard(
    number: String,
    plan: RotationPlan,
    onDetails: () -> Unit
) {
    Card(
        onClick = onDetails,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(number, color = ForestGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                StatusPill(
                    text = if (plan.isBaseline) "Baseline" else "Crop Advisor",
                    background = if (plan.isBaseline) LightMint else Color(0xFFE2F2E6),
                    foreground = ForestGreen
                )
            }
            Text(plan.title, color = ForestGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(plan.summary, color = Color.Gray, fontSize = 12.sp)
            if (plan.sequence.isNotEmpty()) {
                CropSequence(plan.sequence.joinToString(" → "), false)
            } else if (plan.isBaseline) {
                StatusPill("Choose last season's crop", Color(0xFFF2F3F4), Color.DarkGray)
            } else if (plan.crops.isEmpty() || plan.sequence.isEmpty()) {
                StatusPill("Needs last crop and advisor result", Color(0xFFFFF0D5), Color(0xFF805900))
            } else {
                CropSequence(plan.sequence.joinToString(" → "), false)
            }
            plan.crops.firstOrNull()?.let { candidate ->
                Text(
                    text = buildString {
                        append("Climate fit ${candidate.suitabilityScore}%")
                        candidate.expectedProfit?.let {
                            append(" · Historical est. Rs ${NumberFormat.getNumberInstance().format(it)} /ha")
                        }
                    },
                    color = ForestGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Explore details", color = ForestGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ForestGreen)
            }
        }
    }
}

@Composable
private fun CandidateDetail(candidate: com.cropexpert.app.domain.model.CropRecommendation) {
    DetailSection(
        candidate.cropName,
        buildString {
            append("Climate fit ${candidate.suitabilityScore}% · ${candidate.plantIn} to ${candidate.harvestBy} · ${candidate.durationDays} days.")
            candidate.expectedProfit?.let {
                append("\nHistorical risk-adjusted estimate: Rs ${NumberFormat.getNumberInstance().format(it)} /ha.")
            }
            append("\nProfit evidence: ${candidate.profitConfidence} confidence · ${candidate.profitBasis}.")
            append("\nWater: ${candidate.waterRequirement}. ${candidate.suitabilityReason}")
        }
    )
}

@Composable
private fun CropSequence(sequence: String, unknown: Boolean) {
    if (unknown) {
        StatusPill("No current sequence entered", Color(0xFFF2F3F4), Color.DarkGray)
        return
    }
    val crops = sequence
        .split("→", ",", "->")
        .map(String::trim)
        .filter(String::isNotBlank)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        crops.forEachIndexed { index, crop ->
            Surface(
                color = LightMint,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    crop,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    color = ForestGreen,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            if (index < crops.lastIndex) {
                Text("›", color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ScreenHeader(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(eyebrow, color = ForestGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(title, style = MaterialTheme.typography.headlineSmall, color = ForestGreen, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color.DarkGray, fontSize = 14.sp)
    }
}

@Composable
private fun StatusPill(text: String, background: Color, foreground: Color) {
    Surface(color = background, shape = RoundedCornerShape(50)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = foreground,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CompactInfoBanner(text: String) {
    Surface(color = Color(0xFFFFF4D6), shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF805900), modifier = Modifier.size(18.dp))
            Text(text, color = Color(0xFF513D12), fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpandableProfileSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(color = LightMint, shape = RoundedCornerShape(10.dp)) {
                Icon(icon, contentDescription = null, tint = ForestGreen, modifier = Modifier.padding(9.dp).size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = ForestGreen, fontWeight = FontWeight.Bold)
                Text(
                    summary,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = ForestGreen
            )
        }
    }
}

@Composable
private fun DetailSection(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title.uppercase(), color = ForestGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
            Text(body, color = Color.DarkGray, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ProfileTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        minLines = minLines,
        isError = isError,
        supportingText = supportingText?.let { text -> { Text(text) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CropSelector(
    selectedCrop: String,
    onCropSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val customCrop = selectedCrop.takeIf { it.isNotBlank() && it !in supportedRotationCrops }.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selectedCrop.ifBlank { "Choose last season's crop" },
                    modifier = Modifier.weight(1f),
                    color = ForestGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Default.ExpandMore, contentDescription = "Show crop choices", tint = ForestGreen)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                supportedRotationCrops.forEach { crop ->
                    DropdownMenuItem(
                        text = { Text(crop) },
                        onClick = {
                            onCropSelected(crop)
                            expanded = false
                        }
                    )
                }
            }
        }
        ProfileTextField(
            label = "Or enter another crop",
            value = customCrop,
            onValueChange = onCropSelected,
            placeholder = "Crop name"
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChoiceChips(
    title: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = ForestGreen, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        options.chunked(2).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { option ->
                    FilterChip(
                        selected = selected == option,
                        onClick = { onSelected(if (selected == option) "" else option) },
                        label = { Text(option, maxLines = 1) }
                    )
                }
            }
        }
    }
}

private fun FarmProfile.normalized(): FarmProfile = copy(
    lastSeasonCrop = lastSeasonCrop.trim(),
    harvestLevel = harvestLevel.trim(),
    residueManagement = residueManagement.trim(),
    nutrientInputs = nutrientInputs.trim(),
    recentCrops = recentCrops.trim(),
    rotationYears = rotationYears.trim(),
    waterAvailability = waterAvailability.trim(),
    soilTestResults = soilTestResults.trim(),
    soilPH = soilPH.trim(),
    soilTestDate = soilTestDate.trim(),
    soilTestSource = soilTestSource.trim(),
    constraints = constraints.trim()
)

@Preview(showBackground = true, backgroundColor = 0xFFF1FAF3, heightDp = 1000)
@Composable
private fun FarmProfileScreenPreview() {
    MaterialTheme(typography = Typography) {
        FarmProfileScreen(
            profile = FarmProfile(lastSeasonCrop = "Rice"),
            onSave = {},
            onOpenCropAdvisor = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF1FAF3, heightDp = 1000)
@Composable
private fun RotationPlansScreenPreview() {
    MaterialTheme(typography = Typography) {
        RotationPlansScreen(
            profile = FarmProfile(
                lastSeasonCrop = "Rice",
                harvestLevel = "Average",
                residueManagement = "Mostly left",
                priorities = listOf("Conserve water", "Soil health")
            ),
            advisorResponse = null,
            onEditProfile = {},
            onOpenCropAdvisor = {},
            onOpenPlan = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF1FAF3, heightDp = 1000)
@Composable
private fun RotationPlanDetailScreenPreview() {
    MaterialTheme(typography = Typography) {
        RotationPlanDetailScreen(
            planId = "short_term",
            profile = FarmProfile(
                lastSeasonCrop = "Rice",
                soilTestResults = "pH 5.8; phosphorus 12 mg/kg",
                soilPH = "5.8",
                soilTestDate = "2026-03",
                soilTestSource = "Local lab"
            ),
            advisorResponse = null,
            onBack = {},
            onEditProfile = {}
        )
    }
}
