package com.example.practicasensores.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.practicasensores.data.sensors.SensorController
import com.example.practicasensores.data.sensors.SensorType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

// Tamaño del mundo del juego (se escala a cualquier pantalla)
const val WORLD_W = 1000f
const val WORLD_H = 1800f
private const val BALL_R = 25f
private const val TILT_SPEED = 2.2f

enum class LevelOutcome { GOAL, CAUGHT, CRASHED }

fun collisionLimit(level: Int): Int = 6 + level * 2

data class LevelStats(
    val level: Int,
    val timeSec: Int,
    val collisions: Int,
    val completed: Boolean,
    val outcome: LevelOutcome = if (completed) LevelOutcome.GOAL else LevelOutcome.CRASHED
)

data class Wall(val x: Float, val y: Float, val w: Float, val h: Float)

data class LevelDef(
    val name: String,
    val goalX: Float,
    val goalY: Float,
    val enemyX: Float,
    val enemyY: Float,
    val walls: List<Wall>
)

data class GameUiState(
    val currentLevel: Int = 1,
    val ballX: Float = 100f,
    val ballY: Float = 100f,
    val enemyX: Float = 500f,
    val enemyY: Float = 1500f,
    val goalX: Float = 900f,
    val goalY: Float = 1700f,
    val isGameOver: Boolean = false,
    val isVictory: Boolean = false,
    val collisionCount: Int = 0,
    val maxCollisions: Int = 8,
    val ambientLight: Float = 500f,
    val isDarkMode: Boolean = true,
    val sessionTimeSec: Int = 0,
    val levelHistory: List<LevelStats> = emptyList()
)

private val levels = listOf(
    LevelDef(
        name = "Calentamiento",
        goalX = 880f, goalY = 1650f,
        enemyX = 500f, enemyY = 1500f,
        walls = listOf(Wall(0f, 600f, 700f, 40f))
    ),
    LevelDef(
        name = "Zigzag",
        goalX = 120f, goalY = 1650f,
        enemyX = 850f, enemyY = 1500f,
        walls = listOf(
            Wall(300f, 450f, 700f, 40f),
            Wall(0f, 900f, 700f, 40f),
            Wall(300f, 1350f, 700f, 40f)
        )
    ),
    LevelDef(
        name = "Pasillos",
        goalX = 800f, goalY = 1650f,
        enemyX = 850f, enemyY = 400f,
        walls = listOf(
            Wall(250f, 0f, 40f, 1000f),
            Wall(550f, 800f, 40f, 1000f)
        )
    ),
    LevelDef(
        name = "Cruce",
        goalX = 860f, goalY = 1650f,
        enemyX = 800f, enemyY = 1000f,
        walls = listOf(
            Wall(0f, 400f, 600f, 40f),
            Wall(400f, 800f, 600f, 40f),
            Wall(0f, 1200f, 600f, 40f),
            Wall(700f, 1240f, 40f, 560f)
        )
    ),
    LevelDef(
        name = "Laberinto final",
        goalX = 150f, goalY = 1650f,
        enemyX = 850f, enemyY = 900f,
        walls = listOf(
            Wall(0f, 350f, 760f, 40f),
            Wall(240f, 750f, 760f, 40f),
            Wall(0f, 1150f, 760f, 40f),
            Wall(500f, 1190f, 40f, 360f)
        )
    )
)

