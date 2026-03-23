package com.example.flappy_bird_clone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flappy_bird_clone.models.Bird
import com.example.flappy_bird_clone.models.GamePhysicsConfigPx
import com.example.flappy_bird_clone.models.GameState
import com.example.flappy_bird_clone.models.GameTuning
import com.example.flappy_bird_clone.models.Pipe
import com.example.flappy_bird_clone.models.PlayState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class GameViewModel : ViewModel() {

    private var configPx: GamePhysicsConfigPx? = null
    private var gameLoopJob: Job? = null

    private val _gameState = kotlinx.coroutines.flow.MutableStateFlow(GameState())
    val gameState: kotlinx.coroutines.flow.StateFlow<GameState> = _gameState

    fun onScreenSizeChanged(widthPx: Int, heightPx: Int, physicsConfigPx: GamePhysicsConfigPx) {
        if (widthPx <= 0 || heightPx <= 0) return

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
            _gameState.value = buildInitialState(width, height, physicsConfigPx, PlayState.MainMenu)
        }
    }

    fun onStartFromMenu() {
        val state = _gameState.value
        if (state.playState != PlayState.MainMenu) return

        _gameState.value = state.copy(playState = PlayState.WaitingToStart)
    }

    fun onTap() {
        when (_gameState.value.playState) {
            PlayState.MainMenu -> Unit
            PlayState.WaitingToStart -> {
                startPlaying()
                flap()
            }

            PlayState.Playing -> flap()
            PlayState.GameOver -> resetToWaiting()
        }
    }

    private fun startPlaying() {
        if (gameLoopJob?.isActive == true) return
        _gameState.value = _gameState.value.copy(playState = PlayState.Playing)

        gameLoopJob = viewModelScope.launch {
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
        )
    }

    private fun flap() {
        if (configPx == null) return
        val state = _gameState.value

        if (state.playState == PlayState.GameOver) return

        _gameState.value = state.copy(
            bird = state.bird.copy(velocityY = GameTuning.FLAP_VELOCITY_PX_PER_SEC),
        )
    }

    private fun update(deltaSeconds: Float) {
        val localConfig = configPx ?: return
        val state = _gameState.value

        if (state.playState != PlayState.Playing) return

        val groundTop = state.screenHeightPx - localConfig.groundHeightPx

        val velocityY = state.bird.velocityY + (GameTuning.GRAVITY_PX_PER_SEC_SQUARED * deltaSeconds)
        val birdY = state.bird.y + (velocityY * deltaSeconds)
        val nextBird = state.bird.copy(y = birdY, velocityY = velocityY)

        val movedPipes = state.pipes.map { pipe ->
            pipe.copy(x = pipe.x - (GameTuning.PIPE_SPEED_PX_PER_SEC * deltaSeconds))
        }

        val recycledPipes = recyclePipes(movedPipes, state.screenWidthPx, groundTop, localConfig)

        var nextScore = state.score
        val scoredPipes = recycledPipes.map { pipe ->
            if (!pipe.hasScored && nextBird.x > pipe.x + localConfig.pipeWidthPx) {
                nextScore += 1
                pipe.copy(hasScored = true)
            } else {
                pipe
            }
        }

        val hasCollision = detectCollision(nextBird, scoredPipes, groundTop, localConfig)

        _gameState.value = state.copy(
            playState = if (hasCollision) PlayState.GameOver else PlayState.Playing,
            bird = if (hasCollision) nextBird.copy(velocityY = 0f) else nextBird,
            pipes = scoredPipes,
            score = nextScore,
        )
    }

    private fun recyclePipes(
        pipes: List<Pipe>,
        screenWidthPx: Float,
        groundTop: Float,
        config: GamePhysicsConfigPx,
    ): List<Pipe> {
        val rightMostX = pipes.maxOfOrNull { it.x } ?: screenWidthPx
        var spawnCursor = rightMostX

        return pipes.map { pipe ->
            if (pipe.x + config.pipeWidthPx >= 0f) {
                pipe
            } else {
                spawnCursor += config.pipeSpacingPx + randomSpacingJitter(config.pipeSpacingPx)
                pipe.copy(
                    x = spawnCursor,
                    gapTopY = randomGapTop(groundTop, config),
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
                val lowerPipeTop = pipe.gapTopY + config.pipeGapHeightPx
                birdTop < upperPipeBottom || birdBottom > lowerPipeTop
            }
        }
    }

    private fun buildInitialState(
        widthPx: Float,
        heightPx: Float,
        config: GamePhysicsConfigPx,
        playState: PlayState,
    ): GameState {
        val bird = Bird(
            x = widthPx * GameTuning.BIRD_X_RATIO,
            y = heightPx * GameTuning.BIRD_Y_RATIO,
            velocityY = 0f,
        )

        val groundTop = heightPx - config.groundHeightPx
        val firstPipeX = widthPx * (1f + GameTuning.PIPE_SPAWN_OFFSET_RATIO)

        val pipes = List(GameTuning.PIPE_COUNT) { index ->
            Pipe(
                x = firstPipeX + (index * config.pipeSpacingPx),
                gapTopY = randomGapTop(groundTop, config),
                hasScored = false,
            )
        }

        return GameState(
            playState = playState,
            bird = bird,
            pipes = pipes,
            score = 0,
            screenWidthPx = widthPx,
            screenHeightPx = heightPx,
        )
    }

    private fun randomGapTop(groundTop: Float, config: GamePhysicsConfigPx): Float {
        val minGapTop = config.pipeVerticalMarginPx
        val maxGapTop = groundTop - config.pipeGapHeightPx - config.pipeVerticalMarginPx

        if (maxGapTop <= minGapTop) return minGapTop

        return Random.nextFloat() * (maxGapTop - minGapTop) + minGapTop
    }

    private fun randomSpacingJitter(spacingPx: Float): Float {
        val jitter = spacingPx * GameTuning.PIPE_SPACING_JITTER_RATIO
        return Random.nextFloat() * jitter
    }

    override fun onCleared() {
        gameLoopJob?.cancel()
        super.onCleared()
    }
}
