package com.example.flappy_bird_clone

import com.example.flappy_bird_clone.models.Difficulty
import com.example.flappy_bird_clone.models.GamePhysicsConfigPx
import com.example.flappy_bird_clone.models.Pipe
import com.example.flappy_bird_clone.models.PlayState
import com.example.flappy_bird_clone.models.SoundCue
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class ExampleUnitTest {

    private val testConfig = GamePhysicsConfigPx(
        birdWidthPx = 64f,
        birdHeightPx = 48f,
        pipeWidthPx = 92f,
        pipeGapHeightPx = 190f,
        pipeSpacingPx = 250f,
        groundHeightPx = 96f,
        pipeVerticalMarginPx = 44f,
    )

    private fun createReadyViewModel(
        loadHighScore: () -> Int = { 0 },
        saveHighScore: (Int) -> Unit = {},
    ): GameViewModel {
        return GameViewModel(
            loadHighScore = loadHighScore,
            saveHighScore = saveHighScore,
        ).apply {
            onScreenSizeChanged(1080, 1920, testConfig)
        }
    }

    @Test
    fun startFromMenu_movesStateToWaitingToStart() {
        val viewModel = createReadyViewModel()

        assertEquals(PlayState.MainMenu, viewModel.gameState.value.playState)
        viewModel.onStartFromMenu()
        assertEquals(PlayState.WaitingToStart, viewModel.gameState.value.playState)
    }

    @Test
    fun loadHighScore_appliesPersistedValueOnInit() {
        val viewModel = createReadyViewModel(loadHighScore = { 7 })

        assertEquals(7, viewModel.gameState.value.highScore)
    }

    @Test
    fun selectingHardDifficulty_reducesGapAndSpacing() {
        val viewModel = createReadyViewModel()
        val normalState = viewModel.gameState.value

        viewModel.onDifficultySelected(Difficulty.Hard)
        val hardState = viewModel.gameState.value

        assertEquals(Difficulty.Hard, hardState.difficulty)
        assertTrue(hardState.pipeGapHeightPx < normalState.pipeGapHeightPx)
        assertTrue(hardState.pipeSpacingPx < normalState.pipeSpacingPx)
    }

    @Test
    fun scoringPipe_increasesScoreAndHighScore() {
        var savedScore = -1
        val viewModel = createReadyViewModel(saveHighScore = { savedScore = it })
        val baseState = viewModel.gameState.value
        val bird = baseState.bird.copy(y = 520f, velocityY = 0f)
        val scoringPipe = Pipe(
            x = bird.x - 120f,
            gapTopY = 250f,
            hasScored = false,
        )

        viewModel.setGameStateForTest(
            baseState.copy(
                playState = PlayState.Playing,
                bird = bird,
                pipes = listOf(scoringPipe),
                score = 0,
                highScore = 0,
            ),
        )
        viewModel.advanceFrameForTest(deltaSeconds = 0.016f)

        val updatedState = viewModel.gameState.value
        assertEquals(1, updatedState.score)
        assertEquals(1, updatedState.highScore)
        assertEquals(1, savedScore)
        assertEquals(SoundCue.Score, updatedState.soundCue)
    }

    @Test
    fun collisionWithPipe_setsGameOverAndHitCue() {
        val viewModel = createReadyViewModel()
        val baseState = viewModel.gameState.value
        val bird = baseState.bird.copy(y = 120f, velocityY = 0f)
        val blockingPipe = Pipe(
            x = bird.x + 6f,
            gapTopY = 360f,
            hasScored = false,
        )

        viewModel.setGameStateForTest(
            baseState.copy(
                playState = PlayState.Playing,
                bird = bird,
                pipes = listOf(blockingPipe),
                score = 0,
            ),
        )
        viewModel.advanceFrameForTest(deltaSeconds = 0.016f)

        assertEquals(PlayState.GameOver, viewModel.gameState.value.playState)
        assertEquals(SoundCue.Hit, viewModel.gameState.value.soundCue)
    }

    @Test
    fun toggleSound_switchesSoundSetting() {
        val viewModel = createReadyViewModel()

        assertTrue(viewModel.gameState.value.isSoundEnabled)
        viewModel.onToggleSound()
        assertFalse(viewModel.gameState.value.isSoundEnabled)
        viewModel.onToggleSound()
        assertTrue(viewModel.gameState.value.isSoundEnabled)
    }
}