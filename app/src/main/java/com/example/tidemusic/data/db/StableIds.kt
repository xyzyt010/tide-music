package com.example.tidemusic.data.db

import com.example.tidemusic.util.orUnknown
import java.security.MessageDigest

/**
 * Deterministic id derivation for entities whose natural key is a path or name string.
 *
 * Used so rescans diff cleanly against `dateModified` without churn in cross-reference
 * tables (PlaylistSongCrossRef, QueueItem, Bookmark) — the same file always hashes to the
 * same song id across library rebuilds.
 */
object StableIds {

    fun pathId(absolutePath: String): Long = sha1Long(absolutePath.lowercase())

    fun albumId(albumName: String?, artistName: String?): Long =
        sha1Long((albumName.orUnknown() + "|" + artistName.orUnknown()).lowercase())

    fun artistId(name: String?): Long = sha1Long(name.orUnknown().lowercase())

    fun folderId(path: String): Long = sha1Long(path.trimEnd('/').lowercase())

    /** ISO band centers for the 10-band parametric EQ (spec Section 8.4 Tier 2). */
    val eqBandCentersHz: FloatArray = floatArrayOf(
        31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16_000f,
    )

    private fun sha1Long(input: String): Long {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        // Take the first 8 bytes and pack into a positive 63-bit long.
        var v = 0L
        for (i in 0 until 8) {
            v = (v shl 8) or (bytes[i].toLong() and 0xFF)
        }
        return v and 0x7FFF_FFFF_FFFF_FFFFL
    }
}
