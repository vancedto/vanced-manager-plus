package com.revanced.net.revancedmanager

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.revanced.net.revancedmanager.domain.model.ThemeMode
import androidx.hilt.navigation.compose.hiltViewModel
import com.revanced.net.revancedmanager.core.common.LocaleHelper
import com.revanced.net.revancedmanager.data.local.preferences.PreferencesManager
import com.revanced.net.revancedmanager.presentation.bloc.AppBloc
import com.revanced.net.revancedmanager.presentation.ui.screen.MainScreen
import com.revanced.net.revancedmanager.presentation.ui.theme.RevancedManagerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main activity for ReVanced Manager
 * Updated to use AppCompatActivity for modern language switching support
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var preferencesManager: PreferencesManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Install splash screen
        installSplashScreen()

        // Edge-to-edge: transparent status bar + nav bar, correct icon colors per theme
        enableEdgeToEdge()

        setContent {
            val viewModel: AppBloc = hiltViewModel()
            val state by viewModel.state.collectAsState()
            
            val themeMode = when (val currentState = state) {
                is com.revanced.net.revancedmanager.presentation.bloc.AppState.Success -> currentState.config.themeMode
                is com.revanced.net.revancedmanager.presentation.bloc.AppState.Error -> currentState.config.themeMode
                else -> ThemeMode.SYSTEM
            }

            // Keep system bar icons legible regardless of the app's chosen theme
            // (the app can force LIGHT/DARK independently of the system setting).
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            LaunchedEffect(darkTheme) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }

            RevancedManagerTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Activity cleanup - no special handling needed for the new approach
    }
    
    /**
     * Android 7+ quirk: after attachBaseContext the system may call applyOverrideConfiguration
     * and reset the locale back to the device default. We prevent that by copying the locale
     * we set in attachBaseContext back into overrideConfiguration before calling super.
     */
    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (overrideConfiguration != null) {
            // Preserve the uiMode (dark/light) that Android passes in, but inherit everything
            // else (including locale) from the base context we configured in attachBaseContext.
            val uiMode = overrideConfiguration.uiMode
            overrideConfiguration.setTo(baseContext.resources.configuration)
            overrideConfiguration.uiMode = uiMode
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    override fun attachBaseContext(newBase: Context?) {
        // Apply language configuration using LocaleHelper
        newBase?.let { context ->
            val sharedPrefs = context.getSharedPreferences("ReVancedManagerPreferences", Context.MODE_PRIVATE)
            val languageCode = sharedPrefs.getString("language", null)
            
            if (languageCode != null) {
                // Handle language codes with country codes (e.g., es-ES -> es)
                val cleanLanguageCode = if (languageCode.contains("-")) {
                    languageCode.split("-")[0]
                } else {
                    languageCode
                }
                
                val contextWithLanguage = LocaleHelper.setLocale(context, cleanLanguageCode)
                super.attachBaseContext(contextWithLanguage)
            } else {
                super.attachBaseContext(newBase)
            }
        } ?: super.attachBaseContext(newBase)
    }
}



