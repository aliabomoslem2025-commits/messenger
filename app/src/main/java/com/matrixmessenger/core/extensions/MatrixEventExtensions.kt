package com.matrixmessenger.core.extensions

import org.matrix.android.sdk.api.session.room.model.message.MessageContent
import org.matrix.android.sdk.api.session.room.model.message.MessageContentWithFormattedBody
import org.matrix.android.sdk.api.session.room.model.message.MessageWithAttachmentContent
import org.matrix.android.sdk.api.session.room.model.message.MessageType
import com.matrixmessenger.BuildConfig
import timber.log.Timber

/**
 * Extension functions for Matrix Event types
 */

/**
 * Get formatted body from message content, falling back to body if not available
 */
fun MessageContent?.getFormattedBody(): String {
    return when (this) {
        is MessageContentWithFormattedBody -> {
            this.formattedBody ?: this.body ?: ""
        }
        else -> {
            this?.body ?: ""
        }
    }
}

/**
 * Check if message content is text type
 */
fun MessageContent?.isTextType(): Boolean {
    return (this?.msgType == MessageType.MSGTYPE_TEXT || 
            this?.msgType == MessageType.MSGTYPE_EMOTE || 
            this?.msgType == MessageType.MSGTYPE_NOTICE)
}

/**
 * Check if message content is image type
 */
fun MessageContent?.isImageType(): Boolean {
    return this?.msgType == MessageType.MSGTYPE_IMAGE
}

/**
 * Check if message content is video type
 */
fun MessageContent?.isVideoType(): Boolean {
    return this?.msgType == MessageType.MSGTYPE_VIDEO
}

/**
 * Check if message content is file type
 */
fun MessageContent?.isFileType(): Boolean {
    return this?.msgType == MessageType.MSGTYPE_FILE
}

/**
 * Check if message content is audio type
 */
fun MessageContent?.isAudioType(): Boolean {
    return this?.msgType == MessageType.MSGTYPE_AUDIO
}

/**
 * Check if message content is location type
 */
fun MessageContent?.isLocationType(): Boolean {
    return this?.msgType == MessageType.MSGTYPE_LOCATION
}

/**
 * Get media URL from message content
 */
fun MessageContent?.getMediaUrl(): String? {
    return (this as? MessageWithAttachmentContent)?.url
}

/**
 * Get thumbnail URL from message content
 */
fun MessageContent?.getThumbnailUrl(): String? {
    return (this as? org.matrix.android.sdk.api.session.room.model.message.MessageImageContent)?.info?.thumbnailUrl
}

/**
 * Safe string extraction from Any? type
 */
fun Any?.safeToString(default: String = ""): String {
    return this?.toString() ?: default
}

/**
 * Log Matrix event details for debugging
 */
fun logMatrixEvent(tag: String, eventType: String?, content: Any?) {
    if (BuildConfig.DEBUG) {
        Timber.tag(tag).d("Event Type: $eventType")
        Timber.tag(tag).d("Content: $content")
    }
}
