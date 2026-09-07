package com.revanced.net.revancedmanager.presentation.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.revanced.net.revancedmanager.R
import com.revanced.net.revancedmanager.domain.model.ThemeMode
import com.revanced.net.revancedmanager.presentation.ui.theme.RevancedManagerTheme

@Composable
fun ProcessingIndicatorButton(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(R.string.filter_processing, count)
    IconButton(
        onClick = onClick,
        modifier = modifier.tvFocusBorder(shape = RoundedCornerShape(50))
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = label
            }
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
            )
            val textStyle = if (count > 9) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelMedium
            }
            Text(
                text = "$count",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = textStyle,
                lineHeight = textStyle.fontSize,
                maxLines = 1,
                modifier = Modifier.wrapContentHeight()
            )
        }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Composable
private fun ProcessingIndicatorButtonLightPreview() {
    RevancedManagerTheme(themeMode = ThemeMode.LIGHT) {
        Surface {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProcessingIndicatorButton(count = 1, onClick = {})
                ProcessingIndicatorButton(count = 9, onClick = {})
                ProcessingIndicatorButton(count = 12, onClick = {})
            }
        }
    }
}

@Preview(name = "Dark Mode", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProcessingIndicatorButtonDarkPreview() {
    RevancedManagerTheme(themeMode = ThemeMode.DARK) {
        Surface {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProcessingIndicatorButton(count = 1, onClick = {})
                ProcessingIndicatorButton(count = 9, onClick = {})
                ProcessingIndicatorButton(count = 12, onClick = {})
            }
        }
    }
}
