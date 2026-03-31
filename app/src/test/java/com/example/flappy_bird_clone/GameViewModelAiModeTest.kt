package com.example.flappy_bird_clone

import com.example.flappy_bird_clone.ai.WinnerNetworkMetadata
import com.example.flappy_bird_clone.ai.WinnerNetworkModel
import com.example.flappy_bird_clone.ai.WinnerNetworkNode
import com.example.flappy_bird_clone.ai.WinnerNetworkEdge
import com.example.flappy_bird_clone.models.ControlMode
import com.example.flappy_bird_clone.models.GamePhysicsConfigPx
import com.example.flappy_bird_clone.models.Pipe
import com.example.flappy_bird_clone.models.PlayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameViewModelAiModeTest {

    private val testConfig = GamePhysicsConfigPx(
        birdWidthPx = 64f,
        birdHeightPx = 48f,
        pipeWidthPx = 92f,
        pipeGapHeightPx = 190f,
        pipeSpacingPx = 250f,
        groundHeightPx = 96f,
        pipeVerticalMarginPx = 44f,
    )

    @Test
    fun toggleControlMode_staysManualWhenAiModelUnavailable() {
        val viewModel = GameViewModel(
            aiModelProvider = {
                Result.failure(IllegalStateException("missing winner_network.json"))
            },
        )

        viewModel.onScreenSizeChanged(1080, 1920, testConfig)

        val initial = viewModel.gameState.value
        assertFalse(initial.isAiAvailable)
        assertEquals(ControlMode.Manual, initial.controlMode)
        assertTrue(initial.aiFallbackReason?.contains("missing", ignoreCase = true) == true)

        viewModel.onToggleControlMode()
        assertEquals(ControlMode.Manual, viewModel.gameState.value.controlMode)
    }

    @Test
    fun aiMode_autoFlapsDuringPlayingWhenScoreExceedsThreshold() {
        var fakeNowNanos = 0L
        val viewModel = GameViewModel(
            aiModelProvider = { Result.success(createSimpleAlwaysFlapModel()) },
            flapDecisionThreshold = 0.5f,
            aiFlapCooldownMs = 0L,
            nowNanoTime = {
                fakeNowNanos += 200_000_000L
                fakeNowNanos
            },
        )

        viewModel.onScreenSizeChanged(1080, 1920, testConfig)
        assertTrue(viewModel.gameState.value.isAiAvailable)
        assertEquals(ControlMode.Manual, viewModel.gameState.value.controlMode)

        viewModel.onToggleControlMode()
        assertEquals(ControlMode.Ai, viewModel.gameState.value.controlMode)

        val baseState = viewModel.gameState.value
        val safeBird = baseState.bird.copy(y = 640f, velocityY = 220f)
        val safePipe = Pipe(
            x = safeBird.x + 260f,
            gapTopY = 520f,
            hasScored = false,
        )

        viewModel.setGameStateForTest(
            baseState.copy(
                playState = PlayState.Playing,
                bird = safeBird,
                pipes = listOf(safePipe),
                score = 0,
            ),
        )

        viewModel.advanceFrameForTest(0.016f)

        val updated = viewModel.gameState.value
        assertTrue(updated.bird.velocityY < 0f)
    }

    private fun createSimpleAlwaysFlapModel(): WinnerNetworkModel {
        return WinnerNetworkModel(
            metadata = WinnerNetworkMetadata(flapThreshold = 0.5f),
            inputOrder = listOf(
                "bird_y_norm",
                "bird_velocity_norm",
                "next_pipe_dx_norm",
                "gap_top_delta_norm",
                "gap_bottom_delta_norm",
            ),
            outputOrder = listOf("flap_score"),
            inputNodeIndices = listOf(0, 1, 2, 3, 4),
            outputNodeIndices = listOf(5),
            evaluationOrder = listOf(5),
            nodesCompact = listOf(
                WinnerNetworkNode(idx = 0, bias = 0.0, response = 1.0, activation = "identity"),
                WinnerNetworkNode(idx = 1, bias = 0.0, response = 1.0, activation = "identity"),
                WinnerNetworkNode(idx = 2, bias = 0.0, response = 1.0, activation = "identity"),
                WinnerNetworkNode(idx = 3, bias = 0.0, response = 1.0, activation = "identity"),
                WinnerNetworkNode(idx = 4, bias = 0.0, response = 1.0, activation = "identity"),
                WinnerNetworkNode(idx = 5, bias = 1.3, response = 1.0, activation = "tanh"),
            ),
            edgesCompact = listOf(
                WinnerNetworkEdge(srcIdx = 0, dstIdx = 5, weight = 0.05),
                WinnerNetworkEdge(srcIdx = 1, dstIdx = 5, weight = 0.02),
            ),
        )
    }
}

