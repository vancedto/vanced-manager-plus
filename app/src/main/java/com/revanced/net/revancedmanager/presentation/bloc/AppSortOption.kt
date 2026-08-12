package com.revanced.net.revancedmanager.presentation.bloc

/**
 * How the app list is ordered.
 *
 * Catalog order is fine for ~32 apps and stops being fine at ~500, where "what changed recently"
 * and "find it alphabetically" are the two ways people actually look. The dates come from the v3
 * catalog, which publishes a build date per app that nothing used until now.
 */
enum class AppSortOption {
    /** The order the catalog itself lists apps in — curated, so it stays the default. */
    CATALOG,

    /** Newest build first. */
    RECENTLY_UPDATED,

    NAME_ASC
}
