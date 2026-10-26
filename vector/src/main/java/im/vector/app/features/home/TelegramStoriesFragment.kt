/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

package im.vector.app.features.home

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import android.animation.ObjectAnimator
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.core.platform.VectorBaseFragment
import im.vector.app.databinding.FragmentTelegramStoriesBinding
import im.vector.app.features.media.ImageContentRenderer
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

private const val STORY_PHOTO_DURATION_MS = 5_000L
private const val ARG_INITIAL_STORY_EVENT_ID = "initial_story_event_id"

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
    private var storyViewerDialog: Dialog? = null
    private var storyViewerIndex = -1
    private var didOpenRequestedInitialStory = false
    private var viewerImage: ImageView? = null
    private var viewerTitle: TextView? = null
    private var viewerProgress: ProgressBar? = null
    private var viewerPreviousButton: Button? = null
    private var viewerNextButton: Button? = null
    private var viewerOpenVideoButton: Button? = null
    private val storyTimerHandler = Handler(Looper.getMainLooper())
    private var storyProgressAnimator: ObjectAnimator? = null
    private val advanceStoryRunnable = Runnable {
        val current = stories.getOrNull(storyViewerIndex)
        if (storyViewerDialog?.isShowing == true && current != null && !current.isVideo) {
            showStory(storyViewerIndex + 1)
        }
    }

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
                openRequestedInitialStoryIfPresent()
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

    private fun openRequestedInitialStoryIfPresent() {
        if (didOpenRequestedInitialStory) return
        didOpenRequestedInitialStory = true
        val eventId = arguments?.getString(ARG_INITIAL_STORY_EVENT_ID).orEmpty()
        if (eventId.isBlank()) return
        val initialIndex = stories.indexOfFirst { it.event.eventId == eventId }
        if (initialIndex >= 0) showStory(initialIndex)
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
        val story = stories.getOrNull(index)
        if (story == null) {
            storyTimerHandler.removeCallbacks(advanceStoryRunnable)
            storyProgressAnimator?.cancel()
            storyViewerDialog?.dismiss()
            return
        }
        storyViewerIndex = index
        viewLifecycleOwner.lifecycleScope.launch {
            if (!story.isSeen) runCatching { storiesRepository.markSeen(story.event.eventId) }
            stories = stories.map {
                if (it.event.eventId == story.event.eventId) it.copy(isSeen = true) else it
            }
            renderStories()
            renderStoryViewer(story)
        }
    }

    /**
     * Full-screen story viewer with a short photo timer, read ring, previous/next
     * controls, and an explicit Matrix event hand-off for video playback.
     */
    private fun renderStoryViewer(story: TelegramStoriesRepository.Story) {
        if (storyViewerDialog == null) {
            createStoryViewerDialog().also {
                storyViewerDialog = it
                it.show()
                it.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        }
        viewerTitle?.text = story.ownerUserId.substringBefore(':').removePrefix("@")
                .ifBlank { getString(R.string.telegram_stories_title) }
        viewerPreviousButton?.isEnabled = storyViewerIndex > 0
        viewerNextButton?.isEnabled = storyViewerIndex < stories.lastIndex
        viewerOpenVideoButton?.isVisible = story.isVideo
        viewerProgress?.isVisible = !story.isVideo
        viewerProgress?.progress = 0

        val image = viewerImage ?: return
        imageContentRenderer.clear(image)
        val data = storiesRepository.buildThumbnailData(story)
        if (data != null) {
            image.scaleType = ImageView.ScaleType.FIT_CENTER
            imageContentRenderer.render(data, ImageContentRenderer.Mode.FULL_SIZE, image)
        } else {
            image.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        storyTimerHandler.removeCallbacks(advanceStoryRunnable)
        storyProgressAnimator?.cancel()
        storyProgressAnimator = null
        if (!story.isVideo) {
            viewerProgress?.let { progress ->
                storyProgressAnimator = ObjectAnimator.ofInt(progress, "progress", 0, 100).apply {
                    duration = STORY_PHOTO_DURATION_MS
                    start()
                }
            }
            storyTimerHandler.postDelayed(advanceStoryRunnable, STORY_PHOTO_DURATION_MS)
        }
    }

    @Suppress("DEPRECATION")
    private fun createStoryViewerDialog(): Dialog {
        val context = requireContext()
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
        }
        val image = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = getString(R.string.telegram_stories_title)
        }
        root.addView(image)
        viewerImage = image

        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x55FFFFFF)
        }
        root.addView(progress, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3),
                Gravity.TOP
        ).apply {
            leftMargin = dp(12)
            rightMargin = dp(12)
            topMargin = dp(12)
        })
        viewerProgress = progress

        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(20), dp(12), dp(8))
            background = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(0x99000000.toInt(), Color.TRANSPARENT)
            }
        }
        val title = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            textSize = 15f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        viewerTitle = title
        topBar.addView(title)
        val close = TextView(context).apply {
            text = "×"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = getString(android.R.string.cancel)
            setPadding(dp(12), 0, dp(8), dp(4))
            setOnClickListener { storyViewerDialog?.dismiss() }
        }
        topBar.addView(close)
        root.addView(topBar, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
        ))

        val bottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(18), dp(12), dp(20))
            background = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.BOTTOM_TOP
                colors = intArrayOf(0x99000000.toInt(), Color.TRANSPARENT)
            }
        }
        val previous = Button(context).apply {
            text = "‹"
            textSize = 24f
            isAllCaps = false
            contentDescription = getString(android.R.string.dialog_alert_title)
            setOnClickListener { if (storyViewerIndex > 0) showStory(storyViewerIndex - 1) }
        }
        viewerPreviousButton = previous
        val next = Button(context).apply {
            text = "›"
            textSize = 24f
            isAllCaps = false
            contentDescription = getString(R.string.telegram_stories_open_viewer)
            setOnClickListener { showStory(storyViewerIndex + 1) }
        }
        viewerNextButton = next
        val openVideo = Button(context).apply {
            text = getString(R.string.telegram_stories_open_video)
            isAllCaps = false
            setOnClickListener {
                val story = stories.getOrNull(storyViewerIndex) ?: return@setOnClickListener
                storyViewerDialog?.dismiss()
                navigator.openRoom(requireContext(), story.roomId, story.event.eventId)
            }
        }
        viewerOpenVideoButton = openVideo
        bottomBar.addView(previous, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) })
        bottomBar.addView(next, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(4) })
        bottomBar.addView(openVideo, LinearLayout.LayoutParams(0, dp(48), 2f).apply { marginStart = dp(8) })
        root.addView(bottomBar, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        ))

        return Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
            setContentView(root)
            window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
            window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            setOnDismissListener {
                storyTimerHandler.removeCallbacks(advanceStoryRunnable)
                storyProgressAnimator?.cancel()
                storyProgressAnimator = null
                imageContentRenderer.clear(image)
                storyViewerDialog = null
                viewerImage = null
                viewerTitle = null
                viewerProgress = null
                viewerPreviousButton = null
                viewerNextButton = null
                viewerOpenVideoButton = null
            }
        }
    }

    override fun onDestroyView() {
        storyTimerHandler.removeCallbacks(advanceStoryRunnable)
        storyProgressAnimator?.cancel()
        storyViewerDialog?.dismiss()
        storyViewerDialog = null
        super.onDestroyView()
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

    companion object {
        fun newInstance(initialStoryEventId: String? = null): TelegramStoriesFragment {
            return TelegramStoriesFragment().apply {
                arguments = Bundle().apply {
                    if (!initialStoryEventId.isNullOrBlank()) {
                        putString(ARG_INITIAL_STORY_EVENT_ID, initialStoryEventId)
                    }
                }
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
