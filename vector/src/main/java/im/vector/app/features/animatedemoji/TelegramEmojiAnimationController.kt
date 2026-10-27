/*
 * Animated-emoji playback backed by Lottie assets.
 * See docs/TELEGRAM_ANIMATED_EMOJI_ASSETS.md for asset attribution and runtime behavior.
 */
package im.vector.app.features.animatedemoji

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.util.TypedValue
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import im.vector.app.R
import im.vector.app.core.utils.isAnimationEnabled
import kotlin.random.Random

object TelegramEmojiAnimationController {
    private const val STANDALONE_SIZE_DP = 48
    private const val REACTION_SIZE_DP = 104

    private class TextSnapshot(
            val text: CharSequence?,
            val gravity: Int,
            val textSizePx: Float,
            val alpha: Float,
            val visibility: Int,
            val contentDescription: CharSequence?
    )

    private class StandaloneState(
            val eventId: String,
            val emoji: String,
            private val snapshot: TextSnapshot,
            val animationView: LottieAnimationView
    ) : View.OnAttachStateChangeListener {
        var hasStarted = false
        var finished = false
        var compositionReady = false
        var animatorListener: Animator.AnimatorListener? = null

        override fun onViewAttachedToWindow(view: View) {
            val textView = view as? TextView ?: return
            startIfNeeded(textView)
        }

        override fun onViewDetachedFromWindow(view: View) {
            animationView.pauseAnimation()
        }

        fun startIfNeeded(view: TextView) {
            if (!view.isAttachedToWindow || finished || !compositionReady) return
            if (!hasStarted) {
                animationView.progress = 0f
                animationView.playAnimation()
                hasStarted = true
            } else {
                animationView.resumeAnimation()
            }
        }

        fun replay(view: TextView): Boolean {
            if (!compositionReady) return false
            finished = false
            hasStarted = false
            animationView.visibility = View.VISIBLE
            view.alpha = 0f
            animationView.progress = 0f
            startIfNeeded(view)
            return true
        }

        fun restore(view: TextView) {
            view.removeOnAttachStateChangeListener(this)
            animatorListener?.let(animationView::removeAnimatorListener)
            animatorListener = null
            animationView.cancelAnimation()
            animationView.visibility = View.GONE
            animationView.contentDescription = null
            animationView.setBackgroundColor(Color.TRANSPARENT)
            animationView.setImageDrawable(null)
            view.text = snapshot.text
            view.gravity = snapshot.gravity
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, snapshot.textSizePx)
            view.contentDescription = snapshot.contentDescription
            view.alpha = snapshot.alpha
            view.visibility = snapshot.visibility
            view.requestLayout()
            animationView.requestLayout()
        }
    }

    /**
     * The emoji animation is a transparent sibling over the original TextView.
     * Keeping the TextView underneath preserves message measurement, hit targets and
     * a Unicode fallback while the Lottie composition loads.
     */
    fun animationViewFor(textView: TextView): LottieAnimationView? {
        // The TextView is inflated from a ViewStub nested inside its own LinearLayout;
        // the animation view is its sibling in the enclosing FrameLayout. Walk every
        // ancestor instead of searching only the direct parent (which always returned null).
        var ancestor = textView.parent
        while (ancestor is ViewGroup) {
            val animationView = ancestor.findViewById<LottieAnimationView>(R.id.messageAnimatedEmojiView)
            if (animationView != null) return animationView
            ancestor = ancestor.parent
        }
        return null
    }

    /**
     * Keep the animation alive when Matrix updates a local echo from SENDING to SENT/SYNCED.
     * Only a different event or different emoji resets the animation layer.
     */
    fun prepareForMessage(textView: TextView, eventId: String, emoji: String?) {
        val existing = textView.getTag(R.id.tag_telegram_animated_emoji_state) as? StandaloneState
        if (existing != null && emoji != null && existing.eventId == eventId && existing.emoji == emoji) {
            return
        }
        clear(textView)
    }

    fun hasStateForMessage(textView: TextView, eventId: String, emoji: String): Boolean {
        val state = textView.getTag(R.id.tag_telegram_animated_emoji_state) as? StandaloneState
        return state != null && state.eventId == eventId && state.emoji == emoji
    }

    /** Clear animation state when Epoxy/RecyclerView recycles the message TextView. */
    fun clear(textView: TextView) {
        val state = textView.getTag(R.id.tag_telegram_animated_emoji_state) as? StandaloneState
        textView.setTag(R.id.tag_telegram_animated_emoji_state, null)
        if (state != null) {
            state.restore(textView)
        } else {
            animationViewFor(textView)?.let { animationView ->
                animationView.cancelAnimation()
                animationView.visibility = View.GONE
                animationView.contentDescription = null
                animationView.setBackgroundColor(Color.TRANSPARENT)
                animationView.setImageDrawable(null)
            }
        }
    }

    /** Play the emoji animation above the transparent message content. */
    fun playStandalone(textView: TextView, emoji: String, eventId: String, seed: Int = eventId.hashCode()): Boolean {
        if (!textView.context.isAnimationEnabled()) return false
        val animationView = animationViewFor(textView) ?: return false

        val existing = textView.getTag(R.id.tag_telegram_animated_emoji_state) as? StandaloneState
        if (existing != null) {
            if (existing.eventId == eventId && existing.emoji == emoji) {
                return if (existing.compositionReady) existing.replay(textView) else true
            }
            clear(textView)
        }

        val snapshot = TextSnapshot(
                text = textView.text,
                gravity = textView.gravity,
                textSizePx = textView.textSize,
                alpha = textView.alpha,
                visibility = textView.visibility,
                contentDescription = textView.contentDescription
        )
        val state = StandaloneState(eventId, emoji, snapshot, animationView)
        textView.setTag(R.id.tag_telegram_animated_emoji_state, state)
        textView.addOnAttachStateChangeListener(state)

        loadCatalogAnimation(textView, animationView, state, emoji, seed)
        return true
    }

    private fun loadCatalogAnimation(
            textView: TextView,
            animationView: LottieAnimationView,
            state: StandaloneState,
            emoji: String,
            seed: Int
    ) {
        TelegramEmojiAssetCatalog.findAsync(
                context = textView.context,
                emoji = emoji,
                typesInPriorityOrder = listOf("animated", "dice"),
                seed = seed
        ) { asset ->
            if (textView.getTag(R.id.tag_telegram_animated_emoji_state) !== state) return@findAsync
            if (asset == null) {
                restoreIfCurrent(textView, state)
                return@findAsync
            }

            loadComposition(
                    context = textView.context,
                    asset = asset,
                    cacheKey = "telemoji:${asset.path}",
                    onSuccess = { composition ->
                        if (textView.getTag(R.id.tag_telegram_animated_emoji_state) !== state) return@loadComposition
                        try {
                            animationView.setBackgroundColor(Color.TRANSPARENT)
                            animationView.setComposition(composition)
                            animationView.repeatCount = 0
                            animationView.speed = 1f
                            animationView.progress = 0f
                            animationView.contentDescription = null
                            animationView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            animationView.isClickable = false
                            animationView.isFocusable = false
                            state.compositionReady = true
                            state.animatorListener = object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    if (textView.getTag(R.id.tag_telegram_animated_emoji_state) === state) {
                                        state.finished = true
                                    }
                                }

                                override fun onAnimationCancel(animation: Animator) {
                                    if (textView.getTag(R.id.tag_telegram_animated_emoji_state) === state) {
                                        state.finished = true
                                    }
                                }
                            }.also(animationView::addAnimatorListener)
                            // Hide only the glyph; its layout size and clickable target remain.
                            textView.alpha = 0f
                            animationView.visibility = View.VISIBLE
                            textView.requestLayout()
                            animationView.requestLayout()
                            state.startIfNeeded(textView)
                        } catch (throwable: Throwable) {
                            restoreIfCurrent(textView, state)
                        }
                    },
                    onFailure = {
                        restoreIfCurrent(textView, state)
                    }
            )
        }
    }

    /**
     * Animate picker cells only while the grid is idle. Static glyphs remain visible
     * until the real composition is ready; recycling cancels the prior cell request.
     */
    fun bindPickerPreview(
            animationView: LottieAnimationView,
            emoji: String,
            onAnimationReady: () -> Unit
    ) {
        clearPickerPreview(animationView)
        if (!animationView.context.isAnimationEnabled()) return
        val token = Any()
        animationView.setTag(R.id.tag_telegram_picker_emoji_state, token)
        TelegramEmojiAssetCatalog.findAsync(
                context = animationView.context,
                emoji = emoji,
                typesInPriorityOrder = listOf("animated"),
                seed = 0
        ) { asset ->
            if (animationView.getTag(R.id.tag_telegram_picker_emoji_state) !== token || asset == null) return@findAsync
            loadComposition(
                    context = animationView.context,
                    asset = asset,
                    cacheKey = "telemoji:${asset.path}",
                    onSuccess = { composition ->
                        if (animationView.getTag(R.id.tag_telegram_picker_emoji_state) !== token) return@loadComposition
                        animationView.setComposition(composition)
                        animationView.repeatCount = LottieDrawable.INFINITE
                        animationView.contentDescription = emoji
                        animationView.visibility = View.VISIBLE
                        onAnimationReady()
                        animationView.playAnimation()
                    },
                    onFailure = {
                        // Keep the static glyph if local and network assets are unavailable.
                    }
            )
        }
    }

    fun clearPickerPreview(animationView: LottieAnimationView) {
        animationView.setTag(R.id.tag_telegram_picker_emoji_state, null)
        animationView.cancelAnimation()
        animationView.visibility = View.GONE
        animationView.contentDescription = null
    }

    /** Replay a loaded animation when the user taps the emoji message. */
    fun replay(view: View): Boolean {
        val textView = view as? TextView ?: return false
        val state = textView.getTag(R.id.tag_telegram_animated_emoji_state) as? StandaloneState ?: return false
        // Do not dispatch a second load while the first composition is still downloading.
        if (!state.compositionReady) return true
        return state.replay(textView)
    }

    /** Render the real reaction animation in the parent overlay, then remove it after one play. */
    fun playReaction(context: Context, host: ViewGroup, emoji: String, centerX: Float, centerY: Float) {
        if (!context.isAnimationEnabled() || !host.isAttachedToWindow) return
        TelegramEmojiAssetCatalog.findAsync(
                context = context,
                emoji = emoji,
                typesInPriorityOrder = listOf("reactions", "generic_anim"),
                seed = Random.nextInt()
        ) { asset ->
            if (asset == null || !host.isAttachedToWindow) return@findAsync
            loadComposition(
                    context = context,
                    asset = asset,
                    cacheKey = "telemoji:${asset.path}",
                    onSuccess = { composition ->
                        if (!host.isAttachedToWindow) return@loadComposition
                        val overlay = host.overlay
                        val size = dp(context, REACTION_SIZE_DP)
                        val animationView = LottieAnimationView(host.context).apply {
                            setComposition(composition)
                            repeatCount = 0
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            contentDescription = null
                            isClickable = false
                            isFocusable = false
                        }
                        animationView.measure(
                                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
                        )
                        val left = (centerX - size / 2f).toInt()
                        val top = (centerY - size / 2f).toInt()
                        animationView.layout(left, top, left + size, top + size)

                        var removed = false
                        var detachListener: View.OnAttachStateChangeListener? = null
                        val removeAnimation: () -> Unit = {
                            if (!removed) {
                                removed = true
                                overlay.remove(animationView)
                                detachListener?.let { host.removeOnAttachStateChangeListener(it) }
                            }
                        }
                        detachListener = object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(view: View) = Unit
                            override fun onViewDetachedFromWindow(view: View) = removeAnimation()
                        }
                        animationView.addAnimatorListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) = removeAnimation()
                            override fun onAnimationCancel(animation: Animator) = removeAnimation()
                        })
                        host.addOnAttachStateChangeListener(detachListener)
                        overlay.add(animationView)
                        animationView.progress = 0f
                        animationView.playAnimation()
                    },
                    onFailure = {
                        // Preserve the Matrix reaction state if loading fails.
                    }
            )
        }
    }

    /**
     * Prefer APK-bundled animations for immediate playback and offline support.
     * Remote fetching remains a fallback for older/incomplete APKs.
     */
    private fun loadComposition(
            context: Context,
            asset: TelegramEmojiAssetCatalog.AnimationAsset,
            cacheKey: String,
            onSuccess: (LottieComposition) -> Unit,
            onFailure: () -> Unit
    ) {
        val appContext = context.applicationContext
        LottieCompositionFactory.fromAsset(
                appContext,
                "telegram_emoji/${asset.path}",
                cacheKey
        ).addListener { composition ->
            onSuccess(composition)
        }.addFailureListener {
            LottieCompositionFactory.fromUrl(
                    appContext,
                    asset.url,
                    cacheKey
            ).addListener { composition ->
                onSuccess(composition)
            }.addFailureListener {
                onFailure()
            }
        }
    }

    private fun restoreIfCurrent(view: TextView, state: StandaloneState) {
        if (view.getTag(R.id.tag_telegram_animated_emoji_state) !== state) return
        view.setTag(R.id.tag_telegram_animated_emoji_state, null)
        state.restore(view)
    }

    private fun dp(context: Context, value: Int): Int =
            (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
