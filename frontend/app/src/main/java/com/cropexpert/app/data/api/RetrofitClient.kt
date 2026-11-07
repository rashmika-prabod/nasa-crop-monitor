package com.cropexpert.app.data.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    /**
     * BASE_URL Configuration:
     * - Using "127.0.0.1" to support ADB Port Forwarding (adb reverse tcp:8000 tcp:8000)
     *   for both Emulator and physical devices.
     */
    private const val BASE_URL = "http://127.0.0.1:8000/"

    val api: NasaApiService by lazy { createApi(BASE_URL) }

    fun createApi(baseUrl: String): NasaApiService {
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)
            .callTimeout(600, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NasaApiService::class.java)
    }
}
