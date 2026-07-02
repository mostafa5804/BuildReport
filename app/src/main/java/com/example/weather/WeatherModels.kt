package com.example.weather

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeocodingResponse(
    val results: List<GeocodingResult>?
)

@JsonClass(generateAdapter = true)
data class GeocodingResult(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?,
    val admin1: String? // State/Province
)

@JsonClass(generateAdapter = true)
data class ForecastResponse(
    val latitude: Double,
    val longitude: Double,
    val current: CurrentWeather?,
    val daily: DailyWeather?
)

@JsonClass(generateAdapter = true)
data class CurrentWeather(
    val time: String,
    @Json(name = "temperature_2m") val temperature: Double,
    @Json(name = "relative_humidity_2m") val humidity: Int = 0,
    @Json(name = "is_day") val isDay: Int = 1,
    @Json(name = "weather_code") val weatherCode: Int,
    @Json(name = "wind_speed_10m") val windSpeed: Double,
    @Json(name = "rain") val rain: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class DailyWeather(
    val time: List<String>,
    @Json(name = "weather_code") val weatherCode: List<Int>,
    @Json(name = "temperature_2m_max") val maxTemp: List<Double>,
    @Json(name = "temperature_2m_min") val minTemp: List<Double>,
    @Json(name = "sunrise") val sunrise: List<String> = emptyList(),
    @Json(name = "sunset") val sunset: List<String> = emptyList(),
    @Json(name = "uv_index_max") val uvIndexMax: List<Double> = emptyList(),
    @Json(name = "rain_sum") val rainSum: List<Double> = emptyList(),
    @Json(name = "snowfall_sum") val snowfallSum: List<Double> = emptyList()
)

