package com.matrixmessenger.data.matrix.mapper

import com.matrixmessenger.domain.model.MatrixUser
import com.matrixmessenger.domain.model.PresenceStatus
import org.matrix.android.sdk.api.session.user.model.User
import org.matrix.android.sdk.api.session.presence.model.PresenceState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserMapper @Inject constructor() {

    fun mapToMatrixUser(user: User, isOnline: Boolean = false, lastSeen: Long? = null): MatrixUser {
        val presenceStatus = when (user.currentPresence) {
            PresenceState.ONLINE -> PresenceStatus.ONLINE
            PresenceState.UNAVAILABLE -> PresenceStatus.AWAY
            PresenceState.OFFLINE -> PresenceStatus.OFFLINE
            else -> PresenceStatus.OFFLINE
        }
        
        return MatrixUser(
            userId = user.userId,
            displayName = user.displayName,
            avatarUrl = user.avatarUrl,
            isOnline = user.currentPresence == PresenceState.ONLINE,
            lastSeen = lastSeen ?: user.lastActiveAgo,
            presenceStatus = presenceStatus
        )
    }

    fun mapToMatrixUser(
        userId: String,
        displayName: String?,
        avatarUrl: String?,
        isOnline: Boolean = false,
        lastSeen: Long? = null
    ): MatrixUser {
        return MatrixUser(
            userId = userId,
            displayName = displayName,
            avatarUrl = avatarUrl,
            isOnline = isOnline,
            lastSeen = lastSeen,
            presenceStatus = if (isOnline) PresenceStatus.ONLINE else PresenceStatus.OFFLINE
        )
    }
}
