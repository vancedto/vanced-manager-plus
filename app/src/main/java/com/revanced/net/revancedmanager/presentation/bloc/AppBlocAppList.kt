package com.revanced.net.revancedmanager.presentation.bloc

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.revanced.net.revancedmanager.R
import com.revanced.net.revancedmanager.config.Config
import com.revanced.net.revancedmanager.core.common.PackageOwnership
import com.revanced.net.revancedmanager.core.common.Result
import com.revanced.net.revancedmanager.domain.model.AppConfig
import com.revanced.net.revancedmanager.domain.model.AppStatus
import com.revanced.net.revancedmanager.domain.model.Language
import com.revanced.net.revancedmanager.domain.model.RevancedApp
import com.revanced.net.revancedmanager.domain.model.ThemeMode
import com.revanced.net.revancedmanager.domain.model.visibleFor
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
 * Restore the per-app preferences that live on the device rather than in the catalog, so every
 * path that loads a list applies all of them instead of remembering to call each one.
 */
internal fun AppBloc.applyLocalFlags(apps: List<RevancedApp>): List<RevancedApp> =
    applyUpdatePromptMutes(applyFavorites(apps))

/**
 * Mark the apps the user has excluded from the update prompt in the detail screen.
 *
 * Stored per catalog entry for the same reason favourites are, and stored as the muted set, so
 * the common case — nothing muted — costs one empty read and leaves the list untouched.
 */
