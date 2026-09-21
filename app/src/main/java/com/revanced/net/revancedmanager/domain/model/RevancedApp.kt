package com.revanced.net.revancedmanager.domain.model

import com.revanced.net.revancedmanager.config.Config

/**
 * Domain model representing a ReVanced application
 *
 * [downloadUrl] and [latestVersion] describe the variant chosen for *this* device, so everything
 * downstream — download, install, update checks — keeps working off them unchanged. The full set
 * of published builds is in [variants], for display.
 *
 * The fields below [updatePromptEnabled] arrive with the v3 API and all have defaults, so callers
 * that construct or copy this model without them still compile. [isFavorite] and
 * [updatePromptEnabled] are local preferences rather than catalog data.
 */
data class RevancedApp(
    /**
     * Identity of this *catalog entry*, unique across the list.
     *
     * Not the same thing as [packageName], and that is the point: the same Android package is
     * patched by several groups into several catalog entries — com.google.android.youtube has six.
     * Keying the list on the package name makes LazyColumn throw the moment two of them are
     * enabled together, so anything about *which row this is* uses this instead.
     *
     * [packageName] stays the identity for everything that talks to Android — install, uninstall,
     * open, installed version — because there the package really is the identity.
     */
    val id: String,
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
    val isFavorite: Boolean = false,
    /**
     * Whether this entry may be offered by the launch update prompt and the daily update
     * notification. Toggled per app in the detail screen; on by default.
     *
     * A local preference like [isFavorite], not something the catalog carries.
     */
    val updatePromptEnabled: Boolean = true,
    /** 200px icon for the detail page; falls back to [iconUrl] when the server has none. */
    val iconLargeUrl: String = "",
    /** Plain text with newlines — the catalog is not markdown. */
    val longDescription: String = "",
    /** Patches and community features built into the app. */
    val features: List<String> = emptyList(),
    /** Patch provider key (morphe, revanced, ...). Null for apps that are not patched. */
    val provider: String? = null,
    val author: String = "",
    val website: String? = null,
    /** Catalog slug, used to build the app's page URL on the site. */
    val slug: String = "",
    /** Size of the chosen variant. */
    val sizeBytes: Long = 0,
    val sizeText: String = "",
    /** Build date of the chosen variant, ISO-8601. Formatted where it is displayed. */
    val updatedAt: String = "",
    /** Every published build, including ones this device cannot run. */
    val variants: List<AppVariant> = emptyList(),
    /**
     * True when no published build matches this device's ABIs and [downloadUrl] is therefore a
     * best-effort attempt rather than a build known to fit — see `VariantSelector.Match`.
     *
     * The install is still offered, because on the devices this actually happens to it usually
     * works; it is flagged so the UI can say so instead of presenting it as the right build.
     */
    val isBestEffortVariant: Boolean = false,
    /**
     * Which installed versions of [packageName] are this entry's own builds. Null for the usual
     * one-entry-per-package case, where whatever is installed is by definition this entry's.
     *
     * Set only where two catalog entries are alternatives for the same package — MicroG RE (6.x,
     * 7.x) and ReVanced GmsCore (0.3.x) both install as app.revanced.android.gms. Install status is
     * a property of the package (one slot, one build), but *which entry* that build belongs to is
     * decided by this band: see `PackageOwnership`.
     */
    val installedVersionRange: InstalledVersionRange? = null
) {
    /**
     * True for an app patched by a community provider rather than the ReVanced or Morphe teams.
     *
     * An app with no provider is not a community contribution: nobody patched it (MicroG, NewPipe,
     * SmartTube), so it stays visible whatever the user chose.
     */
    val isCommunityContribution: Boolean
        get() = provider != null && provider !in Config.MAINSTREAM_PROVIDERS
}

/**
 * The apps the user has chosen to see. The single definition of "hidden", so the main list, the
 * update prompt, "Update all" and the background update check cannot disagree about it: an app the
 * user asked not to see must not come back as an update notification.
 */
fun List<RevancedApp>.visibleFor(config: AppConfig): List<RevancedApp> =
    if (config.showCommunityApps) this else filterNot { it.isCommunityContribution }

/**
 * A half-open version band `[min, max)`; a null bound is open on that side.
 */
data class InstalledVersionRange(
    val min: String? = null,
    val max: String? = null
)

/**
 * One published build of an app. [arch] is an Android ABI name or "universal".
 */
data class AppVariant(
    val arch: String,
    val version: String,
    val url: String,
    val sizeBytes: Long,
    val sizeText: String,
    val updatedAt: String
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
