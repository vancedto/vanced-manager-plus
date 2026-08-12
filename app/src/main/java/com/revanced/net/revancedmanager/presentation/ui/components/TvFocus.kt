package com.revanced.net.revancedmanager.presentation.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a highlight border around an element when it (or a focusable descendant —
 * e.g. the internal focus target of a Material [androidx.compose.material3.Button] or
 * [androidx.compose.material3.IconButton]) gains D-pad focus.
 *
 * Touch devices never fire focus events for these controls, so on phones/tablets this
 * modifier is a no-op. On Android TV / Google TV / Fire TV it makes the currently
 * selected control clearly visible while navigating with the remote.
 *
 * Apply it on the `modifier` passed to a clickable control so the border wraps the
 * whole control:
 * ```
 * Button(onClick = …, modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(8.dp)))
 * ```
 */
@Composable
fun Modifier.tvFocusBorder(
    shape: Shape = RoundedCornerShape(8.dp),
    color: Color = MaterialTheme.colorScheme.primary,
    width: Dp = 2.dp
): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        // hasFocus covers the case where the control adds its own focusable below us.
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .border(
            width = if (focused) width else 0.dp,
            color = if (focused) color else Color.Transparent,
            shape = shape
        )
}
