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
     * Generates a non-repeating shuffle permutation for [count] items.
     *
     * @param count Total number of items in the queue
     * @param fixedFirstIndex Optional index that MUST be placed at the very start of the shuffle order
     *                        (e.g., the song currently playing, or the specific song clicked by user)
     * @param recentIndices Set of song indices that played recently and should be pushed to the
     *                      back half of the shuffle queue to avoid repetitions
     * @param seed The hardware-derived 64-bit seed
     */
    fun buildPermutation(
        count: Int,
        fixedFirstIndex: Int? = null,
        recentIndices: Set<Int> = emptySet(),
        seed: Long
    ): IntArray {
        if (count <= 1) return IntArray(count) { it }

        val rng = java.util.Random(seed)

        // Collect all other indices to shuffle
        val remainingIndices = ArrayList<Int>(count)
        for (i in 0 until count) {
            if (fixedFirstIndex == null || i != fixedFirstIndex) {
                remainingIndices.add(i)
            }
        }

        // Partition remaining items into "fresh" vs "recently played"
        val fresh = ArrayList<Int>()
        val recent = ArrayList<Int>()
        for (idx in remainingIndices) {
            if (recentIndices.contains(idx)) {
                recent.add(idx)
            } else {
                fresh.add(idx)
            }
        }

        // Fisher-Yates shuffle each partition with the hardware-derived PRNG
        fisherYates(fresh, rng)
        fisherYates(recent, rng)

        val result = IntArray(count)
        var writePos = 0

        // If a fixed starting index was requested, it goes first
        if (fixedFirstIndex != null && fixedFirstIndex in 0 until count) {
            result[writePos++] = fixedFirstIndex
        }

        // Fresh unplayed items play next
        for (item in fresh) {
            result[writePos++] = item
        }

        // Recently played items play last, preventing repeat loops
        for (item in recent) {
            result[writePos++] = item
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
        recentIndices: Set<Int> = emptySet(),
        seed: Long
    ): ShuffleOrder {
        val perm = buildPermutation(count, fixedFirstIndex, recentIndices, seed)
        return ShuffleOrder.DefaultShuffleOrder(perm, seed)
    }
}
