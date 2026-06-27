package com.revanced.net.revancedmanager.presentation.ui.theme

import android.graphics.Bitmap
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import kotlin.random.Random

private const val TILE_SIZE = 128

fun Modifier.noiseBackground(
    color: Color,
    noiseAlpha: Float = 0.04f,
): Modifier = composed {
    val noiseBrush = remember {
        val bitmap = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888)
        val rng = Random(42)
        val pixels = IntArray(TILE_SIZE * TILE_SIZE) {
            val v = rng.nextInt(256)
            android.graphics.Color.argb(255, v, v, v)
        }
        bitmap.setPixels(pixels, 0, TILE_SIZE, 0, 0, TILE_SIZE, TILE_SIZE)
        ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
    // Screen adds light specks on dark backgrounds (Overlay is near-invisible on near-black).
    // Multiply adds dark specks on light backgrounds.
    val blendMode = if (color.luminance() < 0.1f) BlendMode.Screen else BlendMode.Multiply
    drawBehind {
        drawRect(color = color)
        drawRect(brush = noiseBrush, alpha = noiseAlpha, blendMode = blendMode)
    }
}
