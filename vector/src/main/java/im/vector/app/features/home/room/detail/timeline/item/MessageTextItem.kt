/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.item

import android.text.Spanned
import android.text.method.MovementMethod
import android.view.View
import android.view.ViewStub
import android.view.animation.OvershootInterpolator
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.PrecomputedTextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import im.vector.app.R
import im.vector.app.core.epoxy.onClick
import im.vector.app.core.epoxy.onLongClickIgnoringLinks
import im.vector.app.core.utils.isAnimationEnabled
import im.vector.app.features.home.room.detail.timeline.TimelineEventController
import im.vector.app.features.home.room.detail.timeline.style.TimelineMessageLayout
import im.vector.app.features.home.room.detail.timeline.tools.findPillsAndProcess
import im.vector.app.features.home.room.detail.timeline.url.PreviewUrlRetriever
import im.vector.app.features.home.room.detail.timeline.url.PreviewUrlUiState
import im.vector.app.features.home.room.detail.timeline.view.MessageBubbleView
import im.vector.app.features.home.room.detail.timeline.url.PreviewUrlView
import im.vector.app.features.media.ImageContentRenderer
import im.vector.lib.core.utils.epoxy.charsequence.EpoxyCharSequence
import io.element.android.wysiwyg.EditorStyledTextView
import io.noties.markwon.MarkwonPlugin
import org.matrix.android.sdk.api.extensions.orFalse

@EpoxyModelClass
abstract class MessageTextItem : AbsMessageItem<MessageTextItem.Holder>() {

    @EpoxyAttribute
    var searchForPills: Boolean = false

    @EpoxyAttribute
    var message: EpoxyCharSequence? = null

    @EpoxyAttribute
    var bindingOptions: BindingOptions? = null

    @EpoxyAttribute
    var useBigFont: Boolean = false

    @EpoxyAttribute
    var previewUrlRetriever: PreviewUrlRetriever? = null

    @EpoxyAttribute
    var previewUrlCallback: TimelineEventController.PreviewUrlCallback? = null

    @EpoxyAttribute
    var imageContentRenderer: ImageContentRenderer? = null

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var movementMethod: MovementMethod? = null

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var markwonPlugins: (List<MarkwonPlugin>)? = null

    @EpoxyAttribute
    var useRichTextEditorStyle: Boolean = false

    private val previewUrlViewUpdater = PreviewUrlViewUpdater()

    override fun bind(holder: Holder) {
        // Preview URL
        previewUrlViewUpdater.previewUrlView = holder.previewUrlView
        previewUrlViewUpdater.imageContentRenderer = imageContentRenderer
        val safePreviewUrlRetriever = previewUrlRetriever
        if (safePreviewUrlRetriever == null) {
            holder.previewUrlView.isVisible = false
        } else {
            safePreviewUrlRetriever.addListener(attributes.informationData.eventId, previewUrlViewUpdater)
        }
        holder.previewUrlView.delegate = previewUrlCallback
        holder.previewUrlView.renderMessageLayout(attributes.informationData.messageLayout)
        if (useRichTextEditorStyle) {
            holder.plainMessageView?.isVisible = false
        } else {
            holder.richMessageView?.isVisible = false
        }
        val messageView: AppCompatTextView = if (useRichTextEditorStyle) holder.requireRichMessageView() else holder.requirePlainMessageView()
        messageView.isVisible = true
        // Resolve paragraph direction from the message text, not from the app's
        // global layout direction. This keeps Persian/Arabic text right-aligned
        // while leaving navigation and the overall chat layout in LTR when needed.
        messageView.textDirection = View.TEXT_DIRECTION_FIRST_STRONG_LTR
        messageView.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
        if (useBigFont) {
            messageView.textSize = 44F
        } else {
            messageView.textSize = 16F
        }
        if (searchForPills) {
            message?.charSequence?.findPillsAndProcess(coroutineScope) {
                // mmm.. not sure this is so safe in regards to cell reuse
                it.bind(messageView)
            }
        }
        message?.charSequence.let { charSequence ->
            markwonPlugins?.forEach { plugin -> plugin.beforeSetText(messageView, charSequence as Spanned) }
        }
        super.bind(holder)
        messageView.movementMethod = movementMethod
        renderSendState(messageView, messageView)
        messageView.onClick(attributes.itemClickListener)
        messageView.onLongClickIgnoringLinks(attributes.itemLongClickListener)
        messageView.setTextWithEmojiSupport(message?.charSequence, bindingOptions)
        animateNewOutgoingEmoji(messageView)
        // The send-state binder applies Element's generic text tint; restore the
        // bubble-specific palette so the outgoing Telegram-blue bubble stays readable.
        val bubbleLayout = attributes.informationData.messageLayout as? TimelineMessageLayout.Bubble
        if (bubbleLayout != null) {
            (holder.view as? MessageBubbleView)?.refreshBubbleTextColor(bubbleLayout)
        }
        markwonPlugins?.forEach { plugin -> plugin.afterSetText(messageView) }
    }

