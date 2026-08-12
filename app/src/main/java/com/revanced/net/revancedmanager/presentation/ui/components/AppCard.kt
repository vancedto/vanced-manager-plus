package com.revanced.net.revancedmanager.presentation.ui.components

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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.revanced.net.revancedmanager.domain.model.AppStatus
import com.revanced.net.revancedmanager.domain.model.RevancedApp

/**
 * Card component for displaying app information
 * Replaces the old AppInfoCard with cleaner structure
 */
@Composable
fun AppCard(
    app: RevancedApp,
    onDownloadClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onOpenClick: () -> Unit,
    onReinstallClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onCancelDownload: () -> Unit,
    onOpenDetail: () -> Unit,
    isCompactMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Tapping the card opens the detail screen; the action buttons inside consume their own
    // clicks first, so nothing they do falls through to here.
    Card(
        onClick = onOpenDetail,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .tvFocusBorder(shape = RoundedCornerShape(16.dp)),
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
                    
                    Text(
                        text = appVersionsText(app.currentVersion, app.latestVersion),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Freshness only. Size is a download-time decision and the detail screen is
                    // where that decision gets made — carrying it in the list adds a number to
                    // every row for the few moments it matters.
                    appMetaText(sizeText = "", updatedAt = app.updatedAt)?.let { meta ->
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // Status indicator
                AppStatusIndicator(
                    status = app.status,
                    progress = app.downloadProgress
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // A two-line teaser only. Expanding it here used to be the sole way to read the rest;
            // now the whole card opens the detail screen, which has the full text and more besides,
            // so a show-more control on top of that would be two ways to do one thing.
            if (app.description.isNotBlank()) {
                Text(
                    text = app.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
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
            
            // Action buttons — shared with the detail screen so the two cannot disagree
            // about which actions a status affords
            AppActionButtons(
                app = app,
                onDownloadClick = onDownloadClick,
                onOpenClick = onOpenClick,
                onReinstallClick = onReinstallClick,
                onUninstallClick = onUninstallClick,
                onFavoriteToggle = onFavoriteToggle,
                onCancelDownload = onCancelDownload,
                compact = true,
                isCompactMode = isCompactMode
            )
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
