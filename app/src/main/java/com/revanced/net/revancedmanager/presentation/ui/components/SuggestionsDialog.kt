package com.revanced.net.revancedmanager.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.revanced.net.revancedmanager.R
import com.revanced.net.revancedmanager.domain.model.RevancedApp

/**
 * First-run popup suggesting a hand-picked set of apps. Every app starts
 * checked; the confirm button installs the checked ones, the dismiss button
 * skips the popup for good.
 */
@Composable
fun SuggestionsDialog(
    suggestedApps: List<RevancedApp>,
    onInstall: (List<String>) -> Unit,
    onSkip: () -> Unit
) {
    // All suggestions start selected
    val checked = remember(suggestedApps) {
        mutableStateMapOf<String, Boolean>().apply {
            suggestedApps.forEach { put(it.id, true) }
        }
    }
    val selectedCount = checked.count { it.value }

    AlertDialog(
        onDismissRequest = onSkip,
        title = {
            Text(
                text = stringResource(R.string.suggestions_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.suggestions_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.heightIn(max = dialogListMaxHeight())) {
                    items(items = suggestedApps, key = { it.id }) { app ->
                        AppCheckRow(
                            app = app,
                            subtitle = app.packageName,
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
                    onInstall(checked.filterValues { it }.keys.toList())
                },
                enabled = selectedCount > 0,
                modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
            ) {
                Text(stringResource(R.string.suggestions_install, selectedCount))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
            ) {
                Text(stringResource(R.string.suggestions_skip))
            }
        }
    )
}
