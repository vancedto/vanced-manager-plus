package com.revanced.net.revancedmanager.core.common

import com.revanced.net.revancedmanager.config.Config
import com.revanced.net.revancedmanager.domain.model.AppStatus
import com.revanced.net.revancedmanager.domain.model.RevancedApp

/**
 * The "this app needs MicroG" rule, kept free of Android so it can be tested on its own.
 *
 * An app flagged `requireMicroG` installs and opens without it, then fails at the Google sign-in —
 * long after the manager could have said anything. So the question is asked when the download is
 * started, once per batch.
 */
object MicroGRequirement {

    /**
     * The MicroG entry to install for an app that needs one: [Config.MICROG_PREFERRED_SLUG], or the
     * first MicroG entry in catalog order when that one is missing. The catalog lists several builds
     * of the same package, and installing more than one would put them over each other.
     */
    fun preferredEntry(apps: List<RevancedApp>): RevancedApp? {
        val candidates = apps.filter { it.packageName == Config.MICROG_PACKAGE }
        return candidates.firstOrNull { it.slug == Config.MICROG_PREFERRED_SLUG }
            ?: candidates.firstOrNull()
    }

    /**
     * Whether starting [batch] should offer MicroG as well: something in it needs MicroG, nothing in
     * it already is MicroG, and MicroG is neither on the device nor on its way there.
     */
    /**
     * Installed apps that sign in through MicroG — what uninstalling it would break. One per
     * package: two entries of the same package are one app on the device.
     */
    fun dependents(apps: List<RevancedApp>): List<RevancedApp> =
        apps.filter {
            it.requiresMicroG && (it.status == AppStatus.UP_TO_DATE || it.status == AppStatus.UPDATE_AVAILABLE)
        }.distinctBy { it.packageName }

    fun shouldOffer(batch: List<RevancedApp>, microGInstalledOrInFlight: Boolean): Boolean =
        !microGInstalledOrInFlight &&
            batch.any { it.requiresMicroG } &&
            batch.none { it.packageName == Config.MICROG_PACKAGE }
}
