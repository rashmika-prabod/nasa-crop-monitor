package com.cropexpert.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cropexpert.app.domain.model.FarmProfile

@Entity(tableName = "farm")
data class FarmEntity(
    @PrimaryKey val id: Int = 1,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val boundaryJson: String? = null,
    val lastSeasonCrop: String = "",
    val harvestLevel: String = "",
    val residueManagement: String = "",
    val nutrientInputs: String = "",
    val recentCrops: String = "",
    val rotationYears: String = "",
    val waterAvailability: String = "",
    val soilPH: String = "",
    val soilTestResults: String = "",
    val soilTestDate: String = "",
    val soilTestSource: String = "",
    val constraints: String = "",
    val priorities: String = ""
) {
    fun toProfile() = FarmProfile(
        lastSeasonCrop = lastSeasonCrop,
        harvestLevel = harvestLevel,
        residueManagement = residueManagement,
        nutrientInputs = nutrientInputs,
        recentCrops = recentCrops,
        rotationYears = rotationYears,
        waterAvailability = waterAvailability,
        soilPH = soilPH,
        soilTestResults = soilTestResults,
        soilTestDate = soilTestDate,
        soilTestSource = soilTestSource,
        constraints = constraints,
        priorities = priorities.split("|").filter(String::isNotBlank)
    )

    fun withProfile(profile: FarmProfile) = copy(
        lastSeasonCrop = profile.lastSeasonCrop,
        harvestLevel = profile.harvestLevel,
        residueManagement = profile.residueManagement,
        nutrientInputs = profile.nutrientInputs,
        recentCrops = profile.recentCrops,
        rotationYears = profile.rotationYears,
        waterAvailability = profile.waterAvailability,
        soilPH = profile.soilPH,
        soilTestResults = profile.soilTestResults,
        soilTestDate = profile.soilTestDate,
        soilTestSource = profile.soilTestSource,
        constraints = profile.constraints,
        priorities = profile.priorities.joinToString("|")
    )
}
