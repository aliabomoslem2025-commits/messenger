package com.matrixmessenger.feature.chatlist.presentation

import androidx.compose.runtime.Immutable

@Immutable
data class ChatListItemUiModel(
    val roomId: String,
    val name: String,
    val avatarUrl: String?,
    val initials: String,
    val lastMessage: String?,
    val timestamp: String,
    val unreadCount: Int,
    val isOnline: Boolean = false,
    val isMuted: Boolean = false,
    val isPinned: Boolean = false,
    val isVerified: Boolean = false,
    val senderName: String? = null,
    val draftText: String? = null
)
