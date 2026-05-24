package com.agarthavision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.agarthavision.ui.navigation.AgarthaNavGraph
import com.agarthavision.ui.theme.AgarthaVisionTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * App entry point. Hosts the [AgarthaNavGraph]; per-screen ViewModels are
 * injected by Hilt where the screens are composed (Login, Capture, etc.).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgarthaVisionTheme {
                AgarthaNavGraph()
            }
        }
    }
}
