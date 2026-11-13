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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.cropexpert.app.ui.theme.ForestGreen
import com.cropexpert.app.ui.theme.LightMint
import com.cropexpert.app.ui.theme.SoftRed
import com.cropexpert.app.ui.theme.WarningRed
import com.cropexpert.app.domain.model.AgroLocationProfile
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun LocationSelectionScreen(
    onConfirmLocation: (Double, Double, String?) -> Unit,
    viewModel: LocationProfileViewModel = viewModel(factory = LocationProfileViewModel.Factory)
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val locationProfileState by viewModel.uiState.collectAsState()
    var showIntelligenceInfo by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var currentStepIndex by remember { mutableIntStateOf(0) }
    var isDrawingField by remember { mutableStateOf(false) }
    var isAdjustingField by remember { mutableStateOf(false) }
    var fieldPoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var selectedFieldPointIndex by remember { mutableStateOf<Int?>(null) }
    var fieldMessage by remember { mutableStateOf<String?>(null) }
    var showFieldHelp by remember { mutableStateOf(false) }
    var searchFieldBounds by remember { mutableStateOf<Rect?>(null) }

    // Initial Coordinates (Sri Lanka Default)
    val defaultLocation = LatLng(7.8731, 80.7718)
    var selectedLocation by remember { mutableStateOf(defaultLocation) }
    var mapType by remember { mutableStateOf(MapType.SATELLITE) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selectedLocation, 7f)
    }

    LaunchedEffect(selectedLocation, isPreview, isDrawingField) {
        if (!isPreview && !isDrawingField) {
            viewModel.resolveLocation(selectedLocation.latitude, selectedLocation.longitude)
        }
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
                    fieldPoints = emptyList()
                    isDrawingField = false
                    isAdjustingField = false
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
                    fieldPoints = emptyList()
                    isDrawingField = false
                    isAdjustingField = false
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
            "Preparing field conditions...",
            "Looking up district and SRTM elevation...",
            "Preparing field location...",
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
                fieldPoints.toBoundaryJson()
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .imePadding()
                .blur(if (isLoading) 12.dp else 0.dp),
            containerColor = LightMint,
            bottomBar = {
                Surface(
                    color = LightMint,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            val areaHa = fieldPoints.areaSquareMeters() / 10_000.0
                            if (fieldPoints.isNotEmpty() && fieldPoints.size < 3) {
                                fieldMessage = "Add at least 3 boundary points or clear the outline to use a map pin."
                            } else if (areaHa > 5_000) {
                                fieldMessage = "The outline is over 5,000 ha. Zoom in and draw only the crop field."
                            } else {
                                isLoading = true
                            }
                        },
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
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .pointerInput(searchFieldBounds) {
                        detectTapGestures { tapPosition ->
                            if (searchFieldBounds?.contains(tapPosition) != true) {
                                focusManager.clearFocus()
                            }
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
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
                        .onGloballyPositioned { searchFieldBounds = it.boundsInParent() },
                    shape = RoundedCornerShape(28.dp),
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
                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(
                            state = rememberScrollState(),
                            enabled = !isDrawingField && !isAdjustingField
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Use Current GPS Location",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF1A1C1E)
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
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = false,
                                scrollGesturesEnabled = !isAdjustingField
                            ),
                            onMapClick = { latLng ->
                                if (isDrawingField) {
                                    if (fieldPoints.size < 50) {
                                        val updatedPoints = fieldPoints + latLng
                                        if (updatedPoints.hasSelfIntersection()) {
                                            fieldMessage = "Boundary edges cannot cross. Undo the last point and try again."
                                        } else {
                                            fieldPoints = updatedPoints
                                            if (fieldPoints.size >= 3) {
                                                selectedLocation = fieldPoints.fieldCentroid()
                                            }
                                            fieldMessage = null
                                        }
                                    } else {
                                        fieldMessage = "A field outline can contain up to 50 points."
                                    }
                                } else if (isAdjustingField) {
                                    val index = selectedFieldPointIndex
                                    if (index == null || index !in fieldPoints.indices) {
                                        fieldMessage = "Select a boundary point, then tap where to move it."
                                    } else {
                                        val updatedPoints = fieldPoints.toMutableList()
                                        updatedPoints[index] = latLng
                                        if (updatedPoints.hasSelfIntersection()) {
                                            fieldMessage = "That move would cross the boundary. The point was not moved."
                                        } else {
                                            fieldPoints = updatedPoints
                                            if (updatedPoints.size >= 3) {
                                                selectedLocation = updatedPoints.fieldCentroid()
                                            }
                                            fieldMessage = "Point ${index + 1} moved."
                                        }
                                    }
                                } else if (!isAdjustingField) {
                                    fieldPoints = emptyList()
                                    selectedLocation = latLng
                                    fieldMessage = null
                                }
                            }
                        ) {
                            if (fieldPoints.size >= 2) {
                                Polyline(
                                    points = fieldPoints,
                                    color = Color.White,
                                    width = 4f
                                )
                            }
                            if (fieldPoints.size >= 3) {
                                Polygon(
                                    points = fieldPoints,
                                    fillColor = Color(0x22FFFFFF),
                                    strokeColor = Color.White,
                                    strokeWidth = 5f
                                )
                            }
                            if (fieldPoints.isNotEmpty()) {
                                fieldPoints.forEachIndexed { index, point ->
                                    val isSelected = selectedFieldPointIndex == index
                                    val fieldVertexIcon = rememberFieldVertexIcon(isSelected)
                                    val markerState = remember(index) {
                                        MarkerState(position = point)
                                    }
                                    var dragStartPoint by remember(index) { mutableStateOf(point) }
                                    LaunchedEffect(point) {
                                        if (markerState.dragState != DragState.DRAG) {
                                            markerState.position = point
                                        }
                                    }
                                    LaunchedEffect(markerState, index) {
                                        snapshotFlow { markerState.dragState to markerState.position }
                                            .collect { (dragState, position) ->
                                                val currentPoints = fieldPoints
                                                if (index !in currentPoints.indices) return@collect
                                                if (dragState == DragState.START) {
                                                    dragStartPoint = currentPoints[index]
                                                }
                                                if (dragState == DragState.DRAG || dragState == DragState.END) {
                                                    val updatedPoints = currentPoints.toMutableList()
                                                    updatedPoints[index] = position
                                                    if (
                                                        dragState == DragState.END &&
                                                        updatedPoints.hasSelfIntersection()
                                                    ) {
                                                        val revertedPoints = currentPoints.toMutableList()
                                                        revertedPoints[index] = dragStartPoint
                                                        fieldPoints = revertedPoints
                                                        markerState.position = dragStartPoint
                                                        fieldMessage = "That move would cross the boundary. The point was returned to its previous position."
                                                    } else {
                                                        fieldPoints = updatedPoints
                                                        if (updatedPoints.size >= 3) {
                                                            selectedLocation = updatedPoints.fieldCentroid()
                                                        }
                                                        fieldMessage = null
                                                    }
                                                }
                                            }
                                    }
                                    Marker(
                                        state = markerState,
                                        title = "Field boundary point ${index + 1}",
                                        snippet = "Drag to adjust this part of the boundary",
                                        draggable = true,
                                        icon = fieldVertexIcon,
                                        anchor = Offset(0.5f, 0.5f),
                                        zIndex = if (isSelected) 2f else 1f,
                                        onClick = {
                                            if (isAdjustingField) {
                                                selectedFieldPointIndex = index
                                                fieldMessage = "Point ${index + 1} selected. Tap the map to move it."
                                            }
                                            true
                                        }
                                    )
                                }
                            }
                            if (fieldPoints.size < 3) {
                                Marker(
                                    state = MarkerState(position = selectedLocation),
                                    title = "Selected Farm Plot",
                                    snippet = "Tap Draw field to outline any field shape"
                                )
                            }
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
                                    text = "SRTM ELEVATION LAYER",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Optional: outline your field",
                                fontWeight = FontWeight.Bold,
                                color = ForestGreen
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Box {
                                TextButton(
                                    onClick = { showFieldHelp = !showFieldHelp },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Text(
                                        "!",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.LightGray
                                    )
                                }
                                DropdownMenu(
                                    expanded = showFieldHelp,
                                    onDismissRequest = { showFieldHelp = false }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .widthIn(max = 280.dp)
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            if (isDrawingField) {
                                                "Zoom in and tap 3–50 points around the field edge, then select Finish outline."
                                            } else if (isAdjustingField) {
                                                "Tap a boundary point to select it, then tap its new position on the map. You can also drag a point."
                                            } else {
                                                "Draw a boundary by tapping points around your field. Use Adjust shape to reposition points."
                                            },
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            "Satellite weather products are much coarser than a farm. The field outline improves spatial sampling but cannot create detail the source data does not contain.",
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (!isDrawingField) {
                                        fieldPoints = emptyList()
                                        selectedFieldPointIndex = null
                                        fieldMessage = null
                                        isAdjustingField = false
                                    } else {
                                        isAdjustingField = false
                                        selectedFieldPointIndex = null
                                    }
                                    isDrawingField = !isDrawingField
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                            ) {
                                Text(if (isDrawingField) "Finish outline" else "Draw field")
                            }
                            if (fieldPoints.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        isDrawingField = false
                                        isAdjustingField = !isAdjustingField
                                        selectedFieldPointIndex = null
                                        fieldMessage = null
                                    },
                                    enabled = !isDrawingField
                                ) {
                                    Text(
                                        if (isAdjustingField) "Done adjusting" else "Adjust shape",
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        if (fieldPoints.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(onClick = {
                                    fieldPoints = fieldPoints.dropLast(1)
                                    if (fieldPoints.size >= 3) {
                                        selectedLocation = fieldPoints.fieldCentroid()
                                    }
                                    if (fieldPoints.isEmpty()) {
                                        isAdjustingField = false
                                    }
                                    if (selectedFieldPointIndex?.let { it >= fieldPoints.size } == true) {
                                        selectedFieldPointIndex = null
                                    }
                                    fieldMessage = null
                                }, modifier = Modifier.weight(1f)) {
                                    Text("Undo point", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = {
                                        fieldPoints = emptyList()
                                        isDrawingField = false
                                        isAdjustingField = false
                                        selectedFieldPointIndex = null
                                        fieldMessage = null
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Clear", maxLines = 1)
                                }
                            }
                        }
                        if (fieldPoints.size >= 3) {
                            val areaHa = fieldPoints.areaSquareMeters() / 10_000.0
                            Text(
                                "${fieldPoints.size} points · ${String.format(Locale.getDefault(), "%.2f", areaHa)} ha · advisor location is the field center",
                                color = ForestGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (fieldPoints.isNotEmpty()) {
                            Text("${fieldPoints.size}/3 boundary points", color = ForestGreen, fontSize = 12.sp)
                        }
                        fieldMessage?.let { Text(it, color = WarningRed, fontSize = 12.sp) }
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
                            text = "Earth-observation layers are regional estimates, not on-farm nutrient measurements. Soil nutrient levels cannot be determined from the map alone.",
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Agro-Climatic Profile", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ForestGreen)
                                Text(
                                    if (fieldPoints.size >= 3) "Based on the outlined field" else "Based on the selected map point",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(onClick = { showIntelligenceInfo = true }) {
                                Icon(Icons.Default.Info, contentDescription = "About agro-climatic profile", tint = ForestGreen)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        when {
                            locationProfileState.isLoading -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ForestGreen)
                                    Text("Looking up this location...", color = Color.Gray, fontSize = 13.sp)
                                }
                            }
                            locationProfileState.errorMessage != null -> {
                                Text(
                                    locationProfileState.errorMessage.orEmpty(),
                                    color = WarningRed,
                                    fontSize = 13.sp
                                )
                                TextButton(onClick = {
                                    viewModel.retry(selectedLocation.latitude, selectedLocation.longitude)
                                }) {
                                    Text("Retry lookup", color = ForestGreen)
                                }
                            }
                            else -> locationProfileState.profile?.let { profile ->
                                LocationProfileDetails(profile)
                            } ?: LocationProfileDetails(null)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
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
        if (showIntelligenceInfo) {
            AlertDialog(
                onDismissRequest = { showIntelligenceInfo = false },
                confirmButton = {
                    TextButton(onClick = { showIntelligenceInfo = false }) {
                        Text("Got it", color = ForestGreen)
                    }
                },
                title = { Text("About this profile", color = ForestGreen) },
                text = {
                    Text(
                        "District is looked up from FAO administrative boundaries. Elevation is sampled from the USGS SRTM elevation dataset at about 30 m resolution. An authoritative Sri Lankan agro-ecological zone boundary layer is not connected yet, so the app will not guess a zone or rainfall pattern."
                    )
                }
            )
        }
    }
}

@Composable
private fun rememberFieldVertexIcon(selected: Boolean = false): BitmapDescriptor {
    val density = LocalDensity.current.density
    return remember(density, selected) {
        val size = (48 * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val center = size / 2f
        val radius = size * 0.32f
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
                color = if (selected) {
                    android.graphics.Color.rgb(245, 124, 0)
                } else {
                    android.graphics.Color.rgb(27, 94, 32)
                }
                style = Paint.Style.STROKE
                strokeWidth = size * 0.1f
            }
        )
        BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}

@Composable
private fun LocationProfileDetails(profile: AgroLocationProfile?) {
    val elevation = profile?.elevationMeters?.let { String.format(Locale.getDefault(), "%.0f m", it) }
        ?: "Unavailable"
    IntelligenceRow(Icons.Default.Public, "District", profile?.district ?: "Unavailable")
    Divider(modifier = Modifier.alpha(0.05f))
    IntelligenceRow(Icons.Default.Height, "Elevation · SRTM", elevation)
    Divider(modifier = Modifier.alpha(0.05f))
    IntelligenceRow(Icons.Default.Eco, "Agro-ecological zone", profile?.agroZone ?: "Not mapped")
    Text(
        (profile?.source ?: "District: FAO GAUL 2025 · Elevation: USGS SRTM GL1") +
            "\nAEZ boundary layer not connected; no zone is guessed.",
        color = Color.Gray,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 8.dp)
    )
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
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF1A1C1E),
            textAlign = TextAlign.End
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LocationSelectionScreenPreview() {
    LocationSelectionScreen(onConfirmLocation = { _, _, _ -> })
}

private fun List<LatLng>.toBoundaryJson(): String? {
    if (size < 3) return null
    val ring = if (first() == last()) this else this + first()
    return ring.joinToString(prefix = "[[", postfix = "]]", separator = ",") {
        "[${it.longitude},${it.latitude}]"
    }
}

private fun List<LatLng>.fieldCentroid(): LatLng {
    if (size < 3) {
        return LatLng(map { it.latitude }.average(), map { it.longitude }.average())
    }
    var twiceArea = 0.0
    var longitudeMoment = 0.0
    var latitudeMoment = 0.0
    for (index in indices) {
        val current = this[index]
        val next = this[(index + 1) % size]
        val cross = current.longitude * next.latitude - next.longitude * current.latitude
        twiceArea += cross
        longitudeMoment += (current.longitude + next.longitude) * cross
        latitudeMoment += (current.latitude + next.latitude) * cross
    }
    if (kotlin.math.abs(twiceArea) < 1e-12) {
        return LatLng(map { it.latitude }.average(), map { it.longitude }.average())
    }
    return LatLng(
        latitudeMoment / (3 * twiceArea),
        longitudeMoment / (3 * twiceArea)
    )
}

private fun List<LatLng>.areaSquareMeters(): Double {
    if (size < 3) return 0.0
    val radius = 6_371_008.8
    var total = 0.0
    for (index in indices) {
        val current = this[index]
        val next = this[(index + 1) % size]
        val deltaLongitude = Math.toRadians(next.longitude - current.longitude)
        total += deltaLongitude * (
            2 +
                kotlin.math.sin(Math.toRadians(current.latitude)) +
                kotlin.math.sin(Math.toRadians(next.latitude))
            )
    }
    return kotlin.math.abs(total) * radius * radius / 2
}

private fun List<LatLng>.hasSelfIntersection(): Boolean {
    if (size < 4) return false
    val edges = (0 until size - 1).map { this[it] to this[it + 1] } +
        (last() to first())
    for (firstEdge in edges.indices) {
        for (secondEdge in firstEdge + 1 until edges.size) {
            if (secondEdge == firstEdge + 1 ||
                (firstEdge == 0 && secondEdge == edges.lastIndex)
            ) {
                continue
            }
            if (segmentsIntersect(
                    edges[firstEdge].first,
                    edges[firstEdge].second,
                    edges[secondEdge].first,
                    edges[secondEdge].second
                )
            ) {
                return true
            }
        }
    }
    return false
}

private fun segmentsIntersect(a: LatLng, b: LatLng, c: LatLng, d: LatLng): Boolean {
    fun orientation(first: LatLng, second: LatLng, third: LatLng): Double =
        (second.longitude - first.longitude) * (third.latitude - first.latitude) -
            (second.latitude - first.latitude) * (third.longitude - first.longitude)

    fun onSegment(first: LatLng, second: LatLng, point: LatLng): Boolean =
        point.longitude in minOf(first.longitude, second.longitude)..maxOf(first.longitude, second.longitude) &&
            point.latitude in minOf(first.latitude, second.latitude)..maxOf(first.latitude, second.latitude)

    val o1 = orientation(a, b, c)
    val o2 = orientation(a, b, d)
    val o3 = orientation(c, d, a)
    val o4 = orientation(c, d, b)
    if ((o1 > 0 && o2 < 0 || o1 < 0 && o2 > 0) &&
        (o3 > 0 && o4 < 0 || o3 < 0 && o4 > 0)
    ) {
        return true
    }
    return (o1 == 0.0 && onSegment(a, b, c)) ||
        (o2 == 0.0 && onSegment(a, b, d)) ||
        (o3 == 0.0 && onSegment(c, d, a)) ||
        (o4 == 0.0 && onSegment(c, d, b))
}
