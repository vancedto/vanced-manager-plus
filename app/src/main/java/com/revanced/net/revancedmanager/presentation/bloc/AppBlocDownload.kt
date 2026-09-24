package com.revanced.net.revancedmanager.presentation.bloc

import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.revanced.net.revancedmanager.R
import com.revanced.net.revancedmanager.config.Config
import com.revanced.net.revancedmanager.core.common.InFlightAttribution
import com.revanced.net.revancedmanager.core.common.MicroGRequirement
import com.revanced.net.revancedmanager.data.manager.DownloadState
import com.revanced.net.revancedmanager.data.manager.short
import com.revanced.net.revancedmanager.domain.model.AppStatus
import com.revanced.net.revancedmanager.domain.model.RevancedApp
import com.revanced.net.revancedmanager.domain.model.visibleFor
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File

// ============= DOWNLOAD LOGIC =============
//
// Downloads are WorkManager work. The single collector below is the only
// signal path: progress, completion, failure, and cancellation all arrive as
// WorkInfo updates — including work that survived a process death or reboot.
//
// Terminal states are claimed through downloadManager.claimTerminal, which is process-wide.
// It has to be: the guard used to be a ViewModel field, and when more than one AppBloc was alive
// each one claimed the same finished download separately — one deleted the APK as a replay while
// the others, the visible one included, then found no file and left the card stuck at 98 %.

internal fun AppBloc.observeDownloads() {
    downloadManager.downloads
        .onEach { downloads -> downloads.forEach { handleDownloadUpdate(it) } }
        .launchIn(viewModelScope)
}

private fun AppBloc.handleDownloadUpdate(download: DownloadState) {
    when (download.state) {
        WorkInfo.State.ENQUEUED,
        WorkInfo.State.BLOCKED,
        WorkInfo.State.RUNNING -> {
            download.appId?.let { inFlightInitiators[download.packageName] = it }
            val currentStatus = inFlightEntry(download.packageName)?.status
            if (currentStatus != null && currentStatus != AppStatus.DOWNLOADING) {
                updateAppStatus(download.packageName, AppStatus.DOWNLOADING)
            }
            updateAppProgress(download.packageName, download.progress)
        }

        // The side effects of a finished download — queueing the install, deleting the APK,
        // pruning the record, raising a toast — happen once, for whoever claims the work id.
        // Reconciling this instance's own card does not: see releaseDownloadingCard.
        WorkInfo.State.SUCCEEDED -> if (downloadManager.claimTerminal(download.id)) {
            // Seed here as well as on RUNNING: a download that finished while the process was dead
            // is replayed straight to SUCCEEDED, and the install it queues needs to know whose it is.
            download.appId?.let { inFlightInitiators[download.packageName] = it }
            val filePath = download.filePath
            val file = filePath?.let { File(it) }
            if (file != null && file.exists()) {
                handleDownloadCompleted(download.packageName, filePath, download.id.short())
            } else {
                handleDownloadFileMissing(download.packageName, filePath, download.id.short())
            }
        } else {
            releaseDownloadingCard(download.packageName, download.appId)
        }

        WorkInfo.State.FAILED -> if (downloadManager.claimTerminal(download.id)) {
            Log.e(
                TAG_BLOC,
                "Download work ${download.id.short()} FAILED: ${download.packageName} " +
                    "raw=${download.error}"
            )
            handleDownloadFailed(download.packageName, buildDownloadErrorMessage(download.error))
        } else {
            releaseDownloadingCard(download.packageName, download.appId)
        }

        WorkInfo.State.CANCELLED -> {
            // Feedback is handled where the cancel was requested (cancelDownload
            // or a REPLACE re-enqueue) — nothing to do here beyond leaving a trace, so a
            // cancellation is not mistaken for a download that vanished without explanation.
            if (downloadManager.claimTerminal(download.id)) {
                Log.i(
                    TAG_BLOC,
                    "Download work ${download.id.short()} CANCELLED: ${download.packageName} " +
                        "(feedback handled at the cancel site)"
                )
            }
            releaseDownloadingCard(download.packageName, download.appId)
        }
    }
}

