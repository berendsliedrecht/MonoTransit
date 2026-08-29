package com.calmapps.calmtransit

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calmapps.calmtransit.api.GeoMatch
import com.calmapps.calmtransit.api.Itinerary
import com.calmapps.calmtransit.api.createTransitousApi
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val AMSTERDAM: ZoneId = ZoneId.of("Europe/Amsterdam")

class TransitViewModel(application: Application) : AndroidViewModel(application) {

    private val transitous by lazy { createTransitousApi() }
    private val prefs by lazy { application.getSharedPreferences("calmtransit", Context.MODE_PRIVATE) }

    var fromPlace by mutableStateOf<GeoMatch?>(null)
    var toPlace by mutableStateOf<GeoMatch?>(null)
    var departAt by mutableStateOf<OffsetDateTime?>(null); private set // null = leave now
    var trips by mutableStateOf<List<Itinerary>>(emptyList()); private set
    var isPlanning by mutableStateOf(false); private set
    var planError by mutableStateOf<String?>(null); private set

    var placeResults by mutableStateOf<List<GeoMatch>>(emptyList()); private set
    var recentPlaces by mutableStateOf<List<GeoMatch>>(emptyList()); private set
    var favoritePlaces by mutableStateOf<List<GeoMatch>>(emptyList()); private set
    private var placeSearchJob: Job? = null

    init {
        recentPlaces = loadPlaces("recent_places")
        favoritePlaces = loadPlaces("favorite_places")
    }

    /** Remember a picked place; most recent first, deduplicated, persisted. */
    fun addRecentPlace(match: GeoMatch) {
        recentPlaces = (listOf(match) + recentPlaces.filterNot { samePlace(it, match) }).take(8)
        savePlaces("recent_places", recentPlaces)
    }

    fun removeRecentPlace(match: GeoMatch) {
        recentPlaces = recentPlaces.filterNot { samePlace(it, match) }
        savePlaces("recent_places", recentPlaces)
    }

    fun isFavorite(match: GeoMatch): Boolean = favoritePlaces.any { samePlace(it, match) }

    fun toggleFavorite(match: GeoMatch) {
        favoritePlaces =
            if (isFavorite(match)) favoritePlaces.filterNot { samePlace(it, match) }
            else favoritePlaces + match
        savePlaces("favorite_places", favoritePlaces)
    }

    private fun samePlace(a: GeoMatch, b: GeoMatch) = a.name == b.name && a.city == b.city

    private fun loadPlaces(key: String): List<GeoMatch> = runCatching {
        Gson().fromJson(prefs.getString(key, null), Array<GeoMatch>::class.java)?.toList()
    }.getOrNull().orEmpty()

    private fun savePlaces(key: String, places: List<GeoMatch>) {
        prefs.edit().putString(key, Gson().toJson(places)).apply()
    }

    fun swapPlaces() {
        val from = fromPlace
        fromPlace = toPlace
        toPlace = from
    }

    /** Shift the departure time; drifting to before now resets to "leave now". */
    fun adjustDeparture(minutes: Long) {
        val now = OffsetDateTime.now(AMSTERDAM)
        val next = (departAt ?: now).plusMinutes(minutes)
        departAt = if (next.isBefore(now)) null else next
    }

    fun resetDeparture() {
        departAt = null
    }

    fun searchPlaces(query: String) {
        placeSearchJob?.cancel()
        if (query.length < 3) {
            placeResults = emptyList()
            return
        }
        placeSearchJob = viewModelScope.launch {
            delay(300)
            // Failures just leave the previous type-ahead results in place
            runCatching { transitous.geocode(query) }.onSuccess { matches ->
                placeResults = matches
                    .filter { it.name != null && it.place != null }
                    .distinctBy { it.name to it.city }
                    .take(10)
            }
        }
    }

    fun planTrip() {
        val from = fromPlace?.place ?: return
        val to = toPlace?.place ?: return
        viewModelScope.launch {
            isPlanning = true
            planError = null
            runCatching {
                transitous.plan(from, to, time = departAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            }
                .onSuccess { trips = it.itineraries }
                .onFailure {
                    planError = it.message ?: "Trip planning failed"
                    trips = emptyList()
                }
            isPlanning = false
        }
    }
}
