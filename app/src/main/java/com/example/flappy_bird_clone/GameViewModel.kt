package com.example.flappy_bird_clone

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.flappy_bird_clone.ai.WinnerNetworkEvaluator
import com.example.flappy_bird_clone.ai.WinnerNetworkLoader
import com.example.flappy_bird_clone.ai.WinnerNetworkModel
import com.example.flappy_bird_clone.models.Bird
import com.example.flappy_bird_clone.models.ControlMode
import com.example.flappy_bird_clone.models.Difficulty
import com.example.flappy_bird_clone.models.GamePhysicsConfigPx
import com.example.flappy_bird_clone.models.GameState
import com.example.flappy_bird_clone.models.GameTuning
import com.example.flappy_bird_clone.models.Pipe
import com.example.flappy_bird_clone.models.PlayState
import com.example.flappy_bird_clone.models.SoundCue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class GameViewModel(
    private val loadHighScore: () -> Int = { 0 },
    private val saveHighScore: (Int) -> Unit = {},
    private val aiModelProvider: () -> Result<WinnerNetworkModel> = {
        Result.failure(IllegalStateException("AI model provider is not configured"))
    },
    private val flapDecisionThreshold: Float = DEFAULT_AI_THRESHOLD,
    private val aiFlapCooldownMs: Long = DEFAULT_AI_COOLDOWN_MS,
    private val startMode: ControlMode = DEFAULT_START_MODE,
    private val isThresholdOverrideEnabled: Boolean = false,
    private val nowNanoTime: () -> Long = System::nanoTime,
) : ViewModel() {

    private data class RuntimeTuning(
        val gravityPxPerSecSquared: Float,
        val flapVelocityPxPerSec: Float,
        val pipeSpeedPxPerSec: Float,
        val pipeGapHeightPx: Float,
        val pipeSpacingPx: Float,
    )

    companion object {
        private const val PREFS_NAME: String = "flappy_bird_clone_prefs"
        private const val HIGH_SCORE_KEY: String = "high_score"
        private const val DEFAULT_MODEL_ASSET_PATH: String = "winner_network.json"
        private const val DEFAULT_AI_THRESHOLD: Float = 0.5f
        private const val DEFAULT_AI_COOLDOWN_MS: Long = 100L
        private val DEFAULT_START_MODE: ControlMode = ControlMode.Manual
        private const val AI_LOG_TAG: String = "FlappyAI"

        private data class ParsedAiArgs(
            val threshold: Float?,
            val cooldownMs: Long?,
            val startMode: ControlMode?,
        )

        private fun parseAiRuntimeArgs(rawArgs: String?): ParsedAiArgs {
            if (rawArgs.isNullOrBlank()) {
                return ParsedAiArgs(threshold = null, cooldownMs = null, startMode = null)
            }

            var threshold: Float? = null
            var cooldownMs: Long? = null
            var startMode: ControlMode? = null

            rawArgs
                .split(',', ';')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { entry ->
                    val keyValue = entry.split('=', ':', limit = 2)
                    if (keyValue.size != 2) return@forEach

                    val key = keyValue[0].trim().lowercase()
                    val value = keyValue[1].trim()

                    when (key) {
                        "threshold", "th" -> {
                            val parsed = value.toFloatOrNull()
                            if (parsed != null) {
                                threshold = parsed.coerceIn(0f, 1f)
                            }
                        }

                        "cooldown", "cooldownms", "cooldown_ms" -> {
                            val parsed = value.toLongOrNull()
                            if (parsed != null) {
                                cooldownMs = parsed.coerceAtLeast(0L)
                            }
                        }

                        "startmode", "mode" -> {
                            startMode = when (value.lowercase()) {
                                "ai", "auto" -> ControlMode.Ai
                                "manual", "man", "human" -> ControlMode.Manual
                                else -> null
                            }
                        }
                    }
                }

            return ParsedAiArgs(
                threshold = threshold,
                cooldownMs = cooldownMs,
                startMode = startMode,
            )
        }

        fun provideFactory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (!modelClass.isAssignableFrom(GameViewModel::class.java)) {
                        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                    }

                    val appContext = context.applicationContext
                    val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val parsedAiArgs = parseAiRuntimeArgs(BuildConfig.AI_RUNTIME_ARGS)

                    @Suppress("UNCHECKED_CAST")
                    return GameViewModel(
                        loadHighScore = { preferences.getInt(HIGH_SCORE_KEY, 0) },
                        saveHighScore = { score ->
                            preferences.edit().putInt(HIGH_SCORE_KEY, score).apply()
                        },
                        aiModelProvider = {
                            WinnerNetworkLoader.loadFromAssets(
                                context = appContext,
                                assetPath = DEFAULT_MODEL_ASSET_PATH,
                            )
                        },
                        flapDecisionThreshold = parsedAiArgs.threshold ?: DEFAULT_AI_THRESHOLD,
                        aiFlapCooldownMs = parsedAiArgs.cooldownMs ?: DEFAULT_AI_COOLDOWN_MS,
                        startMode = parsedAiArgs.startMode ?: DEFAULT_START_MODE,
                        isThresholdOverrideEnabled = parsedAiArgs.threshold != null,
                    ) as T
                }
            }
        }
    }

    private var configPx: GamePhysicsConfigPx? = null
    private var gameLoopJob: Job? = null
    private val gameScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var persistedHighScoreLoaded: Boolean = false
    private var persistedHighScore: Int = 0

    private var aiEvaluator: WinnerNetworkEvaluator? = null
    private var aiThreshold: Float = flapDecisionThreshold
    private var lastAiFlapAtMs: Long = Long.MIN_VALUE / 4

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState

    init {
        initializeAiModel()
    }

    fun onScreenSizeChanged(widthPx: Int, heightPx: Int, physicsConfigPx: GamePhysicsConfigPx) {
        if (widthPx <= 0 || heightPx <= 0) return

        ensureHighScoreLoaded()
        configPx = physicsConfigPx

        val currentState = _gameState.value
        val width = widthPx.toFloat()
        val height = heightPx.toFloat()

        val needsInit =
            currentState.screenWidthPx != width ||
                currentState.screenHeightPx != height ||
                currentState.pipes.isEmpty()

        if (needsInit) {
            gameLoopJob?.cancel()

            _gameState.value = buildInitialState(
                widthPx = width,
                heightPx = height,
                config = physicsConfigPx,
                playState = PlayState.MainMenu,
                difficulty = currentState.difficulty,
                highScore = maxOf(currentState.highScore, persistedHighScore),
                isSoundEnabled = currentState.isSoundEnabled,
                controlMode = currentState.controlMode,
                isAiAvailable = currentState.isAiAvailable,
                aiFallbackReason = currentState.aiFallbackReason,
            )
        }
    }

    fun onStartFromMenu() {
        val state = _gameState.value
        if (state.playState != PlayState.MainMenu) return

        _gameState.value = withSoundCue(
            state.copy(playState = PlayState.WaitingToStart),
            SoundCue.Start,
        )
    }

    fun onTap() {
        when (_gameState.value.playState) {
            PlayState.MainMenu,
            PlayState.Paused,
            -> Unit

            PlayState.WaitingToStart -> {
                startPlaying()
                if (_gameState.value.controlMode == ControlMode.Manual) {
                    flap()
                }
            }

            PlayState.Playing -> {
                if (_gameState.value.controlMode == ControlMode.Manual) {
                    flap()
                }
            }

            PlayState.GameOver -> resetToWaiting()
        }
    }

    fun onTogglePause() {
        val state = _gameState.value
        when (state.playState) {
            PlayState.Playing -> {
                gameLoopJob?.cancel()
                _gameState.value = state.copy(playState = PlayState.Paused)
            }

            PlayState.Paused -> {
                _gameState.value = state.copy(playState = PlayState.Playing)
                startPlaying()
            }

            else -> Unit
        }
    }

    fun onToggleSound() {
        val state = _gameState.value
        val enabled = !state.isSoundEnabled
        val nextState = state.copy(isSoundEnabled = enabled)

        _gameState.value = if (enabled) {
            withSoundCue(nextState, SoundCue.Start)
        } else {
            nextState.copy(soundCue = SoundCue.None)
        }
    }

    fun onToggleControlMode() {
        val state = _gameState.value
        if (!state.isAiAvailable) {
            _gameState.value = state.copy(controlMode = ControlMode.Manual)
            return
        }

        val nextMode = if (state.controlMode == ControlMode.Manual) {
            ControlMode.Ai
        } else {
            ControlMode.Manual
        }

        if (nextMode == ControlMode.Ai) {
            lastAiFlapAtMs = Long.MIN_VALUE / 4
        }

        _gameState.value = state.copy(controlMode = nextMode)
    }

    fun onDifficultySelected(difficulty: Difficulty) {
        val state = _gameState.value
        if (state.difficulty == difficulty) return
        if (state.playState == PlayState.Playing) return

        val localConfig = configPx
        if (localConfig == null || state.screenWidthPx <= 0f || state.screenHeightPx <= 0f) {
            _gameState.value = state.copy(difficulty = difficulty)
            return
        }

        gameLoopJob?.cancel()
        val nextPlayState = if (state.playState == PlayState.MainMenu) {
            PlayState.MainMenu
        } else {
            PlayState.WaitingToStart
        }

        _gameState.value = buildInitialState(
            widthPx = state.screenWidthPx,
            heightPx = state.screenHeightPx,
            config = localConfig,
            playState = nextPlayState,
            difficulty = difficulty,
            highScore = maxOf(state.highScore, persistedHighScore),
            isSoundEnabled = state.isSoundEnabled,
            controlMode = state.controlMode,
            isAiAvailable = state.isAiAvailable,
            aiFallbackReason = state.aiFallbackReason,
        )
    }

    private fun initializeAiModel() {
        val modelResult = runCatching { aiModelProvider() }
            .getOrElse { Result.failure(it) }

        if (modelResult.isFailure) {
            val message = modelResult.exceptionOrNull()?.message ?: "Unable to load AI model"
            _gameState.value = _gameState.value.copy(
                controlMode = ControlMode.Manual,
                isAiAvailable = false,
                aiFallbackReason = message,
            )
            Log.d(
                AI_LOG_TAG,
                "initializeAiModel status=UNAVAILABLE threshold=$aiThreshold mode=${_gameState.value.controlMode} reason=$message",
            )
            return
        }

        val model = modelResult.getOrNull() ?: return
        val evaluatorResult = runCatching { WinnerNetworkEvaluator(model) }

        if (evaluatorResult.isFailure) {
            val message = evaluatorResult.exceptionOrNull()?.message ?: "Unable to initialize AI"
            _gameState.value = _gameState.value.copy(
                controlMode = ControlMode.Manual,
                isAiAvailable = false,
                aiFallbackReason = message,
            )
            Log.d(
                AI_LOG_TAG,
                "initializeAiModel status=UNAVAILABLE threshold=$aiThreshold mode=${_gameState.value.controlMode} reason=$message",
            )
            return
        }

        aiEvaluator = evaluatorResult.getOrNull()
        aiThreshold = if (isThresholdOverrideEnabled) {
            flapDecisionThreshold
        } else {
            model.metadata.flapThreshold ?: flapDecisionThreshold
        }

        val initialMode = if (startMode == ControlMode.Ai) ControlMode.Ai else ControlMode.Manual
        _gameState.value = _gameState.value.copy(
            controlMode = initialMode,
            isAiAvailable = true,
            aiFallbackReason = null,
        )
        Log.d(
            AI_LOG_TAG,
            "initializeAiModel status=AVAILABLE threshold=$aiThreshold mode=${_gameState.value.controlMode} cooldownMs=$aiFlapCooldownMs thresholdOverride=$isThresholdOverrideEnabled",
        )
    }

    private fun startPlaying() {
        if (gameLoopJob?.isActive == true) return
        if (_gameState.value.playState != PlayState.Playing) {
            _gameState.value = _gameState.value.copy(playState = PlayState.Playing)
        }

        gameLoopJob = gameScope.launch {
            var previousNanos = System.nanoTime()
            while (isActive && _gameState.value.playState == PlayState.Playing) {
                val nowNanos = System.nanoTime()
                val deltaSeconds = ((nowNanos - previousNanos) / 1_000_000_000f).coerceIn(0.0f, 0.05f)
                previousNanos = nowNanos

                update(deltaSeconds)
                delay(16L)
            }
        }
    }

    private fun resetToWaiting() {
        gameLoopJob?.cancel()
        val state = _gameState.value
        val localConfig = configPx ?: return

        _gameState.value = buildInitialState(
            widthPx = state.screenWidthPx,
            heightPx = state.screenHeightPx,
            config = localConfig,
            playState = PlayState.WaitingToStart,
            difficulty = state.difficulty,
            highScore = maxOf(state.highScore, persistedHighScore),
            isSoundEnabled = state.isSoundEnabled,
            controlMode = state.controlMode,
            isAiAvailable = state.isAiAvailable,
            aiFallbackReason = state.aiFallbackReason,
        )
    }

    private fun flap() {
        val localConfig = configPx ?: return
        val state = _gameState.value

        if (state.playState != PlayState.Playing && state.playState != PlayState.WaitingToStart) return

        val tuning = runtimeTuning(state.difficulty, localConfig)

        _gameState.value = withSoundCue(
            state.copy(
                bird = state.bird.copy(velocityY = tuning.flapVelocityPxPerSec),
            ),
            SoundCue.Flap,
        )
    }

    private fun update(deltaSeconds: Float) {
        val localConfig = configPx ?: return
        val state = _gameState.value

        if (state.playState != PlayState.Playing) return

        val tuning = runtimeTuning(state.difficulty, localConfig)
        var frameState = state

        if (frameState.controlMode == ControlMode.Ai && shouldAiFlap(frameState, localConfig, tuning)) {
            frameState = withSoundCue(
                frameState.copy(
                    bird = frameState.bird.copy(velocityY = tuning.flapVelocityPxPerSec),
                ),
                SoundCue.Flap,
            )
            lastAiFlapAtMs = nowNanoTime() / 1_000_000L
        }

        val groundTop = frameState.screenHeightPx - localConfig.groundHeightPx

        val velocityY = frameState.bird.velocityY + (tuning.gravityPxPerSecSquared * deltaSeconds)
        val birdY = frameState.bird.y + (velocityY * deltaSeconds)
        val nextBird = frameState.bird.copy(y = birdY, velocityY = velocityY)

        val movedPipes = frameState.pipes.map { pipe ->
            pipe.copy(x = pipe.x - (tuning.pipeSpeedPxPerSec * deltaSeconds))
        }

        val recycledPipes = recyclePipes(
            pipes = movedPipes,
            screenWidthPx = frameState.screenWidthPx,
            groundTop = groundTop,
            config = localConfig,
            pipeSpacingPx = tuning.pipeSpacingPx,
            pipeGapHeightPx = tuning.pipeGapHeightPx,
        )

        var nextScore = frameState.score
        val scoredPipes = recycledPipes.map { pipe ->
            if (!pipe.hasScored && nextBird.x > pipe.x + localConfig.pipeWidthPx) {
                nextScore += 1
                pipe.copy(hasScored = true)
            } else {
                pipe
            }
        }

        var nextHighScore = frameState.highScore
        if (nextScore > nextHighScore) {
            nextHighScore = nextScore
            persistedHighScore = nextHighScore
            runCatching { saveHighScore(nextHighScore) }
        }

        val hasCollision = detectCollision(
            bird = nextBird,
            pipes = scoredPipes,
            groundTop = groundTop,
            config = localConfig,
            pipeGapHeightPx = tuning.pipeGapHeightPx,
        )

        var nextState = frameState.copy(
            playState = if (hasCollision) PlayState.GameOver else PlayState.Playing,
            bird = if (hasCollision) nextBird.copy(velocityY = 0f) else nextBird,
            pipes = scoredPipes,
            score = nextScore,
            highScore = nextHighScore,
            pipeGapHeightPx = tuning.pipeGapHeightPx,
            pipeSpacingPx = tuning.pipeSpacingPx,
        )

        nextState = when {
            hasCollision -> withSoundCue(nextState, SoundCue.Hit)
            nextScore > frameState.score -> withSoundCue(nextState, SoundCue.Score)
            else -> nextState
        }

        _gameState.value = nextState
    }

    private fun shouldAiFlap(
        state: GameState,
        config: GamePhysicsConfigPx,
        tuning: RuntimeTuning,
    ): Boolean {
        val evaluator = aiEvaluator ?: return false
        if (!state.isAiAvailable) return false

        val nowMs = nowNanoTime() / 1_000_000L
        if (nowMs - lastAiFlapAtMs < aiFlapCooldownMs) return false

        val inputs = buildAiInputs(state, config, tuning, evaluator)
        val outputs = runCatching { evaluator.evaluate(inputs) }.getOrNull() ?: return false
        val flapScore = extractFlapScore(outputs, evaluator.outputOrder)

        return flapScore > aiThreshold
    }

    private fun buildAiInputs(
        state: GameState,
        config: GamePhysicsConfigPx,
        tuning: RuntimeTuning,
        evaluator: WinnerNetworkEvaluator,
    ): FloatArray {
        val nextPipe = findNextPipe(state.pipes, state.bird.x, config.pipeWidthPx)

        val birdCenterY = state.bird.y + (config.birdHeightPx * 0.5f)
        val safeScreenWidth = state.screenWidthPx.coerceAtLeast(1f)
        val safeScreenHeight = state.screenHeightPx.coerceAtLeast(1f)

        val gapTop = nextPipe?.gapTopY ?: (safeScreenHeight * 0.4f)
        val gapBottom = gapTop + tuning.pipeGapHeightPx
        val pipeX = nextPipe?.x ?: safeScreenWidth

        val featureMap = mapOf(
            "bird_y_norm" to clip01(state.bird.y / safeScreenHeight),
            "bird_velocity_norm" to clip(state.bird.velocityY / 1000f, -3f, 3f),
            "next_pipe_dx_norm" to clip(
                (pipeX + config.pipeWidthPx - state.bird.x) / safeScreenWidth,
                -2f,
                2f,
            ),
            "gap_top_delta_norm" to clip((birdCenterY - gapTop) / safeScreenHeight, -2f, 2f),
            "gap_bottom_delta_norm" to clip((birdCenterY - gapBottom) / safeScreenHeight, -2f, 2f),
        )

        return FloatArray(evaluator.inputOrder.size) { index ->
            featureMap[evaluator.inputOrder[index]] ?: 0f
        }
    }

    private fun findNextPipe(pipes: List<Pipe>, birdX: Float, pipeWidthPx: Float): Pipe? {
        if (pipes.isEmpty()) return null

        val ahead = pipes.filter { pipe -> (pipe.x + pipeWidthPx) >= birdX }
        return if (ahead.isNotEmpty()) {
            ahead.minByOrNull { it.x }
        } else {
            pipes.minByOrNull { it.x }
        }
    }

    private fun extractFlapScore(outputs: FloatArray, outputOrder: List<String>): Float {
        if (outputs.isEmpty()) return 0f
        val flapIndex = outputOrder.indexOf("flap_score")
        return if (flapIndex in outputs.indices) outputs[flapIndex] else outputs.first()
    }

    private fun clip(value: Float, minValue: Float, maxValue: Float): Float {
        return value.coerceIn(minValue, maxValue)
    }

    private fun clip01(value: Float): Float {
        return clip(value, 0f, 1f)
    }

    private fun recyclePipes(
        pipes: List<Pipe>,
        screenWidthPx: Float,
        groundTop: Float,
        config: GamePhysicsConfigPx,
        pipeSpacingPx: Float,
        pipeGapHeightPx: Float,
    ): List<Pipe> {
        val rightMostX = pipes.maxOfOrNull { it.x } ?: screenWidthPx
        var spawnCursor = rightMostX

        return pipes.map { pipe ->
            if (pipe.x + config.pipeWidthPx >= 0f) {
                pipe
            } else {
                spawnCursor += pipeSpacingPx + randomSpacingJitter(pipeSpacingPx)
                pipe.copy(
                    x = spawnCursor,
                    gapTopY = randomGapTop(groundTop, config, pipeGapHeightPx),
                    hasScored = false,
                )
            }
        }
    }

    private fun detectCollision(
        bird: Bird,
        pipes: List<Pipe>,
        groundTop: Float,
        config: GamePhysicsConfigPx,
        pipeGapHeightPx: Float,
    ): Boolean {
        // Shrink collider to match visible content when sprites have transparent padding.
        val birdInsetX = config.birdWidthPx * 0.18f
        val birdInsetY = config.birdHeightPx * 0.14f
        val pipeInsetX = config.pipeWidthPx * 0.08f

        val birdLeft = bird.x + birdInsetX
        val birdRight = bird.x + config.birdWidthPx - birdInsetX
        val birdTop = bird.y + birdInsetY
        val birdBottom = bird.y + config.birdHeightPx - birdInsetY

        if (birdTop <= 0f || birdBottom >= groundTop) return true

        return pipes.any { pipe ->
            val pipeLeft = pipe.x + pipeInsetX
            val pipeRight = pipe.x + config.pipeWidthPx - pipeInsetX

            val overlapsX = birdRight > pipeLeft && birdLeft < pipeRight
            if (!overlapsX) {
                false
            } else {
                val upperPipeBottom = pipe.gapTopY
                val lowerPipeTop = pipe.gapTopY + pipeGapHeightPx
                birdTop < upperPipeBottom || birdBottom > lowerPipeTop
            }
        }
    }

    private fun buildInitialState(
        widthPx: Float,
        heightPx: Float,
        config: GamePhysicsConfigPx,
        playState: PlayState,
        difficulty: Difficulty,
        highScore: Int,
        isSoundEnabled: Boolean,
        controlMode: ControlMode,
        isAiAvailable: Boolean,
        aiFallbackReason: String?,
    ): GameState {
        val tuning = runtimeTuning(difficulty, config)

        val bird = Bird(
            x = widthPx * GameTuning.BIRD_X_RATIO,
            y = heightPx * GameTuning.BIRD_Y_RATIO,
            velocityY = 0f,
        )

        val groundTop = heightPx - config.groundHeightPx
        val firstPipeX = widthPx * (1f + GameTuning.PIPE_SPAWN_OFFSET_RATIO)

        val pipes = List(GameTuning.PIPE_COUNT) { index ->
            Pipe(
                x = firstPipeX + (index * tuning.pipeSpacingPx),
                gapTopY = randomGapTop(groundTop, config, tuning.pipeGapHeightPx),
                hasScored = false,
            )
        }

        val sanitizedMode = if (isAiAvailable) controlMode else ControlMode.Manual

        return GameState(
            playState = playState,
            bird = bird,
            pipes = pipes,
            score = 0,
            highScore = highScore,
            screenWidthPx = widthPx,
            screenHeightPx = heightPx,
            difficulty = difficulty,
            isSoundEnabled = isSoundEnabled,
            controlMode = sanitizedMode,
            isAiAvailable = isAiAvailable,
            aiFallbackReason = aiFallbackReason,
            pipeGapHeightPx = tuning.pipeGapHeightPx,
            pipeSpacingPx = tuning.pipeSpacingPx,
        )
    }

    private fun runtimeTuning(difficulty: Difficulty, config: GamePhysicsConfigPx): RuntimeTuning {
        val profile = GameTuning.profileFor(difficulty)
        val gapHeightPx = (config.pipeGapHeightPx * profile.pipeGapMultiplier)
            .coerceAtLeast(config.birdHeightPx * 2.3f)
        val spacingPx = (config.pipeSpacingPx * profile.pipeSpacingMultiplier)
            .coerceAtLeast(config.pipeWidthPx * 1.8f)

        return RuntimeTuning(
            gravityPxPerSecSquared = GameTuning.GRAVITY_PX_PER_SEC_SQUARED * profile.gravityMultiplier,
            flapVelocityPxPerSec = GameTuning.FLAP_VELOCITY_PX_PER_SEC * profile.flapVelocityMultiplier,
            pipeSpeedPxPerSec = GameTuning.PIPE_SPEED_PX_PER_SEC * profile.pipeSpeedMultiplier,
            pipeGapHeightPx = gapHeightPx,
            pipeSpacingPx = spacingPx,
        )
    }

    private fun randomGapTop(
        groundTop: Float,
        config: GamePhysicsConfigPx,
        pipeGapHeightPx: Float,
    ): Float {
        val minGapTop = config.pipeVerticalMarginPx
        val maxGapTop = groundTop - pipeGapHeightPx - config.pipeVerticalMarginPx

        if (maxGapTop <= minGapTop) return minGapTop

        return Random.nextFloat() * (maxGapTop - minGapTop) + minGapTop
    }

    private fun randomSpacingJitter(spacingPx: Float): Float {
        val jitter = spacingPx * GameTuning.PIPE_SPACING_JITTER_RATIO
        return Random.nextFloat() * jitter
    }

    private fun withSoundCue(state: GameState, cue: SoundCue): GameState {
        if (!state.isSoundEnabled || cue == SoundCue.None) {
            return state
        }

        return state.copy(
            soundCue = cue,
            soundCueToken = state.soundCueToken + 1,
        )
    }

    private fun ensureHighScoreLoaded() {
        if (persistedHighScoreLoaded) return

        persistedHighScoreLoaded = true
        persistedHighScore = runCatching { loadHighScore() }
            .getOrDefault(0)
            .coerceAtLeast(0)

        if (persistedHighScore > _gameState.value.highScore) {
            _gameState.value = _gameState.value.copy(highScore = persistedHighScore)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun setGameStateForTest(state: GameState) {
        _gameState.value = state
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun advanceFrameForTest(deltaSeconds: Float) {
        update(deltaSeconds.coerceAtLeast(0f))
    }

    override fun onCleared() {
        gameLoopJob?.cancel()
        gameScope.cancel()
        super.onCleared()
    }
}
