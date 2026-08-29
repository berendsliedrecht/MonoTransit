package com.berend.transit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.search_bar.SearchBarDefaultsMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD

data class PickerEntry<T>(
    val label: String,
    val sublabel: String?,
    val value: T,
    val favorite: Boolean = false,
)

/** Full-screen search list; the caller owns the query state and supplies matching entries. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun <T> SearchPicker(
    placeholder: String,
    query: String,
    onQueryChange: (String) -> Unit,
    entries: List<PickerEntry<T>>,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    onToggleFavorite: ((T) -> Unit)? = null,
    onLongPress: ((T) -> Unit)? = null,
) {
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
            title = {
                SearchBarDefaultsMMD.InputField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = { },
                    expanded = true,
                    onExpandedChange = { },
                    placeholder = { TextMMD(placeholder) },
                )
            },
        )

        LazyColumnMMD(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            items(entries.size) { index ->
                val entry = entries[index]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .combinedClickable(
                                onClick = { onSelect(entry.value) },
                                onLongClick = onLongPress?.let { handler -> { handler(entry.value) } },
                            )
                            .padding(vertical = if (entry.sublabel == null) 14.dp else 10.dp),
                    ) {
                        TextMMD(text = entry.label, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        entry.sublabel?.let { TextMMD(text = it, fontSize = 13.sp) }
                    }
                    if (onToggleFavorite != null) {
                        IconButton(onClick = { onToggleFavorite(entry.value) }) {
                            Icon(
                                imageVector = if (entry.favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = if (entry.favorite) "Remove favorite" else "Add favorite",
                            )
                        }
                    }
                }
                if (index < entries.lastIndex) HorizontalDividerMMD()
            }
        }
    }
}