/**
 * Take a card out of DOWNLOADING when this instance is not the one handling the finished work.
 *
 * DOWNLOADING is the one status a finished download can leave dangling with nothing left to
 * complete it, and that dangling card — frozen at 98 % with only Cancel to escape — was the whole
 * bug. Claiming the work id decides who runs the side effects; it must not also decide whose card
 * gets unstuck, or a handler in another instance leaves the visible one hanging.
 *
 * Deliberately narrow. A repeat emission of the same finished WorkInfo also lands here, and by
 * then this instance may have legitimately moved on to READY_TO_INSTALL or INSTALLING — statuses
 * an install is still driving, which must not be reset out from under it.
 */
private fun AppBloc.releaseDownloadingCard(packageName: String, appId: String?) {
    if (packageName in pendingInstalls) return
    // A CANCELLED emission for work that REPLACE superseded arrives after the replacement has
    // already claimed the package. Releasing then would take the card of the download that is
    // genuinely running.
    if (appId != null && inFlightInitiators[packageName]?.let { it != appId } == true) return
    val currentStatus = inFlightEntry(packageName)?.status
    if (currentStatus != AppStatus.DOWNLOADING) return

    Log.i(
        TAG_BLOC,
        "[$blocId] Releasing stale DOWNLOADING card for $packageName " +
            "(its work finished under another handler)"
    )
    updateAppStatus(packageName, resolveActualStatus(packageName))
    updateAppProgress(packageName, 0f)
}

// ============= DOWNLOAD GATE =============
//
// Two things used to go wrong only after the download had finished, when the user had long
// stopped watching: the install bounced off the "Install unknown apps" permission halfway through
// a batch, and an app that needs MicroG installed fine and then failed at the Google sign-in.
// Both are asked about here, before anything is fetched — once per batch, not once per app.

/** A download the user asked for by pressing a button: goes through the gate. */
internal fun AppBloc.requestDownload(appId: String, packageName: String, downloadUrl: String) {
    // "Download again" in an install-failure dialog arrives here, and nothing else closed that
    // dialog — it stayed up over the download it had just started. From a card or the detail
    // screen there is no dialog open (it would be modal), so this is a no-op there.
    dismissDialog()
    val app = (_state.value as? AppState.Success)?.apps?.find { it.id == appId }
    if (app == null) {
        // Entry gone from the list (a refresh dropped it): nothing to ask about, just fetch it.
        downloadApp(appId, packageName, downloadUrl)
        return
    }
    requestDownloads(listOf(app))
}

/**
 * Start downloads for [apps] once nothing stands in their way.
 *
 * [permissionChecked] and [microGAnswered] say which questions are already behind this batch, so
 * a batch resumed after one question is not asked it again.
 */
