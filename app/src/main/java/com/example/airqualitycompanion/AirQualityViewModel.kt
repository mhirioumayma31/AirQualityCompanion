
package com.example.airqualitycompanion

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.airqualitycompanion.data.AirQuality
import com.example.airqualitycompanion.data.AirQualityRepository
import com.example.airqualitycompanion.data.City
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class AirQualityUiState(
    val isLoading: Boolean = false,
    val airQuality: AirQuality? = null,
    val comparison: AirQuality? = null,
    val favorites: List<City> = emptyList(),
    val error: String? = null,
    val comparisonError: String? = null
)

class AirQualityViewModel(application: Application) :
    AndroidViewModel(application) {

    private val repository = AirQualityRepository()
    private val preferences = application.getSharedPreferences(
        "air_quality_preferences",
        0
    )

    private val _uiState = MutableStateFlow(   //version modifiable
        AirQualityUiState(favorites = readFavorites())
    )
    val uiState: StateFlow<AirQualityUiState> = _uiState.asStateFlow()   //version exposé a la lect

    private var searchJob: Job? = null
    private var comparisonJob: Job? = null

    init {
        searchCity("Castres")
    }

    fun searchCity(query: String) {
        val name = query.trim() //nettoyer le texte
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "Saisis le nom d'une ville."
            )
            return
        }

        searchJob?.cancel()  //demande l'annulation de l'ancienne coroutine
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            try {
                val city = repository.searchCity(name)
                    ?: throw IllegalArgumentException(
                        "Ville introuvable. Vérifie son nom."
                    )

                val airQuality = repository.getAirQuality(city)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    airQuality = airQuality,
                    error = null
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                        ?: "Impossible de charger les données."
                )
            }
        }
    }

    fun refresh() {
        val city = _uiState.value.airQuality?.city
        if (city != null) {
            loadCity(city)
        } else {
            searchCity("Castres")
        }
    }

    fun loadCity(city: City) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            try {
                val data = repository.getAirQuality(city)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    airQuality = data,
                    error = null
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Erreur de chargement : ${e.message}"
                )
            }
        }
    }

    fun toggleFavorite(city: City) {
        val favorites = _uiState.value.favorites.toMutableList()
        val existing = favorites.any {
            it.latitude == city.latitude &&
                    it.longitude == city.longitude
        }

        if (existing) {
            favorites.removeAll {
                it.latitude == city.latitude &&
                        it.longitude == city.longitude
            }
        } else {
            favorites.add(city)
        }

        saveFavorites(favorites)
        _uiState.value = _uiState.value.copy(favorites = favorites)
    }

    fun isFavorite(city: City): Boolean {
        return _uiState.value.favorites.any {
            it.latitude == city.latitude &&
                    it.longitude == city.longitude
        }
    }

    fun compareCity(query: String) {
        val name = query.trim()
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(
                comparisonError = "Saisis une ville à comparer."
            )
            return
        }

        comparisonJob?.cancel()
        comparisonJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                comparison = null,
                comparisonError = null
            )

            try {
                val city = repository.searchCity(name)
                    ?: throw IllegalArgumentException("Ville introuvable.")

                val result = repository.getAirQuality(city)

                _uiState.value = _uiState.value.copy(
                    comparison = result,
                    comparisonError = null
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    comparisonError = e.message
                        ?: "La comparaison est impossible."
                )
            }
        }
    }

    fun clearComparison() {
        _uiState.value = _uiState.value.copy(
            comparison = null,
            comparisonError = null
        )
    }

    fun showError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }

    private fun saveFavorites(cities: List<City>) {
        val array = JSONArray()

        cities.forEach { city ->
            array.put(
                JSONObject().apply {
                    put("name", city.name)
                    put("country", city.country)
                    put("latitude", city.latitude)
                    put("longitude", city.longitude)
                }
            )
        }

        preferences.edit()
            .putString("favorites", array.toString())
            .apply()
    }

    private fun readFavorites(): List<City> {
        val raw = preferences.getString("favorites", null)
            ?: return emptyList()

        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                City(
                    name = item.getString("name"),
                    country = item.optString("country", ""),
                    latitude = item.getDouble("latitude"),
                    longitude = item.getDouble("longitude")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}