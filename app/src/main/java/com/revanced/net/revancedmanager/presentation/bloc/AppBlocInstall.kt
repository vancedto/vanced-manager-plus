package com.revanced.net.revancedmanager.presentation.bloc

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.revanced.net.revancedmanager.R
import com.revanced.net.revancedmanager.core.common.Result
import com.revanced.net.revancedmanager.data.manager.InstallationResult
import com.revanced.net.revancedmanager.domain.model.AppStatus
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

// ============= INSTALLATION LOGIC =============
//
// Installations run strictly one at a time: requests go through the
// AppBloc.installRequests channel and a single processor coroutine drives each
// install to a terminal result before starting the next.

private const val INSTALL_RESULT_TIMEOUT_MS = 60_000L

internal fun AppBloc.installApp(packageName: String, apkFilePath: String) {
    val appName = (_state.value as? AppState.Success)
        ?.apps?.find { it.packageName == packageName }?.title ?: packageName
    queueInstallation(packageName, apkFilePath, appName)
}

internal fun AppBloc.queueInstallation(packageName: String, filePath: String, appName: String = packageName) {
    if (!pendingInstalls.add(packageName)) {
        Log.w(TAG_BLOC, "Already queued for install, skipping: $packageName")
        return
    }
    Log.i(TAG_BLOC, "Queueing installation: $appName ($packageName)")
    installRequests.trySend(AppBloc.PendingInstallation(packageName, filePath, appName))
}

internal fun AppBloc.startInstallationProcessor() {
    viewModelScope.launch {
        for (request in installRequests) {
            // Dropped while queued (e.g. a fresh download replaced the APK)
            if (!pendingInstalls.contains(request.packageName)) continue
            try {
                runInstallation(request)
            } finally {
                pendingInstalls.remove(request.packageName)
            }
        }
    }
}

private suspend fun AppBloc.runInstallation(request: AppBloc.PendingInstallation) = coroutineScope {
    val packageName = request.packageName
    Log.i(TAG_BLOC, "Starting installation: ${request.appName} ($packageName)")
    updateAppStatus(packageName, AppStatus.INSTALLING)

    // Subscribe before committing the session so a fast result can't be missed
    val terminalResult = async(start = CoroutineStart.UNDISPATCHED) {
        packageInstaller.installationResults.first {
            it.packageName == packageName && it !is InstallationResult.PendingUserAction
        }
    }
    val pendingActionToast = launch(start = CoroutineStart.UNDISPATCHED) {
        packageInstaller.installationResults.first {
            it.packageName == packageName && it is InstallationResult.PendingUserAction
        }
        showToast(stringProvider.getString(R.string.installation_pending_user_action))
    }

    try {
        val startError = startInstallation(packageName, request.filePath)
        if (startError != null) {
            handleInstallationFailure(packageName, startError)
            return@coroutineScope
        }

        // While backgrounded, the system confirm dialog is delivered as a
        // notification and the user may take arbitrarily long — keep waiting.
        var result = withTimeoutOrNull(INSTALL_RESULT_TIMEOUT_MS) { terminalResult.await() }
        while (result == null && wasAppBackgrounded) {
            result = withTimeoutOrNull(INSTALL_RESULT_TIMEOUT_MS) { terminalResult.await() }
        }

        when (result) {
            null -> {
                Log.w(TAG_BLOC, "Installation timed out: $packageName")
                handleInstallationAborted(packageName, "User cancelled or timeout")
            }
            is InstallationResult.Success ->
                handleInstallationSuccess(
                    packageName,
                    appManager.getInstalledVersion(packageName) ?: "Unknown"
                )
            is InstallationResult.Failed -> {
                val userAborted =
                    result.statusCode == android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED ||
                    result.error.contains("aborted", ignoreCase = true) ||
                    result.error.contains("cancelled", ignoreCase = true) ||
                    result.error.contains("user denied", ignoreCase = true)
                if (userAborted) handleInstallationAborted(packageName, result.error)
                else handleInstallationFailure(packageName, result.error)
            }
            is InstallationResult.PendingUserAction -> Unit // filtered out above
        }
    } finally {
        terminalResult.cancel()
        pendingActionToast.cancel()
    }
}

