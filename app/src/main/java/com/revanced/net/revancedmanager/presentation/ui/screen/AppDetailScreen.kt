package com.revanced.net.revancedmanager.presentation.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.revanced.net.revancedmanager.R
import com.revanced.net.revancedmanager.domain.model.AppStatus
import com.revanced.net.revancedmanager.domain.model.AppVariant
import com.revanced.net.revancedmanager.domain.model.RevancedApp
import com.revanced.net.revancedmanager.presentation.bloc.AppBloc
import com.revanced.net.revancedmanager.presentation.bloc.AppEvent
import com.revanced.net.revancedmanager.presentation.bloc.AppState
import com.revanced.net.revancedmanager.presentation.ui.components.AppActionButtons
import com.revanced.net.revancedmanager.presentation.ui.components.AppDialogHost
import com.revanced.net.revancedmanager.presentation.ui.components.appMetaText
import com.revanced.net.revancedmanager.presentation.ui.components.labelledValue
import com.revanced.net.revancedmanager.presentation.ui.components.tvFocusBorder
import com.revanced.net.revancedmanager.presentation.ui.theme.noiseBackground

/**
 * One app in full: everything the v3 catalog carries that the list has no room for — the long
 * description, the patches that went into the build, the published builds per architecture.
 *
 * Reads the same [AppBloc] as the list, so a download started here shows its progress in both
 * places and a confirmation opened here appears here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    appId: String,
    viewModel: AppBloc,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.background
    val noiseAlpha = if (background.luminance() < 0.1f) 0.08f else 0.05f

    val successState = state as? AppState.Success
    val app = successState?.apps?.find { it.id == appId }
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.noiseBackground(background, noiseAlpha),
        containerColor = Color.Transparent,
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {
                    Text(
                        text = app?.title ?: stringResource(R.string.app_details),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (app != null) {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.app_details_more)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_app)) },
                                onClick = {
                                    menuExpanded = false
                                    shareApp(context, app)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_package_name)) },
                                onClick = {
                                    menuExpanded = false
                                    copyToClipboard(context, app.packageName)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.package_name_copied),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                            app.website?.takeIf { it.isNotBlank() }?.let { website ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.open_website)) },
                                    onClick = {
                                        menuExpanded = false
                                        launchUrl(context, website)
                                    }
                                )
                            }
                            app.slug.takeIf { it.isNotBlank() }?.let { slug ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.open_app_page)) },
                                    onClick = {
                                        menuExpanded = false
                                        launchUrl(context, "https://vanced.to/$slug")
                                    }
                                )
                            }
                        }
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        if (app == null) {
            // Reachable if a refresh drops the app from the catalog while this screen is open.
            NotFoundContent(onBack = onBack, modifier = Modifier.padding(paddingValues))
            return@Scaffold
        }

        AppDetailContent(
            app = app,
            onEvent = viewModel::handleEvent,
            modifier = Modifier.padding(paddingValues)
        )

        // The bloc holds one dialog for the whole app, so a confirmation started from here has to
        // be drawn here too.
        AppDialogHost(
            dialogState = successState?.dialogState,
            onEvent = viewModel::handleEvent
        )
    }
}

@Composable
private fun AppDetailContent(
    app: RevancedApp,
    onEvent: (AppEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeaderSection(app)
        }

        item {
            // Progress belongs above the actions: while downloading, the button is disabled and
            // the bar is the only thing that moves.
            if (app.status == AppStatus.DOWNLOADING && app.downloadProgress > 0) {
                LinearProgressIndicator(
                    progress = { app.downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            AppActionButtons(
                app = app,
                onDownloadClick = { onEvent(AppEvent.DownloadApp(app.packageName, app.downloadUrl)) },
                onOpenClick = { onEvent(AppEvent.OpenApp(app.packageName)) },
                onReinstallClick = { onEvent(AppEvent.ShowReinstallConfirmation(app.packageName)) },
                onUninstallClick = { onEvent(AppEvent.UninstallApp(app.packageName)) },
                onFavoriteToggle = { onEvent(AppEvent.ToggleFavorite(app.id)) },
                onCancelDownload = { onEvent(AppEvent.CancelDownload(app.packageName)) },
                compact = false
            )
        }

        if (app.requiresMicroG) {
            item { MicroGCard() }
        }

        val description = app.longDescription.takeIf { it.isNotBlank() } ?: app.description
        if (description.isNotBlank()) {
            item {
                Section(title = stringResource(R.string.app_details_about)) {
                    // Plain text with newlines — the catalog is not markdown, so nothing to render.
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (app.features.isNotEmpty()) {
            item {
                Section(title = stringResource(R.string.app_details_features)) {
                    // A bullet list rather than chips: these average around ten entries and are
                    // full sentences, which wrap badly in chips.
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        app.features.forEach { feature ->
                            Row {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = feature,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (app.variants.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.app_details_variants),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    // The list is here to show what exists, not to ask a question: the build to
                    // install was already decided from Build.SUPPORTED_ABIS.
                    Text(
                        text = stringResource(R.string.app_details_variants_auto),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (app.isBestEffortVariant) {
                item {
                    BestEffortCard(
                        deviceAbi = deviceAbi(),
                        chosenArch = app.variants.firstOrNull { it.url == app.downloadUrl }?.arch
                            ?: ""
                    )
                }
            }

            items(app.variants, key = { it.arch + it.version }) { variant ->
                VariantRow(
                    variant = variant,
                    isSelected = variant.url.isNotBlank() && variant.url == app.downloadUrl,
                    isCompatible = isCompatibleWithDevice(variant.arch)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeaderSection(app: RevancedApp) {
    val context = LocalContext.current

    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            // The 200px icon exists for exactly this; the list keeps using the 64px one.
            model = app.iconLargeUrl.takeIf { it.isNotBlank() } ?: app.iconUrl,
            contentDescription = null,
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = app.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            val author = app.author.takeIf { it.isNotBlank() }
            if (author != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.app_details_by, author),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    app.provider?.takeIf { it.isNotBlank() }?.let { provider ->
                        Spacer(modifier = Modifier.width(6.dp))
                        ProviderBadge(provider)
                    }
                }
            }

            // Tapping copies it — the same thing the overflow menu offers, within reach of the
            // thumb that is already on the name.
            val packageCopied = stringResource(R.string.package_name_copied)
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .tvFocusBorder(shape = RoundedCornerShape(4.dp))
                    .clickable {
                        copyToClipboard(context, app.packageName)
                        Toast.makeText(context, packageCopied, Toast.LENGTH_SHORT).show()
                    }
            )

            Spacer(modifier = Modifier.height(4.dp))

            app.currentVersion?.let { current ->
                Text(
                    text = labelledValue(
                        pattern = stringResource(R.string.installed_version),
                        value = current,
                        valueColor = MaterialTheme.colorScheme.primary
                    ),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text(
                text = labelledValue(
                    pattern = stringResource(R.string.latest_version),
                    value = app.latestVersion,
                    valueColor = MaterialTheme.colorScheme.secondary
                ),
                style = MaterialTheme.typography.labelMedium
            )

            appMetaText(app.sizeText, app.updatedAt)?.let { meta ->
                Text(text = meta, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ProviderBadge(provider: String) {
    Text(
        text = provider,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun MicroGCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        )
    ) {
        Text(
            text = "⚠️ " + stringResource(R.string.app_details_requires_microg),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(12.dp)
        )
    }
}

/**
 * Shown when the app publishes nothing for this device's ABIs. The install is still offered —
 * ARM translation makes it work on most of the devices that land here — but saying so is better
 * than presenting a mismatched build as the right one.
 */
