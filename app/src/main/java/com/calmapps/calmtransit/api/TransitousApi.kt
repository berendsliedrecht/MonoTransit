package com.calmapps.calmtransit.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Transitous (https://transitous.org): free community-run MOTIS routing over aggregated GTFS.
 * Door-to-door multimodal planning, no API key; intended for non-commercial use.
 */
interface TransitousApi {

    /** Free-text search for stops, addresses and places. */
    @GET("api/v1/geocode")
    suspend fun geocode(@Query("text") text: String): List<GeoMatch>

    @GET("api/v3/plan")
    suspend fun plan(
        @Query("fromPlace") fromPlace: String,
        @Query("toPlace") toPlace: String,
        @Query("numItineraries") numItineraries: Int = 5,
        @Query("time") time: String? = null,
    ): PlanResponse
}

fun createTransitousApi(): TransitousApi = Retrofit.Builder()
    .baseUrl("https://api.transitous.org/")
    .client(
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        // Transitous policy: identify app, version and a contact method
                        .header(
                            "User-Agent",
                            "Transit/0.1.0 (https://github.com/berendsliedrecht/eink-transport; berendcsliedrecht@gmail.com)",
                        )
                        .build(),
                )
            }
            .build(),
    )
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(TransitousApi::class.java)

data class GeoMatch(
    val type: String? = null, // STOP, ADDRESS or PLACE
    val name: String? = null,
    val id: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val areas: List<GeoArea> = emptyList(),
) {
    val city: String? get() = areas.firstOrNull { it.default }?.name

    /** Value for the plan endpoint: stop id when available, else raw coordinates. */
    val place: String? get() = if (type == "STOP" && !id.isNullOrBlank()) id else lat?.let { "$it,$lon" }
}

data class GeoArea(val name: String? = null, val default: Boolean = false)

data class PlanResponse(val itineraries: List<Itinerary> = emptyList())

data class Itinerary(
    val startTime: String? = null,
    val endTime: String? = null,
    val duration: Long = 0, // seconds
    val transfers: Int = 0,
    val legs: List<PlanLeg> = emptyList(),
)

data class PlanLeg(
    val mode: String? = null,
    val routeShortName: String? = null,
    val headsign: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val scheduledStartTime: String? = null,
    val duration: Long = 0, // seconds
    val from: PlanStop? = null,
    val to: PlanStop? = null,
) {
    val isTransit: Boolean get() = mode != "WALK"

    /** "Bus 44", "Metro 51", or the train's own name ("Sprinter", "ECD 9563"). */
    val label: String
        get() = when (mode) {
            "BUS" -> "Bus ${routeShortName.orEmpty()}".trim()
            "TRAM" -> "Tram ${routeShortName.orEmpty()}".trim()
            "SUBWAY" -> "Metro ${routeShortName.orEmpty()}".trim()
            "FERRY" -> "Ferry ${routeShortName.orEmpty()}".trim()
            else -> routeShortName ?: "Train"
        }
}

data class PlanStop(
    val name: String? = null,
    val track: String? = null,
    val cancelled: Boolean = false,
)

private val AMSTERDAM_ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Transitous UTC timestamp as local Dutch time. */
fun String?.toPlanTime(): OffsetDateTime? = this?.let {
    runCatching { OffsetDateTime.parse(it).atZoneSameInstant(AMSTERDAM_ZONE).toOffsetDateTime() }.getOrNull()
}

fun OffsetDateTime?.asClock(): String = this?.format(CLOCK) ?: "--:--"
