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
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * On-device Speech-to-Text recognizer powered by Vosk Kaldi speech model
 * combined with TEN Framework's TenVAD voice activity detection.
 *
 * Runs 100% locally and offline without external cloud dependencies.
 */
class VoskSpeechRecognizer(
    private val context: Context,
    private val tenVad: TenVadDetector,
    private val onReady: () -> Unit = {},
    private val onRmsChanged: (Float) -> Unit = {},
    private val onPartialResult: (String) -> Unit = {},
    private val onFinalResult: (String) -> Unit = {},
    private val onVadStateChanged: (Boolean, Float) -> Unit = { _, _ -> },
    private val onError: (String) -> Unit = {},
    private val onListeningEnded: () -> Unit = {}
) : Closeable {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null

    private var voskModel: Model? = null
    private var recognizer: Recognizer? = null
    private var isModelReady = false

    @Volatile
    var isListening: Boolean = false
        private set

    companion object {
        private const val TAG = "VoskSpeechRecognizer"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val SILENCE_TIMEOUT_MS = 1400L // End-of-turn turn detection
    }

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isModelReady) return@withContext true

        try {
            val targetDir = File(context.filesDir, "models/vosk-model-small-en-us-0.15")
            if (!targetDir.exists() || targetDir.listFiles().isNullOrEmpty()) {
                Log.i(TAG, "Unpacking Vosk model from assets to ${targetDir.absolutePath}...")
                copyAssetFolder(context, "models/vosk-model-small-en-us-0.15", targetDir)
            }

            voskModel = Model(targetDir.absolutePath)
            recognizer = Recognizer(voskModel, SAMPLE_RATE.toFloat())
            isModelReady = true
            Log.i(TAG, "Vosk speech model initialized successfully from ${targetDir.absolutePath}")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize Vosk model: ${e.message}", e)
            isModelReady = false
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (isListening) return

        if (!isModelReady || recognizer == null) {
            scope.launch {
                val ok = initialize()
                if (ok) {
                    startListeningInternal()
                } else {
                    onError("Vosk speech model failed to load.")
                }
            }
            return
        }

        startListeningInternal()
    }

    @SuppressLint("MissingPermission")
    private fun startListeningInternal() {
        val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufSize <= 0) {
            onError("AudioRecord configuration not supported on this device.")
            return
        }

        val bufferSize = minBufSize * 2
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onError("Failed to initialize AudioRecord. Check microphone permissions.")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isListening = true
            recognizer?.reset()
            onReady()

            recordingJob = scope.launch(Dispatchers.IO) {
                processAudioStream(bufferSize)
            }
        } catch (e: SecurityException) {
            isListening = false
            onError("Microphone permission denied. Please grant microphone access.")
        } catch (e: Throwable) {
            isListening = false
            onError("Microphone recording error: ${e.message}")
        }
    }

    private suspend fun processAudioStream(bufferSize: Int) {
        val shortBuffer = ShortArray(bufferSize / 2)
        var speechDetectedInSession = false
        var lastSpeechTime = 0L
        val vadHopSize = tenVad.hopSize
        val vadChunk = ShortArray(vadHopSize)

        try {
            while (isListening && recordingJob?.isActive == true) {
                val readShorts = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: -1
                if (readShorts <= 0) continue

                // 1. Compute Audio RMS for Visualizer
                var sumSquares = 0.0
                for (i in 0 until readShorts) {
                    val s = shortBuffer[i].toDouble()
                    sumSquares += s * s
                }
                val rms = sqrt(sumSquares / readShorts)
                val rmsDb = if (rms > 0) (20 * log10(rms)).toFloat() else 0f
                val normRms = (rmsDb / 65.0f).coerceIn(0f, 1f)
                onRmsChanged(normRms)

                // 2. TenVAD Speech Detection
                var isVoiceInChunk = false
                var highestProb = 0f
                var offset = 0
                while (offset + vadHopSize <= readShorts) {
                    System.arraycopy(shortBuffer, offset, vadChunk, 0, vadHopSize)
                    val (isVoice, prob) = tenVad.processFrame(vadChunk)
                    if (isVoice) isVoiceInChunk = true
                    if (prob > highestProb) highestProb = prob
                    offset += vadHopSize
                }

                val now = System.currentTimeMillis()
                if (isVoiceInChunk) {
                    speechDetectedInSession = true
                    lastSpeechTime = now
                    onVadStateChanged(true, highestProb)
                } else {
                    onVadStateChanged(false, highestProb)
                }

                // 3. Feed audio to Vosk Recognizer
                val rec = recognizer
                if (rec != null) {
                    val accepted = rec.acceptWaveForm(shortBuffer, readShorts)
                    if (accepted) {
                        val resultJson = rec.result
                        val text = parseVoskJson(resultJson, "text")
                        if (text.isNotBlank()) {
                            onPartialResult(text)
                        }
                    } else {
                        val partialJson = rec.partialResult
                        val partial = parseVoskJson(partialJson, "partial")
                        if (partial.isNotBlank()) {
                            onPartialResult(partial)
                        }
                    }
                }

                // 4. TenVAD Endpointing / Silence detection
                if (speechDetectedInSession && (now - lastSpeechTime > SILENCE_TIMEOUT_MS)) {
                    Log.i(TAG, "TenVAD detected end of speech (silence timeout). Finalizing...")
                    finishRecognition()
                    break
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Audio stream loop error: ${e.message}", e)
        }
    }

    fun stopListening() {
        if (!isListening) return
        finishRecognition()
    }

    private fun finishRecognition() {
        isListening = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Throwable) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        }

        val rec = recognizer
        val finalText = if (rec != null) {
            val finalJson = rec.finalResult
            parseVoskJson(finalJson, "text")
        } else ""

        onListeningEnded()
        onFinalResult(finalText.trim())
    }

    private fun parseVoskJson(jsonString: String, key: String): String {
        return try {
            val obj = JSONObject(jsonString)
            obj.optString(key, "")
        } catch (_: Throwable) {
            ""
        }
    }

    private fun copyAssetFolder(context: Context, srcName: String, dstDir: File) {
        if (!dstDir.exists()) dstDir.mkdirs()
        val fileList = context.assets.list(srcName) ?: return
        for (filename in fileList) {
            val srcFile = "$srcName/$filename"
            val dstFile = File(dstDir, filename)
            val subFiles = context.assets.list(srcFile)
            if (!subFiles.isNullOrEmpty()) {
                copyAssetFolder(context, srcFile, dstFile)
            } else {
                context.assets.open(srcFile).use { input: InputStream ->
                    FileOutputStream(dstFile).use { output: FileOutputStream ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    override fun close() {
        stopListening()
        recognizer?.close()
        voskModel?.close()
        recognizer = null
        voskModel = null
        isModelReady = false
    }
}
