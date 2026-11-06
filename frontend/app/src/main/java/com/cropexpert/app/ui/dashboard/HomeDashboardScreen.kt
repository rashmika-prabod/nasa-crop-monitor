package com.cropexpert.app.ui.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cropexpert.app.ui.theme.ForestGreen
import com.cropexpert.app.ui.theme.LightMint
import com.cropexpert.app.ui.theme.VibrantLime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeDashboardScreen(
    onNavigateToFuturePlan: () -> Unit,
    onNavigateToCropAdvisor: () -> Unit,
    viewModel: HomeDashboardViewModel = viewModel(factory = HomeDashboardViewModel.Factory)
) {
    val telemetryState by viewModel.telemetry.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val lastSuccessfulSync by viewModel.lastSuccessfulSync.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightMint)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Immersive "Live" Header with Data Provenance
        SatelliteStatusHeader(
            sourceMetadata = telemetryState?.sourceMetadata,
            lastSyncTime = lastSuccessfulSync,
            isSyncing = isLoading
        )

        errorMessage?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(message, color = Color(0xFFB71C1C))
                    Button(
                        onClick = viewModel::refreshData,
                        enabled = !isLoading
                    ) {
                        Text("Retry")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (isLoading && telemetryState == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ForestGreen)
            }
        } else {
            telemetryState?.let { telemetry ->
                // 2. Land Status Profile Hero Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(ForestGreen, Color(0xFF032210))
                                    )
                                )
                        )

                        // Data Provenance (Mission Attribution)
                        Text(
                            text = "SOURCE: ${telemetry.sourceMetadata}",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.5f)
                        )

                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            modifier = Modifier
                                .size(160.dp)
                                .align(Alignment.CenterEnd)
                                .offset(x = 30.dp)
                                .graphicsLayer(alpha = 0.08f),
                            tint = VibrantLime
                        )

                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "LAND STATUS PROFILE",
                                color = VibrantLime,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.5.sp
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Dynamic Values from State
                            ProfileRow(label = "DISTRICT", value = telemetry.district)
                            ProfileRow(label = "ZONE", value = telemetry.zone)
                            ProfileRow(label = "ELEVATION", value = telemetry.elevation)

                            Spacer(modifier = Modifier.weight(1f))

                            Button(
                                onClick = onNavigateToCropAdvisor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VibrantLime),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Get Advice",
                                    color = ForestGreen,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. Current Conditions (2x2 Grid)
                Text(
                    text = "Current Conditions",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = ForestGreen,
                    modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TelemetryCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.WaterDrop,
                            label = "Soil Moisture (Root Zone)",
                            value = telemetry.soilMoisture,
                            status = observationStatus(
                                telemetry.soilMoisture,
                                telemetry.soilMoistureObservedAt
                            ),
                            tag = "NASA SMAP (11km)",
                            chartValues = telemetry.soilMoistureHistory.orEmpty()
                        )
                        TelemetryCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.Science,
                            label = "Soil pH",
                            value = telemetry.soilPH,
                            status = if (telemetry.soilPH.isAvailable()) "Estimated" else "No data",
                            tag = "PH BY DEPTH (250m)",
                            chartValues = telemetry.soilPHDepthProfile.orEmpty()
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TelemetryCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.WbSunny,
                            label = "Ambient Temp",
                            value = telemetry.ambientTemp,
                            status = observationStatus(
                                telemetry.ambientTemp,
                                telemetry.ambientTempObservedAt
                            ),
                            tag = "ERA5-LAND HISTORY",
                            chartValues = telemetry.ambientTempHistory.orEmpty()
                        )
                        TelemetryCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.Cloud,
                            label = "Upcoming Rain",
                            value = telemetry.upcomingRain,
                            status = forecastStatus(
                                telemetry.upcomingRain,
                                telemetry.forecastIssuedAt
                            ),
                            tag = "NOAA GFS (6H STEPS)",
                            chartValues = telemetry.rainForecast.orEmpty()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 4. Explore CTA
                Button(
                    onClick = onNavigateToFuturePlan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Explore 24-Month Climate Outlook",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SatelliteStatusHeader(
    sourceMetadata: String? = null,
    lastSyncTime: Long? = null,
    isSyncing: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = Color.White.copy(alpha = 0.5f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Circle,
                    contentDescription = null,
                    tint = Color.Green.copy(alpha = alpha),
                    modifier = Modifier.size(8.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = buildString {
                        append(if (isSyncing) "SYNCING EARTH DATA" else "EARTH DATA")
                        append(" | LAST SYNC: ")
                        append(
                            lastSyncTime?.let {
                                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
                            } ?: if (isSyncing) "SYNCING" else "NOT YET"
                        )
                    },
                    color = ForestGreen,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        sourceMetadata?.let {
            Text(
                text = it,
                color = ForestGreen.copy(alpha = 0.6f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End
        )
    }
}

private fun String.isAvailable(): Boolean = isNotBlank() && this != "Unavailable"

private fun observationStatus(value: String, observedAt: Long?): String {
    if (!value.isAvailable() || observedAt == null) return "No data"

    val ageMillis = (System.currentTimeMillis() - observedAt).coerceAtLeast(0L)
    return when {
        ageMillis <= 6 * 60 * 60 * 1000L -> "Recent"
        ageMillis <= 24 * 60 * 60 * 1000L -> "Delayed"
        else -> "Stale"
    }
}

private fun forecastStatus(value: String, issuedAt: Long?): String {
    if (!value.isAvailable() || issuedAt == null) return "No data"

    val ageMillis = (System.currentTimeMillis() - issuedAt).coerceAtLeast(0L)
    return when {
        ageMillis <= 12 * 60 * 60 * 1000L -> "Forecast"
        ageMillis <= 24 * 60 * 60 * 1000L -> "Aging"
        else -> "Stale"
    }
}

@Composable
fun TelemetryCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    status: String,
    tag: String,
    chartValues: List<Float> = emptyList()
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier.height(130.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = ForestGreen
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        fontSize = 9.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = VibrantLime,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = status,
                        fontSize = 8.sp,
                        color = ForestGreen,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Text(
                text = value,
                fontSize = if (value.length > 8) 16.sp else 22.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = ForestGreen,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            if (chartValues.isEmpty()) {
                Text(
                    text = "Trend unavailable",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                ) {
                    val low = chartValues.minOrNull() ?: return@Canvas
                    val high = chartValues.maxOrNull() ?: return@Canvas
                    val valueRange = (high - low).takeIf { it > 0f } ?: 1f
                    val path = Path()
                    chartValues.forEachIndexed { index, value ->
                        val x = if (chartValues.size == 1) {
                            size.width / 2f
                        } else {
                            size.width * index / (chartValues.lastIndex)
                        }
                        val y = size.height - ((value - low) / valueRange * size.height)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = ForestGreen.copy(alpha = 0.55f),
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            Surface(
                color = LightMint,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = tag,
                    fontSize = 8.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    color = ForestGreen.copy(alpha = 0.7f),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF1FAF3)
@Composable
fun HomeDashboardPreview() {
    HomeDashboardScreen(
        onNavigateToFuturePlan = {},
        onNavigateToCropAdvisor = {}
    )
}
