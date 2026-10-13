package com.example.tidemusic.voice

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-device neural wake word engine executing microWakeWord's "Hey Jarvis" TFLite model.
 *
 * Replicates the standard microWakeWord audio frontend:
 * - 16 kHz 16-bit PCM input
 * - 40 log-mel energy filterbanks computed over 30 ms windows with 10 ms hop size
 * - TFLite quantized INT8 streaming inference
 * - Multi-frame sliding window decision smoothing
 */
class MicroWakeWordEngine(
    private val context: Context,
    private val probabilityCutoff: Float = 0.85f,
    private val slidingWindowSize: Int = 5,
    private val onWakeWordDetected: (String) -> Unit
) : Closeable {

    private var interpreter: Interpreter? = null
    private val slidingProbabilities = ArrayDeque<Float>()
    private var isModelLoaded = false

    // Audio frontend parameters matching microWakeWord
    private val sampleRate = 16000
    private val frameSize = 480 // 30 ms
    private val hopSize = 160   // 10 ms
    private val numMelFilters = 40
    private val minFreq = 125.0
    private val maxFreq = 7500.0

    // FFT & Filterbank buffers
    private val fftSize = 512
    private val melFilterBank = Array(numMelFilters) { FloatArray(fftSize / 2 + 1) }
    private val hammingWindow = FloatArray(frameSize)

    init {
        initializeAudioFrontend()
        loadModel()
    }

    private fun initializeAudioFrontend() {
        // Precompute Hamming window
        for (i in 0 until frameSize) {
            hammingWindow[i] = (0.54 - 0.46 * cos(2.0 * Math.PI * i / (frameSize - 1))).toFloat()
        }

        // Precompute Mel Filterbank
        val minMel = hzToMel(minFreq)
        val maxMel = hzToMel(maxFreq)
        val melStep = (maxMel - minMel) / (numMelFilters + 1)

        val binFrequencies = DoubleArray(numMelFilters + 2) { i ->
            melToHz(minMel + i * melStep)
        }

        val fftBinHz = sampleRate.toDouble() / fftSize
        for (m in 0 until numMelFilters) {
            val leftHz = binFrequencies[m]
            val centerHz = binFrequencies[m + 1]
            val rightHz = binFrequencies[m + 2]

            for (k in 0..fftSize / 2) {
                val freq = k * fftBinHz
                if (freq >= leftHz && freq <= centerHz && centerHz > leftHz) {
                    melFilterBank[m][k] = ((freq - leftHz) / (centerHz - leftHz)).toFloat()
                } else if (freq > centerHz && freq <= rightHz && rightHz > centerHz) {
                    melFilterBank[m][k] = ((rightHz - freq) / (rightHz - centerHz)).toFloat()
                } else {
                    melFilterBank[m][k] = 0f
                }
            }
        }
    }

    private fun hzToMel(hz: Double): Double = 2595.0 * log10(1.0 + hz / 700.0)
    private fun melToHz(mel: Double): Double = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)

    private fun loadModel() {
        try {
            val modelBuffer = loadModelFile("models/hey_jarvis.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(modelBuffer, options)
            isModelLoaded = true
            Log.i(TAG, "microWakeWord (Hey Jarvis) TFLite model loaded successfully")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load hey_jarvis.tflite from assets: ${e.message}", e)
            isModelLoaded = false
        }
    }

    private fun loadModelFile(assetPath: String): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = context.assets.openFd(assetPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Feeds incoming PCM audio samples (16 kHz 16-bit mono).
     * Extracts log-mel spectrogram features and executes TFLite inference.
     */
    @Synchronized
    fun processAudio(samples: ShortArray, length: Int) {
        if (!isModelLoaded || interpreter == null || length < frameSize) return

        // Process audio in 30ms frames with 10ms hops
        var offset = 0
        while (offset + frameSize <= length) {
            val melEnergies = computeLogMel(samples, offset)
            val prob = runInference(melEnergies)

            slidingProbabilities.addLast(prob)
            if (slidingProbabilities.size > slidingWindowSize) {
                slidingProbabilities.removeFirst()
            }

            val avgProb = slidingProbabilities.average().toFloat()
            if (avgProb >= probabilityCutoff) {
                Log.i(TAG, "Wake word detected! Avg probability: $avgProb >= $probabilityCutoff")
                slidingProbabilities.clear()
                onWakeWordDetected("Hey Jarvis")
                break
            }

            offset += hopSize
        }
    }

    private fun computeLogMel(samples: ShortArray, offset: Int): FloatArray {
        // Windowed real and imaginary components for FFT
        val real = DoubleArray(fftSize)
        val imag = DoubleArray(fftSize)

        for (i in 0 until frameSize) {
            real[i] = (samples[offset + i] / 32768.0) * hammingWindow[i]
        }

        // In-place Radix-2 FFT
        fft(real, imag)

        // Power spectrum
        val power = FloatArray(fftSize / 2 + 1)
        for (i in 0..fftSize / 2) {
            power[i] = (real[i] * real[i] + imag[i] * imag[i]).toFloat()
        }

        // Apply Mel Filterbank & Log
        val logMel = FloatArray(numMelFilters)
        for (m in 0 until numMelFilters) {
            var energy = 0f
            for (k in 0..fftSize / 2) {
                energy += power[k] * melFilterBank[m][k]
            }
            logMel[m] = ln(max(energy, 1e-6f))
        }

        return logMel
    }

    private fun runInference(melEnergies: FloatArray): Float {
        val interp = interpreter ?: return 0f
        try {
            // microWakeWord input shape is typically (1, 1, 40) or (1, 40)
            val inputTensor = interp.getInputTensor(0)
            val isInt8 = inputTensor.dataType() == org.tensorflow.lite.DataType.INT8
            val isFloat32 = inputTensor.dataType() == org.tensorflow.lite.DataType.FLOAT32

            val inputBuffer = ByteBuffer.allocateDirect(
                if (isInt8) numMelFilters else numMelFilters * 4
            ).order(ByteOrder.nativeOrder())

            if (isInt8) {
                val quantParams = inputTensor.quantizationParams()
                val scale = if (quantParams != null && quantParams.scale > 0f) quantParams.scale else 0.102f
                val zeroPoint = quantParams?.zeroPoint ?: -128
                for (v in melEnergies) {
                    val quantized = ((v / scale) + zeroPoint).toInt().coerceIn(-128, 127).toByte()
                    inputBuffer.put(quantized)
                }
            } else {
                for (v in melEnergies) {
                    inputBuffer.putFloat(v)
                }
            }
            inputBuffer.rewind()

            val outputBuffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
            interp.run(inputBuffer, outputBuffer)
            outputBuffer.rewind()

            val outputTensor = interp.getOutputTensor(0)
            return if (outputTensor.dataType() == org.tensorflow.lite.DataType.INT8) {
                val quant = outputTensor.quantizationParams()
                val raw = outputBuffer.get().toInt()
                val scale = quant?.scale ?: 0.00390625f
                val zp = quant?.zeroPoint ?: -128
                ((raw - zp) * scale).coerceIn(0f, 1f)
            } else {
                outputBuffer.float.coerceIn(0f, 1f)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Inference error: ${e.message}")
            return 0f
        }
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        var l = 1
        while (l < n) {
            val step = l shl 1
            val angle = -Math.PI / l
            val wStepR = cos(angle)
            val wStepI = sin(angle)
            var wR = 1.0
            var wI = 0.0

            for (m in 0 until l) {
                for (i in m until n step step) {
                    val p = i + l
                    val tr = wR * real[p] - wI * imag[p]
                    val ti = wR * imag[p] + wI * real[p]
                    real[p] = real[i] - tr
                    imag[p] = imag[i] - ti
                    real[i] += tr
                    imag[i] += ti
                }
                val nextWR = wR * wStepR - wI * wStepI
                wI = wR * wStepI + wI * wStepR
                wR = nextWR
            }
            l = step
        }
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        isModelLoaded = false
    }

    companion object {
        private const val TAG = "MicroWakeWordEngine"
    }
}
