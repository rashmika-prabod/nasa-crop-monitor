package com.cropexpert.app.ui.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cropexpert.app.data.repository.NasaWeatherRepository
import com.cropexpert.app.domain.model.AgroLocationProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LocationProfileUiState(
    val isLoading: Boolean = false,
    val profile: AgroLocationProfile? = null,
    val errorMessage: String? = null
)

class LocationProfileViewModel(
    private val repository: NasaWeatherRepository = NasaWeatherRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationProfileUiState())
    val uiState: StateFlow<LocationProfileUiState> = _uiState.asStateFlow()
    private var requestJob: Job? = null

    fun resolveLocation(latitude: Double, longitude: Double) {
        requestJob?.cancel()
        _uiState.value = LocationProfileUiState(isLoading = true)
        requestJob = viewModelScope.launch {
            delay(500)
            try {
                val profile = repository.getLocationProfile(latitude, longitude)
                if (isActive) {
                    _uiState.value = LocationProfileUiState(profile = profile)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isActive) {
                    _uiState.value = LocationProfileUiState(
                        errorMessage = error.localizedMessage
                            ?: "Could not load location intelligence. Check the service and try again."
                    )
                }
            }
        }
    }

    fun retry(latitude: Double, longitude: Double) = resolveLocation(latitude, longitude)

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (!modelClass.isAssignableFrom(LocationProfileViewModel::class.java)) {
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
                return LocationProfileViewModel() as T
            }
        }
    }
}
