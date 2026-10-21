
package com.example.airqualitycompanion.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AirQualityRepository {
    private val client = HttpClient(Android)
    suspend fun searchCity(query: String): City? =
        withContext(Dispatchers.IO) {
            val response = client.get("https://geocoding-api.open-meteo.com/v1/search") {
                parameter("name", query)
                parameter("count", 1)
                parameter("language", "fr")
                parameter("format", "json") }
            val json = JSONObject(response.bodyAsText())
            val results = json.optJSONArray("results")
            if (results == null || results.length() == 0) { return@withContext null }
            val item = results.getJSONObject(0)
            City(name = item.optString("name", query),
                country = item.optString("country", ""),
                latitude = item.getDouble("latitude"),
                longitude = item.getDouble("longitude"))
        }

    suspend fun getAirQuality(city: City): AirQuality =
        withContext(Dispatchers.IO) {
            val response = client.get(
                "https://air-quality-api.open-meteo.com/v1/air-quality"
            ) {
                parameter("latitude", city.latitude)
                parameter("longitude", city.longitude)
                parameter("current", "pm2_5,pm10,ozone,nitrogen_dioxide,european_aqi")
                parameter("hourly", "pm2_5,pm10,ozone,nitrogen_dioxide")
                parameter("forecast_days", 3)
                parameter("timezone", "auto")
            }

            val json = JSONObject(response.bodyAsText())
            val current = json.optJSONObject("current")
                ?: throw IllegalStateException("Les données actuelles sont indisponibles.")

            val pollutants = listOf(
                Pollutant(
                    name = "Particules fines",
                    symbol = "PM2.5",
                    value = current.numberOrNull("pm2_5"),
                    unit = "µg/m³",
                    description = "Très petites particules pouvant pénétrer profondément dans les poumons."),
                Pollutant(name = "Particules inhalables",
                    symbol = "PM10",
                    value = current.numberOrNull("pm10"),
                    unit = "µg/m³",
                    description = "Particules en suspension pouvant irriter les voies respiratoires."),
                Pollutant(name = "Ozone",
                    symbol = "O₃",
                    value = current.numberOrNull("ozone"),
                    unit = "µg/m³",
                    description = "Polluant pouvant irriter les yeux et les voies respiratoires."),
                Pollutant(
                    name = "Dioxyde d'azote",
                    symbol = "NO₂",
                    value = current.numberOrNull("nitrogen_dioxide"),
                    unit = "µg/m³",
                    description = "Polluant notamment associé au trafic routier et à la combustion."))


            val hourly = json.optJSONObject("hourly")
            val timesJson = hourly?.optJSONArray("time")

            val times = mutableListOf<String>()

            if (timesJson != null) {
                for (i in 0 until timesJson.length()) {
                    times.add(timesJson.optString(i))
                }
            }

            fun readHourlyValues(key: String): List<Double?> {
                val array = hourly?.optJSONArray(key)

                return List(times.size) { index ->
                    if (array == null || index >= array.length() || array.isNull(index)) {
                        null
                    } else {
                        array.optDouble(index, Double.NaN)
                            .takeIf { it.isFinite() }
                    }
                }
            }
            AirQuality(
                city = city,
                aqi = current.numberOrNull("european_aqi")?.toInt(),
                time = current.optString("time", "Inconnue"),
                pollutants = pollutants,
                hourlyTimes = times,
                hourlyPm25 = readHourlyValues("pm2_5"),
                hourlyPm10 = readHourlyValues("pm10"),
                hourlyOzone = readHourlyValues("ozone"),
                hourlyNo2 = readHourlyValues("nitrogen_dioxide"),)}
    fun close() {
        client.close() }
    private fun JSONObject.numberOrNull(key: String): Double? {
        if (isNull(key) || !has(key)) return null
        val value = optDouble(key, Double.NaN)
        return value.takeIf { it.isFinite() }
    }
}