package com.berend.transit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berend.transit.ui.PlannerScreen
import com.mudita.mmd.ThemeMMD

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThemeMMD {
                TransitApp()
            }
        }
    }
}

@Composable
fun TransitApp(viewModel: TransitViewModel = viewModel()) {
    // Exclude the IME inset: the window already pans for the keyboard
    // (adjustPan), and letting it shrink the layout squeezes LazyColumnMMD
    // to zero height, which crashes MMD's scrollbar draw pass.
    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.ime))) {
        PlannerScreen(viewModel)
    }
}