internal fun AppBloc.requestDownloads(
    apps: List<RevancedApp>,
    permissionChecked: Boolean = false,
    microGAnswered: Boolean = false
) {
    if (apps.isEmpty()) return

    if (!permissionChecked && !appManager.canInstallPackages()) {
        Log.i(TAG_BLOC, "[$blocId] Install permission missing — holding ${apps.size} download(s)")
        downloadsAwaitingPermission = (downloadsAwaitingPermission + apps).distinctBy { it.id }
        showConfirmationDialog(
            title = stringProvider.getString(R.string.install_permission_title),
            message = stringProvider.getString(R.string.install_permission_message),
            onConfirm = AppEvent.InstallPermissionAnswer(openSettings = true),
            onCancel = AppEvent.InstallPermissionAnswer(openSettings = false),
            confirmLabel = stringProvider.getString(R.string.install_permission_open)
        )
        return
    }

    if (!microGAnswered) {
        val catalog = (_state.value as? AppState.Success)?.apps.orEmpty()
        val microG = MicroGRequirement.preferredEntry(catalog)
        val microGPresent = appManager.isAppInstalled(Config.MICROG_PACKAGE) ||
            catalog.any {
                it.packageName == Config.MICROG_PACKAGE && it.status in InFlightAttribution.IN_FLIGHT_STATUSES
            }
        if (microG != null && MicroGRequirement.shouldOffer(apps, microGPresent)) {
            val needing = apps.first { it.requiresMicroG }
            val ids = apps.map { it.id }
            Log.i(TAG_BLOC, "[$blocId] ${needing.packageName} needs MicroG, none installed — offering ${microG.id}")
            showConfirmationDialog(
                title = stringProvider.getString(R.string.microg_required_title),
                message = stringProvider.getString(R.string.microg_required_message, needing.title, microG.title),
                // MicroG first, so its download starts ahead of the app that needs it
                onConfirm = AppEvent.StartDownloads(listOf(microG.id) + ids),
                // Declining — or tapping outside — still fetches what the user actually asked for
                onCancel = AppEvent.StartDownloads(ids),
                confirmLabel = stringProvider.getString(R.string.microg_install_both),
                cancelLabel = stringProvider.getString(R.string.microg_skip)
            )
            return
        }
    }

    apps.forEach { downloadApp(it.id, it.packageName, it.downloadUrl) }
}

/** The MicroG dialog's answer: the batch to fetch, with MicroG leading it if it was accepted. */
internal fun AppBloc.startDownloads(appIds: List<String>) {
    dismissDialog()
    val catalog = (_state.value as? AppState.Success)?.apps.orEmpty()
    val apps = appIds.mapNotNull { id -> catalog.find { it.id == id } }
    requestDownloads(apps, permissionChecked = true, microGAnswered = true)
}

internal fun AppBloc.answerInstallPermission(openSettings: Boolean) {
    dismissDialog()
    if (!openSettings) {
        Log.i(TAG_BLOC, "[$blocId] Install permission declined — dropping held downloads")
        downloadsAwaitingPermission = emptyList()
        return
    }
    if (appManager.openInstallPermissionSettings()) {
        // Picked up in resumeDownloadsAwaitingPermission when the user comes back
        installPermissionScreenOpened = true
    } else {
        // No settings screen on this device: let the install ask for the permission itself
        val held = downloadsAwaitingPermission
        downloadsAwaitingPermission = emptyList()
        requestDownloads(held, permissionChecked = true)
    }
}

/** Back from the permission screen: fetch what was held if the permission was granted. */
internal fun AppBloc.resumeDownloadsAwaitingPermission() {
    if (!installPermissionScreenOpened) return
    installPermissionScreenOpened = false
    val held = downloadsAwaitingPermission
    downloadsAwaitingPermission = emptyList()
    if (held.isEmpty()) return

    if (appManager.canInstallPackages()) {
        Log.i(TAG_BLOC, "[$blocId] Install permission granted — resuming ${held.size} download(s)")
        requestDownloads(held, permissionChecked = true)
    } else {
        Log.i(TAG_BLOC, "[$blocId] Returned without the install permission — ${held.size} download(s) dropped")
        showToast(stringProvider.getString(R.string.install_permission_denied))
    }
}

/**
 * Start (or restart) the download of [packageName], on behalf of catalog entry [appId].
 *
 * [appId] is what puts the spinner on the row the user pressed and nowhere else. The download
 * itself stays per package — one unique work name, one APK path — because the device has one
 * install slot for it.
 */
