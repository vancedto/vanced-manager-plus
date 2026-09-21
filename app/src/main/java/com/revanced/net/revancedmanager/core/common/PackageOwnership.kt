package com.revanced.net.revancedmanager.core.common

import com.revanced.net.revancedmanager.domain.model.AppStatus
import com.revanced.net.revancedmanager.domain.model.RevancedApp

/**
 * Which catalog entry an installed package belongs to, when several entries share the package.
 *
 * Android has one slot per package, so "installed, version X" is a fact about the package. But the
 * catalog can list two builds for that slot — MicroG RE (7.x, signed by Morphe) and ReVanced GmsCore
 * (0.3.x, signed by ReVanced) are both app.revanced.android.gms — and they are alternatives, not
 * versions of each other. Treating both rows as "installed at 0.3.13" made the RE row say an update
 * to 7.1 was available, when what it offered was a switch to a differently-signed app that the
 * installer refuses until the old one is uninstalled.
 *
 * The rule: an entry *claims* an installed version when the version falls inside the entry's
 * [RevancedApp.installedVersionRange]. Among the entries for one package, the single entry whose
 * band holds the version is the **owner** and is shown as installed; if no band holds it, the
 * single entry that has no band takes it (a band-less entry claims whatever the others leave,
 * which is the shape the catalog linter allows). Every other entry is shown as not installed — its
 * button reads "Install", and the signature preflight will ask about uninstalling the current
 * build if the user goes ahead, which is the honest description of what happens.
 *
 * When the catalog does not disambiguate — no entry has a range, two bands hold the version, or
 * nothing claims it — the first entry in catalog order is the owner. That is what every version of
 * this app did before ranges existed, so a catalog without them changes nothing.
 */
object PackageOwnership {

    /**
     * Whether [app]'s band contains [installedVersion]. An entry with no band claims every version
     * on its own; [ownerOf] is where a banded entry takes precedence over it.
     */
    fun claims(app: RevancedApp, installedVersion: String): Boolean {
        val range = app.installedVersionRange ?: return true
        if (installedVersion.isBlank()) return false
        val min = range.min
        val max = range.max
        if (!min.isNullOrBlank() && VersionComparator.compare(installedVersion, min) < 0) return false
        if (!max.isNullOrBlank() && VersionComparator.compare(installedVersion, max) >= 0) return false
        return true
    }

    /**
     * The entry among [entries] (all sharing one package, in catalog order) that owns an install
     * at [installedVersion]. Never null for a non-empty list: ambiguity falls back to the first.
     */
    fun ownerOf(entries: List<RevancedApp>, installedVersion: String): RevancedApp? {
        if (entries.isEmpty()) return null
        val (banded, unbanded) = entries.partition { it.installedVersionRange != null }
        val bandHolders = banded.filter { claims(it, installedVersion) }
        return when {
            bandHolders.size == 1 -> bandHolders.single()
            bandHolders.isEmpty() && unbanded.size == 1 -> unbanded.single()
            else -> entries.first()
        }
    }

    /**
     * Fill in [RevancedApp.currentVersion] and derive [RevancedApp.status] for a whole list, applying
     * the ownership rule to every group of entries that share a package.
     *
     * The one place this is computed, so the cache path, the network refresh, the update
     * notification and the bloc cannot drift apart on what "installed" means.
     *
     * @param installedVersionOf `null` when the package is not installed.
     */
    fun withInstallStatus(
        apps: List<RevancedApp>,
        installedVersionOf: (packageName: String) -> String?
    ): List<RevancedApp> {
        val versions = apps.map { it.packageName }.distinct().associateWith(installedVersionOf)
        val owners = apps.groupBy { it.packageName }.mapNotNull { (packageName, entries) ->
            val installed = versions[packageName] ?: return@mapNotNull null
            ownerOf(entries, installed)?.let { packageName to it.id }
        }.toMap()

        return apps.map { app ->
            val installed = versions[app.packageName]
            when {
                installed == null -> app.copy(currentVersion = null, status = AppStatus.NOT_INSTALLED)
                owners[app.packageName] != app.id -> app.copy(currentVersion = null, status = AppStatus.NOT_INSTALLED)
                else -> app.copy(
                    currentVersion = installed,
                    status = statusForVersions(installed, app.latestVersion)
                )
            }
        }
    }

    fun statusForVersions(installedVersion: String, latestVersion: String): AppStatus =
        if (VersionComparator.compare(installedVersion, latestVersion) >= 0) AppStatus.UP_TO_DATE
        else AppStatus.UPDATE_AVAILABLE
}
