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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.revanced.net.revancedmanager.presentation.bloc.AppSortOption
import com.revanced.net.revancedmanager.presentation.bloc.AppState
import com.revanced.net.revancedmanager.presentation.bloc.DialogState
import com.revanced.net.revancedmanager.presentation.bloc.clearApkCache
import com.revanced.net.revancedmanager.presentation.bloc.getApkCacheInfo
import com.revanced.net.revancedmanager.presentation.bloc.shareDebugLogs
import com.revanced.net.revancedmanager.presentation.ui.components.AppCard
import com.revanced.net.revancedmanager.presentation.ui.components.AppDialogHost
import com.revanced.net.revancedmanager.presentation.ui.components.SuggestionsDialog
import com.revanced.net.revancedmanager.presentation.ui.components.tvFocusBorder
import com.revanced.net.revancedmanager.presentation.ui.theme.noiseBackground

/**
 * Main screen of the ReVanced Manager app
 * Updated with improved dialog handling and better UX
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: AppBloc = hiltViewModel(),
    onOpenDetail: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val background = MaterialTheme.colorScheme.background
    val noiseAlpha = if (background.luminance() < 0.1f) 0.08f else 0.05f

    // When the last processing item finishes, leave the PROCESSING filter so the
    // user is not left staring at an empty list
    val successState = state as? AppState.Success
    LaunchedEffect(successState?.processingCount, successState?.filterOption) {
        if (successState?.filterOption == AppFilterOption.PROCESSING && successState.processingCount == 0) {
            viewModel.handleEvent(AppEvent.SetFilter(AppFilterOption.ALL))
        }
    }

    Scaffold(
        modifier = Modifier
            .noiseBackground(background, noiseAlpha)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
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
                    // Processing indicator: shows how many apps are being
                    // downloaded/installed/uninstalled; tap to filter to them
                    val processingCount = (state as? AppState.Success)?.processingCount ?: 0
                    AnimatedVisibility(visible = processingCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                    Text("$processingCount")
                                }
                            }
                        ) {
                            IconButton(
                                onClick = { viewModel.handleEvent(AppEvent.SetFilter(AppFilterOption.PROCESSING)) },
                                modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { viewModel.handleEvent(AppEvent.RefreshApps) },
                        modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.retry)
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                    ) {
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
    ) { paddingValues ->
        when (val currentState = state) {
            is AppState.Loading -> {
                LoadingScreen(modifier = Modifier.padding(paddingValues))
            }
            is AppState.Success -> {
                // Recomputing the filter runs search and predicate over every app, and the list
                // is heading for ~500 of them — so do it when the inputs change, not on every
                // recomposition.
                val filtered = remember(
                    currentState.apps,
                    currentState.searchQuery,
                    currentState.filterOption,
                    currentState.sortOption
                ) {
                    currentState.filteredApps
                }

                AppListScreen(
                    apps = filtered,
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
                    sortOption = currentState.sortOption,
                    onSortChange = { sort ->
                        viewModel.handleEvent(AppEvent.SetSort(sort))
                    },
                    onEvent = viewModel::handleEvent,
                    onOpenDetail = onOpenDetail,
                    isCompactMode = currentState.config.compactMode,
                    isRefreshing = currentState.isRefreshing,
                    onRefresh = { viewModel.handleEvent(AppEvent.PullToRefreshApps) },
                    processingCount = currentState.processingCount,
                    modifier = Modifier.padding(paddingValues)
                )

                // First-run suggestions popup (takes precedence over other dialogs)
                currentState.suggestedApps?.let { suggestions ->
                    SuggestionsDialog(
                        suggestedApps = suggestions,
                        onInstall = { selected ->
                            viewModel.handleEvent(AppEvent.InstallSuggestedApps(selected))
                        },
                        onSkip = { viewModel.handleEvent(AppEvent.DismissSuggestions) }
                    )
                }

                AppDialogHost(
                    dialogState = currentState.dialogState,
                    onEvent = viewModel::handleEvent
                )
            }
            is AppState.Error -> {
                ErrorScreen(
                    message = currentState.message,
                    onRetry = { viewModel.handleEvent(AppEvent.RefreshApps) },
                    modifier = Modifier.padding(paddingValues)
                )

                AppDialogHost(
                    dialogState = currentState.dialogState,
                    onEvent = viewModel::handleEvent
                )
            }
        }
    }
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppListScreen(
    apps: List<com.revanced.net.revancedmanager.domain.model.RevancedApp>,
    searchQuery: String = "",
    filterOption: AppFilterOption = AppFilterOption.ALL,
    onSearchQueryChange: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onFilterChange: (AppFilterOption) -> Unit = {},
    sortOption: AppSortOption = AppSortOption.CATALOG,
    onSortChange: (AppSortOption) -> Unit = {},
    onEvent: (AppEvent) -> Unit,
    onOpenDetail: (String) -> Unit = {},
    isCompactMode: Boolean = false,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    processingCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
        // Search + filter bar
        item {
            SearchAndFilterBar(
                query = searchQuery,
                filterOption = filterOption,
                onQueryChange = onSearchQueryChange,
                onClear = onClearSearch,
                onFilterChange = onFilterChange,
                sortOption = sortOption,
                onSortChange = onSortChange,
                processingCount = processingCount,
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
            // The catalog entry, not the package: the same package is patched by several groups
            // into several entries, and a repeated key makes LazyColumn throw.
            key = { app -> app.id },
            // Every row is the same layout, so tell LazyColumn it can reuse the nodes.
            contentType = { "app" }
        ) { app ->
            AppCard(
                app = app,
                onDownloadClick = {
                    onEvent(AppEvent.DownloadApp(app.packageName, app.downloadUrl))
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
                    onEvent(AppEvent.ToggleFavorite(app.id))
                },
                onCancelDownload = {
                    onEvent(AppEvent.CancelDownload(app.packageName))
                },
                onOpenDetail = { onOpenDetail(app.id) },
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
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .tvFocusBorder(shape = MaterialTheme.shapes.medium),
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
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .tvFocusBorder(shape = MaterialTheme.shapes.medium),
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
    sortOption: AppSortOption = AppSortOption.CATALOG,
    onSortChange: (AppSortOption) -> Unit = {},
    processingCount: Int = 0,
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
                        .tvFocusBorder(shape = MaterialTheme.shapes.medium)
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

            SortControl(sortOption = sortOption, onSortChange = onSortChange)
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
                val chipFocusShape = RoundedCornerShape(8.dp)
                FilterChip(
                    selected = filterOption == AppFilterOption.ALL,
                    onClick = { onFilterChange(AppFilterOption.ALL) },
                    label = { Text(text = stringResource(R.string.filter_all), style = MaterialTheme.typography.labelSmall) },
                    colors = chipColors,
                    border = chipBorder,
                    modifier = Modifier.tvFocusBorder(shape = chipFocusShape),
                )
                FilterChip(
                    selected = filterOption == AppFilterOption.INSTALLED,
                    onClick = { onFilterChange(AppFilterOption.INSTALLED) },
                    label = { Text(text = stringResource(R.string.filter_installed), style = MaterialTheme.typography.labelSmall) },
                    colors = chipColors,
                    border = chipBorder,
                    modifier = Modifier.tvFocusBorder(shape = chipFocusShape),
                )
                FilterChip(
                    selected = filterOption == AppFilterOption.NOT_INSTALLED,
                    onClick = { onFilterChange(AppFilterOption.NOT_INSTALLED) },
                    label = { Text(text = stringResource(R.string.filter_not_installed), style = MaterialTheme.typography.labelSmall) },
                    colors = chipColors,
                    border = chipBorder,
                    modifier = Modifier.tvFocusBorder(shape = chipFocusShape),
                )
                FilterChip(
                    selected = filterOption == AppFilterOption.UPDATES_AVAILABLE,
                    onClick = { onFilterChange(AppFilterOption.UPDATES_AVAILABLE) },
                    label = { Text(text = stringResource(R.string.filter_updates), style = MaterialTheme.typography.labelSmall) },
                    colors = chipColors,
                    border = chipBorder,
                    modifier = Modifier.tvFocusBorder(shape = chipFocusShape),
                )
                FilterChip(
                    selected = filterOption == AppFilterOption.FAVORITES,
                    onClick = { onFilterChange(AppFilterOption.FAVORITES) },
                    label = { Text(text = stringResource(R.string.filter_favorites), style = MaterialTheme.typography.labelSmall) },
                    colors = chipColors,
                    border = chipBorder,
                    modifier = Modifier.tvFocusBorder(shape = chipFocusShape),
                )
                // Only meaningful while something is in flight (or still selected)
                if (processingCount > 0 || filterOption == AppFilterOption.PROCESSING) {
                    FilterChip(
                        selected = filterOption == AppFilterOption.PROCESSING,
                        onClick = { onFilterChange(AppFilterOption.PROCESSING) },
                        label = {
                            Text(
                                text = stringResource(R.string.filter_processing, processingCount),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = chipColors,
                        border = chipBorder,
                        modifier = Modifier.tvFocusBorder(shape = chipFocusShape),
                    )
                }
            }
        }
    }
}

/**
 * Sort picker, as a menu rather than another chip row: the three options are mutually exclusive
 * and only one is ever active, which chips would not communicate next to the filter chips.
 *
 * Sorting matters at ~500 apps and barely at ~32, which is why the catalog's own curated order
 * stays the default.
 */
