/*
 * Copyright 2022-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.style

import android.content.res.Resources
import im.vector.app.core.utils.isSingleEmoji
import im.vector.app.core.extensions.getVectorLastMessageContent
import im.vector.app.features.home.TelegramChannel
import im.vector.app.core.extensions.localDateTime
import im.vector.app.features.home.room.detail.timeline.factory.TimelineItemFactoryParams
import im.vector.app.features.settings.VectorPreferences
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.events.model.EventType
import org.matrix.android.sdk.api.session.room.model.message.MessageContent
import org.matrix.android.sdk.api.session.room.model.message.MessageTextContent
import org.matrix.android.sdk.api.session.room.model.message.MessageType
import org.matrix.android.sdk.api.session.room.model.message.MessageVerificationRequestContent
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import org.matrix.android.sdk.api.session.room.timeline.isEdition
import org.matrix.android.sdk.api.session.room.timeline.isRootThread
import javax.inject.Inject

class TimelineMessageLayoutFactory @Inject constructor(
        private val session: Session,
        private val layoutSettingsProvider: TimelineLayoutSettingsProvider,
        private val resources: Resources,
        private val vectorPreferences: VectorPreferences
) {

    companion object {
        // Can be rendered in bubbles, other types will fallback to default
        private val EVENT_TYPES_WITH_BUBBLE_LAYOUT = setOf(
                EventType.MESSAGE,
                EventType.ENCRYPTED,
                EventType.STICKER,
        ) +
                EventType.POLL_START.values +
                EventType.POLL_END.values +
                EventType.STATE_ROOM_BEACON_INFO.values

        // Can't be rendered in bubbles, so get back to default layout
        private val MSG_TYPES_WITHOUT_BUBBLE_LAYOUT = setOf(
                MessageType.MSGTYPE_VERIFICATION_REQUEST
        )

        // Use the bubble layout but without borders
        private val MSG_TYPES_WITH_PSEUDO_BUBBLE_LAYOUT = setOf(
                MessageType.MSGTYPE_IMAGE,
                MessageType.MSGTYPE_VIDEO,
                MessageType.MSGTYPE_STICKER_LOCAL,
                MessageType.MSGTYPE_EMOTE,
                MessageType.MSGTYPE_BEACON_INFO,
                MessageType.MSGTYPE_LOCATION,
                MessageType.MSGTYPE_BEACON_LOCATION_DATA,
        )
        private val MSG_TYPES_WITH_TIMESTAMP_INSIDE_MESSAGE = setOf(
                MessageType.MSGTYPE_IMAGE,
                MessageType.MSGTYPE_VIDEO,
                MessageType.MSGTYPE_STICKER_LOCAL,
                MessageType.MSGTYPE_BEACON_INFO,
                MessageType.MSGTYPE_LOCATION,
                MessageType.MSGTYPE_BEACON_LOCATION_DATA,
        )
    }

    private val cornerRadius: Float by lazy {
        resources.getDimensionPixelSize(im.vector.lib.ui.styles.R.dimen.chat_bubble_corner_radius).toFloat()
    }

    private val groupedCornerRadius: Float by lazy {
        resources.getDimensionPixelSize(im.vector.lib.ui.styles.R.dimen.chat_bubble_grouped_corner_radius).toFloat()
    }

    private val tailCornerRadius: Float by lazy {
        resources.getDimensionPixelSize(im.vector.lib.ui.styles.R.dimen.chat_bubble_tail_corner_radius).toFloat()
    }

    fun create(params: TimelineItemFactoryParams): TimelineMessageLayout {
        val event = params.event
        val nextDisplayableEvent = params.nextDisplayableEvent
        val prevDisplayableEvent = params.prevDisplayableEvent
        val isSentByMe = event.root.senderId == session.myUserId
        val isTelegramChannel = params.partialState.roomSummary?.roomType == TelegramChannel.ROOM_TYPE
        // A channel is a broadcast feed: post alignment must not depend on which
        // administrator account published the Matrix event.
        val isSentByMeForLayout = isSentByMe && !isTelegramChannel

        val date = event.root.localDateTime()
        val nextDate = nextDisplayableEvent?.root?.localDateTime()
        val addDaySeparator = date.toLocalDate() != nextDate?.toLocalDate()

        val isNextMessageReceivedMoreThanOneHourAgo = nextDate?.isBefore(date.minusMinutes(60))
                ?: false

        val showInformation = addDaySeparator ||
                event.senderInfo.avatarUrl != nextDisplayableEvent?.senderInfo?.avatarUrl ||
                event.senderInfo.disambiguatedDisplayName != nextDisplayableEvent?.senderInfo?.disambiguatedDisplayName ||
                nextDisplayableEvent.root.getClearType() !in listOf(EventType.MESSAGE, EventType.STICKER, EventType.ENCRYPTED) ||
                isNextMessageReceivedMoreThanOneHourAgo ||
                isTileTypeMessage(nextDisplayableEvent) ||
                nextDisplayableEvent.isRootThread() ||
                event.isRootThread() ||
                nextDisplayableEvent.isEdition()

        val messageContent = event.getVectorLastMessageContent()
        val isStandaloneEmoji = messageContent.isStandaloneEmoji()
        val shouldBuildBubble = event.shouldBuildBubbleLayout()
        val useBubbleLayout = layoutSettingsProvider.getLayoutSettings() == TimelineLayoutSettings.BUBBLE
        val isDirectRoom = params.partialState.roomSummary?.isDirect == true
        // Telegram 1:1 chats do not repeat an avatar/name on each incoming message.
        val showSenderDetails = showInformation && !isSentByMe && !isDirectRoom && !isTelegramChannel

        // Standalone emoji messages must use transparent Telegram-style geometry even
        // when the user's regular timeline preference is Element's modern layout.
        val messageLayout = if (shouldBuildBubble && (useBubbleLayout || isStandaloneEmoji || isTelegramChannel)) {
            buildBubbleLayout(
                    event = event,
                    nextDisplayableEvent = nextDisplayableEvent,
                    prevDisplayableEvent = prevDisplayableEvent,
                    addDaySeparator = addDaySeparator,
                    showInformation = showSenderDetails,
                    isSentByMe = isSentByMeForLayout,
                    messageContent = messageContent,
                    isStandaloneEmoji = isStandaloneEmoji,
                    isDirectRoom = isDirectRoom || isTelegramChannel,
                    isTelegramChannel = isTelegramChannel
            )
        } else {
            buildModernLayout(showSenderDetails, showInformation)
        }
        return messageLayout
    }

    private fun buildBubbleLayout(
            event: TimelineEvent,
            nextDisplayableEvent: TimelineEvent?,
            prevDisplayableEvent: TimelineEvent?,
            addDaySeparator: Boolean,
            showInformation: Boolean,
            isSentByMe: Boolean,
            messageContent: MessageContent?,
            isStandaloneEmoji: Boolean,
            isDirectRoom: Boolean,
            isTelegramChannel: Boolean
    ): TimelineMessageLayout.Bubble {
        val isFirstFromThisSender = isTelegramChannel || nextDisplayableEvent == null ||
                !nextDisplayableEvent.shouldBuildBubbleLayout() ||
                nextDisplayableEvent.root.senderId != event.root.senderId ||
                addDaySeparator
        val isLastFromThisSender = isTelegramChannel || prevDisplayableEvent == null ||
                !prevDisplayableEvent.shouldBuildBubbleLayout() ||
                prevDisplayableEvent.root.senderId != event.root.senderId ||
                prevDisplayableEvent.root.localDateTime().toLocalDate() != event.root.localDateTime().toLocalDate()

        return TimelineMessageLayout.Bubble(
                showAvatar = showInformation && !isSentByMe && !isTelegramChannel,
                showDisplayName = showInformation && !isSentByMe && !isTelegramChannel,
                addTopMargin = isFirstFromThisSender,
                isIncoming = isTelegramChannel || !isSentByMe,
                cornersRadius = buildCornersRadius(
                        isIncoming = !isSentByMe,
                        isFirstFromThisSender = isFirstFromThisSender,
                        isLastFromThisSender = isLastFromThisSender
                ),
                isPseudoBubble = if (isTelegramChannel) false else messageContent.isPseudoBubble() || isStandaloneEmoji,
                timestampInsideMessage = messageContent.timestampInsideMessage() || isStandaloneEmoji,
                // Channel post time sits on the post surface, not over the video thumbnail.
                addMessageOverlay = if (isStandaloneEmoji || isTelegramChannel) false else messageContent.shouldAddMessageOverlay(),
                isDirect = isDirectRoom,
                isTelegramChannel = isTelegramChannel
        )
    }

    /**
     * Telegram renders a message containing exactly one emoji as a large, transparent
     * animated emoji instead of a colored text bubble. Use Unicode grapheme boundaries
     * so skin-tone, flag, and ZWJ family emoji are treated as one emoji.
     */
    private fun MessageContent?.isStandaloneEmoji(): Boolean {
        val body = (this as? MessageTextContent)?.body.orEmpty()
        return isSingleEmoji(body)
    }

    private fun MessageContent?.isPseudoBubble(): Boolean {
        if (this == null) return false
        return this.msgType in MSG_TYPES_WITH_PSEUDO_BUBBLE_LAYOUT
    }

    private fun MessageContent?.timestampInsideMessage(): Boolean {
        return when {
            this == null -> false
            else -> msgType in MSG_TYPES_WITH_TIMESTAMP_INSIDE_MESSAGE
        }
    }

    private fun MessageContent?.shouldAddMessageOverlay(): Boolean {
        return when {
            this == null || msgType == MessageType.MSGTYPE_BEACON_INFO -> false
            else -> msgType in MSG_TYPES_WITH_TIMESTAMP_INSIDE_MESSAGE
        }
    }

    private fun TimelineEvent.shouldBuildBubbleLayout(): Boolean {
        val type = root.getClearType()
        if (type in EVENT_TYPES_WITH_BUBBLE_LAYOUT) {
            val messageContent = getVectorLastMessageContent()
            return messageContent?.msgType !in MSG_TYPES_WITHOUT_BUBBLE_LAYOUT
        }
        return false
    }

    private fun buildModernLayout(showSenderDetails: Boolean, showTimestamp: Boolean): TimelineMessageLayout.Default {
        return TimelineMessageLayout.Default(
                showAvatar = showSenderDetails,
                showDisplayName = showSenderDetails,
                showTimestamp = showTimestamp || vectorPreferences.alwaysShowTimeStamps()
        )
    }

    private fun buildCornersRadius(
            isIncoming: Boolean,
            isFirstFromThisSender: Boolean,
            isLastFromThisSender: Boolean
    ): TimelineMessageLayout.Bubble.CornersRadius {
        // The chat chrome is intentionally LTR (including for Persian/Arabic locales).
        // Keep grouped-bubble corners on that fixed horizontal axis; paragraph direction
        // is resolved independently by the message TextView..
        val groupedTopRadius = if (isFirstFromThisSender) cornerRadius else groupedCornerRadius
        // The final bubble in a sender group gets Telegram's compact lower tail corner.
        // Intermediate bubbles stay softly joined with the smaller grouped radius.
        val groupedBottomRadius = if (isLastFromThisSender) tailCornerRadius else groupedCornerRadius

        return if (isIncoming) {
            TimelineMessageLayout.Bubble.CornersRadius(
                    topStartRadius = groupedTopRadius,
                    topEndRadius = cornerRadius,
                    bottomStartRadius = groupedBottomRadius,
                    bottomEndRadius = cornerRadius
            )
        } else {
            TimelineMessageLayout.Bubble.CornersRadius(
                    topStartRadius = cornerRadius,
                    topEndRadius = groupedTopRadius,
                    bottomStartRadius = cornerRadius,
                    bottomEndRadius = groupedBottomRadius
            )
        }
    }

    /**
     * Tiles type message never show the sender information (like verification request), so we should repeat it for next message
     * even if same sender.
     */
    private fun isTileTypeMessage(event: TimelineEvent?): Boolean {
        return when (event?.root?.getClearType()) {
            EventType.KEY_VERIFICATION_DONE,
            EventType.KEY_VERIFICATION_CANCEL -> true
            EventType.MESSAGE -> {
                event.getVectorLastMessageContent() is MessageVerificationRequestContent
            }
            else -> false
        }
    }
}
