package com.cropexpert.app.ui.farm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cropexpert.app.data.local.FarmEntity
import com.cropexpert.app.data.local.SoilSampleEntity
import com.cropexpert.app.domain.model.FarmTerrainState
import com.cropexpert.app.ui.location.areaSquareMeters
import com.cropexpert.app.ui.location.parseBoundaryJson
import com.cropexpert.app.ui.theme.ForestGreen
import com.google.android.gms.maps.model.LatLng
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun MyFarmMapCard(
    farm: FarmEntity?,
    samples: List<SoilSampleEntity>,
    terrainState: FarmTerrainState,
    onMapLongClick: (LatLng) -> Unit,
    onEditLocation: () -> Unit,
    onRetryTerrain: () -> Unit
) {
    val boundaryPoints = remember(farm?.boundaryJson) {
        parseBoundaryJson(farm?.boundaryJson)
    }
    val hasFieldBoundary = boundaryPoints.size >= 3
    val area = remember(boundaryPoints) { boundaryPoints.areaSquareMeters() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (hasFieldBoundary) "3D FIELD MODEL" else "FIELD MODEL",
                        color = ForestGreen,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (farm?.latitude != null && farm.longitude != null) {
                        Text(
                            String.format(Locale.US, "%.5f, %.5f", farm.latitude, farm.longitude),
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
                OutlinedButton(onClick = onEditLocation) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = ForestGreen)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when {
                            farm?.latitude == null -> "Add location"
                            hasFieldBoundary -> "Edit boundary"
                            else -> "Plot farm for 3D view"
                        }
                    )
                }
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .clip(RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFDBE6D3)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!hasFieldBoundary) {
                        Text(
                            "Draw your field boundary to generate its clipped 3D terrain model.",
                            color = ForestGreen,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(24.dp)
                        )
                    } else {
                        when (terrainState) {
                            FarmTerrainState.Empty, FarmTerrainState.Loading -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = ForestGreen)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Loading clipped satellite imagery and elevation…", color = ForestGreen)
                                }
                            }
                            is FarmTerrainState.Error -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(20.dp)
                                ) {
                                    Text(terrainState.message, color = ForestGreen)
                                    Button(
                                        onClick = onRetryTerrain,
                                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                                    ) {
                                        Text("Retry terrain")
                                    }
                                }
                            }
                            is FarmTerrainState.Ready -> {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { context ->
                                        TerrainSurfaceView(context, onMapLongClick).apply {
                                            showTerrain(terrainState.terrain, boundaryPoints)
                                        }
                                    },
                                    update = { view ->
                                        view.showTerrain(terrainState.terrain, boundaryPoints)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            if (hasFieldBoundary && terrainState is FarmTerrainState.Ready) {
                Text(
                    "Drag to orbit · pinch to zoom · long-press inside the field to add a soil test. Relief is vertically exaggerated for visibility.",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (area > 0) {
                        String.format(
                            Locale.US,
                            "%.2f ha · %.2f acres",
                            area / 10_000.0,
                            area / 4_046.8564224
                        )
                    } else {
                        "No plotted field boundary"
                    },
                    color = ForestGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    "${samples.size} soil sample${if (samples.size == 1) "" else "s"}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

        }
    }
}

@Composable
fun SoilSamplesSection(
    samples: List<SoilSampleEntity>,
    onDeleteSample: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Soil test points", color = ForestGreen, fontWeight = FontWeight.Bold)
        Text(
            "Saved tests are field notes. They are not yet used to score Crop Advisor recommendations.",
            color = Color.Gray,
            fontSize = 12.sp
        )
        if (samples.isEmpty()) {
            Text("No soil tests saved yet.", color = Color.Gray, fontSize = 13.sp)
        } else {
            samples.forEachIndexed { index, sample ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Science, contentDescription = null, tint = ForestGreen)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Sample ${index + 1} · ${formatSampleDate(sample.sampledAtEpochMillis)}",
                                fontWeight = FontWeight.Bold,
                                color = ForestGreen
                            )
                            sampleSummary(sample)?.let { Text(it, fontSize = 12.sp) }
                            Text(
                                String.format(Locale.US, "%.5f, %.5f", sample.latitude, sample.longitude),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                            if (sample.depthCm.isNotBlank() || sample.source.isNotBlank()) {
                                Text(
                                    listOfNotNull(
                                        sample.depthCm.takeIf(String::isNotBlank)?.let { "${it} cm deep" },
                                        sample.source.takeIf(String::isNotBlank)
                                    ).joinToString(" · "),
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                        TextButton(onClick = { onDeleteSample(sample.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete soil sample")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SoilSampleEntryDialog(
    point: LatLng,
    onDismiss: () -> Unit,
    onSave: (SoilSampleEntity) -> Unit
) {
    var sampledDate by remember { mutableStateOf(sampleDateFormat().format(Date())) }
    var depth by remember { mutableStateOf("") }
    var ph by remember { mutableStateOf("") }
    var nitrogen by remember { mutableStateOf("") }
    var phosphorus by remember { mutableStateOf("") }
    var potassium by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add soil test point") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    String.format(Locale.US, "%.5f, %.5f", point.latitude, point.longitude),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
                SoilEntryField("Test date (YYYY-MM-DD)", sampledDate) { sampledDate = it }
                SoilEntryField("Sample depth (cm)", depth) { depth = it }
                SoilEntryField("Soil pH (0-14)", ph, KeyboardType.Decimal) { ph = it }
                SoilEntryField("Nitrogen (value and unit)", nitrogen) { nitrogen = it }
                SoilEntryField("Phosphorus (value and unit)", phosphorus) { phosphorus = it }
                SoilEntryField("Potassium (value and unit)", potassium) { potassium = it }
                SoilEntryField("Lab / test source", source) { source = it }
                error?.let { Text(it, color = Color(0xFFB71C1C), fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val dateMillis = parseSampleDate(sampledDate.trim())
                val parsedPH = ph.trim().replace(',', '.')
                    .takeIf(String::isNotBlank)?.toDoubleOrNull()
                when {
                    dateMillis == null -> error = "Enter a valid date in YYYY-MM-DD format."
                    ph.isNotBlank() && (parsedPH == null || parsedPH !in 0.0..14.0) ->
                        error = "pH must be a number from 0 to 14."
                    listOf(ph, nitrogen, phosphorus, potassium).none(String::isNotBlank) ->
                        error = "Enter at least one soil-test value."
                    else -> onSave(
                        SoilSampleEntity(
                            latitude = point.latitude,
                            longitude = point.longitude,
                            sampledAtEpochMillis = dateMillis,
                            depthCm = depth.trim(),
                            ph = parsedPH,
                            nitrogen = nitrogen.trim(),
                            phosphorus = phosphorus.trim(),
                            potassium = potassium.trim(),
                            source = source.trim(),
                            notes = ""
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SoilEntryField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next)
    )
}

private fun sampleSummary(sample: SoilSampleEntity): String? =
    listOfNotNull(
        sample.ph?.let { "pH $it" },
        sample.nitrogen.takeIf(String::isNotBlank)?.let { "N $it" },
        sample.phosphorus.takeIf(String::isNotBlank)?.let { "P $it" },
        sample.potassium.takeIf(String::isNotBlank)?.let { "K $it" }
    ).takeIf(List<String>::isNotEmpty)?.joinToString(" · ")

private fun formatSampleDate(epochMillis: Long): String =
    sampleDateFormat().format(Date(epochMillis))

private fun sampleDateFormat(): SimpleDateFormat =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }

private fun parseSampleDate(value: String): Long? {
    val format = sampleDateFormat()
    val position = ParsePosition(0)
    val parsed = format.parse(value, position) ?: return null
    return parsed.time.takeIf { position.index == value.length && format.format(parsed) == value }
}
