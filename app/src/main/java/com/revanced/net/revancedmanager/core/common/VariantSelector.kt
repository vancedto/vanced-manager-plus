package com.revanced.net.revancedmanager.core.common

import com.revanced.net.revancedmanager.data.remote.dto.AppV3DownloadDto

/**
 * Picks which of an app's builds this device should install. Nobody is asked: the device already
 * knows its own ABIs, so the choice is made here and the rest of the app just uses the result.
 *
 * v2 made this choice by endpoint — the app fetched one per-arch file and took whatever was in it,
 * which is how an x86_64 device ended up on the x86 list. v3 ships every architecture together and
 * the choice is made here, per app, against the device's own ABI list.
 *
 * There are two tiers, and the difference matters to the caller:
 *
 * - [Match.EXACT] — the build's arch is one the device declares, or it is universal. Install it.
 * - [Match.BEST_EFFORT] — nothing the app published matches this device, so rather than showing an
 *   entry with no install button at all, the most likely build is offered and the install attempt
 *   is allowed to answer the question. An arm64-v8a APK is the fallback of choice: on the devices
 *   that actually land here — x86 emulators, Chromebooks, Windows Subsystem for Android — ARM
 *   translation usually runs it, and on a 32-bit-only phone a rejected install is still a better
 *   answer than a dead row.
 */
object VariantSelector {

    private const val UNIVERSAL = "universal"

    /**
     * Architectures to fall back on when the device matches none of them, best first.
     *
     * Only reached when no ABI matched, which also means no universal build exists — universal is
     * compatible with everything, so it can never fall through to here.
     */
    private val FALLBACK_ARCH_PREFERENCE = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    /** How well the chosen build fits this device. */
    enum class Match {
        /** The device declares this ABI, or the build is universal. */
        EXACT,

        /** Nothing published fits; this is the build most likely to install anyway. */
        BEST_EFFORT
    }

    /** The chosen build together with how much confidence there is in it. */
    data class Choice(
        val download: AppV3DownloadDto,
        val match: Match
    ) {
        val arch: String get() = download.arch
        val isBestEffort: Boolean get() = match == Match.BEST_EFFORT
    }

    /**
     * @param downloads every build the server published for one app.
     * @param supportedAbis the device's ABIs from `Build.SUPPORTED_ABIS`, already ordered by the
     *   device's own preference — index 0 is the best fit.
     * @return the build to install, or null only when the app has published nothing installable at
     *   all. A device with no matching arch gets a [Match.BEST_EFFORT] choice, not null.
     */
    fun choose(
        downloads: List<AppV3DownloadDto>,
        supportedAbis: List<String>
    ): Choice? {
        val installable = downloads.filter { it.url.isNotBlank() }
        if (installable.isEmpty()) return null

        val compatible = installable.filter { isCompatible(it.arch, supportedAbis) }
        if (compatible.isNotEmpty()) {
            // Newest build first, whatever it was compiled for: an app that has just moved to
            // universal builds still has its older per-arch rows, and the new build is the one
            // worth installing. Only within one version does the exact ABI match beat universal.
            val chosen = compatible.minWith(
                compareByDescending<AppV3DownloadDto> { it.version.asVersion() }
                    .thenBy { abiRank(it.arch, supportedAbis) }
            )
            return Choice(chosen, Match.EXACT)
        }

        // Nothing fits. Take the arch most likely to run anyway, newest build within it.
        val chosen = installable.minWith(
            compareBy<AppV3DownloadDto> { fallbackRank(it.arch) }
                .thenByDescending { it.version.asVersion() }
        )
        return Choice(chosen, Match.BEST_EFFORT)
    }

    private fun isCompatible(arch: String, supportedAbis: List<String>): Boolean =
        arch.equals(UNIVERSAL, ignoreCase = true) || supportedAbis.any { it.equals(arch, ignoreCase = true) }

    /**
     * How well an arch fits this device: the device's own ABI order, with universal placed after
     * every real match because a native build is the better choice when both exist.
     */
    private fun abiRank(arch: String, supportedAbis: List<String>): Int {
        val index = supportedAbis.indexOfFirst { it.equals(arch, ignoreCase = true) }
        return if (index >= 0) index else supportedAbis.size
    }

    /** Position in [FALLBACK_ARCH_PREFERENCE]; anything unrecognised sorts last. */
    private fun fallbackRank(arch: String): Int {
        val index = FALLBACK_ARCH_PREFERENCE.indexOfFirst { it.equals(arch, ignoreCase = true) }
        return if (index >= 0) index else FALLBACK_ARCH_PREFERENCE.size
    }

    /** Wraps a version string so the comparators above can order it numerically. */
    private fun String.asVersion(): ComparableVersion = ComparableVersion(this)

    private class ComparableVersion(val value: String) : Comparable<ComparableVersion> {
        override fun compareTo(other: ComparableVersion): Int =
            VersionComparator.compare(value, other.value)
    }
}
