package com.example.tidemusic.playback

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.media3.exoplayer.source.ShuffleOrder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * High-entropy shuffle engine that harvests dynamic hardware state (battery voltage,
 * temperature, level, high-resolution monotonic clocks, process CPU time, and free heap memory)
 * mixed with cryptographic SecureRandom via SHA-256 to seed a non-repeating,
 * un-biased Fisher-Yates shuffle order.
 */
object HardwareEntropyShuffle {
    private const val TAG = "HardwareEntropyShuffle"
    private val secureRandom = SecureRandom()

    /**
     * Samples real-time hardware variables and hashes them with SHA-256 to produce
     * a 64-bit entropy seed physically coupled to the device's exact thermal, voltage,
     * clock, and memory state at that microsecond.
     */
    fun sampleHardwareSeed(context: Context): Long {
        return try {
            val bb = ByteBuffer.allocate(96)

            // 1. Dynamic battery & power subsystem metrics
            try {
                val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0 // mV (fluctuates continuously)
                val temp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0 // 0.1 deg C thermal sensor
                val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0 // %
                val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ?: 0
                val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
                bb.putInt(voltage)
                bb.putInt(temp)
                bb.putInt(level)
                bb.putInt(status)
                bb.putInt(plugged)
            } catch (_: Throwable) {
                bb.putLong(0x4857454EL)
            }

            // 2. High-precision hardware & kernel monotonic timers
            bb.putLong(System.nanoTime())
            bb.putLong(SystemClock.elapsedRealtimeNanos())
            bb.putLong(SystemClock.uptimeMillis())
            bb.putLong(Process.getElapsedCpuTime())

            // 3. Dynamic runtime JVM memory fluctuations
            val runtime = Runtime.getRuntime()
            bb.putLong(runtime.freeMemory())
            bb.putLong(runtime.totalMemory())

            // 4. Cryptographic SecureRandom padding (16 bytes)
            val randomBytes = ByteArray(16)
            secureRandom.nextBytes(randomBytes)
            bb.put(randomBytes)

            // SHA-256 uniform mixing
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bb.array())
            ByteBuffer.wrap(digest).long
        } catch (e: Throwable) {
            Log.e(TAG, "Error generating hardware seed, falling back to SecureRandom", e)
            secureRandom.nextLong()
        }
    }

    /**
     * Generates a strictly non-repeating shuffle permutation for [count] items.
     * Every index from 0 until [count] appears EXACTLY ONCE (mathematically zero repetitions).
     *
     * @param count Total number of items in the queue
     * @param fixedFirstIndex Optional index that MUST be placed at the very start of the shuffle order
     *                        (e.g., the song currently playing, or the specific song clicked by user)
     * @param avoidFirstIndex Optional index to avoid placing at index 0 (e.g., the last played song from
     *                        the previous shuffle cycle to avoid back-to-back repeats across cycle boundaries)
     * @param seed The hardware-derived 64-bit seed physically sampled from device hardware
     */
    fun buildPermutation(
        count: Int,
        fixedFirstIndex: Int? = null,
        avoidFirstIndex: Int? = null,
        seed: Long
    ): IntArray {
        if (count <= 1) return IntArray(count) { it }

        val rng = java.util.Random(seed)

        // Build list of all indices
        val allIndices = ArrayList<Int>(count)
        for (i in 0 until count) {
            allIndices.add(i)
        }

        val result = IntArray(count)

        if (fixedFirstIndex != null && fixedFirstIndex in 0 until count) {
            // Song at fixedFirstIndex is explicitly placed first
            result[0] = fixedFirstIndex
            allIndices.remove(fixedFirstIndex)

            // Uniformly shuffle all remaining (count - 1) songs using Fisher-Yates
            fisherYates(allIndices, rng)
            for (i in allIndices.indices) {
                result[i + 1] = allIndices[i]
            }
        } else {
            // Full uniform Fisher-Yates shuffle of all songs
            fisherYates(allIndices, rng)

            // If the first song happens to match avoidFirstIndex (and count > 1), swap it with another index
            if (avoidFirstIndex != null && allIndices.isNotEmpty() && allIndices[0] == avoidFirstIndex) {
                val swapTarget = 1 + rng.nextInt(allIndices.size - 1)
                val temp = allIndices[0]
                allIndices[0] = allIndices[swapTarget]
                allIndices[swapTarget] = temp
            }

            for (i in allIndices.indices) {
                result[i] = allIndices[i]
            }
        }

        return result
    }

    private fun fisherYates(list: ArrayList<Int>, rng: java.util.Random) {
        for (i in list.lastIndex downTo 1) {
            val j = rng.nextInt(i + 1)
            val temp = list[i]
            list[i] = list[j]
            list[j] = temp
        }
    }

    /**
     * Builds an ExoPlayer [ShuffleOrder] using hardware-entropy permutation.
     */
    fun createShuffleOrder(
        count: Int,
        fixedFirstIndex: Int? = null,
        avoidFirstIndex: Int? = null,
        seed: Long
    ): ShuffleOrder {
        val perm = buildPermutation(count, fixedFirstIndex, avoidFirstIndex, seed)
        return ShuffleOrder.DefaultShuffleOrder(perm, seed)
    }
}
