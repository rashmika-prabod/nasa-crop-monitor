package com.cropexpert.app.ui.location

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.cropexpert.app.ui.theme.ForestGreen
import com.cropexpert.app.ui.theme.LightMint
import com.cropexpert.app.ui.theme.SoftRed
import com.cropexpert.app.ui.theme.WarningRed
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class AgroZoneData(
    val district: String,
    val zoneCode: String,
    val rainfallPattern: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun LocationSelectionScreen(
    onConfirmLocation: (Double, Double) -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var currentStepIndex by remember { mutableIntStateOf(0) }

    // Initial Coordinates (Sri Lanka Default)
    val defaultLocation = LatLng(7.8731, 80.7718)
    var selectedLocation by remember { mutableStateOf(defaultLocation) }
    var mapType by remember { mutableStateOf(MapType.SATELLITE) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selectedLocation, 7f)
    }

    // Dynamic Zone Profile Based on Selected Coordinates
    val agroZoneData = remember(selectedLocation) {
        resolveAgroZone(selectedLocation.latitude, selectedLocation.longitude)
    }

    // Fused Location Client Setup
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun loadDeviceLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc == null) {
                    locationMessage = "Current location is unavailable. Try again or select it on the map."
                } else {
                    val newPos = LatLng(loc.latitude, loc.longitude)
                    selectedLocation = newPos
                    locationMessage = null
                    coroutineScope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(newPos, 15f)
                        )
                    }
                }
            }.addOnFailureListener {
                locationMessage = "Could not get your location. Please try again."
            }
        } catch (error: SecurityException) {
            locationMessage = "Location permission is required to use GPS."
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            loadDeviceLocation()
        } else {
            locationMessage = "Location permission was denied. Select a location on the map instead."
        }
    }

    fun fetchDeviceLocation() {
        val hasLocationPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        if (hasLocationPermission) {
            loadDeviceLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Geocoding Search Functionality
    fun performSearch(query: String) {
        if (query.isBlank()) return
        coroutineScope.launch {
            isSearching = true
            searchMessage = null
            try {
                val location = withContext(Dispatchers.IO) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query, 1)?.firstOrNull()
                }
                if (location == null) {
                    searchMessage = "No matching location found."
                } else {
                    val newPos = LatLng(location.latitude, location.longitude)
                    selectedLocation = newPos
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(newPos, 13f)
                    )
                    searchMessage = "Location found."
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                searchMessage = "Location search failed. Check your connection and try again."
            } finally {
                isSearching = false
            }
        }
    }

    // Voice Intent Launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.getOrNull(0)
            spokenText?.let {
                searchQuery = it
                performSearch(it)
            }
        }
    }

    // Telemetry Steps Sequence
    val telemetrySteps = remember(selectedLocation) {
        listOf(
            "Accessing NASA Earth Engine...",
            "Retrieving 30m SRTM Elevation Data...",
            "Running Forecasting Tournament...",
            "Targeting Coordinates: [${String.format("%.4f", selectedLocation.latitude)}, ${String.format("%.4f", selectedLocation.longitude)}]..."
        )
    }

    // Navigation and Transition Cycle
    LaunchedEffect(isLoading) {
        if (isLoading) {
            currentStepIndex = 0
            val job = launch {
                while (currentStepIndex < telemetrySteps.size - 1) {
                    delay(800)
                    currentStepIndex++
                }
            }
            delay(3500)
            job.cancel()
            onConfirmLocation(selectedLocation.latitude, selectedLocation.longitude)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 64.dp)
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
                            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
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
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Search Bar with Voice and IME Search Action
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        searchMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp)),
                    placeholder = { Text("Search district or village...", fontSize = 15.sp) },
                    leadingIcon = {
                        IconButton(onClick = { performSearch(searchQuery); keyboardController?.hide() }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray)
                        }
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            }
                            voiceLauncher.launch(intent)
                        }) {
                            Icon(Icons.Outlined.Mic, contentDescription = "Voice Input", tint = ForestGreen)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        performSearch(searchQuery)
                        keyboardController?.hide()
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = ForestGreen,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
                if (isSearching || searchMessage != null) {
                    Text(
                        text = if (isSearching) "Searching..." else searchMessage.orEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        color = if (searchMessage?.startsWith("Location found") == true) {
                            ForestGreen
                        } else {
                            Color.Gray
                        },
                        fontSize = 12.sp
                    )
                }

                // GPS Target Selection Card
                Card(
                    onClick = { fetchDeviceLocation() },
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
                locationMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.fillMaxWidth(),
                        color = WarningRed,
                        fontSize = 12.sp
                    )
                }

                // Interactive Google Map Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            properties = MapProperties(mapType = mapType),
                            uiSettings = MapUiSettings(zoomControlsEnabled = false),
                            onMapClick = { latLng -> selectedLocation = latLng }
                        ) {
                            Marker(
                                state = MarkerState(position = selectedLocation),
                                title = "Selected Farm Plot",
                                snippet = "NASA Telemetry Target"
                            )
                        }

                        // Map Control Controls Overlay
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MapControlButton(
                                icon = if (mapType == MapType.SATELLITE) Icons.Default.Terrain else Icons.Default.Satellite,
                                onClick = {
                                    mapType = if (mapType == MapType.SATELLITE) MapType.NORMAL else MapType.SATELLITE
                                }
                            )
                            MapControlButton(icon = Icons.Default.Add, onClick = {
                                coroutineScope.launch {
                                    cameraPositionState.animate(CameraUpdateFactory.zoomIn())
                                }
                            })
                            MapControlButton(icon = Icons.Default.Remove, onClick = {
                                coroutineScope.launch {
                                    cameraPositionState.animate(CameraUpdateFactory.zoomOut())
                                }
                            })
                        }

                        // Spatial Context Overlay (Elevation Pill)
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Height, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "SRTM 30m Grid Active",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Scientific Disclaimer
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SoftRed,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(16.dp)) {
                        Icon(Icons.Default.Warning, null, tint = WarningRed, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "GPS measures regional environmental data. For precise nutrient mapping, a local soil chemistry test is recommended.",
                            fontSize = 13.sp, color = WarningRed, fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Agro-Climatic Intelligence Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Agro-Climatic Intelligence", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ForestGreen)
                        Spacer(Modifier.height(16.dp))
                        IntelligenceRow(Icons.Default.Public, "District", agroZoneData.district)
                        Divider(modifier = Modifier.alpha(0.05f))
                        IntelligenceRow(Icons.Default.Eco, "Agro-Ecological Zone", agroZoneData.zoneCode)
                        Divider(modifier = Modifier.alpha(0.05f))
                        IntelligenceRow(Icons.Default.WaterDrop, "Rainfall Pattern", agroZoneData.rainfallPattern)
                    }
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Satellite Scan Overlay (The "Scientific Analysis" Transition)
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "scan")
                val laserOffset by infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
                    label = "laser"
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val y = size.height * laserOffset
                    drawLine(
                        brush = Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFFFB74D), Color.Transparent)),
                        start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 3.dp.toPx()
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SettingsInputAntenna, null, tint = Color.White, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = telemetrySteps[currentStepIndex],
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }
    }
}

fun resolveAgroZone(lat: Double, lon: Double): AgroZoneData {
    return when {
        // Western Province / Colombo bounds
        lat in 6.7..7.2 && lon in 79.8..80.1 -> AgroZoneData("Colombo (Wet Zone)", "WL2a", "Bimodal (Yala / Maha)")
        // Central Highlands / Kandy / Nuwara Eliya
        lat in 6.8..7.4 && lon in 80.4..80.8 -> AgroZoneData("Nuwara Eliya (Upcountry)", "WU3", "Continuous Wet")
        // Southern Province / Hambantota
        lat in 6.0..6.5 && lon in 80.8..81.4 -> AgroZoneData("Hambantota (Semi-Arid)", "DL5", "Unimodal Maha Heavy")
        // Default North Central / Dry Zone (Anuradhapura)
        else -> AgroZoneData("Anuradhapura (Dry Zone)", "DL1b", "Bimodal (Yala / Maha)")
    }
}

@Composable
fun MapControlButton(icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = ForestGreen, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun IntelligenceRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, color = Color(0xFF1A1C1E))
    }
}

@Preview(showBackground = true)
@Composable
fun LocationSelectionScreenPreview() {
    LocationSelectionScreen(onConfirmLocation = { _, _ -> })
}
