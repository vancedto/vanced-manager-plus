package com.revanced.net.revancedmanager.core.common

import com.revanced.net.revancedmanager.domain.model.AppStatus
import com.revanced.net.revancedmanager.domain.model.RevancedApp

/**
 * Which catalog entry a status or progress update is about, when several entries share a package.
 *
 * [PackageOwnership] answers this for a *settled* status — what is installed belongs to whoever's
 * version band holds it. This object answers it for an *in-flight* one, where the question is a
 * different one: not "whose build is on the device" but "whose button started this".
 *
 * Android gives a package one install slot, one APK path and one download, so there is only ever
 * one operation in flight per package. But it was started from one catalog entry, and that is the
 * row that should show it. MicroG RE and ReVanced GmsCore are both `app.revanced.android.gms`;
 * updating RE used to spin both rows, which claimed two downloads where there was one and put a
 * Cancel button on a row that had asked for nothing.
 *
 * The initiator is not always known — work enqueued by an older build of this app carries no id,
 * and a package broadcast is about the package rather than any entry. Then every entry of the
 * package is updated, which is what this app did before initiators were tracked. The fallback is
 * deliberately *not* the [PackageOwnership] owner: with GmsCore installed, a replayed RE download
 * would put the spinner on GmsCore and leave the RE row tappable — and a tap there enqueues a
 * REPLACE that silently kills the download already running. "Both rows busy" never invites that.
 */
object InFlightAttribution {

    /** Statuses that describe what is installed rather than what is happening. */
    val SETTLED_STATUSES = setOf(
        AppStatus.NOT_INSTALLED,
        AppStatus.UP_TO_DATE,
        AppStatus.UPDATE_AVAILABLE,
        AppStatus.UNKNOWN
    )

    /**
     * Statuses that mean work is under way, so a status refresh must not overwrite them.
     *
     * Derived rather than listed, so a new [AppStatus] cannot end up in neither set — and so the
     * two halves of this rule cannot drift apart.
     */
    val IN_FLIGHT_STATUSES = AppStatus.entries.toSet() - SETTLED_STATUSES

    /**
     * Set [status] on the entries of [packageName] it belongs to.
     *
     * A settled status goes to the [PackageOwnership] owner of [installedVersion], and every other
     * entry of the package reads as not installed — unchanged, and the one place that rule is
     * applied outside the repository. An in-flight status goes to [initiatorId] alone, leaving the
     * package's other entries exactly as they are; with no initiator it goes to all of them.
     *
     * @param installedVersion what is installed as [packageName], or null when nothing is.
     */
    fun applyStatus(
        apps: List<RevancedApp>,
        packageName: String,
        status: AppStatus,
        initiatorId: String?,
        installedVersion: String?
    ): List<RevancedApp> {
        if (status in SETTLED_STATUSES) {
            // No install, no owner: nothing on the device belongs to any entry, so the status —
            // NOT_INSTALLED, in practice — is true of all of them.
            val ownerId = installedVersion?.let {
                PackageOwnership.ownerOf(apps.filter { app -> app.packageName == packageName }, it)?.id
            }
            return apps.map { app ->
                when {
                    app.packageName != packageName -> app
                    ownerId == null || app.id == ownerId -> app.copy(status = status)
                    else -> app.copy(status = AppStatus.NOT_INSTALLED, currentVersion = null)
                }
            }
        }
        return apps.map { app ->
            if (targets(app, packageName, initiatorId, apps)) app.copy(status = status) else app
        }
    }

    /**
     * Set [progress] on the entries of [packageName] an in-flight update belongs to, by the same
     * rule as [applyStatus].
     *
     * Progress is written to the same entries as the status even though the bar is only drawn while
     * the status is DOWNLOADING: writing one without the other leaves a stale fraction on the
     * sibling entry, which surfaces the next time that entry legitimately downloads.
     */
    fun applyProgress(
        apps: List<RevancedApp>,
        packageName: String,
        progress: Float,
        initiatorId: String?
    ): List<RevancedApp> = apps.map { app ->
        if (targets(app, packageName, initiatorId, apps)) app.copy(downloadProgress = progress) else app
    }

    /**
     * Whether an in-flight update for [packageName] applies to [app].
     *
     * An [initiatorId] that matches no entry of the package is treated as no initiator at all: it
     * is an id from a catalog the list no longer holds, and honouring it would leave the operation
     * with nowhere to land — a card that can never come out of DOWNLOADING.
     */
    private fun targets(
        app: RevancedApp,
        packageName: String,
        initiatorId: String?,
        apps: List<RevancedApp>
    ): Boolean {
        if (app.packageName != packageName) return false
        if (initiatorId == null) return true
        if (apps.none { it.packageName == packageName && it.id == initiatorId }) return true
        return app.id == initiatorId
    }
}
