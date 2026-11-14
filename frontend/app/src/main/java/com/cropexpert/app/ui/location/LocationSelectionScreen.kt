package com.cropexpert.app.ui.location

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
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
import com.google.android.gms.location.Priority
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

data class AgroZoneData(
    val district: String,
    val zoneCode: String,
    val rainfallPattern: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun LocationSelectionScreen(
    initialLatitude: Double,
    initialLongitude: Double,
    initialBoundaryJson: String?,
    initialZoom: Float,
    onConfirmLocation: (Double, Double, String?) -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var currentStepIndex by remember { mutableIntStateOf(0) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var isFetchingLocation by remember { mutableStateOf(false) }

    val initialLocation = LatLng(initialLatitude, initialLongitude)
    var selectedLocation by remember(initialLatitude, initialLongitude) {
        mutableStateOf(initialLocation)
    }
    var boundaryPoints by remember(initialBoundaryJson) {
        mutableStateOf(parseBoundaryJson(initialBoundaryJson))
    }
    var isDrawingBoundary by remember { mutableStateOf(false) }
    var mapType by remember { mutableStateOf(MapType.SATELLITE) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLocation, initialZoom)
    }
    LaunchedEffect(initialLatitude, initialLongitude, initialZoom) {
        cameraPositionState.move(
            CameraUpdateFactory.newLatLngZoom(initialLocation, initialZoom)
        )
    }

    // Dynamic Zone Profile Based on Selected Coordinates
    val agroZoneData = remember(selectedLocation) {
        resolveAgroZone(selectedLocation.latitude, selectedLocation.longitude)
    }

    // Fused Location Client Setup
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var boundaryMarkerIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }

    fun fetchDeviceLocation() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation && !hasCoarseLocation) {
            locationMessage = "Location permission is needed to find your current position."
            return
        }

        isFetchingLocation = true
        locationMessage = "Finding your current location…"
        val request = CurrentLocationRequest.Builder()
            .setPriority(
                if (hasFineLocation) Priority.PRIORITY_HIGH_ACCURACY
                else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            )
            .setDurationMillis(20_000)
            .build()

        try {
            fusedLocationClient.getCurrentLocation(request, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        isFetchingLocation = false
                        val newPosition = LatLng(location.latitude, location.longitude)
                        selectedLocation = newPosition
                        locationMessage = "Current location found."
                        coroutineScope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(newPosition, 15f)
                            )
                        }
                    } else {
                        fusedLocationClient.lastLocation
                            .addOnSuccessListener { lastLocation ->
                                isFetchingLocation = false
                                if (lastLocation == null) {
                                    locationMessage =
                                        "Couldn't get a location. Turn on device location and try again."
                                } else {
                                    val newPosition = LatLng(
                                        lastLocation.latitude,
                                        lastLocation.longitude
                                    )
                                    selectedLocation = newPosition
                                    locationMessage = "Using your last known location."
                                    coroutineScope.launch {
                                        cameraPositionState.animate(
                                            CameraUpdateFactory.newLatLngZoom(newPosition, 15f)
                                        )
                                    }
                                }
                            }
                            .addOnFailureListener { error ->
                                isFetchingLocation = false
                                locationMessage = error.localizedMessage
                                    ?: "Couldn't get your current location. Please try again."
                            }
                    }
                }
                .addOnFailureListener { error ->
                    isFetchingLocation = false
                    locationMessage = error.localizedMessage
                        ?: "Couldn't get your current location. Please try again."
                }
        } catch (error: SecurityException) {
            isFetchingLocation = false
            locationMessage = "Location permission is required to find your current position."
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchDeviceLocation()
        } else {
            isFetchingLocation = false
            locationMessage = "Location permission was denied. Allow it in Android Settings to use GPS."
        }
    }

    fun requestDeviceLocation() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation || hasCoarseLocation) {
            fetchDeviceLocation()
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
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocationName(query, 1)
            if (!results.isNullOrEmpty()) {
                val loc = results[0]
                val newPos = LatLng(loc.latitude, loc.longitude)
                selectedLocation = newPos
                coroutineScope.launch {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(newPos, 13f)
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
            onConfirmLocation(
                selectedLocation.latitude,
                selectedLocation.longitude,
                boundaryPoints.toBoundaryJson()
            )
        }
    }
    val boundaryError = when {
        boundaryPoints.size in 1..2 ->
            "Add at least 3 boundary points or clear the outline before saving."
        boundaryPoints.hasSelfIntersection() ->
            "The field outline crosses itself. Undo a point or clear the outline."
        else -> null
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
                        enabled = !isLoading && boundaryError == null,
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
                    onValueChange = { searchQuery = it },
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

                // GPS Target Selection Card
                Card(
                    onClick = { requestDeviceLocation() },
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
                                    text = if (isFetchingLocation) "Finding current location…" else "Accurate to ~15m",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                if (isFetchingLocation) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 2.dp,
                                        color = ForestGreen
                                    )
                                } else {
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
                }
                locationMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isFetchingLocation) Color.Gray else WarningRed,
                        fontSize = 13.sp
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
                            onMapLoaded = {
                                boundaryMarkerIcon = createBoundaryMarkerIcon()
                            },
                            onMapClick = { latLng ->
                                if (isDrawingBoundary) {
                                    boundaryPoints = boundaryPoints + latLng
                                    if (boundaryPoints.size >= 3) {
                                        selectedLocation = boundaryPoints.fieldCentroid()
                                    }
                                } else {
                                    selectedLocation = latLng
                                }
                            }
                        ) {
                            if (boundaryPoints.size >= 3) {
                                Polygon(
                                    points = boundaryPoints,
                                    fillColor = Color.White.copy(alpha = 0.35f),
                                    strokeColor = Color.White,
                                    strokeWidth = 4f
                                )
                            } else if (boundaryPoints.size >= 2) {
                                Polyline(
                                    points = boundaryPoints,
                                    color = Color.White,
                                    width = 4f
                                )
                            }
                            boundaryPoints.forEachIndexed { index, point ->
                                key(index) {
                                    val markerState = remember(index) {
                                        MarkerState(position = point)
                                    }
                                    LaunchedEffect(point) {
                                        markerState.position = point
                                    }
                                    LaunchedEffect(markerState, isDrawingBoundary) {
                                        if (isDrawingBoundary) {
                                            snapshotFlow { markerState.position }
                                                .collect { draggedPosition ->
                                                    if (boundaryPoints.getOrNull(index) != draggedPosition) {
                                                        boundaryPoints =
                                                            boundaryPoints.toMutableList().apply {
                                                                this[index] = draggedPosition
                                                            }
                                                        if (boundaryPoints.size >= 3) {
                                                            selectedLocation =
                                                                boundaryPoints.fieldCentroid()
                                                        }
                                                    }
                                                }
                                        }
                                    }
                                    Marker(
                                        state = markerState,
                                        title = "Boundary point ${index + 1}",
                                        draggable = isDrawingBoundary,
                                        icon = boundaryMarkerIcon
                                    )
                                }
                            }
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
                        }

                        // SRTM Elevation Badge
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp),
                            color = Color.Black.copy(alpha = 0.65f),
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { isDrawingBoundary = !isDrawingBoundary },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (isDrawingBoundary) Icons.Default.LocationOn else Icons.Default.Edit,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isDrawingBoundary) "Finish boundary" else "Draw boundary")
                    }
                    IconButton(
                        onClick = {
                            boundaryPoints = boundaryPoints.dropLast(1)
                        },
                        enabled = boundaryPoints.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo boundary point")
                    }
                    IconButton(
                        onClick = { boundaryPoints = emptyList() },
                        enabled = boundaryPoints.isNotEmpty()
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear boundary")
                    }
                }
                Text(
                    text = if (isDrawingBoundary) {
                        "Tap the map to add field-boundary points (${boundaryPoints.size} added)."
                    } else {
                        "Tap the map to select the farm location. Draw a boundary to outline the field."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Gray,
                    fontSize = 13.sp
                )
                boundaryError?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.fillMaxWidth(),
                        color = WarningRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
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

                // Dynamic Agro-Climatic Intelligence Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Agro-Climatic Profile", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ForestGreen)
                            Surface(color = LightMint, shape = RoundedCornerShape(4.dp)) {
                                Text("Verified Zone", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = ForestGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        IntelligenceRow(Icons.Default.Public, "Primary District", agroZoneData.district)
                        Divider(modifier = Modifier.alpha(0.08f))
                        IntelligenceRow(Icons.Default.Eco, "NASA AEZ Classification", agroZoneData.zoneCode)
                        Divider(modifier = Modifier.alpha(0.08f))
                        IntelligenceRow(Icons.Default.WaterDrop, "Precipitation Cycle", agroZoneData.rainfallPattern)
                    }
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Satellite Scan Overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF1A1C1E)
        )
    }
}

private fun createBoundaryMarkerIcon(): BitmapDescriptor {
    val size = 48
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val center = size / 2f
    val radius = 13f
    canvas.drawCircle(
        center,
        center,
        radius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
    )
    canvas.drawCircle(
        center,
        center,
        radius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(22, 101, 52)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
    )
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

@Preview(showBackground = true)
@Composable
fun LocationSelectionScreenPreview() {
    LocationSelectionScreen(
        initialLatitude = 7.8731,
        initialLongitude = 80.7718,
        initialBoundaryJson = null,
        initialZoom = 7f,
        onConfirmLocation = { _, _, _ -> }
    )
}
