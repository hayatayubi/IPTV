package com.hayat.lumen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.hayat.lumen.ui.LumenRoot
import com.hayat.lumen.ui.theme.LumenTheme

/** Single Activity: every screen is a composable inside it. */
class MainActivity : ComponentActivity() {
    private val container get() = (application as LumenApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                LumenTheme {
                    LumenRoot(onExit = ::finish)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // App left the screen (Home pressed, another app opened): free the decoders.
        container.player.release()
    }
}
