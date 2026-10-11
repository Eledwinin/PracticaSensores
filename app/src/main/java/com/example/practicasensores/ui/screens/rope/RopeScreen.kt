package com.example.practicasensores.ui.screens.rope

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.practicasensores.viewmodel.RopeViewModel

@Composable
fun RopeScreen(
    viewModel: RopeViewModel,
    onNavigateToAnalysis: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val scaleMultiplier by animateFloatAsState(
        targetValue = if (uiState.isJumping) 1.5f else 1.0f,
        animationSpec = tween(durationMillis = 200), label = "JumpAnimation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141E30))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Contador de Saltos",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 48.dp)
        )

        Box(
            modifier = Modifier
                .size(200.dp)
                .drawBehind {
                    val radius = (size.minDimension / 2) * scaleMultiplier
                    val color = if (uiState.isJumping) Color(0xFF42A5F5) else Color.DarkGray
                    drawCircle(
                        color = color.copy(alpha = 0.5f),
                        radius = radius
                    )
                    drawCircle(
                        color = Color(0xFF1E88E5),
                        radius = size.minDimension / 2.5f
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${uiState.jumpCount}",
                    color = Color.White,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Saltos",
                    color = Color.LightGray,
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(64.dp))

        Button(
            onClick = { viewModel.toggleTracking() },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.isTracking) Color(0xFFEF5350) else Color(0xFF42A5F5)
            )
        ) {
            Text(
                text = if (uiState.isTracking) "Detener Rutina" else "Iniciar Rutina",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!uiState.isTracking && uiState.jumpCount > 0) {
            Button(
                onClick = onNavigateToAnalysis,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9E82F0))
            ) {
                Text(
                    text = "Ver Análisis de Sesión",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}