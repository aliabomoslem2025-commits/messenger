/*
 * Copyright 2019-2026.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import im.vector.app.R

/**
 * Draws the chat wallpaper at its native vector dimensions.
 *
 * The chat Activity uses adjustResize, so the root view becomes shorter while the
 * keyboard is visible. A normal View background is laid out to the new bounds and
 * can scale its artwork. This drawable rasterizes the vector once and repeats it
 * vertically instead: its scale and horizontal alignment remain stable across IME
 * transitions while the newly visible bottom area is simply covered by more pattern.
 */
internal class TelegramChatWallpaperDrawable(context: Context) : Drawable() {

    private val wallpaperBitmap: Bitmap
    private val wallpaperShader: BitmapShader
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shaderMatrix = Matrix()

    init {
        val source = requireNotNull(
                ContextCompat.getDrawable(context, R.drawable.bg_telegram_chat_doodles_pattern)
        ) { "Chat wallpaper vector resource is missing" }

        val width = source.intrinsicWidth.coerceAtLeast(1)
        val height = source.intrinsicHeight.coerceAtLeast(1)
        wallpaperBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(wallpaperBitmap).also { bitmapCanvas ->
            source.setBounds(0, 0, width, height)
            source.draw(bitmapCanvas)
        }

        wallpaperShader = BitmapShader(
                wallpaperBitmap,
                Shader.TileMode.CLAMP,
                Shader.TileMode.REPEAT
        )
        paint.shader = wallpaperShader
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return

        // Keep the 360dp artwork centered horizontally and anchored to the top.
        // TileMode.REPEAT applies only on Y; CLAMP avoids horizontal distortion.
        val horizontalOffset = bounds.left + (bounds.width() - wallpaperBitmap.width) / 2f
        shaderMatrix.setTranslate(horizontalOffset, bounds.top.toFloat())
        wallpaperShader.setLocalMatrix(shaderMatrix)

        canvas.drawRect(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.right.toFloat(),
                bounds.bottom.toFloat(),
                paint
        )
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    override fun getIntrinsicWidth(): Int = wallpaperBitmap.width

    override fun getIntrinsicHeight(): Int = wallpaperBitmap.height
}
