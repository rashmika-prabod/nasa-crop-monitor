package com.cropexpert.app.ui.dashboard

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TelemetryData(
    val soilMoisture: String = "34%",
    val soilPH: String = "6.2",
    val ambientTemp: String = "29°C",
    val upcomingRain: String = "12 mm"
)

data class LandStatusData(
    val district: String = "Anuradhapura",
    val zone: String = "DL1b",
    val elevation: String = "152m"
)

class HomeDashboardViewModel : ViewModel() {
    private val _telemetry = MutableStateFlow(TelemetryData())
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    private val _landStatus = MutableStateFlow(LandStatusData())
    val landStatus: StateFlow<LandStatusData> = _landStatus.asStateFlow()
}
