package com.example.flappy_bird_clone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.flappy_bird_clone.ui.theme.Flappy_bird_cloneTheme

class MainActivity : ComponentActivity() {
    private val gameViewModel: GameViewModel by viewModels {
        GameViewModel.provideFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Flappy_bird_cloneTheme {
                GameScreen(viewModel = gameViewModel)
            }
        }
    }
}
