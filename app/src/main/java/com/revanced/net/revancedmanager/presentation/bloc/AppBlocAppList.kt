package com.revanced.net.revancedmanager.presentation.bloc

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.revanced.net.revancedmanager.R
import com.revanced.net.revancedmanager.config.Config
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
import java.time.LocalDate

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

internal fun AppBloc.setSort(sort: AppSortOption) {
    val currentState = _state.value
    if (currentState is AppState.Success) {
        _state.value = currentState.copy(sortOption = sort)
    }
}

/**
 * Mark the apps the user has starred.
 *
 * Favourites are stored per catalog entry, not per package: preferring the anddea build of YouTube
 * says nothing about the other five entries patched from the same package. That is the opposite of
 * install status, which really does belong to the package.
 *
 * Sets saved before ids existed hold package names, so those are migrated in place the first time
 * a list is available to resolve them against — while the current catalog still maps each package
 * to exactly one included entry and the answer is unambiguous.
 */
internal fun AppBloc.applyFavorites(apps: List<RevancedApp>): List<RevancedApp> {
    val favorites = migrateFavoritesToIds(apps)
    return if (favorites.isEmpty()) apps
    else apps.map { it.copy(isFavorite = it.id in favorites) }
}

private fun AppBloc.migrateFavoritesToIds(apps: List<RevancedApp>): Set<String> {
    val stored = preferencesManager.getFavorites()
    if (stored.isEmpty() || apps.isEmpty()) return stored

    val knownIds = apps.mapTo(mutableSetOf()) { it.id }
    val legacy = stored.filterNot { it in knownIds }
    if (legacy.isEmpty()) return stored

    // Anything that is not an id may be a package name from an older install. Entries matching
    // neither are kept rather than dropped — an app missing from this particular response is not
    // proof it is gone for good, and losing a star is not worth the tidier set.
    val byPackage = apps.groupBy { it.packageName }
    val migrated = stored.flatMapTo(mutableSetOf()) { entry ->
        when {
            entry in knownIds -> listOf(entry)
            byPackage.containsKey(entry) -> byPackage.getValue(entry).map { it.id }
            else -> listOf(entry)
        }
    }

    if (migrated != stored) {
        Log.i(TAG_BLOC, "Migrated ${stored.size} favourite(s) from package names to catalog ids")
        preferencesManager.saveFavorites(migrated)
    }
    return migrated
}

