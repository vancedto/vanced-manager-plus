package com.revanced.net.revancedmanager.presentation.bloc

import com.revanced.net.revancedmanager.domain.model.AppConfig
import com.revanced.net.revancedmanager.domain.model.AppStatus

/**
 * Represents all possible events that can occur in the app
 */
sealed class AppEvent {
    data object LoadApps : AppEvent()
    data object RefreshApps : AppEvent()
    data object PullToRefreshApps : AppEvent()
    
    // New improved UX events
    data object LoadAppsFromCacheFirst : AppEvent()
    data object BackgroundRefreshApps : AppEvent()
    data class UpdateSingleApp(val app: com.revanced.net.revancedmanager.domain.model.RevancedApp) : AppEvent()
    
    /**
     * [appId] is the catalog entry whose button was pressed. Required rather than defaulted: two
     * entries can share [packageName], and only the id says which row is asking — see
     * `InFlightAttribution`.
     */
    data class DownloadApp(val appId: String, val packageName: String, val downloadUrl: String) : AppEvent()
    /**
     * Carry on with a batch of downloads once the MicroG question has been answered — MicroG's
     * own entry leads [appIds] when the user chose to install it too.
     */
    data class StartDownloads(val appIds: List<String>) : AppEvent()
    /** Answer to the "allow app installs" dialog shown before the first download. */
    data class InstallPermissionAnswer(val openSettings: Boolean) : AppEvent()
    data class InstallApp(val packageName: String, val apkFilePath: String) : AppEvent()
    /** [confirmed] once the user has seen what uninstalling it would break (MicroG only). */
    data class UninstallApp(val packageName: String, val confirmed: Boolean = false) : AppEvent()
    data class ShowReinstallConfirmation(val appId: String, val packageName: String) : AppEvent()
    data class ReinstallApp(val appId: String, val packageName: String) : AppEvent()
    data class OpenApp(val packageName: String) : AppEvent()
    data class UpdateAppProgress(val packageName: String, val progress: Float) : AppEvent()
    data class UpdateAppStatus(val packageName: String, val status: AppStatus) : AppEvent()
    data class ShowError(val message: String) : AppEvent()
    
    // New events for improved flow
    data class RetryInstallation(val packageName: String, val apkFilePath: String, val shouldUninstallFirst: Boolean = false) : AppEvent()
    data class CancelDownload(val packageName: String) : AppEvent()
    /** Escape hatch for an install that is stuck waiting on a confirmation that never arrives. */
    data class CancelInstallation(val packageName: String) : AppEvent()
    data class ConfirmUninstallBeforeReinstall(val packageName: String, val apkFilePath: String) : AppEvent()
    data class ShowConfirmationDialog(val title: String, val message: String, val onConfirm: AppEvent, val onCancel: AppEvent? = null) : AppEvent()
    data object DismissDialog : AppEvent()
    data class DismissDialogAndUpdateStatus(val packageName: String, val status: AppStatus) : AppEvent()

    // Configuration events
    // Navigating to and from settings is the navigation graph's job, not an event.
    data class SaveSettings(val config: AppConfig) : AppEvent()
    data object ResetSettings : AppEvent()
    data object LoadConfiguration : AppEvent()
    
    // Search events
    data class SearchApps(val query: String) : AppEvent()
    data object ClearSearch : AppEvent()

    // Filter events
    data class SetFilter(val filter: AppFilterOption) : AppEvent()

    // Sort events
    data class SetSort(val sort: AppSortOption) : AppEvent()

    // Favorites events
    /** Keyed by catalog entry: several entries can share a package, and a star is about one of them. */
    data class ToggleFavorite(val appId: String) : AppEvent()

    // Update events (update prompt dialog / update notification)
    /** Every app with an update the user has not muted — the notification's "Update all" action. */
    data object UpdateAllApps : AppEvent()
    /** Only the entries ticked in the update prompt. */
    data class UpdateSelectedApps(val appIds: List<String>) : AppEvent()
    /** Include or exclude one app from the update prompt and the update notification. */
    data class ToggleUpdatePrompt(val appId: String) : AppEvent()

    // First-run suggestions events
    data class InstallSuggestedApps(val appIds: List<String>) : AppEvent()
    data object DismissSuggestions : AppEvent()

    /** Answer to the first-run "where should apps come from?" dialog. */
    data class ChooseAppSource(val showCommunityApps: Boolean) : AppEvent()
}