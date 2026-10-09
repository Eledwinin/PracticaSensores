package com.example.practicasensores.ui.navegation

sealed class Route(val route: String){
    object Home: Route("home_route")
    object Rope: Route("rope_route")
    object Game: Route("game_route")
    object Analysis: Route("analysis_route")
}