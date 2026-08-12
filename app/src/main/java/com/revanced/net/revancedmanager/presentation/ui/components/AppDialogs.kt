package com.revanced.net.revancedmanager.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
            }
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
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        }
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
 * "N updates available" prompt with three choices:
 * update everything, snooze for today, or dismiss.
 */
@Composable
fun UpdatePromptDialog(dialogState: DialogState.UpdatePrompt) {
    AlertDialog(
        onDismissRequest = dialogState.onDismiss,
        title = {
            Text(
                text = stringResource(R.string.update_prompt_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = stringResource(R.string.update_prompt_message, dialogState.updateCount),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = dialogState.onUpdateAll,
                modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
            ) {
                Text(stringResource(R.string.update_all))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = dialogState.onSkipToday,
                    modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                ) {
                    Text(stringResource(R.string.skip_today))
                }
                TextButton(
                    onClick = dialogState.onDismiss,
                    modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}
