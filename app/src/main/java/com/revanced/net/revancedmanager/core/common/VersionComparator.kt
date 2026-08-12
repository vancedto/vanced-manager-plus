package com.revanced.net.revancedmanager.core.common

/**
 * Single source of truth for comparing app version strings.
 * Handles numeric dot-separated versions with optional non-numeric suffixes
 * (e.g. "19.16.39-release") and treats missing parts as zero so that
 * "1.2" == "1.2.0".
 */
object VersionComparator {

    /**
     * @return positive if [version1] > [version2], negative if less, zero if equal
     * or if either version is blank/unparseable.
     */
    fun compare(version1: String, version2: String): Int {
        if (version1.isEmpty() || version2.isEmpty()) return 0
        return try {
            val parts1 = version1.split(".").map { part ->
                part.takeWhile { it.isDigit() }.toLongOrNull() ?: 0L
            }
            val parts2 = version2.split(".").map { part ->
                part.takeWhile { it.isDigit() }.toLongOrNull() ?: 0L
            }
            val length = maxOf(parts1.size, parts2.size)
            for (i in 0 until length) {
                val n1 = parts1.getOrElse(i) { 0L }
                val n2 = parts2.getOrElse(i) { 0L }
                when {
                    n1 > n2 -> return 1
                    n1 < n2 -> return -1
                }
            }
            0
        } catch (e: Exception) {
            0
        }
    }
}
