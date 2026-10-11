package com.example.practicasensores.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.practicasensores.data.sensors.SensorController
import com.example.practicasensores.data.sensors.SensorType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

// ---------------- Estado de la UI ----------------
data class RopeUiState(
    val jumpCount: Int = 0,
    val currentYAcceleration: Float = 0f,
    val isJumping: Boolean = false,
    val jumpHistory: List<Long> = emptyList(),
    val isTracking: Boolean = false
)

// ---------------- Modelos del análisis ----------------
data class Pause(val index: Int, val durationMs: Long)

data class RopeAnalysis(
    val intervalsMs: List<Long>,
    val medianIntervalMs: Long,
    val pauses: List<Pause>,
    val jumpsPerBlock: List<Int>,
    val bestBlock: Int,
    val worstBlock: Int
)

// Tipos de nodo del árbol (como "métodos" en el Profiler)
enum class NodeType { SESSION, BLOCK, ACTIVE, PAUSE }

// Un nodo del árbol de llamadas
data class TreeNode(
    val label: String,
    val type: NodeType,
    val timeMs: Long,            // tiempo total del nodo
    val detail: String,          // ej. "22 saltos"
    val children: List<TreeNode> = emptyList()
)

class RopeViewModel(private val sensorController: SensorController) : ViewModel() {

    private val _uiState = MutableStateFlow(RopeUiState())
    val uiState: StateFlow<RopeUiState> = _uiState.asStateFlow()

    private val jumpThreshold = 15.0f
    private val resetThreshold = 10.0f
    private var isPeak = false

    init {
        startTracking()
    }

    private fun startTracking() {
        viewModelScope.launch {
            sensorController.getSensorData(SensorType.ACCELEROMETER)
                .catch { e -> }
                .collect { reading ->
                    processAcceleration(reading.values[1])
                }
        }
    }

    fun toggleTracking() {
        if (_uiState.value.isTracking) {
            _uiState.value = _uiState.value.copy(isTracking = false)
        } else {
            isPeak = false
            _uiState.value = RopeUiState(isTracking = true)
        }
    }

    private fun processAcceleration(y: Float) {
        if (!_uiState.value.isTracking) return

        if (y > jumpThreshold && !isPeak) {
            isPeak = true
            val currentHistory = _uiState.value.jumpHistory.toMutableList()
            currentHistory.add(System.currentTimeMillis())

            _uiState.value = _uiState.value.copy(
                jumpCount = _uiState.value.jumpCount + 1,
                isJumping = true,
                currentYAcceleration = y,
                jumpHistory = currentHistory
            )
        } else if (y < resetThreshold && isPeak) {
            isPeak = false
            _uiState.value = _uiState.value.copy(isJumping = false, currentYAcceleration = y)
        } else {
            _uiState.value = _uiState.value.copy(currentYAcceleration = y)
        }
    }

    fun resetCounter() {
        isPeak = false
        _uiState.value = RopeUiState()
    }

    // =====================================================
    //  DATOS BASE (intervalos, pausas, bloques)
    // =====================================================
    fun analyzeData(): RopeAnalysis {
        val history = _uiState.value.jumpHistory
        if (history.size < 2) {
            return RopeAnalysis(emptyList(), 0L, emptyList(), List(5) { 0 }, 0, 0)
        }

        val intervals = history.zipWithNext { a, b -> b - a }
        val median = intervals.sorted()[intervals.size / 2]
        val pauseLimit = maxOf(median * 3, 1500L)
        val pauses = intervals.mapIndexedNotNull { i, d ->
            if (d > pauseLimit) Pause(i, d) else null
        }

        val start = history.first()
        val total = (history.last() - start).coerceAtLeast(1L)
        val segDuration = total / 5.0
        val segments = IntArray(5)
        for (t in history) {
            segments[((t - start) / segDuration).toInt().coerceIn(0, 4)]++
        }

        return RopeAnalysis(
            intervalsMs = intervals,
            medianIntervalMs = median,
            pauses = pauses,
            jumpsPerBlock = segments.toList(),
            bestBlock = segments.indices.maxByOrNull { segments[it] } ?: 0,
            worstBlock = segments.indices.minByOrNull { segments[it] } ?: 0
        )
    }

