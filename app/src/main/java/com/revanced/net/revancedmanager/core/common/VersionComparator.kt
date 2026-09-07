package com.revanced.net.revancedmanager.core.common

/**
 * Single source of truth for comparing app version strings.
 * Handles numeric dot-separated versions with optional non-numeric suffixes
 * (e.g. "19.16.39-release") and treats missing parts as zero so that
 * "1.2" == "1.2.0".
 *
 * A segment carrying a non-numeric suffix ranks below the same number without one, so a
 * prerelease stays under its own release: on numbers alone "7.1.0-dev.5" reads as [7,1,0,5]
 * and beats the stable "7.1.0" at [7,1,0]. This mirrors ReVanced.Models BuildVersion.Comparer
 * on the server, so the site and this app agree on which build is newer.
 */
object VersionComparator {

    /**
     * @return positive if [version1] > [version2], negative if less, zero if equal
     * or if either version is blank/unparseable.
     */
    fun compare(version1: String, version2: String): Int {
        if (version1.isEmpty() || version2.isEmpty()) return 0
        return try {
            val parts1 = parse(version1)
            val parts2 = parse(version2)
            val length = maxOf(parts1.size, parts2.size)
            for (i in 0 until length) {
                val (n1, suffixed1) = parts1.getOrElse(i) { ZERO }
                val (n2, suffixed2) = parts2.getOrElse(i) { ZERO }
                when {
                    n1 > n2 -> return 1
                    n1 < n2 -> return -1
                    suffixed1 != suffixed2 -> return if (suffixed1) -1 else 1
                }
            }
            0
        } catch (e: Exception) {
            0
        }
    }

    private val ZERO = Segment(0L, suffixed = false)

    private data class Segment(val number: Long, val suffixed: Boolean)

    private fun parse(version: String): List<Segment> = version.split(".").map { part ->
        val digits = part.takeWhile { it.isDigit() }
        Segment(digits.toLongOrNull() ?: 0L, suffixed = digits.length < part.length)
    }
}
