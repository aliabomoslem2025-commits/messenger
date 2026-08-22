package com.matrixmessenger.data.matrix.mapper

import com.matrixmessenger.domain.model.MatrixUser
import com.matrixmessenger.domain.model.PresenceState as DomainPresenceState
import org.matrix.android.sdk.api.session.user.model.User
import org.matrix.android.sdk.api.session.presence.model.PresenceEnum
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserMapper @Inject constructor() {

    fun mapToMatrixUser(user: User, presence: PresenceEnum? = null, lastActiveAgo: Long? = null): MatrixUser {
        val domainPresence = when (presence) {
            PresenceEnum.ONLINE -> DomainPresenceState.ONLINE
            PresenceEnum.UNAVAILABLE -> DomainPresenceState.UNAVAILABLE
            PresenceEnum.OFFLINE -> DomainPresenceState.OFFLINE
            else -> DomainPresenceState.UNKNOWN
        }
        
        return MatrixUser(
            userId = user.userId,
            displayName = user.displayName,
            avatarUrl = user.avatarUrl,
            presence = domainPresence,
            lastSeen = lastActiveAgo?.let { Date(System.currentTimeMillis() - it) }
        )
    }

    fun mapToMatrixUser(
        userId: String,
        displayName: String?,
        avatarUrl: String?,
        presence: DomainPresenceState = DomainPresenceState.OFFLINE,
        lastSeen: Date? = null
    ): MatrixUser {
        return MatrixUser(
            userId = userId,
            displayName = displayName,
            avatarUrl = avatarUrl,
            presence = presence,
            lastSeen = lastSeen
        )
    }
}
