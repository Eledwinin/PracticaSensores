package com.example.practicasensores.ui.screens.analysis

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.practicasensores.viewmodel.NodeType
import com.example.practicasensores.viewmodel.TreeNode

// Color de cada tipo de nodo (se usa en el árbol y en el flame chart)
fun nodeColor(type: NodeType): Color = when (type) {
    NodeType.SESSION -> Color(0xFF42A5F5)
    NodeType.BLOCK -> Color(0xFF9E82F0)
    NodeType.ACTIVE -> Color(0xFF4DB6AC)
    NodeType.PAUSE -> Color(0xFFEF5350)
}

private val lineColor = Color(0x55FFFFFF)
private val cardColor = Color(0xFF151B2B)


@Composable
fun NodeTree(roots: List<TreeNode>, totalMs: Long) {
    Column(modifier = Modifier.fillMaxWidth()) {
        roots.forEach { TreeNodeItem(node = it, totalMs = totalMs, depth = 0) }
    }
}

@Composable
private fun TreeNodeItem(node: TreeNode, totalMs: Long, depth: Int) {
    // La raíz inicia expandida; los demás niveles se abren al tocarlos
    var expanded by remember { mutableStateOf(depth == 0) }
    val hasChildren = node.children.isNotEmpty()
    val color = nodeColor(node.type)
    val fraction =
        if (totalMs > 0) (node.timeMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f

    // graphicsLayer: la flecha rota con animación al expandir
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "arrowRotation"
    )

    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = hasChildren) { expanded = !expanded }
                .padding(vertical = 6.dp, horizontal = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "▶",
                    color = if (hasChildren) Color.White else Color.Transparent,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .width(18.dp)
                        .graphicsLayer { rotationZ = arrowRotation }
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                ) {
                    Text(
                        text = node.label,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(text = node.detail, color = Color.LightGray, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format("%.1f s", node.timeMs / 1000f),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = String.format("%.0f%%", fraction * 100f),
                        color = color,
                        fontSize = 12.sp
                    )
                }
            }

            // Barra de porcentaje (drawBehind)
            Spacer(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(5.dp)
                    .drawBehind {
                        drawRoundRect(
                            color = Color(0x22FFFFFF),
                            cornerRadius = CornerRadius(3.dp.toPx())
                        )
                        drawRoundRect(
                            color = color,
                            size = Size(size.width * fraction, size.height),
                            cornerRadius = CornerRadius(3.dp.toPx())
                        )
                    }
            )
        }

        // Hijos con líneas conectoras (como las flechas del árbol del Profiler)
        AnimatedVisibility(visible = expanded && hasChildren) {
            Column(
                modifier = Modifier
                    .padding(start = 14.dp)
                    .drawBehind {
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
            ) {
                node.children.forEach { child ->
                    Box(
                        modifier = Modifier
                            .drawBehind {
                                drawLine(
                                    color = lineColor,
                                    start = Offset(0f, 20.dp.toPx()),
                                    end = Offset(10.dp.toPx(), 20.dp.toPx()),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                            .padding(start = 10.dp)
                    ) {
                        TreeNodeItem(node = child, totalMs = totalMs, depth = depth + 1)
                    }
                }
            }
        }
    }
}

private data class FlameRect(
    val label: String,
    val type: NodeType,
    val x: Float,
    val w: Float,
    val level: Int
)

private fun layoutFlame(
    nodes: List<TreeNode>,
    x0: Float,
    width: Float,
    parentTime: Long,
    level: Int,
    out: MutableList<FlameRect>
) {
    if (parentTime <= 0L) return
    var x = x0
    nodes.forEach { n ->
        val w = width * n.timeMs / parentTime
        if (w > 0f) {
            out.add(FlameRect(n.label, n.type, x, w, level))
            layoutFlame(n.children, x, w, n.timeMs, level + 1, out)
        }
        x += w
    }
}

@Composable
fun FlameChart(roots: List<TreeNode>, totalMs: Long, levels: Int = 3) {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp * levels)
            .drawWithCache {
                val rects = mutableListOf<FlameRect>()
                layoutFlame(roots, 0f, size.width, totalMs, 0, rects)

                val rowH = size.height / levels
                val gap = 2.dp.toPx()
                val paint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 11.sp.toPx()
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }

                onDrawBehind {
                    rects.forEach { r ->
                        val top = r.level * rowH
                        drawRoundRect(
                            color = nodeColor(r.type),
                            topLeft = Offset(r.x + gap / 2f, top + gap / 2f),
                            size = Size((r.w - gap).coerceAtLeast(1f), rowH - gap),
                            cornerRadius = CornerRadius(6.dp.toPx())
                        )
                        // Solo escribe la etiqueta si el bloque es lo bastante ancho
                        if (r.w > 56.dp.toPx()) {
                            drawContext.canvas.nativeCanvas.drawText(
                                r.label,
                                r.x + r.w / 2f,
                                top + rowH / 2f + paint.textSize / 3f,
                                paint
                            )
                        }
                    }
                }
            }
    )
}

// Leyenda de colores
@Composable
fun TreeLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
    ) {
        listOf(
            "Sesión" to NodeType.SESSION,
            "Bloque" to NodeType.BLOCK,
            "Saltando" to NodeType.ACTIVE,
            "Pausa" to NodeType.PAUSE
        ).forEach { (name, type) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(nodeColor(type))
                )
                Text(
                    text = name,
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, color = Color(0xFF42A5F5), fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
fun MetricTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
fun TabPills(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardColor)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf("Top-Down", "Bottom-Up").forEachIndexed { i, label ->
            val active = selected == i
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) Color(0xFF42A5F5) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (active) Color.White else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ---------------- Pirámide (Top-Down invertida / Bottom-Up normal) ----------------
data class PyramidLayer(val title: String, val value: String, val color: Color)

@Composable
fun PyramidChart(layers: List<PyramidLayer>, inverted: Boolean) {
    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        val n = layers.size
        val gap = 5.dp.toPx()
        val layerH = size.height / n
        val cx = size.width / 2f

        fun widthAt(y: Float): Float {
            val t = y / size.height
            val f = if (inverted) 1f - 0.6f * t else 0.3f + 0.7f * t
            return size.width * f
        }

        val titlePaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 15.sp.toPx()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val valuePaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 12.sp.toPx()
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        layers.forEachIndexed { i, layer ->
            val y0 = i * layerH + gap / 2f
            val y1 = (i + 1) * layerH - gap / 2f
            val w0 = widthAt(y0)
            val w1 = widthAt(y1)

            val path = Path().apply {
                moveTo(cx - w0 / 2f, y0)
                lineTo(cx + w0 / 2f, y0)
                lineTo(cx + w1 / 2f, y1)
                lineTo(cx - w1 / 2f, y1)
                close()
            }
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(layer.color, layer.color.copy(alpha = 0.7f)),
                    startY = y0,
                    endY = y1
                )
            )

            val midY = (y0 + y1) / 2f
            drawContext.canvas.nativeCanvas.drawText(layer.title, cx, midY - 1.dp.toPx(), titlePaint)
            drawContext.canvas.nativeCanvas.drawText(layer.value, cx, midY + 16.dp.toPx(), valuePaint)
        }
    }
}