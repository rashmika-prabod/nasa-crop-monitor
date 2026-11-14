package com.cropexpert.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.cropexpert.app.domain.model.FarmProfile
import com.cropexpert.app.domain.model.CropRecommendationResponse
import com.cropexpert.app.domain.model.soilPHValue
import com.cropexpert.app.ui.cropadvisor.CropAdvisorScreen
import com.cropexpert.app.ui.rotation.MyFarmScreen
import com.cropexpert.app.ui.rotation.FarmProfileSaver
import com.cropexpert.app.ui.rotation.RotationPlanDetailScreen
import com.cropexpert.app.ui.rotation.RotationPlansScreen
import com.cropexpert.app.ui.theme.ForestGreen
import com.cropexpert.app.ui.theme.LightMint

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Field", Icons.Default.Home)
    object CropAdvisor : Screen("crop_advisor", "Crop Advisor", Icons.Default.Psychology)
    object FuturePlan : Screen("future_plan", "Rotation Plans", Icons.Default.Timeline)
    object MyFarm : Screen("my_farm", "My Farm", Icons.Default.Agriculture)
    object PlanDetails : Screen("plan_details/{planId}", "Plan Details", Icons.Default.Timeline) {
        fun createRoute(planId: String) = "plan_details/$planId"
    }
}

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    var farmProfile by rememberSaveable(stateSaver = FarmProfileSaver) {
        mutableStateOf(FarmProfile())
    }
    var advisorResponse by remember { mutableStateOf<CropRecommendationResponse?>(null) }
    val items = listOf(
        Screen.Home,
        Screen.CropAdvisor,
        Screen.FuturePlan,
        Screen.MyFarm
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
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
                            indicatorColor = LightMint,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeDashboardScreen(
                    onNavigateToRotationPlans = {
                        navController.navigate(Screen.FuturePlan.route)
                    }
                )
            }
            composable(Screen.CropAdvisor.route) {
                CropAdvisorScreen(
                    soilPH = farmProfile.soilPHValue(),
                    onUseForRotation = { response ->
                        advisorResponse = response
                        navController.navigate(Screen.FuturePlan.route)
                    }
                )
            }
            composable(Screen.FuturePlan.route) {
                RotationPlansScreen(
                    profile = farmProfile,
                    advisorResponse = advisorResponse,
                    onEditProfile = { navController.navigate(Screen.MyFarm.route) },
                    onOpenCropAdvisor = { navController.navigate(Screen.CropAdvisor.route) },
                    onOpenPlan = { planId ->
                        navController.navigate(Screen.PlanDetails.createRoute(planId))
                    }
                )
            }
            composable(Screen.MyFarm.route) {
                MyFarmScreen(
                    profile = farmProfile,
                    farm = null,
                    soilSamples = emptyList(),
                    onSave = {
                        if (it.soilPH != farmProfile.soilPH) advisorResponse = null
                        farmProfile = it
                    },
                    onEditLocation = {},
                    onAddSoilSample = {},
                    onDeleteSoilSample = {},
                    onOpenCropAdvisor = { navController.navigate(Screen.CropAdvisor.route) }
                )
            }
            composable(Screen.PlanDetails.route) { entry ->
                RotationPlanDetailScreen(
                    planId = entry.arguments?.getString("planId").orEmpty(),
                    profile = farmProfile,
                    advisorResponse = advisorResponse,
                    onBack = { navController.popBackStack() },
                    onEditProfile = { navController.navigate(Screen.MyFarm.route) }
                )
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
