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
import im.vector.app.features.animatedemoji.TelegramEmojiAnimationController
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
        messageView.onClick { view ->
            if (!TelegramEmojiAnimationController.replay(view) && useBigFont) {
                messageView.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { emoji ->
                    TelegramEmojiAnimationController.playStandalone(
                            messageView,
                            emoji,
                            attributes.informationData.eventId.hashCode()
                    )
                }
            }
            attributes.itemClickListener?.invoke(view)
        }
        messageView.onLongClickIgnoringLinks(attributes.itemLongClickListener)
        TelegramEmojiAnimationController.clear(messageView)
        messageView.setTextWithEmojiSupport(message?.charSequence, bindingOptions)
        animateRecentStandaloneEmoji(messageView)
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
    /**
     * Replace a recent standalone Unicode emoji with its real Lottie asset where
     * available. The event-id guard prevents RecyclerView rebinding from replaying it.
     */
    private fun animateRecentStandaloneEmoji(messageView: AppCompatTextView) {
        val info = attributes.informationData

        // Reset properties potentially left behind by view recycling.
        messageView.animate().cancel()
        messageView.scaleX = 1f
        messageView.scaleY = 1f
        messageView.alpha = 1f
        messageView.rotation = 0f
        messageView.translationX = 0f
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

        val emoji = messageView.text?.toString()?.trim().orEmpty()
        if (emoji.isNotEmpty()) {
            TelegramEmojiAnimationController.playStandalone(
                    messageView,
                    emoji,
                    info.eventId.hashCode()
            )
        }
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
