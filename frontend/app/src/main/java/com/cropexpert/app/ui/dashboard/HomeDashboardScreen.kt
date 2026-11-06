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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cropexpert.app.ui.theme.ForestGreen
import com.cropexpert.app.ui.theme.LightMint
import com.cropexpert.app.ui.theme.VibrantLime

@Composable
fun HomeDashboardScreen(
    onNavigateToFuturePlan: () -> Unit,
    onNavigateToCropAdvisor: () -> Unit,
    viewModel: HomeDashboardViewModel = viewModel()
) {
    val telemetry by viewModel.telemetry.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightMint)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Immersive "Live" Header
        SatelliteStatusHeader()

        // 2. Land Status Profile Hero Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Background Gradient: ForestGreen to Darker Green
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(ForestGreen, Color(0xFF032210))
                            )
                        )
                )

                // NASA Mission Attribution
                Text(
                    text = "SOURCE: NASA SMAP / GPM / SRTM",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.5f)
                )

                // Hero Card Map Preview Silhouette (Agro-Ecological Map Thumbnail)
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
                    
                    ProfileRow(label = "DISTRICT", value = "Anuradhapura")
                    ProfileRow(label = "ZONE", value = "DL1b")
                    ProfileRow(label = "ELEVATION", value = "152m")

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
                    label = "Soil Moisture", //don't change the label
                    value = telemetry.soilMoisture,
                    status = "Normal",
                    tag = "NASA SMAP (10km)"
                )
                TelemetryCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Science,
                    label = "Soil pH",
                    value = telemetry.soilPH,
                    status = "Optimal",
                    tag = "REGIONAL ADAPTED"
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TelemetryCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.WbSunny,
                    label = "Ambient Temp",
                    value = telemetry.ambientTemp,
                    status = "Stable",
                    tag = "ERA5 + SRTM 30m"
                )
                TelemetryCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Cloud,
                    label = "Upcoming Rain",
                    value = telemetry.upcomingRain,
                    status = "Expected",
                    tag = "NASA GPM / CHIRPS"
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
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SatelliteStatusHeader() {
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

    Surface(
        color = Color.White.copy(alpha = 0.5f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.padding(bottom = 16.dp)
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
                text = "NASA Earth Observation Stream Active | LAST SYNC: T-14M",
                color = ForestGreen,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun TelemetryCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    status: String,
    tag: String
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
            // 1. Fix Status Badge Wrapping (Constraint Enforcement)
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

            // 2. Refine Scientific Typography (Value)
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = ForestGreen
            )

            // 3. Sparkline Polish
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            ) {
                val width = size.width
                val height = size.height
                val path = Path().apply {
                    moveTo(0f, height * 0.8f)
                    cubicTo(
                        width * 0.2f, height * 0.4f,
                        width * 0.5f, height * 0.9f,
                        width * 0.7f, height * 0.3f
                    )
                    cubicTo(
                        width * 0.85f, height * 0.2f,
                        width * 0.95f, height * 0.6f,
                        width, height * 0.4f
                    )
                }
                drawPath(
                    path = path,
                    color = ForestGreen.copy(alpha = 0.3f),
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // 2. Refine Tag Typography
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
