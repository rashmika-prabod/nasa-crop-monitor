package com.cropexpert.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FarmEntity::class, SoilSampleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class FarmDatabase : RoomDatabase() {
    abstract fun farmDao(): FarmDao

    companion object {
        @Volatile
        private var instance: FarmDatabase? = null

        fun getInstance(context: Context): FarmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FarmDatabase::class.java,
                    "crop_expert_farm.db"
                ).build().also { instance = it }
            }
    }
}
