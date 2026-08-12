package com.revanced.net.revancedmanager.presentation.bloc

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revanced.net.revancedmanager.R
import com.revanced.net.revancedmanager.core.common.StringProvider
import com.revanced.net.revancedmanager.data.local.preferences.PreferencesManager
import com.revanced.net.revancedmanager.data.manager.AppDownloadManager
import com.revanced.net.revancedmanager.data.manager.AppManager
import com.revanced.net.revancedmanager.data.manager.DebugLogManager
import com.revanced.net.revancedmanager.data.manager.PackageChangedReceiver
import com.revanced.net.revancedmanager.data.manager.PackageEvent
import com.revanced.net.revancedmanager.data.manager.RevancedPackageInstaller
import com.revanced.net.revancedmanager.data.manager.UninstallationResult
import com.revanced.net.revancedmanager.domain.model.AppStatus
import com.revanced.net.revancedmanager.domain.usecase.AppManagementUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

/** Tag used by all AppBloc extension files. */
internal const val TAG_BLOC = "AppBloc"

/**
 * Core ViewModel — owns state, routes events, and manages lifecycle.
 * Domain logic lives in the companion extension files:
 *   AppBlocDownload.kt, AppBlocInstall.kt, AppBlocAppList.kt, AppBlocConfig.kt
 *
 * Downloads run as WorkManager work ([AppDownloadManager]); their progress and
 * results arrive through a single WorkInfo flow collected in [observeDownloads].
 * Installations run sequentially through [installRequests], processed by
 * [startInstallationProcessor].
 */
