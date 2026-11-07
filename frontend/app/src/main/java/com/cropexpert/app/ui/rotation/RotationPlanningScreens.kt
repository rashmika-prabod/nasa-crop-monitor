package com.cropexpert.app.ui.rotation

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import com.cropexpert.app.domain.model.FarmProfile
import com.cropexpert.app.domain.model.CropRecommendationResponse
import com.cropexpert.app.domain.model.RotationPlan
import com.cropexpert.app.domain.model.buildRotationPlans
import com.cropexpert.app.domain.model.farmPriorities
import com.cropexpert.app.domain.model.soilPHValue
import com.cropexpert.app.ui.theme.ForestGreen
import com.cropexpert.app.ui.theme.LightMint
import com.cropexpert.app.ui.theme.Typography

val FarmProfileSaver = Saver<FarmProfile, List<String>>(
    save = { profile ->
        listOf(
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
        if (values.size != 9) {
            null
        } else {
            FarmProfile(
                recentCrops = values[0],
                rotationYears = values[1],
                waterAvailability = values[2],
                soilPH = values[3],
                soilTestResults = values[4],
                soilTestDate = values[5],
                soilTestSource = values[6],
                constraints = values[7],
                priorities = values[8].split("|").filter(String::isNotBlank)
            )
        }
    }
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
            eyebrow = "OPTIONAL SETUP",
            title = "Your farm, your priorities",
            subtitle = "Add what you know. Skip anything you don't."
        )
        CompactInfoBanner(
            text = "A soil-test pH can refine Crop Advisor's pH fit. Other nutrients are recorded but are not scored yet."
        )

        ExpandableProfileSection(
            title = "Crop history",
            summary = currentProfile.recentCrops.ifBlank { "Optional · helps set your baseline" },
            expanded = cropHistoryExpanded,
            onClick = { cropHistoryExpanded = !cropHistoryExpanded },
            icon = Icons.Default.Landscape
        )
        if (cropHistoryExpanded) {
            ProfileTextField(
                label = "Recent crops, oldest to newest",
                value = currentProfile.recentCrops,
                onValueChange = { currentProfile = currentProfile.copy(recentCrops = it) },
                placeholder = "e.g. rice, mung bean, rice",
                minLines = 2
            )
            ProfileTextField(
                label = "Years of history (optional)",
                value = currentProfile.rotationYears,
                onValueChange = { currentProfile = currentProfile.copy(rotationYears = it) },
                placeholder = "Number of years",
                keyboardType = KeyboardType.Number
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
            Text("Get crop candidates", color = Color.White)
        }
        Text("You can update this information later.", modifier = Modifier.align(Alignment.CenterHorizontally), color = Color.Gray, fontSize = 12.sp)
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
            eyebrow = "FIELD SHIFT · UMA OYA PILOT",
            title = "Rotation ideas",
            subtitle = if (advisorResponse == null) {
                "Get crop candidates to compare options for your field"
            } else {
                "Built from your Crop Advisor climate shortlist"
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill("${plans.size} options", Color(0xFFE2F2E6), ForestGreen)
            StatusPill(
                if (climateCandidateCount == null) {
                    "No crop results yet"
                } else {
                    "$climateCandidateCount candidates"
                },
                Color(0xFFFFF0D5),
                Color(0xFF805900)
            )
        }

        StrategyCard(
            number = "1",
            plan = plans[0],
            onDetails = { onOpenPlan(plans[0].id) }
        )
        StrategyCard(
            number = "2",
            plan = plans[1],
            onDetails = { onOpenPlan(plans[1].id) }
        )
        StrategyCard(
            number = "3",
            plan = plans[2],
            onDetails = { onOpenPlan(plans[2].id) }
        )

        if (advisorResponse == null) {
            CompactInfoBanner("Get Crop Advisor results to populate the two candidate options. No crops are invented when results are missing.")
        } else {
            val source = when {
                advisorResponse.soilPHSource.isNullOrBlank() -> "Soil pH source not reported"
                advisorResponse.soilPHUsed != null -> "${advisorResponse.soilPHSource}: pH ${"%.1f".format(advisorResponse.soilPHUsed)}"
                else -> advisorResponse.soilPHSource
            }
            StatusPill(source, LightMint, ForestGreen)
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
                    Text("Used as farmer context; Crop Advisor does not yet score these priorities.", color = Color.Gray, fontSize = 12.sp)
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
        CompactInfoBanner("Candidates come from the individual-crop climate ranking. Their order is not a validated rotation; planting overlap, crop-family breaks, and local practice still need checking.")
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
        "Baseline" to (plan.farmerReportedSequence ?: "No crop history entered; no current sequence is assumed."),
        "Evidence" to "This baseline records farmer input only and is not scored by Crop Advisor.",
        "Next step" to "Add or update crop history in Farm Profile."
    )
    val alternativeDetails = listOf(
        "How candidates were chosen" to plan.summary,
        "Important limitation" to "These are crop candidates, not a validated multi-season rotation. Crop-family breaks, pest-cycle effects, and crop-to-crop calendar compatibility have not been checked."
    )
    val details = if (plan.isBaseline) baselineDetails else alternativeDetails
    val title = plan.title
    val candidates = plan.crops
    val statusText = if (plan.isBaseline) "Farmer baseline" else "Crop Advisor climate shortlist"
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
            "Your field data",
            if (profile.soilPH.isBlank() && profile.soilTestResults.isBlank()) {
                "No soil test entered. Satellite observations cannot replace a local nutrient test."
            } else {
                "Farmer-provided${profile.soilPH.takeIf(String::isNotBlank)?.let { " pH $it" }.orEmpty()}" +
                    profile.soilTestResults.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty() +
                    listOf(profile.soilTestDate, profile.soilTestSource)
                        .filter(String::isNotBlank)
                        .joinToString(prefix = " · ")
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
        CompactInfoBanner("Satellite data describe an area and time period, not every condition in an individual field.")
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
                Text("OPTION $number", color = ForestGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                StatusPill(
                    text = if (plan.isBaseline) "Baseline" else "Crop Advisor",
                    background = if (plan.isBaseline) LightMint else Color(0xFFE2F2E6),
                    foreground = ForestGreen
                )
            }
            Text(plan.title, color = ForestGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(plan.summary, color = Color.Gray, fontSize = 12.sp)
            if (plan.isBaseline) {
                CropSequence(plan.farmerReportedSequence.orEmpty(), plan.farmerReportedSequence.isNullOrBlank())
            } else if (plan.crops.isEmpty()) {
                StatusPill("No distinct candidate available", Color(0xFFFFF0D5), Color(0xFF805900))
            } else {
                CropSequence(plan.crops.joinToString(" → ") { it.cropName }, false)
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
        "Climate fit ${candidate.suitabilityScore}% · ${candidate.plantIn} to ${candidate.harvestBy} · ${candidate.durationDays} days\n" +
            "Water: ${candidate.waterRequirement}\n${candidate.suitabilityReason}"
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

private fun FarmProfile.normalized(): FarmProfile = copy(
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
            profile = FarmProfile(),
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
                recentCrops = "Rice → mung bean → rice",
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
            planId = "legume",
            profile = FarmProfile(
                recentCrops = "Rice → mung bean → rice",
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
