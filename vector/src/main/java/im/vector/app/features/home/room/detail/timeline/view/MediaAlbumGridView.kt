/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

package im.vector.app.features.home.room.detail.timeline.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import im.vector.app.R
import im.vector.app.features.home.room.detail.timeline.helper.ContentDownloadOverlayBinder
import im.vector.app.features.home.room.detail.timeline.helper.ContentUploadStateTrackerBinder
import im.vector.app.core.files.LocalFilesHelper
import im.vector.app.features.home.room.detail.timeline.item.MediaAlbumTile
import im.vector.app.features.media.ImageContentRenderer
import kotlin.math.ceil
import kotlin.math.min

/**
 * Telegram-inspired collage layout for image/video albums. Tile positions are recalculated
 * from the remaining media count on every bind, so removal never leaves holes in the grid.
 */
class MediaAlbumGridView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    private data class TileViews(
            val tile: MediaAlbumTile,
            val image: ImageView
    )

    private val density = resources.displayMetrics.density
    private val gap = dp(2)
    private val tileViews = mutableListOf<TileViews>()
    private var measuredTileBounds: List<Rect> = emptyList()
    private var boundImageRenderer: ImageContentRenderer? = null
    private var boundDownloadBinder: ContentDownloadOverlayBinder? = null
    private var boundUploadBinder: ContentUploadStateTrackerBinder? = null

    init {
        clipChildren = true
        clipToPadding = true
    }

    fun bind(
            tiles: List<MediaAlbumTile>,
            imageContentRenderer: ImageContentRenderer,
            downloadOverlayBinder: ContentDownloadOverlayBinder,
            uploadStateTrackerBinder: ContentUploadStateTrackerBinder,
            onTileClick: ((MediaAlbumTile, android.view.View) -> Unit)?
    ) {
        clear()
        boundImageRenderer = imageContentRenderer
        boundDownloadBinder = downloadOverlayBinder
        boundUploadBinder = uploadStateTrackerBinder

        tiles.forEach { tile ->
            val frame = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.DKGRAY)
                    cornerRadius = dp(3).toFloat()
                }
                clipToOutline = true
                clipChildren = true
            }
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = tile.imageData.filename
                layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            }
            frame.addView(image)

            if (tile.videoContent != null) {
                val play = ImageView(context).apply {
                    setImageResource(R.drawable.ic_material_play_circle)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = tile.imageData.filename
                    layoutParams = FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER)
                    setOnClickListener { onTileClick?.invoke(tile, image) }
                }
                frame.addView(play)
            }

            val download = ImageView(context).apply {
                setImageResource(R.drawable.ic_telegram_download_arrow)
                scaleType = ImageView.ScaleType.CENTER
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0x99000000.toInt())
                }
                contentDescription = tile.imageData.filename
                isVisible = !tile.downloadUrl.isNullOrBlank()
                layoutParams = FrameLayout.LayoutParams(dp(36), dp(36), Gravity.BOTTOM or Gravity.END).apply {
                    rightMargin = dp(8)
                    bottomMargin = dp(8)
                }
            }
            frame.addView(download)

            val progress = ProgressBar(context).apply {
                isIndeterminate = true
                indeterminateDrawable = ContextCompat.getDrawable(context, R.drawable.bg_telegram_voice_download_indeterminate)
                indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
                isVisible = false
                layoutParams = FrameLayout.LayoutParams(dp(32), dp(32), Gravity.BOTTOM or Gravity.END).apply {
                    rightMargin = dp(10)
                    bottomMargin = dp(10)
                }
            }
            frame.addView(progress)

            val uploadProgressLayout = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(dp(9), dp(4), dp(9), dp(5))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(0xCC202124.toInt())
                    cornerRadius = dp(7).toFloat()
                }
                visibility = ViewGroup.GONE
                layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                    leftMargin = dp(7)
                    rightMargin = dp(7)
                    bottomMargin = dp(7)
                }
                addView(android.widget.TextView(context).apply {
                    id = R.id.mediaProgressTextView
                    textSize = 10f
                    maxLines = 1
                    setTextColor(Color.WHITE)
                }, android.widget.LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
                addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    id = R.id.mediaProgressBar
                    max = 100
                    progress = 0
                    isIndeterminate = true
                    progressTintList = ColorStateList.valueOf(Color.WHITE)
                    indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
                }, android.widget.LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(3)))
            }
            frame.addView(uploadProgressLayout)
            uploadStateTrackerBinder.bind(
                    eventId = tile.eventId,
                    isLocalFile = LocalFilesHelper(context).isLocalFile(tile.imageData.url),
                    progressLayout = uploadProgressLayout
            )

            imageContentRenderer.render(
                    tile.imageData,
                    ImageContentRenderer.Mode.THUMBNAIL,
                    image,
                    RoundedCorners(dp(3))
            )
            // The renderer sizes standalone media; a collage tile must fill its calculated slot.
            image.layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            image.setOnClickListener { onTileClick?.invoke(tile, image) }

            tile.downloadUrl?.let { url ->
                downloadOverlayBinder.bind(
                        eventId = tile.eventId,
                        mxcUrl = url,
                        progress = progress,
                        downloadIcon = download,
                        showPlayIconOnSuccess = false,
                        messageContent = tile.messageContent,
                        onMediaClick = { onTileClick?.invoke(tile, image) }
                )
            }

            addView(frame)
            tileViews.add(TileViews(tile, image))
        }
        requestLayout()
    }

    fun clear() {
        tileViews.forEach { tile ->
            boundImageRenderer?.clear(tile.image)
            boundDownloadBinder?.unbind(tile.tile.eventId)
            boundUploadBinder?.unbind(tile.tile.eventId)
            tile.image.setOnClickListener(null)
        }
        removeAllViews()
        tileViews.clear()
        measuredTileBounds = emptyList()
        boundImageRenderer = null
        boundDownloadBinder = null
        boundUploadBinder = null
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val specWidth = MeasureSpec.getSize(widthMeasureSpec)
        val fallbackWidth = (resources.displayMetrics.widthPixels - dp(64)).coerceAtLeast(dp(180))
        val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> fallbackWidth
            else -> specWidth.coerceAtLeast(dp(120))
        }
        val desiredHeight = calculateHeight(width, childCount)
        val resolvedWidth = resolveSize(width, widthMeasureSpec)
        val resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(resolvedWidth, resolvedHeight)
        measuredTileBounds = calculateBounds(resolvedWidth, resolvedHeight, childCount)
        measuredTileBounds.forEachIndexed { index, bounds ->
            getChildAt(index).measure(
                    MeasureSpec.makeMeasureSpec(bounds.width(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(bounds.height(), MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val bounds = if (measuredTileBounds.size == childCount) measuredTileBounds
        else calculateBounds(right - left, bottom - top, childCount)
        bounds.forEachIndexed { index, rect ->
            getChildAt(index).layout(rect.left, rect.top, rect.right, rect.bottom)
        }
    }

    private fun calculateHeight(width: Int, count: Int): Int {
        if (count <= 1) return width

        // Telegram's group layout considers source proportions, not only item count.
        // Missing/broken dimensions fall back to a square so they cannot collapse the album.
        val averageAspectRatio = tileViews
                .map { tile ->
                    val sourceWidth = tile.tile.imageData.width?.takeIf { it > 0 } ?: 1
                    val sourceHeight = tile.tile.imageData.height?.takeIf { it > 0 } ?: 1
                    sourceWidth.toFloat() / sourceHeight.toFloat()
                }
                .average()
                .toFloat()
                .takeIf { it.isFinite() && it > 0f }
                ?: 1f

        val portraitFactor = when {
            averageAspectRatio < 0.72f -> 1.34f
            averageAspectRatio < 0.95f -> 1.18f
            averageAspectRatio < 1.25f -> 1.02f
            averageAspectRatio < 1.65f -> 0.84f
            else -> 0.66f
        }
        val cellWidth = (width - gap * 2) / 3f
        val raw = when (count) {
            2 -> width * when {
                averageAspectRatio > 1.65f -> 0.52f
                averageAspectRatio < 0.82f -> 0.92f
                else -> 0.70f
            }
            // Three-media Telegram albums commonly use a tall left tile and two stacked
            // tiles on the right, matching the supplied reference screenshot.
            3 -> width * portraitFactor
            4 -> width * when {
                averageAspectRatio > 1.55f -> 0.60f
                averageAspectRatio < 0.80f -> 0.92f
                else -> 0.76f
            }
            5 -> width * when {
                averageAspectRatio > 1.55f -> 0.70f
                averageAspectRatio < 0.80f -> 1.08f
                else -> 0.88f
            }
            else -> cellWidth * ceil(count / 3f) * portraitFactor.coerceIn(0.72f, 1.08f)
        }
        return raw.toInt().coerceIn(dp(116), dp(420))
    }

    private fun calculateBounds(width: Int, height: Int, count: Int): List<Rect> {
        if (count == 0) return emptyList()
        if (count == 1) return listOf(Rect(0, 0, width, height))
        val result = ArrayList<Rect>(count)
        when (count) {
            2 -> {
                val columnWidth = (width - gap) / 2
                result += Rect(0, 0, columnWidth, height)
                result += Rect(columnWidth + gap, 0, width, height)
            }
            3 -> {
                val leftWidth = ((width - gap) * 0.62f).toInt()
                val rightLeft = leftWidth + gap
                val rowHeight = (height - gap) / 2
                result += Rect(0, 0, leftWidth, height)
                result += Rect(rightLeft, 0, width, rowHeight)
                result += Rect(rightLeft, rowHeight + gap, width, height)
            }
            4 -> {
                val columnWidth = (width - gap) / 2
                val rowHeight = (height - gap) / 2
                result += Rect(0, 0, columnWidth, rowHeight)
                result += Rect(columnWidth + gap, 0, width, rowHeight)
                result += Rect(0, rowHeight + gap, columnWidth, height)
                result += Rect(columnWidth + gap, rowHeight + gap, width, height)
            }
            5 -> {
                val leftWidth = ((width - gap) * 0.60f).toInt()
                val rightLeft = leftWidth + gap
                val rightWidth = width - rightLeft
                val halfRight = (rightWidth - gap) / 2
                val rowHeight = (height - gap) / 2
                result += Rect(0, 0, leftWidth, height)
                result += Rect(rightLeft, 0, rightLeft + halfRight, rowHeight)
                result += Rect(rightLeft + halfRight + gap, 0, width, rowHeight)
                result += Rect(rightLeft, rowHeight + gap, rightLeft + halfRight, height)
                result += Rect(rightLeft + halfRight + gap, rowHeight + gap, width, height)
            }
            else -> {
                val rows = ceil(count / 3f).toInt()
                val rowHeight = (height - gap * (rows - 1)) / rows
                repeat(rows) { row ->
                    val firstIndex = row * 3
                    val columns = min(3, count - firstIndex)
                    val columnWidth = (width - gap * (columns - 1)) / columns
                    repeat(columns) { column ->
                        val x = column * (columnWidth + gap)
                        val y = row * (rowHeight + gap)
                        val actualRight = if (column == columns - 1) width else x + columnWidth
                        val actualBottom = if (row == rows - 1) height else y + rowHeight
                        result += Rect(x, y, actualRight, actualBottom)
                    }
                }
            }
        }
        return result
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
