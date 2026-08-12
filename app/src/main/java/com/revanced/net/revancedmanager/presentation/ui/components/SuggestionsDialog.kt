package com.revanced.net.revancedmanager.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(items = suggestedApps, key = { it.id }) { app ->
                        SuggestionRow(
                            app = app,
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

@Composable
private fun SuggestionRow(
    app: RevancedApp,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusBorder(shape = RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = app.iconUrl,
            contentDescription = app.title,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}
