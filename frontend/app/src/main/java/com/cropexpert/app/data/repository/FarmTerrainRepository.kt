package com.cropexpert.app.data.repository

import com.cropexpert.app.data.api.RetrofitClient
import com.cropexpert.app.domain.model.FarmTerrainRequest
import com.cropexpert.app.domain.model.FarmTerrainResponse
import com.cropexpert.app.domain.model.TerrainCoordinate

class FarmTerrainRepository {
    suspend fun load(boundary: List<TerrainCoordinate>): FarmTerrainResponse =
        RetrofitClient.api.getFarmTerrain(FarmTerrainRequest(boundary))
}
