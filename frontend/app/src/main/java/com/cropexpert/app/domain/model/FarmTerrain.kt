package com.cropexpert.app.domain.model

data class TerrainCoordinate(
    val lat: Double,
    val lon: Double
)

data class FarmTerrainRequest(
    val boundary: List<TerrainCoordinate>
)

data class FarmTerrainResponse(
    val bounds: List<Double>,
    val elevationGrid: List<List<Double?>>,
    val texturePngBase64: String,
    val elevationSource: String,
    val imagerySource: String
)

sealed interface FarmTerrainState {
    data object Empty : FarmTerrainState
    data object Loading : FarmTerrainState
    data class Ready(val terrain: FarmTerrainResponse) : FarmTerrainState
    data class Error(val message: String) : FarmTerrainState
}
