package com.cropexpert.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "soil_samples")
data class SoilSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val sampledAtEpochMillis: Long,
    val depthCm: String,
    val ph: Double?,
    val nitrogen: String,
    val phosphorus: String,
    val potassium: String,
    val source: String,
    val notes: String
)
