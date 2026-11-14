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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cropexpert.app.ui.dashboard.HomeDashboardScreen
import com.cropexpert.app.ui.cropadvisor.CropAdvisorScreen
import com.cropexpert.app.ui.location.LocationSelectionScreen
import com.cropexpert.app.domain.model.FarmProfile
import com.cropexpert.app.domain.model.CropRecommendationResponse
import com.cropexpert.app.domain.model.soilPHValue
import com.cropexpert.app.ui.rotation.MyFarmScreen
import com.cropexpert.app.ui.rotation.RotationPlanDetailScreen
import com.cropexpert.app.ui.rotation.RotationPlansScreen
import com.cropexpert.app.ui.splash.SplashScreen
import com.cropexpert.app.ui.farm.FarmViewModel
import com.cropexpert.app.ui.theme.ForestGreen
import com.cropexpert.app.ui.theme.LightMint
import com.cropexpert.app.ui.theme.Typography
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home/{lat}/{lon}", "Field", Icons.Default.Home) {
        fun createRoute(lat: Double, lon: Double) = "home/$lat/$lon"
    }
    object CropAdvisor : Screen("crop_advisor/{lat}/{lon}", "Crop Advisor", Icons.Default.Psychology) {
        fun createRoute(lat: Double, lon: Double) = "crop_advisor/$lat/$lon"
    }
    object FuturePlan : Screen("future_plan", "Rotation Plans", Icons.Default.Timeline)
    object MyFarm : Screen("my_farm", "My Farm", Icons.Default.Agriculture)
    object LocationSelection : Screen("location_selection/{editing}", "Edit Field", Icons.Default.Agriculture) {
        fun createRoute(editing: Boolean) = "location_selection/$editing"
    }
    object PlanDetails : Screen("plan_details/{planId}", "Plan Details", Icons.Default.Timeline) {
        fun createRoute(planId: String) = "plan_details/$planId"
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            val farmViewModel: FarmViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )
            val coroutineScope = rememberCoroutineScope()
            val farmEntity by farmViewModel.farm.collectAsState()
            val soilSamples by farmViewModel.soilSamples.collectAsState()
            val farmError by farmViewModel.errorMessage.collectAsState()
            val terrainState by farmViewModel.terrainState.collectAsState()
            var farmProfile by remember(farmEntity) {
                mutableStateOf(farmEntity?.toProfile() ?: FarmProfile())
            }
            val selectedLatitude = farmEntity?.latitude ?: 7.8731
            val selectedLongitude = farmEntity?.longitude ?: 80.7718
            var advisorResponse by remember { mutableStateOf<CropRecommendationResponse?>(null) }
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
                        if (currentRoute != null && currentRoute != "splash" && !currentRoute.startsWith("location_selection")) {
                            NavigationBar(
                                containerColor = LightMint,
                                tonalElevation = 8.dp
                            ) {
                                items.forEach { screen ->
                                    val isSelected = currentDestination?.hierarchy?.any { 
                                        it.route?.startsWith(screen.route.substringBefore("/{")) == true 
                                    } == true

                                    NavigationBarItem(
                                        icon = { Icon(screen.icon, contentDescription = null) },
                                        label = {
                                            Text(
                                                text = screen.label,
                                                maxLines = 1,
                                                softWrap = false,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.sp
                                                )
                                            )
                                        },
                                        selected = isSelected,
                                        onClick = {
                                            val route = if (screen is Screen.Home) {
                                                Screen.Home.createRoute(selectedLatitude, selectedLongitude)
                                            } else if (screen is Screen.CropAdvisor) {
                                                Screen.CropAdvisor.createRoute(selectedLatitude, selectedLongitude)
                                            } else {
                                                screen.route
                                            }

                                            navController.navigate(route) {
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
                                coroutineScope.launch {
                                    val savedFarm = farmViewModel.getSavedFarm()
                                    if (savedFarm?.latitude != null && savedFarm.longitude != null) {
                                        navController.navigate(
                                            Screen.Home.createRoute(
                                                savedFarm.latitude,
                                                savedFarm.longitude
                                            )
                                        ) {
                                            popUpTo("splash") { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate(Screen.LocationSelection.createRoute(false)) {
                                            popUpTo("splash") { inclusive = true }
                                        }
                                    }
                                }
                            })
                        }
                        composable(
                            route = Screen.LocationSelection.route,
                            arguments = listOf(navArgument("editing") { type = NavType.BoolType })
                        ) { entry ->
                            val editing = entry.arguments?.getBoolean("editing") ?: false
                            LocationSelectionScreen(
                                initialLatitude = selectedLatitude,
                                initialLongitude = selectedLongitude,
                                initialBoundaryJson = farmEntity?.boundaryJson,
                                initialZoom = if (editing) 15f else 7f,
                                onConfirmLocation = { lat, lon, boundary ->
                                    farmViewModel.saveLocation(lat, lon, boundary)
                                    if (editing) {
                                        navController.popBackStack()
                                    } else {
                                        navController.navigate(Screen.Home.createRoute(lat, lon)) {
                                            popUpTo("splash") { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }
                        composable(
                            route = Screen.Home.route,
                            arguments = listOf(
                                navArgument("lat") { type = NavType.StringType },
                                navArgument("lon") { type = NavType.StringType }
                            )
                        ) {
                            HomeDashboardScreen(
                                onNavigateToRotationPlans = {
                                    navController.navigate(Screen.FuturePlan.route)
                                }
                            )
                        }
                        composable(
                            route = Screen.CropAdvisor.route,
                            arguments = listOf(
                                navArgument("lat") { type = NavType.StringType },
                                navArgument("lon") { type = NavType.StringType }
                            )
                        ) {
                            CropAdvisorScreen(
                                soilPH = farmProfile.soilPHValue(),
                                fieldBoundary = farmEntity?.boundaryJson,
                                onUseForRotation = { response ->
                                    advisorResponse = response
                                    navController.navigate(Screen.FuturePlan.route) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable(Screen.FuturePlan.route) {
                            RotationPlansScreen(
                                profile = farmProfile,
                                advisorResponse = advisorResponse,
                                onEditProfile = { navController.navigate(Screen.MyFarm.route) },
                                onOpenCropAdvisor = {
                                    navController.navigate(
                                        Screen.CropAdvisor.createRoute(
                                            selectedLatitude,
                                            selectedLongitude
                                        )
                                    )
                                },
                                onOpenPlan = { planId ->
                                    navController.navigate(Screen.PlanDetails.createRoute(planId))
                                }
                            )
                        }
                        composable(Screen.MyFarm.route) {
                            MyFarmScreen(
                                profile = farmProfile,
                                farm = farmEntity,
                                soilSamples = soilSamples,
                                databaseError = farmError,
                                onSave = {
                                    if (it.soilPH != farmProfile.soilPH) advisorResponse = null
                                    farmProfile = it
                                    farmViewModel.saveProfile(it)
                                },
                                onEditLocation = {
                                    navController.navigate(Screen.LocationSelection.createRoute(true))
                                },
                                onAddSoilSample = farmViewModel::addSoilSample,
                                onDeleteSoilSample = farmViewModel::deleteSoilSample,
                                terrainState = terrainState,
                                onLoadTerrain = farmViewModel::loadTerrain,
                                onRetryTerrain = {
                                    farmViewModel.retryTerrain(farmEntity?.boundaryJson)
                                },
                                onOpenCropAdvisor = {
                                    navController.navigate(
                                        Screen.CropAdvisor.createRoute(
                                            selectedLatitude,
                                            selectedLongitude
                                        )
                                    )
                                }
                            )
                        }
                        composable(
                            route = Screen.PlanDetails.route,
                            arguments = listOf(navArgument("planId") { type = NavType.StringType })
                        ) { entry ->
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
