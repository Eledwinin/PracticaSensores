package com.example.practicasensores.ui.navegation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.practicasensores.data.sensors.SensorController
import com.example.practicasensores.ui.screens.analysis.RopeAnalysisScreen
import com.example.practicasensores.ui.screens.analysis.GameAnalysisScreen
import com.example.practicasensores.ui.screens.game.GameScreen
import com.example.practicasensores.ui.screens.home.HomeScreen
import com.example.practicasensores.ui.screens.rope.RopeScreen
import com.example.practicasensores.viewmodel.GameViewModel
import com.example.practicasensores.viewmodel.RopeViewModel

@Composable
fun AppNavigation(){
    val navController = rememberNavController()
    val context = LocalContext.current

    val ropeViewModel: RopeViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RopeViewModel(SensorController(context)) as T
            }
        }
    )

    val gameViewModel: GameViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return GameViewModel(SensorController(context)) as T
            }
        }
    )

    NavHost(navController = navController, startDestination = "home_route"){

        //pantalla principal
        composable(Route.Home.route){
            HomeScreen(
                onNavigateToRope = { navController.navigate(Route.Rope.route) },
                onNavigateToGame = { navController.navigate(Route.Game.route) }
            )
        }


        //pantalla de cuerda
        composable(Route.Rope.route){
            RopeScreen(
                viewModel= ropeViewModel,
                onNavigateToAnalysis = { navController.navigate(Route.RopeAnalysis.route)}
            )
        }
        //pantalla de analisis saltos
        composable(Route.RopeAnalysis.route) {
            RopeAnalysisScreen(
                viewModel = ropeViewModel,
                onNavigateBack = { navController.popBackStack(Route.Home.route, inclusive = false) }
            )
        }

        composable(Route.Game.route) {
            GameScreen(
                viewModel = gameViewModel, // Usamos la instancia compartida
                onNavigateToAnalysis = { navController.navigate(Route.GameAnalysis.route) }
            )
        }

        // pantalla de análisis de juego
        composable(Route.GameAnalysis.route) {
            GameAnalysisScreen(
                viewModel = gameViewModel, // Usamos exactamente la misma instancia compartida
                onNavigateBack = { navController.popBackStack() }
            )
        }

    }

}