internal fun AppBloc.downloadApp(appId: String, packageName: String, downloadUrl: String) {
    Log.i(TAG_BLOC, "[$blocId] Starting download: $packageName (entry $appId)")

    // A different entry of this package may be mid-download; the REPLACE below is about to cancel
    // its work, so hand its card back to whatever is really installed before claiming the package.
    val previous = inFlightInitiators[packageName]
    if (previous != null && previous != appId) {
        Log.i(TAG_BLOC, "[$blocId] Entry $appId supersedes $previous for $packageName")
        val currentState = _state.value
        if (currentState is AppState.Success) {
            _state.value = currentState.copy(apps = settleEntries(currentState.apps, packageName))
        }
    }
    inFlightInitiators[packageName] = appId

    // Drop any install still queued for a stale APK of this package
    pendingInstalls.remove(packageName)

    // Remember that this one was asked for, so completing it installs even when the app is
    // already up to date — which is exactly what choosing another architecture means.
    downloadManager.markUserRequested(packageName)

    updateAppStatus(packageName, AppStatus.DOWNLOADING)
    updateAppProgress(packageName, 0f)
    showToast(stringProvider.getString(R.string.download_starting))

    val appName = (_state.value as? AppState.Success)
        ?.apps?.find { it.id == appId }?.title ?: packageName
    downloadManager.download(appId, packageName, downloadUrl, appName)
}

/**
 * Download every app that has an update available, skipping the ones the user muted in the detail
 * screen and the ones hidden by the source choice — an app kept out of the prompt and the
 * notification should not be updated by the notification's "Update all" action either.
 */
internal fun AppBloc.updateAllApps() {
    val state = _state.value as? AppState.Success ?: return
    val appsToUpdate = state.apps.visibleFor(state.config)
        .filter { it.status == AppStatus.UPDATE_AVAILABLE && it.updatePromptEnabled }
    updateApps(appsToUpdate)
}

/** Download the entries the user ticked in the update prompt. */
internal fun AppBloc.updateSelectedApps(appIds: List<String>) {
    val appsToUpdate = (_state.value as? AppState.Success)
        ?.apps?.filter { it.id in appIds && it.status == AppStatus.UPDATE_AVAILABLE }
        ?: return
    updateApps(appsToUpdate)
}

/**
 * Downloads run in parallel through WorkManager; completed downloads feed the sequential install
 * queue, so no extra coordination is needed here.
 */
private fun AppBloc.updateApps(appsToUpdate: List<RevancedApp>) {
    if (appsToUpdate.isEmpty()) return

    Log.i(TAG_BLOC, "Updating ${appsToUpdate.size} app(s)")
    showToast(stringProvider.getString(R.string.update_all_started, appsToUpdate.size))
    requestDownloads(appsToUpdate)
}

internal fun AppBloc.handleDownloadCompleted(
    packageName: String,
    filePath: String,
    workId: String = "?"
) {
    val app = inFlightEntry(packageName)
    val wasRequestedByUser = downloadManager.consumeUserRequested(packageName)
    val alreadyInstalled = appManager.isApkAlreadyInstalled(filePath)
    val apkSize = runCatching { File(filePath).length() }.getOrDefault(-1L)

    // Work records replayed after a restart can point at an app that has since been installed —
    // don't install the same build again. A download the user just started is never a replay, and
    // skipping it there would silently swallow a deliberate choice of a different architecture for
    // an app that is already up to date.
    //
    // The question is put to the APK file, not to the app list, because the list is loaded
    // asynchronously and the replay usually arrives first: a state-based check would see no app at
    // all and let the install through. That is what made the manager re-offer its own installer
    // after updating itself — the self-install kills the process before the record is pruned, so
    // the leftover download was replayed on every launch.
    // null means the file is not a package PackageManager can read, so PackageInstaller would
    // reject it too — treat it like nothing left to install rather than committing a doomed session.
    if (!wasRequestedByUser && alreadyInstalled != false) {
        Log.i(
            TAG_BLOC,
            "[$blocId] Replayed download installs nothing new, skipping: $packageName " +
                "work=$workId wasRequestedByUser=false " +
                "isApkAlreadyInstalled=${alreadyInstalled ?: "null (APK unreadable)"} " +
                "apkSize=$apkSize"
        )
        finishReplayedDownload(packageName)
        return
    }

    Log.i(
        TAG_BLOC,
        "[$blocId] Download completed, queueing for installation: $packageName -> $filePath " +
            "work=$workId wasRequestedByUser=$wasRequestedByUser " +
            "isApkAlreadyInstalled=${alreadyInstalled ?: "null"} apkSize=$apkSize"
    )
    queueInstallation(packageName, filePath, app?.title ?: packageName)
    showToast(stringProvider.getString(R.string.download_completed_installing))
}

