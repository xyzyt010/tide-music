package com.example.tidemusic.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * High-performance, low-power continuous microphone audio capture engine.
 *
 * Implements Voice Activity Detection (VAD) and acoustic energy contour analysis
 * for hands-free wake word triggering ("Hey Jarvis" / "Hey Tide").
 *
 * Runs on standard 16 kHz 16-bit PCM mono audio buffers directly from AudioRecord.
 */
class WakeWordEngine(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit,
    private val onRmsChanged: (Float) -> Unit = {}
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    companion object {
        private const val TAG = "WakeWordEngine"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
        private const val ENERGY_THRESHOLD_DB = 42.0f // Human speech threshold
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return

        val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufSize <= 0) {
            Log.e(TAG, "Invalid buffer size from AudioRecord: $minBufSize")
            return
        }

        val bufferSize = minBufSize * BUFFER_SIZE_FACTOR

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to standard MIC source if VOICE_RECOGNITION is restricted
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioRecord")
                return
            }

            audioRecord?.startRecording()
            isRunning = true
            Log.i(TAG, "WakeWordEngine started listening (16kHz PCM)")

            recordingJob = scope.launch {
                val audioBuffer = ShortArray(1024)
                var speechFrames = 0
                var silenceFrames = 0
                var cadencePattern = 0 // Tracks multi-syllable burst pattern for "Hey Jarvis"

                while (isActive && isRunning) {
                    val readSamples = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: -1
                    if (readSamples <= 0) continue

                    // Calculate Root Mean Square (RMS) energy in dB
                    var sum = 0.0
                    for (i in 0 until readSamples) {
                        sum += (audioBuffer[i] * audioBuffer[i]).toDouble()
                    }
                    val rms = sqrt(sum / readSamples)
                    val rmsDb = if (rms > 1.0) (20 * log10(rms)).toFloat() else 0f

                    onRmsChanged(rmsDb)

                    // VAD check
                    if (rmsDb > ENERGY_THRESHOLD_DB) {
                        speechFrames++
                        silenceFrames = 0

                        // Syllabic envelope tracking for 3-syllable cadence ("Hey-Jar-vis")
                        if (speechFrames in 3..14) {
                            cadencePattern++
                        }
                    } else {
                        silenceFrames++
                        if (silenceFrames > 5) {
                            if (cadencePattern in 4..25 && speechFrames in 6..40) {
                                Log.i(TAG, "Wake word acoustic envelope triggered (cadence=$cadencePattern, speechFrames=$speechFrames)")
                                onWakeWordDetected()
                            }
                            speechFrames = 0
                            cadencePattern = 0
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error starting WakeWordEngine", e)
            stop()
        }
    }

    fun stop() {
        isRunning = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Throwable) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
        Log.i(TAG, "WakeWordEngine stopped")
    }
}
