package com.example.flappy_bird_clone

import android.media.AudioManager
import android.media.ToneGenerator
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.flappy_bird_clone.models.ControlMode
import com.example.flappy_bird_clone.models.Difficulty
import com.example.flappy_bird_clone.models.GameDimensions
import com.example.flappy_bird_clone.models.GamePhysicsConfigPx
import com.example.flappy_bird_clone.models.PlayState
import com.example.flappy_bird_clone.models.SoundCue
import kotlin.math.roundToInt

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val gameState by viewModel.gameState.collectAsState()
    val density = LocalDensity.current

    val physicsConfigPx = remember(density) {
        with(density) {
            GamePhysicsConfigPx(
                birdWidthPx = GameDimensions.BIRD_WIDTH.toPx(),
                birdHeightPx = GameDimensions.BIRD_HEIGHT.toPx(),
                pipeWidthPx = GameDimensions.PIPE_WIDTH.toPx(),
                pipeGapHeightPx = GameDimensions.PIPE_GAP_HEIGHT.toPx(),
                pipeSpacingPx = GameDimensions.PIPE_SPACING.toPx(),
                groundHeightPx = GameDimensions.GROUND_HEIGHT.toPx(),
                pipeVerticalMarginPx = GameDimensions.PIPE_VERTICAL_MARGIN.toPx(),
            )
        }
    }

    val backgroundPainter: Painter = painterResource(id = R.drawable.bg_game)
    val birdPainter: Painter = painterResource(id = R.drawable.player_image)
    val pipeUpperPainter: Painter = painterResource(id = R.drawable.pipe_upper)
    val pipeLowerPainter: Painter = painterResource(id = R.drawable.pipe_lower)

    val activePipeGapPx = if (gameState.pipeGapHeightPx > 0f) {
        gameState.pipeGapHeightPx
    } else {
        physicsConfigPx.pipeGapHeightPx
    }

    val toneGenerator = remember {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        } catch (_: Throwable) {
            null
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            toneGenerator?.release()
        }
    }
    LaunchedEffect(gameState.soundCueToken, gameState.isSoundEnabled) {
        if (!gameState.isSoundEnabled) return@LaunchedEffect

        when (gameState.soundCue) {
            SoundCue.None -> Unit
            SoundCue.Start -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 60)
            SoundCue.Flap -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
            SoundCue.Score -> toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 80)
            SoundCue.Hit -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 140)
        }
    }

    val groundTopPx = gameState.screenHeightPx - physicsConfigPx.groundHeightPx

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .onSizeChanged { size ->
                viewModel.onScreenSizeChanged(size.width, size.height, physicsConfigPx)
            }
            .pointerInput(gameState.playState, gameState.controlMode) {
                val acceptsTap =
                    gameState.playState == PlayState.WaitingToStart ||
                        gameState.playState == PlayState.GameOver ||
                        (gameState.playState == PlayState.Playing && gameState.controlMode == ControlMode.Manual)

                if (acceptsTap) {
                    detectTapGestures(onTap = { viewModel.onTap() })
                }
            },
    ) {
        Image(
            painter = backgroundPainter,
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        gameState.pipes.forEach { pipe ->
            val upperHeightDp = with(density) { pipe.gapTopY.coerceAtLeast(1f).toDp() }
            val lowerY = pipe.gapTopY + activePipeGapPx
            val lowerHeightPx = (groundTopPx - lowerY).coerceAtLeast(1f)
            val lowerHeightDp = with(density) { lowerHeightPx.toDp() }

            Image(
                painter = pipeUpperPainter,
                contentDescription = "Upper pipe",
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = pipe.x.roundToInt(),
                            y = 0,
                        )
                    }
                    .size(width = GameDimensions.PIPE_WIDTH, height = upperHeightDp),
                contentScale = ContentScale.FillBounds,
            )

            Image(
                painter = pipeLowerPainter,
                contentDescription = "Lower pipe",
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = pipe.x.roundToInt(),
                            y = lowerY.roundToInt(),
                        )
                    }
                    .size(width = GameDimensions.PIPE_WIDTH, height = lowerHeightDp),
                contentScale = ContentScale.FillBounds,
            )
        }

        Image(
            painter = birdPainter,
            contentDescription = "Bird",
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = gameState.bird.x.roundToInt(),
                        y = gameState.bird.y.roundToInt(),
                    )
                }
                .size(GameDimensions.BIRD_WIDTH, GameDimensions.BIRD_HEIGHT),
            contentScale = ContentScale.FillBounds,
        )

        // AndroidView is used here because ground_sprite is a layer-list XML,
        // which painterResource does not support.
        AndroidView(
            factory = { viewContext ->
                ImageView(viewContext).apply {
                    scaleType = ImageView.ScaleType.FIT_XY
                    setImageResource(R.drawable.ground_sprite)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(GameDimensions.GROUND_HEIGHT),
            update = { imageView ->
                imageView.setImageResource(R.drawable.ground_sprite)
            },
        )

        if (gameState.playState != PlayState.MainMenu) {
            Text(
                text = "Score: ${gameState.score}   High: ${gameState.highScore}",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (gameState.playState == PlayState.Playing || gameState.playState == PlayState.Paused) {
                Button(onClick = { viewModel.onTogglePause() }) {
                    Text(text = if (gameState.playState == PlayState.Paused) "Resume" else "Pause")
                }
            }

            Button(onClick = { viewModel.onToggleControlMode() }, enabled = gameState.isAiAvailable) {
                Text(text = if (gameState.controlMode == ControlMode.Ai) "Mode: AI" else "Mode: Manual")
            }

            Button(onClick = { viewModel.onToggleSound() }) {
                Text(text = if (gameState.isSoundEnabled) "Sound: ON" else "Sound: OFF")
            }
        }

        if (!gameState.isAiAvailable && !gameState.aiFallbackReason.isNullOrBlank()) {
            Text(
                text = "AI unavailable: ${gameState.aiFallbackReason}",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = GameDimensions.GROUND_HEIGHT + 12.dp),
            )
        }


        when (gameState.playState) {
            PlayState.MainMenu -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(text = "Flappy Bird Clone", fontWeight = FontWeight.Bold)
                    Text(text = "Tap to flap and avoid pipes")
                    Text(text = "High Score: ${gameState.highScore}", fontWeight = FontWeight.Bold)
                    Text(text = "Control: ${if (gameState.controlMode == ControlMode.Ai) "AI" else "Manual"}")

                    DifficultySelector(
                        selected = gameState.difficulty,
                        onSelect = { level -> viewModel.onDifficultySelected(level) },
                    )

                    Button(onClick = { viewModel.onStartFromMenu() }) {
                        Text(text = "Start Game")
                    }
                }
            }

            PlayState.WaitingToStart -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(text = "Tap to Start", fontWeight = FontWeight.Bold)
                    Text(text = if (gameState.controlMode == ControlMode.Ai) "AI will flap automatically" else "Tap to flap")
                    DifficultySelector(
                        selected = gameState.difficulty,
                        onSelect = { level -> viewModel.onDifficultySelected(level) },
                    )
                }
            }

            PlayState.Paused -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(text = "Paused", fontWeight = FontWeight.Bold)
                    Text(text = "Use Resume to continue")
                }
            }

            PlayState.GameOver -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(text = "Game Over - Tap to Restart", fontWeight = FontWeight.Bold)
                    Text(text = "Final Score: ${gameState.score}")
                    Text(text = "High Score: ${gameState.highScore}", fontWeight = FontWeight.Bold)
                    DifficultySelector(
                        selected = gameState.difficulty,
                        onSelect = { level -> viewModel.onDifficultySelected(level) },
                    )
                }
            }

            PlayState.Playing -> Unit
        }
    }
}

@Composable
private fun DifficultySelector(
    selected: Difficulty,
    onSelect: (Difficulty) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Difficulty: ${difficultyLabel(selected)}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Difficulty.entries.forEach { difficulty ->
                Button(
                    onClick = { onSelect(difficulty) },
                    enabled = selected != difficulty,
                ) {
                    Text(text = difficultyLabel(difficulty))
                }
            }
        }
    }
}

private fun difficultyLabel(difficulty: Difficulty): String {
    return when (difficulty) {
        Difficulty.Easy -> "Easy"
        Difficulty.Normal -> "Normal"
        Difficulty.Hard -> "Hard"
    }
}