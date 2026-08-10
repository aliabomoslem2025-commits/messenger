package com.matrixmessenger.data.matrix.mapper

import com.matrixmessenger.domain.model.MatrixUser
import org.matrix.android.sdk.api.session.user.model.User
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserMapper @Inject constructor() {

    fun mapToMatrixUser(user: User, isOnline: Boolean = false, lastSeen: Long? = null): MatrixUser {
        return MatrixUser(
            userId = user.userId,
            displayName = user.displayName,
            avatarUrl = user.avatarUrl,
            isOnline = isOnline,
            lastSeen = lastSeen,
            presenceStatus = null // TODO: Extract from presence info
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
            presenceStatus = null
        )
    }
}
