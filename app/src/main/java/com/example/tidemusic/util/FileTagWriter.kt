package com.example.tidemusic.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.tidemusic.domain.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * Updates metadata for an audio file both in Android MediaStore and directly on the physical storage file.
 */
object FileTagWriter {
    private const val TAG = "FileTagWriter"

    suspend fun writeTags(
        context: Context,
        song: Song,
        newTitle: String,
        newArtist: String,
        newAlbum: String
    ): Boolean = withContext(Dispatchers.IO) {
        var success = false

        // 1. Update MediaStore through ContentResolver
        try {
            val contentUri = if (song.id > 0) {
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
            } else if (song.uri.isNotBlank()) {
                Uri.parse(song.uri)
            } else null

            if (contentUri != null) {
                val cv = ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, newTitle)
                    put(MediaStore.Audio.Media.ARTIST, newArtist)
                    put(MediaStore.Audio.Media.ALBUM, newAlbum)
                }
                val rows = context.contentResolver.update(contentUri, cv, null, null)
                if (rows > 0) {
                    success = true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update MediaStore for song ${song.id}", e)
        }

        // 2. If physical file exists, update ID3 tags directly
        val file = if (song.filePath.isNotBlank()) File(song.filePath) else null
        if (file != null && file.exists() && file.canWrite()) {
            val ext = file.extension.lowercase()
            if (ext == "mp3") {
                try {
                    writeMp3Id3v1(file, newTitle, newArtist, newAlbum)
                    success = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to write ID3 tag to ${file.absolutePath}", e)
                }
            }

            // Request MediaScanner to refresh the system index for this file
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf(song.mimeType.ifBlank { "audio/*" }),
                    null
                )
            } catch (_: Exception) {}
        }

        success
    }

    /**
     * Writes standard ID3v1.1 tag (128 bytes at the end of the MP3 file).
     */
    private fun writeMp3Id3v1(file: File, title: String, artist: String, album: String) {
        RandomAccessFile(file, "rw").use { raf ->
            val len = raf.length()
            if (len < 128) return

            raf.seek(len - 128)
            val header = ByteArray(3)
            raf.readFully(header)
            val hasExistingTag = (header[0] == 'T'.code.toByte() && header[1] == 'A'.code.toByte() && header[2] == 'G'.code.toByte())

            if (hasExistingTag) {
                raf.seek(len - 128)
            } else {
                raf.seek(len) // Append new tag
            }

            val tagBytes = ByteArray(128)
            // "TAG" header
            tagBytes[0] = 'T'.code.toByte()
            tagBytes[1] = 'A'.code.toByte()
            tagBytes[2] = 'G'.code.toByte()

            // Title (30 bytes, null padded)
            putNullPaddedString(tagBytes, 3, 30, title)
            // Artist (30 bytes, null padded)
            putNullPaddedString(tagBytes, 33, 30, artist)
            // Album (30 bytes, null padded)
            putNullPaddedString(tagBytes, 63, 30, album)

            // Track number slot at byte 126
            tagBytes[125] = 0
            tagBytes[126] = 1.toByte()
            // Genre (255 = unknown)
            tagBytes[127] = 255.toByte()

            raf.write(tagBytes)
        }
    }

    private fun putNullPaddedString(dest: ByteArray, offset: Int, maxLen: Int, text: String) {
        val src = text.toByteArray(Charsets.ISO_8859_1)
        val copyLen = minOf(src.size, maxLen)
        System.arraycopy(src, 0, dest, offset, copyLen)
        for (i in copyLen until maxLen) {
            dest[offset + i] = 0
        }
    }
}
