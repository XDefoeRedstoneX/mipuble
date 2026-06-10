package com.mipuble

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mipuble.ui.MipubleNavHost
import com.mipuble.ui.theme.MipubleTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-Activity host. Screens are Composables wired together by [MipubleNavHost].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MipubleTheme {
                MipubleNavHost()
            }
        }
    }
}
