package com.revanced.net.revancedmanager.presentation.bloc

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.revanced.net.revancedmanager.R
import com.revanced.net.revancedmanager.core.common.Result
import com.revanced.net.revancedmanager.domain.model.AppConfig
import com.revanced.net.revancedmanager.domain.model.AppStatus
import com.revanced.net.revancedmanager.domain.model.Language
import com.revanced.net.revancedmanager.domain.model.RevancedApp
import com.revanced.net.revancedmanager.domain.model.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

// ============= APP LIST MANAGEMENT =============

internal fun AppBloc.searchApps(query: String) {
    val currentState = _state.value
    if (currentState is AppState.Success) {
        _state.value = currentState.copy(searchQuery = query)
    }
}

internal fun AppBloc.clearSearch() {
    val currentState = _state.value
    if (currentState is AppState.Success) {
        _state.value = currentState.copy(searchQuery = "")
    }
}

internal fun AppBloc.setFilter(filter: AppFilterOption) {
    val currentState = _state.value
    if (currentState is AppState.Success) {
        _state.value = currentState.copy(filterOption = filter)
    }
}

internal fun AppBloc.applyFavorites(apps: List<RevancedApp>): List<RevancedApp> {
    val favorites = preferencesManager.getFavorites()
    return if (favorites.isEmpty()) apps
    else apps.map { it.copy(isFavorite = it.packageName in favorites) }
}

internal fun AppBloc.toggleFavorite(packageName: String) {
    val currentState = _state.value as? AppState.Success ?: return
    val app = currentState.apps.find { it.packageName == packageName } ?: return

    if (app.isFavorite) {
        val newFavorites = preferencesManager.getFavorites().toMutableSet()
        newFavorites.remove(packageName)
        preferencesManager.saveFavorites(newFavorites)
        _state.value = currentState.copy(
            apps = currentState.apps.map {
                if (it.packageName == packageName) it.copy(isFavorite = false) else it
            }
        )
        showToast(stringProvider.getString(R.string.favorite_removed))
    } else {
        _state.value = currentState.copy(
            dialogState = DialogState.Confirmation(
                title = stringProvider.getString(R.string.favorite_add_title),
                message = stringProvider.getString(R.string.favorite_add_message, app.title),
                onConfirmAction = {
                    val favorites = preferencesManager.getFavorites().toMutableSet()
                    favorites.add(packageName)
                    preferencesManager.saveFavorites(favorites)
                    val freshState = _state.value as? AppState.Success ?: return@Confirmation
                    _state.value = freshState.copy(
                        apps = freshState.apps.map {
                            if (it.packageName == packageName) it.copy(isFavorite = true) else it
                        },
                        dialogState = null
                    )
                    showToast(stringProvider.getString(R.string.favorite_added))
                },
                onCancelAction = { handleEvent(AppEvent.DismissDialog) }
            )
        )
    }
}

internal fun AppBloc.loadAppsFromCacheFirst() {
    Log.i(TAG_BLOC, "Loading apps from cache first")
    viewModelScope.launch {
        useCases.appRepository.getAppsFromCacheImmediately()
            .onEach { result ->
                when (result) {
                    is Result.Loading -> {
                        _state.value = AppState.Loading
                        loadApps(forceRefresh = false)
                    }
                    is Result.Success -> {
                        val config = loadConfigSafely()
                        _state.value = AppState.Success(applyFavorites(result.data), config = config)
                        viewModelScope.launch {
                            delay(500)
                            handleEvent(AppEvent.BackgroundRefreshApps)
                        }
                    }
                    is Result.Error -> loadApps(forceRefresh = false)
                }
            }
            .launchIn(this)
    }
}

internal fun AppBloc.backgroundRefreshApps() {
    Log.i(TAG_BLOC, "Background refresh starting")
    viewModelScope.launch {
        useCases.appRepository.backgroundRefreshApps()
            .onEach { result ->
                when (result) {
                    is Result.Loading -> Unit
                    is Result.Success -> {
                        val currentState = _state.value
                        if (currentState is AppState.Success) {
                            val updatedApps = useCases.appRepository.getUpdatedApps(currentState.apps, result.data)
                            if (updatedApps.isNotEmpty()) {
                                val config = loadConfigSafely()
                                _state.value = AppState.Success(applyFavorites(result.data), config = config)
                                if (updatedApps.size > 1) {
                                    showToast(stringProvider.getString(R.string.apps_updated, updatedApps.size))
                                }
                            }
                        }
                    }
                    is Result.Error -> Log.w(TAG_BLOC, "Background refresh failed, keeping current state", result.exception)
                }
            }
            .launchIn(this)
    }
}

