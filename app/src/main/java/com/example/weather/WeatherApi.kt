package com.example.weather

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,is_day,weather_code,wind_speed_10m,rain",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,rain_sum,snowfall_sum",
        @Query("timezone") timezone: String = "auto"
    ): ForecastResponse
}

interface GeocodingApi {
    @GET("v1/search")
    suspend fun searchCity(
        @Query("name") name: String,
        @Query("language") language: String = "fa",
        @Query("count") count: Int = 10,
        @Query("format") format: String = "json"
    ): GeocodingResponse
}
