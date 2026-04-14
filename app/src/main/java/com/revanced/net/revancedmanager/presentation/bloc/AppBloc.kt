package com.revanced.net.revancedmanager.presentation.bloc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revanced.net.revancedmanager.R
import com.revanced.net.revancedmanager.core.common.StringProvider
import com.revanced.net.revancedmanager.data.local.preferences.PreferencesManager
import com.revanced.net.revancedmanager.data.manager.AppManager
import com.revanced.net.revancedmanager.data.manager.DownloadService
import com.revanced.net.revancedmanager.data.manager.InstallationResult
import com.revanced.net.revancedmanager.data.manager.PackageChangedReceiver
import com.revanced.net.revancedmanager.data.manager.PackageEvent
import com.revanced.net.revancedmanager.data.manager.RevancedPackageInstaller
import com.revanced.net.revancedmanager.data.manager.DebugLogManager
import com.revanced.net.revancedmanager.data.manager.UninstallationResult
import com.revanced.net.revancedmanager.data.manager.SimpleDownloadManager
import com.revanced.net.revancedmanager.data.repository.DownloadStateRepository
import com.revanced.net.revancedmanager.domain.model.AppStatus
import com.revanced.net.revancedmanager.domain.usecase.AppManagementUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Tag used by all AppBloc extension files. */
internal const val TAG_BLOC = "AppBloc"

/**
 * Core ViewModel — owns state, routes events, and manages lifecycle.
 * Domain logic lives in the companion extension files:
 *   AppBlocDownload.kt, AppBlocInstall.kt, AppBlocAppList.kt, AppBlocConfig.kt
 */