@Composable
private fun BestEffortCard(deviceAbi: String, chosenArch: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        )
    ) {
        Text(
            text = "⚠️ " + stringResource(
                R.string.app_details_variant_best_effort,
                deviceAbi,
                chosenArch
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        content()
    }
}

/**
 * One published build, for information only. Which build gets installed is not a question the
 * user is asked — [VariantSelector][com.revanced.net.revancedmanager.core.common.VariantSelector]
 * answered it from the device's own ABI list before this screen was drawn.
 */
@Composable
private fun VariantRow(
    variant: AppVariant,
    isSelected: Boolean,
    isCompatible: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = variant.arch,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (isSelected) {
                    Text(
                        text = stringResource(R.string.app_details_variant_selected),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (!isCompatible) {
                    Text(
                        text = stringResource(R.string.app_details_incompatible),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Text(
                text = "v${variant.version}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = variant.sizeText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NotFoundContent(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_not_found),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBack) {
                Text(stringResource(R.string.back))
            }
        }
    }
}

/** Whether a build's architecture is one this device can run. */
private fun isCompatibleWithDevice(arch: String): Boolean =
    arch.equals("universal", ignoreCase = true) ||
        Build.SUPPORTED_ABIS.any { it.equals(arch, ignoreCase = true) }

/** The device's preferred ABI, for explaining what the recommended build would have been. */
private fun deviceAbi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: "universal"

private fun shareApp(context: Context, app: RevancedApp) {
    val link = if (app.slug.isNotBlank()) "https://vanced.to/${app.slug}" else "https://vanced.to"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, app.title)
        putExtra(Intent.EXTRA_TEXT, "${app.title} — $link")
    }
    context.startActivity(Intent.createChooser(intent, app.title))
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(text, text))
}

private fun launchUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, url, Toast.LENGTH_SHORT).show()
    }
}
