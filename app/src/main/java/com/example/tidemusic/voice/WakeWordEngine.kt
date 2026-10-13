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
import java.io.Closeable
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Continuous acoustic monitoring engine combining TEN Framework's TenVAD
 * and microWakeWord's "Hey Jarvis" TFLite neural network.
 *
 * Runs locally on standard 16 kHz 16-bit PCM mono audio stream.
 */
class WakeWordEngine(
    private val context: Context,
    private val tenVad: TenVadDetector,
    private val onWakeWordDetected: () -> Unit,
    private val onRmsChanged: (Float) -> Unit = {}
) : Closeable {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var microWakeWordEngine: MicroWakeWordEngine? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    companion object {
        private const val TAG = "WakeWordEngine"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
    }

    init {
        microWakeWordEngine = MicroWakeWordEngine(
            context = context,
            probabilityCutoff = 0.85f,
            slidingWindowSize = 5,
            onWakeWordDetected = {
                Log.i(TAG, "microWakeWord detected: Hey Jarvis!")
                onWakeWordDetected()
            }
        )
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
                Log.e(TAG, "AudioRecord failed to initialize. State: ${audioRecord?.state}")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isRunning = true
            Log.i(TAG, "WakeWordEngine started listening for 'Hey Jarvis' via microWakeWord & TenVAD")

            recordingJob = scope.launch(Dispatchers.IO) {
                val shortBuffer = ShortArray(bufferSize / 2)
                val vadHopSize = tenVad.hopSize
                val vadChunk = ShortArray(vadHopSize)

                while (isRunning && isActive) {
                    val readShorts = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: -1
                    if (readShorts <= 0) continue

                    // 1. Audio RMS for visualizer
                    var sumSquares = 0.0
                    for (i in 0 until readShorts) {
                        val s = shortBuffer[i].toDouble()
                        sumSquares += s * s
                    }
                    val rms = sqrt(sumSquares / readShorts)
                    val rmsDb = if (rms > 0) (20 * log10(rms)).toFloat() else 0f
                    val normalizedRms = (rmsDb / 65.0f).coerceIn(0.0f, 1.0f)
                    onRmsChanged(normalizedRms)

                    // 2. TenVAD Voice Activity Filtering
                    var hasSpeech = false
                    var offset = 0
                    while (offset + vadHopSize <= readShorts) {
                        System.arraycopy(shortBuffer, offset, vadChunk, 0, vadHopSize)
                        val (isVoice, _) = tenVad.processFrame(vadChunk)
                        if (isVoice) {
                            hasSpeech = true
                            break
                        }
                        offset += vadHopSize
                    }

                    // 3. microWakeWord neural inference if speech activity is present
                    if (hasSpeech || normalizedRms > 0.25f) {
                        microWakeWordEngine?.processAudio(shortBuffer, readShorts)
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Microphone permission not granted for WakeWordEngine: ${e.message}")
            isRunning = false
            stop()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start WakeWordEngine: ${e.message}", e)
            isRunning = false
            stop()
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.i(TAG, "WakeWordEngine stopped")
        } catch (e: Throwable) {
            Log.e(TAG, "Error stopping WakeWordEngine: ${e.message}")
        }
    }

    override fun close() {
        stop()
        microWakeWordEngine?.close()
        microWakeWordEngine = null
    }
}
