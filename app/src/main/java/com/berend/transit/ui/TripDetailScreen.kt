package com.berend.transit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berend.transit.api.Itinerary
import com.berend.transit.api.PlanLeg
import com.berend.transit.api.PlanStop
import com.berend.transit.api.asClock
import com.berend.transit.api.toPlanTime
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import java.time.Duration

// One fixed-height list item per text row. LazyColumnMMD ignores scroll
// gestures and hides its scrollbar unless item count times the FIRST visible
// item's height exceeds the viewport, so variable item heights make it
// under-estimate the content and swallow swipes on longer trips.
private val ROW_HEIGHT = 52.dp

internal sealed interface LegRow
internal data class HeaderRow(val leg: PlanLeg) : LegRow
internal data class StopRow(
    val time: String?,
    val scheduledTime: String?,
    val stop: PlanStop?,
    val dividerAfter: Boolean,
) : LegRow
internal data class WalkRow(val minutes: Long) : LegRow

internal fun legRows(itinerary: Itinerary): List<LegRow> {
    val lastTransit = itinerary.legs.lastOrNull { it.isTransit }
    return buildList {
        for (leg in itinerary.legs) {
            if (leg.isTransit) {
                add(HeaderRow(leg))
                add(StopRow(leg.startTime, leg.scheduledStartTime, leg.from, dividerAfter = false))
                add(StopRow(leg.endTime, leg.scheduledEndTime, leg.to, dividerAfter = leg !== lastTransit))
            } else if (leg.duration >= 60) {
                add(WalkRow(leg.duration / 60))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(itinerary: Itinerary, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val rows = legRows(itinerary)

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
                    text = "${itinerary.startTime.toPlanTime().asClock()} - ${itinerary.endTime.toPlanTime().asClock()}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            },
        )

        LazyColumnMMD(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item {
                val minutes = itinerary.duration / 60
                val transfers = when (itinerary.transfers) {
                    0 -> "no transfers"
                    1 -> "1 transfer"
                    else -> "${itinerary.transfers} transfers"
                }
                DetailRow {
                    TextMMD("${minutes / 60} h ${minutes % 60} min, $transfers", fontSize = 14.sp)
                }
            }
            items(rows.size) { index ->
                when (val row = rows[index]) {
                    is HeaderRow -> DetailRow {
                        val headsign = row.leg.headsign?.takeIf { it.isNotBlank() }?.let { " to $it" }.orEmpty()
                        TextMMD(
                            text = "${row.leg.label}$headsign",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    is StopRow -> DetailRow(dividerAfter = row.dividerAfter) {
                        StopDetail(row.time, row.scheduledTime, row.stop)
                    }
                    is WalkRow -> DetailRow {
                        TextMMD("Walk ${row.minutes} min", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(dividerAfter: Boolean = false, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT),
    ) {
        content()
        if (dividerAfter) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) { HorizontalDividerMMD() }
        }
    }
}

@Composable
private fun StopDetail(time: String?, scheduledTime: String?, stop: PlanStop?) {
    val actual = time.toPlanTime()
    val scheduled = scheduledTime.toPlanTime()
    val delay = if (actual != null && scheduled != null) Duration.between(scheduled, actual).toMinutes() else 0

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextMMD(actual.asClock(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (delay > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            TextMMD("+$delay", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            TextMMD(stop?.name.orEmpty(), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (stop?.cancelled == true) {
                TextMMD("Cancelled", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        stop?.track?.let { TrackChip(it) }
    }
}

@Composable
private fun TrackChip(track: String) {
    TextMMD(
        text = "Track $track",
        fontSize = 14.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .border(2.dp, Color.Black)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