/** Kick off the install session. Returns an error message, or null when started. */
private suspend fun AppBloc.startInstallation(packageName: String, filePath: String): String? = try {
    when (val result = useCases.installAppUseCase(packageName, filePath)) {
        is Result.Success ->
            if (result.data) {
                showToast(stringProvider.getString(R.string.installation_started))
                null
            } else {
                stringProvider.getString(R.string.installation_failed_start)
            }
        is Result.Error -> result.message
        is Result.Loading -> null
    }
} catch (e: Exception) {
    e.message ?: "Installation failed"
}

internal fun AppBloc.handleInstallationSuccess(packageName: String, installedVersion: String) {
    val currentState = _state.value as? AppState.Success ?: return
    val app = currentState.apps.find { it.packageName == packageName } ?: return

    val newStatus = statusForVersions(installedVersion, app.latestVersion)
    // Success arrives via both PackageInstaller results and the system
    // PACKAGE_ADDED broadcast — skip the second delivery.
    if (app.status == newStatus && app.currentVersion == installedVersion) {
        Log.i(TAG_BLOC, "Installation success already handled for: $packageName")
        return
    }

    _state.value = currentState.copy(
        apps = currentState.apps.map { appItem ->
            if (appItem.packageName == packageName)
                appItem.copy(status = newStatus, currentVersion = installedVersion, downloadProgress = 0f)
            else appItem
        }
    )
    showToast(stringProvider.getString(R.string.installation_completed))

    preferencesManager.removeKey("pending_install_${packageName}")
    installationRetries.remove(packageName)
    pendingInstalls.remove(packageName)

    // Clean up the downloaded APK once it has been installed (if enabled)
    if (preferencesManager.isAutoDeleteApkEnabled()) {
        deleteDownloadedApk(packageName)
    }
    downloadManager.pruneFinishedWork()
}

internal fun AppBloc.handleInstallationFailure(packageName: String, error: String) {
    Log.w(TAG_BLOC, "Installation failed: $packageName - $error")
    val actualStatus = resolveActualStatus(packageName)
    updateAppStatus(packageName, actualStatus)

    // Retry is only possible while the downloaded APK still exists (it may have
    // been auto-deleted after a previous install)
    val retries = installationRetries[packageName] ?: 0
    val apkPath = getDownloadPath(packageName)
    if (retries < 1 && apkPath != null) {
        installationRetries[packageName] = retries + 1
        showConfirmationDialog(
            title = stringProvider.getString(R.string.installation_failed_title),
            message = stringProvider.getString(R.string.installation_failed_retry_message, error),
            onConfirm = AppEvent.RetryInstallation(packageName, apkPath, shouldUninstallFirst = true),
            // Use actualStatus so cancelling the dialog doesn't overwrite with wrong status
            onCancel = AppEvent.DismissDialogAndUpdateStatus(packageName, actualStatus)
        )
    } else {
        installationRetries.remove(packageName)
        showError(stringProvider.getString(R.string.download_failed, error))
    }
    downloadManager.pruneFinishedWork()
}

internal fun AppBloc.handleInstallationAborted(packageName: String, error: String) {
    Log.i(TAG_BLOC, "Installation aborted by user: $packageName - $error")
    installationRetries.remove(packageName)
    updateAppStatus(packageName, resolveActualStatus(packageName))
    showToast(stringProvider.getString(R.string.installation_cancelled_by_user))
    downloadManager.pruneFinishedWork()
}

