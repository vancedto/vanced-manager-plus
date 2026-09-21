package com.revanced.net.revancedmanager.config

/**
 * Application configuration for debug and runtime settings.
 * This object provides centralized control over various app behaviors.
 */
object Config {
    
    /**
     * Enable verbose debug logging throughout the application.
     * When true, detailed logs will be printed for:
     * - SharedPreferences operations (save/load)
     * - App configuration changes
     * - Installation/download events
     * - UI state changes
     * 
     * Set to false for production builds to reduce logcat noise.
     */
    const val ENABLE_LOG = false
    
    /**
     * Log tag prefix for all debug logs
     */
    const val LOG_TAG = "RvMng"

    /**
     * Package names suggested for installation on the first app run.
     * They are matched against the app list loaded from the API, so title,
     * icon and download URL always come from live data; packages missing from
     * the API response (or already installed) are simply not suggested.
     */
    val SUGGESTED_PACKAGES = listOf(
        "app.morphe.android.youtube",             // YouTube Morphe
        "app.morphe.android.apps.youtube.music",  // YouTube Music Morphe
        "app.revanced.android.gms",                 // MicroG (required by the two above)
        "app.morphe.android.apps.photos"            // Google Photos Morphe
    )

    /**
     * Patch providers treated as "official" sources: the ReVanced team and the Morphe team. Every
     * other provider in the catalog is a community contributor, and the user chooses on first run
     * (and later in Settings) whether those apps are listed at all — the catalog grew past 300
     * entries and most of them come from community repos.
     *
     * Keys match `provider` in the v3 API, which is `patchProvider` in revanced.yaml. Apps with no
     * provider (MicroG, NewPipe, SmartTube, the manager itself) are not patched by anyone and are
     * always shown — MicroG in particular is required by the Morphe YouTube builds.
     */
    val MAINSTREAM_PROVIDERS = setOf("morphe", "revanced")
}
