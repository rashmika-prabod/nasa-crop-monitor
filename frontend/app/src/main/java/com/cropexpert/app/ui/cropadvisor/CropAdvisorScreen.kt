package com.cropexpert.app.ui.cropadvisor

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.cropexpert.app.domain.model.CropRecommendation
import com.cropexpert.app.domain.model.CropRecommendationResponse
import com.cropexpert.app.data.api.NetworkConfig
import com.cropexpert.app.ui.theme.ForestGreen
import com.cropexpert.app.ui.theme.LightMint
import com.cropexpert.app.ui.theme.VibrantLime
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropAdvisorScreen(
    soilPH: Double? = null,
    fieldBoundary: String? = null,
    onUseForRotation: (CropRecommendationResponse) -> Unit = {},
    viewModel: CropAdvisorViewModel = viewModel(factory = CropAdvisorViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("crop_advisor_hosted", android.content.Context.MODE_PRIVATE)
    }
    var backendUrl by rememberSaveable {
        mutableStateOf(preferences.getString("base_url", NetworkConfig.BASE_URL).orEmpty())
    }
    var apiKey by remember {
        mutableStateOf(preferences.getString("api_key", "").orEmpty())
    }

    LaunchedEffect(backendUrl, apiKey, soilPH, fieldBoundary) {
        if (backendUrl.isNotBlank() && apiKey.isNotBlank()) {
            viewModel.loadRecommendations(backendUrl, apiKey, soilPH, fieldBoundary)
        }
    }
    val recommendationsListState = rememberLazyListState()
    LaunchedEffect(state.priority) {
        recommendationsListState.animateScrollToItem(0)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LightMint)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = recommendationsListState,
            contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                state.response?.let { response ->
                    ContextHeader(response)
                } ?: AdvisorTitle()
            }

            item {
                AdvisorConnectionPanel(
                    backendUrl = backendUrl,
                    onBackendUrlChange = { backendUrl = it },
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it },
                    onConnect = {
                        preferences.edit()
                            .putString("base_url", backendUrl.trim())
                            .putString("api_key", apiKey.trim())
                            .apply()
                        viewModel.loadRecommendations(backendUrl, apiKey, soilPH, fieldBoundary)
                    }
                )
            }

            item {
                PrioritySelector(
                    selected = state.priority,
                    onSelect = viewModel::setPriority
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CropCategoryFilter.values().toList()) { category ->
                        FilterChip(
                            selected = state.category == category,
                            onClick = { viewModel.setCategory(category) },
                            label = { Text(category.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ForestGreen,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = ForestGreen
                            )
                        )
                    }
                }
            }

            if (state.isLoading && state.response == null) {
                item { TournamentLoading() }
            } else if (!state.isConfigured && state.response == null) {
                item {
                    EmptyRecommendations(
                        isLoading = false,
                        message = "Enter the shared service API key above to load recommendations."
                    )
                }
            } else if (state.errorMessage != null && state.response == null) {
                item {
                    ErrorPanel(
                        message = state.errorMessage.orEmpty(),
                        onRetry = {
                            viewModel.loadRecommendations(backendUrl, apiKey, soilPH, fieldBoundary)
                        }
                    )
                }
            } else if (state.visibleRecommendations.isEmpty()) {
                item {
                    EmptyRecommendations(
                        isLoading = state.isLoading,
                        message = when {
                            state.isLoading -> "Refreshing crop recommendations..."
                            state.priority == RecommendationPriority.MAXIMUM_PROFIT ->
                                "No crops have historical profit data for this planting window."
                            else ->
                                "No crops met the climate suitability and profit data thresholds for this planting window."
                        }
                    )
                }
            } else {
                itemsIndexed(
                    items = state.visibleRecommendations,
                    key = { _, recommendation -> recommendation.cropName }
                ) { index, recommendation ->
                    CropRecommendationCard(
                        recommendation = recommendation,
                        rank = index + 1,
                        priority = state.priority
                    )
                }
                item {
                    Text(
                        text = "Profit is based on historical Department of Agriculture returns, not a price forecast. Water requirements are crop-table descriptions, not normalized against paddy. Crop limits and some durations use flagged assumptions; use this shortlist as guidance, not a guarantee.",
                        color = ForestGreen.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            state.response?.let { response ->
                item {
                    val climateCandidates = response.recommendations.filter {
                        it.rankingMode.equals("climate", ignoreCase = true)
                    }
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Continue to rotation plans",
                                color = ForestGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${climateCandidates.size} climate-ranked crop candidates can be used to populate plan options. The candidate sets are not yet checked as complete rotations.",
                                color = Color.DarkGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (soilPH != null) {
                                val pHStatus = when {
                                    response.soilPHUsed != null &&
                                        response.soilPHSource.equals("Farmer-provided soil test", ignoreCase = true) &&
                                        abs(response.soilPHUsed - soilPH) < 0.01 ->
                                        "API used your soil-test pH ${response.soilPHUsed.format(1)}."
                                    response.soilPHUsed != null ->
                                        "API used ${response.soilPHSource ?: "reported"} pH ${response.soilPHUsed.format(1)}; manual pH was not confirmed."
                                    else ->
                                        "API did not report soil pH use. Check that the hosted service is up to date."
                                }
                                Text(
                                    "Soil test pH ${soilPH.format(1)} · $pHStatus",
                                    color = ForestGreen,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Button(
                                onClick = { onUseForRotation(response) },
                                enabled = climateCandidates.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                            ) {
                                Text("Build rotation options", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvisorConnectionPanel(
    backendUrl: String,
    onBackendUrlChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                text = "CROP ADVISOR SERVICE",
                color = ForestGreen,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black
            )
            OutlinedTextField(
                value = backendUrl,
                onValueChange = onBackendUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("HTTPS service URL") },
                placeholder = { Text("https://your-service.onrender.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Service API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                enabled = backendUrl.startsWith("https://", ignoreCase = true) && apiKey.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
            ) {
                Text("Connect and run model")
            }
        }
    }
}

@Composable
private fun AdvisorTitle() {
    Column {
        Text(
            text = "Crop Advisor",
            color = ForestGreen,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "Forecast-based climate fit and historical returns",
            color = ForestGreen.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ContextHeader(response: CropRecommendationResponse) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdvisorTitle()
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = ForestGreen),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${response.district}  •  ${response.agroZone}",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "PLANTING WINDOW  ${response.plantingWindow}",
                    color = VibrantLime,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black
                )
                response.fieldAreaHa?.let { area ->
                    Text(
                        text = "FIELD-AREA SAMPLE  ${area.format(2)} ha · ${response.samplingBasis.orEmpty()}",
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    MaeMetric(
                        label = "RAIN MAE",
                        value = response.rainfallMaeMm?.let { "${it.format(0)} mm" } ?: "N/A",
                        modifier = Modifier.weight(1f)
                    )
                    MaeMetric(
                        label = "TOURNAMENT SKILL",
                        value = response.rainfallSkillVsClimatology?.let {
                            "${(it * 100).format(0)}% vs baseline"
                        } ?: "N/A",
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = "Selected: ${response.rainfallModel} rain • ${response.temperatureModel} temperature",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                response.suitabilityRankCorrelation?.let { correlation ->
                    Text(
                        text = "Backtest Spearman ${correlation.format(2)} • top-five overlap ${response.topFiveOverlap ?: 0}/5",
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun MaeMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PrioritySelector(
    selected: RecommendationPriority,
    onSelect: (RecommendationPriority) -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PriorityOption(
                label = "Climate Resilience",
                selected = selected == RecommendationPriority.CLIMATE_RESILIENCE,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(RecommendationPriority.CLIMATE_RESILIENCE) }
            )
            PriorityOption(
                label = "Maximum Profit",
                selected = selected == RecommendationPriority.MAXIMUM_PROFIT,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(RecommendationPriority.MAXIMUM_PROFIT) }
            )
        }
    }
}

@Composable
private fun PriorityOption(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) ForestGreen else Color.Transparent,
            contentColor = if (selected) Color.White else ForestGreen
        )
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CropRecommendationCard(
    recommendation: CropRecommendation,
    rank: Int,
    priority: RecommendationPriority
) {
    val context = LocalContext.current
    val imageRequest = remember(recommendation.imageUrl) {
        recommendation.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
            ImageRequest.Builder(context)
                .data(imageUrl)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(true)
                .build()
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(VibrantLime)
            ) {
                if (imageRequest == null) {
                    CropImagePlaceholder(recommendation.cropName)
                } else {
                    SubcomposeAsyncImage(
                        model = imageRequest,
                        contentDescription = "${recommendation.cropName} crop",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        loading = { CropImagePlaceholder(recommendation.cropName) },
                        error = { CropImagePlaceholder(recommendation.cropName) },
                        success = { SubcomposeAsyncImageContent() }
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    color = VibrantLime,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "#$rank PICK",
                        color = ForestGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recommendation.cropName,
                        color = ForestGreen,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black
                    )
                    recommendation.localName?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            color = ForestGreen.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = "${recommendation.plantIn} → ${recommendation.harvestBy}",
                        color = ForestGreen.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
                SuitabilityGauge(recommendation.suitabilityScore)
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SciencePill(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.WaterDrop,
                    label = "WATER VS PADDY",
                    value = "${recommendation.waterRequirementVsPaddy ?: "Unquantified vs Paddy"} • ${recommendation.waterRequirement}"
                )
                SciencePill(
                    modifier = Modifier.weight(0.75f),
                    icon = Icons.Outlined.Schedule,
                    label = "GROWTH",
                    value = "${recommendation.durationDays} days",
                    numericValue = true
                )
                SciencePill(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Eco,
                    label = "IMPACT",
                    value = recommendation.suitabilityReason
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                color = LightMint,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = if (priority == RecommendationPriority.MAXIMUM_PROFIT) {
                            "HISTORICAL PROFIT PER DAY"
                        } else {
                            "EST. RISK-ADJUSTED RETURN"
                        },
                        color = ForestGreen.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = (if (priority == RecommendationPriority.MAXIMUM_PROFIT) {
                            recommendation.profitPerDay
                        } else {
                            recommendation.expectedProfit
                        })?.let {
                            if (priority == RecommendationPriority.MAXIMUM_PROFIT) {
                                "Rs. ${it.format(0)}/ha/day"
                            } else {
                                "Rs. ${it.format(0)}/ha"
                            }
                        } ?: "Profit data unavailable",
                        color = ForestGreen,
                        fontSize = 17.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "${recommendation.profitConfidence.uppercase()} EVIDENCE • ${recommendation.profitBasis}",
                        color = ForestGreen.copy(alpha = 0.72f),
                        fontSize = 9.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    recommendation.confidenceLevel?.let {
                        Text(
                            text = "TOURNAMENT  $it",
                            color = ForestGreen,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black
                        )
                    }
                    ReliabilityTag(recommendation)
                }
            }
        }
    }
}

@Composable
private fun CropImagePlaceholder(cropName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightMint),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Eco,
            contentDescription = null,
            tint = ForestGreen.copy(alpha = 0.7f),
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = cropName,
            color = ForestGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SuitabilityGauge(score: Int) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(66.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = LightMint,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = ForestGreen,
                startAngle = -90f,
                sweepAngle = score.coerceIn(0, 100) * 3.6f,
                useCenter = false,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score",
                color = ForestGreen,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                lineHeight = 19.sp
            )
            Text(
                text = "FIT",
                color = ForestGreen.copy(alpha = 0.68f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SciencePill(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    numericValue: Boolean = false
) {
    Surface(
        modifier = modifier.height(72.dp),
        color = LightMint,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    color = ForestGreen.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = value,
                color = ForestGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (numericValue) FontFamily.Monospace else FontFamily.SansSerif,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReliabilityTag(recommendation: CropRecommendation) {
    val correlation = recommendation.suitabilityRankCorrelation
    val overlap = recommendation.topFiveOverlap
    val label = when {
        correlation == null || overlap == null -> "Backtest reliability unavailable"
        correlation >= 0.8 && overlap >= 4 -> "High stability • top five ${overlap}/5"
        correlation >= 0.5 -> "Moderate stability • top five ${overlap}/5"
        else -> "Low stability • check local conditions"
    }
    Text(
        text = correlation?.let { "$label • Spearman ${it.format(2)}" } ?: label,
        color = ForestGreen,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black
    )
}

@Composable
private fun TournamentLoading() {
    val transition = rememberInfiniteTransition(label = "tournament-loading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(76.dp)) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = VibrantLime,
                strokeWidth = 6.dp
            )
            Icon(
                imageVector = Icons.Outlined.Eco,
                contentDescription = null,
                tint = ForestGreen,
                modifier = Modifier
                    .size(34.dp)
                    .rotate(rotation)
            )
        }
        Text(
            text = "Running Forecasting Tournament...",
            color = ForestGreen,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Comparing seasonal baselines against historical weather",
            color = ForestGreen.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(message, color = ForestGreen, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onRetry) {
            Text("Retry", color = ForestGreen)
        }
    }
}

@Composable
private fun EmptyRecommendations(isLoading: Boolean, message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = ForestGreen)
        }
        Text(
            text = message,
            color = ForestGreen,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun Double.format(digits: Int): String =
    String.format(Locale.US, "%,.${digits}f", this)

@Preview(showBackground = true, backgroundColor = 0xFFF1FAF3, heightDp = 1000)
@Composable
private fun CropAdvisorScreenPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightMint)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ContextHeader(
            CropRecommendationResponse(
                district = "Anuradhapura",
                agroZone = "DL1b",
                plantingWindow = "Dec 2026",
                rainfallMaeMm = 38.0,
                rainfallSkillVsClimatology = 0.12,
                rainfallModel = "recent_climatology",
                temperatureMaeC = 0.8,
                temperatureSkillVsClimatology = 0.06,
                temperatureModel = "holt_winters",
                suitabilityRankCorrelation = 0.82,
                topFiveOverlap = 4,
                recommendations = emptyList()
            )
        )
        PrioritySelector(
            selected = RecommendationPriority.CLIMATE_RESILIENCE,
            onSelect = {}
        )
        CropRecommendationCard(
            recommendation = CropRecommendation(
                cropName = "Green Gram",
                localName = "Mung Bean",
                category = "Legumes",
                suitabilityScore = 92,
                expectedProfit = 185000.0,
                profitBasis = "District observed, 2024",
                profitConfidence = "high",
                confidenceLevel = "Rain MAE 38 mm | Temp MAE 0.8 C",
                imageUrl = null,
                waterRequirement = "Low to moderate",
                waterRequirementVsPaddy = "Unquantified vs Paddy",
                durationDays = 85,
                suitabilityReason = "Within forecast climate range",
                plantIn = "Dec 2026",
                harvestBy = "Mar 2027",
                forecastRainMm = 420.0,
                idealRainMinMm = 350.0,
                idealRainMaxMm = 650.0,
                forecastTemperatureC = 27.0,
                idealTemperatureMinC = 25.0,
                idealTemperatureMaxC = 32.0,
                rank = 1,
                suitabilityRankCorrelation = 0.82,
                topFiveOverlap = 4
            ),
            rank = 1,
            priority = RecommendationPriority.CLIMATE_RESILIENCE
        )
    }
}
