package com.matrixmessenger.data.matrix.mapper

import org.matrix.android.sdk.api.session.room.model.RoomSummary
import org.matrix.android.sdk.api.session.room.model.Membership
import org.matrix.android.sdk.api.session.room.model.tag.RoomTag
import org.matrix.android.sdk.api.session.room.notification.RoomNotificationState
import com.matrixmessenger.domain.model.MatrixRoom
import com.matrixmessenger.domain.model.MembershipState
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomMapper @Inject constructor() {

    fun map(
        summary: RoomSummary, 
        isEncrypted: Boolean,
        notificationState: RoomNotificationState? = null
    ): MatrixRoom {
        return MatrixRoom(
            roomId = summary.roomId,
            name = summary.displayName,
            topic = summary.topic,
            avatarUrl = summary.avatarUrl,
            alias = summary.canonicalAlias,
            isDirect = summary.isDirect,
            isEncrypted = isEncrypted,
            isPinned = summary.hasTag(RoomTag.ROOM_TAG_FAVOURITE),
            isMuted = notificationState == RoomNotificationState.MUTE,
            membership = summary.membership.toDomain(),
            unreadCount = summary.notificationCount,
            lastMessage = null, // Set by repository
            timestamp = summary.latestPreviewableEvent?.root?.originServerTs?.let { Date(it) },
            memberCount = summary.joinedMembersCount ?: 0,
            inviter = null,
            canonicalAlias = summary.canonicalAlias
        )
    }

    private fun Membership.toDomain(): MembershipState = when (this) {
        Membership.INVITE -> MembershipState.INVITE
        Membership.JOIN -> MembershipState.JOIN
        Membership.LEAVE -> MembershipState.LEAVE
        Membership.BAN -> MembershipState.BAN
        Membership.KNOCK -> MembershipState.KNOCK
        else -> MembershipState.LEAVE
    }
}
