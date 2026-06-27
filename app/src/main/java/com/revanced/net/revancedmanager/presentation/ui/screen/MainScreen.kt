package com.revanced.net.revancedmanager.presentation.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revanced.net.revancedmanager.R
import com.revanced.net.revancedmanager.presentation.bloc.AppBloc
import com.revanced.net.revancedmanager.presentation.bloc.AppEvent
import com.revanced.net.revancedmanager.presentation.bloc.AppFilterOption
import com.revanced.net.revancedmanager.presentation.bloc.AppState
import com.revanced.net.revancedmanager.presentation.bloc.DialogState
import com.revanced.net.revancedmanager.presentation.bloc.shareDebugLogs
import com.revanced.net.revancedmanager.presentation.ui.components.AppCard
import com.revanced.net.revancedmanager.presentation.ui.theme.noiseBackground
import kotlinx.coroutines.delay

/**
 * Main screen of the ReVanced Manager app
 * Updated with improved dialog handling and better UX
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: AppBloc = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val background = MaterialTheme.colorScheme.background
    val noiseAlpha = if (background.luminance() < 0.1f) 0.08f else 0.05f

    val showSettings = when (val s = state) {
        is AppState.Success -> s.showSettings
        is AppState.Error -> s.showSettings
        else -> false
    }
    
    // Remember the current toast to cancel it when a new one appears
    var currentToast by remember { mutableStateOf<Toast?>(null) }

    // Handle toast messages with cancellation of previous toast
    LaunchedEffect(toastMessage) {
        toastMessage?.let { message ->
            // Cancel any existing toast immediately
            currentToast?.cancel()
            
            // Create and show new toast with shorter duration
            val newToast = Toast.makeText(context, message, Toast.LENGTH_SHORT).apply {
                duration = Toast.LENGTH_SHORT
            }
            
            currentToast = newToast
            newToast.show()
            
            // Clear the toast message from ViewModel
            viewModel.clearToast()
            
            // Auto-dismiss the toast after a shorter time (1 second instead of 2)
            delay(1000)
            newToast.cancel()
            currentToast = null
        }
    }

    Scaffold(
        modifier = Modifier
            .noiseBackground(background, noiseAlpha)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            if (showSettings) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.settings),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.handleEvent(AppEvent.NavigateBackFromSettings) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.app_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.handleEvent(AppEvent.RefreshApps) }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.retry)
                            )
                        }
                        IconButton(onClick = { viewModel.handleEvent(AppEvent.NavigateToSettings) }) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.settings)
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    )
                )
            }
        }
    ) { paddingValues ->
        when (val currentState = state) {
            is AppState.Loading -> {
                LoadingScreen(modifier = Modifier.padding(paddingValues))
            }
            is AppState.Success -> {
                if (currentState.showSettings) {
                    SettingsScreen(
                        currentConfig = currentState.config,
                        onSave = { newConfig -> viewModel.handleEvent(AppEvent.SaveSettings(newConfig)) },
                        onCancel = { viewModel.handleEvent(AppEvent.NavigateBackFromSettings) },
                        onResetSettings = { viewModel.handleEvent(AppEvent.ResetSettings) },
                        getLogContent = { viewModel.debugLogManager.getLastLines() },
                        onShareLogs = { viewModel.shareDebugLogs() },
                        modifier = Modifier.padding(paddingValues)
                    )
                } else {
                    AppListScreen(
                        apps = currentState.filteredApps,
                        searchQuery = currentState.searchQuery,
                        filterOption = currentState.filterOption,
                        onSearchQueryChange = { query ->
                            viewModel.handleEvent(AppEvent.SearchApps(query))
                        },
                        onClearSearch = {
                            viewModel.handleEvent(AppEvent.ClearSearch)
                        },
                        onFilterChange = { filter ->
                            viewModel.handleEvent(AppEvent.SetFilter(filter))
                        },
                        onEvent = viewModel::handleEvent,
                        isCompactMode = currentState.config.compactMode,
                        modifier = Modifier.padding(paddingValues)
                    )

                    // Handle dialogs
                    currentState.dialogState?.let { dialogState ->
                        when (dialogState) {
                            is DialogState.Confirmation -> {
                                ConfirmationDialog(
                                    title = dialogState.title,
                                    message = dialogState.message,
                                    onConfirm = { dialogState.onConfirmAction() },
                                    onCancel = {
                                        dialogState.onCancelAction?.invoke()
                                            ?: viewModel.handleEvent(AppEvent.DismissDialog)
                                    }
                                )
                            }
                            is DialogState.Progress -> {
                                ProgressDialog(
                                    title = dialogState.title,
                                    message = dialogState.message,
                                    progress = dialogState.progress
                                )
                            }
                        }
                    }
                }
            }
            is AppState.Error -> {
                if (currentState.showSettings) {
                    SettingsScreen(
                        currentConfig = currentState.config,
                        onSave = { newConfig -> viewModel.handleEvent(AppEvent.SaveSettings(newConfig)) },
                        onCancel = { viewModel.handleEvent(AppEvent.NavigateBackFromSettings) },
                        onResetSettings = { viewModel.handleEvent(AppEvent.ResetSettings) },
                        getLogContent = { viewModel.debugLogManager.getLastLines() },
                        onShareLogs = { viewModel.shareDebugLogs() },
                        modifier = Modifier.padding(paddingValues)
                    )
                } else {
                    ErrorScreen(
                        message = currentState.message,
                        onRetry = { viewModel.handleEvent(AppEvent.RefreshApps) },
                        modifier = Modifier.padding(paddingValues)
                    )

                    // Handle dialogs in error state too
                    currentState.dialogState?.let { dialogState ->
                        when (dialogState) {
                            is DialogState.Confirmation -> {
                                ConfirmationDialog(
                                    title = dialogState.title,
                                    message = dialogState.message,
                                    onConfirm = { dialogState.onConfirmAction() },
                                    onCancel = {
                                        dialogState.onCancelAction?.invoke()
                                            ?: viewModel.handleEvent(AppEvent.DismissDialog)
                                    }
                                )
                            }
                            is DialogState.Progress -> {
                                ProgressDialog(
                                    title = dialogState.title,
                                    message = dialogState.message,
                                    progress = dialogState.progress
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Confirmation dialog component
 */
