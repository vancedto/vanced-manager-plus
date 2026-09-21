package com.revanced.net.revancedmanager.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revanced.net.revancedmanager.R

/**
 * First-run popup asking where apps should come from: everything in the catalog, or only the
 * ReVanced and Morphe builds. "All apps" is preselected — the community builds are the ones
 * keeping YouTube, TikTok and Telegram working, so hiding them is the opt-in.
 *
 * There is no dismiss button and tapping outside confirms the current selection: the answer has
 * to be recorded either way, or the dialog would come back on every launch.
 */
@Composable
fun AppSourceDialog(
    onChoose: (showCommunityApps: Boolean) -> Unit
) {
    var showCommunityApps by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = { onChoose(showCommunityApps) },
        title = {
            Text(
                text = stringResource(R.string.app_source_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.app_source_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(modifier = Modifier.selectableGroup()) {
                    AppSourceOption(
                        label = stringResource(R.string.app_source_option_all),
                        isSelected = showCommunityApps,
                        onSelect = { showCommunityApps = true }
                    )
                    AppSourceOption(
                        label = stringResource(R.string.app_source_option_official),
                        isSelected = !showCommunityApps,
                        onSelect = { showCommunityApps = false }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onChoose(showCommunityApps) },
                modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
            ) {
                Text(stringResource(R.string.app_source_continue))
            }
        }
    )
}

@Composable
private fun AppSourceOption(
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusBorder(shape = RoundedCornerShape(8.dp))
            .selectable(selected = isSelected, onClick = onSelect, role = Role.RadioButton)
            .then(
                if (isSelected) Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Selection is handled by the row, so the button itself is display only.
        RadioButton(selected = isSelected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