@HiltViewModel
class AppBloc @Inject constructor(
    @ApplicationContext internal val context: Context,
    internal val useCases: AppManagementUseCases,
    internal val downloadManager: AppDownloadManager,
    internal val appManager: AppManager,
    internal val preferencesManager: PreferencesManager,
    internal val stringProvider: StringProvider,
    internal val packageInstaller: RevancedPackageInstaller,
    internal val packageChangedReceiver: PackageChangedReceiver,
    val debugLogManager: DebugLogManager
) : ViewModel(), DefaultLifecycleObserver {

    // ---- Public state ----
    internal val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state.asStateFlow()

    internal val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // ---- Download tracking ----
    /** Work IDs whose terminal state (success/failure/cancel) was already processed. */
    internal val handledDownloads = mutableSetOf<UUID>()

    /**
     * Packages whose download the user asked for during this session.
     *
     * Tells a deliberate download apart from a WorkManager record replayed after a restart, which
     * is the only thing the "already up to date" guard in handleDownloadCompleted is meant to
     * catch. Without it, choosing a different architecture for an app that is already up to date
     * downloads the file and then silently discards it.
     */
    internal val userRequestedDownloads = mutableSetOf<String>()

    // ---- Install queue ----
    internal val installRequests = Channel<PendingInstallation>(Channel.UNLIMITED)
    /** Packages queued for install or currently installing. */
    internal val pendingInstalls = mutableSetOf<String>()
    internal val installationRetries = mutableMapOf<String, Int>()

    // ---- Lifecycle ----
    internal var wasAppBackgrounded = false

    // ---- Launch prompts ----
    /** The "updates available" prompt is asked at most once per app session. */
    internal var updatePromptShownThisSession = false
    /** Set when the update notification's "Update all" action opened the app. */
    internal var pendingUpdateAllRequest = false

    // ---- Uninstall / reinstall tracking ----
    internal val pendingReinstalls = mutableMapOf<String, String>()         // packageName -> apkPath (retry flow)
    internal val pendingReinstallDownloads = mutableMapOf<String, String>() // packageName -> downloadUrl (reinstall flow)
    internal val pendingUninstallChecks = mutableSetOf<String>()

    data class PendingInstallation(
        val packageName: String,
        val filePath: String,
        val appName: String
    )

    init {
        Log.i(TAG_BLOC, "AppBloc initialized")
        handleEvent(AppEvent.LoadConfiguration)
        handleEvent(AppEvent.LoadAppsFromCacheFirst)
        startInstallationProcessor()
        observeDownloads()
        setupUninstallListener()
        packageChangedReceiver.register()
        setupPackageChangedListener()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    // ---- Lifecycle ----

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.i(TAG_BLOC, "APP MOVED TO FOREGROUND — was backgrounded: $wasAppBackgrounded")
        if (wasAppBackgrounded) {
            checkPendingUninstallsOnForeground()
        }
        wasAppBackgrounded = false
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Log.i(TAG_BLOC, "APP MOVED TO BACKGROUND")
        wasAppBackgrounded = true
    }

    override fun onCleared() {
        super.onCleared()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        packageChangedReceiver.unregister()
    }

    // ---- Listener setup ----

    private fun setupPackageChangedListener() {
        packageChangedReceiver.packageEvents
            .onEach { event ->
                when (event) {
                    is PackageEvent.Installed, is PackageEvent.Updated -> {
                        val packageName = when (event) {
                            is PackageEvent.Installed -> event.packageName
                            is PackageEvent.Updated -> event.packageName
                            else -> return@onEach
                        }
                        Log.i(TAG_BLOC, "System event: Package installed/updated: $packageName")
                        pendingUninstallChecks.remove(packageName)
                        val installedVersion = appManager.getInstalledVersion(packageName)
                        if (installedVersion != null) handleInstallationSuccess(packageName, installedVersion)
                    }
                    is PackageEvent.Uninstalled -> {
                        Log.i(TAG_BLOC, "System event: Package uninstalled: ${event.packageName}")
                        pendingUninstallChecks.remove(event.packageName)
                        handlePendingReinstall(event.packageName)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun setupUninstallListener() {
        packageInstaller.uninstallationResults
            .onEach { result ->
                when (result) {
                    is UninstallationResult.Cancelled -> {
                        Log.i(TAG_BLOC, "Uninstall cancelled by user: ${result.packageName}")
                        clearPendingReinstall(result.packageName)
                        viewModelScope.launch { updateSingleAppStatus(result.packageName) }
                        showToast(stringProvider.getString(R.string.uninstallation_cancelled))
                    }
                    is UninstallationResult.Failed -> {
                        Log.w(TAG_BLOC, "Uninstall failed: ${result.packageName}, code=${result.statusCode}")
                        clearPendingReinstall(result.packageName)
                        viewModelScope.launch { updateSingleAppStatus(result.packageName) }
                        showError(stringProvider.getString(R.string.uninstallation_failed, result.message))
                    }
                    is UninstallationResult.Success -> {
                        // Handled by PackageChangedReceiver (PackageEvent.Uninstalled)
                        Log.i(TAG_BLOC, "Uninstall success confirmed via PackageInstaller: ${result.packageName}")
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * After an uninstall completes, continue whichever flow requested it:
     * retry-install from an existing APK, reinstall via a fresh download, or
     * plain uninstall.
     */
    internal fun handlePendingReinstall(packageName: String) {
        val pendingApkPath = pendingReinstalls.remove(packageName)
        val pendingDownloadUrl = pendingReinstallDownloads.remove(packageName)
        when {
            pendingApkPath != null -> installApp(packageName, pendingApkPath)
            pendingDownloadUrl != null -> {
                Log.i(TAG_BLOC, "Starting reinstall download for: $packageName")
                downloadApp(packageName, pendingDownloadUrl)
            }
            else -> {
                updateAppStatus(packageName, AppStatus.NOT_INSTALLED)
                showToast(stringProvider.getString(R.string.uninstallation_completed))
            }
        }
    }

    internal fun clearPendingReinstall(packageName: String) {
        pendingReinstalls.remove(packageName)
        pendingReinstallDownloads.remove(packageName)
        pendingUninstallChecks.remove(packageName)
    }

    // ---- Lifecycle helpers (background/foreground) ----

    private fun checkPendingUninstallsOnForeground() {
        viewModelScope.launch {
            pendingUninstallChecks.toList().forEach { packageName ->
                if (!appManager.isAppInstalled(packageName)) {
                    Log.i(TAG_BLOC, "Background uninstall detected: $packageName")
                    pendingUninstallChecks.remove(packageName)
                    handlePendingReinstall(packageName)
                }
            }
        }
    }

    // ---- Event router ----

    fun handleEvent(event: AppEvent) {
        when (event) {
            is AppEvent.LoadApps -> loadApps(forceRefresh = false)
            is AppEvent.RefreshApps -> loadApps(forceRefresh = true)
            is AppEvent.PullToRefreshApps -> pullToRefreshApps()
            is AppEvent.LoadAppsFromCacheFirst -> loadAppsFromCacheFirst()
            is AppEvent.BackgroundRefreshApps -> backgroundRefreshApps()
            is AppEvent.UpdateSingleApp -> updateSingleApp(event.app)
            is AppEvent.DownloadApp -> downloadApp(event.packageName, event.downloadUrl)
            is AppEvent.CancelDownload -> cancelDownload(event.packageName)
            is AppEvent.InstallApp -> installApp(event.packageName, event.apkFilePath)
            is AppEvent.RetryInstallation -> retryInstallation(event.packageName, event.apkFilePath, event.shouldUninstallFirst)
            is AppEvent.ConfirmUninstallBeforeReinstall -> confirmUninstallBeforeReinstall(event.packageName, event.apkFilePath)
            is AppEvent.UninstallApp -> uninstallApp(event.packageName)
            is AppEvent.ShowReinstallConfirmation -> showReinstallConfirmation(event.packageName)
            is AppEvent.ReinstallApp -> reinstallApp(event.packageName)
            is AppEvent.OpenApp -> openApp(event.packageName)
            is AppEvent.UpdateAppProgress -> updateAppProgress(event.packageName, event.progress)
            is AppEvent.UpdateAppStatus -> updateAppStatus(event.packageName, event.status)
            is AppEvent.ShowError -> showError(event.message)
            is AppEvent.ShowConfirmationDialog -> showConfirmationDialog(event.title, event.message, event.onConfirm, event.onCancel)
            is AppEvent.DismissDialog -> dismissDialog()
            is AppEvent.DismissDialogAndUpdateStatus -> { dismissDialog(); updateAppStatus(event.packageName, event.status) }
            is AppEvent.SaveSettings -> saveSettings(event.config)
            is AppEvent.ResetSettings -> resetSettings()
            is AppEvent.LoadConfiguration -> loadConfiguration()
            is AppEvent.SearchApps -> searchApps(event.query)
            is AppEvent.ClearSearch -> clearSearch()
            is AppEvent.SetFilter -> setFilter(event.filter)
            is AppEvent.SetSort -> setSort(event.sort)
            is AppEvent.ToggleFavorite -> toggleFavorite(event.appId)
            is AppEvent.UpdateAllApps -> updateAllApps()
            is AppEvent.InstallSuggestedApps -> installSuggestedApps(event.appIds)
            is AppEvent.DismissSuggestions -> dismissSuggestions()
        }
    }

    /**
     * Called when the app was opened from the update notification's
     * "Update all" action. If the list is already on screen the update starts
     * immediately; otherwise it runs as soon as the list finishes loading
     * (consumed in [onAppListLoaded]).
     */
    fun requestUpdateAll() {
        if (_state.value is AppState.Success) {
            updateAllApps()
        } else {
            pendingUpdateAllRequest = true
        }
    }

    // ---- Shared UI helpers (used by all extension files) ----

    internal fun showError(message: String) {
        Log.w(TAG_BLOC, "Showing error: $message")
        _toastMessage.value = message
    }

    internal fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    internal fun showConfirmationDialog(title: String, message: String, onConfirm: AppEvent, onCancel: AppEvent?) {
        val dialogState = DialogState.Confirmation(
            title = title,
            message = message,
            onConfirmAction = { handleEvent(onConfirm) },
            onCancelAction = onCancel?.let { { handleEvent(it) } }
        )
        when (val currentState = _state.value) {
            is AppState.Success -> _state.value = currentState.copy(dialogState = dialogState)
            is AppState.Error -> _state.value = currentState.copy(dialogState = dialogState)
            is AppState.Loading -> Unit
        }
    }

    internal fun dismissDialog() {
        when (val currentState = _state.value) {
            is AppState.Success -> _state.value = currentState.copy(dialogState = null)
            is AppState.Error -> _state.value = currentState.copy(dialogState = null)
            is AppState.Loading -> Unit
        }
    }

    internal fun getDownloadPath(packageName: String): String? {
        val baseDir = context.getExternalFilesDir(null) ?: return null
        val apkFile = File(File(baseDir, "downloads"), "$packageName.apk")
        return if (apkFile.exists()) apkFile.absolutePath else null
    }

    private fun openApp(packageName: String) {
        viewModelScope.launch {
            try {
                val result = useCases.openAppUseCase(packageName)
                when (result) {
                    is com.revanced.net.revancedmanager.core.common.Result.Success -> {
                        if (!result.data) showError(stringProvider.getString(R.string.failed_open_app))
                    }
                    is com.revanced.net.revancedmanager.core.common.Result.Error ->
                        showError(stringProvider.getString(R.string.failed_open_app_error, result.message))
                    is com.revanced.net.revancedmanager.core.common.Result.Loading -> Unit
                }
            } catch (e: Exception) {
                showError(stringProvider.getString(R.string.failed_open_app_error, e.message ?: ""))
            }
        }
    }
}