@Composable
private fun SortControl(
    sortOption: AppSortOption,
    onSortChange: (AppSortOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isActive = sortOption != AppSortOption.CATALOG

    Box {
        Box(
            modifier = Modifier
                .size(48.dp)
                .tvFocusBorder(shape = MaterialTheme.shapes.medium)
                .clip(MaterialTheme.shapes.medium)
                .background(
                    if (isActive) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(R.string.sort_label),
                modifier = Modifier.size(20.dp),
                tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SortMenuItem(R.string.sort_catalog, AppSortOption.CATALOG, sortOption) {
                expanded = false
                onSortChange(it)
            }
            SortMenuItem(R.string.sort_recently_updated, AppSortOption.RECENTLY_UPDATED, sortOption) {
                expanded = false
                onSortChange(it)
            }
            SortMenuItem(R.string.sort_name_asc, AppSortOption.NAME_ASC, sortOption) {
                expanded = false
                onSortChange(it)
            }
        }
    }
}

@Composable
private fun SortMenuItem(
    labelRes: Int,
    option: AppSortOption,
    selected: AppSortOption,
    onClick: (AppSortOption) -> Unit
) {
    androidx.compose.material3.DropdownMenuItem(
        text = {
            Text(
                text = stringResource(labelRes),
                color = if (option == selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        onClick = { onClick(option) }
    )
}
