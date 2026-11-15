package com.cropexpert.app.data.repository

import com.cropexpert.app.data.local.FarmDao
import com.cropexpert.app.data.local.FarmEntity
import com.cropexpert.app.data.local.SoilSampleEntity
import com.cropexpert.app.domain.model.FarmProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FarmRepository(private val dao: FarmDao) {
    private val farmWriteMutex = Mutex()

    fun observeFarm(): Flow<FarmEntity?> = dao.observeFarm()

    fun observeSoilSamples(): Flow<List<SoilSampleEntity>> = dao.observeSoilSamples()

    suspend fun saveLocation(latitude: Double, longitude: Double, boundaryJson: String?) {
        farmWriteMutex.withLock {
            val current = dao.observeFarm().first()
            dao.saveFarm(
                (current ?: FarmEntity()).copy(
                    latitude = latitude,
                    longitude = longitude,
                    boundaryJson = boundaryJson
                )
            )
        }
    }

    suspend fun saveProfile(profile: FarmProfile) {
        farmWriteMutex.withLock {
            val current = dao.observeFarm().first() ?: FarmEntity()
            dao.saveFarm(current.withProfile(profile))
        }
    }

    suspend fun addSoilSample(sample: SoilSampleEntity) = dao.addSoilSample(sample)

    suspend fun deleteSoilSample(sampleId: Long) = dao.deleteSoilSample(sampleId)
}
