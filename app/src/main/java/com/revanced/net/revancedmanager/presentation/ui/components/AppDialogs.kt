package com.revanced.net.revancedmanager.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(items = dialogState.apps, key = { it.id }) { app ->
                        AppCheckRow(
                            app = app,
                            // The version change is what the user is being asked about; the package
                            // name is a tap away on the app's own page.
                            subtitle = app.currentVersion
                                ?.let { "v$it → v${app.latestVersion}" }
                                ?: "v${app.latestVersion}",
                            isChecked = checked[app.id] == true,
                            onCheckedChange = { checked[app.id] = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    dialogState.onUpdateSelected(checked.filterValues { it }.keys.toList())
                },
                enabled = selectedCount > 0,
                modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
            ) {
                Text(stringResource(R.string.update_selected, selectedCount))
            }
        },
        dismissButton = {
            FlowRow(
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = dialogState.onTurnOff,
                    modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                ) {
                    Text(stringResource(R.string.update_prompt_dont_show_again))
                }
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
