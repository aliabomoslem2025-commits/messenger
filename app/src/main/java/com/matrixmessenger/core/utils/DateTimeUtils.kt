package com.matrixmessenger.core.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Utility functions for date and time formatting
 */

object DateTimeUtils {

    private const val TIME_FORMAT = "HH:mm"
    private const val DATE_FORMAT = "MMM dd, yyyy"
    private const val DATETIME_FORMAT = "MMM dd, yyyy HH:mm"
    private const val ISO_8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

    /**
     * Format timestamp to time string (HH:mm)
     */
    fun formatTime(timestamp: Long): String {
        return SimpleDateFormat(TIME_FORMAT, Locale.getDefault()).format(Date(timestamp))
    }

    /**
     * Format timestamp to date string (MMM dd, yyyy)
     */
    fun formatDate(timestamp: Long): String {
        return SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(Date(timestamp))
    }

    /**
     * Format timestamp to datetime string
     */
    fun formatDateTime(timestamp: Long): String {
        return SimpleDateFormat(DATETIME_FORMAT, Locale.getDefault()).format(Date(timestamp))
    }

    /**
     * Format timestamp to relative time (e.g., "5 min ago", "2 hours ago")
     */
    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            diff < TimeUnit.HOURS.toMillis(1) -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
                "$minutes ${if (minutes == 1L) "min" else "mins"} ago"
            }
            diff < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                "$hours ${if (hours == 1L) "hour" else "hours"} ago"
            }
            diff < TimeUnit.DAYS.toMillis(7) -> {
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                "$days ${if (days == 1L) "day" else "days"} ago"
            }
            else -> formatDate(timestamp)
        }
    }

    /**
     * Format timestamp for message grouping (Today, Yesterday, or date)
     */
    fun formatMessageDate(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val today = getStartOfDay(now)
        val yesterday = today - TimeUnit.DAYS.toMillis(1)
        val messageDay = getStartOfDay(timestamp)

        return when {
            messageDay >= today -> "Today"
            messageDay >= yesterday -> "Yesterday"
            else -> formatDate(timestamp)
        }
    }

    /**
     * Get start of day in milliseconds
     */
    private fun getStartOfDay(timestamp: Long): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.time = Date(timestamp)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * Check if timestamp is from today
     */
    fun isToday(timestamp: Long): Boolean {
        val now = System.currentTimeMillis()
        val today = getStartOfDay(now)
        val messageDay = getStartOfDay(timestamp)
        return messageDay >= today
    }

    /**
     * Check if timestamp is from yesterday
     */
    fun isYesterday(timestamp: Long): Boolean {
        val now = System.currentTimeMillis()
        val today = getStartOfDay(now)
        val yesterday = today - TimeUnit.DAYS.toMillis(1)
        val messageDay = getStartOfDay(timestamp)
        return messageDay >= yesterday && messageDay < today
    }

    /**
     * Parse ISO 8601 timestamp to milliseconds
     */
    fun parseIso8601(isoString: String?): Long {
        return try {
            if (isoString.isNullOrBlank()) {
                System.currentTimeMillis()
            } else {
                val format = SimpleDateFormat(ISO_8601_FORMAT, Locale.US)
                format.parse(isoString)?.time ?: System.currentTimeMillis()
            }
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * Format duration in seconds to MM:SS or HH:MM:SS
     */
    fun formatDuration(seconds: Long): String {
        return when {
            seconds < 3600 -> {
                val mins = TimeUnit.SECONDS.toMinutes(seconds)
                val secs = seconds % 60
                String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
            }
            else -> {
                val hours = TimeUnit.SECONDS.toHours(seconds)
                val mins = TimeUnit.SECONDS.toMinutes(seconds) % 60
                val secs = seconds % 60
                String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, mins, secs)
            }
        }
    }
}
