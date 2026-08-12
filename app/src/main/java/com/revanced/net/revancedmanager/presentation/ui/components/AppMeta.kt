package com.revanced.net.revancedmanager.presentation.ui.components

import android.text.format.DateUtils
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.revanced.net.revancedmanager.R
import java.time.Instant

/** The placeholder every one of these patterns is built around. */
private const val ARG = "%1\$s"

/** What sits between two facts on the same line, everywhere in the app. */
private const val SEPARATOR = "  ·  "

/**
 * Label dimmed, value bright — the two-tone treatment the Installed/Latest line uses, so a glance
 * lands on the number rather than on the word in front of it.
 *
 * The placeholder can sit anywhere in a translated pattern, so the split is done by finding it
 * rather than by assuming the label comes first.
 */
@Composable
fun labelledValue(
    pattern: String,
    value: String,
    valueColor: Color,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
): AnnotatedString = buildAnnotatedString {
    appendLabelledValue(pattern, value, valueColor, labelColor)
}

private fun AnnotatedString.Builder.appendLabelledValue(
    pattern: String,
    value: String,
    valueColor: Color,
    labelColor: Color
) {
    val label = SpanStyle(color = labelColor, fontWeight = FontWeight.Normal)
    val strong = SpanStyle(color = valueColor, fontWeight = FontWeight.Medium)
    val at = pattern.indexOf(ARG)
    if (at < 0) {
        withStyle(strong) { append(value) }
        return
    }
    withStyle(label) { append(pattern.substring(0, at)) }
    withStyle(strong) { append(value) }
    withStyle(label) { append(pattern.substring(at + ARG.length)) }
}

/**
 * "Installed: 19.16.39 · Latest: 19.17.41" on one line, dotted apart the same way the size and
 * date line is.
 */
@Composable
fun appVersionsText(
    currentVersion: String?,
    latestVersion: String,
    installedColor: Color = MaterialTheme.colorScheme.primary,
    latestColor: Color = MaterialTheme.colorScheme.secondary,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
): AnnotatedString {
    val installedPattern = stringResource(R.string.installed_version)
    val latestPattern = stringResource(R.string.latest_version)
    return buildAnnotatedString {
        if (currentVersion != null) {
            appendLabelledValue(installedPattern, currentVersion, installedColor, labelColor)
            withStyle(SpanStyle(color = labelColor)) { append(SEPARATOR) }
        }
        appendLabelledValue(latestPattern, latestVersion, latestColor, labelColor)
    }
}

/**
 * "Size 172 MB · Updated 3 days ago" — how big the download is and how fresh the build is, both
 * worth knowing before tapping rather than after a 172 MB transfer has started.
 *
 * Returns null when the catalog carries neither, so the caller can drop the whole line instead of
 * printing a separator with nothing around it.
 */
@Composable
fun appMetaText(
    sizeText: String,
    updatedAt: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
): AnnotatedString? {
    val size = sizeText.takeIf { it.isNotBlank() }
    val updated = relativeUpdatedAt(updatedAt)
    if (size == null && updated == null) return null

    val sizeLabel = stringResource(R.string.app_details_size)
    val updatedPattern = stringResource(R.string.card_updated)

    return buildAnnotatedString {
        if (size != null) {
            appendLabelledValue("$sizeLabel $ARG", size, valueColor, labelColor)
        }
        if (updated != null) {
            if (size != null) {
                withStyle(SpanStyle(color = labelColor)) { append(SEPARATOR) }
            }
            appendLabelledValue(updatedPattern, updated, valueColor, labelColor)
        }
    }
}

/**
 * "3 days ago" from the ISO-8601 timestamp the v3 catalog publishes, or null for anything
 * unparseable — a missing date is left out rather than shown as a placeholder.
 */
fun relativeUpdatedAt(isoTimestamp: String): String? {
    if (isoTimestamp.isBlank()) return null
    return try {
        val millis = Instant.parse(isoTimestamp).toEpochMilli()
        DateUtils.getRelativeTimeSpanString(
            millis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    } catch (e: Exception) {
        null
    }
}
