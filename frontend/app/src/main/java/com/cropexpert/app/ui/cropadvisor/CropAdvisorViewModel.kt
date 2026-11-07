package com.cropexpert.app.ui.cropadvisor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.cropexpert.app.data.repository.CropAdvisorRepository
import com.cropexpert.app.domain.model.CropRecommendation
import com.cropexpert.app.domain.model.CropRecommendationResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RecommendationPriority {
    CLIMATE_RESILIENCE,
    MAXIMUM_PROFIT
}

enum class CropCategoryFilter(val label: String) {
    ALL("All"),
    LEGUMES("Legumes"),
    GRAINS("Grains"),
    VEGETABLES("Vegetables")
}

data class CropAdvisorUiState(
    val isLoading: Boolean = false,
    val isConfigured: Boolean = false,
    val errorMessage: String? = null,
    val response: CropRecommendationResponse? = null,
    val priority: RecommendationPriority = RecommendationPriority.CLIMATE_RESILIENCE,
    val category: CropCategoryFilter = CropCategoryFilter.ALL,
    val visibleRecommendations: List<CropRecommendation> = emptyList()
)

class CropAdvisorViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: CropAdvisorRepository = CropAdvisorRepository()
) : ViewModel() {

    private val latitude = savedStateHandle.get<String>("lat")?.toDoubleOrNull() ?: 7.8731
    private val longitude = savedStateHandle.get<String>("lon")?.toDoubleOrNull() ?: 80.7718

    private val _uiState = MutableStateFlow(CropAdvisorUiState())
    val uiState: StateFlow<CropAdvisorUiState> = _uiState.asStateFlow()
    private var requestJob: Job? = null

    fun loadRecommendations(baseUrl: String, apiKey: String, soilPH: Double? = null) {
        val normalizedUrl = baseUrl.trim().trimEnd('/') + "/"
        if (!normalizedUrl.startsWith("https://", ignoreCase = true) || apiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                isConfigured = false,
                errorMessage = "Enter the HTTPS service URL and API key."
            )
            return
        }

        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                isConfigured = true,
                errorMessage = null,
                response = null,
                visibleRecommendations = emptyList()
            )
            try {
                val response = repository.getRecommendations(
                    latitude,
                    longitude,
                    soilPH,
                    normalizedUrl,
                    apiKey.trim()
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    response = response,
                    errorMessage = null
                ).withFilteredRecommendations()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.localizedMessage
                        ?: "Could not load crop recommendations. Please try again."
                )
            }
        }
    }

    fun setPriority(priority: RecommendationPriority) {
        _uiState.value = _uiState.value.copy(priority = priority)
            .withFilteredRecommendations()
    }

    fun setCategory(category: CropCategoryFilter) {
        _uiState.value = _uiState.value.copy(category = category)
            .withFilteredRecommendations()
    }

    private fun CropAdvisorUiState.withFilteredRecommendations(): CropAdvisorUiState {
        val requestedMode = when (priority) {
            RecommendationPriority.CLIMATE_RESILIENCE -> "climate"
            RecommendationPriority.MAXIMUM_PROFIT -> "profit"
        }
        val recommendations = response?.recommendations.orEmpty()
            .filter { recommendation ->
                recommendation.rankingMode.equals(requestedMode, ignoreCase = true) &&
                    (category == CropCategoryFilter.ALL ||
                    recommendation.category.equals(category.label, ignoreCase = true))
            }
        val sorted = when (priority) {
            RecommendationPriority.CLIMATE_RESILIENCE ->
                recommendations.sortedWith(
                    compareByDescending<CropRecommendation> { it.suitabilityScore }
                        .thenByDescending { it.expectedProfit ?: Double.NEGATIVE_INFINITY }
                )
            RecommendationPriority.MAXIMUM_PROFIT ->
                recommendations.sortedWith(
                    compareByDescending<CropRecommendation> {
                        it.profitPerDay ?: Double.NEGATIVE_INFINITY
                    }.thenBy { it.durationDays }
                )
        }
        return copy(visibleRecommendations = sorted)
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                if (!modelClass.isAssignableFrom(CropAdvisorViewModel::class.java)) {
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
                return modelClass.cast(
                    CropAdvisorViewModel(extras.createSavedStateHandle())
                ) ?: throw IllegalArgumentException(
                    "Could not create ViewModel: ${modelClass.name}"
                )
            }
        }
    }
}