    /**
     * Telegram-inspired entrance for a standalone large-emoji message. Run it for
     * freshly received as well as locally sent events, but never for old history.
     * Remember event IDs so RecyclerView/Epoxy rebinding cannot replay it while
     * the user scrolls through the same timeline.
     */
    private fun animateNewOutgoingEmoji(messageView: AppCompatTextView) {
        val info = attributes.informationData

        // Epoxy may recycle a TextView that was animated for a previous message.
        // Restore its baseline before checking whether this event should animate.
        messageView.animate().cancel()
        messageView.scaleX = 1f
        messageView.scaleY = 1f
        messageView.alpha = 1f
        messageView.rotation = 0f
        messageView.translationY = 0f

        if (!useBigFont || !messageView.context.isAnimationEnabled()) return
        val localTimestamp = info.ageLocalTS ?: return
        val ageMs = System.currentTimeMillis() - localTimestamp
        if (ageMs !in -2_000L..8_000L) return

        val shouldAnimate = synchronized(animatedBigEmojiEventIds) {
            if (animatedBigEmojiEventIds.contains(info.eventId)) {
                false
            } else {
                animatedBigEmojiEventIds.add(info.eventId)
                if (animatedBigEmojiEventIds.size > MAX_REMEMBERED_BIG_EMOJI_ANIMATIONS) {
                    val iterator = animatedBigEmojiEventIds.iterator()
                    if (iterator.hasNext()) {
                        iterator.next()
                        iterator.remove()
                    }
                }
                true
            }
        }
        if (!shouldAnimate) return

        val density = messageView.resources.displayMetrics.density
        messageView.pivotX = messageView.width / 2f
        messageView.pivotY = messageView.height / 2f
        messageView.scaleX = 0.48f
        messageView.scaleY = 0.48f
        messageView.alpha = 0.22f
        messageView.rotation = if (info.sentByMe) -8f else 8f
        messageView.translationY = 8f * density
        messageView.translationX = 0f

        val emoji = messageView.text?.toString()?.trim().orEmpty()
        val motion = when {
            containsAnyEmoji(emoji, listOf("😂", "🤣", "😹", "😆", "😅")) -> listOf(
                    EmojiMotionStep(1.18f, 1.18f, if (info.sentByMe) 12f else -12f, 3f, -2f, 1f, 145L),
                    EmojiMotionStep(1.04f, 1.04f, if (info.sentByMe) -11f else 11f, -4f, 0f, 1f, 105L),
                    EmojiMotionStep(1.10f, 1.10f, if (info.sentByMe) 5f else -5f, 2f, 0f, 1f, 100L),
                    EmojiMotionStep(1f, 1f, 0f, 0f, 0f, 1f, 110L)
            )
            containsAnyEmoji(emoji, listOf("😭", "😢", "🥹", "☹️", "😿")) -> listOf(
                    EmojiMotionStep(1.10f, 1.12f, 0f, 0f, -3f, 1f, 180L),
                    EmojiMotionStep(0.98f, 0.98f, 0f, 0f, 4f, 1f, 170L),
                    EmojiMotionStep(1.02f, 1.02f, 0f, 0f, -1f, 1f, 120L),
                    EmojiMotionStep(1f, 1f, 0f, 0f, 0f, 1f, 100L)
            )
            containsAnyEmoji(emoji, listOf("❤️", "❤", "💕", "💖", "💗", "💓", "💞", "💘", "😍", "🥰")) -> listOf(
                    EmojiMotionStep(1.30f, 1.30f, 0f, 0f, 0f, 1f, 145L),
                    EmojiMotionStep(0.88f, 0.88f, 0f, 0f, 0f, 1f, 115L),
                    EmojiMotionStep(1.17f, 1.17f, 0f, 0f, 0f, 1f, 135L),
                    EmojiMotionStep(1f, 1f, 0f, 0f, 0f, 1f, 110L)
            )
            containsAnyEmoji(emoji, listOf("🔥", "💥")) -> listOf(
                    EmojiMotionStep(1.14f, 1.18f, 0f, 0f, -7f, 1f, 150L),
                    EmojiMotionStep(0.98f, 0.96f, 0f, 0f, 3f, 1f, 115L),
                    EmojiMotionStep(1.08f, 1.10f, 0f, 0f, -3f, 1f, 115L),
                    EmojiMotionStep(1f, 1f, 0f, 0f, 0f, 1f, 105L)
            )
            containsAnyEmoji(emoji, listOf("👍", "👎", "👏", "🙏", "💪")) -> listOf(
                    EmojiMotionStep(1.16f, 1.16f, if (info.sentByMe) -14f else 14f, 0f, -2f, 1f, 160L),
                    EmojiMotionStep(1.04f, 1.04f, if (info.sentByMe) 7f else -7f, 0f, 0f, 1f, 120L),
                    EmojiMotionStep(1f, 1f, 0f, 0f, 0f, 1f, 120L)
            )
            containsAnyEmoji(emoji, listOf("🎉", "🎊", "🥳", "✨", "🌟")) -> listOf(
                    EmojiMotionStep(1.20f, 1.20f, if (info.sentByMe) -16f else 16f, 0f, -6f, 1f, 170L),
                    EmojiMotionStep(1.07f, 1.07f, if (info.sentByMe) 11f else -11f, 0f, 2f, 1f, 120L),
                    EmojiMotionStep(1f, 1f, 0f, 0f, 0f, 1f, 130L)
            )
            else -> listOf(
                    EmojiMotionStep(1.12f, 1.12f, if (info.sentByMe) -4f else 4f, 0f, -2f, 1f, 250L),
                    EmojiMotionStep(1f, 1f, 0f, 0f, 0f, 1f, 130L)
            )
        }
        runEmojiMotion(messageView, motion, 0, density)
    }

