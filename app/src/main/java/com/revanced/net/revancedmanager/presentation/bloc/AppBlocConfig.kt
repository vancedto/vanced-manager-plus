package com.revanced.net.revancedmanager.presentation.bloc

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.revanced.net.revancedmanager.R
import com.revanced.net.revancedmanager.core.common.LocaleHelper
import com.revanced.net.revancedmanager.domain.model.AppConfig
import com.revanced.net.revancedmanager.domain.model.Language
import com.revanced.net.revancedmanager.domain.model.ThemeMode
import kotlinx.coroutines.launch

// ============= CONFIGURATION =============

internal fun AppBloc.navigateToSettings() {
    val config = loadConfigSafely()
    when (val s = _state.value) {
        is AppState.Success -> _state.value = s.copy(showSettings = true, config = config)
        is AppState.Error   -> _state.value = s.copy(showSettings = true, config = config)
        is AppState.Loading -> _state.value = AppState.Success(apps = emptyList(), showSettings = true, config = config)
    }
}

internal fun AppBloc.navigateBackFromSettings() {
    setShowSettings(false)
}

internal fun AppBloc.saveSettings(config: AppConfig) {
    Log.i(TAG_BLOC, "=== SAVE SETTINGS START ===")

    val previousConfig = loadConfigSafely()
    preferencesManager.saveAppConfig(config)

    when (val s = _state.value) {
        is AppState.Success -> _state.value = s.copy(config = config, showSettings = false)
        is AppState.Error   -> _state.value = s.copy(config = config, showSettings = false)
        is AppState.Loading -> Unit
    }

    // Start or stop log capture when debug mode changes
    if (previousConfig.debugModeEnabled != config.debugModeEnabled) {
        if (config.debugModeEnabled) {
            Log.i(TAG_BLOC, "Debug mode enabled — starting log capture")
            debugLogManager.startCapture()
        } else {
            Log.i(TAG_BLOC, "Debug mode disabled — stopping log capture")
            debugLogManager.stopCapture()
        }
    }

    // Language change: apply locale then recreate activity (don't show toast — activity is about to restart)
    if (previousConfig.language != config.language) {
        val code = config.language.code.split("-")[0]
        LocaleHelper.applyLocaleToActivity(context, code)
    } else {
        // No locale change: safe to show toast in current language
        showToast(stringProvider.getString(R.string.configuration_saved))
    }
}

internal fun AppBloc.resetSettings() {
    Log.i(TAG_BLOC, "=== RESET SETTINGS ===")
    val defaults = AppConfig()
    preferencesManager.saveAppConfig(defaults)

    when (val s = _state.value) {
        is AppState.Success -> _state.value = s.copy(config = defaults, showSettings = false)
        is AppState.Error   -> _state.value = s.copy(config = defaults, showSettings = false)
        is AppState.Loading -> Unit
    }

    if (defaults.debugModeEnabled != true) {
        debugLogManager.stopCapture()
    }

    showToast(stringProvider.getString(R.string.settings_reset_to_defaults))
    // Recreate activity to apply default locale (English)
    LocaleHelper.applyLocaleToActivity(context, Language.ENGLISH.code)
}

internal fun AppBloc.shareDebugLogs() {
    val shared = debugLogManager.shareLatestLog()
    if (!shared) {
        showToast(stringProvider.getString(R.string.debug_no_logs_to_share))
    }
}

internal fun AppBloc.loadConfiguration() {
    val config = try {
        preferencesManager.getAppConfig()
    } catch (e: Exception) {
        Log.w(TAG_BLOC, "Failed to load configuration, using fallback", e)
        AppConfig(ThemeMode.DARK, Language.ENGLISH)
    }

    when (val currentState = _state.value) {
        is AppState.Success -> _state.value = currentState.copy(config = config)
        is AppState.Error -> _state.value = currentState.copy(config = config)
        is AppState.Loading -> Log.d(TAG_BLOC, "Config loaded during loading state, will apply when ready")
    }

    // Resume log capture if debug mode was enabled in a previous session
    if (config.debugModeEnabled && !debugLogManager.isCapturing()) {
        Log.i(TAG_BLOC, "Debug mode was enabled — resuming log capture on startup")
        debugLogManager.startCapture()
    }
}

private fun AppBloc.setShowSettings(show: Boolean) {
    when (val s = _state.value) {
        is AppState.Success -> _state.value = s.copy(showSettings = show)
        is AppState.Error   -> _state.value = s.copy(showSettings = show)
        is AppState.Loading -> Unit
    }
}
