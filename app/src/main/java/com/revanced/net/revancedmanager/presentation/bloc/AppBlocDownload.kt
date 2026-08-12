package com.revanced.net.revancedmanager.presentation.bloc

import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.revanced.net.revancedmanager.R
import com.revanced.net.revancedmanager.data.manager.DownloadState
import com.revanced.net.revancedmanager.domain.model.AppStatus
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File

// ============= DOWNLOAD LOGIC =============
//
// Downloads are WorkManager work. The single collector below is the only
// signal path: progress, completion, failure, and cancellation all arrive as
// WorkInfo updates — including work that survived a process death or reboot.

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

        WorkInfo.State.SUCCEEDED -> if (handledDownloads.add(download.id)) {
            val filePath = download.filePath
            if (filePath != null && File(filePath).exists()) {
                handleDownloadCompleted(download.packageName, filePath)
            } else {
                Log.w(TAG_BLOC, "Completed download has no file, dropping: ${download.packageName}")
                downloadManager.pruneFinishedWork()
            }
        }

        WorkInfo.State.FAILED -> if (handledDownloads.add(download.id)) {
            handleDownloadFailed(download.packageName, buildDownloadErrorMessage(download.error))
        }

        WorkInfo.State.CANCELLED -> if (handledDownloads.add(download.id)) {
            // Feedback is handled where the cancel was requested (cancelDownload
            // or a REPLACE re-enqueue) — nothing to do here.
        }
    }
}

internal fun AppBloc.downloadApp(packageName: String, downloadUrl: String) {
    Log.i(TAG_BLOC, "Starting download: $packageName")

    // Drop any install still queued for a stale APK of this package
    pendingInstalls.remove(packageName)

    // Remember that this one was asked for, so completing it installs even when the app is
    // already up to date — which is exactly what choosing another architecture means.
    userRequestedDownloads.add(packageName)

    updateAppStatus(packageName, AppStatus.DOWNLOADING)
    updateAppProgress(packageName, 0f)
    showToast(stringProvider.getString(R.string.download_starting))

    val appName = (_state.value as? AppState.Success)
        ?.apps?.find { it.packageName == packageName }?.title ?: packageName
    downloadManager.download(packageName, downloadUrl, appName)
}

/**
 * Download every app that has an update available. Downloads run in parallel
 * through WorkManager; completed downloads feed the sequential install queue,
 * so no extra coordination is needed here.
 */
internal fun AppBloc.updateAllApps() {
    val appsToUpdate = (_state.value as? AppState.Success)
        ?.apps?.filter { it.status == AppStatus.UPDATE_AVAILABLE }
        ?: return
    if (appsToUpdate.isEmpty()) return

    Log.i(TAG_BLOC, "Updating all apps: ${appsToUpdate.size} update(s)")
    showToast(stringProvider.getString(R.string.update_all_started, appsToUpdate.size))
    appsToUpdate.forEach { downloadApp(it.packageName, it.downloadUrl) }
}

internal fun AppBloc.handleDownloadCompleted(packageName: String, filePath: String) {
    val app = (_state.value as? AppState.Success)?.apps?.find { it.packageName == packageName }
    val wasRequestedByUser = userRequestedDownloads.remove(packageName)

    // Work records replayed after a restart can point at an app that has since
    // been installed — don't install the same version again. A download the user just started is
    // never a replay, and skipping it there would silently swallow a deliberate choice of a
    // different architecture for an app that is already up to date.
    if (!wasRequestedByUser && app != null && resolveActualStatus(packageName) == AppStatus.UP_TO_DATE) {
        Log.i(TAG_BLOC, "Replayed download for an up-to-date app, skipping install: $packageName")
        downloadManager.pruneFinishedWork()
        return
    }

    Log.i(TAG_BLOC, "Download completed, queueing for installation: $packageName -> $filePath")
    queueInstallation(packageName, filePath, app?.title ?: packageName)
    showToast(stringProvider.getString(R.string.download_completed_installing))
}

internal fun AppBloc.handleDownloadFailed(packageName: String, error: String) {
    Log.e(TAG_BLOC, "Download failed: $packageName - $error")
    userRequestedDownloads.remove(packageName)
    updateAppStatus(packageName, resolveActualStatus(packageName))
    updateAppProgress(packageName, 0f)
    showError(error)
    downloadManager.pruneFinishedWork()
}

internal fun AppBloc.cancelDownload(packageName: String) {
    Log.i(TAG_BLOC, "Cancelling download: $packageName")
    userRequestedDownloads.remove(packageName)
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
