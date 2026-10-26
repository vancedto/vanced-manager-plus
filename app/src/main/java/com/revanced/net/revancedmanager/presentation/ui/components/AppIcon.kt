package com.revanced.net.revancedmanager.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage

/**
 * An app's icon on a neutral tile. The tile is what shows while the image loads, and a generic
 * Android glyph replaces it when the image fails — the card used to show a spinner per row while
 * loading and a Download arrow on failure, which read as a button to press.
 *
 * Plain [AsyncImage] rather than SubcomposeAsyncImage: subcomposition per row is the slow path
 * Coil warns about for lazy lists.
 *
 * [contentDescription] is null wherever the title is printed next to the icon, so TalkBack does
 * not read the name twice.
 */
@Composable
fun AppIcon(
    url: String,
    size: Dp,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    var failed by remember(url) { mutableStateOf(url.isBlank()) }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (failed) {
            Icon(
                imageVector = Icons.Outlined.Android,
                contentDescription = contentDescription,
                modifier = Modifier.size(size * 0.55f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { failed = true }
            )
        }
    }
}
