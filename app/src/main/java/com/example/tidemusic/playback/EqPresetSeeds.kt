package com.example.tidemusic.playback

import com.example.tidemusic.data.db.EqPresetEntity
import com.example.tidemusic.data.db.StableIds

/**
 * Built-in EQ presets seeded into Room at first run (spec Section 8.4 preset list).
 *
 * Each row is a [EqPresetEntity] with 10 CSV-packed band gains (ISO band centers:
 * 31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz) and a pre-amp stage. 'custom' presets
 * saved by the user are appended with `isBuiltIn = false`.
 */
object EqPresetSeeds {

    private fun preset(name: String, gains: FloatArray, preamp: Float): EqPresetEntity =
        EqPresetEntity(
            id = StableIds.artistId("eq-preset:$name"), // stable id derived from name
            name = name,
            isBuiltIn = true,
            bandGains = gains.joinToString(","),
            preampGain = preamp,
        )

    val builtIns: List<EqPresetEntity> = listOf(
        preset("Flat", FloatArray(10) { 0f }, 0f),
        // Bass boost: raise the lowest three bands.
        preset("Bass Boost", floatArrayOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f), -2f),
        // Treble boost: raise the highest three bands.
        preset("Treble Boost", floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 2f, 4f, 5f, 6f), -2f),
        // Vocal boost: emphasize midrange.
        preset("Vocal Boost", floatArrayOf(-1f, 0f, 2f, 4f, 4f, 3f, 2f, 0f, -1f, -1f), -1f),
        preset("Rock", floatArrayOf(4f, 3f, 1f, 0f, -1f, 0f, 1f, 3f, 4f, 4f), -1f),
        preset("Pop", floatArrayOf(-1f, 0f, 2f, 3f, 3f, 2f, 0f, -1f, -1f, -1f), 0f),
        preset("Jazz", floatArrayOf(2f, 3f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f), 0f),
        preset("Classical", floatArrayOf(3f, 2f, 0f, 0f, -1f, -1f, 0f, 2f, 3f, 4f), 0f),
        preset("Electronic", floatArrayOf(4f, 4f, 1f, 0f, -2f, 1f, 1f, 3f, 4f, 5f), -1f),
        preset("Acoustic", floatArrayOf(3f, 2f, 1f, 2f, 2f, 1f, 0f, 1f, 2f, 3f), 0f),
    )
}
