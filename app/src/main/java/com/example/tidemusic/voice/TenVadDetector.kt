package com.example.tidemusic.voice

import android.util.Log
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.io.Closeable
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Native Voice Activity Detection (VAD) using TEN Framework's TenVAD engine.
 *
 * Calls libten_vad.so via JNA for real-time speech frame classification
 * with high precision and low latency.
 */
class TenVadDetector(
    val hopSize: Int = 256, // 256 samples (16 ms @ 16 kHz)
    val threshold: Float = 0.5f
) : Closeable {

    interface TenVadLib : Library {
        fun ten_vad_create(handle: PointerByReference, hopSize: NativeLong, threshold: Float): Int
        fun ten_vad_process(
            handle: Pointer,
            audioData: ShortArray,
            audioDataLength: NativeLong,
            outProb: FloatArray,
            outFlag: IntArray
        ): Int
        fun ten_vad_destroy(handle: PointerByReference): Int
        fun ten_vad_get_version(): String
    }

    private var lib: TenVadLib? = null
    private var handleRef: PointerByReference = PointerByReference()
    private var isNativeLoaded = false
    private val probArray = FloatArray(1)
    private val flagArray = IntArray(1)

    init {
        try {
            lib = Native.load("ten_vad", TenVadLib::class.java)
            val res = lib?.ten_vad_create(handleRef, NativeLong(hopSize.toLong()), threshold) ?: -1
            if (res == 0 && handleRef.value != null) {
                isNativeLoaded = true
                val ver = try { lib?.ten_vad_get_version() } catch (_: Throwable) { "unknown" }
                Log.i(TAG, "TenVAD native library successfully initialized (v$ver, hopSize=$hopSize, threshold=$threshold)")
            } else {
                Log.w(TAG, "ten_vad_create returned $res, falling back to heuristic VAD")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not load libten_vad.so (${e.message}), using fallback VAD", e)
            isNativeLoaded = false
        }
    }

    /**
     * Process an audio frame of size [hopSize] (int16 PCM @ 16 kHz).
     * @return Pair of (isSpeechDetected, probability [0.0..1.0])
     */
    @Synchronized
    fun processFrame(frame: ShortArray): Pair<Boolean, Float> {
        if (frame.isEmpty()) return Pair(false, 0f)

        if (isNativeLoaded && lib != null && handleRef.value != null) {
            try {
                val ret = lib!!.ten_vad_process(
                    handleRef.value,
                    frame,
                    NativeLong(frame.size.toLong()),
                    probArray,
                    flagArray
                )
                if (ret == 0) {
                    val isSpeech = flagArray[0] == 1
                    val prob = probArray[0].coerceIn(0.0f, 1.0f)
                    return Pair(isSpeech, prob)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error in ten_vad_process: ${e.message}", e)
            }
        }

        // Robust acoustic energy contour fallback VAD
        var sumSquares = 0.0
        var zeroCrossings = 0
        for (i in frame.indices) {
            val sample = frame[i].toDouble()
            sumSquares += sample * sample
            if (i > 0 && ((frame[i] >= 0 && frame[i - 1] < 0) || (frame[i] < 0 && frame[i - 1] >= 0))) {
                zeroCrossings++
            }
        }
        val rms = sqrt(sumSquares / frame.size)
        val rmsDb = if (rms > 0) (20 * log10(rms)).toFloat() else 0f
        val zcr = zeroCrossings.toFloat() / frame.size

        // Human speech typically resides between 38 dB and 85 dB with ZCR < 0.35
        val isSpeechFallback = rmsDb > 40.0f && zcr in 0.02f..0.40f
        val probFallback = ((rmsDb - 40.0f) / 30.0f).coerceIn(0.0f, 1.0f)

        return Pair(isSpeechFallback, probFallback)
    }

    override fun close() {
        if (isNativeLoaded && lib != null && handleRef.value != null) {
            try {
                lib!!.ten_vad_destroy(handleRef)
                Log.i(TAG, "TenVAD handle released")
            } catch (e: Throwable) {
                Log.e(TAG, "Error releasing TenVAD handle: ${e.message}", e)
            } finally {
                isNativeLoaded = false
            }
        }
    }

    companion object {
        private const val TAG = "TenVadDetector"
    }
}
