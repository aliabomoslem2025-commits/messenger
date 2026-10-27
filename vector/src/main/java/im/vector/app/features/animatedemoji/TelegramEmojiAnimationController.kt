/*
 * Animated-emoji playback backed by Lottie assets.
 * See docs/TELEGRAM_ANIMATED_EMOJI_ASSETS.md for asset attribution and runtime behavior.
 */
package im.vector.app.features.animatedemoji

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import im.vector.app.R
import im.vector.app.core.utils.isAnimationEnabled
import kotlin.random.Random

object TelegramEmojiAnimationController {
    private const val STANDALONE_SIZE_DP = 72
    private const val REACTION_SIZE_DP = 104

    private class TextSnapshot(
            val text: CharSequence?,
            val gravity: Int,
            val textSizePx: Float,
            val drawables: Array<Drawable?>,
            val compoundDrawablePadding: Int,
            val contentDescription: CharSequence?
    )

    private class StandaloneState(
            private val snapshot: TextSnapshot
    ) : View.OnAttachStateChangeListener {
        var drawable: LottieDrawable? = null
        var hasStarted = false
        var finished = false

        override fun onViewAttachedToWindow(view: View) {
            val textView = view as? TextView ?: return
            startIfNeeded(textView)
        }

        override fun onViewDetachedFromWindow(view: View) {
            drawable?.pauseAnimation()
        }

        fun startIfNeeded(view: TextView) {
            val animation = drawable ?: return
            if (!view.isAttachedToWindow || finished) return
            if (!hasStarted) {
                animation.progress = 0f
                animation.playAnimation()
                hasStarted = true
            } else {
                animation.resumeAnimation()
            }
        }

        fun replay(view: TextView): Boolean {
            if (drawable == null) return false
            finished = false
            hasStarted = false
            drawable?.progress = 0f
            startIfNeeded(view)
            return true
        }

        fun restore(view: TextView) {
            view.removeOnAttachStateChangeListener(this)
            drawable?.stop()
            drawable = null
            view.setCompoundDrawables(
                    snapshot.drawables.getOrNull(0),
                    snapshot.drawables.getOrNull(1),
                    snapshot.drawables.getOrNull(2),
                    snapshot.drawables.getOrNull(3)
            )
            view.compoundDrawablePadding = snapshot.compoundDrawablePadding
            view.text = snapshot.text
            view.gravity = snapshot.gravity
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, snapshot.textSizePx)
            view.contentDescription = snapshot.contentDescription
            view.requestLayout()
        }
    }

    /** Clear animation state when Epoxy/RecyclerView reuses the message TextView. */
    fun clear(textView: TextView) {
        val state = textView.getTag(R.id.tag_telegram_animated_emoji_state) as? StandaloneState ?: return
        textView.setTag(R.id.tag_telegram_animated_emoji_state, null)
        state.restore(textView)
    }

    /** Load a matching standalone emoji. Unicode remains visible while loading or on failure. */
    fun playStandalone(textView: TextView, emoji: String, seed: Int = 0): Boolean {
        if (!textView.context.isAnimationEnabled()) return false

        val existing = textView.getTag(R.id.tag_telegram_animated_emoji_state) as? StandaloneState
        if (existing != null) {
            return if (existing.drawable != null) existing.replay(textView) else true
        }

        val snapshot = TextSnapshot(
                text = textView.text,
                gravity = textView.gravity,
                textSizePx = textView.textSize,
                drawables = textView.compoundDrawables,
                compoundDrawablePadding = textView.compoundDrawablePadding,
                contentDescription = textView.contentDescription
        )
        val state = StandaloneState(snapshot)
        textView.setTag(R.id.tag_telegram_animated_emoji_state, state)
        textView.addOnAttachStateChangeListener(state)

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

            LottieCompositionFactory.fromUrl(
                    textView.context.applicationContext,
                    asset.url,
                    "telemoji:${asset.path}"
            ).addListener { composition ->
                if (textView.getTag(R.id.tag_telegram_animated_emoji_state) !== state) return@addListener
                try {
                    val drawable = LottieDrawable().apply {
                        this.composition = composition
                        repeatCount = 0
                        val size = dp(textView.context, STANDALONE_SIZE_DP)
                        setBounds(0, 0, size, size)
                    }
                    drawable.addAnimatorListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (textView.getTag(R.id.tag_telegram_animated_emoji_state) === state) {
                                state.finished = true
                            }
                        }
                    })
                    state.drawable = drawable
                    textView.text = ""
                    textView.gravity = Gravity.CENTER
                    textView.compoundDrawablePadding = 0
                    textView.contentDescription = snapshot.text?.toString() ?: emoji
                    textView.setCompoundDrawables(null, drawable, null, null)
                    textView.requestLayout()
                    state.startIfNeeded(textView)
                } catch (throwable: Throwable) {
                    restoreIfCurrent(textView, state)
                }
            }.addFailureListener {
                restoreIfCurrent(textView, state)
            }
        }
        return true
    }

    /** Replay the animation when a user taps a previously animated emoji message. */
    fun replay(view: View): Boolean {
        val textView = view as? TextView ?: return false
        val state = textView.getTag(R.id.tag_telegram_animated_emoji_state) as? StandaloneState ?: return false
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
            LottieCompositionFactory.fromUrl(
                    context.applicationContext,
                    asset.url,
                    "telemoji:${asset.path}"
            ).addListener { composition ->
                if (!host.isAttachedToWindow) return@addListener
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
                detachListener?.let { host.addOnAttachStateChangeListener(it) }
                overlay.add(animationView)
                animationView.progress = 0f
                animationView.playAnimation()
            }.addFailureListener {
                // Missing remote assets leave the existing Matrix reaction UI untouched.
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
