package com.example.practicasensores.ui.screens.analysis

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.practicasensores.viewmodel.NodeType
import com.example.practicasensores.viewmodel.RopeViewModel

@Composable
fun RopeAnalysisScreen(
    viewModel: RopeViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val history = uiState.jumpHistory
    val totalJumps = uiState.jumpCount
    var selectedTab by remember { mutableStateOf(0) }

    val analysis = remember(history) { viewModel.analyzeData() }
    val topDown = remember(history) { viewModel.buildTopDown() }
    val bottomUp = remember(history) { viewModel.buildBottomUp() }

    val durationSec = if (history.size > 1) (history.last() - history.first()) / 1000f else 0f
    val calories = String.format("%.1f", totalJumps * 0.15f)
    val median = analysis.medianIntervalMs.coerceAtLeast(1L)

    val totalPauseSec = analysis.pauses.sumOf { it.durationMs } / 1000f
    val longestPauseSec = (analysis.pauses.maxOfOrNull { it.durationMs } ?: 0L) / 1000f
    val lostJumps = analysis.pauses
        .sumOf { (it.durationMs / median).toInt() - 1 }
        .coerceAtLeast(0)
    val efficiency = if (durationSec > 0f)
        ((1f - totalPauseSec / durationSec) * 100f).coerceIn(0f, 100f)
    else 100f

    val best = analysis.bestBlock
    val worst = analysis.worstBlock

    val topDownLayers = listOf(
        PyramidLayer("Sesión", "$totalJumps saltos · ${String.format("%.1f", durationSec)} s", nodeColor(NodeType.SESSION)),
        PyramidLayer("Bloques", "mejor: B${best + 1} · flojo: B${worst + 1}", nodeColor(NodeType.BLOCK)),
        PyramidLayer("Detalle", "${analysis.pauses.size} pausas", nodeColor(NodeType.ACTIVE))
    )
    val bottomUpLayers = listOf(
        PyramidLayer("Sesión", "activo ${String.format("%.0f", efficiency)}%", nodeColor(NodeType.SESSION)),
        PyramidLayer("Pausas", "${analysis.pauses.size} · ${String.format("%.1f", totalPauseSec)} s", nodeColor(NodeType.BLOCK)),
        PyramidLayer("Saltos", "~$median ms entre saltos", nodeColor(NodeType.ACTIVE))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141E30))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Reporte de Rutina",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile("Saltos", "$totalJumps", Color(0xFF42A5F5), Modifier.weight(1f))
            MetricTile("Duración", "${String.format("%.1f", durationSec)}s", Color(0xFF4DB6AC), Modifier.weight(1f))
            MetricTile("Calorías", "$calories kcal", Color(0xFFFFB74D), Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionCard(
            title = "Saltos por bloque",
            subtitle = "Verde: mejor bloque · Rojo: el más flojo"
        ) {
            BlockBarChart(analysis.jumpsPerBlock, best, worst)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ritmo normal ~$median ms · pausa más larga ${String.format("%.1f", longestPauseSec)} s",
                color = Color.LightGray,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TabPills(selected = selectedTab, onSelect = { selectedTab = it })

        Spacer(modifier = Modifier.height(16.dp))

        if (history.size < 2) {
            Text(
                text = "Necesitas al menos 2 saltos para ver el análisis.",
                color = Color.Gray,
                fontSize = 14.sp
            )
        } else if (selectedTab == 0) {
            SectionCard(
                title = "Top-Down",
                subtitle = "De la sesión completa al detalle de cada bloque"
            ) {
                PyramidChart(topDownLayers, inverted = true)
                Spacer(modifier = Modifier.height(12.dp))
                TreeLegend()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Mejor tramo: bloque ${best + 1} (${analysis.jumpsPerBlock[best]} saltos) · " +
                            "más flojo: bloque ${worst + 1} (${analysis.jumpsPerBlock[worst]} saltos).",
                    color = Color(0xFFE0E0E0),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                NodeTree(listOf(topDown), topDown.timeMs)
            }
        } else {
            SectionCard(
                title = "Bottom-Up",
                subtitle = "De cada salto a la conclusión de la sesión"
            ) {
                PyramidChart(bottomUpLayers, inverted = false)
                Spacer(modifier = Modifier.height(12.dp))
                TreeLegend()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Las pausas te costaron ~$lostJumps saltos. " +
                            "Estuviste activo el ${String.format("%.0f", efficiency)}% de la sesión.",
                    color = Color(0xFFE0E0E0),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                NodeTree(bottomUp, bottomUp.sumOf { it.timeMs })
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onNavigateBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5))
        ) {
            Text("Finalizar y Volver", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun BlockBarChart(jumps: List<Int>, best: Int, worst: Int) {
    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
        val n = jumps.size.coerceAtLeast(1)
        val maxJumps = (jumps.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
        val slot = size.width / n
        val barW = slot * 0.5f
        val base = size.height - 24.dp.toPx()
        val top = 18.dp.toPx()
        val highlight = best != worst && jumps.getOrNull(best) != jumps.getOrNull(worst)

        val textPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 12.sp.toPx()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        jumps.forEachIndexed { i, count ->
            val x = i * slot + (slot - barW) / 2f
            val cx = x + barW / 2f
            val h = ((count / maxJumps) * (base - top)).coerceAtLeast(6.dp.toPx())

            val colors = when {
                highlight && i == best -> listOf(Color(0xFF69F0AE), Color(0xFF00C853))
                highlight && i == worst -> listOf(Color(0xFFFF8A80), Color(0xFFE53935))
                else -> listOf(Color(0xFF64B5F6), Color(0xFF1E88E5))
            }

            drawRoundRect(
                color = Color(0x12FFFFFF),
                topLeft = Offset(x, top),
                size = Size(barW, base - top),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
            drawRoundRect(
                brush = Brush.verticalGradient(colors, startY = base - h, endY = base),
                topLeft = Offset(x, base - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
            drawContext.canvas.nativeCanvas.drawText("$count", cx, base - h - 4.dp.toPx(), textPaint)
            drawContext.canvas.nativeCanvas.drawText("B${i + 1}", cx, size.height - 6.dp.toPx(), textPaint)
        }
    }
}