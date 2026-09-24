package com.revanced.net.revancedmanager.presentation.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import com.revanced.net.revancedmanager.presentation.ui.components.ProcessingIndicatorButton
import com.revanced.net.revancedmanager.presentation.ui.components.AppSourceDialog
import com.revanced.net.revancedmanager.presentation.ui.components.SuggestionsDialog
import com.revanced.net.revancedmanager.presentation.ui.components.tvFocusBorder
import com.revanced.net.revancedmanager.presentation.ui.theme.noiseBackground
import com.revanced.net.revancedmanager.presentation.ui.theme.updateColor

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

    var searchActive by rememberSaveable { mutableStateOf(false) }
    var filterExpanded by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val successState = state as? AppState.Success
    val isFilterActive = (successState?.filterOption ?: AppFilterOption.ALL) != AppFilterOption.ALL
    val showChips = (filterExpanded || isFilterActive) && successState != null

    // Auto-show chips when a filter becomes active from outside
    LaunchedEffect(isFilterActive) {
        if (isFilterActive) filterExpanded = true
    }

    // Pin top bar and focus search field when entering search mode
    LaunchedEffect(searchActive) {
        if (searchActive) {
            scrollBehavior.state.heightOffset = 0f
            focusRequester.requestFocus()
        }
    }

    // Intercept system back button to exit search before exiting screen
    BackHandler(enabled = searchActive) {
        viewModel.handleEvent(AppEvent.ClearSearch)
        searchActive = false
    }

    // Scroll list to top when search query, filter option, or sort option changes
    var isFirstRun by remember { mutableStateOf(true) }
    LaunchedEffect(
        successState?.searchQuery,
        successState?.filterOption,
        successState?.sortOption
    ) {
        if (isFirstRun) {
            isFirstRun = false
        } else if (successState != null) {
            listState.scrollToItem(0)
        }
    }

    // When the last processing item finishes, leave the PROCESSING filter so the
    // user is not left staring at an empty list
    LaunchedEffect(successState?.processingCount, successState?.filterOption) {
        if (successState?.filterOption == AppFilterOption.PROCESSING && successState.processingCount == 0) {
            viewModel.handleEvent(AppEvent.SetFilter(AppFilterOption.ALL))
        }
    }

    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 8 }
    }

    Scaffold(
        modifier = Modifier
            .noiseBackground(background, noiseAlpha)
            .then(
                if (searchActive) Modifier
                else Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
            ),
        containerColor = Color.Transparent,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (searchActive) {
                            TextField(
                                value = successState?.searchQuery ?: "",
                                onValueChange = { viewModel.handleEvent(AppEvent.SearchApps(it)) },
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.search_apps),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(R.string.app_subtitle),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (searchActive) {
                            IconButton(
                                onClick = {
                                    viewModel.handleEvent(AppEvent.ClearSearch)
                                    searchActive = false
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .tvFocusBorder(shape = RoundedCornerShape(50))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.search_close),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        if (searchActive) {
                            val query = successState?.searchQuery ?: ""
                            if (query.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.handleEvent(AppEvent.ClearSearch) },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .tvFocusBorder(shape = RoundedCornerShape(50))
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = stringResource(R.string.clear_search),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        } else {
                            // Processing indicator: shows how many apps are being
                            // downloaded/installed/uninstalled; tap to filter to them
                            val processingCount = successState?.processingCount ?: 0
                            AnimatedVisibility(visible = processingCount > 0) {
                                ProcessingIndicatorButton(
                                    count = processingCount,
                                    onClick = {
                                        // Same toggle rule as the chips: tapping it while
                                        // already filtered to processing goes back to all
                                        val current = successState?.filterOption
                                        viewModel.handleEvent(
                                            AppEvent.SetFilter(
                                                if (current == AppFilterOption.PROCESSING) AppFilterOption.ALL
                                                else AppFilterOption.PROCESSING
                                            )
                                        )
                                    }
                                )
                            }
                            if (successState != null) {
                                IconButton(
                                    onClick = { searchActive = true },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .tvFocusBorder(shape = RoundedCornerShape(50))
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = stringResource(R.string.search_label),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                BadgedBox(
                                    badge = {
                                        if (isFilterActive) {
                                            Badge(containerColor = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                ) {
                                    IconButton(
                                        onClick = { filterExpanded = !filterExpanded },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .tvFocusBorder(shape = RoundedCornerShape(50))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.FilterAlt,
                                            contentDescription = stringResource(R.string.filter_label),
                                            modifier = Modifier.size(20.dp),
                                            // LocalContentColor is what the untinted action
                                            // icons (search/refresh/settings) already use, so
                                            // the inactive state matches them exactly
                                            tint = if (isFilterActive) MaterialTheme.colorScheme.primary
                                                   else LocalContentColor.current
                                        )
                                    }
                                }
                                SortControl(
                                    sortOption = successState.sortOption,
                                    onSortChange = { viewModel.handleEvent(AppEvent.SetSort(it)) }
                                )
                            }
                            IconButton(
                                onClick = { viewModel.handleEvent(AppEvent.RefreshApps) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .tvFocusBorder(shape = RoundedCornerShape(50))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.refresh),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier
                                    .size(40.dp)
                                    .tvFocusBorder(shape = RoundedCornerShape(50))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = stringResource(R.string.settings),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    )
                )

                AnimatedVisibility(
                    visible = showChips,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    if (successState != null) {
                        val filterCounts = remember(
                            successState.apps,
                            successState.searchQuery,
                            successState.config.showCommunityApps
                        ) {
                            successState.filterCounts
                        }
                        FilterChipsRow(
                            filterOption = successState.filterOption,
                            onFilterChange = { viewModel.handleEvent(AppEvent.SetFilter(it)) },
                            processingCount = successState.processingCount,
                            counts = filterCounts,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showScrollToTop,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.scroll_to_top)
                    )
                }
            }
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
                    currentState.sortOption,
                    currentState.config.showCommunityApps
                ) {
                    currentState.filteredApps
                }
                val updatableCount = remember(currentState.apps, currentState.config.showCommunityApps) {
                    currentState.updatableCount
                }

                AppListScreen(
                    apps = filtered,
                    searchQuery = currentState.searchQuery,
                    filterOption = currentState.filterOption,
                    updatableCount = updatableCount,
                    onEvent = viewModel::handleEvent,
                    onOpenDetail = onOpenDetail,
                    isCompactMode = currentState.config.compactMode,
                    isRefreshing = currentState.isRefreshing,
                    onRefresh = { viewModel.handleEvent(AppEvent.PullToRefreshApps) },
                    listState = listState,
                    modifier = Modifier.padding(paddingValues)
                )

                // First-run app source question — decides what the suggestions popup and the
                // update prompt may list, so it comes before both.
                if (currentState.askAppSource) {
                    AppSourceDialog(
                        onChoose = { viewModel.handleEvent(AppEvent.ChooseAppSource(it)) }
                    )
                }

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
    updatableCount: Int = 0,
    onEvent: (AppEvent) -> Unit,
    onOpenDetail: (String) -> Unit = {},
    isCompactMode: Boolean = false,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {

        // "Update all" used to be reachable only from the launch prompt and the daily
        // notification: dismiss the prompt once and the only way left was updating app by app.
        // Shown on the views where updates are what the user is looking at.
        val showUpdateBanner = updatableCount > 0 && searchQuery.isBlank() && filterOption in setOf(
            AppFilterOption.ALL,
            AppFilterOption.INSTALLED,
            AppFilterOption.UPDATES_AVAILABLE
        )
        if (showUpdateBanner) {
            item(key = "update_all_banner", contentType = "banner") {
                UpdateAllBanner(
                    count = updatableCount,
                    onUpdateAll = { onEvent(AppEvent.UpdateAllApps) }
                )
            }
        }

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
                    onEvent(AppEvent.DownloadApp(app.id, app.packageName, app.downloadUrl))
                },
                onUninstallClick = {
                    onEvent(AppEvent.UninstallApp(app.packageName))
                },
                onReinstallClick = {
                    onEvent(AppEvent.ShowReinstallConfirmation(app.id, app.packageName))
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
                onCancelInstall = {
                    onEvent(AppEvent.CancelInstallation(app.packageName))
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
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.visit_website),
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
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.source_code),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * "N updates available · Update all", at the top of the list.
 */
@Composable
private fun UpdateAllBanner(
    count: Int,
    onUpdateAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.updateColor.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.ArrowCircleUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.updateColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.updates_banner, count),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onUpdateAll,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.updateColor,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(10.dp))
        ) {
            Text(
                text = stringResource(R.string.update_all),
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
        Toast.makeText(context, context.getString(R.string.open_url_failed), Toast.LENGTH_SHORT).show()
    }
}

/**
 * Filter chips row displayed under the TopAppBar
 */
@Composable
private fun FilterChipsRow(
    filterOption: AppFilterOption,
    onFilterChange: (AppFilterOption) -> Unit,
    processingCount: Int,
    counts: Map<AppFilterOption, Int>,
    modifier: Modifier = Modifier
) {
    // "Installed (12)": the number is the length of the list behind the chip, so an empty filter
    // shows as empty before it is tapped
    @Composable
    fun label(labelRes: Int, option: AppFilterOption): String =
        stringResource(R.string.filter_with_count, stringResource(labelRes), counts[option] ?: 0)

    Row(
        modifier = modifier
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
        // Tapping the chip that is already applied clears the filter instead of re-applying it,
        // so "Favorites" -> "Favorites" lands back on the full list without a trip to "All".
        val onChipClick: (AppFilterOption) -> Unit = { option ->
            onFilterChange(if (filterOption == option) AppFilterOption.ALL else option)
        }
        FilterChip(
            selected = filterOption == AppFilterOption.ALL,
            onClick = { onFilterChange(AppFilterOption.ALL) },
            label = { Text(text = label(R.string.filter_all, AppFilterOption.ALL), style = MaterialTheme.typography.labelSmall) },
            colors = chipColors,
            border = chipBorder,
            modifier = Modifier.tvFocusBorder(shape = chipFocusShape),
        )
        FilterChip(
            selected = filterOption == AppFilterOption.INSTALLED,
            onClick = { onChipClick(AppFilterOption.INSTALLED) },
            label = { Text(text = label(R.string.filter_installed, AppFilterOption.INSTALLED), style = MaterialTheme.typography.labelSmall) },
            colors = chipColors,
            border = chipBorder,
            modifier = Modifier.tvFocusBorder(shape = chipFocusShape),
        )
        FilterChip(
            selected = filterOption == AppFilterOption.NOT_INSTALLED,
            onClick = { onChipClick(AppFilterOption.NOT_INSTALLED) },
            label = { Text(text = label(R.string.filter_not_installed, AppFilterOption.NOT_INSTALLED), style = MaterialTheme.typography.labelSmall) },
            colors = chipColors,
            border = chipBorder,
            modifier = Modifier.tvFocusBorder(shape = chipFocusShape),
        )
        FilterChip(
            selected = filterOption == AppFilterOption.UPDATES_AVAILABLE,
            onClick = { onChipClick(AppFilterOption.UPDATES_AVAILABLE) },
            label = { Text(text = label(R.string.filter_updates, AppFilterOption.UPDATES_AVAILABLE), style = MaterialTheme.typography.labelSmall) },
            colors = chipColors,
            border = chipBorder,
            modifier = Modifier.tvFocusBorder(shape = chipFocusShape),
        )
        FilterChip(
            selected = filterOption == AppFilterOption.FAVORITES,
            onClick = { onChipClick(AppFilterOption.FAVORITES) },
            label = { Text(text = label(R.string.filter_favorites, AppFilterOption.FAVORITES), style = MaterialTheme.typography.labelSmall) },
            colors = chipColors,
            border = chipBorder,
            modifier = Modifier.tvFocusBorder(shape = chipFocusShape),
        )
        // Only meaningful while something is in flight (or still selected)
        if (processingCount > 0 || filterOption == AppFilterOption.PROCESSING) {
            FilterChip(
                selected = filterOption == AppFilterOption.PROCESSING,
                onClick = { onChipClick(AppFilterOption.PROCESSING) },
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
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier
                .size(40.dp)
                .tvFocusBorder(shape = RoundedCornerShape(50))
        ) {
            Icon(
                // Material Symbols "list_arrow"; not in material-icons-extended, so it ships
                // as a local vector drawable
                painter = painterResource(id = R.drawable.ic_list_arrow),
                contentDescription = stringResource(R.string.sort_label),
                modifier = Modifier.size(20.dp),
                tint = if (isActive) MaterialTheme.colorScheme.primary
                       else LocalContentColor.current
            )
        }

        DropdownMenu(
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