/**
 * A download WorkManager reports as SUCCEEDED whose APK is not on disk.
 *
 * This used to be a bare log-and-return, which is what actually froze the UI: the card kept
 * AppStatus.DOWNLOADING and its last progress value (98 %) forever, with no way out but Cancel.
 * Whatever the cause, the card has to go back to a real status — an in-flight status that nothing
 * will ever complete is worse than an error.
 */
private fun AppBloc.handleDownloadFileMissing(
    packageName: String,
    filePath: String?,
    workId: String
) {
    val wasRequestedByUser = downloadManager.consumeUserRequested(packageName)
    Log.w(
        TAG_BLOC,
        "[$blocId] Completed download has no file, dropping: $packageName work=$workId " +
            "filePath=${filePath ?: "<none reported>"} wasRequestedByUser=$wasRequestedByUser"
    )

    updateAppStatus(packageName, resolveActualStatus(packageName))
    updateAppProgress(packageName, 0f)
    downloadManager.pruneFinishedWork()

    // Only worth interrupting the user when they were waiting on this particular download.
    // A replayed record losing its file is routine cleanup, not something to report.
    if (wasRequestedByUser) {
        showError(stringProvider.getString(R.string.download_file_missing))
    }
}

/**
 * Clean up after a download whose install is not going to happen: drop the work record so it is
 * not replayed again, and drop the APK the successful install would have removed itself.
 *
 * Both are normally done by handleInstallationSuccess, which never runs when the package being
 * installed is this app — the system kills the process the moment the update commits.
 */
private fun AppBloc.finishReplayedDownload(packageName: String) {
    downloadManager.pruneFinishedWork()
    if (preferencesManager.isAutoDeleteApkEnabled()) {
        deleteDownloadedApk(packageName)
    }
    updateAppStatus(packageName, resolveActualStatus(packageName))
    // Without this the card keeps the progress value the abandoned download reached.
    updateAppProgress(packageName, 0f)
}

internal fun AppBloc.handleDownloadFailed(packageName: String, error: String) {
    Log.e(TAG_BLOC, "[$blocId] Download failed: $packageName - $error")
    downloadManager.clearUserRequested(packageName)
    updateAppStatus(packageName, resolveActualStatus(packageName))
    updateAppProgress(packageName, 0f)
    showError(error)
    downloadManager.pruneFinishedWork()
}

internal fun AppBloc.cancelDownload(packageName: String) {
    Log.i(TAG_BLOC, "[$blocId] Cancelling download: $packageName")
    downloadManager.clearUserRequested(packageName)
    downloadManager.cancel(packageName)

    val restoredStatus = resolveActualStatus(packageName)
    updateAppStatus(packageName, restoredStatus)
    updateAppProgress(packageName, 0f)

    if (restoredStatus != AppStatus.DOWNLOADING) {
        showToast(stringProvider.getString(R.string.download_cancelled))
    }
}

private fun AppBloc.buildDownloadErrorMessage(rawMessage: String?): String = when {
    rawMessage?.contains("insufficient memory", ignoreCase = true) == true ||
    rawMessage?.contains("OutOfMemoryError", ignoreCase = true) == true ->
        stringProvider.getString(R.string.download_failed_memory)
    rawMessage?.contains("space", ignoreCase = true) == true ->
        stringProvider.getString(R.string.download_failed_storage)
    else ->
        stringProvider.getString(R.string.download_failed, rawMessage ?: "Unknown error")
}
