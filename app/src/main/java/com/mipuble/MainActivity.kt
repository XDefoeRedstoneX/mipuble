package com.mipuble

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mipuble.ui.library.LibraryScreen
import com.mipuble.ui.theme.MipubleTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-Activity host. All screens are Composables; multi-screen navigation
 * arrives in Phase 2 alongside the reader screen.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MipubleTheme {
                LibraryScreen()
            }
        }
    }
}
