package com.revanced.net.revancedmanager.presentation.bloc

import com.revanced.net.revancedmanager.domain.model.AppConfig
import com.revanced.net.revancedmanager.domain.model.AppStatus
import com.revanced.net.revancedmanager.domain.model.RevancedApp

/**
 * Represents the different states of the main app screen
 */
sealed class AppState {
    data object Loading : AppState()
    data class Success(
        val apps: List<RevancedApp>,
        val searchQuery: String = "",
        val filterOption: AppFilterOption = AppFilterOption.ALL,
        val sortOption: AppSortOption = AppSortOption.CATALOG,
        val dialogState: DialogState? = null,
        val config: AppConfig = AppConfig(),
        val isRefreshing: Boolean = false,
        /** First-run suggestions to show in a popup; null = popup hidden. */
        val suggestedApps: List<RevancedApp>? = null
    ) : AppState() {
        /** Number of apps currently being downloaded, installed or uninstalled. */
        val processingCount: Int
            get() = apps.count { it.status in PROCESSING_STATUSES }

        /**
         * Returns filtered apps based on search query and active filter option.
         * Search matches title or package name (case-insensitive).
         */
        val filteredApps: List<RevancedApp>
            get() = sorted(filtered())

        private fun filtered(): List<RevancedApp> {
                val searched = if (searchQuery.isBlank()) apps else apps.filter { app ->
                    app.title.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
                }
                return when (filterOption) {
                    AppFilterOption.ALL -> searched
                    AppFilterOption.INSTALLED -> searched.filter {
                        it.status != AppStatus.NOT_INSTALLED && it.status != AppStatus.UNKNOWN
                    }
                    AppFilterOption.NOT_INSTALLED -> searched.filter {
                        it.status == AppStatus.NOT_INSTALLED
                    }
                    AppFilterOption.UPDATES_AVAILABLE -> searched.filter {
                        it.status == AppStatus.UPDATE_AVAILABLE
                    }
                    AppFilterOption.FAVORITES -> searched.filter { it.isFavorite }
                    AppFilterOption.PROCESSING -> searched.filter { it.status in PROCESSING_STATUSES }
                }
        }

        /**
         * Catalog order is the list as the server sent it, so sorting is a no-op there rather than
         * a re-sort by index.
         *
         * Dates are ISO-8601 from the v3 catalog and sort correctly as plain strings; an app with
         * no date sorts last rather than jumping to the top.
         */
        private fun sorted(apps: List<RevancedApp>): List<RevancedApp> = when (sortOption) {
            AppSortOption.CATALOG -> apps
            AppSortOption.RECENTLY_UPDATED -> apps.sortedWith(
                compareByDescending<RevancedApp> { it.updatedAt.isNotBlank() }
                    .thenByDescending { it.updatedAt }
            )
            AppSortOption.NAME_ASC -> apps.sortedBy { it.title.lowercase() }
        }

        private companion object {
            val PROCESSING_STATUSES = setOf(
                AppStatus.DOWNLOADING,
                AppStatus.INSTALLING,
                AppStatus.UNINSTALLING
            )
        }
    }
    data class Error(
        val message: String,
        val dialogState: DialogState? = null,
        val config: AppConfig = AppConfig()
    ) : AppState()
}

/**
 * Represents dialog states for confirmations
 */
sealed class DialogState {
    data class Confirmation(
        val title: String,
        val message: String,
        val onConfirmAction: () -> Unit,
        val onCancelAction: (() -> Unit)? = null
    ) : DialogState()
    
    data class Progress(
        val title: String,
        val message: String,
        val progress: Float? = null
    ) : DialogState()

    /**
     * Three-choice prompt shown after the app list is refreshed on launch when
     * updates are available: update everything, snooze for the rest of the day,
     * or just close the dialog.
     */
    data class UpdatePrompt(
        val updateCount: Int,
        val onUpdateAll: () -> Unit,
        val onSkipToday: () -> Unit,
        val onDismiss: () -> Unit
    ) : DialogState()
}

/**
 * Represents individual app states for UI updates
 */
data class AppUiState(
    val app: RevancedApp,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) 