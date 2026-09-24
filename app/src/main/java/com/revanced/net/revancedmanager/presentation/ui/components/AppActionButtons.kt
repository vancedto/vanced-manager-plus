package com.revanced.net.revancedmanager.presentation.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revanced.net.revancedmanager.R
import com.revanced.net.revancedmanager.domain.model.AppStatus
import com.revanced.net.revancedmanager.domain.model.RevancedApp
import com.revanced.net.revancedmanager.presentation.ui.theme.downloadColor
import com.revanced.net.revancedmanager.presentation.ui.theme.openColor
import com.revanced.net.revancedmanager.presentation.ui.theme.uninstallColor
import com.revanced.net.revancedmanager.presentation.ui.theme.updateColor
import kotlinx.coroutines.delay

/** How long an install may sit in INSTALLING before the user is offered a way to cancel it. */
private const val STUCK_INSTALL_GRACE_MS = 30_000L

/**
 * The status-driven action row — what an app offers right now, from Download through Open,
 * Re-install and Uninstall.
 *
 * Shared by the list card and the detail screen so the two cannot disagree about which action a
 * status affords. [compact] chooses the presentation: the card's small chips, or the taller
 * buttons the detail screen has room for.
 */
@Composable
fun AppActionButtons(
    app: RevancedApp,
    onDownloadClick: () -> Unit,
    onOpenClick: () -> Unit,
    onReinstallClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onCancelDownload: () -> Unit,
    onCancelInstall: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
    /**
     * The user's "compact mode" setting, which trims the card down to one action plus an icon.
     * Independent of [compact], which is about which screen is doing the drawing.
     */
    isCompactMode: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (app.status) {
            AppStatus.NOT_INSTALLED -> {
                ActionButton(
                    text = stringResource(R.string.download),
                    icon = Icons.Default.Download,
                    onClick = onDownloadClick,
                    color = MaterialTheme.colorScheme.downloadColor,
                    compact = compact,
                    modifier = Modifier.weight(1f)
                )
            }
            AppStatus.UPDATE_AVAILABLE -> {
                ActionButton(
                    text = stringResource(R.string.update),
                    icon = Icons.Default.Refresh,
                    onClick = onDownloadClick,
                    color = MaterialTheme.colorScheme.updateColor,
                    compact = compact,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    text = stringResource(R.string.open),
                    icon = Icons.Default.PlayArrow,
                    onClick = onOpenClick,
                    color = MaterialTheme.colorScheme.openColor,
                    compact = compact,
                    modifier = Modifier.weight(1f)
                )
                // An app with a pending update is still an installed app. Without this the only
                // way to remove one was to update it first — or leave the manager for the system
                // settings. The card keeps its two buttons; the detail screen has the room.
                // (No Re-install here: re-installing downloads the latest build, which is Update.)
                if (!compact) {
                    ActionButton(
                        text = stringResource(R.string.uninstall),
                        icon = Icons.Default.Delete,
                        onClick = onUninstallClick,
                        color = MaterialTheme.colorScheme.uninstallColor,
                        compact = compact,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            AppStatus.UP_TO_DATE -> {
                ActionButton(
                    text = stringResource(R.string.open),
                    icon = Icons.Default.PlayArrow,
                    onClick = onOpenClick,
                    color = MaterialTheme.colorScheme.openColor,
                    compact = compact,
                    modifier = Modifier.weight(1f)
                )
                // Compact mode keeps only Open plus an icon-sized uninstall; the detail screen
                // always has room for the full set.
                if (!isCompactMode || !compact) {
                    ActionButton(
                        text = stringResource(R.string.reinstall),
                        icon = Icons.Default.Sync,
                        onClick = onReinstallClick,
                        color = MaterialTheme.colorScheme.updateColor,
                        compact = compact,
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        text = stringResource(R.string.uninstall),
                        icon = Icons.Default.Delete,
                        onClick = onUninstallClick,
                        color = MaterialTheme.colorScheme.uninstallColor,
                        compact = compact,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .tvFocusBorder(shape = RoundedCornerShape(8.dp))
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
                // Tapping cancels. A download is background work that survives killing the app,
                // and these are large files — leaving the user no way out of one they started by
                // mistake is worse than the small risk of cancelling by accident.
                ActionButton(
                    text = stringResource(
                        R.string.downloading_cancel,
                        (app.downloadProgress * 100).toInt()
                    ),
                    icon = Icons.Default.Close,
                    onClick = onCancelDownload,
                    color = MaterialTheme.colorScheme.uninstallColor,
                    compact = compact,
                    modifier = Modifier.weight(1f)
                )
            }
            AppStatus.INSTALLING -> {
                // An install that hangs — a swallowed confirmation notification, a ROM that
                // silently drops the session — used to freeze the whole sequential queue with no
                // way out. After a grace period the button turns into a cancel.
                // Keyed by entry, not package: two entries can share one, and only one of them is
                // the install this timer is about.
                var cancellable by remember(app.id) { mutableStateOf(false) }
                LaunchedEffect(app.id) {
                    delay(STUCK_INSTALL_GRACE_MS)
                    cancellable = true
                }
                if (cancellable) {
                    ActionButton(
                        text = stringResource(R.string.cancel_installation),
                        icon = Icons.Default.Close,
                        onClick = onCancelInstall,
                        color = MaterialTheme.colorScheme.uninstallColor,
                        compact = compact,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    ActionButton(
                        text = "${stringResource(R.string.installing)}...",
                        icon = Icons.Default.Download,
                        onClick = { },
                        enabled = false,
                        compact = compact,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            AppStatus.UNINSTALLING -> {
                ActionButton(
                    text = "${stringResource(R.string.uninstalling)}...",
                    icon = Icons.Default.Delete,
                    onClick = { },
                    enabled = false,
                    compact = compact,
                    modifier = Modifier.weight(1f)
                )
            }
            AppStatus.READY_TO_INSTALL -> {
                // Downloaded and waiting its turn — installs run one at a time. Saying
                // "Installing" here claimed ten apps were installing at once during a batch
                // update; tapping cancels this app's place in the queue.
                ActionButton(
                    text = stringResource(R.string.waiting_to_install),
                    icon = Icons.Default.Close,
                    onClick = onCancelInstall,
                    color = MaterialTheme.colorScheme.uninstallColor,
                    compact = compact,
                    modifier = Modifier.weight(1f)
                )
            }
            AppStatus.UNKNOWN -> {
                ActionButton(
                    text = stringResource(R.string.unknown),
                    icon = Icons.Default.Download,
                    onClick = { },
                    enabled = false,
                    compact = compact,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Favorite toggle — always at far right of action row
        IconButton(
            onClick = onFavoriteToggle,
            modifier = Modifier
                .size(if (compact) 26.dp else 40.dp)
                .tvFocusBorder(shape = CircleShape)
        ) {
            Icon(
                imageVector = if (app.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = stringResource(
                    if (app.isFavorite) R.string.favorite_remove_title else R.string.favorite_add_title
                ),
                modifier = Modifier.size(if (compact) 14.dp else 22.dp),
                tint = if (app.isFavorite) Color(0xFFFFD700)
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One action, drawn either as the card's small chip or as a full-height button.
 *
 * The label shrinks to fit rather than truncating. Three buttons share one card row, and the words
 * behind them differ wildly by language — "Open" against "Deinstallieren" — so a fixed size either
 * wastes the row in English or cuts the label in half in German. Letting the type step down keeps
 * the whole word readable in every locale.
 */
@Composable
private fun RowScope.ActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(if (compact) 26.dp else 40.dp)
            .tvFocusBorder(shape = RoundedCornerShape(if (compact) 8.dp else 12.dp)),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(if (compact) 8.dp else 12.dp),
        contentPadding = if (compact) {
            PaddingValues(horizontal = 2.dp, vertical = 0.dp)
        } else {
            PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 11.dp else 16.dp)
        )
        Spacer(modifier = Modifier.width(if (compact) 3.dp else 6.dp))
        AutoSizeLabel(
            text = text,
            compact = compact,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

/**
 * A single-line label that steps its own size down until it fits the space it was given.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AutoSizeLabel(
    text: String,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val base = if (compact) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.labelLarge
    }
    BasicText(
        text = text,
        modifier = modifier,
        style = base.copy(
            color = LocalContentColor.current,
            textAlign = TextAlign.Center,
            // Both of these are absolute sp in the type scale, sized for the largest step. Left
            // in, they would not shrink with the font — the tracking would eat the room that was
            // just freed, and the fixed line height would push a shrunken label off centre.
            letterSpacing = 0.sp,
            lineHeight = TextUnit.Unspecified,
            lineHeightStyle = null
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = if (compact) 7.sp else 11.sp,
            maxFontSize = if (compact) 11.sp else 14.sp,
            stepSize = 0.5.sp
        )
    )
}
