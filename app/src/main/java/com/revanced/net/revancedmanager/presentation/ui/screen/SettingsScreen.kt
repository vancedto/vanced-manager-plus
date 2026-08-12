package com.revanced.net.revancedmanager.presentation.ui.screen

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revanced.net.revancedmanager.R
import com.revanced.net.revancedmanager.domain.model.AppConfig
import com.revanced.net.revancedmanager.domain.model.Language
import com.revanced.net.revancedmanager.domain.model.ThemeMode
import com.revanced.net.revancedmanager.presentation.bloc.ApkCacheInfo
import com.revanced.net.revancedmanager.presentation.ui.components.tvFocusBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen settings view.
 *
 * Owns its own Scaffold and top bar: settings is a navigation destination now, not a mode the
 * list screen switches into, so nothing above it is drawing an app bar on its behalf.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentConfig: AppConfig,
    onSave: (AppConfig) -> Unit,
    onCancel: () -> Unit,
    onResetSettings: () -> Unit,
    getLogContent: () -> String,
    onShareLogs: () -> Unit,
    getApkCacheInfo: () -> ApkCacheInfo,
    onClearApkCache: () -> ApkCacheInfo,
    modifier: Modifier = Modifier
) {
    var selectedTheme by remember(currentConfig) { mutableStateOf(currentConfig.themeMode) }
    var selectedLanguage by remember(currentConfig) { mutableStateOf(currentConfig.language) }
    var compactMode by remember(currentConfig) { mutableStateOf(currentConfig.compactMode) }
    var debugMode by remember(currentConfig) { mutableStateOf(currentConfig.debugModeEnabled) }
    var autoDeleteApk by remember(currentConfig) { mutableStateOf(currentConfig.autoDeleteApkEnabled) }
    var autoUpdateCheck by remember(currentConfig) { mutableStateOf(currentConfig.autoUpdateCheckEnabled) }
    var showUpdatePrompt by remember(currentConfig) { mutableStateOf(currentConfig.showUpdatePromptEnabled) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Appearance section
                SettingsSectionHeader(stringResource(R.string.settings_section_appearance))
                ThemeSelector(
                    selectedTheme = selectedTheme,
                    onThemeSelected = { selectedTheme = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Language section
                SettingsSectionHeader(stringResource(R.string.language))
                LanguageSelector(
                    selectedLanguage = selectedLanguage,
                    onLanguageSelected = { selectedLanguage = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Display section
                SettingsSectionHeader(stringResource(R.string.settings_section_display))
                SettingsSwitchRow(
                    title = stringResource(R.string.compact_mode),
                    checked = compactMode,
                    onCheckedChange = { compactMode = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Updates section
                SettingsSectionHeader(stringResource(R.string.settings_section_updates))
                SettingsSwitchRow(
                    title = stringResource(R.string.auto_update_check),
                    subtitle = stringResource(R.string.auto_update_check_description),
                    checked = autoUpdateCheck,
                    onCheckedChange = { autoUpdateCheck = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.show_update_prompt),
                    subtitle = stringResource(R.string.show_update_prompt_description),
                    checked = showUpdatePrompt,
                    onCheckedChange = { showUpdatePrompt = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Storage section
                SettingsSectionHeader(stringResource(R.string.settings_section_storage))
                SettingsSwitchRow(
                    title = stringResource(R.string.auto_delete_apk),
                    subtitle = stringResource(R.string.auto_delete_apk_description),
                    checked = autoDeleteApk,
                    onCheckedChange = { autoDeleteApk = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Debug section
                SettingsSectionHeader(stringResource(R.string.settings_section_debug))
                DebugModeSection(
                    debugMode = debugMode,
                    onDebugModeChange = { debugMode = it },
                    getLogContent = getLogContent,
                    onShareLogs = onShareLogs,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Danger Zone
                SettingsSectionHeader(stringResource(R.string.settings_section_danger_zone))
                ApkCacheCleanupButton(
                    getApkCacheInfo = getApkCacheInfo,
                    onClearApkCache = onClearApkCache,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                OutlinedButton(
                    onClick = { showResetConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .tvFocusBorder(shape = RoundedCornerShape(50))
                ) {
                    Text(stringResource(R.string.settings_reset_button))
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Sticky bottom action bar
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusBorder(shape = RoundedCornerShape(50))
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        onSave(
                            AppConfig(
                                themeMode = selectedTheme,
                                language = selectedLanguage,
                                compactMode = compactMode,
                                debugModeEnabled = debugMode,
                                autoDeleteApkEnabled = autoDeleteApk,
                                autoUpdateCheckEnabled = autoUpdateCheck,
                                showUpdatePromptEnabled = showUpdatePrompt
                            )
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusBorder(shape = RoundedCornerShape(50))
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }

    // Reset confirmation dialog
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.settings_reset_confirm_title)) },
            text = { Text(stringResource(R.string.settings_reset_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        onResetSettings()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// ---- Section header ----

@Composable
private fun SettingsSectionHeader(title: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp)
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}

// ---- Switch row ----

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
            )
        }
    }
}

// ---- APK cache cleanup ----

@Composable
private fun ApkCacheCleanupButton(
    getApkCacheInfo: () -> ApkCacheInfo,
    onClearApkCache: () -> ApkCacheInfo,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // null = still calculating
    var cacheInfo by remember { mutableStateOf<ApkCacheInfo?>(null) }
    var isClearing by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cacheInfo = withContext(Dispatchers.IO) { getApkCacheInfo() }
    }

    val info = cacheInfo
    val sizeText = info?.let {
        android.text.format.Formatter.formatShortFileSize(context, it.totalBytes)
    }
    val isEmpty = info != null && info.fileCount == 0

    val label = when {
        info == null -> stringResource(R.string.settings_clear_cache_calculating)
        isEmpty -> stringResource(R.string.settings_clear_cache_empty)
        else -> stringResource(R.string.settings_clear_cache_button, info.fileCount, sizeText ?: "")
    }

    OutlinedButton(
        onClick = { showConfirm = true },
        enabled = info != null && !isEmpty && !isClearing,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        ),
        modifier = modifier.tvFocusBorder(shape = RoundedCornerShape(50))
    ) {
        if (isClearing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(label)
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_cache_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_clear_cache_confirm_message,
                        sizeText ?: ""
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirm = false
                        isClearing = true
                        coroutineScope.launch {
                            val updated = withContext(Dispatchers.IO) { onClearApkCache() }
                            cacheInfo = updated
                            isClearing = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// ---- Debug section ----

@Composable
private fun DebugModeSection(
    debugMode: Boolean,
    onDebugModeChange: (Boolean) -> Unit,
    getLogContent: () -> String,
    onShareLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var showLogViewer by remember { mutableStateOf(false) }
    var logViewerContent by remember { mutableStateOf("") }
    var isLoadingLogs by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (debugMode) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (debugMode)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.debug_mode),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.debug_mode_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = debugMode,
                    onCheckedChange = onDebugModeChange,
                    modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                )
            }

            if (debugMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            showLogViewer = true
                            isLoadingLogs = true
                            coroutineScope.launch {
                                logViewerContent = withContext(Dispatchers.IO) { getLogContent() }
                                isLoadingLogs = false
                            }
                        },
                        modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = stringResource(R.string.debug_view_logs),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onShareLogs,
                        modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.debug_share_logs),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Log viewer dialog
    if (showLogViewer) {
        AlertDialog(
            onDismissRequest = { showLogViewer = false },
            title = { Text(stringResource(R.string.debug_logs_title)) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    if (isLoadingLogs) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        val scrollState = rememberScrollState()
                        Text(
                            text = logViewerContent.ifEmpty {
                                stringResource(R.string.debug_no_logs_available)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.verticalScroll(scrollState)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogViewer = false }) {
                    Text(stringResource(R.string.close_button))
                }
            }
        )
    }
}

// ---- Theme selector ----

@Composable
private fun ThemeSelector(
    selectedTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val collapseInteraction = remember { MutableInteractionSource() }
    val isHeaderFocused by collapseInteraction.collectIsFocusedAsState()

    if (expanded) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isHeaderFocused) 2.dp else 0.dp,
                            color = if (isHeaderFocused) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable(
                            interactionSource = collapseInteraction,
                            indication = LocalIndication.current
                        ) { expanded = false }
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.theme),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                ThemeMode.entries.forEach { mode ->
                    ThemeItem(
                        themeMode = mode,
                        isSelected = selectedTheme == mode,
                        onSelect = {
                            onThemeSelected(mode)
                            expanded = false
                        }
                    )
                }
            }
        }
    } else {
        Card(
            onClick = { expanded = true },
            shape = RoundedCornerShape(16.dp),
            modifier = modifier
                .fillMaxWidth()
                .tvFocusBorder(shape = RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = getThemeDisplayText(selectedTheme),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.tap_to_change),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeItem(
    themeMode: ThemeMode,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusBorder(shape = RoundedCornerShape(8.dp))
            .selectable(selected = isSelected, onClick = onSelect)
            .then(
                if (isSelected) Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when (themeMode) {
                ThemeMode.LIGHT -> "☀️"
                ThemeMode.DARK -> "🌙"
                ThemeMode.SYSTEM -> "⚙️"
            },
            fontSize = 18.sp,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = getThemeDisplayText(themeMode),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun getThemeDisplayText(themeMode: ThemeMode): String = when (themeMode) {
    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
    ThemeMode.DARK -> stringResource(R.string.theme_dark)
    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
}

// ---- Language selector ----

@Composable
private fun LanguageSelector(
    selectedLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val collapseInteraction = remember { MutableInteractionSource() }
    val isHeaderFocused by collapseInteraction.collectIsFocusedAsState()

    if (expanded) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isHeaderFocused) 2.dp else 0.dp,
                            color = if (isHeaderFocused) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable(
                            interactionSource = collapseInteraction,
                            indication = LocalIndication.current
                        ) { expanded = false }
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.select_language),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(Language.entries) { language ->
                        LanguageItem(
                            language = language,
                            isSelected = selectedLanguage == language,
                            onSelect = {
                                onLanguageSelected(language)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    } else {
        Card(
            onClick = { expanded = true },
            shape = RoundedCornerShape(16.dp),
            modifier = modifier
                .fillMaxWidth()
                .tvFocusBorder(shape = RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = selectedLanguage.flagEmoji,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Text(
                        text = selectedLanguage.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.tap_to_change),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageItem(
    language: Language,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusBorder(shape = RoundedCornerShape(8.dp))
            .selectable(selected = isSelected, onClick = onSelect)
            .then(
                if (isSelected) Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = language.flagEmoji,
            fontSize = 18.sp,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = language.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}
