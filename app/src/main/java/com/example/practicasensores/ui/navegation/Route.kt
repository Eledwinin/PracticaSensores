package com.example.practicasensores.ui.navegation

sealed class Route(val route: String){
    object Home: Route("home_route")
    object Rope: Route("rope_route")
    object RopeAnalysis: Route("rope_analysis_route")
    object Game: Route("game_route")
    object GameAnalysis: Route("game_analysis_route")

}