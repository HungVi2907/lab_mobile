package com.example.flappy_bird_clone

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.flappy_bird_clone.models.GameDimensions
import com.example.flappy_bird_clone.models.GamePhysicsConfigPx
import com.example.flappy_bird_clone.models.PlayState
import kotlin.math.roundToInt

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val gameState by viewModel.gameState.collectAsState()
    val density = LocalDensity.current
    val context = LocalContext.current

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

    // Custom UI assets from res/drawable.
    // TODO: Thay tên file ảnh của bạn vào đây
    val backgroundPainter: Painter = painterResource(id = R.drawable.bg_game)
    val birdPainter: Painter = painterResource(id = R.drawable.player_image)
    val pipeUpperPainter: Painter = painterResource(id = R.drawable.pipe_upper)
    val pipeLowerPainter: Painter = painterResource(id = R.drawable.pipe_lower)

    val groundResId = remember(context) {
        // Add a real image at res/drawable/ground_sprite.png (or .webp). Fallback keeps app runnable.
        context.resources.getIdentifier("ground_sprite", "drawable", context.packageName)
    }
    val groundPainter: Painter = painterResource(
        id = if (groundResId != 0) groundResId else R.drawable.bg_game,
    )

    val groundTopPx = gameState.screenHeightPx - physicsConfigPx.groundHeightPx

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .onSizeChanged { size ->
                viewModel.onScreenSizeChanged(size.width, size.height, physicsConfigPx)
            }
            .pointerInput(gameState.playState) {
                if (gameState.playState != PlayState.MainMenu) {
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
            val lowerY = pipe.gapTopY + physicsConfigPx.pipeGapHeightPx
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

        Image(
            painter = groundPainter,
            contentDescription = "Ground",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(GameDimensions.GROUND_HEIGHT),
            contentScale = ContentScale.FillBounds,
        )

        if (gameState.playState != PlayState.MainMenu) {
            Text(
                text = "Score: ${gameState.score}",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                fontWeight = FontWeight.Bold,
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
                    Button(onClick = { viewModel.onStartFromMenu() }) {
                        Text(text = "Start Game")
                    }
                }
            }

            PlayState.WaitingToStart -> {
                Text(
                    text = "Tap to Start",
                    modifier = Modifier.align(Alignment.Center),
                    fontWeight = FontWeight.Bold,
                )
            }

            PlayState.GameOver -> {
                Text(
                    text = "Game Over - Tap to Restart",
                    modifier = Modifier.align(Alignment.Center),
                    fontWeight = FontWeight.Bold,
                )
            }

            PlayState.Playing -> Unit
        }
    }
}
