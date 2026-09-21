package com.revanced.net.revancedmanager.presentation.bloc

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.revanced.net.revancedmanager.R
import com.revanced.net.revancedmanager.core.common.PackageOwnership
import com.revanced.net.revancedmanager.core.common.Result
import com.revanced.net.revancedmanager.data.manager.InstallationResult
import com.revanced.net.revancedmanager.domain.model.AppStatus
import com.revanced.net.revancedmanager.domain.model.InstallFailureReason
import com.revanced.net.revancedmanager.domain.model.InstallPreflight
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

// ============= INSTALLATION LOGIC =============
//
// Installations run strictly one at a time: requests go through the
// AppBloc.installRequests channel and a single processor coroutine drives each
// install to a terminal result before starting the next.

private const val INSTALL_RESULT_TIMEOUT_MS = 60_000L

/** Statuses that mean work is already under way, so a status refresh must not overwrite them. */
private val IN_FLIGHT_STATUSES = setOf(
    AppStatus.DOWNLOADING,
    AppStatus.READY_TO_INSTALL,
    AppStatus.INSTALLING,
    AppStatus.UNINSTALLING
)

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
    // Queued is not installing: with a sequential processor, only runInstallation may claim
    // INSTALLING — otherwise a 10-app update shows ten "Installing…" cards for one real install.
    updateAppStatus(packageName, AppStatus.READY_TO_INSTALL)
    installRequests.trySend(AppBloc.PendingInstallation(packageName, filePath, appName))
}

internal fun AppBloc.startInstallationProcessor() {
    viewModelScope.launch {
        for (request in installRequests) {
            try {
                // Dropped while queued: a fresh download replaced the APK, or the user cancelled
                // this app's place in the queue.
                if (pendingInstalls.contains(request.packageName)) {
                    runInstallation(request)
                }
            } finally {
                pendingInstalls.remove(request.packageName)
                // Queue drained — now is the time to put failure dialogs on screen, rather than
                // interrupting the run and having each one overwrite the last. Runs for skipped
                // requests too, so a cancelled tail of the queue still releases the dialogs.
                flushQueuedDialogs()
            }
        }
    }
}

private suspend fun AppBloc.runInstallation(request: AppBloc.PendingInstallation) = coroutineScope {
    val packageName = request.packageName
    Log.i(TAG_BLOC, "Starting installation: ${request.appName} ($packageName)")

    // Predictable failures (signature conflict, downgrade, wrong or damaged APK) are caught here,
    // before the session is committed: the user is asked once up front instead of after a
    // guaranteed-to-fail install.
    val preflight = withContext(Dispatchers.IO) { appManager.preflight(packageName, request.filePath) }
    if (preflight !is InstallPreflight.Ok) {
        Log.w(TAG_BLOC, "[$blocId] Preflight blocked $packageName: ${preflight::class.simpleName}")
        handleInstallPreflight(packageName, request.filePath, preflight)
        return@coroutineScope
    }
    Log.i(TAG_BLOC, "[$blocId] Preflight OK for $packageName, committing session")

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

        // Keep waiting past the timeout while either (a) the app is backgrounded — the system
        // confirm dialog is delivered as a notification and the user may take arbitrarily long —
        // or (b) the system still has our install session open: writing and verifying a large
        // APK on a slow device can exceed the timeout, and concluding "cancelled" then would
        // mislabel a working install and start the next one in parallel. A cancel (user or
        // system) closes the session and delivers ABORTED, which ends this loop.
        var result = withTimeoutOrNull(INSTALL_RESULT_TIMEOUT_MS) { terminalResult.await() }
        while (result == null && (wasAppBackgrounded || packageInstaller.hasActiveSessionFor(packageName))) {
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
                    appManager.getInstalledVersion(packageName) ?: "Unknown",
                    source = "PackageInstaller result"
                )
            is InstallationResult.Failed -> {
                val reason = InstallFailureReason.from(result.statusCode, result.error)
                if (reason == InstallFailureReason.ABORTED) handleInstallationAborted(packageName, result.error)
                else handleInstallationFailure(packageName, result.error, result.statusCode)
            }
            is InstallationResult.PendingUserAction -> Unit // filtered out above
        }
    } finally {
        terminalResult.cancel()
        pendingActionToast.cancel()
    }
}

