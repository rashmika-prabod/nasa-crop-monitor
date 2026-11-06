package com.cropexpert.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import com.cropexpert.app.ui.splash.SplashScreen
import com.cropexpert.app.ui.location.LocationSelectionScreen
import com.cropexpert.app.ui.map.MainMapScreen
import com.cropexpert.app.ui.theme.Typography

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // App state to manage navigation
            var currentScreen by remember { mutableStateOf("splash") }

            MaterialTheme(typography = Typography) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    when (currentScreen) {
                        "splash" -> {
                            SplashScreen(onNavigateToMain = {
                                currentScreen = "location_selection"
                            })
                        }
                        "location_selection" -> {
                            LocationSelectionScreen(onConfirmLocation = {
                                currentScreen = "main_map"
                            })
                        }
                        "main_map" -> {
                            MainMapScreen()
                        }
                    }
                }
            }
        }
    }
}
