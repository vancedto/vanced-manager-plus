package com.revanced.net.revancedmanager.domain.usecase

import com.revanced.net.revancedmanager.core.common.Result
import com.revanced.net.revancedmanager.domain.repository.AppRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper class that contains all app management use cases
 * This makes it easier to inject all use cases into ViewModels
 */
@Singleton
data class AppManagementUseCases @Inject constructor(
    val getAppsUseCase: GetAppsUseCase,
    val installAppUseCase: InstallAppUseCase,
    val uninstallAppUseCase: UninstallAppUseCase,
    val openAppUseCase: OpenAppUseCase,
    val appRepository: AppRepository
)

/**
 * Use case for installing apps
 */
class InstallAppUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(packageName: String, apkFilePath: String): Result<Boolean> {
        return appRepository.installApp(packageName, apkFilePath)
    }
}

/**
 * Use case for uninstalling apps
 */
class UninstallAppUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(packageName: String): Result<Boolean> {
        return appRepository.uninstallApp(packageName)
    }
}

/**
 * Use case for opening installed apps
 */
class OpenAppUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(packageName: String): Result<Boolean> {
        return appRepository.openApp(packageName)
    }
}

/**
 * Use case for checking if an app is installed
 */
class IsAppInstalledUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(packageName: String): Boolean {
        return appRepository.isAppInstalled(packageName)
    }
} 