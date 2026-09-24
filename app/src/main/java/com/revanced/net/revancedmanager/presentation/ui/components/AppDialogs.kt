package com.revanced.net.revancedmanager.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.revanced.net.revancedmanager.R
import com.revanced.net.revancedmanager.presentation.bloc.AppEvent
import com.revanced.net.revancedmanager.presentation.bloc.DialogState

/**
 * Renders whichever dialog the bloc currently has open.
 *
 * Lives in a shared component because both the list and the detail screen need it: a re-install
 * confirmation started from the detail screen has to appear on the detail screen, and the bloc
 * holds one dialog state for the whole app rather than one per screen.
 */
@Composable
fun AppDialogHost(
    dialogState: DialogState?,
    onEvent: (AppEvent) -> Unit
) {
    when (dialogState) {
        null -> Unit
        is DialogState.Confirmation -> ConfirmationDialog(
            title = dialogState.title,
            message = dialogState.message,
            onConfirm = { dialogState.onConfirmAction() },
            onCancel = {
                dialogState.onCancelAction?.invoke() ?: onEvent(AppEvent.DismissDialog)
            },
            confirmLabel = dialogState.confirmLabel,
            cancelLabel = dialogState.cancelLabel,
            destructive = dialogState.destructive,
            showCancelButton = dialogState.showCancelButton
        )
        is DialogState.Progress -> ProgressDialog(
            title = dialogState.title,
            message = dialogState.message,
            progress = dialogState.progress
        )
        is DialogState.UpdatePrompt -> UpdatePromptDialog(dialogState)
    }
}

/**
 * Confirmation dialog component
 */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    confirmLabel: String? = null,
    cancelLabel: String? = null,
    destructive: Boolean = false,
    showCancelButton: Boolean = true
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
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(confirmLabel ?: stringResource(R.string.confirm))
            }
        },
        dismissButton = if (showCancelButton) {
            {
                TextButton(onClick = onCancel) {
                    Text(cancelLabel ?: stringResource(R.string.cancel))
                }
            }
        } else null
    )
}

/**
 * Progress dialog component
 */
@Composable
fun ProgressDialog(
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (progress != null) {
                    CircularProgressIndicator(progress = { progress })
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
 * "N updates available" prompt: every app with an update listed with its icon and version change,
 * each tickable, plus the choices to snooze for today, dismiss, or never ask again.
 *
 * Everything starts ticked, so the common answer — update the lot — is still one tap. Alongside
 * snooze and cancel sits a "don't show again" button, the inverse of "Update popup on launch" in
 * Settings, so the other common answer — never ask me this — is one tap, right where the asking happens.
 *
 * Four actions do not fit on one line in most languages, so they are laid out as two fixed rows
 * inside the confirm slot instead of being left to wrap: a wrapping row plus a list tall enough to
 * fill the screen pushed the last button off the bottom of the dialog. For the same reason the list
 * is capped against the screen height rather than a constant.
 */
@Composable
fun UpdatePromptDialog(dialogState: DialogState.UpdatePrompt) {
    val checked = remember(dialogState.apps) {
        mutableStateMapOf<String, Boolean>().apply {
            dialogState.apps.forEach { put(it.id, true) }
        }
    }
    val selectedCount = checked.count { it.value }

    AlertDialog(
        onDismissRequest = dialogState.onDismiss,
        title = {
            Text(
                text = stringResource(R.string.update_prompt_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.update_prompt_message, dialogState.apps.size),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = dialogListMaxHeight())) {
                    items(items = dialogState.apps, key = { it.id }) { app ->
                        AppCheckRow(
                            app = app,
                            // The version change is what the user is being asked about; the package
                            // name is a tap away on the app's own page.
                            subtitle = versionSubtitle(app.currentVersion, app.latestVersion),
                            isChecked = checked[app.id] == true,
                            onCheckedChange = { checked[app.id] = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactTextButton(
                        text = stringResource(R.string.update_prompt_dont_show_again),
                        onClick = dialogState.onTurnOff
                    )
                    CompactTextButton(
                        text = stringResource(R.string.skip_today),
                        onClick = dialogState.onSkipToday
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactTextButton(
                        text = stringResource(R.string.cancel),
                        onClick = dialogState.onDismiss
                    )
                    Button(
                        onClick = {
                            dialogState.onUpdateSelected(checked.filterValues { it }.keys.toList())
                        },
                        enabled = selectedCount > 0,
                        modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                    ) {
                        Text(
                            text = stringResource(R.string.update_selected, selectedCount),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    )
}

/**
 * "v1.0 → v1.1", or just the version we know when one of the two is missing: a dangling arrow
 * pointing at nothing reads like a bug.
 */
private fun versionSubtitle(currentVersion: String?, latestVersion: String): String = when {
    latestVersion.isBlank() -> currentVersion?.takeIf { it.isNotBlank() }?.let { "v$it" }.orEmpty()
    currentVersion.isNullOrBlank() -> "v$latestVersion"
    else -> "v$currentVersion → v$latestVersion"
}

/**
 * How tall a dialog's app list may grow. Capped against the screen so the title, message and
 * buttons always keep their room — a constant cap works on a tall phone and hides a button on a
 * short one.
 */
@Composable
internal fun dialogListMaxHeight(): Dp =
    (LocalConfiguration.current.screenHeightDp * 0.42f).dp.coerceIn(120.dp, 300.dp)

/**
 * A text button sized for a crowded dialog action row: default Material padding makes four actions
 * too wide for a phone, and a wrapped action row is what pushes buttons out of sight.
 */
@Composable
private fun CompactTextButton(
    text: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .defaultMinSize(minWidth = 1.dp, minHeight = 36.dp)
            .tvFocusBorder(shape = RoundedCornerShape(50)),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}