private fun AppBloc.applyUpdatePromptMutes(apps: List<RevancedApp>): List<RevancedApp> {
    val muted = preferencesManager.getUpdatePromptMutedApps()
    return if (muted.isEmpty()) apps
    else apps.map { it.copy(updatePromptEnabled = it.id !in muted) }
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

/**
 * Flip whether this app may be offered by the update prompt and the update notification.
 *
 * No confirmation, unlike starring: it is a switch on the app's own page and flipping it back is
 * the same gesture again.
 */
internal fun AppBloc.toggleUpdatePrompt(appId: String) {
    val currentState = _state.value as? AppState.Success ?: return
    val app = currentState.apps.find { it.id == appId } ?: return

    val enabled = !app.updatePromptEnabled
    val muted = preferencesManager.getUpdatePromptMutedApps().toMutableSet()
    if (enabled) muted.remove(appId) else muted.add(appId)
    preferencesManager.saveUpdatePromptMutedApps(muted)

    Log.i(TAG_BLOC, "Update suggestions for $appId: ${if (enabled) "on" else "off"}")
    _state.value = currentState.copy(
        apps = currentState.apps.map {
            if (it.id == appId) it.copy(updatePromptEnabled = enabled) else it
        }
    )
    showToast(
        stringProvider.getString(
            if (enabled) R.string.update_prompt_unmuted else R.string.update_prompt_muted,
            app.title
        )
    )
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
                        _state.value = AppState.Success(
                            mergeInFlightState(applyLocalFlags(result.data)),
                            config = config
                        )
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
                                    apps = mergeInFlightState(applyLocalFlags(result.data)),
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
                        _state.value = AppState.Success(mergeInFlightState(applyLocalFlags(result.data)), config = config)
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
                            apps = mergeInFlightState(applyLocalFlags(result.data)),
                            searchQuery = latest?.searchQuery ?: "",
                            filterOption = latest?.filterOption ?: AppFilterOption.ALL,
                            sortOption = latest?.sortOption ?: AppSortOption.CATALOG,
                            dialogState = latest?.dialogState,
                            config = config,
                            isRefreshing = false,
                            suggestedApps = latest?.suggestedApps,
                            askAppSource = latest?.askAppSource ?: false
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

/**
 * Set the status of every entry of [packageName].
 *
 * An in-flight status (downloading, installing, uninstalling) is about the package — one download,
 * one installer session — so every entry sharing the package shows it. A settled status is about
 * a build that is or is not installed, and that build belongs to exactly one entry: the owner under
 * [PackageOwnership] gets [status], and any other entry for the package reads as not installed.
 * With one entry per package, which is every package but MicroG, the two cases are the same.
 */
internal fun AppBloc.updateAppStatus(packageName: String, status: AppStatus) {
    val currentState = _state.value as? AppState.Success ?: return
    val ownerId = if (status in SETTLED_STATUSES) ownerOf(currentState.apps, packageName)?.id else null
    _state.value = currentState.copy(
        apps = currentState.apps.map { app ->
            when {
                app.packageName != packageName -> app
                ownerId == null || app.id == ownerId -> app.copy(status = status)
                else -> app.copy(status = AppStatus.NOT_INSTALLED, currentVersion = null)
            }
        }
    )
}

/** Statuses that describe what is installed rather than what is happening. */
private val SETTLED_STATUSES = setOf(
    AppStatus.NOT_INSTALLED,
    AppStatus.UP_TO_DATE,
    AppStatus.UPDATE_AVAILABLE,
    AppStatus.UNKNOWN
)

// ============= HELPERS =============

/**
 * The entry among [apps] that owns whatever is installed as [packageName], or null when nothing
 * is installed or nothing in the list carries the package.
 */
internal fun AppBloc.ownerOf(apps: List<RevancedApp>, packageName: String): RevancedApp? {
    val installedVersion = appManager.getInstalledVersion(packageName) ?: return null
    return PackageOwnership.ownerOf(apps.filter { it.packageName == packageName }, installedVersion)
}

/**
 * Re-derive version and status for every entry of [packageName] from what is actually installed,
 * clearing any download progress. The bloc-side twin of the repository's withInstallStatus, for
 * the moments a flow settles — install finished, package event, status refresh.
 */
internal fun AppBloc.settleEntries(apps: List<RevancedApp>, packageName: String): List<RevancedApp> {
    val entries = apps.filter { it.packageName == packageName }
    if (entries.isEmpty()) return apps
    val installedVersion = appManager.getInstalledVersion(packageName)
    val settled = PackageOwnership.withInstallStatus(entries) { installedVersion }.associateBy { it.id }
    return apps.map { app -> settled[app.id]?.copy(downloadProgress = 0f) ?: app }
}

internal fun AppBloc.resolveActualStatus(packageName: String): AppStatus {
    if (!appManager.isAppInstalled(packageName)) return AppStatus.NOT_INSTALLED
    val installedVersion = appManager.getInstalledVersion(packageName) ?: return AppStatus.NOT_INSTALLED
    // The owner's latest version, not the first entry's: with two MicroG entries the first one
    // in catalog order may be the build that is *not* installed.
    val entries = (_state.value as? AppState.Success)?.apps.orEmpty()
    val latestVersion = PackageOwnership.ownerOf(entries.filter { it.packageName == packageName }, installedVersion)
        ?.latestVersion ?: return AppStatus.UP_TO_DATE
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
 *
 * [AppBloc.pendingInstalls] is consulted as well as the current list, because an install can start
 * before there is any list to record it on: a download left finished by a previous process is
 * replayed as soon as the bloc subscribes, which is well before the catalog has loaded.
 */
internal fun AppBloc.mergeInFlightState(newApps: List<RevancedApp>): List<RevancedApp> {
    val inFlight = (_state.value as? AppState.Success)?.apps
        .orEmpty()
        .filter {
            it.status == AppStatus.DOWNLOADING ||
            it.status == AppStatus.INSTALLING ||
            it.status == AppStatus.UNINSTALLING
        }
        .associateBy { it.packageName }
    if (inFlight.isEmpty() && pendingInstalls.isEmpty()) return newApps
    return newApps.map { app ->
        val old = inFlight[app.packageName]
        when {
            old != null -> app.copy(status = old.status, downloadProgress = old.downloadProgress)
            app.packageName in pendingInstalls -> app.copy(status = AppStatus.INSTALLING)
            else -> app
        }
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
 * "Update all" request from the update notification first; otherwise asks
 * where apps should come from (once), then offers the first-run suggestions
 * popup, then the "updates available" prompt.
 */
internal fun AppBloc.onAppListLoaded() {
    if (pendingUpdateAllRequest) {
        pendingUpdateAllRequest = false
        // The user already chose to update everything — don't ask again.
        updatePromptShownThisSession = true
        updateAllApps()
        return
    }
    maybeAskAppSource()
    maybeShowSuggestions()
    maybeShowUpdatePrompt()
}

/**
 * First run (or first launch after upgrading to a version that has the choice): ask whether to
 * list community-patched apps or only ReVanced and Morphe. Goes before the other launch popups
 * because it decides what those popups may list; [chooseAppSource] resumes them.
 */
private fun AppBloc.maybeAskAppSource() {
    if (preferencesManager.isAppSourceChosen()) return
    val state = _state.value as? AppState.Success ?: return
    if (state.askAppSource || state.apps.isEmpty()) return

    Log.i(TAG_BLOC, "Asking for app source on first run")
    _state.value = state.copy(askAppSource = true)
}

/**
 * Record the answer to the app source dialog, then carry on with the launch popups it was
 * holding back. The choice is saved as part of [AppConfig], so the switch in Settings and this
 * dialog write the same preference.
 */
internal fun AppBloc.chooseAppSource(showCommunityApps: Boolean) {
    preferencesManager.setAppSourceChosen()
    val state = _state.value as? AppState.Success ?: return

    val config = state.config.copy(showCommunityApps = showCommunityApps)
    preferencesManager.saveAppConfig(config)
    Log.i(TAG_BLOC, "App source chosen: community apps ${if (showCommunityApps) "shown" else "hidden"}")
    _state.value = state.copy(config = config, askAppSource = false)

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
    if (state.askAppSource || state.suggestedApps != null || state.apps.isEmpty()) return

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
    if (state.dialogState != null || state.suggestedApps != null || state.askAppSource) return

    // Apps muted in the detail screen are left out entirely rather than listed unticked: the point
    // of the switch is not to be asked about them. Same for apps hidden by the source choice.
    val updatable = state.apps.visibleFor(state.config).filter {
        it.status == AppStatus.UPDATE_AVAILABLE && it.updatePromptEnabled
    }
    if (updatable.isEmpty()) return

    val today = LocalDate.now().toString()
    if (preferencesManager.getUpdatePromptSnoozedDate() == today) return

    Log.i(TAG_BLOC, "Showing update prompt: ${updatable.size} update(s) available")
    updatePromptShownThisSession = true
    _state.value = state.copy(
        dialogState = DialogState.UpdatePrompt(
            apps = updatable,
            onUpdateSelected = { appIds ->
                dismissDialog()
                handleEvent(AppEvent.UpdateSelectedApps(appIds))
            },
            onSkipToday = {
                preferencesManager.setUpdatePromptSnoozedDate(today)
                dismissDialog()
            },
            onDismiss = { dismissDialog() },
            onTurnOff = { turnOffUpdatePrompt() }
        )
    )
}

/**
 * The "Don't show again" button inside the prompt itself: flip the "Update popup on launch"
 * setting off, so the user who never wants to be asked does not have to find it in Settings. Only
 * the popup goes — the launch refresh, the status on every card and the daily notification are untouched.
 */
private fun AppBloc.turnOffUpdatePrompt() {
    val config = loadConfigSafely().copy(showUpdatePromptEnabled = false)
    preferencesManager.saveAppConfig(config)
    when (val s = _state.value) {
        is AppState.Success -> _state.value = s.copy(config = config, dialogState = null)
        is AppState.Error -> _state.value = s.copy(config = config)
        is AppState.Loading -> Unit
    }
    Log.i(TAG_BLOC, "Update prompt turned off from the prompt")
    showToast(stringProvider.getString(R.string.update_prompt_turned_off))
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
