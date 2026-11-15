package com.cropexpert.app.ui.rotation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.cropexpert.app.data.local.FarmEntity
import com.cropexpert.app.data.local.SoilSampleEntity
import com.cropexpert.app.data.api.NetworkConfig
import com.cropexpert.app.data.repository.CropAdvisorRepository
import com.cropexpert.app.BuildConfig
import com.cropexpert.app.domain.model.CropRecommendation
import com.cropexpert.app.domain.model.CropRecommendationResponse
import com.cropexpert.app.domain.model.FarmProfile
import com.cropexpert.app.domain.model.FarmTerrainState
import com.cropexpert.app.domain.model.RotationPlan
import com.cropexpert.app.domain.model.buildRotationPlans
import com.cropexpert.app.domain.model.farmPriorities
import com.cropexpert.app.domain.model.lastSeasonCropValue
import com.cropexpert.app.domain.model.rankRotationCandidates
import com.cropexpert.app.domain.model.soilPHValue
import com.cropexpert.app.ui.theme.ForestGreen
import com.cropexpert.app.ui.theme.LightMint
import com.cropexpert.app.ui.theme.Typography
import com.cropexpert.app.ui.farm.MyFarmMapCard
import com.cropexpert.app.ui.farm.SoilSampleEntryDialog
import com.cropexpert.app.ui.farm.SoilSamplesSection
import com.cropexpert.app.ui.location.isInsideBoundary
import com.cropexpert.app.ui.location.parseBoundaryJson
import com.google.android.gms.maps.model.LatLng
import java.text.SimpleDateFormat
import java.util.Date
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.CancellationException

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
fun MyFarmScreen(
    profile: FarmProfile,
    farm: FarmEntity?,
    soilSamples: List<SoilSampleEntity>,
    databaseError: String? = null,
    onSave: (FarmProfile) -> Unit,
    onOpenCropAdvisor: () -> Unit,
    onEditLocation: () -> Unit,
    onAddSoilSample: (SoilSampleEntity) -> Unit,
    onDeleteSoilSample: (Long) -> Unit,
    terrainState: FarmTerrainState = FarmTerrainState.Empty,
    onLoadTerrain: (String?) -> Unit = {},
    onRetryTerrain: () -> Unit = {}
) {
    var currentProfile by remember(profile) { mutableStateOf(profile) }
    var cropHistoryExpanded by remember { mutableStateOf(false) }
    var soilTestExpanded by remember { mutableStateOf(false) }
    var constraintsExpanded by remember { mutableStateOf(false) }
    var pendingSamplePosition by remember { mutableStateOf<LatLng?>(null) }
    var mapMessage by remember { mutableStateOf<String?>(null) }
    var showSoilInfo by remember { mutableStateOf(false) }
    val boundaryPoints = remember(farm?.boundaryJson) {
        parseBoundaryJson(farm?.boundaryJson)
    }
    val samplesOutsideBoundary = remember(boundaryPoints, soilSamples) {
        if (boundaryPoints.size < 3) 0 else
            soilSamples.count {
                !LatLng(it.latitude, it.longitude).isInsideBoundary(boundaryPoints)
            }
    }
    LaunchedEffect(farm?.boundaryJson) {
        onLoadTerrain(farm?.boundaryJson)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightMint)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(
            eyebrow = "YOUR FIELD",
            title = "My Farm",
            subtitle = "Manage your field location, soil notes, and farm context."
        )
        MyFarmMapCard(
            farm = farm,
            samples = soilSamples,
            terrainState = terrainState,
            onRetryTerrain = onRetryTerrain,
            onEditLocation = onEditLocation,
            onMapLongClick = { point ->
                when {
                    boundaryPoints.size < 3 ->
                        mapMessage = "Outline your field before adding a soil sample."
                    !point.isInsideBoundary(boundaryPoints) ->
                        mapMessage = "Place soil samples inside the field boundary."
                    else -> {
                        mapMessage = null
                        pendingSamplePosition = point
                    }
                }
            }
        )
        databaseError?.let {
            CompactInfoBanner("Could not save farm information: $it")
        }
        mapMessage?.let { Text(it, color = Color(0xFF9B1C1C), fontSize = 12.sp) }
        Text(
            if (boundaryPoints.size >= 3) {
                "The field is rendered as a clipped 3D terrain model. Drag to orbit, pinch to zoom, and long-press the field to add a soil-test point."
            } else {
                "Plot your farm boundary to generate a 3D terrain model clipped to that field."
            },
            color = Color.Gray,
            fontSize = 12.sp
        )
        SoilSamplesSection(
            samples = soilSamples,
            onDeleteSample = onDeleteSoilSample,
            onInfoClick = { showSoilInfo = true }
        )
        if (samplesOutsideBoundary > 0) {
            CompactInfoBanner(
                "$samplesOutsideBoundary saved soil sample${if (samplesOutsideBoundary == 1) " is" else "s are"} outside the current outline. Records were kept when the boundary changed; review their map pins."
            )
        }
        if (farm?.boundaryJson != null && boundaryPoints.size < 3) {
            CompactInfoBanner("The saved boundary could not be displayed. Edit the field boundary to draw it again; saved soil tests are retained.")
        }

        SectionHeading(title = "Farm details")
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
                    "This profile pH is the separate Crop Advisor override; mapped soil-test points above are not scored yet."
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
        OutlinedButton(
            onClick = { onSave(currentProfile.normalized()) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !invalidSoilPH
        ) {
            Text("Save My Farm details", color = ForestGreen)
        }
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

    pendingSamplePosition?.let { point ->
        SoilSampleEntryDialog(
            point = point,
            onDismiss = { pendingSamplePosition = null },
            onSave = { sample ->
                onAddSoilSample(sample)
                pendingSamplePosition = null
            }
        )
    }
    if (showSoilInfo) {
        AlertDialog(
            onDismissRequest = { showSoilInfo = false },
            title = { Text("About soil test points") },
            text = {
                Text("Saved tests are field notes. They are not yet used to score Crop Advisor recommendations.")
            },
            confirmButton = {
                TextButton(onClick = { showSoilInfo = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
fun RotationPlansScreen(
    profile: FarmProfile,
    advisorResponse: CropRecommendationResponse?,
    onEditProfile: () -> Unit,
    onOpenCropAdvisor: () -> Unit,
    latitude: Double? = null,
    longitude: Double? = null,
    fieldBoundary: String? = null,
    recommendationBaseUrl: String = NetworkConfig.BASE_URL,
    cropAdvisorApiKey: String = BuildConfig.CROP_ADVISOR_API_KEY,
    loadRecommendationsOnOpen: Boolean = false
) {
    var consideredCropId by rememberSaveable(advisorResponse) { mutableStateOf<String?>(null) }
    var alternativesExpanded by rememberSaveable(advisorResponse) { mutableStateOf(false) }
    var whyExpanded by rememberSaveable(advisorResponse) { mutableStateOf(false) }
    var limitsExpanded by rememberSaveable(advisorResponse) { mutableStateOf(false) }
    var displayedResponse by remember(advisorResponse, loadRecommendationsOnOpen) {
        mutableStateOf(advisorResponse.takeUnless { loadRecommendationsOnOpen })
    }
    var recommendationLoading by remember { mutableStateOf(false) }
    var recommendationError by remember { mutableStateOf<String?>(null) }
    var requestGeneration by remember { mutableStateOf(0) }
    val cropAdvisorRepository = remember { CropAdvisorRepository() }
    LaunchedEffect(advisorResponse, loadRecommendationsOnOpen) {
        if (!loadRecommendationsOnOpen) displayedResponse = advisorResponse
    }
    LaunchedEffect(
        loadRecommendationsOnOpen,
        latitude,
        longitude,
        fieldBoundary,
        profile.soilPH,
        recommendationBaseUrl,
        cropAdvisorApiKey,
        requestGeneration
    ) {
        if (!loadRecommendationsOnOpen && requestGeneration == 0) return@LaunchedEffect
        if (loadRecommendationsOnOpen) displayedResponse = null
        if (latitude == null || longitude == null) {
            recommendationError = "Add your farm location before loading crop plans."
            return@LaunchedEffect
        }
        if (recommendationBaseUrl.isBlank() || cropAdvisorApiKey.isBlank()) {
            recommendationError = "Crop recommendations are not configured in this app build."
            return@LaunchedEffect
        }
        recommendationLoading = true
        recommendationError = null
        try {
            displayedResponse = cropAdvisorRepository.getRecommendations(
                latitude = latitude,
                longitude = longitude,
                soilPH = profile.soilPHValue(),
                fieldBoundary = fieldBoundary,
                baseUrl = recommendationBaseUrl,
                apiKey = cropAdvisorApiKey,
                rotationWindowMonths = if (loadRecommendationsOnOpen) 4 else null
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            recommendationError = error.localizedMessage
                ?: "Could not load crop recommendations. Please try again."
        } finally {
            recommendationLoading = false
        }
    }
    val eligibleClimateCandidates = remember(profile, displayedResponse) {
        val recommendations = displayedResponse?.recommendations.orEmpty()
        val hasRotationCandidates = recommendations.any {
            it.rankingMode.equals("rotation", ignoreCase = true)
        }
        recommendations
            .filter {
                if (hasRotationCandidates) {
                    it.rankingMode.equals("rotation", ignoreCase = true)
                } else {
                    it.rankingMode.equals("climate", ignoreCase = true)
                }
            }
            .distinctBy { it.cropId ?: it.cropName }
    }
    val climateCandidates = remember(profile, eligibleClimateCandidates) {
        rankRotationCandidates(profile, eligibleClimateCandidates)
    }
    val plans = remember(profile, eligibleClimateCandidates) {
        buildRotationPlans(profile, eligibleClimateCandidates)
    }
    val nextSeasonPlan = plans.firstOrNull { it.id == "short_term" }
    val laterSeasonPlan = plans.firstOrNull { it.id == "return_to_previous" }
    val previousCrop = profile.lastSeasonCropValue()
    val featuredCandidate = nextSeasonPlan?.crops?.firstOrNull()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightMint)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(
            eyebrow = "YOUR FIELD",
            title = "Crop rotation",
            subtitle = "Climate-screened crops you can plant within the next ${displayedResponse?.plantingWindowMonths ?: 4} months."
        )
        RotationContextCard(
            previousCrop = previousCrop,
            district = displayedResponse?.district,
            agroZone = displayedResponse?.agroZone
        )
        when {
            displayedResponse == null && recommendationLoading -> RotationLoadingCard()
            displayedResponse == null && recommendationError != null -> RotationEmptyCard(
                title = "Could not load crop plans",
                body = recommendationError.orEmpty()
            )
            displayedResponse == null -> RotationEmptyCard(
                title = "Find your next crop",
                body = "Load crop options screened for your field and planting window."
            )
            climateCandidates.isEmpty() -> RotationEmptyCard(
                title = "No suitable crops found",
                body = "Refresh Crop Advisor or review your field location. No substitute crop is shown."
            )
            featuredCandidate != null -> RotationCandidateCard(
                candidate = featuredCandidate,
                isConsidered = consideredCropId == (featuredCandidate.cropId ?: featuredCandidate.cropName),
                selectionReason = nextSeasonPlan.selectionReason,
                showDetails = whyExpanded,
                onToggleDetails = { whyExpanded = !whyExpanded },
                onConsider = {
                    val candidateId = featuredCandidate.cropId ?: featuredCandidate.cropName
                    consideredCropId =
                        if (consideredCropId == candidateId) null else candidateId
                }
            )
        }
        RotationRoadmapCard(
            nextSeasonPlan = nextSeasonPlan,
            laterSeasonPlan = laterSeasonPlan
        )
        val visibleOptions = climateCandidates.take(3)
        if (visibleOptions.size > 1) {
            TextButton(
                onClick = { alternativesExpanded = !alternativesExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (alternativesExpanded) "Hide other options" else "Other options (${visibleOptions.size - 1})",
                    color = ForestGreen,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    if (alternativesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = ForestGreen
                )
            }
            if (alternativesExpanded) {
                visibleOptions.drop(1).forEach { candidate ->
                    RotationAlternativeRow(
                        candidate = candidate,
                        isConsidered = consideredCropId == (candidate.cropId ?: candidate.cropName),
                        onConsider = {
                            val candidateId = candidate.cropId ?: candidate.cropName
                            consideredCropId =
                                if (consideredCropId == candidateId) null else candidateId
                        }
                    )
                }
            }
        }
        consideredCropId?.let { selectedId ->
            climateCandidates.firstOrNull { (it.cropId ?: it.cropName) == selectedId }?.let {
                Text(
                    "Considering ${it.cropName}; this does not change the suggested plan.",
                    color = ForestGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        RotationPlanLimits(
            response = displayedResponse,
            expanded = limitsExpanded,
            onToggle = { limitsExpanded = !limitsExpanded }
        )
        TextButton(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) {
            Text("Edit farm", color = ForestGreen)
        }
        Button(
            onClick = {
                if (loadRecommendationsOnOpen) {
                    recommendationError = null
                    requestGeneration += 1
                } else {
                    onOpenCropAdvisor()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !recommendationLoading,
            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
        ) {
            Text(
                when {
                    recommendationLoading -> "Loading crops…"
                    displayedResponse == null && recommendationError != null -> "Retry"
                    displayedResponse == null -> "Find crops"
                    else -> "Refresh crops"
                },
                color = Color.White
            )
        }
        if (loadRecommendationsOnOpen) {
            TextButton(onClick = onOpenCropAdvisor, modifier = Modifier.fillMaxWidth()) {
                Text("Open Crop Advisor", color = ForestGreen)
            }
        }
    }
}

@Composable
private fun RotationLoadingCard() {
    Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = ForestGreen,
                strokeWidth = 2.dp
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Finding suitable crops", color = ForestGreen, fontWeight = FontWeight.Bold)
                Text(
                    "Checking your location and next planting window…",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(title, color = ForestGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun RotationContextCard(
    previousCrop: String,
    district: String?,
    agroZone: String?
) {
    val location = listOfNotNull(
        district?.takeIf { it.isNotBlank() && !it.equals("Unavailable", true) },
        agroZone?.takeIf { it.isNotBlank() && !it.equals("Unavailable", true) }
    ).joinToString(" · ")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "After ${previousCrop.ifBlank { "last crop not set" }}",
            color = Color.DarkGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (location.isNotBlank()) {
            Text(
                location,
                color = Color.Gray,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RotationRoadmapCard(
    nextSeasonPlan: RotationPlan?,
    laterSeasonPlan: RotationPlan?
) {
    val nextCrop = nextSeasonPlan?.crops?.firstOrNull()
    val laterCrop = laterSeasonPlan?.crops?.firstOrNull()
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (nextCrop == null) {
                Text(
                    "After harvest",
                    color = ForestGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Record the crop and refresh the plan for the following season.",
                    color = Color.DarkGray,
                    fontSize = 12.sp
                )
            } else {
                RotationTimelineStep(
                    marker = "1",
                    title = nextCrop.cropName,
                    subtitle = "Plant ${nextCrop.plantIn}",
                    isLast = false
                )
                RotationTimelineStep(
                    marker = "2",
                    title = "Harvest & check in",
                    subtitle = "Expected ${nextCrop.harvestBy} · record results and refresh",
                    isLast = laterCrop == null
                )
                if (laterCrop != null) {
                    RotationTimelineStep(
                        marker = "3",
                        title = laterCrop.cropName,
                        subtitle = "Possible window ${laterCrop.plantIn} · confirm after harvest",
                        isLast = true
                    )
                }
            }
        }
    }
}

@Composable
private fun RotationTimelineStep(
    marker: String,
    title: String,
    subtitle: String,
    isLast: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(LightMint, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(marker, color = ForestGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(22.dp)
                        .background(Color(0xFFD8E9DC))
                )
            }
        }
        Column(modifier = Modifier.padding(top = 1.dp)) {
            Text(title, color = ForestGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RotationCandidateCard(
    candidate: CropRecommendation,
    isConsidered: Boolean,
    selectionReason: String,
    showDetails: Boolean,
    onToggleDetails: () -> Unit,
    onConsider: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "NEXT SEASON",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        candidate.cropName,
                        color = ForestGreen,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                StatusPill("${candidate.suitabilityScore}% climate fit", LightMint, ForestGreen)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("PLANTING WINDOW", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${candidate.plantIn} → ${candidate.harvestBy}",
                        color = Color.DarkGray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                StatusPill(
                    "${candidate.durationDays} days",
                    Color(0xFFF2F3F4),
                    Color.DarkGray
                )
            }
            Text(
                "Water need · ${candidate.waterRequirement}",
                color = Color(0xFF245A85),
                fontSize = 12.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                onClick = onConsider,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) {
                Text(if (isConsidered) "Considering" else "Consider this crop", color = Color.White)
                }
                TextButton(onClick = onToggleDetails) {
                Text(if (showDetails) "Less" else "Why?", color = ForestGreen)
                Icon(
                    if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = ForestGreen
                )
                }
            }
            if (showDetails) {
                if (selectionReason.isNotBlank()) {
                    Text(
                        selectionReason,
                        color = Color.DarkGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = buildString {
                        append(candidate.cropFamily?.let { "Family: $it. " }.orEmpty())
                        candidate.suitabilityReason
                            .takeIf(String::isNotBlank)
                            ?.let(::append)
                    },
                    color = Color.DarkGray,
                    fontSize = 12.sp
                )
                val climateRisks = remember(candidate) { candidate.climateWatchItems() }
                if (climateRisks.isEmpty()) {
                    Text(
                        if (candidate.hasClimateComparisonData()) {
                            "No rainfall or temperature mismatch flagged."
                        } else {
                            "Some climate comparison data is missing."
                        },
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                } else {
                    climateRisks.forEach { risk ->
                        Text(risk, color = Color(0xFF805900), fontSize = 11.sp)
                    }
                }
                candidate.expectedProfit?.let { profit ->
                    Text(
                        "Past income estimate: Rs ${NumberFormat.getNumberInstance().format(profit)} /ha · ${candidate.profitConfidence} confidence",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RotationAlternativeRow(
    candidate: CropRecommendation,
    isConsidered: Boolean,
    onConsider: () -> Unit
) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(candidate.cropName, color = ForestGreen, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${candidate.suitabilityScore}% fit · ${candidate.plantIn}",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            TextButton(onClick = onConsider) {
                Text(if (isConsidered) "Considering" else "Consider", color = ForestGreen)
            }
        }
    }
}

@Composable
private fun RotationPlanLimits(
    response: CropRecommendationResponse?,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        color = Color(0xFFF3F5F3),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("About this plan", color = ForestGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = ForestGreen
                )
            }
            if (expanded) {
                Text(
                    "Planting dates are limited to the next ${response?.plantingWindowMonths ?: 4} forecast months, but climate fit is checked across each full crop cycle. Monthly projections are not daily forecasts or planting guarantees.",
                    color = Color.DarkGray,
                    fontSize = 11.sp
                )
                response?.let { forecast ->
                    val rainfallError = forecast.rainfallMaeMm
                        ?.takeIf(Double::isFinite)
                        ?.let { "${formatRotationValue(it)} mm" }
                        ?: "unavailable"
                    val temperatureError = forecast.temperatureMaeC
                        ?.takeIf(Double::isFinite)
                        ?.let { "${formatRotationValue(it)} °C" }
                        ?: "unavailable"
                    Text(
                        "Models selected on validation data: ${forecast.rainfallModel} rainfall, ${forecast.temperatureModel} temperature. Historical hold-out MAE: rainfall $rainfallError (${formatForecastSkill(forecast.rainfallSkillVsClimatology)} vs climatology); temperature $temperatureError (${formatForecastSkill(forecast.temperatureSkillVsClimatology)} vs climatology). Positive skill means lower error than climatology; historical scores do not guarantee future accuracy.",
                        color = Color.DarkGray,
                        fontSize = 11.sp
                    )
                }
                Text(
                    "Family diversity is a simple heuristic; nutrient recovery and pest-break effects are not modeled. Recheck after harvest.",
                    color = Color.DarkGray,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun formatForecastSkill(value: Double?): String =
    value?.takeIf(Double::isFinite)?.let {
        NumberFormat.getPercentInstance().format(it)
    } ?: "unavailable"

private fun CropRecommendation.climateWatchItems(): List<String> = buildList {
    if (forecastRainMm.isFinite() && idealRainMinMm.isFinite() &&
        idealRainMaxMm.isFinite() && idealRainMinMm <= idealRainMaxMm
    ) {
        when {
            forecastRainMm < idealRainMinMm -> add(
                "Forecast rainfall (${formatRotationValue(forecastRainMm)} mm) is below this crop's ideal range (${formatRotationValue(idealRainMinMm)}–${formatRotationValue(idealRainMaxMm)} mm); check water access."
            )
            forecastRainMm > idealRainMaxMm -> add(
                "Forecast rainfall (${formatRotationValue(forecastRainMm)} mm) is above this crop's ideal range (${formatRotationValue(idealRainMinMm)}–${formatRotationValue(idealRainMaxMm)} mm); check local drainage conditions."
            )
        }
    }
    if (forecastTemperatureC.isFinite() && idealTemperatureMinC.isFinite() &&
        idealTemperatureMaxC.isFinite() && idealTemperatureMinC <= idealTemperatureMaxC
    ) {
        when {
            forecastTemperatureC < idealTemperatureMinC -> add(
                "Forecast temperature (${formatRotationValue(forecastTemperatureC)}°C) is below this crop's ideal range (${formatRotationValue(idealTemperatureMinC)}–${formatRotationValue(idealTemperatureMaxC)}°C); review planting timing."
            )
            forecastTemperatureC > idealTemperatureMaxC -> add(
                "Forecast temperature (${formatRotationValue(forecastTemperatureC)}°C) is above this crop's ideal range (${formatRotationValue(idealTemperatureMinC)}–${formatRotationValue(idealTemperatureMaxC)}°C); review planting timing."
            )
        }
    }
}

private fun CropRecommendation.hasClimateComparisonData(): Boolean =
    forecastRainMm.isFinite() && idealRainMinMm.isFinite() && idealRainMaxMm.isFinite() &&
        idealRainMinMm <= idealRainMaxMm &&
        forecastTemperatureC.isFinite() && idealTemperatureMinC.isFinite() &&
        idealTemperatureMaxC.isFinite() && idealTemperatureMinC <= idealTemperatureMaxC

private fun formatRotationValue(value: Double): String =
    NumberFormat.getNumberInstance().format(value)

@Composable
private fun RotationEmptyCard(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
        modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, color = ForestGreen, fontWeight = FontWeight.Bold)
        Text(body, color = Color.Gray, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
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
            if (plan.id == "return_to_previous") {
                "After harvest, record the crop and refresh Crop Advisor. A later crop is shown only when its forecast planting window follows this harvest."
            } else {
                "Select last season's crop and get Crop Advisor results to complete this candidate sequence."
            }
        } else {
            plan.sequence.joinToString(" → ")
        },
        "Nutrient effects" to "Not assessed yet. Crop nutrient and residue-trait data are not connected; no nitrogen restoration is claimed.",
        "Important limitation" to "The family check is an exact-name, immediate-last-crop diversity heuristic only. Pest cycles, nutrient effects, and a complete multi-season rotation are not validated."
    )
    val details = if (plan.isBaseline) baselineDetails else alternativeDetails
    val title = plan.title
    val candidates = plan.crops
    val statusText = when {
        plan.isBaseline -> "Comparison baseline"
        plan.id == "short_term" -> "Provisional · climate screen + family heuristic"
        else -> "Conditional · reassess after harvest"
    }
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
            Text("Open My Farm", color = ForestGreen)
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
        "Why ${candidate.cropName} fits",
        buildString {
            append("Crop Advisor · climate fit ${candidate.suitabilityScore}% · ${candidate.plantIn} to ${candidate.harvestBy} · ${candidate.durationDays} days. ")
            candidate.cropFamily?.takeIf(String::isNotBlank)?.let { append("Crop family: $it. ") }
            candidate.suitabilityReason.takeIf(String::isNotBlank)?.let { append("$it ") }
            candidate.expectedProfit?.let {
                append("Historical risk-adjusted estimate: Rs ${NumberFormat.getNumberInstance().format(it)} /ha. ")
            }
            append("Profit evidence: ${candidate.profitConfidence} confidence · ${candidate.profitBasis}. ")
            append("Water: ${candidate.waterRequirement}.")
        }
    )
    val climateRisks = candidate.climateWatchItems()
    DetailSection(
        "Climate conditions to watch",
        buildString {
            if (climateRisks.isEmpty()) {
                append(
                    if (candidate.hasClimateComparisonData()) {
                        "No rainfall or temperature mismatch reported by Crop Advisor. "
                    } else {
                        "Climate comparison data is incomplete. "
                    }
                )
            } else {
                append(climateRisks.joinToString(" "))
                append(" ")
            }
            append("Rotation, pest-break, and nutrient risks are not assessed yet.")
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
        MyFarmScreen(
            profile = FarmProfile(lastSeasonCrop = "Rice"),
            farm = null,
            soilSamples = emptyList(),
            onSave = {},
            onOpenCropAdvisor = {},
            onEditLocation = {},
            onAddSoilSample = {},
            onDeleteSoilSample = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF1FAF3, heightDp = 1000)
@Composable
private fun RotationPlansScreenPreview() {
    MaterialTheme(typography = Typography) {
        RotationPlansScreen(
            profile = FarmProfile(
                lastSeasonCrop = "Maize",
                harvestLevel = "Average",
                residueManagement = "Mostly left",
                priorities = listOf("Conserve water", "Soil health")
            ),
            advisorResponse = rotationPreviewResponse(),
            onEditProfile = {},
            onOpenCropAdvisor = {}
        )
    }
}

private fun rotationPreviewResponse() = CropRecommendationResponse(
    district = "Anuradhapura",
    agroZone = "DL1b",
    plantingWindow = "Dec 2026",
    rainfallMaeMm = 38.0,
    rainfallSkillVsClimatology = null,
    rainfallModel = "recent_climatology",
    temperatureMaeC = 0.8,
    temperatureSkillVsClimatology = null,
    temperatureModel = "holt_winters",
    suitabilityRankCorrelation = null,
    topFiveOverlap = null,
    recommendations = listOf(
        rotationPreviewCandidate(
            name = "Green Gram",
            family = "Fabaceae",
            score = 92,
            rank = 1,
            plantIn = "Dec 2026",
            harvestBy = "Mar 2027",
            waterNeed = "Low to moderate"
        ),
        rotationPreviewCandidate(
            name = "Cowpea",
            family = "Fabaceae",
            score = 88,
            rank = 2,
            plantIn = "Jan 2027",
            harvestBy = "Apr 2027",
            waterNeed = "Low to moderate"
        ),
        rotationPreviewCandidate(
            name = "Cabbage",
            family = "Brassicaceae",
            score = 81,
            rank = 3,
            plantIn = "Jun 2027",
            harvestBy = "Sep 2027",
            waterNeed = "Moderate"
        )
    )
)

private fun rotationPreviewCandidate(
    name: String,
    family: String,
    score: Int,
    rank: Int,
    plantIn: String,
    harvestBy: String,
    waterNeed: String
) = CropRecommendation(
    cropName = name,
    localName = null,
    category = "Legumes",
    suitabilityScore = score,
    expectedProfit = 185_000.0,
    profitBasis = "District observed, 2024",
    profitConfidence = "medium",
    confidenceLevel = null,
    imageUrl = null,
    waterRequirement = waterNeed,
    waterRequirementVsPaddy = null,
    durationDays = 85,
    suitabilityReason = "Forecast conditions are within the preferred climate range.",
    plantIn = plantIn,
    harvestBy = harvestBy,
    forecastRainMm = 420.0,
    idealRainMinMm = 350.0,
    idealRainMaxMm = 650.0,
    forecastTemperatureC = 27.0,
    idealTemperatureMinC = 25.0,
    idealTemperatureMaxC = 32.0,
    rank = rank,
    suitabilityRankCorrelation = null,
    topFiveOverlap = null,
    netProfitPerHa = 185_000.0,
    cropId = name.lowercase().replace(" ", "_"),
    cropFamily = family
)

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
