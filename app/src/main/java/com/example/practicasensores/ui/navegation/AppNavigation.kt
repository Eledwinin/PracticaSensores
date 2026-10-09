package com.example.practicasensores.ui.navegation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.practicasensores.ui.screens.analysis.AnalysisScreen
import com.example.practicasensores.ui.screens.game.GameScreen
import com.example.practicasensores.ui.screens.home.HomeScreen
import com.example.practicasensores.ui.screens.rope.RopeScreen

@Composable
fun AppNavigation(){
    val navController = rememberNavController()

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
                onNavigateToAnalysis = { navController.navigate(Route.Analysis.route)}
            )
        }

        //pantalla de juego
        composable(Route.Game.route){
            GameScreen(
                onNavigateToAnalysis = { navController.navigate(Route.Analysis.route)}
            )
        }

        //pantalla de analisis
        composable(Route.Analysis.route){
            AnalysisScreen(
                onNavigateBack = { navController.navigate(Route.Home.route)}
            )
        }

    }

}