@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Progress dialog component
 */
@Composable
private fun ProgressDialog(
    title: String,
    message: String,
    progress: Float?
) {
    AlertDialog(
        onDismissRequest = { /* Not dismissible */ },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (progress != null) {
                    CircularProgressIndicator(
                        progress = { progress }
                    )
                } else {
                    CircularProgressIndicator()
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = { /* No button for progress dialog */ }
    )
}

/**
 * Loading screen component
 */
@Composable
private fun LoadingScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.loading_apps_message),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * Error screen component
 */
@Composable
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.error_prefix, message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

/**
 * App list screen component
 */
@Composable
private fun AppListScreen(
    apps: List<com.revanced.net.revancedmanager.domain.model.RevancedApp>,
    searchQuery: String = "",
    filterOption: AppFilterOption = AppFilterOption.ALL,
    onSearchQueryChange: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onFilterChange: (AppFilterOption) -> Unit = {},
    onEvent: (AppEvent) -> Unit,
    isCompactMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        // Search + filter bar
        item {
            SearchAndFilterBar(
                query = searchQuery,
                filterOption = filterOption,
                onQueryChange = onSearchQueryChange,
                onClear = onClearSearch,
                onFilterChange = onFilterChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // App cards
        // Empty state when no apps match search
        if (apps.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = when {
                            searchQuery.isNotBlank() -> stringResource(R.string.no_apps_found, searchQuery)
                            filterOption == AppFilterOption.FAVORITES -> stringResource(R.string.no_favorites_yet)
                            filterOption != AppFilterOption.ALL -> stringResource(R.string.no_apps_for_filter)
                            else -> stringResource(R.string.no_apps_available)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // App cards
        items(
            items = apps,
            key = { app -> app.packageName }
        ) { app ->
            AppCard(
                app = app,
                onDownloadClick = {
                    onEvent(AppEvent.DownloadApp(app.packageName, app.downloadUrl))
                },
                onInstallClick = {
                    // This will be called internally after download completes
                },
                onUninstallClick = {
                    onEvent(AppEvent.UninstallApp(app.packageName))
                },
                onReinstallClick = {
                    onEvent(AppEvent.ShowReinstallConfirmation(app.packageName))
                },
                onOpenClick = {
                    onEvent(AppEvent.OpenApp(app.packageName))
                },
                onFavoriteToggle = {
                    onEvent(AppEvent.ToggleFavorite(app.packageName))
                },
                isCompactMode = isCompactMode
            )
        }

        // Support buttons
        item {
            SupportButtons(
                onKofiClick = { launchUrl(context, "https://vanced.to/donate-redir") },
                onWebsiteClick = { launchUrl(context, "https://vanced.to") },
                onGithubClick = { launchUrl(context, "https://github.com/vancedto/vanced-manager-plus/") }
            )
        }

        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Support buttons component
 */
@Composable
private fun SupportButtons(
    onKofiClick: () -> Unit,
    onWebsiteClick: () -> Unit,
    onGithubClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Ko-fi support button
        // Button(
        //     onClick = onKofiClick,
        //     colors = ButtonDefaults.buttonColors(
        //         containerColor = Color(0xFF4285F4),
        //         contentColor = Color.White
        //     ),
        //     modifier = Modifier.fillMaxWidth(0.8f),
        //     shape = MaterialTheme.shapes.medium
        // ) {
        //     Icon(
        //         imageVector = Icons.Filled.Coffee,
        //         contentDescription = "Support on Ko-fi",
        //         modifier = Modifier.size(20.dp),
        //         tint = Color.White
        //     )
        //     Spacer(modifier = Modifier.width(8.dp))
        //     Text(
        //         text = "Support me on Ko-fi",
        //         style = MaterialTheme.typography.labelLarge,
        //         color = Color.White
        //     )
        // }

        // Website button
        Button(
            onClick = onWebsiteClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.fillMaxWidth(0.8f),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(
                imageVector = Icons.Filled.Language,
                contentDescription = "Visit website",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Visit vanced.to",
                style = MaterialTheme.typography.labelLarge
            )
        }

        // Github button
        Button(
            onClick = onGithubClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.fillMaxWidth(0.8f),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(
                imageVector = Icons.Filled.Code,
                contentDescription = "Github",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Source code",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * Helper function to launch URLs
 */
private fun launchUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to open URL", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Combined search + filter bar with animated filter chips.
 */
@Composable
private fun SearchAndFilterBar(
    query: String,
    filterOption: AppFilterOption,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onFilterChange: (AppFilterOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var filterExpanded by remember { mutableStateOf(filterOption != AppFilterOption.ALL) }
    val isFilterActive = filterOption != AppFilterOption.ALL

    // Auto-show chips when a filter becomes active from outside
    LaunchedEffect(isFilterActive) {
        if (isFilterActive) filterExpanded = true
    }

    // Chips are visible when expanded OR when a filter is active
    val showChips = filterExpanded || isFilterActive

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Search field
            androidx.compose.material3.TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_apps),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        androidx.compose.material3.IconButton(
                            onClick = onClear,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
                textStyle = MaterialTheme.typography.bodySmall
            )

            // Filter toggle button with active-state badge
            BadgedBox(
                badge = {
                    if (isFilterActive) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary)
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (isFilterActive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { filterExpanded = !filterExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.FilterList,
                        contentDescription = stringResource(R.string.filter_label),
                        modifier = Modifier.size(20.dp),
                        tint = if (isFilterActive) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Animated filter chips row
        AnimatedVisibility(
            visible = showChips,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chipColors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val chipBorder = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = false,
                    borderColor = MaterialTheme.colorScheme.outline,
                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                )
                FilterChip(
                    selected = filterOption == AppFilterOption.ALL,
                    onClick = { onFilterChange(AppFilterOption.ALL) },
                    label = { Text(text = stringResource(R.string.filter_all), style = MaterialTheme.typography.labelSmall) },
                    colors = chipColors,
                    border = chipBorder,
                )
                FilterChip(
                    selected = filterOption == AppFilterOption.INSTALLED,
                    onClick = { onFilterChange(AppFilterOption.INSTALLED) },
                    label = { Text(text = stringResource(R.string.filter_installed), style = MaterialTheme.typography.labelSmall) },
                    colors = chipColors,
                    border = chipBorder,
                )
                FilterChip(
                    selected = filterOption == AppFilterOption.NOT_INSTALLED,
                    onClick = { onFilterChange(AppFilterOption.NOT_INSTALLED) },
                    label = { Text(text = stringResource(R.string.filter_not_installed), style = MaterialTheme.typography.labelSmall) },
                    colors = chipColors,
                    border = chipBorder,
                )
                FilterChip(
                    selected = filterOption == AppFilterOption.UPDATES_AVAILABLE,
                    onClick = { onFilterChange(AppFilterOption.UPDATES_AVAILABLE) },
                    label = { Text(text = stringResource(R.string.filter_updates), style = MaterialTheme.typography.labelSmall) },
                    colors = chipColors,
                    border = chipBorder,
                )
                FilterChip(
                    selected = filterOption == AppFilterOption.FAVORITES,
                    onClick = { onFilterChange(AppFilterOption.FAVORITES) },
                    label = { Text(text = stringResource(R.string.filter_favorites), style = MaterialTheme.typography.labelSmall) },
                    colors = chipColors,
                    border = chipBorder,
                )
            }
        }
    }
}