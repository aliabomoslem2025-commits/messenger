/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

package im.vector.app.features.home

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * Story editor and Telegram-like "Share story" sheet.
 *
 * Photo stories are rendered to a vertical image before upload so text, emoji
 * stickers, drawing, crop and brightness adjustments become part of the media.
 * Video decorations and captions are saved as encrypted Matrix event metadata.
 */
@AndroidEntryPoint
class TelegramStoryComposerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STORY_URI = TelegramStoryCameraActivity.EXTRA_STORY_URI
        const val EXTRA_STORY_MIME = TelegramStoryCameraActivity.EXTRA_STORY_MIME
        private const val STORY_WIDTH = 1080
        private const val STORY_HEIGHT = 1920
    }

    @Inject lateinit var storiesRepository: TelegramStoriesRepository

    private lateinit var mediaUri: Uri
    private lateinit var mimeType: String
    private lateinit var previewFrame: FrameLayout
    private lateinit var captionInput: EditText
    private lateinit var drawingOverlay: StoryDrawingOverlayView
    private var imagePreview: ImageView? = null
    private var videoPreview: VideoView? = null
    private var drawingEnabled = false
    private var cropToFill = true
    private var brightnessPercent = 100
    private var durationHours = 24
    private val overlayTexts = mutableListOf<String>()
    private val stickers = mutableListOf<String>()
    private val overlayTextPositions = mutableListOf<PointF>()
    private val stickerPositions = mutableListOf<PointF>()

    private var availableContacts: List<StoryContact> = emptyList()
    private var selectedAudience = TelegramStoriesRepository.AUDIENCE_EVERYONE
    private val selectedUserIds = linkedSetOf<String>()
    private val excludedUserIds = linkedSetOf<String>()
    private val closeFriendIds = linkedSetOf<String>()
    private var allowScreenshots = true
    private var keepOnProfile = false
    private var posting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uriValue = intent.getStringExtra(EXTRA_STORY_URI)
        if (uriValue.isNullOrBlank()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        mediaUri = Uri.parse(uriValue)
        mimeType = intent.getStringExtra(EXTRA_STORY_MIME)
                ?: contentResolver.getType(mediaUri).orEmpty()
        if (!mimeType.startsWith("image/") && !mimeType.startsWith("video/")) {
            Toast.makeText(this, R.string.telegram_story_unsupported_media, Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        buildEditor()
    }

    private fun buildEditor() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(8))
        }
        topBar.addView(textButton("‹", 30f) { finish() }, LinearLayout.LayoutParams(dp(48), dp(44)))
        topBar.addView(TextView(this).apply {
            text = getString(R.string.telegram_story_new)
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        topBar.addView(textButton("◷", 23f) { showDurationPicker() }, LinearLayout.LayoutParams(dp(48), dp(44)))
        root.addView(topBar)

        previewFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        if (mimeType.startsWith("video/")) {
            videoPreview = VideoView(this).apply {
                setVideoURI(mediaUri)
                setOnPreparedListener { player ->
                    player.isLooping = true
                    start()
                }
            }
            previewFrame.addView(videoPreview, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ))
        } else {
            imagePreview = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageURI(mediaUri)
                contentDescription = getString(R.string.telegram_story_preview)
            }
            previewFrame.addView(imagePreview, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

        drawingOverlay = StoryDrawingOverlayView()
        drawingOverlay.visibility = View.GONE
        previewFrame.addView(drawingOverlay, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ))
        root.addView(previewFrame, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ))

        captionInput = EditText(this).apply {
            hint = getString(R.string.telegram_story_caption_hint)
            setHintTextColor(0xFFAAAAAA.toInt())
            setTextColor(Color.WHITE)
            textSize = 15f
            maxLines = 3
            filters = arrayOf(android.text.InputFilter.LengthFilter(2048))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(0x55222222)
            }
        }
        root.addView(captionInput, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dp(12), dp(8), dp(12), dp(6))
        })

        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        tools.addView(toolButton("✎", getString(R.string.telegram_story_draw)) { toggleDrawing() })
        tools.addView(toolButton("☻", getString(R.string.telegram_story_stickers)) { showStickerPicker() })
        tools.addView(toolButton("T", getString(R.string.telegram_story_text)) { showTextEditor() })
        tools.addView(toolButton("▢", getString(R.string.telegram_story_crop)) { toggleCrop() })
        tools.addView(toolButton("☷", getString(R.string.telegram_story_adjust)) { showAdjustDialog() })
        root.addView(tools)

        val next = MaterialButton(this).apply {
            text = getString(R.string.telegram_next)
            isAllCaps = false
            setOnClickListener { openShareStorySheet() }
        }
        root.addView(next, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        ).apply {
            setMargins(dp(16), dp(4), dp(16), dp(12))
        })
        setContentView(root)
    }

    private fun toolButton(icon: String, description: String, action: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = description
            setOnClickListener { action() }
            addView(TextView(this@TelegramStoryComposerActivity).apply {
                text = icon
                textSize = 22f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(48), dp(34)))
            addView(TextView(this@TelegramStoryComposerActivity).apply {
                text = description
                textSize = 10f
                setTextColor(0xFFCCCCCC.toInt())
                gravity = Gravity.CENTER
                maxLines = 1
            })
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(0, dp(54), 1f)
        }
    }

    private fun textButton(label: String, size: Float, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun toggleDrawing() {
        drawingEnabled = !drawingEnabled
        drawingOverlay.visibility = if (drawingEnabled) View.VISIBLE else View.GONE
        Toast.makeText(
                this,
                if (drawingEnabled) R.string.telegram_story_draw_hint else R.string.telegram_story_draw_done,
                Toast.LENGTH_SHORT
        ).show()
    }

    private fun showTextEditor() {
        val input = EditText(this).apply {
            hint = getString(R.string.telegram_story_text_hint)
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF888888.toInt())
            setSingleLine(false)
            minLines = 1
            maxLines = 5
        }
        AlertDialog.Builder(this)
                .setTitle(R.string.telegram_story_add_text)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val value = input.text?.toString()?.trim().orEmpty()
                    if (value.isNotEmpty()) {
                        overlayTexts += value.take(300)
                        addPreviewText(value.take(300))
                    }
                }
                .show()
    }

    private fun addPreviewText(value: String) {
        val index = overlayTexts.lastIndex
        val text = TextView(this).apply {
            text = value
            textSize = 25f
            setTextColor(Color.WHITE)
            setShadowLayer(dp(3).toFloat(), 0f, dp(2).toFloat(), Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(0x33000000)
            }
        }
        previewFrame.addView(text, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
        ))
        attachMovableOverlay(text, overlayTextPositions, index, 0.42f + index * 0.085f)
    }

    private fun showStickerPicker() {
        val values = arrayOf("❤️", "😂", "🔥", "👍", "🥰", "🎉", "😍", "🤔", "👏", "✨", "🐱", "🌟")
        AlertDialog.Builder(this)
                .setTitle(R.string.telegram_story_stickers)
                .setItems(values) { _, index ->
                    stickers += values[index]
                    val stickerIndex = stickers.lastIndex
                    val sticker = TextView(this).apply {
                        text = values[index]
                        textSize = 48f
                        gravity = Gravity.CENTER
                        setShadowLayer(dp(4).toFloat(), 0f, dp(2).toFloat(), Color.BLACK)
                    }
                    previewFrame.addView(sticker, FrameLayout.LayoutParams(
                            dp(88), dp(88), Gravity.TOP or Gravity.START
                    ))
                    attachMovableOverlay(sticker, stickerPositions, stickerIndex, 0.56f + stickerIndex * 0.11f)
                }
                .show()
    }

    /**
     * Text and emoji overlays can be repositioned by dragging, matching the
     * free-placement behavior of the Telegram story editor. Positions are stored
     * as normalized centers so the same composition can be applied to the upload.
     */
    private fun attachMovableOverlay(
            overlay: View,
            positions: MutableList<PointF>,
            index: Int,
            defaultCenterY: Float,
    ) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f

        fun savePosition() {
            val width = previewFrame.width.coerceAtLeast(1)
            val height = previewFrame.height.coerceAtLeast(1)
            val centerX = (overlay.x + overlay.width / 2f) / width
            val centerY = (overlay.y + overlay.height / 2f) / height
            val point = PointF(centerX.coerceIn(0f, 1f), centerY.coerceIn(0f, 1f))
            while (positions.size <= index) positions.add(PointF(0.5f, defaultCenterY))
            positions[index] = point
        }

        previewFrame.post {
            val x = (previewFrame.width - overlay.width) / 2f
            val y = previewFrame.height * defaultCenterY - overlay.height / 2f
            overlay.x = x.coerceAtLeast(0f)
            overlay.y = y.coerceAtLeast(0f)
            savePosition()
        }

        overlay.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = view.x
                    startY = view.y
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxX = (previewFrame.width - view.width).coerceAtLeast(0)
                    val maxY = (previewFrame.height - view.height).coerceAtLeast(0)
                    view.x = (startX + event.rawX - downRawX).coerceIn(0f, maxX.toFloat())
                    view.y = (startY + event.rawY - downRawY).coerceIn(0f, maxY.toFloat())
                    savePosition()
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    savePosition()
                    true
                }
                else -> true
            }
        }
    }

    private fun toggleCrop() {
        cropToFill = !cropToFill
        imagePreview?.scaleType = if (cropToFill) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        Toast.makeText(
                this,
                if (cropToFill) R.string.telegram_story_crop_fill else R.string.telegram_story_crop_fit,
                Toast.LENGTH_SHORT
        ).show()
    }

    private fun showAdjustDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(8))
        }
        container.addView(TextView(this).apply {
            text = getString(R.string.telegram_story_adjust_brightness)
            setTextColor(Color.WHITE)
        })
        val slider = SeekBar(this).apply {
            max = 200
            progress = brightnessPercent
        }
        container.addView(slider)
        AlertDialog.Builder(this)
                .setTitle(R.string.telegram_story_adjust)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    brightnessPercent = slider.progress
                    applyPreviewBrightness()
                }
                .show()
    }

    private fun applyPreviewBrightness() {
        val offset = (brightnessPercent - 100) * 1.4f
        val matrix = ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, offset,
                0f, 1f, 0f, 0f, offset,
                0f, 0f, 1f, 0f, offset,
                0f, 0f, 0f, 1f, 0f
        ))
        imagePreview?.colorFilter = ColorMatrixColorFilter(matrix)
    }

    private fun showDurationPicker() {
        val options = arrayOf(
                getString(R.string.telegram_story_duration_6h),
                getString(R.string.telegram_story_duration_12h),
                getString(R.string.telegram_story_duration_24h),
                getString(R.string.telegram_story_duration_48h)
        )
        val values = intArrayOf(6, 12, 24, 48)
        AlertDialog.Builder(this)
                .setTitle(R.string.telegram_story_duration)
                .setSingleChoiceItems(options, values.indexOf(durationHours).coerceAtLeast(0)) { dialog, which ->
                    durationHours = values[which]
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    private fun openShareStorySheet() {
        lifecycleScope.launch {
            try {
                availableContacts = storiesRepository.getEligibleContacts()
                closeFriendIds.clear()
                closeFriendIds.addAll(storiesRepository.getCloseFriends())
                showShareStorySheet()
            } catch (_: Exception) {
                Toast.makeText(this@TelegramStoryComposerActivity, R.string.telegram_story_contacts_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showShareStorySheet() {
        val dialog = BottomSheetDialog(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(0xFF171717.toInt())
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.telegram_story_share_story)
            textSize = 18f
            setTextColor(Color.WHITE)
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.telegram_story_choose_who_can_view)
            textSize = 12f
            setTextColor(0xFFAAAAAA.toInt())
            setPadding(0, dp(3), 0, dp(10))
        })

        val audienceGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val audienceRows = listOf(
                TelegramStoriesRepository.AUDIENCE_EVERYONE to getString(R.string.telegram_story_everyone),
                TelegramStoriesRepository.AUDIENCE_CONTACTS to getString(R.string.telegram_story_contacts),
                TelegramStoriesRepository.AUDIENCE_CLOSE_FRIENDS to getString(R.string.telegram_story_close_friends),
                TelegramStoriesRepository.AUDIENCE_SELECTED to getString(R.string.telegram_story_selected_contacts)
        )
        audienceRows.forEach { (value, label) ->
            val row = RadioButton(this).apply {
                id = View.generateViewId()
                text = label
                textSize = 14f
                setTextColor(Color.WHITE)
                buttonTintList = android.content.res.ColorStateList.valueOf(0xFF229ED9.toInt())
                isChecked = selectedAudience == value
                tag = value
                setPadding(0, dp(4), 0, dp(4))
            }
            audienceGroup.addView(row)
        }
        audienceGroup.setOnCheckedChangeListener { group, checkedId ->
            val selected = group.findViewById<RadioButton>(checkedId)
            selectedAudience = selected?.tag as? String ?: TelegramStoriesRepository.AUDIENCE_EVERYONE
        }
        content.addView(audienceGroup)

        content.addView(linkRow(getString(R.string.telegram_story_exclude_people)) {
            showContactPicker(
                    getString(R.string.telegram_story_exclude_people),
                    excludedUserIds
            ) { selected ->
                excludedUserIds.clear()
                excludedUserIds.addAll(selected)
            }
        })
        content.addView(linkRow(getString(R.string.telegram_story_edit_close_friends)) {
            showContactPicker(
                    getString(R.string.telegram_story_close_friends),
                    closeFriendIds
            ) { selected ->
                closeFriendIds.clear()
                closeFriendIds.addAll(selected)
                lifecycleScope.launch { storiesRepository.saveCloseFriends(closeFriendIds) }
            }
        })
        content.addView(linkRow(getString(R.string.telegram_story_edit_selected_contacts)) {
            showContactPicker(
                    getString(R.string.telegram_story_selected_contacts),
                    selectedUserIds
            ) { selected ->
                selectedUserIds.clear()
                selectedUserIds.addAll(selected)
            }
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.telegram_story_select_people_who_wont_see)
            textSize = 12f
            setTextColor(0xFF229ED9.toInt())
            setPadding(0, dp(8), 0, dp(12))
        })

        val allowSwitch = SwitchMaterial(this).apply {
            text = getString(R.string.telegram_story_allow_screenshots)
            setTextColor(Color.WHITE)
            isChecked = allowScreenshots
            setOnCheckedChangeListener { _, checked -> allowScreenshots = checked }
        }
        content.addView(allowSwitch)
        val profileSwitch = SwitchMaterial(this).apply {
            text = getString(R.string.telegram_story_keep_on_my_page)
            setTextColor(Color.WHITE)
            isChecked = keepOnProfile
            setOnCheckedChangeListener { _, checked -> keepOnProfile = checked }
        }
        content.addView(profileSwitch)

        val albumRow = TextView(this).apply {
            text = getString(R.string.telegram_story_album_all_stories)
            textSize = 14f
            setTextColor(0xFF229ED9.toInt())
            setPadding(0, dp(12), 0, dp(12))
        }
        content.addView(albumRow)
        content.addView(TextView(this).apply {
            text = getString(R.string.telegram_story_expiration_note)
            textSize = 11f
            setTextColor(0xFF888888.toInt())
            setPadding(0, 0, 0, dp(10))
        })

        val postButton = MaterialButton(this).apply {
            text = getString(R.string.telegram_story_post)
            isAllCaps = false
            setOnClickListener {
                dialog.dismiss()
                postStory()
            }
        }
        content.addView(postButton, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        ))
        dialog.setContentView(content)
        dialog.show()
    }

    private fun linkRow(label: String, action: () -> Unit): View {
        return TextView(this).apply {
            text = "$label  ›"
            textSize = 13f
            setTextColor(0xFF229ED9.toInt())
            setPadding(0, dp(9), 0, dp(9))
            isClickable = true
            setOnClickListener { action() }
        }
    }

    private fun showContactPicker(
            title: String,
            initiallySelected: Set<String>,
            onDone: (Set<String>) -> Unit,
    ) {
        if (availableContacts.isEmpty()) {
            Toast.makeText(this, R.string.telegram_story_no_contacts, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = availableContacts.map { it.displayName }.toTypedArray()
        val checked = BooleanArray(availableContacts.size) { index ->
            availableContacts[index].userId in initiallySelected
        }
        val selected = initiallySelected.toMutableSet()
        AlertDialog.Builder(this)
                .setTitle(title)
                .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                    val userId = availableContacts[which].userId
                    if (isChecked) selected.add(userId) else selected.remove(userId)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ -> onDone(selected) }
                .show()
    }

    private fun postStory() {
        if (posting) return
        if (selectedAudience == TelegramStoriesRepository.AUDIENCE_SELECTED && selectedUserIds.isEmpty()) {
            Toast.makeText(this, R.string.telegram_story_select_at_least_one, Toast.LENGTH_LONG).show()
            return
        }
        if (selectedAudience == TelegramStoriesRepository.AUDIENCE_CLOSE_FRIENDS && closeFriendIds.isEmpty()) {
            Toast.makeText(this, R.string.telegram_story_close_friends_empty, Toast.LENGTH_LONG).show()
            return
        }
        posting = true
        lifecycleScope.launch {
            try {
                if (selectedAudience == TelegramStoriesRepository.AUDIENCE_CLOSE_FRIENDS) {
                    storiesRepository.saveCloseFriends(closeFriendIds)
                }
                val uploadUri = prepareMediaForUpload()
                val draft = StoryDraft(
                        caption = captionInput.text?.toString().orEmpty().trim(),
                        overlayTexts = overlayTexts.toList(),
                        stickers = stickers.toList(),
                        overlayTextPositions = overlayTextPositions.map { "${it.x},${it.y}" },
                        stickerPositions = stickerPositions.map { "${it.x},${it.y}" },
                        drawingPaths = drawingOverlay.serializedPaths(),
                        brightnessPercent = brightnessPercent,
                        cropToFill = cropToFill,
                        allowScreenshots = allowScreenshots,
                        keepOnProfile = keepOnProfile,
                        durationHours = durationHours,
                        audience = selectedAudience,
                        selectedUserIds = selectedUserIds.toSet(),
                        excludedUserIds = excludedUserIds.toSet(),
                        albumName = "All Stories",
                )
                storiesRepository.publishStory(uploadUri, draft)
                Toast.makeText(this@TelegramStoryComposerActivity, R.string.telegram_stories_upload_started, Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            } catch (failure: Throwable) {
                posting = false
                Toast.makeText(
                        this@TelegramStoryComposerActivity,
                        failure.message ?: getString(R.string.telegram_story_post_failed),
                        Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun prepareMediaForUpload(): Uri {
        if (!mimeType.startsWith("image/")) return mediaUri
        // Snapshot view-owned drawing coordinates on the main thread before
        // bitmap processing switches to Dispatchers.IO.
        val drawingSnapshot = drawingOverlay.serializedPaths()
        val textPositionSnapshot = overlayTextPositions.map { PointF(it.x, it.y) }
        val stickerPositionSnapshot = stickerPositions.map { PointF(it.x, it.y) }
        return withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(mediaUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    ?: error("Could not read the selected photo")
            val sample = max(
                    1,
                    max(bounds.outWidth / STORY_WIDTH, bounds.outHeight / STORY_HEIGHT)
            )
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val source = contentResolver.openInputStream(mediaUri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: error("Could not decode the selected photo")
            val output = Bitmap.createBitmap(STORY_WIDTH, STORY_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            canvas.drawColor(Color.BLACK)

            val sourceRect = if (cropToFill) {
                val targetRatio = STORY_WIDTH.toFloat() / STORY_HEIGHT
                val sourceRatio = source.width.toFloat() / source.height
                if (sourceRatio > targetRatio) {
                    val cropWidth = (source.height * targetRatio).toInt()
                    val left = (source.width - cropWidth) / 2
                    android.graphics.Rect(left, 0, left + cropWidth, source.height)
                } else {
                    val cropHeight = (source.width / targetRatio).toInt()
                    val top = (source.height - cropHeight) / 2
                    android.graphics.Rect(0, top, source.width, top + cropHeight)
                }
            } else {
                android.graphics.Rect(0, 0, source.width, source.height)
            }
            val destination = if (cropToFill) {
                RectF(0f, 0f, STORY_WIDTH.toFloat(), STORY_HEIGHT.toFloat())
            } else {
                val scale = min(
                        STORY_WIDTH.toFloat() / sourceRect.width(),
                        STORY_HEIGHT.toFloat() / sourceRect.height()
                )
                val width = sourceRect.width() * scale
                val height = sourceRect.height() * scale
                RectF(
                        (STORY_WIDTH - width) / 2f,
                        (STORY_HEIGHT - height) / 2f,
                        (STORY_WIDTH + width) / 2f,
                        (STORY_HEIGHT + height) / 2f
                )
            }
            val brightnessOffset = (brightnessPercent - 100) * 1.4f
            val colorMatrix = ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, brightnessOffset,
                    0f, 1f, 0f, 0f, brightnessOffset,
                    0f, 0f, 1f, 0f, brightnessOffset,
                    0f, 0f, 0f, 1f, 0f
            ))
            val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(colorMatrix)
            }
            canvas.drawBitmap(source, sourceRect, destination, imagePaint)

            val decorationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = STORY_WIDTH * 0.066f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                setShadowLayer(STORY_WIDTH * 0.006f, 0f, STORY_WIDTH * 0.003f, Color.BLACK)
            }
            overlayTexts.forEachIndexed { index, text ->
                val position = textPositionSnapshot.getOrNull(index)
                        ?: PointF(0.5f, 0.42f + index * 0.085f)
                canvas.drawText(
                        text,
                        position.x * STORY_WIDTH,
                        position.y * STORY_HEIGHT,
                        decorationPaint
                )
            }
            stickers.forEachIndexed { index, sticker ->
                val position = stickerPositionSnapshot.getOrNull(index)
                        ?: PointF(0.25f + (index % 3) * 0.25f, 0.58f + (index / 3) * 0.11f)
                decorationPaint.textSize = STORY_WIDTH * 0.105f
                canvas.drawText(
                        sticker,
                        position.x * STORY_WIDTH,
                        position.y * STORY_HEIGHT,
                        decorationPaint
                )
            }
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = STORY_WIDTH * 0.008f
            }
            drawingSnapshot.forEach { serialized ->
                val points = serialized.split("|").mapNotNull { pair ->
                    val xy = pair.split(",")
                    if (xy.size != 2) null else {
                        val x = xy[0].toFloatOrNull()
                        val y = xy[1].toFloatOrNull()
                        if (x == null || y == null) null else PointF(x * STORY_WIDTH, y * STORY_HEIGHT)
                    }
                }
                if (points.isNotEmpty()) {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    canvas.drawPath(path, strokePaint)
                }
            }

            source.recycle()
            val directory = File(filesDir, "media").apply { mkdirs() }
            val file = File.createTempFile("story_edited_", ".jpg", directory)
            file.outputStream().use { outputStream ->
                output.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
            }
            output.recycle()
            FileProvider.getUriForFile(
                    this@TelegramStoryComposerActivity,
                    packageName + ".multipicker.fileprovider",
                    file
            )
        }
    }

    override fun onPause() {
        super.onPause()
        videoPreview?.pause()
    }

    override fun onDestroy() {
        videoPreview?.stopPlayback()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class StoryDrawingOverlayView : View(this@TelegramStoryComposerActivity) {
        private val strokes = mutableListOf<MutableList<PointF>>()
        private var currentStroke: MutableList<PointF>? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dp(4).toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), Color.BLACK)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    currentStroke = mutableListOf(PointF(event.x, event.y)).also(strokes::add)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    currentStroke?.add(PointF(event.x, event.y))
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    currentStroke?.add(PointF(event.x, event.y))
                    currentStroke = null
                    invalidate()
                    return true
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            strokes.forEach { points ->
                if (points.isEmpty()) return@forEach
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                canvas.drawPath(path, paint)
            }
        }

        fun serializedPaths(): List<String> {
            if (width <= 0 || height <= 0) return emptyList()
            return strokes.map { points ->
                points.joinToString("|") { point ->
                    "${(point.x / width).coerceIn(0f, 1f)},${(point.y / height).coerceIn(0f, 1f)}"
                }
            }
        }
    }
}