internal suspend fun AppBloc.updateSingleAppStatus(packageName: String) {
    try {
        val currentState = _state.value
        if (currentState is AppState.Success) {
            val appToUpdate = currentState.apps.find { it.packageName == packageName } ?: return

            if (appToUpdate.status in listOf(AppStatus.DOWNLOADING, AppStatus.INSTALLING, AppStatus.UNINSTALLING)) {
                if (appToUpdate.status == AppStatus.UNINSTALLING) {
                    if (!appManager.isAppInstalled(packageName)) {
                        val updatedApps = currentState.apps.map { app ->
                            if (app.packageName == packageName)
                                app.copy(status = AppStatus.NOT_INSTALLED, currentVersion = null, downloadProgress = 0f)
                            else app
                        }
                        _state.value = currentState.copy(apps = updatedApps)
                        showToast(stringProvider.getString(R.string.uninstallation_completed))
                    }
                }
                return
            }

            val isInstalled = appManager.isAppInstalled(packageName)
            val installedVersion = if (isInstalled) appManager.getInstalledVersion(packageName) else null
            val newStatus =
                if (installedVersion != null) statusForVersions(installedVersion, appToUpdate.latestVersion)
                else AppStatus.NOT_INSTALLED

            if (newStatus != appToUpdate.status) {
                val updatedApps = currentState.apps.map { app ->
                    if (app.packageName == packageName)
                        app.copy(status = newStatus, currentVersion = installedVersion, downloadProgress = 0f)
                    else app
                }
                _state.value = currentState.copy(apps = updatedApps)
                showToast(stringProvider.getString(R.string.app_status_updated))
            }
        }
    } catch (e: Exception) {
        loadApps(forceRefresh = true)
    }
}

internal fun AppBloc.retryInstallation(packageName: String, apkFilePath: String, shouldUninstallFirst: Boolean) {
    dismissDialog()

    if (shouldUninstallFirst) {
        viewModelScope.launch {
            try {
                val isInstalled = appManager.isAppInstalled(packageName)
                if (!isInstalled) {
                    showToast(stringProvider.getString(R.string.app_not_installed_proceeding))
                    installApp(packageName, apkFilePath)
                    return@launch
                }

                pendingReinstalls[packageName] = apkFilePath
                pendingUninstallChecks.add(packageName)
                updateAppStatus(packageName, AppStatus.UNINSTALLING)

                val uninstallResult = useCases.uninstallAppUseCase(packageName)
                when (uninstallResult) {
                    is Result.Success -> {
                        if (uninstallResult.data) {
                            showToast(stringProvider.getString(R.string.old_version_uninstalled))
                            // Reinstall triggered by PackageChangedReceiver when uninstall completes
                        } else {
                            clearPendingReinstall(packageName)
                            showError(stringProvider.getString(R.string.failed_uninstall_old_version))
                            updateAppStatus(packageName, AppStatus.NOT_INSTALLED)
                        }
                    }
                    is Result.Error -> {
                        clearPendingReinstall(packageName)
                        showError(stringProvider.getString(R.string.failed_uninstall_old_version_error, uninstallResult.message))
                        updateAppStatus(packageName, AppStatus.NOT_INSTALLED)
                    }
                    is Result.Loading -> Unit
                }
            } catch (e: Exception) {
                clearPendingReinstall(packageName)
                showError(stringProvider.getString(R.string.failed_uninstall_old_version_error, e.message ?: ""))
                updateAppStatus(packageName, AppStatus.NOT_INSTALLED)
            }
        }
    } else {
        installApp(packageName, apkFilePath)
    }
}

internal fun AppBloc.confirmUninstallBeforeReinstall(packageName: String, apkFilePath: String) {
    val currentState = _state.value
    if (currentState is AppState.Success) {
        val app = currentState.apps.find { it.packageName == packageName }
        app?.let {
            showConfirmationDialog(
                title = stringProvider.getString(R.string.uninstall_required_title),
                message = stringProvider.getString(R.string.uninstall_required_message, app.title),
                onConfirm = AppEvent.RetryInstallation(packageName, apkFilePath, shouldUninstallFirst = true),
                onCancel = AppEvent.UpdateAppStatus(packageName, AppStatus.NOT_INSTALLED)
            )
        }
    }
}

