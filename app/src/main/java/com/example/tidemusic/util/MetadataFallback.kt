package com.example.tidemusic.util

/**
 * Centralized "Unknown" fallback rule (spec Section 3 / 8.2).
 *
 * Any time `title`, `artist`, or `album` is null/blank after tag extraction, the literal
 * string "Unknown" should be displayed — never the raw filename, never blank. Never let a
 * missing tag break grouping: unknown-artist tracks group together under a single
 * "Unknown Artist" bucket.
 *
 * Every display path in the app routes metadata through this one utility; there is no
 * per-screen duplication.
 */
fun String?.orUnknown(): String =
    if (this.isNullOrBlank()) "Unknown" else this.trim()
