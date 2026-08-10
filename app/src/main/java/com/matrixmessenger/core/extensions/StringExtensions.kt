package com.matrixmessenger.core.extensions

import android.util.Patterns
import java.util.regex.Pattern

/**
 * Extension functions for String
 */

/**
 * Validate email format
 */
fun String.isValidEmail(): Boolean {
    return this.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(this).matches()
}

/**
 * Validate password strength (minimum 8 characters)
 */
fun String.isValidPassword(): Boolean {
    return this.length >= 8
}

/**
 * Validate Matrix user ID format (@username:homeserver.domain)
 */
fun String.isValidMatrixUserId(): Boolean {
    val pattern = Pattern.compile("^@[^:]+:[^.]+\\.[^.]+$")
    return pattern.matcher(this).matches()
}

/**
 * Validate Matrix room ID format (!roomid:homeserver.domain)
 */
fun String.isValidMatrixRoomId(): Boolean {
    val pattern = Pattern.compile("^[!+#][^:]+:[^.]+\\.[^.]+$")
    return pattern.matcher(this).matches()
}

/**
 * Extract username from Matrix user ID
 */
fun String.extractMatrixUsername(): String? {
    return if (isValidMatrixUserId()) {
        this.substring(1).substringBefore(":")
    } else {
        null
    }
}

/**
 * Extract homeserver from Matrix user ID
 */
fun String.extractMatrixHomeserver(): String? {
    return if (isValidMatrixUserId()) {
        this.substringAfter(":")
    } else {
        null
    }
}

/**
 * Truncate string to max length with ellipsis
 */
fun String.truncate(maxLength: Int): String {
    return if (this.length > maxLength) {
        "${this.take(maxLength)}..."
    } else {
        this
    }
}

/**
 * Remove HTML tags from string
 */
fun String.removeHtmlTags(): String {
    return this.replace(Regex("<[^>]*>"), "")
}

/**
 * Format file size to human readable string
 */
fun Long.formatFileSize(): String {
    return when {
        this < 1024 -> "$this B"
        this < 1024 * 1024 -> "${this / 1024} KB"
        this < 1024 * 1024 * 1024 -> "${this / (1024 * 1024)} MB"
        else -> "${this / (1024 * 1024 * 1024)} GB"
    }
}
