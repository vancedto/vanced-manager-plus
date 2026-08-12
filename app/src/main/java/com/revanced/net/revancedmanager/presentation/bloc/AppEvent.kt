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
    
    data class DownloadApp(val packageName: String, val downloadUrl: String) : AppEvent()
    data class InstallApp(val packageName: String, val apkFilePath: String) : AppEvent()
    data class UninstallApp(val packageName: String) : AppEvent()
    data class ShowReinstallConfirmation(val packageName: String) : AppEvent()
    data class ReinstallApp(val packageName: String) : AppEvent()
    data class OpenApp(val packageName: String) : AppEvent()
    data class UpdateAppProgress(val packageName: String, val progress: Float) : AppEvent()
    data class UpdateAppStatus(val packageName: String, val status: AppStatus) : AppEvent()
    data class ShowError(val message: String) : AppEvent()
    
    // New events for improved flow
    data class RetryInstallation(val packageName: String, val apkFilePath: String, val shouldUninstallFirst: Boolean = false) : AppEvent()
    data class CancelDownload(val packageName: String) : AppEvent()
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

    // Update-all events (update prompt dialog / update notification)
    data object UpdateAllApps : AppEvent()

    // First-run suggestions events
    data class InstallSuggestedApps(val appIds: List<String>) : AppEvent()
    data object DismissSuggestions : AppEvent()
}