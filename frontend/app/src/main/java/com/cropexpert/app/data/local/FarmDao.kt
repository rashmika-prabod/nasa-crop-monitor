package com.cropexpert.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FarmDao {
    @Query("SELECT * FROM farm WHERE id = 1")
    fun observeFarm(): Flow<FarmEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFarm(farm: FarmEntity)

    @Query("SELECT * FROM soil_samples ORDER BY sampledAtEpochMillis DESC, id DESC")
    fun observeSoilSamples(): Flow<List<SoilSampleEntity>>

    @Insert
    suspend fun addSoilSample(sample: SoilSampleEntity)

    @Query("DELETE FROM soil_samples WHERE id = :sampleId")
    suspend fun deleteSoilSample(sampleId: Long)
}
