package com.revanced.net.revancedmanager.domain.model

/**
 * Domain model representing a ReVanced application
 */
data class RevancedApp(
    val packageName: String,
    val title: String,
    val latestVersion: String,
    val currentVersion: String?,
    val description: String,
    val iconUrl: String,
    val downloadUrl: String,
    val requiresMicroG: Boolean,
    val index: Int,
    val status: AppStatus,
    val downloadProgress: Float = 0f,
    val isFavorite: Boolean = false
)

/**
 * Enum representing the installation status of an app
 */
enum class AppStatus {
    NOT_INSTALLED,
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    DOWNLOADING,
    INSTALLING,
    UNINSTALLING,
    READY_TO_INSTALL,
    UNKNOWN
}

/**
 * Data class for app download information
 */
data class AppDownload(
    val packageName: String,
    val url: String,
    val filePath: String? = null,
    val progress: Float = 0f,
    val isComplete: Boolean = false
) 