package com.revanced.net.revancedmanager.presentation.bloc

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.revanced.net.revancedmanager.R
import com.revanced.net.revancedmanager.domain.model.AppStatus
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File

// ============= DOWNLOAD LOGIC =============

internal fun AppBloc.downloadApp(packageName: String, downloadUrl: String) {
    Log.i(TAG_BLOC, "Starting download: $packageName")

    cancelDownload(packageName)
    completedDownloads.remove(packageName)
    installationQueue.removeAll { it.packageName == packageName }

    val downloadJob = viewModelScope.launch {
        try {
            updateAppStatus(packageName, AppStatus.DOWNLOADING)
            updateAppProgress(packageName, 0f)
            showToast(stringProvider.getString(R.string.download_starting))

            simpleDownloadManager.downloadApp(packageName, downloadUrl)
                .catch { error ->
                    Log.e(TAG_BLOC, "Download failed for $packageName", error)
                    handleDownloadFailed(packageName, buildDownloadErrorMessage(error.message))
                }
                .onEach { download ->
                    updateAppProgress(packageName, download.progress)
                    if (download.isComplete && download.filePath != null) {
                        Log.i(TAG_BLOC, "Download completed for $packageName: ${download.filePath}")
                        handleDownloadCompleted(packageName, download.filePath)
                        return@onEach
                    }
                }
                .launchIn(this)

        } catch (e: Exception) {
            Log.e(TAG_BLOC, "Download error for $packageName", e)
            handleDownloadFailed(packageName, buildDownloadErrorMessage(e.message))
        }
    }

    activeDownloads[packageName] = downloadJob
}

internal fun AppBloc.handleDownloadCompleted(packageName: String, filePath: String) {
    if (completedDownloads.contains(packageName)) {
        Log.w(TAG_BLOC, "Download completion already handled for: $packageName")
        return
    }

    Log.i(TAG_BLOC, "Download completed, queueing for installation: $packageName -> $filePath")
    completedDownloads.add(packageName)
    activeDownloads.remove(packageName)

    val appName = (_state.value as? AppState.Success)
        ?.apps?.find { it.packageName == packageName }?.title ?: packageName

    queueInstallation(packageName, filePath, appName)
    showToast(stringProvider.getString(R.string.download_completed_installing))
}

internal fun AppBloc.handleDownloadFailed(packageName: String, error: String) {
    Log.e(TAG_BLOC, "Download failed: $packageName - $error")
    activeDownloads.remove(packageName)
    val restoredStatus = resolveActualStatus(packageName)
    updateAppStatus(packageName, restoredStatus)
    updateAppProgress(packageName, 0f)
    showError(error)
}

internal fun AppBloc.cancelDownload(packageName: String) {
    Log.i(TAG_BLOC, "Cancelling download: $packageName")
    activeDownloads[packageName]?.cancel()
    activeDownloads.remove(packageName)
    simpleDownloadManager.cancelDownload(packageName)

    val restoredStatus = resolveActualStatus(packageName)
    updateAppStatus(packageName, restoredStatus)
    updateAppProgress(packageName, 0f)

    if (restoredStatus != AppStatus.DOWNLOADING) {
        showToast(stringProvider.getString(R.string.download_cancelled))
    }
}

internal fun AppBloc.checkDownloadServiceProgress() {
    try {
        val activeMap = simpleDownloadManager.getActiveDownloads()
        Log.i(TAG_BLOC, "SimpleDownloadManager reports ${activeMap.size} active downloads")

        activeMap.forEach { (packageName, download) ->
            if (download.isComplete && download.filePath != null) {
                val currentStatus = (_state.value as? AppState.Success)
                    ?.apps?.find { it.packageName == packageName }?.status
                if (currentStatus == AppStatus.UP_TO_DATE ||
                    currentStatus == AppStatus.INSTALLING ||
                    installationQueue.any { it.packageName == packageName }
                ) {
                    Log.i(TAG_BLOC, "Skipping already handled package: $packageName (status=$currentStatus)")
                } else {
                    handleEvent(AppEvent.DownloadCompleted(packageName, download.filePath!!))
                }
            } else if (!download.isComplete) {
                updateAppProgress(packageName, download.progress)
                updateAppStatus(packageName, AppStatus.DOWNLOADING)
            }
        }
    } catch (e: Exception) {
        Log.w(TAG_BLOC, "Error checking download service progress", e)
    }
}

internal fun AppBloc.checkPendingDownloadsOnForeground() {
    Log.i(TAG_BLOC, "=== CHECKING PENDING DOWNLOADS ON FOREGROUND ===")

    viewModelScope.launch {
        try {
            checkDownloadServiceProgress()
            kotlinx.coroutines.delay(1000)

            val completed = downloadStateRepository.getCompletedDownloads()
            val active = downloadStateRepository.getActiveDownloads()
            Log.i(TAG_BLOC, "Found ${completed.size} completed, ${active.size} active downloads")

            if (completed.isNotEmpty()) {
                completed.forEach { dl ->
                    queueInstallation(dl.packageName, dl.filePath!!, dl.appName)
                }
            }

            active.forEach { dl ->
                val filePath = dl.filePath
                if (filePath != null && File(filePath).exists()) {
                    downloadStateRepository.markDownloadCompleted(dl.packageName, filePath)
                    queueInstallation(dl.packageName, filePath, dl.appName)
                } else {
                    // File missing — resolve actual device state, don't assume UPDATE_AVAILABLE
                    updateAppStatus(dl.packageName, resolveActualStatus(dl.packageName))
                    downloadStateRepository.removeDownloadState(dl.packageName)
                }
            }

            downloadStateRepository.cleanupOldFailedDownloads()

        } catch (e: Exception) {
            Log.e(TAG_BLOC, "Error checking pending downloads", e)
        }
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
