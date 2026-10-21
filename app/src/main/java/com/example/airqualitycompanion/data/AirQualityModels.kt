
package com.example.airqualitycompanion.data

data class City(
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double
)

data class Pollutant(
    val name: String,
    val symbol: String,
    val value: Double?,
    val unit: String,
    val description: String
)

data class AirQuality(
    val city: City,
    val aqi: Int?,
    val time: String,
    val pollutants: List<Pollutant>,
    val hourlyTimes: List<String>,
    val hourlyPm25: List<Double?>,
    val hourlyPm10: List<Double?> = emptyList(),
    val hourlyOzone: List<Double?> = emptyList(),
    val hourlyNo2: List<Double?> = emptyList()
)
