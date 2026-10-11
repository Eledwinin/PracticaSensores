package com.example.practicasensores.ui.screens.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.practicasensores.viewmodel.GameViewModel
import com.example.practicasensores.viewmodel.WORLD_H
import com.example.practicasensores.viewmodel.WORLD_W

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onNavigateToAnalysis: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val level = viewModel.getLevel(uiState.currentLevel)

    val dark = uiState.isDarkMode
    val backgroundColor = if (dark) Color(0xFF0B0F19) else Color(0xFFF0F4F8)
    val boardColor = if (dark) Color(0xFF111827) else Color.White
    val borderColor = if (dark) Color(0xFF2A3350) else Color(0xFFCBD5E1)
    val textColor = if (dark) Color.White else Color.Black
    val wallColor = if (dark) Color(0xFF9E82F0) else Color(0xFF512DA8)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Neon Tilt Maze - Nivel ${uiState.currentLevel}",
                color = textColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(text = level.name, color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { lvl ->
                    Button(
                        onClick = { viewModel.loadLevel(lvl) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.currentLevel == lvl) Color(0xFF42A5F5) else Color(0xFF151B2B)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("N$lvl", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Choques: ${uiState.collisionCount}/${uiState.maxCollisions}",
                    color = Color(0xFFEF5350),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = "Tiempo: ${uiState.sessionTimeSec}s", color = textColor, fontSize = 13.sp)
                Text(text = "Luz: ${uiState.ambientLight.toInt()} lx", color = Color(0xFF00E676), fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // El mundo es de 1000x1800 y se escala al espacio disponible
            Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val s = minOf(size.width / WORLD_W, size.height / WORLD_H)
                val ox = (size.width - WORLD_W * s) / 2f
                val oy = (size.height - WORLD_H * s) / 2f

                withTransform({
                    translate(ox, oy)
                    scale(s, s, pivot = Offset.Zero)
                }) {
                    drawRoundRect(
                        color = boardColor,
                        size = Size(WORLD_W, WORLD_H),
                        cornerRadius = CornerRadius(24f)
                    )
                    drawRoundRect(
                        color = borderColor,
                        size = Size(WORLD_W, WORLD_H),
                        cornerRadius = CornerRadius(24f),
                        style = Stroke(width = 4f)
                    )

                    level.walls.forEach {
                        drawRoundRect(
                            color = wallColor,
                            topLeft = Offset(it.x, it.y),
                            size = Size(it.w, it.h),
                            cornerRadius = CornerRadius(10f)
                        )
                    }

                    val goal = Offset(uiState.goalX, uiState.goalY)
                    drawCircle(Color(0xFF00E676).copy(alpha = 0.25f), radius = 65f, center = goal)
                    drawCircle(Color(0xFF00E676), radius = 35f, center = goal)

                    val enemy = Offset(uiState.enemyX, uiState.enemyY)
                    drawCircle(Color(0xFFEF5350).copy(alpha = 0.25f), radius = 42f, center = enemy)
                    drawCircle(Color(0xFFEF5350), radius = 25f, center = enemy)

                    val ball = Offset(uiState.ballX, uiState.ballY)
                    drawCircle(Color(0xFF42A5F5).copy(alpha = 0.25f), radius = 42f, center = ball)
                    drawCircle(Color(0xFF42A5F5), radius = 25f, center = ball)
                    drawCircle(Color.White.copy(alpha = 0.5f), radius = 8f, center = ball - Offset(7f, 7f))
                }
            }

            Text(
                text = "Inclina el teléfono para guiar la esfera azul a la meta verde",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
        }

        if (uiState.isGameOver || uiState.isVictory) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xEE0B0F19)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (uiState.isVictory) "¡NIVEL ${uiState.currentLevel} SUPERADO!" else "¡GAME OVER!",
                        color = if (uiState.isVictory) Color(0xFF00E676) else Color(0xFFEF5350),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${uiState.sessionTimeSec}s · ${uiState.collisionCount} choques",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (uiState.isVictory && uiState.currentLevel < 5) {
                        Button(
                            onClick = { viewModel.loadLevel(uiState.currentLevel + 1) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                        ) {
                            Text("Siguiente Nivel (Nivel ${uiState.currentLevel + 1})", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { viewModel.loadLevel(uiState.currentLevel) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5))
                    ) {
                        Text("Reintentar Nivel", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onNavigateToAnalysis,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9E82F0))
                    ) {
                        Text("Ver Reporte y Gráfica", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}