class GameViewModel(private val sensorController: SensorController) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var gameJob: Job? = null
    private var timerJob: Job? = null
    private var currentTiltX = 0f
    private var currentTiltY = 0f
    private var touchingWall = false

    init {
        startSensorTracking()
        loadLevel(1)
    }

    fun getLevel(level: Int): LevelDef = levels[(level - 1).coerceIn(0, levels.lastIndex)]

    fun loadLevel(level: Int) {
        val def = getLevel(level)
        touchingWall = false
        _uiState.value = _uiState.value.copy(
            currentLevel = level,
            ballX = 100f,
            ballY = 100f,
            enemyX = def.enemyX,
            enemyY = def.enemyY,
            goalX = def.goalX,
            goalY = def.goalY,
            isGameOver = false,
            isVictory = false,
            collisionCount = 0,
            maxCollisions = collisionLimit(level),
            sessionTimeSec = 0
        )
        startEnemyAndTimer()
    }

    private fun startSensorTracking() {
        viewModelScope.launch {
            try {
                sensorController.getSensorData(SensorType.GRAVITY)
                    .catch { }
                    .collect { reading ->
                        if (reading.values.isNotEmpty()) {
                            currentTiltX = reading.values[0] * -1f
                            currentTiltY = reading.values[1]
                        }
                    }
            } catch (e: Exception) {
            }
        }

        viewModelScope.launch {
            try {
                sensorController.getSensorData(SensorType.LIGHT)
                    .catch { }
                    .collect { reading ->
                        if (reading.values.isNotEmpty()) {
                            val lux = reading.values[0]
                            _uiState.value = _uiState.value.copy(
                                ambientLight = lux,
                                isDarkMode = lux < 50f
                            )
                        }
                    }
            } catch (e: Exception) {
            }
        }
    }

    private fun hitsWall(x: Float, y: Float, walls: List<Wall>): Boolean = walls.any {
        x > it.x - BALL_R && x < it.x + it.w + BALL_R &&
                y > it.y - BALL_R && y < it.y + it.h + BALL_R
    }

    private fun startEnemyAndTimer() {
        gameJob?.cancel()
        timerJob?.cancel()

        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val s = _uiState.value
                if (!s.isGameOver && !s.isVictory) {
                    _uiState.value = s.copy(sessionTimeSec = s.sessionTimeSec + 1)
                }
            }
        }

        gameJob = viewModelScope.launch {
            while (true) {
                delay(30L)
                val s = _uiState.value
                if (s.isGameOver || s.isVictory) continue
                val def = getLevel(s.currentLevel)

                // La esfera se desliza por las paredes (X e Y por separado)
                val nx = (s.ballX + currentTiltX * TILT_SPEED).coerceIn(40f, 960f)
                val ny = (s.ballY + currentTiltY * TILT_SPEED).coerceIn(40f, 1760f)
                var bx = s.ballX
                var by = s.ballY
                var blocked = false
                if (hitsWall(nx, by, def.walls)) blocked = true else bx = nx
                if (hitsWall(bx, ny, def.walls)) blocked = true else by = ny

                // Un choque cuenta una sola vez por contacto
                val collisions = if (blocked && !touchingWall) s.collisionCount + 1 else s.collisionCount
                touchingWall = blocked

                val enemySpeed = 2f + s.currentLevel * 0.5f
                val dx = bx - s.enemyX
                val dy = by - s.enemyY
                val dist = sqrt(dx * dx + dy * dy)
                var ex = s.enemyX
                var ey = s.enemyY
                if (dist > 5f) {
                    ex += (dx / dist) * enemySpeed
                    ey += (dy / dist) * enemySpeed
                }

                val reached = abs(bx - def.goalX) < 40f && abs(by - def.goalY) < 40f
                val caught = dist < 40f
                val tooMany = collisions >= s.maxCollisions

                if (reached || caught || tooMany) {
                    val outcome = when {
                        reached -> LevelOutcome.GOAL
                        caught -> LevelOutcome.CAUGHT
                        else -> LevelOutcome.CRASHED
                    }
                    val stats = LevelStats(s.currentLevel, s.sessionTimeSec, collisions, reached, outcome)
                    val history = s.levelHistory.filter { it.level != s.currentLevel } + stats
                    _uiState.value = s.copy(
                        ballX = bx,
                        ballY = by,
                        enemyX = ex,
                        enemyY = ey,
                        collisionCount = collisions,
                        isVictory = reached,
                        isGameOver = !reached,
                        levelHistory = history.sortedBy { it.level }
                    )
                    continue
                }

                _uiState.value = s.copy(
                    ballX = bx,
                    ballY = by,
                    enemyX = ex,
                    enemyY = ey,
                    collisionCount = collisions
                )
            }
        }
    }

    // TOP-DOWN: Sesión -> cómo terminó cada nivel -> niveles
    fun getTopDownGameTree(): List<TreeNode> {
        val history = _uiState.value.levelHistory
        if (history.isEmpty()) return emptyList()

        fun levelNode(s: LevelStats) = TreeNode(
            label = "Nivel ${s.level}",
            detail = "${s.collisions} choques",
            type = if (s.completed) NodeType.ACTIVE else NodeType.PAUSE,
            timeMs = s.timeSec * 1000L
        )

        fun groupNode(label: String, list: List<LevelStats>) = TreeNode(
            label = label,
            detail = "${list.size} niveles · ${list.sumOf { it.collisions }} choques",
            type = NodeType.BLOCK,
            timeMs = list.sumOf { it.timeSec } * 1000L,
            children = list.map { levelNode(it) }
        )

        val groups = buildList {
            val goal = history.filter { it.outcome == LevelOutcome.GOAL }
            val caught = history.filter { it.outcome == LevelOutcome.CAUGHT }
            val crashed = history.filter { it.outcome == LevelOutcome.CRASHED }
            if (goal.isNotEmpty()) add(groupNode("Llegaste a la meta", goal))
            if (caught.isNotEmpty()) add(groupNode("Te atrapó el enemigo", caught))
            if (crashed.isNotEmpty()) add(groupNode("Demasiados choques", crashed))
        }

        val root = TreeNode(
            label = "Sesión global",
            detail = "${history.size} niveles · ${history.sumOf { it.collisions }} choques",
            type = NodeType.SESSION,
            timeMs = (history.sumOf { it.timeSec } * 1000L).coerceAtLeast(1L),
            children = groups
        )
        return listOf(root)
    }
}