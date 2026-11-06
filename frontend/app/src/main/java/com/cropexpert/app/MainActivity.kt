package com.cropexpert.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cropexpert.app.ui.dashboard.HomeDashboardScreen
import com.cropexpert.app.ui.location.LocationSelectionScreen
import com.cropexpert.app.ui.splash.SplashScreen
import com.cropexpert.app.ui.theme.ForestGreen
import com.cropexpert.app.ui.theme.LightMint
import com.cropexpert.app.ui.theme.Typography

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object CropAdvisor : Screen("crop_advisor", "Crop Advisor", Icons.Default.Psychology)
    object FuturePlan : Screen("future_plan", "Future Plan", Icons.Default.Timeline)
    object MyFarm : Screen("my_farm", "My Farm", Icons.Default.Agriculture)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val currentRoute = currentDestination?.route

            val items = listOf(
                Screen.Home,
                Screen.CropAdvisor,
                Screen.FuturePlan,
                Screen.MyFarm
            )

            MaterialTheme(typography = Typography) {
                Scaffold(
                    bottomBar = {
                        if (currentRoute != null && currentRoute !in listOf("splash", "location_selection")) {
                            NavigationBar(
                                containerColor = LightMint,
                                tonalElevation = 8.dp
                            ) {
                                items.forEach { screen ->
                                    NavigationBarItem(
                                        icon = { Icon(screen.icon, contentDescription = null) },
                                        label = { Text(screen.label) },
                                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                        onClick = {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = ForestGreen,
                                            selectedTextColor = ForestGreen,
                                            indicatorColor = Color.White,
                                            unselectedIconColor = Color.Gray,
                                            unselectedTextColor = Color.Gray
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "splash",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("splash") {
                            SplashScreen(onNavigateToMain = {
                                navController.navigate("location_selection") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            })
                        }
                        composable("location_selection") {
                            LocationSelectionScreen(onConfirmLocation = {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo("location_selection") { inclusive = true }
                                }
                            })
                        }
                        composable(Screen.Home.route) {
                            HomeDashboardScreen(
                                onNavigateToFuturePlan = {
                                    navController.navigate(Screen.FuturePlan.route)
                                },
                                onNavigateToCropAdvisor = {
                                    navController.navigate(Screen.CropAdvisor.route)
                                }
                            )
                        }
                        composable(Screen.CropAdvisor.route) {
                            PlaceholderScreen("Crop Advisor - Suitability Ranking")
                        }
                        composable(Screen.FuturePlan.route) {
                            PlaceholderScreen("Future Plan - 24-Month Climate Forecasting")
                        }
                        composable(Screen.MyFarm.route) {
                            PlaceholderScreen("My Farm - Agro-Ecological Profile")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LightMint
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall, color = ForestGreen)
        }
    }
}
