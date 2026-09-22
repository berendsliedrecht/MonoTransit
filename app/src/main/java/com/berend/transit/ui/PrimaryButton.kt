package com.berend.transit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.ButtonMMD

/** Filled button in pure black/white: anything in between dithers on e-ink. */
@Composable
fun PrimaryButtonMMD(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    ButtonMMD(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black,
            contentColor = Color.White,
            disabledContainerColor = Color.White,
            disabledContentColor = Color.Black,
        ),
        border = BorderStroke(2.dp, Color.Black),
        modifier = modifier,
        content = content,
    )
}
