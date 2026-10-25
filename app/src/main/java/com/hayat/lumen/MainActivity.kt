package com.hayat.lumen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hayat.lumen.ui.hello.HelloScreen
import com.hayat.lumen.ui.theme.LumenTheme

/** Single Activity: every screen is a composable inside it. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LumenTheme {
                HelloScreen()
            }
        }
    }
}
