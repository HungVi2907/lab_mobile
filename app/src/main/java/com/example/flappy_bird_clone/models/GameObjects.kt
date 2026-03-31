package com.example.flappy_bird_clone.models

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Size and feel configuration.
 *
 * Update this object first when replacing assets or tuning gameplay.
 */
object GameDimensions {
    val BIRD_WIDTH: Dp = 64.dp
    val BIRD_HEIGHT: Dp = 48.dp

    val PIPE_WIDTH: Dp = 92.dp
    val PIPE_GAP_HEIGHT: Dp = 190.dp
    val PIPE_SPACING: Dp = 250.dp

    // Ground strip height used by both rendering and collision.
    val GROUND_HEIGHT: Dp = 96.dp
    val PIPE_VERTICAL_MARGIN: Dp = 44.dp
}

object GameTuning {
    const val GRAVITY_PX_PER_SEC_SQUARED: Float = 1850f
    const val FLAP_VELOCITY_PX_PER_SEC: Float = -670f
    const val PIPE_SPEED_PX_PER_SEC: Float = 210f

    const val BIRD_X_RATIO: Float = 0.28f
    const val BIRD_Y_RATIO: Float = 0.42f

    const val PIPE_COUNT: Int = 3
    const val PIPE_SPAWN_OFFSET_RATIO: Float = 0.28f
    const val PIPE_SPACING_JITTER_RATIO: Float = 0.18f

    data class DifficultyProfile(
        val gravityMultiplier: Float,
        val flapVelocityMultiplier: Float,
        val pipeSpeedMultiplier: Float,
        val pipeGapMultiplier: Float,
        val pipeSpacingMultiplier: Float,
    )

    fun profileFor(difficulty: Difficulty): DifficultyProfile {
        return when (difficulty) {
            Difficulty.Easy -> DifficultyProfile(
                gravityMultiplier = 0.9f,
                flapVelocityMultiplier = 1.06f,
                pipeSpeedMultiplier = 0.86f,
                pipeGapMultiplier = 1.2f,
                pipeSpacingMultiplier = 1.1f,
            )

            Difficulty.Normal -> DifficultyProfile(
                gravityMultiplier = 1f,
                flapVelocityMultiplier = 1f,
                pipeSpeedMultiplier = 1f,
                pipeGapMultiplier = 1f,
                pipeSpacingMultiplier = 1f,
            )

            Difficulty.Hard -> DifficultyProfile(
                gravityMultiplier = 1.14f,
                flapVelocityMultiplier = 0.95f,
                pipeSpeedMultiplier = 1.18f,
                pipeGapMultiplier = 0.82f,
                pipeSpacingMultiplier = 0.9f,
            )
        }
    }
}

enum class Difficulty {
    Easy,
    Normal,
    Hard,
}

enum class SoundCue {
    None,
    Start,
    Flap,
    Score,
    Hit,
}

enum class PlayState {
    MainMenu,
    WaitingToStart,
    Playing,
    Paused,
    GameOver,
}

enum class ControlMode {
    Manual,
    Ai,
}

data class Bird(
    val x: Float,
    val y: Float,
    val velocityY: Float,
)

data class Pipe(
    val x: Float,
    val gapTopY: Float,
    val hasScored: Boolean,
)

data class GameState(
    val playState: PlayState = PlayState.MainMenu,
    val bird: Bird = Bird(0f, 0f, 0f),
    val pipes: List<Pipe> = emptyList(),
    val score: Int = 0,
    val highScore: Int = 0,
    val screenWidthPx: Float = 0f,
    val screenHeightPx: Float = 0f,
    val difficulty: Difficulty = Difficulty.Normal,
    val isSoundEnabled: Boolean = true,
    val controlMode: ControlMode = ControlMode.Manual,
    val isAiAvailable: Boolean = false,
    val aiFallbackReason: String? = null,
    val pipeGapHeightPx: Float = 0f,
    val pipeSpacingPx: Float = 0f,
    val soundCue: SoundCue = SoundCue.None,
    val soundCueToken: Int = 0,
)

data class GamePhysicsConfigPx(
    val birdWidthPx: Float,
    val birdHeightPx: Float,
    val pipeWidthPx: Float,
    val pipeGapHeightPx: Float,
    val pipeSpacingPx: Float,
    val groundHeightPx: Float,
    val pipeVerticalMarginPx: Float,
)
