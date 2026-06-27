package com.revanced.net.revancedmanager.presentation.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.revanced.net.revancedmanager.R
import com.revanced.net.revancedmanager.domain.model.AppStatus
import com.revanced.net.revancedmanager.domain.model.RevancedApp
import com.revanced.net.revancedmanager.presentation.ui.theme.downloadColor
import com.revanced.net.revancedmanager.presentation.ui.theme.openColor
import com.revanced.net.revancedmanager.presentation.ui.theme.uninstallColor
import com.revanced.net.revancedmanager.presentation.ui.theme.updateColor

/**
 * Card component for displaying app information
 * Replaces the old AppInfoCard with cleaner structure
 */
@Composable
fun AppCard(
    app: RevancedApp,
    onDownloadClick: () -> Unit,
    onInstallClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onOpenClick: () -> Unit,
    onReinstallClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    isCompactMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // App header (icon, title, version)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SubcomposeAsyncImage(
                    model = app.iconUrl,
                    contentDescription = "${app.title} icon",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                    loading = {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    },
                    error = {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "App icon placeholder",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    success = {
                        SubcomposeAsyncImageContent()
                    }
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        app.currentVersion?.let { currentVersion ->
                            Row {
                                Text(
                                    text = stringResource(R.string.installed_version, "").dropLast(1), // Remove placeholder
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "v${app.currentVersion}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Row {
                            Text(
                                text = stringResource(R.string.latest_version, "").dropLast(1), // Remove placeholder
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "v${app.latestVersion}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                
                // Status indicator
                AppStatusIndicator(
                    status = app.status,
                    progress = app.downloadProgress
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // App description with read more functionality
            var isExpanded by remember { mutableStateOf(false) }
            val descInteractionSource = remember { MutableInteractionSource() }
            val isDescFocused by descInteractionSource.collectIsFocusedAsState()
            val showLessInteractionSource = remember { MutableInteractionSource() }
            val isShowLessFocused by showLessInteractionSource.collectIsFocusedAsState()

            Column {
                if (isExpanded) {
                    // Full description - clickable to collapse
                    Text(
                        text = app.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .border(
                                width = if (isDescFocused) 2.dp else 0.dp,
                                color = if (isDescFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable(
                                interactionSource = descInteractionSource,
                                indication = LocalIndication.current
                            ) { isExpanded = false }
                    )

                    // MicroG requirement indicator
                    if (app.requiresMicroG) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚠️ Requires MicroG",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFA726)
                        )
                    }

                    // Show less button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isShowLessFocused) 2.dp else 0.dp,
                                color = if (isShowLessFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable(
                                interactionSource = showLessInteractionSource,
                                indication = LocalIndication.current
                            ) { isExpanded = false }
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.show_less),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.ExpandLess,
                            contentDescription = stringResource(R.string.show_less),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(start = 2.dp)
                        )
                    }
                } else {
                    // Collapsed description - clickable to expand
                    Text(
                        text = app.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .border(
                                width = if (isDescFocused) 2.dp else 0.dp,
                                color = if (isDescFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable(
                                interactionSource = descInteractionSource,
                                indication = LocalIndication.current
                            ) {
                                if (app.description.length > 100) {
                                    isExpanded = true
                                }
                            }
                    )
                }
            }
            
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Download progress indicator
            if (app.status == AppStatus.DOWNLOADING && app.downloadProgress > 0) {
                LinearProgressIndicator(
                    progress = { app.downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            // Action buttons - Compact design
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (app.status) {
                    AppStatus.NOT_INSTALLED -> {
                        CompactActionButton(
                            text = stringResource(R.string.download),
                            icon = Icons.Default.Download,
                            onClick = onDownloadClick,
                            color = MaterialTheme.colorScheme.downloadColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    AppStatus.UPDATE_AVAILABLE -> {
                        CompactActionButton(
                            text = stringResource(R.string.update),
                            icon = Icons.Default.Refresh,
                            onClick = onDownloadClick,
                            color = MaterialTheme.colorScheme.updateColor,
                            modifier = Modifier.weight(1f)
                        )
                        CompactActionButton(
                            text = stringResource(R.string.open),
                            icon =  Icons.Default.PlayArrow,
                            onClick = onOpenClick,
                            color = MaterialTheme.colorScheme.openColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    AppStatus.UP_TO_DATE -> {
                        CompactActionButton(
                            text = stringResource(R.string.open),
                            icon = Icons.Default.PlayArrow,
                            onClick = onOpenClick,
                            color = MaterialTheme.colorScheme.openColor,
                            modifier = Modifier.weight(1f)
                        )
                        if (!isCompactMode) {
                            CompactActionButton(
                                text = stringResource(R.string.reinstall),
                                icon = Icons.Default.Sync,
                                onClick = onReinstallClick,
                                color = MaterialTheme.colorScheme.updateColor,
                                modifier = Modifier.weight(1f)
                            )
                            CompactActionButton(
                                text = stringResource(R.string.uninstall),
                                icon = Icons.Default.Delete,
                                onClick = onUninstallClick,
                                color = MaterialTheme.colorScheme.uninstallColor,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            // Compact: icon-only uninstall button
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.uninstallColor,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onUninstallClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.uninstall),
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    AppStatus.DOWNLOADING -> {
                        CompactActionButton(
                            text = stringResource(R.string.downloading_progress, (app.downloadProgress * 100).toInt()),
                            icon = Icons.Default.Download,
                            onClick = { },
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    AppStatus.INSTALLING -> {
                        CompactActionButton(
                            text = "${stringResource(R.string.installing)}...",
                            icon = Icons.Default.Download,
                            onClick = { },
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    AppStatus.UNINSTALLING -> {
                        CompactActionButton(
                            text = "${stringResource(R.string.uninstalling)}...",
                            icon = Icons.Default.Delete,
                            onClick = { },
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    AppStatus.READY_TO_INSTALL -> {
                        CompactActionButton(
                            text = stringResource(R.string.installing),
                            icon = Icons.Default.Download,
                            onClick = onDownloadClick, // Use same click handler to trigger installation
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    AppStatus.UNKNOWN -> {
                        CompactActionButton(
                            text = stringResource(R.string.unknown),
                            icon = Icons.Default.Download,
                            onClick = { },
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Favorite toggle — always at far right of action row
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = if (app.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (app.isFavorite) Color(0xFFFFD700)
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Status indicator component
 */
@Composable
private fun AppStatusIndicator(
    status: AppStatus,
    progress: Float,
    modifier: Modifier = Modifier
) {
    when (status) {
        AppStatus.UP_TO_DATE -> {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = modifier.size(18.dp)
            )
        }
        AppStatus.UPDATE_AVAILABLE -> {
            Icon(
                imageVector = Icons.Default.ArrowCircleUp,
                contentDescription = null,
                tint = Color(0xFFF59E0B),
                modifier = modifier.size(18.dp)
            )
        }
        AppStatus.DOWNLOADING, AppStatus.INSTALLING, AppStatus.UNINSTALLING -> {
            CircularProgressIndicator(
                modifier = modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        AppStatus.READY_TO_INSTALL -> {
            Icon(
                imageVector = Icons.Default.InstallMobile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = modifier.size(18.dp)
            )
        }
        else -> {
            // No indicator for NOT_INSTALLED or UNKNOWN
        }
    }
}

/**
 * Compact action button component with vibrant colors
 */
@Composable
private fun CompactActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(26.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 3.dp,
            vertical = 2.dp
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}