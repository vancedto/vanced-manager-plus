package com.revanced.net.revancedmanager.domain.usecase

import com.revanced.net.revancedmanager.core.common.Result
import com.revanced.net.revancedmanager.core.common.VersionComparator
import com.revanced.net.revancedmanager.domain.model.AppStatus
import com.revanced.net.revancedmanager.domain.model.RevancedApp
import com.revanced.net.revancedmanager.domain.repository.AppRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case for getting and sorting ReVanced apps
 * Implements business logic for app retrieval and status determination
 */
class GetAppsUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    
    /**
     * Get apps with proper sorting and status checking
     * @param forceRefresh Whether to force refresh from network
     * @return Flow of Result containing sorted list of apps
     */
    operator fun invoke(forceRefresh: Boolean = false): Flow<Result<List<RevancedApp>>> {
        return appRepository.getApps(forceRefresh).map { result ->
            when (result) {
                is Result.Success -> {
                    val sortedApps = result.data
                        .map { app -> app.copy(status = determineAppStatus(app)) }
                        .sortedWith(
                            compareBy<RevancedApp> { app ->
                                // Primary sort: installed apps first
                                when (app.status) {
                                    AppStatus.UP_TO_DATE,
                                    AppStatus.UPDATE_AVAILABLE -> 0
                                    else -> 1
                                }
                            }.thenBy { app ->
                                // Secondary sort: by index within each group
                                app.index
                            }
                        )
                    Result.Success(sortedApps)
                }
                is Result.Error -> result
                is Result.Loading -> result
            }
        }
    }
    
    private fun compareVersions(installedVersion: String, latestVersion: String): Int =
        VersionComparator.compare(installedVersion, latestVersion)


    /**
     * Determine the status of an app based on its installed and latest versions
     * @param app The RevancedApp to check
     * @return AppStatus representing the current status
     */
    private fun determineAppStatus(app: RevancedApp): AppStatus {
        return when {
            app.currentVersion == null -> AppStatus.NOT_INSTALLED
            compareVersions(app.currentVersion, app.latestVersion) >= 0 -> AppStatus.UP_TO_DATE
            else -> AppStatus.UPDATE_AVAILABLE
        }
    }
} 