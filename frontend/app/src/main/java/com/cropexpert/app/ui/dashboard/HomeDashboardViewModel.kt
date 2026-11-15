package com.cropexpert.app.ui.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.cropexpert.app.data.repository.NasaWeatherRepository
import com.cropexpert.app.domain.model.AgroTelemetry
import com.cropexpert.app.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HomeDashboardViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: WeatherRepository = NasaWeatherRepository()
) : ViewModel() {

    private val latitude = savedStateHandle.get<String>("lat")?.toDoubleOrNull() ?: 7.8731
    private val longitude = savedStateHandle.get<String>("lon")?.toDoubleOrNull() ?: 80.7718

    private val _telemetry = MutableStateFlow<AgroTelemetry?>(null)
    val telemetry: StateFlow<AgroTelemetry?> = _telemetry.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _lastSuccessfulSync = MutableStateFlow<Long?>(null)
    val lastSuccessfulSync: StateFlow<Long?> = _lastSuccessfulSync.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refreshData()
        viewModelScope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MILLIS)
                refreshData()
            }
        }
    }

    /**
     * Refreshes field conditions for the selected coordinates.
     */
    fun refreshData() {
        if (refreshJob?.isActive == true) return

        refreshJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                repository.getLatestConditions(latitude, longitude).collect { result ->
                    result.onSuccess {
                        _telemetry.value = it
                        _lastSuccessfulSync.value = System.currentTimeMillis()
                    }.onFailure { error ->
                        _errorMessage.value = error.localizedMessage
                            ?: "Could not load farm conditions. Please try again."
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _errorMessage.value = error.localizedMessage
                    ?: "Could not load farm conditions. Please try again."
            } finally {
                _isLoading.value = false
            }
        }
    }

    companion object {
        private const val REFRESH_INTERVAL_MILLIS = 5 * 60 * 1000L

        val Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                if (!modelClass.isAssignableFrom(HomeDashboardViewModel::class.java)) {
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
                return modelClass.cast(
                    HomeDashboardViewModel(extras.createSavedStateHandle())
                )
            }
        }
    }
}