internal fun AppBloc.updateSingleApp(updatedApp: RevancedApp) {
    val currentState = _state.value
    if (currentState is AppState.Success) {
        _state.value = currentState.copy(
            apps = currentState.apps.map { app ->
                if (app.packageName == updatedApp.packageName) updatedApp.copy(isFavorite = app.isFavorite)
                else app
            }
        )
    }
}

internal fun AppBloc.loadApps(forceRefresh: Boolean) {
    Log.i(TAG_BLOC, "Loading apps, forceRefresh: $forceRefresh")
    viewModelScope.launch {
        useCases.getAppsUseCase(forceRefresh)
            .onEach { result ->
                when (result) {
                    is Result.Loading -> _state.value = AppState.Loading
                    is Result.Success -> {
                        val config = loadConfigSafely()
                        _state.value = AppState.Success(applyFavorites(result.data), config = config)
                    }
                    is Result.Error -> {
                        val config = loadConfigSafely()
                        _state.value = AppState.Error(message = result.message, config = config)
                    }
                }
            }
            .launchIn(this)
    }
}

// ============= STATUS / PROGRESS UPDATES =============

internal fun AppBloc.updateAppProgress(packageName: String, progress: Float) {
    val currentState = _state.value
    if (currentState is AppState.Success) {
        _state.value = currentState.copy(
            apps = currentState.apps.map { app ->
                if (app.packageName == packageName) app.copy(downloadProgress = progress) else app
            }
        )
    }
}

internal fun AppBloc.updateAppStatus(packageName: String, status: AppStatus) {
    val currentState = _state.value
    if (currentState is AppState.Success) {
        _state.value = currentState.copy(
            apps = currentState.apps.map { app ->
                if (app.packageName == packageName) app.copy(status = status) else app
            }
        )
    }
}

// ============= HELPERS =============

internal fun AppBloc.resolveActualStatus(packageName: String): AppStatus {
    if (!appManager.isAppInstalled(packageName)) return AppStatus.NOT_INSTALLED
    val installedVersion = appManager.getInstalledVersion(packageName) ?: return AppStatus.NOT_INSTALLED
    val latestVersion = (_state.value as? AppState.Success)
        ?.apps?.find { it.packageName == packageName }?.latestVersion ?: return AppStatus.UP_TO_DATE
    return when (compareVersions(installedVersion, latestVersion)) {
        0, 1 -> AppStatus.UP_TO_DATE
        else -> AppStatus.UPDATE_AVAILABLE
    }
}

internal fun AppBloc.compareVersions(version1: String, version2: String): Int {
    if (version1.isEmpty() || version2.isEmpty()) return 0
    return try {
        val parts1 = version1.split(".").map { it.takeWhile { c -> c.isDigit() }.ifEmpty { "0" } }
        val parts2 = version2.split(".").map { it.takeWhile { c -> c.isDigit() }.ifEmpty { "0" } }
        val length = minOf(parts1.size, parts2.size)
        for (i in 0 until length) {
            val n1 = parts1[i].toLongOrNull() ?: 0L
            val n2 = parts2[i].toLongOrNull() ?: 0L
            when { n1 > n2 -> return 1; n1 < n2 -> return -1 }
        }
        parts1.size.compareTo(parts2.size)
    } catch (e: Exception) { 0 }
}

/** Safely load AppConfig from preferences, falling back to DARK/ENGLISH. */
internal fun AppBloc.loadConfigSafely(): AppConfig = try {
    preferencesManager.getAppConfig()
} catch (e: Exception) {
    Log.w(TAG_BLOC, "Failed to load config, using fallback", e)
    AppConfig(ThemeMode.DARK, Language.ENGLISH)
}
