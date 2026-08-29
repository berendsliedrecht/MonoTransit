package com.berend.transit.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berend.transit.TransitViewModel
import com.berend.transit.api.GeoMatch
import com.berend.transit.api.Itinerary
import com.berend.transit.api.asClock
import com.berend.transit.api.toPlanTime
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD

private enum class PickTarget { From, To }

@Composable
fun PlannerScreen(viewModel: TransitViewModel) {
    var picking by remember { mutableStateOf<PickTarget?>(null) }
    var selected by remember { mutableStateOf<Itinerary?>(null) }

    val detail = selected
    if (detail != null) {
        TripDetailScreen(itinerary = detail, onBack = { selected = null })
        return
    }

    val pick = picking
    if (pick != null) {
        var query by remember(pick) { mutableStateOf("") }
        fun matches(m: GeoMatch) = query.isBlank() || m.name.orEmpty().contains(query, ignoreCase = true)
        val favorites = viewModel.favoritePlaces.filter(::matches)
        val recents = viewModel.recentPlaces
            .filter(::matches)
            .filterNot { viewModel.isFavorite(it) }
        val results = viewModel.placeResults
            .filter { query.isNotBlank() }
            .filterNot { r -> (favorites + recents).any { it.name == r.name && it.city == r.city } }
        SearchPicker(
            placeholder = if (pick == PickTarget.From) "From stop or address" else "To stop or address",
            query = query,
            onQueryChange = {
                query = it
                viewModel.searchPlaces(it)
            },
            entries = favorites.map { match ->
                PickerEntry(match.name.orEmpty(), listOfNotNull("Favorite", match.city).joinToString(", "), match, favorite = true)
            } + recents.map { match ->
                PickerEntry(match.name.orEmpty(), listOfNotNull("Recent", match.city).joinToString(", "), match)
            } + results.map { match ->
                PickerEntry(match.name.orEmpty(), placeSublabel(match), match)
            },
            onSelect = { match ->
                viewModel.addRecentPlace(match)
                if (pick == PickTarget.From) viewModel.fromPlace = match else viewModel.toPlace = match
                picking = null
            },
            onDismiss = { picking = null },
            onToggleFavorite = viewModel::toggleFavorite,
            onLongPress = viewModel::removeRecentPlace,
        )
        return
    }

    PlannerContent(
        viewModel = viewModel,
        onPickFrom = { picking = PickTarget.From },
        onPickTo = { picking = PickTarget.To },
        onSelectTrip = { selected = it },
    )
}

private fun placeSublabel(match: GeoMatch): String? {
    val type = when (match.type) {
        "STOP" -> "Stop"
        "ADDRESS" -> "Address"
        else -> null
    }
    return listOfNotNull(type, match.city).joinToString(", ").ifBlank { null }
}

@Composable
private fun PlannerContent(
    viewModel: TransitViewModel,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onSelectTrip: (Itinerary) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButtonMMD(onClick = onPickFrom, modifier = Modifier.fillMaxWidth()) {
                    TextMMD(viewModel.fromPlace?.name ?: "From", fontSize = 16.sp)
                }
                OutlinedButtonMMD(onClick = onPickTo, modifier = Modifier.fillMaxWidth()) {
                    TextMMD(viewModel.toPlace?.name ?: "To", fontSize = 16.sp)
                }
            }
            IconButton(onClick = viewModel::swapPlaces) {
                Icon(
                    imageVector = Icons.Outlined.SwapVert,
                    contentDescription = "Swap from and to",
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButtonMMD(onClick = viewModel::toggleArriveBy) {
                TextMMD(if (viewModel.arriveBy) "Arrive" else "Leave", fontSize = 16.sp)
            }
            OutlinedButtonMMD(onClick = viewModel::resetDeparture, modifier = Modifier.weight(1f)) {
                TextMMD(viewModel.departAt?.asClock() ?: "now", fontSize = 16.sp)
            }
            OutlinedButtonMMD(onClick = { viewModel.adjustDeparture(-15) }) {
                TextMMD("-15", fontSize = 16.sp)
            }
            OutlinedButtonMMD(onClick = { viewModel.adjustDeparture(15) }) {
                TextMMD("+15", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        ButtonMMD(
            onClick = viewModel::planTrip,
            enabled = viewModel.fromPlace != null && viewModel.toPlace != null && !viewModel.isPlanning,
            // Pure black/white: anything in between dithers on e-ink
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White,
                disabledContainerColor = Color.White,
                disabledContentColor = Color.Black,
            ),
            border = BorderStroke(2.dp, Color.Black),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextMMD(if (viewModel.isPlanning) "Planning..." else "Plan trip", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        viewModel.planError?.let {
            Spacer(modifier = Modifier.height(8.dp))
            TextMMD("Error: $it")
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumnMMD(contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.weight(1f)) {
            items(viewModel.trips.size) { index ->
                val itinerary = viewModel.trips[index]
                ItineraryRow(itinerary, onClick = { onSelectTrip(itinerary) })
                if (index < viewModel.trips.lastIndex) HorizontalDividerMMD()
            }
        }

        AttributionFooter()
    }
}

// Transitous usage policy requires a visible link to its data sources page,
// and OSM attribution guidelines require crediting OpenStreetMap contributors.
@Composable
private fun AttributionFooter() {
    val context = LocalContext.current
    TextMMD(
        text = "Routing: Transitous, transitous.org/sources\n© OpenStreetMap contributors",
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://transitous.org/sources/")))
                }
            }
            .padding(vertical = 6.dp),
    )
}

@Composable
private fun ItineraryRow(itinerary: Itinerary, onClick: () -> Unit) {
    val minutes = itinerary.duration / 60
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        TextMMD(
            text = "${itinerary.startTime.toPlanTime().asClock()} - ${itinerary.endTime.toPlanTime().asClock()}",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        TextMMD("%d:%02d".format(minutes / 60, minutes % 60), fontSize = 16.sp)
    }
}
