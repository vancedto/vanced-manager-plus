package com.revanced.net.revancedmanager.presentation.bloc

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revanced.net.revancedmanager.R
import com.revanced.net.revancedmanager.core.common.InFlightAttribution
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
import com.revanced.net.revancedmanager.domain.model.RevancedApp
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
import java.util.concurrent.atomic.AtomicInteger
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
    // The work-id and user-requested guards used to live here as ViewModel fields. They now
    // belong to AppDownloadManager (@Singleton) instead: they describe a download, not an
    // observer of one, and while more than one AppBloc was alive each instance passed its own
    // copy of the guard for the same finished work — see AppDownloadManager's class doc.

    /** Short identity for log lines, so duplicate instances are visible in one line. */
    internal val blocId: String = Integer.toHexString(System.identityHashCode(this)).takeLast(4)

    // ---- Install queue ----
    internal val installRequests = Channel<PendingInstallation>(Channel.UNLIMITED)
    /**
     * Packages queued for install or currently installing.
     *
     * Stays a ViewModel field, unlike the download guards: this is one instance's own install
     * queue, drained by that instance's [startInstallationProcessor], and there is only ever one
     * live AppBloc now that MainActivity is singleTop.
     */
    internal val pendingInstalls = mutableSetOf<String>()
    internal val installationRetries = mutableMapOf<String, Int>()

    /**
     * packageName -> id of the catalog entry whose button started the operation now in flight.
     *
     * There is one operation per package — one download, one APK on disk, one installer session —
     * but it was started from one entry, and that is the row that should show it. MicroG RE and
     * ReVanced GmsCore are both `app.revanced.android.gms`; without this, updating RE spun both.
     *
     * Written where an operation starts, re-seeded from the download work's own tag so it survives
     * process death, and cleared in exactly one place: [settleEntries], the single terminal
     * re-derivation. Not cleared by a settled [updateAppStatus], because the failure paths write
     * the settled status *before* reading the title and download URL for the dialog they raise.
     *
     * Absent means the initiator is unknown — see [InFlightAttribution] for what that falls back to.
     */
    internal val inFlightInitiators = mutableMapOf<String, String>()

    /**
     * Install-failure dialogs waiting their turn. There is a single dialogState for the whole
     * app, so during a batch update each new failure dialog used to overwrite the previous one —
     * the user only ever saw (and answered) the last failure, and the rest vanished without a
     * trace. Failure dialogs queue here instead and are shown one at a time, only once the
     * install queue has drained, so they never interrupt a running batch either.
     */
    internal val queuedDialogs = ArrayDeque<DialogState.Confirmation>()

    // ---- Lifecycle ----
    internal var wasAppBackgrounded = false

    // ---- Launch prompts ----
    /** The "updates available" prompt is asked at most once per app session. */
    internal var updatePromptShownThisSession = false
    /** Set when the update notification's "Update all" action opened the app. */
    internal var pendingUpdateAllRequest = false

    // ---- Download gate ----
    /** Downloads held back until the user grants "Install unknown apps"; see requestDownloads. */
    internal var downloadsAwaitingPermission: List<RevancedApp> = emptyList()
    /**
     * Set once the user has been sent to the permission screen, so only the return from *that*
     * trip resumes the held downloads — not any other trip to the background while the dialog
     * is still up.
     */
    internal var installPermissionScreenOpened = false

    // ---- Uninstall / reinstall tracking ----
    internal val pendingReinstalls = mutableMapOf<String, String>()  // packageName -> apkPath (retry flow)
    internal val pendingUninstallChecks = mutableSetOf<String>()

    data class PendingInstallation(
        val packageName: String,
        val filePath: String,
        val appName: String
    )

    companion object {
        /** Live AppBloc count, used only to make a duplicate-instance bug obvious in the log. */
        private val liveInstances = AtomicInteger(0)
    }

    init {
        val liveCount = liveInstances.incrementAndGet()
        Log.i(TAG_BLOC, "[$blocId] AppBloc initialized (live instances: $liveCount)")
        if (liveCount > 1) {
            // Every extra instance collects the shared download flow and drives its own install
            // queue. That is the bug this warning exists to catch: notification PendingIntents
            // without FLAG_ACTIVITY_SINGLE_TOP (or a MainActivity that is not singleTop) stack a
            // second activity, whose predecessor is stopped rather than destroyed, so onCleared()
            // never runs and its AppBloc stays alive.
            Log.w(
                TAG_BLOC,
                "[$blocId] $liveCount AppBloc instances are alive at once — expected exactly 1. " +
                    "A second MainActivity was almost certainly stacked on the task."
            )
        }
        // Sessions left over from a killed process would otherwise linger forever (and hold
        // storage); nothing in this process owns them yet, so they are safe to abandon.
        packageInstaller.abandonOrphanedSessions()
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
            resumeDownloadsAwaitingPermission()
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
        val remaining = liveInstances.decrementAndGet()
        Log.i(TAG_BLOC, "[$blocId] AppBloc cleared (live instances: $remaining)")
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
                        Log.i(TAG_BLOC, "[$blocId] System event: Package installed/updated: $packageName")
                        pendingUninstallChecks.remove(packageName)
                        val installedVersion = appManager.getInstalledVersion(packageName)
                        if (installedVersion != null) {
                            handleInstallationSuccess(
                                packageName,
                                installedVersion,
                                source = "PACKAGE_ADDED broadcast"
                            )
                        }
                    }
                    is PackageEvent.Uninstalled -> {
                        Log.i(TAG_BLOC, "[$blocId] System event: Package uninstalled: ${event.packageName}")
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
     * install the already-downloaded-and-verified APK, or plain uninstall.
     */
    internal fun handlePendingReinstall(packageName: String) {
        val pendingApkPath = pendingReinstalls.remove(packageName)
        if (pendingApkPath != null) {
            // Still the same operation — the install that asked for this uninstall is next, so the
            // initiator recorded for it stays.
            installApp(packageName, pendingApkPath)
        } else {
            inFlightInitiators.remove(packageName)
            updateAppStatus(packageName, AppStatus.NOT_INSTALLED)
            showToast(stringProvider.getString(R.string.uninstallation_completed))
        }
    }

    internal fun clearPendingReinstall(packageName: String) {
        pendingReinstalls.remove(packageName)
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
            is AppEvent.DownloadApp -> requestDownload(event.appId, event.packageName, event.downloadUrl)
            is AppEvent.StartDownloads -> startDownloads(event.appIds)
            is AppEvent.InstallPermissionAnswer -> answerInstallPermission(event.openSettings)
            is AppEvent.CancelDownload -> cancelDownload(event.packageName)
            is AppEvent.CancelInstallation -> cancelInstallation(event.packageName)
            is AppEvent.InstallApp -> installApp(event.packageName, event.apkFilePath)
            is AppEvent.RetryInstallation -> retryInstallation(event.packageName, event.apkFilePath, event.shouldUninstallFirst)
            is AppEvent.ConfirmUninstallBeforeReinstall -> confirmUninstallBeforeReinstall(event.packageName, event.apkFilePath)
            is AppEvent.UninstallApp -> uninstallApp(event.packageName, event.confirmed)
            is AppEvent.ShowReinstallConfirmation -> showReinstallConfirmation(event.appId, event.packageName)
            is AppEvent.ReinstallApp -> reinstallApp(event.appId, event.packageName)
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
            is AppEvent.UpdateSelectedApps -> updateSelectedApps(event.appIds)
            is AppEvent.ToggleUpdatePrompt -> toggleUpdatePrompt(event.appId)
            is AppEvent.InstallSuggestedApps -> installSuggestedApps(event.appIds)
            is AppEvent.DismissSuggestions -> dismissSuggestions()
            is AppEvent.ChooseAppSource -> chooseAppSource(event.showCommunityApps)
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

    internal fun showConfirmationDialog(
        title: String,
        message: String,
        onConfirm: AppEvent,
        onCancel: AppEvent?,
        confirmLabel: String? = null,
        cancelLabel: String? = null,
        destructive: Boolean = false,
        showCancelButton: Boolean = true
    ) {
        setDialogState(
            buildConfirmation(title, message, onConfirm, onCancel, confirmLabel, cancelLabel, destructive, showCancelButton)
        )
    }

    /**
     * Show an install-failure dialog without losing any other: shown immediately when nothing
     * else is on screen and the install queue is idle, queued otherwise. The queue drains one
     * dialog at a time via [dismissDialog] / [flushQueuedDialogs].
     */
    internal fun enqueueConfirmationDialog(
        title: String,
        message: String,
        onConfirm: AppEvent,
        onCancel: AppEvent?,
        confirmLabel: String? = null,
        cancelLabel: String? = null,
        destructive: Boolean = false,
        showCancelButton: Boolean = true
    ) {
        val dialog = buildConfirmation(title, message, onConfirm, onCancel, confirmLabel, cancelLabel, destructive, showCancelButton)
        if (currentDialogState() == null && pendingInstalls.isEmpty()) {
            setDialogState(dialog)
        } else {
            queuedDialogs.addLast(dialog)
        }
    }

    /** A dialog whose only action is acknowledging — used for errors that must be readable (§1.1). */
    internal fun showInfoDialog(title: String, message: String) {
        enqueueConfirmationDialog(
            title = title,
            message = message,
            onConfirm = AppEvent.DismissDialog,
            onCancel = null,
            confirmLabel = stringProvider.getString(R.string.close_button),
            showCancelButton = false
        )
    }

    internal fun dismissDialog() {
        when (val currentState = _state.value) {
            is AppState.Success -> _state.value = currentState.copy(dialogState = null)
            is AppState.Error -> _state.value = currentState.copy(dialogState = null)
            is AppState.Loading -> Unit
        }
        flushQueuedDialogs()
    }

    /** Show the next queued failure dialog, if the screen is free and no batch is running. */
    internal fun flushQueuedDialogs() {
        if (queuedDialogs.isEmpty()) return
        if (currentDialogState() != null || pendingInstalls.isNotEmpty()) return
        setDialogState(queuedDialogs.removeFirst())
    }

    private fun buildConfirmation(
        title: String,
        message: String,
        onConfirm: AppEvent,
        onCancel: AppEvent?,
        confirmLabel: String?,
        cancelLabel: String?,
        destructive: Boolean,
        showCancelButton: Boolean
    ) = DialogState.Confirmation(
        title = title,
        message = message,
        onConfirmAction = { handleEvent(onConfirm) },
        onCancelAction = onCancel?.let { { handleEvent(it) } },
        confirmLabel = confirmLabel,
        cancelLabel = cancelLabel,
        destructive = destructive,
        showCancelButton = showCancelButton
    )

    private fun currentDialogState(): DialogState? = when (val s = _state.value) {
        is AppState.Success -> s.dialogState
        is AppState.Error -> s.dialogState
        is AppState.Loading -> null
    }

    private fun setDialogState(dialogState: DialogState) {
        when (val currentState = _state.value) {
            is AppState.Success -> _state.value = currentState.copy(dialogState = dialogState)
            is AppState.Error -> _state.value = currentState.copy(dialogState = dialogState)
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
