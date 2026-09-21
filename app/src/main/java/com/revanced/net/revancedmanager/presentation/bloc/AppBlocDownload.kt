package com.revanced.net.revancedmanager.presentation.bloc

import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.revanced.net.revancedmanager.R
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
            val currentStatus = (_state.value as? AppState.Success)
                ?.apps?.find { it.packageName == download.packageName }?.status
            if (currentStatus != null && currentStatus != AppStatus.DOWNLOADING) {
                updateAppStatus(download.packageName, AppStatus.DOWNLOADING)
            }
            updateAppProgress(download.packageName, download.progress)
        }

        // The side effects of a finished download — queueing the install, deleting the APK,
        // pruning the record, raising a toast — happen once, for whoever claims the work id.
        // Reconciling this instance's own card does not: see releaseDownloadingCard.
        WorkInfo.State.SUCCEEDED -> if (downloadManager.claimTerminal(download.id)) {
            val filePath = download.filePath
            val file = filePath?.let { File(it) }
            if (file != null && file.exists()) {
                handleDownloadCompleted(download.packageName, filePath, download.id.short())
            } else {
                handleDownloadFileMissing(download.packageName, filePath, download.id.short())
            }
        } else {
            releaseDownloadingCard(download.packageName)
        }

        WorkInfo.State.FAILED -> if (downloadManager.claimTerminal(download.id)) {
            Log.e(
                TAG_BLOC,
                "Download work ${download.id.short()} FAILED: ${download.packageName} " +
                    "raw=${download.error}"
            )
            handleDownloadFailed(download.packageName, buildDownloadErrorMessage(download.error))
        } else {
            releaseDownloadingCard(download.packageName)
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
            releaseDownloadingCard(download.packageName)
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
private fun AppBloc.releaseDownloadingCard(packageName: String) {
    if (packageName in pendingInstalls) return
    val currentStatus = (_state.value as? AppState.Success)
        ?.apps?.find { it.packageName == packageName }?.status
    if (currentStatus != AppStatus.DOWNLOADING) return

    Log.i(
        TAG_BLOC,
        "[$blocId] Releasing stale DOWNLOADING card for $packageName " +
            "(its work finished under another handler)"
    )
    updateAppStatus(packageName, resolveActualStatus(packageName))
    updateAppProgress(packageName, 0f)
}

internal fun AppBloc.downloadApp(packageName: String, downloadUrl: String) {
    Log.i(TAG_BLOC, "[$blocId] Starting download: $packageName")

    // Drop any install still queued for a stale APK of this package
    pendingInstalls.remove(packageName)

    // Remember that this one was asked for, so completing it installs even when the app is
    // already up to date — which is exactly what choosing another architecture means.
    downloadManager.markUserRequested(packageName)

    updateAppStatus(packageName, AppStatus.DOWNLOADING)
    updateAppProgress(packageName, 0f)
    showToast(stringProvider.getString(R.string.download_starting))

    val appName = (_state.value as? AppState.Success)
        ?.apps?.find { it.packageName == packageName }?.title ?: packageName
    downloadManager.download(packageName, downloadUrl, appName)
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
    appsToUpdate.forEach { downloadApp(it.packageName, it.downloadUrl) }
}

internal fun AppBloc.handleDownloadCompleted(
    packageName: String,
    filePath: String,
    workId: String = "?"
) {
    val app = (_state.value as? AppState.Success)?.apps?.find { it.packageName == packageName }
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
