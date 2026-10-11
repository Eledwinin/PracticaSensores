package com.example.practicasensores.ui.screens.analysis

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.practicasensores.viewmodel.GameViewModel
import com.example.practicasensores.viewmodel.LevelStats
import com.example.practicasensores.viewmodel.NodeType
import com.example.practicasensores.viewmodel.collisionLimit
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameAnalysisScreen(
    viewModel: GameViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val history = uiState.levelHistory
    var selectedTab by remember { mutableStateOf(0) }

    val topDown = remember(history) { viewModel.getTopDownGameTree() }

    val totalTime = history.sumOf { it.timeSec }
    val totalHits = history.sumOf { it.collisions }
    val wins = history.count { it.completed }
    val failed = history.size - wins
    // Margen de error: choques usados frente al límite permitido en cada nivel
    fun usage(l: LevelStats) = l.collisions.toFloat() / collisionLimit(l.level)
    val precision = if (history.isNotEmpty())
        (100 - history.map { usage(it) }.average() * 100).roundToInt().coerceIn(0, 100)
    else 0
    val hardest = history.maxByOrNull { usage(it) }
    val slowest = history.maxByOrNull { it.timeSec }

    val topDownLayers = listOf(
        PyramidLayer("Sesión", "$totalTime s · $wins de ${history.size} superados", nodeColor(NodeType.SESSION)),
        PyramidLayer("Resultado", "$wins en meta · $failed fallidos", nodeColor(NodeType.BLOCK)),
        PyramidLayer("Nivel más lento", slowest?.let { "N${it.level} · ${it.timeSec} s" } ?: "-", nodeColor(NodeType.ACTIVE))
    )
    val bottomUpLayers = listOf(
        PyramidLayer("Precisión", "$precision%", nodeColor(NodeType.SESSION)),
        PyramidLayer(
            "Más exigente",
            hardest?.let { "N${it.level} · ${it.collisions}/${collisionLimit(it.level)} choques" } ?: "-",
            nodeColor(NodeType.BLOCK)
        ),
        PyramidLayer("Choques", "$totalHits en ${history.size} niveles", nodeColor(NodeType.ACTIVE))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reporte del Laberinto", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("← Volver", color = Color(0xFF42A5F5), fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B0F19),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0B0F19)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile("Superados", "$wins/5", Color(0xFF00E676), Modifier.weight(1f))
                MetricTile("Tiempo", "${totalTime}s", Color(0xFF42A5F5), Modifier.weight(1f))
                MetricTile("Choques", "$totalHits", Color(0xFFEF5350), Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(title = "Tiempo por nivel", subtitle = "Verde: superado · Rojo: fallido") {
                TimeBarChart(history)
            }

            Spacer(modifier = Modifier.height(16.dp))

            TabPills(selected = selectedTab, onSelect = { selectedTab = it })

            Spacer(modifier = Modifier.height(16.dp))

            if (history.isEmpty()) {
                Text(
                    text = "Juega al menos un nivel para ver el análisis.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            } else if (selectedTab == 0) {
                SectionCard(
                    title = "Top-Down",
                    subtitle = "De la sesión completa al detalle de cada nivel"
                ) {
                    PyramidChart(topDownLayers, inverted = true)
                    Spacer(modifier = Modifier.height(12.dp))
                    GameLegend()
                    Spacer(modifier = Modifier.height(16.dp))
                    NodeTree(topDown, topDown.firstOrNull()?.timeMs ?: 0L)
                }
            } else {
                SectionCard(
                    title = "Bottom-Up",
                    subtitle = "De los choques de cada nivel a la precisión global"
                ) {
                    PyramidChart(bottomUpLayers, inverted = false)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Margen de error por nivel", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Choques usados del límite permitido", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    history.forEach { LevelUsageRow(it) }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Conclusión: precisión global de $precision%" +
                                (hardest?.let { ", el nivel más exigente fue el ${it.level}." } ?: "."),
                        color = Color(0xFFE0E0E0),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ---------------- Barras: tiempo por nivel ----------------
@Composable
private fun TimeBarChart(history: List<LevelStats>) {
    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
        val maxTime = (history.maxOfOrNull { it.timeSec } ?: 0).coerceAtLeast(5).toFloat()
        val slot = size.width / 5f
        val barW = slot * 0.5f
        val base = size.height - 24.dp.toPx()
        val top = 18.dp.toPx()

        val textPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 12.sp.toPx()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        for (i in 1..5) {
            val stats = history.find { it.level == i }
            val x = (i - 1) * slot + (slot - barW) / 2f
            val cx = x + barW / 2f

            drawRoundRect(
                color = Color(0x12FFFFFF),
                topLeft = Offset(x, top),
                size = Size(barW, base - top),
                cornerRadius = CornerRadius(8.dp.toPx())
            )

            if (stats != null) {
                val h = ((stats.timeSec / maxTime) * (base - top)).coerceAtLeast(6.dp.toPx())
                val colors = if (stats.completed)
                    listOf(Color(0xFF69F0AE), Color(0xFF00C853))
                else
                    listOf(Color(0xFFFF8A80), Color(0xFFE53935))
                drawRoundRect(
                    brush = Brush.verticalGradient(colors, startY = base - h, endY = base),
                    topLeft = Offset(x, base - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(8.dp.toPx())
                )
                drawContext.canvas.nativeCanvas.drawText("${stats.timeSec}s", cx, base - h - 4.dp.toPx(), textPaint)
            }

            drawContext.canvas.nativeCanvas.drawText("N$i", cx, size.height - 6.dp.toPx(), textPaint)
        }
    }
}

@Composable
private fun GameLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(
            "Sesión" to nodeColor(NodeType.SESSION),
            "Resultado" to nodeColor(NodeType.BLOCK),
            "Superado" to nodeColor(NodeType.ACTIVE),
            "Fallido" to nodeColor(NodeType.PAUSE)
        ).forEach { (name, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                Text(name, color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun LevelUsageRow(stats: LevelStats) {
    val limit = collisionLimit(stats.level)
    val used = (stats.collisions.toFloat() / limit).coerceIn(0f, 1f)
    val color = when {
        used < 0.5f -> Color(0xFF00E676)
        used < 0.8f -> Color(0xFFFFB74D)
        else -> Color(0xFFEF5350)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("N${stats.level}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0x1AFFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(used)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(color)
            )
        }
        Text(
            "${stats.collisions}/$limit",
            color = Color.LightGray,
            fontSize = 12.sp,
            modifier = Modifier.width(48.dp).padding(start = 8.dp)
        )
    }
}