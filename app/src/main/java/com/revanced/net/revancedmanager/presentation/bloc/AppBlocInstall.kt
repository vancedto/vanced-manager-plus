package com.revanced.net.revancedmanager.presentation.bloc

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.revanced.net.revancedmanager.R
import com.revanced.net.revancedmanager.core.common.Result
import com.revanced.net.revancedmanager.domain.model.AppStatus
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

// ============= INSTALLATION LOGIC =============

internal fun AppBloc.installApp(packageName: String, apkFilePath: String) {
    val appName = (_state.value as? AppState.Success)
        ?.apps?.find { it.packageName == packageName }?.title ?: packageName
    Log.i(TAG_BLOC, "Queueing app installation: $appName ($packageName)")
    queueInstallation(packageName, apkFilePath, appName)
}

internal fun AppBloc.installAppDirect(packageName: String, apkFilePath: String) {
    Log.i(TAG_BLOC, "Installing app directly: $packageName from $apkFilePath")
    viewModelScope.launch {
        try {
            val result = useCases.installAppUseCase(packageName, apkFilePath)
            when (result) {
                is Result.Success -> {
                    if (result.data) {
                        showToast(stringProvider.getString(R.string.installation_started))
                    } else {
                        handleInstallationFailedWithRetry(packageName, stringProvider.getString(R.string.installation_failed_start))
                    }
                }
                is Result.Error -> handleInstallationFailedWithRetry(packageName, result.message)
                is Result.Loading -> Unit
            }
        } catch (e: Exception) {
            handleInstallationFailedWithRetry(packageName, e.message ?: "Installation failed")
        }
    }
}

internal fun AppBloc.handleInstallationFailedWithRetry(packageName: String, error: String) {
    val currentRetries = installationRetries[packageName] ?: 0

    // Resolve actual device state — don't assume UPDATE_AVAILABLE
    val actualStatus = resolveActualStatus(packageName)
    updateAppStatus(packageName, actualStatus)

    if (currentRetries < 1) {
        installationRetries[packageName] = currentRetries + 1
        showConfirmationDialog(
            title = stringProvider.getString(R.string.installation_failed_title),
            message = stringProvider.getString(R.string.installation_failed_retry_message, error),
            onConfirm = AppEvent.RetryInstallation(packageName, getDownloadPath(packageName) ?: "", shouldUninstallFirst = true),
            // Use actualStatus so cancelling the dialog doesn't overwrite with wrong status
            onCancel = AppEvent.DismissDialogAndUpdateStatus(packageName, actualStatus)
        )
    } else {
        installationRetries.remove(packageName)
        showError(stringProvider.getString(R.string.download_failed, error))
    }

    installationQueue.removeAll { it.packageName == packageName }

    viewModelScope.launch {
        try {
            downloadStateRepository.removeDownloadState(packageName)
        } catch (e: Exception) {
            Log.w(TAG_BLOC, "Failed to remove download state for failed installation", e)
        }
    }
}

