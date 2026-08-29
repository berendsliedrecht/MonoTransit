package com.berend.transit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
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
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        PlannerScreen(viewModel)
    }
}
