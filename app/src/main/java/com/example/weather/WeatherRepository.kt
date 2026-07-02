package com.example.weather

import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object WeatherRepository {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val forecastApi = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OpenMeteoApi::class.java)

    private val geocodingApi = Retrofit.Builder()
        .baseUrl("https://geocoding-api.open-meteo.com/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GeocodingApi::class.java)

    suspend fun searchCity(query: String): List<GeocodingResult> = withContext(Dispatchers.IO) {
        try {
            val response = geocodingApi.searchCity(name = query)
            response.results ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getForecast(lat: Double, lon: Double): ForecastResponse? = withContext(Dispatchers.IO) {
        try {
            forecastApi.getForecast(latitude = lat, longitude = lon)
        } catch (e: Exception) {
            null
        }
    }
    
    // Convert WMO weather code to icon and description
    fun getWeatherCodeInfo(code: Int, isDay: Boolean = true): Pair<String, String> {
        return when (code) {
            0, 1 -> if (isDay) "☀️" to "آفتابی" else "🌙" to "صاف"
            2 -> if (isDay) "⛅" to "نیمه ابری" else "☁️" to "نیمه ابری"
            3 -> "☁️" to "ابری"
            45, 48 -> "🌫️" to "مه‌آلود"
            in 51..55 -> "🌧️" to "باران ریزه"
            in 61..65 -> "🌧️" to "بارانی"
            in 71..75 -> "❄️" to "برفی"
            in 95..99 -> "⛈️" to "رعد و برق"
            else -> "❓" to "نامشخص"
        }
    }
}
