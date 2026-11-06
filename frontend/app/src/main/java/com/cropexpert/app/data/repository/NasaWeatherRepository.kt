package com.cropexpert.app.data.repository

import com.cropexpert.app.data.api.RetrofitClient
import com.cropexpert.app.domain.model.AgroTelemetry
import com.cropexpert.app.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException

class NasaWeatherRepository : WeatherRepository {

    override fun getLatestConditions(lat: Double, lon: Double): Flow<Result<AgroTelemetry>> = flow {
        try {
            val response = RetrofitClient.api.getFarmAnalysis(lat, lon)
            emit(Result.success(response))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
}