/**
 * Kick off the install session. Returns an error message, or null when started.
 *
 * Refuses to open a second session while one is already live for this package. The sequential
 * processor makes that impossible within one AppBloc, so this is a backstop against the shape of
 * bug that produced four concurrent sessions for a single tap: several live AppBlocs, each with
 * its own install queue, all reacting to the same finished download. Three of those sessions were
 * left orphaned and surfaced later as spurious INSTALL_FAILED_ABORTED results.
 */
private suspend fun AppBloc.startInstallation(packageName: String, filePath: String): String? {
    if (packageInstaller.hasActiveSessionFor(packageName)) {
        Log.w(
            TAG_BLOC,
            "[$blocId] Install session already open for $packageName — not opening a second one"
        )
        return stringProvider.getString(R.string.installation_failed_start)
    }
    return try {
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
}

internal fun AppBloc.handleInstallationSuccess(
    packageName: String,
    installedVersion: String,
    source: String = "unspecified"
) {
    Log.i(
        TAG_BLOC,
        "[$blocId] Installation success delivered via $source: $packageName v$installedVersion"
    )
    // Cleanup first, unconditionally: an install can finish before the catalog list has loaded
    // (a download record replayed after process death does exactly this), and skipping cleanup
    // there used to leave the APK on disk and the work record alive to be replayed on every
    // launch. Everything here is idempotent, so the duplicate success delivery is harmless.
    preferencesManager.removeKey("pending_install_${packageName}")
    installationRetries.remove(packageName)
    pendingInstalls.remove(packageName)
    if (preferencesManager.isAutoDeleteApkEnabled()) {
        deleteDownloadedApk(packageName)
    }
    downloadManager.pruneFinishedWork()

    // UI update only when there is a list to update
    val currentState = _state.value as? AppState.Success ?: return
    // The entry that owns the build just installed — with two MicroG entries, the one whose
    // version band holds it, not the first one in the list.
    val app = PackageOwnership.ownerOf(
        currentState.apps.filter { it.packageName == packageName },
        installedVersion
    ) ?: return

    val newStatus = statusForVersions(installedVersion, app.latestVersion)
    // Success arrives via both PackageInstaller results and the system
    // PACKAGE_ADDED broadcast — skip the second delivery.
    if (app.status == newStatus && app.currentVersion == installedVersion) {
        Log.i(
            TAG_BLOC,
            "[$blocId] Installation success already handled for: $packageName (this delivery: $source)"
        )
        return
    }

    _state.value = currentState.copy(apps = settleEntries(currentState.apps, packageName))
    showToast(stringProvider.getString(R.string.installation_completed))
}

/**
 * Route an install failure to the recovery that actually fits its cause.
 *
 * Uninstall-and-retry is only offered when removing the old version can fix the install
 * (signature conflict, downgrade). For every other cause — no space, wrong ABI, damaged APK,
 * Play Protect — uninstalling would cost the user their app and data while the retry fails for
 * the same reason, so those get an explanation (and a re-download where that helps) instead.
 */
internal fun AppBloc.handleInstallationFailure(packageName: String, error: String, statusCode: Int = -1) {
    Log.w(TAG_BLOC, "[$blocId] Installation failed: $packageName - $error (status=$statusCode)")
    updateAppStatus(packageName, resolveActualStatus(packageName))
    downloadManager.pruneFinishedWork()

    val appName = appTitle(packageName)
    when (val reason = InstallFailureReason.from(statusCode, error)) {
        InstallFailureReason.ABORTED -> {
            installationRetries.remove(packageName)
            showToast(stringProvider.getString(R.string.installation_cancelled_by_user))
        }

        InstallFailureReason.SIGNATURE_CONFLICT,
        InstallFailureReason.VERSION_DOWNGRADE -> {
            // Retry is only possible while the downloaded APK still exists (it may have
            // been auto-deleted after a previous install)
            val apkPath = getDownloadPath(packageName)
            val retries = installationRetries[packageName] ?: 0
            if (apkPath != null && retries < 1) {
                confirmUninstallAndInstall(packageName, apkPath, reason)
            } else {
                installationRetries.remove(packageName)
                showError(stringProvider.getString(R.string.installation_failed, error))
            }
        }

        InstallFailureReason.STORAGE_FULL -> showInfoDialog(
            stringProvider.getString(R.string.installation_failed_title),
            stringProvider.getString(R.string.install_failed_storage_message, appName)
        )

        InstallFailureReason.INCOMPATIBLE -> showInfoDialog(
            stringProvider.getString(R.string.installation_failed_title),
            stringProvider.getString(R.string.install_failed_incompatible_message, appName)
        )

        InstallFailureReason.BLOCKED -> showInfoDialog(
            stringProvider.getString(R.string.installation_failed_title),
            stringProvider.getString(R.string.install_failed_blocked_message, appName)
        )

        InstallFailureReason.INVALID_APK -> {
            deleteDownloadedApk(packageName)
            offerRedownload(
                packageName,
                stringProvider.getString(R.string.install_failed_invalid_message, appName)
            )
        }

        InstallFailureReason.UNKNOWN -> offerRedownload(
            packageName,
            stringProvider.getString(R.string.install_failed_generic_message, appName, error)
        )
    }
}

/**
 * Act on a failed preflight check — same decisions as a real install failure, minus the failed
 * install: the doomed session is never committed and the user never sees the pointless system
 * dialog for it.
 */
private fun AppBloc.handleInstallPreflight(
    packageName: String,
    apkFilePath: String,
    preflight: InstallPreflight
) {
    updateAppStatus(packageName, resolveActualStatus(packageName))
    when (preflight) {
        is InstallPreflight.Ok -> Unit

        is InstallPreflight.RequiresUninstall ->
            confirmUninstallAndInstall(packageName, apkFilePath, preflight.reason)

        is InstallPreflight.PackageMismatch -> {
            Log.e(TAG_BLOC, "APK on disk is ${preflight.apkPackageName}, expected $packageName — blocked")
            showInfoDialog(
                stringProvider.getString(R.string.installation_failed_title),
                stringProvider.getString(
                    R.string.install_wrong_package_message,
                    appTitle(packageName),
                    preflight.apkPackageName
                )
            )
        }

        is InstallPreflight.ApkUnreadable -> {
            deleteDownloadedApk(packageName)
            offerRedownload(
                packageName,
                stringProvider.getString(R.string.install_failed_invalid_message, appTitle(packageName))
            )
        }
    }
}

/**
 * The one dialog allowed to lead to an uninstall. It spells out that the app's data is lost for
 * good, and its confirm button says what it does instead of a generic "Confirm".
 */
private fun AppBloc.confirmUninstallAndInstall(
    packageName: String,
    apkFilePath: String,
    reason: InstallFailureReason
) {
    val messageRes = when (reason) {
        InstallFailureReason.VERSION_DOWNGRADE -> R.string.install_failed_downgrade_message
        else -> R.string.install_failed_signature_message
    }
    enqueueConfirmationDialog(
        title = stringProvider.getString(R.string.uninstall_required_title),
        message = stringProvider.getString(messageRes, appTitle(packageName)),
        onConfirm = AppEvent.RetryInstallation(packageName, apkFilePath, shouldUninstallFirst = true),
        // Use the real status so cancelling the dialog doesn't overwrite with wrong status
        onCancel = AppEvent.DismissDialogAndUpdateStatus(packageName, resolveActualStatus(packageName)),
        confirmLabel = stringProvider.getString(R.string.uninstall_and_reinstall),
        destructive = true
    )
}

/** Offer to download the app again; falls back to a plain info dialog when the catalog entry is gone. */
private fun AppBloc.offerRedownload(packageName: String, message: String) {
    val app = (_state.value as? AppState.Success)?.apps?.find { it.packageName == packageName }
    if (app != null) {
        enqueueConfirmationDialog(
            title = stringProvider.getString(R.string.installation_failed_title),
            message = message,
            onConfirm = AppEvent.DownloadApp(packageName, app.downloadUrl),
            onCancel = AppEvent.DismissDialog,
            confirmLabel = stringProvider.getString(R.string.download_again)
        )
    } else {
        showInfoDialog(stringProvider.getString(R.string.installation_failed_title), message)
    }
}

private fun AppBloc.appTitle(packageName: String): String =
    (_state.value as? AppState.Success)?.apps?.find { it.packageName == packageName }?.title
        ?: packageName

/**
 * The way out of an install that never finishes (plan-08-14.md §2.5).
 *
 * Because installs run strictly one at a time, one wedged install froze every other app's install
 * behind it with no way to intervene. Cancelling abandons the system session — which makes the
 * waiting processor observe a terminal result and move on — or, for an app still queued, simply
 * drops it from the queue.
 */
internal fun AppBloc.cancelInstallation(packageName: String) {
    Log.i(TAG_BLOC, "User cancelled installation: $packageName")
    val hadSession = packageInstaller.cancelInstallation(packageName)
    if (!hadSession) {
        // Still queued behind another install: dropping it from pendingInstalls makes the
        // processor skip the request when it reaches it.
        pendingInstalls.remove(packageName)
        installationRetries.remove(packageName)
        updateAppStatus(packageName, resolveActualStatus(packageName))
        flushQueuedDialogs()
    }
    showToast(stringProvider.getString(R.string.installation_cancelled_by_user))
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
            // In-flight statuses are shared by every entry of the package, so any of them will do.
            val appToUpdate = currentState.apps.find { it.packageName == packageName } ?: return

            if (appToUpdate.status in IN_FLIGHT_STATUSES) {
                if (appToUpdate.status == AppStatus.UNINSTALLING) {
                    if (!appManager.isAppInstalled(packageName)) {
                        _state.value = currentState.copy(apps = settleEntries(currentState.apps, packageName))
                        showToast(stringProvider.getString(R.string.uninstallation_completed))
                    }
                }
                return
            }

            val settledApps = settleEntries(currentState.apps, packageName)
            val changed = settledApps.zip(currentState.apps).any { (after, before) ->
                after.packageName == packageName && after.status != before.status
            }
            if (changed) {
                _state.value = currentState.copy(apps = settledApps)
                showToast(stringProvider.getString(R.string.app_status_updated))
            }
        }
    } catch (e: Exception) {
        loadApps(forceRefresh = true)
    }
}

