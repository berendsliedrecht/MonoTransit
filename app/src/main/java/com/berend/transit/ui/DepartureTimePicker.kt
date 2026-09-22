package com.berend.transit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berend.transit.api.asDayLabel
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.time.DatePickerMMD
import com.mudita.mmd.components.time.TimeInputMMD
import com.mudita.mmd.components.time.rememberDatePickerMMDState
import com.mudita.mmd.components.time.rememberTimeInputMMDState
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Pick an exact travel time and date; confirms with null for "leave now". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepartureTimePicker(
    initial: OffsetDateTime?,
    onConfirm: (OffsetDateTime?) -> Unit,
    onDismiss: () -> Unit,
) {
    val start = remember { initial ?: OffsetDateTime.now(ZoneId.systemDefault()) }
    var date by remember { mutableStateOf(start.toLocalDate()) }
    val timeState = rememberTimeInputMMDState(initialHour = start.hour, initialMinute = start.minute)
    var pickingDate by remember { mutableStateOf(false) }

    if (pickingDate) {
        DatePickerScreen(
            initial = date,
            onPick = {
                date = it
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
        return
    }

    BackHandler(onBack = onDismiss)

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBarMMD(
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
            title = { TextMMD("Travel time", fontSize = 20.sp) },
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TimeInputMMD(state = timeState, modifier = Modifier.fillMaxWidth())

            OutlinedButtonMMD(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                TextMMD(date.asDayLabel(), fontSize = 16.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButtonMMD(onClick = { onConfirm(null) }, modifier = Modifier.weight(1f)) {
                    TextMMD("Now", fontSize = 16.sp)
                }
                PrimaryButtonMMD(
                    onClick = {
                        onConfirm(
                            date.atTime(timeState.hour, timeState.minute)
                                .atZone(ZoneId.systemDefault())
                                .toOffsetDateTime(),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    TextMMD("Set", fontSize = 16.sp)
                }
            }
        }
    }
}

// The MMD date picker state tracks dates as UTC midnight millis.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerScreen(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)

    val initialMillis = remember { initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val state = rememberDatePickerMMDState(initialSelectedDateMillis = initialMillis)

    LaunchedEffect(state.selectedDateMillis) {
        val millis = state.selectedDateMillis ?: return@LaunchedEffect
        // The guard suppresses the effect's initial run; re-picking the same
        // date is a no-op, but backing out yields the same result anyway.
        if (millis != initialMillis) {
            onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBarMMD(
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
            title = { TextMMD("Date", fontSize = 20.sp) },
        )

        DatePickerMMD(state = state, title = null, headline = null, showModeToggle = false)
    }
}