    private fun containsAnyEmoji(text: String, emojis: List<String>): Boolean = emojis.any(text::contains)

    private data class EmojiMotionStep(
            val scaleX: Float,
            val scaleY: Float,
            val rotation: Float,
            val translationX: Float,
            val translationY: Float,
            val alpha: Float,
            val durationMs: Long
    )

    private fun runEmojiMotion(
            messageView: AppCompatTextView,
            steps: List<EmojiMotionStep>,
            index: Int,
            density: Float
    ) {
        if (index >= steps.size) {
            messageView.scaleX = 1f
            messageView.scaleY = 1f
            messageView.alpha = 1f
            messageView.rotation = 0f
            messageView.translationX = 0f
            messageView.translationY = 0f
            return
        }
        val step = steps[index]
        messageView.animate()
                .scaleX(step.scaleX)
                .scaleY(step.scaleY)
                .alpha(step.alpha)
                .rotation(step.rotation)
                .translationX(step.translationX * density)
                .translationY(step.translationY * density)
                .setDuration(step.durationMs)
                .setInterpolator(OvershootInterpolator(1.15f))
                .withEndAction { runEmojiMotion(messageView, steps, index + 1, density) }
                .start()
    }

    private fun AppCompatTextView.setTextWithEmojiSupport(message: CharSequence?, bindingOptions: BindingOptions?) {
        if (bindingOptions?.canUseTextFuture.orFalse() && message != null) {
            val textFuture = PrecomputedTextCompat.getTextFuture(message, TextViewCompat.getTextMetricsParams(this), null)
            setTextFuture(textFuture)
        } else {
            setTextFuture(null)
            text = message
        }
    }

    override fun unbind(holder: Holder) {
        super.unbind(holder)
        previewUrlViewUpdater.previewUrlView = null
        previewUrlViewUpdater.imageContentRenderer = null
        previewUrlRetriever?.removeListener(attributes.informationData.eventId, previewUrlViewUpdater)
    }

    override fun getViewStubId() = STUB_ID

    class Holder : AbsMessageItem.Holder(STUB_ID) {
        val previewUrlView by bind<PreviewUrlView>(R.id.messageUrlPreview)
        private val richMessageStub by bind<ViewStub>(R.id.richMessageTextViewStub)
        private val plainMessageStub by bind<ViewStub>(R.id.plainMessageTextViewStub)
        var richMessageView: EditorStyledTextView? = null
            private set
        var plainMessageView: AppCompatTextView? = null
            private set

        fun requireRichMessageView(): AppCompatTextView {
            val view = richMessageView ?: richMessageStub.inflate().findViewById<EditorStyledTextView>(R.id.messageTextView).also {
                // Required to ensure that `inlineCodeBgHelper` and `codeBlockBgHelper` are initialized
                it.updateStyle(
                        styleConfig = it.styleConfig,
                        mentionDisplayHandler = null,
                )
            }
            richMessageView = view
            return view
        }

        fun requirePlainMessageView(): AppCompatTextView {
            val view = plainMessageView ?: plainMessageStub.inflate().findViewById(R.id.messageTextView)
            plainMessageView = view
            return view
        }
    }

    inner class PreviewUrlViewUpdater : PreviewUrlRetriever.PreviewUrlRetrieverListener {
        var previewUrlView: PreviewUrlView? = null
        var imageContentRenderer: ImageContentRenderer? = null

        override fun onStateUpdated(state: PreviewUrlUiState) {
            val safeImageContentRenderer = imageContentRenderer
            if (safeImageContentRenderer == null) {
                previewUrlView?.isVisible = false
                return
            }
            previewUrlView?.render(state, safeImageContentRenderer)
        }
    }

    companion object {
        private const val MAX_REMEMBERED_BIG_EMOJI_ANIMATIONS = 256
        private val animatedBigEmojiEventIds = LinkedHashSet<String>()
        private val STUB_ID = R.id.messageContentTextStub
    }
}
