package com.sandpause.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import com.sandpause.app.ui.nav.AppNavHost
import com.sandpause.app.ui.theme.SandPauseTheme

/**
 * The parent area: setup, PIN gate, settings, reports. It is a single activity
 * with a Compose navigation graph. The child's lock screen is ChildLockActivity.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SandPauseTheme {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                ) {
                    AppNavHost()
                }
            }
        }
    }
}
