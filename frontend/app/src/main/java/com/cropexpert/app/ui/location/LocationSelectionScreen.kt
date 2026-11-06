package com.cropexpert.app.ui.location

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cropexpert.app.ui.theme.ForestGreen
import com.cropexpert.app.ui.theme.LightMint
import com.cropexpert.app.ui.theme.SoftRed
import com.cropexpert.app.ui.theme.WarningRed
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSelectionScreen(onConfirmLocation: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var currentStepIndex by remember { mutableIntStateOf(0) }

    val telemetrySteps = remember(searchQuery) {
        listOf(
            "Accessing NASA Earth Engine...",
            "Retrieving 30m SRTM Elevation Data...",
            "Running Forecasting Tournament...",
            "Calculating Suitability for ${searchQuery.ifEmpty { "Selected Plot" }}..."
        )
    }

    // Telemetry Cycle and Navigation Simulation
    LaunchedEffect(isLoading) {
        if (isLoading) {
            currentStepIndex = 0
            // Increment telemetry steps
            for (i in telemetrySteps.indices) {
                currentStepIndex = i
                delay(1000)
            }
            onConfirmLocation()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main Screen Content
        Scaffold(
            modifier = Modifier.blur(if (isLoading) 12.dp else 0.dp),
            containerColor = LightMint,
            bottomBar = {
                Surface(
                    color = LightMint,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { isLoading = true },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Confirm Farm Location",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp)),
                    placeholder = { Text("Search district or village...", fontSize = 15.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    trailingIcon = { Icon(Icons.Outlined.Mic, contentDescription = null, tint = ForestGreen) },
                    shape = RoundedCornerShape(28.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = ForestGreen,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                // GPS Button Card with Pulse
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            color = LightMint
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.GpsFixed,
                                    contentDescription = null,
                                    tint = ForestGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Use Current GPS Location",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF1A1C1E)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Accurate to ~15m",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 0.3f,
                                    targetValue = 1.0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1200, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "alpha"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .alpha(alpha)
                                        .background(ForestGreen, CircleShape)
                                )
                            }
                        }
                    }
                }

                // Interactive Map Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFC8E6C9))
                        ) {
                            Box(
                                modifier = Modifier.align(Alignment.Center),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = ForestGreen,
                                    modifier = Modifier.size(52.dp).offset(y = (-20).dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .offset(y = (-22).dp)
                                        .background(Color.White, CircleShape)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MapControlButton(icon = Icons.Default.Layers)
                            MapControlButton(icon = Icons.Default.Add)
                            MapControlButton(icon = Icons.Default.Remove)
                        }

                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Height, // SRTM Elevation focus
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "North Central Province Agrarian Basin",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Disclaimer
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SoftRed,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = WarningRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "GPS measures regional environmental data. For precise nutrient mapping, a local soil chemistry test is recommended.",
                            fontSize = 13.sp,
                            color = WarningRed,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Agro-Climatic Intelligence Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Agro-Climatic Intelligence",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF1A1C1E)
                            )
                            Surface(
                                color = Color(0xFFDCF7E3),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "Verified Zone",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = ForestGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        IntelligenceRow(
                            icon = Icons.Default.Public,
                            label = "District",
                            value = "Anuradhapura (Dry Zone)"
                        )
                        Divider(color = Color.Black.copy(alpha = 0.05f), thickness = 1.dp)
                        IntelligenceRow(
                            icon = Icons.Default.Eco,
                            label = "Agro-Ecological Zone",
                            value = "DL1b"
                        )
                        Divider(color = Color.Black.copy(alpha = 0.05f), thickness = 1.dp)
                        IntelligenceRow(
                            icon = Icons.Default.WaterDrop,
                            label = "Rainfall Pattern",
                            value = "Yala / Maha seasons"
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // 3. The Loading Overlay (Satellite Scan)
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                val infiniteTransition = rememberInfiniteTransition(label = "SatelliteScan")
                
                // Scanning Laser Line Animation
                val laserOffset by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "LaserOffset"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = (laserOffset * screenHeight.toPx()) - (screenHeight.toPx() / 2)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    listOf(Color.Transparent, Color(0xFFFFB74D), Color.Transparent)
                                )
                            )
                    )
                }

                // Center Telemetry Display
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "PulseIcon"
                    )

                    Icon(
                        imageVector = Icons.Default.SettingsInputAntenna,
                        contentDescription = null,
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier
                            .size(72.dp)
                            .graphicsLayer(alpha = pulseAlpha)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = telemetrySteps[currentStepIndex],
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 48.dp),
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MapControlButton(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        IconButton(onClick = { /* Interactive map logic */ }) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.DarkGray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun IntelligenceRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = Color(0xFFF8F9FA)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ForestGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = label, color = Color.Gray, fontSize = 12.sp)
            Text(
                text = value,
                fontWeight = FontWeight.ExtraBold, // Highlighted scientific codes
                fontSize = 15.sp,
                color = Color(0xFF1A1C1E),
                fontFamily = FontFamily.Monospace, // Scientific Data look
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LocationSelectionScreenPreview() {
    LocationSelectionScreen(onConfirmLocation = {})
}