internal fun AppBloc.handleInstallationSuccess(packageName: String, installedVersion: String) {
    val currentState = _state.value
    if (currentState is AppState.Success) {
        val app = currentState.apps.find { it.packageName == packageName }
        app?.let {
            val newStatus = when (compareVersions(installedVersion, app.latestVersion)) {
                0, 1 -> AppStatus.UP_TO_DATE
                else -> AppStatus.UPDATE_AVAILABLE
            }
            val updatedApps = currentState.apps.map { appItem ->
                if (appItem.packageName == packageName) {
                    appItem.copy(status = newStatus, currentVersion = installedVersion, downloadProgress = 0f)
                } else appItem
            }
            _state.value = currentState.copy(apps = updatedApps)
            showToast(stringProvider.getString(R.string.installation_completed))

            preferencesManager.removeKey("pending_install_${packageName}")
            installationRetries.remove(packageName)
            completedDownloads.remove(packageName)
            installationQueue.removeAll { it.packageName == packageName }

            viewModelScope.launch {
                try {
                    downloadStateRepository.removeDownloadState(packageName)
                } catch (e: Exception) {
                    Log.w(TAG_BLOC, "Failed to remove download state", e)
                }
            }
        }
    }
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
            val newStatus = when {
                !isInstalled -> AppStatus.NOT_INSTALLED
                installedVersion != null -> when (compareVersions(installedVersion, appToUpdate.latestVersion)) {
                    0, 1 -> AppStatus.UP_TO_DATE
                    else -> AppStatus.UPDATE_AVAILABLE
                }
                else -> AppStatus.NOT_INSTALLED
            }

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

internal fun AppBloc.handleInstallationCompleted(packageName: String, success: Boolean) {
    if (success) {
        updateAppStatus(packageName, AppStatus.UP_TO_DATE)
        showToast(stringProvider.getString(R.string.installation_completed))
        installationRetries.remove(packageName)
        preferencesManager.removeKey("pending_install_${packageName}")
    } else {
        val currentState = _state.value
        if (currentState is AppState.Success) {
            val app = currentState.apps.find { it.packageName == packageName }
            app?.let {
                val apkFilePath = getDownloadPath(packageName) ?: preferencesManager.getPendingInstallPath(packageName)
                if (apkFilePath != null) {
                    handleInstallationFailed(packageName, apkFilePath, stringProvider.getString(R.string.installation_failed_start))
                } else {
                    updateAppStatus(packageName, AppStatus.NOT_INSTALLED)
                    showError(stringProvider.getString(R.string.installation_failed_apk_not_found))
                }
            }
        }
    }
}

internal fun AppBloc.handleInstallationAborted(packageName: String, error: String) {
    Log.i(TAG_BLOC, "Installation aborted by user: $packageName")
    installationRetries.remove(packageName)

    val currentState = _state.value
    if (currentState is AppState.Success) {
        val app = currentState.apps.find { it.packageName == packageName }
        app?.let {
            val isInstalled = appManager.isAppInstalled(packageName)
            val newStatus = if (isInstalled) {
                val installedVersion = appManager.getInstalledVersion(packageName)
                if (installedVersion != null) {
                    when (compareVersions(installedVersion, app.latestVersion)) {
                        0, 1 -> AppStatus.UP_TO_DATE
                        else -> AppStatus.UPDATE_AVAILABLE
                    }
                } else AppStatus.NOT_INSTALLED
            } else AppStatus.NOT_INSTALLED
            updateAppStatus(packageName, newStatus)
        }
    }

    showToast(stringProvider.getString(R.string.installation_cancelled_by_user))

    viewModelScope.launch {
        downloadStateRepository.removeDownloadState(packageName)
    }
    installationQueue.removeAll { it.packageName == packageName }
    completedDownloads.remove(packageName)
}

internal fun AppBloc.handleInstallationFailed(packageName: String, apkFilePath: String, error: String) {
    val currentRetries = installationRetries[packageName] ?: 0
    if (currentRetries < 1) {
        installationRetries[packageName] = currentRetries + 1
        showConfirmationDialog(
            title = stringProvider.getString(R.string.installation_failed_title),
            message = stringProvider.getString(R.string.installation_failed_retry_message, error),
            onConfirm = AppEvent.RetryInstallation(packageName, apkFilePath, shouldUninstallFirst = true),
            onCancel = AppEvent.UpdateAppStatus(packageName, AppStatus.NOT_INSTALLED)
        )
    } else {
        installationRetries.remove(packageName)
        updateAppStatus(packageName, AppStatus.NOT_INSTALLED)
        showError(stringProvider.getString(R.string.download_failed, error))
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
                            pendingReinstalls.remove(packageName)
                            pendingUninstallChecks.remove(packageName)
                            showError(stringProvider.getString(R.string.failed_uninstall_old_version))
                            updateAppStatus(packageName, AppStatus.NOT_INSTALLED)
                        }
                    }
                    is Result.Error -> {
                        pendingReinstalls.remove(packageName)
                        pendingUninstallChecks.remove(packageName)
                        showError(stringProvider.getString(R.string.failed_uninstall_old_version_error, uninstallResult.message))
                        updateAppStatus(packageName, AppStatus.NOT_INSTALLED)
                    }
                    is Result.Loading -> Unit
                }
            } catch (e: Exception) {
                pendingReinstalls.remove(packageName)
                pendingUninstallChecks.remove(packageName)
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
            pendingReinstallDownloads.remove(packageName)
            pendingUninstallChecks.remove(packageName)
            showError(stringProvider.getString(R.string.reinstall_failed, e.message ?: ""))
            updateSingleAppStatus(packageName)
        }
    }
}

