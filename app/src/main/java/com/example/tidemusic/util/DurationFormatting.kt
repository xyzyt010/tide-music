package com.example.tidemusic.util

import kotlin.math.ln
import kotlin.math.pow

/** Human-friendly duration formatting at any scale (spec Section 6.4 header). */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        else -> "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

/** Aggregate duration formatter for the Folder screen header — handles 3:45 up through `12h 30m`. */
fun formatAggregateDuration(ms: Long): String {
    if (ms <= 0) return "0m"
    val totalSeconds = ms / 1000
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${totalSeconds}s"
    }
}

/** Thousand-separated count for song/subfolder counts in the Folder screen header. */
fun formatCount(count: Int): String = "%,d".format(count)

/** Formatting helper for total playlist/section durations in h:mm:ss or hh:mm:ss format. */
fun formatPlaylistDuration(ms: Long): String {
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}
