package com.revanced.net.revancedmanager.core.common

import com.revanced.net.revancedmanager.domain.model.RevancedApp
import kotlinx.serialization.Serializable
import java.time.Instant

/** What PackageManager says is installed: the versionName and when the APK last changed. */
data class InstalledBuild(
    val versionName: String,
    /** PackageInfo.lastUpdateTime, epoch millis; 0 when unknown. */
    val lastUpdateTime: Long = 0L
)

/**
 * What this manager installed for a package: written when its own install succeeds, so the patch
 * version of the build on the device is known exactly rather than guessed.
 *
 * Only trusted while [versionName] and [lastUpdateTime] still match the installed package — any
 * other install (a browser download, another store) changes lastUpdateTime and retires it.
 */
@Serializable
data class InstalledBuildRecord(
    val versionName: String,
    val patchVersion: String,
    val lastUpdateTime: Long
)

/**
 * Whether a newer build of the *same app version* is published — a patch-only update.
 *
 * The CICD rebuilds an app whose app version did not change when its provider runs with
 * `updateMode: patches` (morphe, revanced-rebrand, anddea): same versionName, same versionCode,
 * newer patches. Comparing versionName alone never offered those builds, so a YouTube installed
 * on patches 1.39 stayed there until YouTube itself moved.
 *
 * The installed APK does not carry its patch version, so it comes from one of two places:
 * - **A record** this manager wrote at install time ([InstalledBuildRecord]). Exact.
 * - **The install time**, when there is no valid record (installed before this existed, or from the
 *   website). An APK installed before the current build was published cannot be that build, and
 *   for the same app version the only thing that makes the server publish again is a newer patch.
 *   [PUBLISH_MARGIN_MS] absorbs clock skew and the gap between building and uploading.
 */
object PatchUpdate {

    const val PUBLISH_MARGIN_MS: Long = 2 * 60 * 60 * 1000L

    /**
     * @param app a catalog entry whose [RevancedApp.latestVersion], [RevancedApp.patchVersion] and
     *   [RevancedApp.updatedAt] describe the build chosen for this device.
     * @param installed what is installed as [RevancedApp.packageName].
     * @param record what this manager recorded installing there, if anything.
     */
    fun hasNewerPatch(app: RevancedApp, installed: InstalledBuild, record: InstalledBuildRecord?): Boolean {
        val latestPatch = app.patchVersion?.takeIf { it.isNotBlank() } ?: return false
        if (installed.versionName.isBlank() || app.latestVersion.isBlank()) return false
        // A different app version is the ordinary update path's business (or a newer install).
        if (VersionComparator.compare(installed.versionName, app.latestVersion) != 0) return false

        if (record != null && isCurrent(record, installed)) {
            return VersionComparator.compare(record.patchVersion, latestPatch) < 0
        }

        if (installed.lastUpdateTime <= 0L) return false
        val published = parseEpochMillis(app.updatedAt) ?: return false
        return installed.lastUpdateTime < published - PUBLISH_MARGIN_MS
    }

    /** Whether [record] still describes the package that is installed now. */
    fun isCurrent(record: InstalledBuildRecord, installed: InstalledBuild): Boolean =
        record.versionName == installed.versionName && record.lastUpdateTime == installed.lastUpdateTime

    private fun parseEpochMillis(isoTimestamp: String): Long? =
        if (isoTimestamp.isBlank()) null
        else runCatching { Instant.parse(isoTimestamp).toEpochMilli() }.getOrNull()
}
