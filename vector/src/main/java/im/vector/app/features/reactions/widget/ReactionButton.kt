/*
 * Copyright 2020-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */
package im.vector.app.features.reactions.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.EmojiSpanify
import im.vector.app.R
import im.vector.app.core.utils.TextUtils
import im.vector.app.core.utils.isAnimationEnabled
import im.vector.app.databinding.ReactionButtonBinding
import javax.inject.Inject

private const val REACTION_BOUNCE_DURATION_MS = 180L
private const val REACTION_TEXT_BOUNCE_SCALE = 0.72f
private const val REACTION_COUNT_BOUNCE_DURATION_MS = 170L

/**
 * An animated reaction button.
 * Displays a String reaction (emoji), with a count, and that can be selected or not (toggle)
 */
@AndroidEntryPoint
class ReactionButton @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
        defStyleRes: Int = im.vector.lib.ui.styles.R.style.TimelineReactionView
) :
        LinearLayout(context, attrs, defStyleAttr, defStyleRes), View.OnClickListener, View.OnLongClickListener {

    @Inject lateinit var emojiSpanify: EmojiSpanify

    private val views: ReactionButtonBinding

    var reactedListener: ReactedListener? = null

    var reactionCount = 11
        set(value) {
            val didChange = field != value
            field = value
            views.reactionCount.text = TextUtils.formatCountToShortDecimal(value)
            if (didChange) {
                animateReactionCountChange()
            }
        }

    var reactionString = "😀"
        set(value) {
            field = value
            // maybe cache this for performances?
            val emojiSpanned = emojiSpanify.spanify(value)
            views.reactionText.text = emojiSpanned
        }

    private var isChecked: Boolean = false
    private var onDrawable: Drawable? = null
    private var offDrawable: Drawable? = null

    init {
        inflate(context, R.layout.reaction_button, this)
        orientation = HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        views = ReactionButtonBinding.bind(this)
        views.reactionCount.text = TextUtils.formatCountToShortDecimal(reactionCount)
        context.withStyledAttributes(attrs, im.vector.lib.ui.styles.R.styleable.ReactionButton, defStyleAttr) {
            onDrawable = ContextCompat.getDrawable(context, R.drawable.reaction_rounded_rect_shape)
            offDrawable = ContextCompat.getDrawable(context, R.drawable.reaction_rounded_rect_shape_off)
            getString(im.vector.lib.ui.styles.R.styleable.ReactionButton_emoji)?.let {
                reactionString = it
            }
            reactionCount = getInt(im.vector.lib.ui.styles.R.styleable.ReactionButton_reaction_count, 0)
            val status = getBoolean(im.vector.lib.ui.styles.R.styleable.ReactionButton_toggled, false)
            setChecked(status)
        }

        setOnClickListener(this)
        setOnLongClickListener(this)
    }

    /**
     * This triggers the entire functionality of the button such as icon changes,
     * animations, listeners etc.
     *
     * @param v
     */
    override fun onClick(v: View) {
        if (!isEnabled) {
            return
        }

        isChecked = !isChecked
        isSelected = isChecked
        isActivated = isChecked
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        background = if (isChecked) onDrawable else offDrawable

        // Keep the existing Matrix reaction callback while adding a short
        // selection bounce. The emoji always returns to its resting scale.
        if (isChecked) {
            reactedListener?.onReacted(this)
        } else {
            reactedListener?.onUnReacted(this)
        }

        animateReactionSelection()
        if (isChecked) {
            playReactionBurst()
        } else {
            playReactionRemoval()
        }
    }

    /**
     * A short Telegram-inspired emoji burst layered above the message timeline.
     * The underlying reaction still goes through the existing Matrix callback.
     */
    private fun playReactionBurst() {
        if (!context.isAnimationEnabled()) return
        val parent = parent as? ViewGroup ?: return
        val overlay = parent.overlay
        val density = resources.displayMetrics.density
        val centerX = x + width / 2f
        val centerY = y + height / 2f
        val reactionGlyph = views.reactionText.text?.toString()?.takeIf { it.isNotBlank() } ?: "✨"
        val glyphs = reactionBurstGlyphs(reactionGlyph)
        val radius = 22f * density

        glyphs.forEachIndexed { index, glyph ->
            val angle = (Math.PI * 2.0 * index / glyphs.size) - Math.PI / 2.0
            val targetX = (kotlin.math.cos(angle) * radius).toFloat()
            val targetY = (kotlin.math.sin(angle) * radius).toFloat()
            val particleSize = (28f * density).toInt()
            val particle = TextView(context).apply {
                text = glyph
                textSize = if (index == 1 || index == 4) 17f else 16f
                gravity = Gravity.CENTER
                includeFontPadding = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                alpha = 0f
                scaleX = 0.35f
                scaleY = 0.35f
            }
            particle.measure(
                    View.MeasureSpec.makeMeasureSpec(particleSize, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(particleSize, View.MeasureSpec.EXACTLY)
            )
            particle.layout(
                    (centerX - particleSize / 2f).toInt(),
                    (centerY - particleSize / 2f).toInt(),
                    (centerX + particleSize / 2f).toInt(),
                    (centerY + particleSize / 2f).toInt()
            )
            overlay.add(particle)
            particle.animate()
                    .alpha(1f)
                    .scaleX(1.12f)
                    .scaleY(1.12f)
                    .setDuration(90L)
                    .setStartDelay(index * 12L)
                    .withEndAction {
                        particle.animate()
                                .translationX(targetX)
                                .translationY(targetY)
                                .alpha(0f)
                                .scaleX(0.55f)
                                .scaleY(0.55f)
                                .setDuration(235L)
                                .setInterpolator(OvershootInterpolator(0.8f))
                                .withEndAction { overlay.remove(particle) }
                                .start()
                    }
                    .start()
        }
    }

    /**
     * A Matrix reaction summary can update after the local echo or when another
     * member reacts. Pop only the count label, not the entire timeline row, so
     * receipt and encryption-state rebinding cannot make the message flicker.
     */
    /**
     * Give common reactions their own visual language rather than playing the same
     * generic sparkles for every emoji. These are independently rendered UI particles;
     * the actual reaction still uses the Matrix event callbacks above.
     */
    private fun reactionBurstGlyphs(emoji: String): List<String> = when {
        listOf("😂", "🤣", "😹", "😆", "😅").any { emoji.contains(it) } ->
            listOf(emoji, "😂", "✨", "🤣", "💫", "😹")
        listOf("😭", "😢", "🥹", "😿").any { emoji.contains(it) } ->
            listOf(emoji, "💧", "😭", "💧", "😢", "✨")
        listOf("❤️", "❤", "💕", "💖", "💗", "💓", "💞", "💘", "😍").any { emoji.contains(it) } ->
            listOf(emoji, "💖", "💕", "💗", "❤️", "✨")
        listOf("🔥", "💥").any { emoji.contains(it) } ->
            listOf(emoji, "🔥", "✨", "💥", "🔥", "💫")
        listOf("👍", "👎", "👏", "🙏", "💪").any { emoji.contains(it) } ->
            listOf(emoji, "✨", "👍", "💫", emoji, "👏")
        listOf("🎉", "🎊", "🥳", "✨", "🌟").any { emoji.contains(it) } ->
            listOf(emoji, "🎉", "✨", "🎊", "🌟", "💫")
        else -> listOf(emoji, "✨", emoji, "💫", emoji, "✨")
    }

    /**
     * Shrink the selected emoji away when a reaction is removed. Matrix remains the
     * source of truth; this animation is only feedback and never changes event state.
     */
    private fun playReactionRemoval() {
        if (!context.isAnimationEnabled()) return
        val parent = parent as? ViewGroup ?: return
        val overlay = parent.overlay
        val density = resources.displayMetrics.density
        val particleSize = (34f * density).toInt()
        val centerX = x + width / 2f
        val centerY = y + height / 2f
        val particle = TextView(context).apply {
            text = views.reactionText.text?.toString()?.takeIf { it.isNotBlank() } ?: "✨"
            textSize = 19f
            gravity = Gravity.CENTER
            includeFontPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
        }
        particle.measure(
                View.MeasureSpec.makeMeasureSpec(particleSize, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(particleSize, View.MeasureSpec.EXACTLY)
        )
        particle.layout(
                (centerX - particleSize / 2f).toInt(),
                (centerY - particleSize / 2f).toInt(),
                (centerX + particleSize / 2f).toInt(),
                (centerY + particleSize / 2f).toInt()
        )
        overlay.add(particle)
        particle.animate()
                .alpha(0f)
                .scaleX(0.25f)
                .scaleY(0.25f)
                .translationY(-10f * density)
                .setDuration(190L)
                .withEndAction { overlay.remove(particle) }
                .start()
    }

    private fun animateReactionCountChange() {
        val countView = views.reactionCount
        countView.animate().cancel()
        countView.scaleX = 1f
        countView.scaleY = 1f
        countView.alpha = 1f
        countView.translationY = 0f

        if (!isAttachedToWindow || !context.isAnimationEnabled()) return

        countView.scaleX = 0.72f
        countView.scaleY = 0.72f
        countView.alpha = 0.65f
        countView.translationY = -resources.displayMetrics.density * 2f
        countView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .translationY(0f)
                .setDuration(REACTION_COUNT_BOUNCE_DURATION_MS)
                .setInterpolator(OvershootInterpolator(1.25f))
                .start()
    }

    private fun animateReactionSelection() {
        animate().cancel()
        views.reactionText.animate().cancel()

        // Respect Android's disabled animator setting, including accessibility/reduced-motion use.
        if (!context.isAnimationEnabled()) {
            scaleX = 1f
            scaleY = 1f
            views.reactionText.scaleX = 1f
            views.reactionText.scaleY = 1f
            return
        }

        val buttonStartScale = if (isChecked) 0.94f else 0.97f
        scaleX = buttonStartScale
        scaleY = buttonStartScale
        animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(REACTION_BOUNCE_DURATION_MS)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()

        // Selection gets the stronger emoji pop; unselecting still gets a softer response.
        val emojiStartScale = if (isChecked) REACTION_TEXT_BOUNCE_SCALE else 0.86f
        views.reactionText.scaleX = emojiStartScale
        views.reactionText.scaleY = emojiStartScale
        views.reactionText.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(REACTION_BOUNCE_DURATION_MS)
                .setInterpolator(OvershootInterpolator(1.5f))
                .start()
    }

    override fun onLongClick(v: View?): Boolean {
        reactedListener?.onLongClick(this)
        return reactedListener != null
    }

    /**
     * Sets the initial state of the button to liked
     * or unliked.
     *
     * @param status
     */
    fun setChecked(status: Boolean?) {
        isChecked = status == true
        isSelected = isChecked
        isActivated = isChecked
        background = if (isChecked) onDrawable else offDrawable

        // Epoxy can rebind/reuse reaction buttons: never keep an old animation scale.
        animate().cancel()
        views.reactionText.animate().cancel()
        scaleX = 1f
        scaleY = 1f
        views.reactionText.scaleX = 1f
        views.reactionText.scaleY = 1f
    }

    interface ReactedListener {
        fun onReacted(reactionButton: ReactionButton)
        fun onUnReacted(reactionButton: ReactionButton)
        fun onLongClick(reactionButton: ReactionButton)
    }
}
