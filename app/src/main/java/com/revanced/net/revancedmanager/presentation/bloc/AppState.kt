package com.revanced.net.revancedmanager.presentation.bloc

import com.revanced.net.revancedmanager.domain.model.AppConfig
import com.revanced.net.revancedmanager.domain.model.AppStatus
import com.revanced.net.revancedmanager.domain.model.RevancedApp
import com.revanced.net.revancedmanager.domain.model.visibleFor

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
        val suggestedApps: List<RevancedApp>? = null,
        /** First-run "where should apps come from?" dialog is on screen. Outranks every other popup. */
        val askAppSource: Boolean = false
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
                // Source choice first: a hidden community app must not surface through search.
                val visible = apps.visibleFor(config)
                val searched = if (searchQuery.isBlank()) visible else visible.filter { app ->
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
                AppStatus.READY_TO_INSTALL,
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
        val onCancelAction: (() -> Unit)? = null,
        /** Overrides the generic "Confirm" so destructive buttons say what they do ("Uninstall & install"). */
        val confirmLabel: String? = null,
        val cancelLabel: String? = null,
        /** Renders the confirm button in the error color for irreversible actions. */
        val destructive: Boolean = false,
        /** False for purely informational dialogs whose only action is acknowledging. */
        val showCancelButton: Boolean = true
    ) : DialogState()
    
    data class Progress(
        val title: String,
        val message: String,
        val progress: Float? = null
    ) : DialogState()

    /**
     * Prompt shown after the app list is refreshed on launch when updates are available. Lists
     * [apps] with a checkbox each — [onUpdateSelected] receives the ids still ticked — alongside
     * the choices to snooze for the rest of the day or just close the dialog.
     */
    data class UpdatePrompt(
        val apps: List<RevancedApp>,
        val onUpdateSelected: (List<String>) -> Unit,
        val onSkipToday: () -> Unit,
        val onDismiss: () -> Unit,
        /** Switch the popup off for good — the same setting as "Update popup on launch". */
        val onTurnOff: () -> Unit
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