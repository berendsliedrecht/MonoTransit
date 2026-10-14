package com.berend.transit.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.LocationSearching
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.berend.transit.api.LatLon
import com.berend.transit.api.PlanLeg
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkScreen(leg: PlanLeg, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val points = remember(leg) { leg.legGeometry?.decode().orEmpty() }
    val gps = rememberGpsLocation()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBarMMD(
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
            title = {
                TextMMD(
                    text = "Walk ${leg.duration / 60} min",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            },
        )

        val meters = leg.distance?.roundToInt()
        val destination = leg.to?.name?.takeIf { it.isNotBlank() }
        if (meters != null || destination != null) {
            TextMMD(
                text = listOfNotNull(meters?.let(::distanceLabel), destination?.let { "to $it" }).joinToString(" "),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        WalkMap(
            points = points,
            gps = gps,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

internal data class GpsState(val location: LatLon?, val satellites: Int)

/** Current GPS fix (null until granted and found) plus visible satellite count. */
@Composable
private fun rememberGpsLocation(): GpsState {
    val context = LocalContext.current
    var location by remember { mutableStateOf<LatLon?>(null) }
    var satellites by remember { mutableStateOf(0) }
    var granted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose {}
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(fix: Location) {
                location = LatLon(fix.latitude, fix.longitude)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        // Satellite visibility feeds the "searching" hint on the map
        val gnssCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                satellites = status.satelliteCount
            }
        }
        // Every available provider: raw GPS alone can take minutes for a
        // first fix and never delivers indoors.
        val providers = manager.allProviders.intersect(
            setOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused"),
        )
        runCatching {
            providers
                .mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
                ?.let { location = LatLon(it.latitude, it.longitude) }
            // 3 s and no minimum distance: the 5 m threshold made updates
            // feel stuck while standing still or drifting slowly
            providers.forEach { manager.requestLocationUpdates(it, 3_000L, 0f, listener) }
            manager.registerGnssStatusCallback(gnssCallback, Handler(Looper.getMainLooper()))
        }
        onDispose {
            manager.removeUpdates(listener)
            manager.unregisterGnssStatusCallback(gnssCallback)
        }
    }
    return GpsState(location, satellites)
}

// Monochrome vector map (assets/map_style.json over OpenFreeMap tiles) with
// the walk route, the current position, and a locate-me button.
@Composable
private fun WalkMap(points: List<LatLon>, gps: GpsState, modifier: Modifier = Modifier) {
    val location = gps.location
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        // Texture mode: a SurfaceView does not compose with the surrounding
        // Compose layout on this stack.
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true))
            .apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { ready ->
            map = ready
            ready.uiSettings.isLogoEnabled = false
            ready.setStyle(Style.Builder().fromUri("asset://map_style.json")) { loaded ->
                if (points.size >= 2) {
                    val route = LineString.fromLngLats(points.map { Point.fromLngLat(it.lon, it.lat) })
                    loaded.addSource(GeoJsonSource("route", route))
                    // A wide white halo isolates the route from the base map,
                    // the solid core makes it the boldest line on screen.
                    loaded.addLayer(
                        LineLayer("route-casing", "route").withProperties(
                            lineColor("#ffffff"),
                            lineWidth(15f),
                            lineCap(Property.LINE_CAP_ROUND),
                            lineJoin(Property.LINE_JOIN_ROUND),
                        ),
                    )
                    loaded.addLayer(
                        LineLayer("route-core", "route").withProperties(
                            lineColor("#000000"),
                            lineWidth(7f),
                            lineCap(Property.LINE_CAP_ROUND),
                            lineJoin(Property.LINE_JOIN_ROUND),
                        ),
                    )
                    val bounds = LatLngBounds.Builder()
                        .apply { points.forEach { include(LatLng(it.lat, it.lon)) } }
                        .build()
                    ready.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 64))
                }
                loaded.addSource(GeoJsonSource("position"))
                loaded.addLayer(
                    CircleLayer("position-dot", "position").withProperties(
                        circleRadius(6f),
                        circleColor("#ffffff"),
                        circleStrokeColor("#000000"),
                        circleStrokeWidth(3f),
                    ),
                )
                style = loaded
                map = ready
            }
        }
    }

    LaunchedEffect(style, location) {
        val fix = location ?: return@LaunchedEffect
        style?.getSourceAs<GeoJsonSource>("position")?.setGeoJson(Point.fromLngLat(fix.lon, fix.lat))
    }

    Box(modifier = modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (location == null) {
            TextMMD(
                text = "Searching GPS, ${gps.satellites} satellites",
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color.White)
                    .border(1.dp, Color.Black)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        IconButton(
            onClick = {
                val fix = location ?: return@IconButton
                map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(fix.lat, fix.lon), 16.0))
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(64.dp)
                .background(Color.White, CircleShape)
                .border(2.dp, Color.Black, CircleShape),
        ) {
            Icon(
                // Searching until the first fix lands, then the filled-center icon
                imageVector = if (location != null) Icons.Outlined.MyLocation else Icons.Outlined.LocationSearching,
                contentDescription = "My location",
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