internal fun AppBloc.toggleFavorite(appId: String) {
    val currentState = _state.value as? AppState.Success ?: return
    val app = currentState.apps.find { it.id == appId } ?: return

    if (app.isFavorite) {
        val newFavorites = preferencesManager.getFavorites().toMutableSet()
        newFavorites.remove(appId)
        // Also drop the pre-id form, so un-starring a migrated favourite actually sticks.
        newFavorites.remove(app.packageName)
        preferencesManager.saveFavorites(newFavorites)
        _state.value = currentState.copy(
            apps = currentState.apps.map {
                if (it.id == appId) it.copy(isFavorite = false) else it
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
                    favorites.add(appId)
                    preferencesManager.saveFavorites(favorites)
                    val freshState = _state.value as? AppState.Success ?: return@Confirmation
                    _state.value = freshState.copy(
                        apps = freshState.apps.map {
                            if (it.id == appId) it.copy(isFavorite = true) else it
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
                        onAppListLoaded()
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
                                // copy() keeps search/filter/dialog/suggestions intact while
                                // swapping in the fresh list
                                _state.value = currentState.copy(
                                    apps = mergeInFlightState(applyFavorites(result.data)),
                                    config = config
                                )
                                if (updatedApps.size > 1) {
                                    showToast(stringProvider.getString(R.string.apps_updated, updatedApps.size))
                                }
                            }
                            onAppListLoaded()
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
                if (app.id == updatedApp.id) updatedApp.copy(isFavorite = app.isFavorite)
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
                        _state.value = AppState.Success(mergeInFlightState(applyFavorites(result.data)), config = config)
                        onAppListLoaded()
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

/**
 * Pull-to-refresh handler: force-refreshes the app list from the network while
 * keeping the current list visible (instead of switching to the full-screen
 * loading state) and drives the refresh indicator via [AppState.Success.isRefreshing].
 */
internal fun AppBloc.pullToRefreshApps() {
    val currentState = _state.value
    if (currentState !is AppState.Success) {
        // No list on screen yet — fall back to the regular refresh.
        loadApps(forceRefresh = true)
        return
    }

    Log.i(TAG_BLOC, "Pull-to-refresh starting")
    _state.value = currentState.copy(isRefreshing = true)

    viewModelScope.launch {
        useCases.getAppsUseCase(forceRefresh = true)
            .onEach { result ->
                when (result) {
                    is Result.Loading -> Unit // Keep the current list visible while refreshing
                    is Result.Success -> {
                        val config = loadConfigSafely()
                        val latest = _state.value as? AppState.Success
                        _state.value = AppState.Success(
                            apps = mergeInFlightState(applyFavorites(result.data)),
                            searchQuery = latest?.searchQuery ?: "",
                            filterOption = latest?.filterOption ?: AppFilterOption.ALL,
                            sortOption = latest?.sortOption ?: AppSortOption.CATALOG,
                            dialogState = latest?.dialogState,
                            config = config,
                            isRefreshing = false,
                            suggestedApps = latest?.suggestedApps
                        )
                        onAppListLoaded()
                    }
                    is Result.Error -> {
                        (_state.value as? AppState.Success)?.let {
                            _state.value = it.copy(isRefreshing = false)
                        }
                        showError(result.message)
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
    return statusForVersions(installedVersion, latestVersion)
}

internal fun AppBloc.statusForVersions(installedVersion: String, latestVersion: String): AppStatus =
    if (compareVersions(installedVersion, latestVersion) >= 0) AppStatus.UP_TO_DATE
    else AppStatus.UPDATE_AVAILABLE

internal fun AppBloc.compareVersions(version1: String, version2: String): Int =
    com.revanced.net.revancedmanager.core.common.VersionComparator.compare(version1, version2)

/**
 * Preserve in-flight download/install state when replacing the app list with
 * freshly loaded data — otherwise a refresh resets a DOWNLOADING/INSTALLING
 * card back to its network-derived status while the operation is still running.
 */
internal fun AppBloc.mergeInFlightState(newApps: List<RevancedApp>): List<RevancedApp> {
    val currentApps = (_state.value as? AppState.Success)?.apps ?: return newApps
    val inFlight = currentApps
        .filter {
            it.status == AppStatus.DOWNLOADING ||
            it.status == AppStatus.INSTALLING ||
            it.status == AppStatus.UNINSTALLING
        }
        .associateBy { it.packageName }
    if (inFlight.isEmpty()) return newApps
    return newApps.map { app ->
        inFlight[app.packageName]?.let { old ->
            app.copy(status = old.status, downloadProgress = old.downloadProgress)
        } ?: app
    }
}

/** Safely load AppConfig from preferences, falling back to DARK/ENGLISH. */
internal fun AppBloc.loadConfigSafely(): AppConfig = try {
    preferencesManager.getAppConfig()
} catch (e: Exception) {
    Log.w(TAG_BLOC, "Failed to load config, using fallback", e)
    AppConfig(ThemeMode.DARK, Language.ENGLISH)
}

// ============= LAUNCH PROMPTS (update prompt / first-run suggestions) =============

/**
 * Called after every successful list load/refresh. Consumes a pending
 * "Update all" request from the update notification first; otherwise offers
 * the first-run suggestions popup, then the "updates available" prompt.
 */
internal fun AppBloc.onAppListLoaded() {
    if (pendingUpdateAllRequest) {
        pendingUpdateAllRequest = false
        // The user already chose to update everything — don't ask again.
        updatePromptShownThisSession = true
        updateAllApps()
        return
    }
    maybeShowSuggestions()
    maybeShowUpdatePrompt()
}

/**
 * First app run: suggest a hand-picked set of apps ([Config.SUGGESTED_PACKAGES])
 * that exist in the loaded list and are not installed yet.
 */
private fun AppBloc.maybeShowSuggestions() {
    if (preferencesManager.isSuggestionsShown()) return
    val state = _state.value as? AppState.Success ?: return
    if (state.suggestedApps != null || state.apps.isEmpty()) return

    // SUGGESTED_PACKAGES names packages, and a package can have several catalog entries — the
    // catalog has four MicroG builds. Offer one per package, the first in catalog order, or the
    // popup would list four MicroGs and ticking one would install all four over each other.
    val suggestions = state.apps
        .filter { it.packageName in Config.SUGGESTED_PACKAGES && it.status == AppStatus.NOT_INSTALLED }
        .distinctBy { it.packageName }
        .sortedBy { Config.SUGGESTED_PACKAGES.indexOf(it.packageName) }

    if (suggestions.isEmpty()) {
        // Nothing to suggest (all installed or missing from the API) — never ask again
        preferencesManager.setSuggestionsShown()
        return
    }

    Log.i(TAG_BLOC, "Showing first-run suggestions: ${suggestions.size} app(s)")
    _state.value = state.copy(suggestedApps = suggestions)
}

/**
 * Show the "N updates available" prompt at most once per session, unless it
 * was snoozed for today, disabled in settings, or another popup is on screen.
 */
private fun AppBloc.maybeShowUpdatePrompt() {
    if (updatePromptShownThisSession) return
    val state = _state.value as? AppState.Success ?: return
    if (!state.config.showUpdatePromptEnabled) return
    if (state.dialogState != null || state.suggestedApps != null) return

    val updateCount = state.apps.count { it.status == AppStatus.UPDATE_AVAILABLE }
    if (updateCount == 0) return

    val today = LocalDate.now().toString()
    if (preferencesManager.getUpdatePromptSnoozedDate() == today) return

    Log.i(TAG_BLOC, "Showing update prompt: $updateCount update(s) available")
    updatePromptShownThisSession = true
    _state.value = state.copy(
        dialogState = DialogState.UpdatePrompt(
            updateCount = updateCount,
            onUpdateAll = {
                dismissDialog()
                handleEvent(AppEvent.UpdateAllApps)
            },
            onSkipToday = {
                preferencesManager.setUpdatePromptSnoozedDate(today)
                dismissDialog()
            },
            onDismiss = { dismissDialog() }
        )
    )
}

/** Install the suggested apps the user ticked, then close the popup for good. */
internal fun AppBloc.installSuggestedApps(appIds: List<String>) {
    preferencesManager.setSuggestionsShown()
    val state = _state.value as? AppState.Success ?: return
    val selected = state.suggestedApps?.filter { it.id in appIds }.orEmpty()
    _state.value = state.copy(suggestedApps = null)

    Log.i(TAG_BLOC, "Installing ${selected.size} suggested app(s)")
    selected.forEach { downloadApp(it.packageName, it.downloadUrl) }
}

/** Close the first-run suggestions popup without installing anything. */
internal fun AppBloc.dismissSuggestions() {
    preferencesManager.setSuggestionsShown()
    val state = _state.value as? AppState.Success ?: return
    _state.value = state.copy(suggestedApps = null)
}
