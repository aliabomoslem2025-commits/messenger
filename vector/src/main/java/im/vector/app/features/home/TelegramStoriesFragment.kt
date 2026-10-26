/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

package im.vector.app.features.home

import android.content.Intent
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.core.platform.VectorBaseFragment
import im.vector.app.databinding.FragmentTelegramStoriesBinding
import im.vector.app.features.media.ImageContentRenderer
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Story hub for Messenger's app-specific encrypted Matrix Stories format.
 *
 * Images and videos are uploaded as regular Matrix media events in private,
 * encrypted, custom-type story rooms. Story expiration is client-enforced; the
 * Matrix homeserver may retain historical events according to its retention policy.
 */
@AndroidEntryPoint
class TelegramStoriesFragment : VectorBaseFragment<FragmentTelegramStoriesBinding>() {

    @Inject lateinit var storiesRepository: TelegramStoriesRepository
    @Inject lateinit var imageContentRenderer: ImageContentRenderer

    private var stories: List<TelegramStoriesRepository.Story> = emptyList()

    private val photoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) publishStory(uri)
    }

    private val videoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) publishStory(uri)
    }

    override fun getBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentTelegramStoriesBinding {
        return FragmentTelegramStoriesBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        views.telegramStoriesAddPhoto.setOnClickListener { photoPicker.launch(arrayOf("image/*")) }
        views.telegramStoriesAddVideo.setOnClickListener { videoPicker.launch(arrayOf("video/*")) }
        reloadStories()
    }

    private fun publishStory(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            views.telegramStoriesStatus.text = getString(R.string.telegram_stories_loading)
            try {
                runCatching {
                    requireContext().contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                storiesRepository.publishStory(uri)
                Toast.makeText(requireContext(), R.string.telegram_stories_upload_started, Toast.LENGTH_SHORT).show()
                reloadStories()
            } catch (failure: Throwable) {
                Toast.makeText(requireContext(), R.string.telegram_stories_upload_failed, Toast.LENGTH_LONG).show()
                views.telegramStoriesStatus.text = failure.message ?: getString(R.string.telegram_stories_upload_failed)
            }
        }
    }

    private fun reloadStories() {
        viewLifecycleOwner.lifecycleScope.launch {
            views.telegramStoriesStatus.text = getString(R.string.telegram_stories_loading)
            views.telegramStoriesAddPhoto.isEnabled = false
            views.telegramStoriesAddVideo.isEnabled = false
            try {
                stories = storiesRepository.loadStories()
                renderStories()
            } catch (failure: Throwable) {
                views.telegramStoriesStatus.text = failure.message ?: getString(R.string.telegram_stories_upload_failed)
            } finally {
                if (view != null) {
                    views.telegramStoriesAddPhoto.isEnabled = true
                    views.telegramStoriesAddVideo.isEnabled = true
                }
            }
        }
    }

    private fun renderStories() {
        val container = views.telegramStoriesPeople
        container.removeAllViews()
        if (stories.isEmpty()) {
            views.telegramStoriesStatus.text = getString(R.string.telegram_stories_empty)
            return
        }
        views.telegramStoriesStatus.text = getString(R.string.telegram_stories_open_viewer)
        val size = dp(76)
        stories.forEachIndexed { index, story ->
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(8)
                }
                isClickable = true
                isFocusable = true
                contentDescription = getString(if (story.isVideo) R.string.telegram_stories_video else R.string.telegram_stories_photo)
                setOnClickListener { showStory(index) }
            }

            val frame = android.widget.FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(size, size)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(resolveSurfaceColor())
                    setStroke(dp(2), if (story.isSeen) 0xFF9E9E9E.toInt() else 0xFF229ED9.toInt())
                }
                setPadding(dp(3), dp(3), dp(3), dp(3))
            }
            val image = ImageView(requireContext()).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFF3F5466.toInt())
                }
            }
            val thumb = storiesRepository.buildThumbnailData(story)
            if (thumb != null) {
                imageContentRenderer.render(thumb, image, size - dp(6))
            } else {
                image.setImageResource(android.R.drawable.ic_menu_gallery)
            }
            frame.addView(image)
            card.addView(frame)

            val label = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(5)
                }
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                textSize = 12f
                setTextColor(resolvePrimaryTextColor())
                text = story.ownerUserId.substringBefore(':').removePrefix("@").take(12)
                        .ifBlank { getString(R.string.telegram_stories_title) }
            }
            card.addView(label)
            container.addView(card)
        }
    }

    private fun showStory(index: Int) {
        val story = stories.getOrNull(index) ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { storiesRepository.markSeen(story.event.eventId) }
            stories = stories.map {
                if (it.event.eventId == story.event.eventId) it.copy(isSeen = true) else it
            }
            renderStories()
            displayStory(story)
        }
    }

    private fun displayStory(story: TelegramStoriesRepository.Story) {
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val image = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(420)
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(resolveSurfaceColor())
            }
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(14).toFloat())
                }
            }
        }
        content.addView(image)
        val data = storiesRepository.buildThumbnailData(story)
        if (data != null) {
            imageContentRenderer.render(data, ImageContentRenderer.Mode.FULL_SIZE, image)
        } else {
            image.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(if (story.isVideo) R.string.telegram_stories_video else R.string.telegram_stories_photo)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)

        if (story.isVideo) {
            dialog.setPositiveButton(R.string.telegram_stories_open_video) { _, _ ->
                navigator.openRoom(requireContext(), story.roomId, story.event.eventId)
            }
        } else {
            dialog.setPositiveButton(android.R.string.ok, null)
        }
        dialog.show()
    }

    private fun resolveSurfaceColor(): Int {
        val values = android.util.TypedValue()
        requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, values, true)
        return if (values.resourceId != 0) {
            runCatching { androidx.core.content.ContextCompat.getColor(requireContext(), values.resourceId) }.getOrDefault(values.data)
        } else values.data
    }

    private fun resolvePrimaryTextColor(): Int {
        val values = android.util.TypedValue()
        requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, values, true)
        return if (values.resourceId != 0) {
            runCatching { androidx.core.content.ContextCompat.getColor(requireContext(), values.resourceId) }.getOrDefault(values.data)
        } else values.data
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