internal fun AppBloc.retryInstallation(packageName: String, apkFilePath: String, shouldUninstallFirst: Boolean) {
    dismissDialog()

    if (!shouldUninstallFirst) {
        installApp(packageName, apkFilePath)
        return
    }

    viewModelScope.launch {
        // The user just confirmed a destructive retry — count it now, not when the dialog was
        // built, so merely seeing (and cancelling) the dialog doesn't burn the retry.
        installationRetries[packageName] = (installationRetries[packageName] ?: 0) + 1

        // The dialog may have been open for a long time (auto-delete or "Clear APK cache" can
        // remove the file meanwhile) — re-verify the APK is still present and the uninstall is
        // still what's needed BEFORE removing the user's app. Never uninstall when the follow-up
        // install is already known to fail.
        val preflight = withContext(Dispatchers.IO) {
            if (!File(apkFilePath).exists()) null
            else appManager.preflight(packageName, apkFilePath)
        }
        when (preflight) {
            null, is InstallPreflight.ApkUnreadable -> {
                if (preflight != null) deleteDownloadedApk(packageName)
                updateAppStatus(packageName, resolveActualStatus(packageName))
                val messageRes =
                    if (preflight == null) R.string.install_apk_missing_message
                    else R.string.install_failed_invalid_message
                offerRedownload(packageName, stringProvider.getString(messageRes, appTitle(packageName)))
                return@launch
            }
            is InstallPreflight.PackageMismatch -> {
                updateAppStatus(packageName, resolveActualStatus(packageName))
                showInfoDialog(
                    stringProvider.getString(R.string.installation_failed_title),
                    stringProvider.getString(
                        R.string.install_wrong_package_message,
                        appTitle(packageName),
                        preflight.apkPackageName
                    )
                )
                return@launch
            }
            is InstallPreflight.Ok -> {
                // Nothing blocks the install anymore (or the app is already gone) — install
                // directly and keep the user's data.
                installApp(packageName, apkFilePath)
                return@launch
            }
            is InstallPreflight.RequiresUninstall -> Unit // proceed with the confirmed uninstall
        }

        try {
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
                        updateSingleAppStatus(packageName)
                    }
                }
                is Result.Error -> {
                    clearPendingReinstall(packageName)
                    showError(stringProvider.getString(R.string.failed_uninstall_old_version_error, uninstallResult.message))
                    updateSingleAppStatus(packageName)
                }
                is Result.Loading -> Unit
            }
        } catch (e: Exception) {
            clearPendingReinstall(packageName)
            showError(stringProvider.getString(R.string.failed_uninstall_old_version_error, e.message ?: ""))
            updateSingleAppStatus(packageName)
        }
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

    // Download first: nothing on the device is touched until the fresh APK is on disk and has
    // passed preflight, so a lost connection, a 404, or a cancelled download can never leave the
    // user without a working app. From there it is an ordinary install — the app is updated in
    // place and keeps its data; the installed version is only removed when the user says so in
    // the dialog runInstallation raises for a signature clash or a downgrade.
    showToast(stringProvider.getString(R.string.reinstall_started))
    downloadApp(packageName, app.downloadUrl)
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
