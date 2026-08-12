package com.revanced.net.revancedmanager.domain.repository

import com.revanced.net.revancedmanager.core.common.Result
import com.revanced.net.revancedmanager.domain.model.RevancedApp
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for app-related data operations
 * This is the contract that data layer must implement
 */
interface AppRepository {
    
    /**
     * Get list of available ReVanced apps
     * @param forceRefresh Whether to force refresh from network
     * @return Flow of Result containing list of apps
     */
    fun getApps(forceRefresh: Boolean = false): Flow<Result<List<RevancedApp>>>
    
    /**
     * Get apps from cache immediately for fast UI loading
     * @return Flow of Result containing cached apps or Loading if no cache
     */
    fun getAppsFromCacheImmediately(): Flow<Result<List<RevancedApp>>>
    
    /**
     * Background refresh apps with fallback to cache
     * @return Flow of Result containing refreshed apps
     */
    fun backgroundRefreshApps(): Flow<Result<List<RevancedApp>>>
    
    /**
     * Compare two app lists and return updated apps
     * @param oldApps Previous app list
     * @param newApps New app list  
     * @return List of apps that have been updated
     */
    fun getUpdatedApps(oldApps: List<RevancedApp>, newApps: List<RevancedApp>): List<RevancedApp>
    
    /**
     * Get installed version of an app
     * @param packageName Package name of the app
     * @return Installed version string or null if not installed
     */
    suspend fun getInstalledVersion(packageName: String): String?
    
    /**
     * Install an APK file
     * @param packageName Package name of the app
     * @param apkFilePath Path to the APK file
     * @return Result of installation
     */
    suspend fun installApp(packageName: String, apkFilePath: String): Result<Boolean>
    
    /**
     * Uninstall an app
     * @param packageName Package name of the app
     * @return Result of uninstallation
     */
    suspend fun uninstallApp(packageName: String): Result<Boolean>
    
    /**
     * Open an installed app
     * @param packageName Package name of the app
     * @return Result of the operation
     */
    suspend fun openApp(packageName: String): Result<Boolean>
    
    /**
     * Check if an app is installed
     * @param packageName Package name of the app
     * @return True if installed, false otherwise
     */
    suspend fun isAppInstalled(packageName: String): Boolean
} 