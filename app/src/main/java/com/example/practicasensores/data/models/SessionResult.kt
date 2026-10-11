package com.example.practicasensores.data.models

data class SessionResult(
    val totalJumps: Int = 0,
    val jumpTimestamps: List<Long> = emptyList() // Guarda el milisegundo exacto de cada salto
)