@HiltViewModel
class AppBloc @Inject constructor(
    @ApplicationContext internal val context: Context,
    internal val useCases: AppManagementUseCases,
    internal val simpleDownloadManager: SimpleDownloadManager,
    internal val appManager: AppManager,
    internal val preferencesManager: PreferencesManager,
    internal val stringProvider: StringProvider,
    internal val packageInstaller: RevancedPackageInstaller,
    internal val downloadStateRepository: DownloadStateRepository,
    internal val packageChangedReceiver: PackageChangedReceiver,
    val debugLogManager: DebugLogManager
) : ViewModel(), DefaultLifecycleObserver {

    // ---- Public state ----
    internal val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state.asStateFlow()

    internal val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // ---- Download tracking ----
    internal val activeDownloads = mutableMapOf<String, kotlinx.coroutines.Job>()
    internal val completedDownloads = mutableSetOf<String>()

    // ---- Install queue ----
    internal val installationRetries = mutableMapOf<String, Int>()
    internal val installationQueue = mutableListOf<PendingInstallation>()
    internal var isInstallationInProgress = false

    // ---- Lifecycle ----
    internal var wasAppBackgrounded = false

    // ---- Uninstall / reinstall tracking ----
    internal val pendingReinstalls = mutableMapOf<String, String>()         // packageName -> apkPath (retry flow)
    internal val pendingReinstallDownloads = mutableMapOf<String, String>() // packageName -> downloadUrl (reinstall flow)
    internal val pendingUninstallChecks = mutableSetOf<String>()

    data class PendingInstallation(
        val packageName: String,
        val filePath: String,
        val appName: String
    )

    // ---- Broadcast receiver for download completion ----
    private val downloadCompleteBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                DownloadService.ACTION_DOWNLOAD_COMPLETE -> {
                    val packageName = intent.getStringExtra(DownloadService.EXTRA_PACKAGE_NAME)
                    val filePath = intent.getStringExtra(DownloadService.EXTRA_FILE_PATH)
                    if (packageName != null && filePath != null) {
                        Log.i(TAG_BLOC, "Individual download complete broadcast: $packageName")
                        handleEvent(AppEvent.DownloadCompleted(packageName, filePath))
                    }
                }
                DownloadService.ACTION_ALL_DOWNLOADS_COMPLETE -> {
                    Log.i(TAG_BLOC, "All downloads complete broadcast received")
                    handleEvent(
                        AppEvent.AutoInstallAllCompleted(
                            completedPackages = intent.getStringArrayListExtra("completed_packages") ?: arrayListOf(),
                            completedNames = intent.getStringArrayListExtra("completed_names") ?: arrayListOf(),
                            completedPaths = intent.getStringArrayListExtra("completed_paths") ?: arrayListOf(),
                            failedPackages = intent.getStringArrayListExtra("failed_packages") ?: arrayListOf(),
                            failedNames = intent.getStringArrayListExtra("failed_names") ?: arrayListOf(),
                            failedErrors = intent.getStringArrayListExtra("failed_errors") ?: arrayListOf()
                        )
                    )
                }
            }
        }
    }

    init {
        Log.i(TAG_BLOC, "AppBloc initialized")
        handleEvent(AppEvent.LoadConfiguration)
        handleEvent(AppEvent.LoadAppsFromCacheFirst)
        setupPackageInstallerListener()
        setupUninstallListener()
        packageChangedReceiver.register()
        setupPackageChangedListener()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        registerDownloadBroadcastReceiver()
    }

    // ---- Lifecycle ----

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.i(TAG_BLOC, "APP MOVED TO FOREGROUND — was backgrounded: $wasAppBackgrounded")
        if (wasAppBackgrounded) {
            checkPendingDownloadsOnForeground()
            checkPendingUninstallsOnForeground()
        } else {
            resetAllQueuesAndPendingDownloads()
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
        try {
            context.unregisterReceiver(downloadCompleteBroadcastReceiver)
        } catch (e: Exception) {
            Log.w(TAG_BLOC, "Failed to unregister broadcast receiver", e)
        }
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
                        val pendingApkPath = pendingReinstalls.remove(event.packageName)
                        val pendingDownloadUrl = pendingReinstallDownloads.remove(event.packageName)
                        when {
                            pendingApkPath != null -> {
                                // Retry flow: reinstall from already-downloaded APK
                                installApp(event.packageName, pendingApkPath)
                            }
                            pendingDownloadUrl != null -> {
                                // Reinstall flow: fetch latest version from network
                                Log.i(TAG_BLOC, "Starting reinstall download for: ${event.packageName}")
                                completedDownloads.remove(event.packageName)
                                updateAppStatus(event.packageName, AppStatus.DOWNLOADING)
                                downloadApp(event.packageName, pendingDownloadUrl)
                            }
                            else -> {
                                updateAppStatus(event.packageName, AppStatus.NOT_INSTALLED)
                                showToast(stringProvider.getString(R.string.uninstallation_completed))
                            }
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun setupPackageInstallerListener() {
        packageInstaller.installationResults
            .onEach { result ->
                when (result) {
                    is InstallationResult.Success -> {
                        Log.i(TAG_BLOC, "Installation successful: ${result.packageName}")
                        handleInstallationSuccess(
                            result.packageName,
                            appManager.getInstalledVersion(result.packageName) ?: "Unknown"
                        )
                        processNextInstallation()
                    }
                    is InstallationResult.Failed -> {
                        Log.i(TAG_BLOC, "Installation failed: ${result.packageName}, error: ${result.error}")
                        val isUserAbort = result.error.contains("aborted", ignoreCase = true) ||
                                         result.error.contains("cancelled", ignoreCase = true) ||
                                         result.error.contains("user denied", ignoreCase = true) ||
                                         result.statusCode == android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED
                        if (isUserAbort) handleInstallationAborted(result.packageName, result.error)
                        else handleInstallationFailedWithRetry(result.packageName, result.error)
                        processNextInstallation()
                    }
                    is InstallationResult.PendingUserAction -> {
                        Log.i(TAG_BLOC, "User action required for: ${result.packageName}")
                        showToast(stringProvider.getString(R.string.installation_pending_user_action))
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(30_000)
                            if (!wasAppBackgrounded && installationQueue.any { it.packageName == result.packageName }) {
                                Log.w(TAG_BLOC, "Installation timeout for: ${result.packageName}")
                                handleInstallationAborted(result.packageName, "User cancelled or timeout")
                                processNextInstallation()
                            }
                        }
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
                        pendingReinstalls.remove(result.packageName)
                        pendingReinstallDownloads.remove(result.packageName)
                        pendingUninstallChecks.remove(result.packageName)
                        viewModelScope.launch { updateSingleAppStatus(result.packageName) }
                        showToast(stringProvider.getString(R.string.uninstallation_cancelled))
                    }
                    is UninstallationResult.Failed -> {
                        Log.w(TAG_BLOC, "Uninstall failed: ${result.packageName}, code=${result.statusCode}")
                        pendingReinstalls.remove(result.packageName)
                        pendingReinstallDownloads.remove(result.packageName)
                        pendingUninstallChecks.remove(result.packageName)
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

    private fun registerDownloadBroadcastReceiver() {
        val filter = IntentFilter().apply {
            addAction(DownloadService.ACTION_DOWNLOAD_COMPLETE)
            addAction(DownloadService.ACTION_ALL_DOWNLOADS_COMPLETE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(downloadCompleteBroadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(downloadCompleteBroadcastReceiver, filter)
        }
    }

    // ---- Lifecycle helpers (background/foreground) ----

    private fun checkPendingUninstallsOnForeground() {
        viewModelScope.launch {
            pendingUninstallChecks.toList().forEach { packageName ->
                if (!appManager.isAppInstalled(packageName)) {
                    Log.i(TAG_BLOC, "Background uninstall detected: $packageName")
                    pendingUninstallChecks.remove(packageName)
                    val pendingApkPath = pendingReinstalls.remove(packageName)
                    val pendingDownloadUrl = pendingReinstallDownloads.remove(packageName)
                    when {
                        pendingApkPath != null -> installApp(packageName, pendingApkPath)
                        pendingDownloadUrl != null -> {
                            completedDownloads.remove(packageName)
                            updateAppStatus(packageName, AppStatus.DOWNLOADING)
                            downloadApp(packageName, pendingDownloadUrl)
                        }
                        else -> {
                            updateAppStatus(packageName, AppStatus.NOT_INSTALLED)
                            showToast(stringProvider.getString(R.string.uninstallation_completed))
                        }
                    }
                }
            }
        }
    }

    private fun resetAllQueuesAndPendingDownloads() {
        viewModelScope.launch {
            try {
                // Clear all in-memory tracking state
                clearInstallationQueue()
                activeDownloads.values.forEach { it.cancel() }
                activeDownloads.clear()
                completedDownloads.clear()
                pendingReinstalls.clear()
                pendingReinstallDownloads.clear()
                pendingUninstallChecks.clear()
                installationRetries.clear()

                // Clear persisted download state so stale entries can't interfere
                downloadStateRepository.clearAllDownloadStates()

                // Re-resolve every app's status from device state — don't trust what was in memory
                val currentState = _state.value
                if (currentState is AppState.Success) {
                    _state.value = currentState.copy(
                        apps = currentState.apps.map { app ->
                            val actual = resolveActualStatus(app.packageName)
                            app.copy(status = actual, downloadProgress = 0f)
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG_BLOC, "Error resetting queues and pending downloads", e)
            }
        }
    }

    // ---- Event router ----

    fun handleEvent(event: AppEvent) {
        when (event) {
            is AppEvent.LoadApps -> loadApps(forceRefresh = false)
            is AppEvent.RefreshApps -> loadApps(forceRefresh = true)
            is AppEvent.LoadAppsFromCacheFirst -> loadAppsFromCacheFirst()
            is AppEvent.BackgroundRefreshApps -> backgroundRefreshApps()
            is AppEvent.UpdateSingleApp -> updateSingleApp(event.app)
            is AppEvent.DownloadApp -> downloadApp(event.packageName, event.downloadUrl)
            is AppEvent.CancelDownload -> cancelDownload(event.packageName)
            is AppEvent.DownloadCompleted -> handleDownloadCompleted(event.packageName, event.filePath)
            is AppEvent.DownloadFailed -> handleDownloadFailed(event.packageName, event.error)
            is AppEvent.InstallApp -> installApp(event.packageName, event.apkFilePath)
            is AppEvent.RetryInstallation -> retryInstallation(event.packageName, event.apkFilePath, event.shouldUninstallFirst)
            is AppEvent.InstallationCompleted -> handleInstallationCompleted(event.packageName, event.success)
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
            is AppEvent.AutoInstallAllCompleted -> autoInstallAllCompleted(
                event.completedPackages, event.completedNames, event.completedPaths,
                event.failedPackages, event.failedNames, event.failedErrors
            )
            is AppEvent.NavigateToSettings -> navigateToSettings()
            is AppEvent.NavigateBackFromSettings -> navigateBackFromSettings()
            is AppEvent.SaveSettings -> saveSettings(event.config)
            is AppEvent.ResetSettings -> resetSettings()
            is AppEvent.LoadConfiguration -> loadConfiguration()
            is AppEvent.SearchApps -> searchApps(event.query)
            is AppEvent.ClearSearch -> clearSearch()
            is AppEvent.SetFilter -> setFilter(event.filter)
            is AppEvent.ToggleFavorite -> toggleFavorite(event.packageName)
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
        val apkFile = File(File(context.getExternalFilesDir(null), "downloads"), "$packageName.apk")
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
