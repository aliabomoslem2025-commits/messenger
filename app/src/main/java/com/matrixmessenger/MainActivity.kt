package com.matrixmessenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.matrixmessenger.data.local.AppPreferences
import com.matrixmessenger.ui.navigation.AppNavigation
import com.matrixmessenger.core.designsystem.tokens.MatrixMessengerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var matrixClientManager: com.matrixmessenger.data.matrix.MatrixClientManager

    @Inject
    lateinit var messageRenderer: com.matrixmessenger.feature.message.presentation.renderer.MessageRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        
        // Check auth state before showing content
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                matrixClientManager.sessionState.collect { state ->
                    if (state !is com.matrixmessenger.data.matrix.MatrixClientManager.SessionState.Loading) {
                        splashScreen.setKeepOnScreenCondition { false }
                    }
                }
            }
        }
        
        setContent {
            val isDarkTheme by appPreferences.isDarkTheme.collectAsState(initial = false)
            
            MatrixMessengerTheme(
                darkTheme = isDarkTheme || isSystemInDarkTheme()
            ) {
                // Force LTR for the app shell, matching Telegram style
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    AppNavigation(messageRenderer = messageRenderer)
                }
            }
        }
    }
}
