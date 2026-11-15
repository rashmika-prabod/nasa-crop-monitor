package com.cropexpert.app.ui.farm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cropexpert.app.data.local.FarmDatabase
import com.cropexpert.app.data.local.FarmEntity
import com.cropexpert.app.data.local.SoilSampleEntity
import com.cropexpert.app.data.repository.FarmRepository
import com.cropexpert.app.data.repository.FarmTerrainRepository
import com.cropexpert.app.domain.model.FarmTerrainState
import com.cropexpert.app.domain.model.TerrainCoordinate
import com.cropexpert.app.domain.model.FarmProfile
import com.cropexpert.app.ui.location.parseBoundaryJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FarmViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FarmRepository(FarmDatabase.getInstance(application).farmDao())
    private val terrainRepository = FarmTerrainRepository()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    private val _terrainState = MutableStateFlow<FarmTerrainState>(FarmTerrainState.Empty)
    val terrainState = _terrainState.asStateFlow()
    private var terrainBoundaryKey: String? = null
    private var terrainRequestJob: Job? = null

    val farm: StateFlow<FarmEntity?> = repository.observeFarm().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )
    val soilSamples: StateFlow<List<SoilSampleEntity>> = repository.observeSoilSamples().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    suspend fun getSavedFarm(): FarmEntity? = repository.observeFarm().first()

    fun saveLocation(latitude: Double, longitude: Double, boundaryJson: String?) {
        runDatabaseOperation { repository.saveLocation(latitude, longitude, boundaryJson) }
    }

    fun saveProfile(profile: FarmProfile) {
        runDatabaseOperation { repository.saveProfile(profile) }
    }

    fun addSoilSample(sample: SoilSampleEntity) {
        runDatabaseOperation { repository.addSoilSample(sample) }
    }

    fun deleteSoilSample(sampleId: Long) {
        runDatabaseOperation { repository.deleteSoilSample(sampleId) }
    }

    fun loadTerrain(boundaryJson: String?) {
        if (boundaryJson.isNullOrBlank()) {
            terrainRequestJob?.cancel()
            terrainRequestJob = null
            terrainBoundaryKey = null
            _terrainState.value = FarmTerrainState.Empty
            return
        }
        if (terrainBoundaryKey == boundaryJson &&
            (_terrainState.value is FarmTerrainState.Loading ||
                _terrainState.value is FarmTerrainState.Ready ||
                _terrainState.value is FarmTerrainState.Error)
        ) return

        terrainRequestJob?.cancel()
        terrainBoundaryKey = boundaryJson
        val points = parseBoundaryJson(boundaryJson)
        if (points.size < 3) {
            _terrainState.value = FarmTerrainState.Error(
                "The saved field boundary is invalid. Edit it and draw the boundary again."
            )
            return
        }
        _terrainState.value = FarmTerrainState.Loading
        terrainRequestJob = viewModelScope.launch {
            try {
                val terrain = terrainRepository.load(
                    points.map { TerrainCoordinate(it.latitude, it.longitude) }
                )
                _terrainState.value = FarmTerrainState.Ready(terrain)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _terrainState.value = FarmTerrainState.Error(
                    error.localizedMessage ?: "Could not load field terrain. Check your connection and retry."
                )
            }
        }
    }

    fun retryTerrain(boundaryJson: String?) {
        terrainRequestJob?.cancel()
        terrainBoundaryKey = null
        loadTerrain(boundaryJson)
    }

    private fun runDatabaseOperation(operation: suspend () -> Unit) {
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                operation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _errorMessage.value = error.localizedMessage
                    ?: "Could not save farm information. Please try again."
            }
        }
    }
}