internal fun AppBloc.uninstallApp(packageName: String) {
    dismissDialog()

    viewModelScope.launch {
        try {
            updateAppStatus(packageName, AppStatus.UNINSTALLING)
            pendingUninstallChecks.add(packageName)

            val result = useCases.uninstallAppUseCase(packageName)
            when (result) {
                is Result.Success -> {
                    if (result.data) {
                        showToast(stringProvider.getString(R.string.uninstallation_started))
                        // Result delivered via setupUninstallListener() or PackageChangedReceiver
                    } else {
                        pendingUninstallChecks.remove(packageName)
                        showError(stringProvider.getString(R.string.failed_start_uninstallation))
                        updateSingleAppStatus(packageName)
                    }
                }
                is Result.Error -> {
                    pendingUninstallChecks.remove(packageName)
                    showError(stringProvider.getString(R.string.uninstallation_failed, result.message))
                    updateSingleAppStatus(packageName)
                }
                is Result.Loading -> Unit
            }
        } catch (e: Exception) {
            pendingUninstallChecks.remove(packageName)
            showError(stringProvider.getString(R.string.uninstallation_failed, e.message ?: ""))
            updateSingleAppStatus(packageName)
        }
    }
}

internal fun AppBloc.showReinstallConfirmation(packageName: String) {
    val currentState = _state.value
    if (currentState is AppState.Success) {
        val app = currentState.apps.find { it.packageName == packageName }
        if (app != null) {
            handleEvent(AppEvent.ShowConfirmationDialog(
                title = stringProvider.getString(R.string.reinstall_confirmation_title),
                message = stringProvider.getString(R.string.reinstall_confirmation_message, app.title),
                onConfirm = AppEvent.ReinstallApp(packageName),
                onCancel = AppEvent.DismissDialog
            ))
        }
    }
}

internal fun AppBloc.reinstallApp(packageName: String) {
    dismissDialog()

    val app = (_state.value as? AppState.Success)?.apps?.find { it.packageName == packageName }
    if (app == null) {
        Log.w(TAG_BLOC, "reinstallApp: app not found in state — $packageName")
        return
    }

    viewModelScope.launch {
        try {
            updateAppStatus(packageName, AppStatus.UNINSTALLING)
            showToast(stringProvider.getString(R.string.reinstall_started))

            val uninstallResult = useCases.uninstallAppUseCase(packageName)
            when (uninstallResult) {
                is Result.Success -> {
                    if (uninstallResult.data) {
                        // Queue the download to start after PackageChangedReceiver confirms uninstall
                        pendingReinstallDownloads[packageName] = app.downloadUrl
                        pendingUninstallChecks.add(packageName)
                        Log.i(TAG_BLOC, "Reinstall: uninstall submitted for $packageName, download queued")
                    } else {
                        showError(stringProvider.getString(R.string.reinstall_failed))
                        updateSingleAppStatus(packageName)
                    }
                }
                is Result.Error -> {
                    showError(stringProvider.getString(R.string.reinstall_failed, uninstallResult.message))
                    updateSingleAppStatus(packageName)
                }
                is Result.Loading -> Unit
            }
        } catch (e: Exception) {
            Log.e(TAG_BLOC, "reinstallApp failed for $packageName", e)
            clearPendingReinstall(packageName)
            showError(stringProvider.getString(R.string.reinstall_failed, e.message ?: ""))
            updateSingleAppStatus(packageName)
        }
    }
}

/**
 * Delete the downloaded APK for [packageName] from the app's downloads directory.
 * Used to free storage after a successful install when auto-delete is enabled.
 */
internal fun AppBloc.deleteDownloadedApk(packageName: String) {
    try {
        val baseDir = context.getExternalFilesDir(null) ?: return
        val apkFile = File(File(baseDir, "downloads"), "$packageName.apk")
        if (apkFile.exists()) {
            if (apkFile.delete()) {
                Log.i(TAG_BLOC, "Deleted downloaded APK after install: ${apkFile.absolutePath}")
            } else {
                Log.w(TAG_BLOC, "Failed to delete downloaded APK: ${apkFile.absolutePath}")
            }
        }
    } catch (e: Exception) {
        Log.w(TAG_BLOC, "Error deleting downloaded APK for $packageName", e)
    }
}
