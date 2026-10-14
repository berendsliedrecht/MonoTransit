package com.berend.transit.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Replacement for LazyColumnMMD, whose scrollbar crashes with
// "Cannot coerce value to an empty range" whenever its canvas is drawn at
// (near) zero height, and swallows swipes when it under-estimates content.
// Plain LazyColumn with a static black thumb: no fade animation to ghost
// on e-ink, and the clamping the MMD version gets wrong is guarded here.
@Composable
fun MonoLazyColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyListScope.() -> Unit,
) {
    val state = rememberLazyListState()
    LazyColumn(
        state = state,
        contentPadding = contentPadding,
        modifier = modifier.drawScrollbar(state),
        content = content,
    )
}

private fun Modifier.drawScrollbar(state: LazyListState): Modifier = drawWithContent {
    drawContent()
    val info = state.layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return@drawWithContent

    // Content height estimated from the average visible item, like any
    // scrollbar over a lazy list.
    val averageItem = visible.sumOf { it.size }.toFloat() / visible.size
    val contentHeight = info.totalItemsCount * averageItem
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    if (contentHeight <= viewport || viewport <= 0f) return@drawWithContent

    val thumbHeight = (viewport * viewport / contentHeight).coerceIn(24.dp.toPx(), viewport)
    val track = (viewport - thumbHeight).coerceAtLeast(0f)
    val scrolled = state.firstVisibleItemIndex * averageItem + state.firstVisibleItemScrollOffset
    val fraction = (scrolled / (contentHeight - viewport)).coerceIn(0f, 1f)
    val width = 3.dp.toPx()
    drawRoundRect(
        color = Color.Black,
        topLeft = Offset(size.width - width, info.viewportStartOffset + track * fraction),
        size = Size(width, thumbHeight),
        cornerRadius = CornerRadius(width / 2),
    )
}