// ============= INSTALLATION QUEUE =============

internal fun AppBloc.queueInstallation(packageName: String, filePath: String, appName: String = packageName) {
    Log.i(TAG_BLOC, "Queuing installation: $appName ($packageName)")

    if (installationQueue.any { it.packageName == packageName }) {
        Log.w(TAG_BLOC, "Already in queue, skipping: $packageName")
        return
    }

    installationQueue.add(AppBloc.PendingInstallation(packageName, filePath, appName))
    Log.i(TAG_BLOC, "Queue size: ${installationQueue.size}, inProgress: $isInstallationInProgress")

    if (!isInstallationInProgress) {
        triggerNextInstallation()
    }
}

internal fun AppBloc.triggerNextInstallation() {
    val next = installationQueue.firstOrNull()
    if (next == null) {
        Log.i(TAG_BLOC, "Installation queue empty, nothing to start")
        isInstallationInProgress = false
        return
    }
    isInstallationInProgress = true
    Log.i(TAG_BLOC, "Starting next installation: ${next.appName} (${next.packageName})")
    updateAppStatus(next.packageName, AppStatus.INSTALLING)
    installAppDirect(next.packageName, next.filePath)
}

internal fun AppBloc.processNextInstallation() {
    Log.i(TAG_BLOC, "processNextInstallation — queue size before cleanup: ${installationQueue.size}")

    installationQueue.removeAll { installation ->
        val state = _state.value as? AppState.Success
        val status = state?.apps?.find { it.packageName == installation.packageName }?.status
        val done = status == AppStatus.UP_TO_DATE ||
                   status == AppStatus.NOT_INSTALLED ||
                   status == AppStatus.UPDATE_AVAILABLE
        if (done) Log.i(TAG_BLOC, "Removing finished entry: ${installation.appName}")
        done
    }

    isInstallationInProgress = false
    Log.i(TAG_BLOC, "Queue size after cleanup: ${installationQueue.size}")
    triggerNextInstallation()
}

internal fun AppBloc.clearInstallationQueue() {
    Log.i(TAG_BLOC, "Clearing installation queue (${installationQueue.size} items)")
    installationQueue.clear()
    isInstallationInProgress = false
}

// ============= CONCURRENT AUTO-INSTALL =============

internal fun AppBloc.autoInstallAllCompleted(
    completedPackages: List<String>,
    completedNames: List<String>,
    completedPaths: List<String>,
    failedPackages: List<String>,
    failedNames: List<String>,
    failedErrors: List<String>
) {
    Log.i(TAG_BLOC, "Auto-installing all completed downloads — completed: ${completedPackages.size}, failed: ${failedPackages.size}")

    viewModelScope.launch {
        try {
            failedPackages.forEach { pkg -> updateAppStatus(pkg, resolveActualStatus(pkg)) }

            val successState = _state.value as? AppState.Success
            completedPackages.zip(completedNames).zip(completedPaths).forEach { (namePkg, path) ->
                val (packageName, appName) = namePkg
                val currentStatus = successState?.apps?.find { it.packageName == packageName }?.status
                if (currentStatus == AppStatus.UP_TO_DATE) { Log.w(TAG_BLOC, "Skipping already installed: $packageName"); return@forEach }
                if (currentStatus == AppStatus.INSTALLING || installationQueue.any { it.packageName == packageName }) {
                    Log.w(TAG_BLOC, "Skipping already-installing: $packageName"); return@forEach
                }
                queueInstallation(packageName, path, appName)
            }

            if (completedPackages.isNotEmpty()) {
                showToast(
                    if (completedPackages.size == 1) "Installing ${completedNames[0]}..."
                    else "Installing ${completedPackages.size} apps..."
                )
            }
            if (failedPackages.isNotEmpty()) {
                showToast("${failedPackages.size} downloads failed")
            }

        } catch (e: Exception) {
            Log.e(TAG_BLOC, "Error auto-installing completed downloads", e)
            showError("Failed to auto-install apps: ${e.message}")
        }
    }
}
