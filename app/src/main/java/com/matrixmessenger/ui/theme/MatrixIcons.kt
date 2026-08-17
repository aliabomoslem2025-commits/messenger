package com.matrixmessenger.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Matrix Messenger Icon System
 * Centralized icon definitions for consistent iconography
 * 
 * Reference: Telegram messenger - clean line icons
 */
object MatrixIcons {
    
    // ========== Navigation Icons ==========\n    val Back: ImageVector = Icons.Filled.ArrowBack
    val Forward: ImageVector = Icons.Filled.ArrowForward
    val Close: ImageVector = Icons.Filled.Close
    val MoreVert: ImageVector = Icons.Filled.MoreVert
    val MoreHoriz: ImageVector = Icons.Filled.MoreHoriz
    
    // ========== Communication Icons ==========\n    val Send: ImageVector = Icons.Filled.Send
    val Microphone: ImageVector = Icons.Filled.Mic
    val Attach: ImageVector = Icons.Filled.AttachFile
    val Camera: ImageVector = Icons.Filled.CameraAlt
    val PhotoLibrary: ImageVector = Icons.Filled.PhotoLibrary
    val Document: ImageVector = Icons.Filled.Description
    val Location: ImageVector = Icons.Filled.LocationOn
    val Contact: ImageVector = Icons.Filled.Person
    
    // ========== Call Icons ==========\n    val Call: ImageVector = Icons.Filled.Call
    val VideoCall: ImageVector = Icons.Filled.Videocam
    val CallEnd: ImageVector = Icons.Filled.CallEnd
    
    // ========== Search & Filter Icons ==========\n    val Search: ImageVector = Icons.Filled.Search
    val Filter: ImageVector = Icons.Filled.FilterList
    val Sort: ImageVector = Icons.Filled.Sort
    
    // ========== Message Icons ==========\n    val Reply: ImageVector = Icons.Filled.Reply
    val ForwardMessage: ImageVector = Icons.Filled.Forward
    val Edit: ImageVector = Icons.Filled.Edit
    val Delete: ImageVector = Icons.Filled.Delete
    val Copy: ImageVector = Icons.Filled.ContentCopy
    val Select: ImageVector = Icons.Filled.DoneAll
    val Pin: ImageVector = Icons.Filled.PushPin
    val Unpin: ImageVector = Icons.Outlined.PushPin
    
    // ========== Reaction Icons ==========\n    val EmojiEmotions: ImageVector = Icons.Filled.EmojiEmotions
    val ThumbUp: ImageVector = Icons.Filled.ThumbUp
    val ThumbDown: ImageVector = Icons.Filled.ThumbDown
    val Favorite: ImageVector = Icons.Filled.Favorite
    val Star: ImageVector = Icons.Filled.Star
    
    // ========== Status Icons ==========\n    val Check: ImageVector = Icons.Filled.Check
    val CheckCircle: ImageVector = Icons.Filled.CheckCircle
    val Error: ImageVector = Icons.Filled.Error
    val Warning: ImageVector = Icons.Filled.Warning
    val Info: ImageVector = Icons.Filled.Info
    
    // ========== Read Receipt Icons ==========\n    val Done: ImageVector = Icons.Filled.Done          // Single check - sent
    val DoneAll: ImageVector = Icons.Filled.DoneAll    // Double check - delivered/read
    
    // ========== User & Profile Icons ==========\n    val Person: ImageVector = Icons.Filled.Person
    val PersonAdd: ImageVector = Icons.Filled.PersonAdd
    val Group: ImageVector = Icons.Filled.Group
    val GroupAdd: ImageVector = Icons.Filled.GroupAdd
    
    // ========== Settings Icons ==========\n    val Settings: ImageVector = Icons.Filled.Settings
    val Notifications: ImageVector = Icons.Filled.Notifications
    val Privacy: ImageVector = Icons.Filled.Lock
    val Security: ImageVector = Icons.Filled.Security
    val Storage: ImageVector = Icons.Filled.Storage
    val Palette: ImageVector = Icons.Filled.Palette
    val Language: ImageVector = Icons.Filled.Language
    val Help: ImageVector = Icons.Filled.Help
    val Logout: ImageVector = Icons.Filled.Logout
    
    // ========== Media Icons ==========\n    val Image: ImageVector = Icons.Filled.Image
    val Video: ImageVector = Icons.Filled.Videocam
    val Audio: ImageVector = Icons.Filled.Audiotrack
    val File: ImageVector = Icons.Filled.InsertDriveFile
    val Folder: ImageVector = Icons.Filled.Folder
    val Download: ImageVector = Icons.Filled.Download
    val Upload: ImageVector = Icons.Filled.Upload
    val Share: ImageVector = Icons.Filled.Share
    
    // ========== Chat List Icons ==========\n    val Chat: ImageVector = Icons.Filled.Chat
    val ChatBubble: ImageVector = Icons.Filled.ChatBubble
    val NewChat: ImageVector = Icons.Filled.AddComment
    val Archive: ImageVector = Icons.Filled.Unarchive
    val Mute: ImageVector = Icons.Filled.VolumeOff
    val Unmute: ImageVector = Icons.Filled.VolumeUp
    
    // ========== Typing & Status Icons ==========\n    val Hourglass: ImageVector = Icons.Filled.HourglassEmpty
    val Refresh: ImageVector = Icons.Filled.Refresh
    val Sync: ImageVector = Icons.Filled.Sync
    
    // ========== Sticker & GIF Icons ==========\n    val Sticker: ImageVector = Icons.Filled.StarBorder  // Placeholder
    val Gif: ImageVector = Icons.Filled.Gif
    
    // ========== Voice Icons ==========\n    val VolumeUp: ImageVector = Icons.Filled.VolumeUp
    val VolumeDown: ImageVector = Icons.Filled.VolumeDown
    val PlayArrow: ImageVector = Icons.Filled.PlayArrow
    val Pause: ImageVector = Icons.Filled.Pause
    val Stop: ImageVector = Icons.Filled.Stop
    
    // ========== Expanded/Collapsed Icons ==========\n    val ExpandMore: ImageVector = Icons.Filled.ExpandMore
    val ExpandLess: ImageVector = Icons.Filled.ExpandLess
    val KeyboardArrowUp: ImageVector = Icons.Filled.KeyboardArrowUp
    val KeyboardArrowDown: ImageVector = Icons.Filled.KeyboardArrowDown
    
    // ========== Menu Icons ==========\n    val Menu: ImageVector = Icons.Filled.Menu
    val ViewList: ImageVector = Icons.Filled.ViewList
    val GridView: ImageVector = Icons.Filled.GridView
    
    // ========== Verified Badge ==========\n    val Verified: ImageVector = Icons.Filled.Verified
    
    /**
     * Get icon based on message type for attachment picker
     */
    fun getIconForMimeType(mimeType: String): ImageVector {
        return when {
            mimeType.startsWith("image/") -> Image
            mimeType.startsWith("video/") -> Video
            mimeType.startsWith("audio/") -> Audio
            mimeType.contains("pdf") -> Document
            else -> File
        }
    }
    
    /**
     * Get presence icon based on status
     */
    fun getPresenceIcon(isOnline: Boolean): ImageVector {
        return if (isOnline) CheckCircle else Info
    }
}