    // Acumulador por bloque
    private class BlockData {
        var jumps = 0
        var activeMs = 0L
        var activeCount = 0
        var pauseMs = 0L
        var pauseCount = 0
        val totalMs: Long get() = activeMs + pauseMs
    }

    // Reparte cada intervalo entre saltos en su bloque, como "Saltando" o "Pausa"
    private fun computeBlocks(): List<BlockData> {
        val history = _uiState.value.jumpHistory
        val blocks = List(5) { BlockData() }
        if (history.size < 2) return blocks

        val start = history.first()
        val total = (history.last() - start).coerceAtLeast(1L)
        val segDuration = total / 5.0
        fun blockOf(t: Long) = ((t - start) / segDuration).toInt().coerceIn(0, 4)

        val intervals = history.zipWithNext { a, b -> b - a }
        val median = intervals.sorted()[intervals.size / 2]
        val pauseLimit = maxOf(median * 3, 1500L)

        history.forEach { blocks[blockOf(it)].jumps++ }
        intervals.forEachIndexed { i, d ->
            val b = blocks[blockOf(history[i])]
            if (d > pauseLimit) {
                b.pauseMs += d
                b.pauseCount++
            } else {
                b.activeMs += d
                b.activeCount++
            }
        }
        return blocks
    }

    // =====================================================
    //  TOP DOWN: Sesión -> Bloques -> (Saltando / Pausas)
    // =====================================================
    fun buildTopDown(): TreeNode {
        val blocks = computeBlocks()
        val children = blocks.mapIndexed { i, b ->
            val leaves = buildList {
                if (b.activeCount > 0) {
                    add(TreeNode("Saltando", NodeType.ACTIVE, b.activeMs, "${b.activeCount} intervalos"))
                }
                if (b.pauseCount > 0) {
                    add(TreeNode("Pausas", NodeType.PAUSE, b.pauseMs, "${b.pauseCount} pausa(s)"))
                }
            }
            TreeNode("Bloque ${i + 1}", NodeType.BLOCK, b.totalMs, "${b.jumps} saltos", leaves)
        }
        return TreeNode(
            label = "Sesión",
            type = NodeType.SESSION,
            timeMs = blocks.sumOf { it.totalMs },
            detail = "${blocks.sumOf { it.jumps }} saltos",
            children = children
        )
    }

    // =====================================================
    //  BOTTOM UP: (Pausas / Saltando) -> Bloques -> Sesión
    //  Se empieza por las "hojas" y se expande hacia sus emisores
    // =====================================================
    fun buildBottomUp(): List<TreeNode> {
        val blocks = computeBlocks()

        fun root(
            label: String,
            type: NodeType,
            unit: String,
            time: (BlockData) -> Long,
            count: (BlockData) -> Int
        ): TreeNode? {
            val callers = blocks.mapIndexedNotNull { i, b ->
                if (count(b) == 0) null
                else {
                    val session = TreeNode("Sesión", NodeType.SESSION, time(b), "${count(b)} $unit")
                    TreeNode("Bloque ${i + 1}", NodeType.BLOCK, time(b), "${count(b)} $unit", listOf(session))
                }
            }
            val totalCount = blocks.sumOf { count(it) }
            if (totalCount == 0) return null
            val totalTime = blocks.sumOf { time(it) }
            return TreeNode(label, type, totalTime, "$totalCount $unit", callers)
        }

        return listOfNotNull(
            root("Pausas", NodeType.PAUSE, "pausa(s)", { it.pauseMs }, { it.pauseCount }),
            root("Saltando", NodeType.ACTIVE, "intervalos", { it.activeMs }, { it.activeCount })
        ).sortedByDescending { it.timeMs }
    }
}