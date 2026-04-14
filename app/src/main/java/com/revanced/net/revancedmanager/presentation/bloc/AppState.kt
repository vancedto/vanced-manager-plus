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
        val dialogState: DialogState? = null,
        val config: AppConfig = AppConfig(),
        val showSettings: Boolean = false
    ) : AppState() {
        /**
         * Returns filtered apps based on search query and active filter option.
         * Search matches title or package name (case-insensitive).
         */
        val filteredApps: List<RevancedApp>
            get() {
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
                }
            }
    }
    data class Error(
        val message: String,
        val dialogState: DialogState? = null,
        val config: AppConfig = AppConfig(),
        val showSettings: Boolean = false
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
}

/**
 * Represents individual app states for UI updates
 */
data class AppUiState(
    val app: RevancedApp,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) 