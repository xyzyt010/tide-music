package com.example.tidemusic.playback

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-scoped sleep timer.
 *
 * The previous implementation kept its Handler inside the SleepTimer screen's composition,
 * so navigating away cancelled the pending callbacks and the timer silently never fired.
 * This singleton owns the Handler for the whole process lifetime; playback pauses even if
 * the user leaves the screen or the app goes to the background.
 */
object SleepTimerManager {

    private val handler = Handler(Looper.getMainLooper())

    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    private var startTime = 0L
    private var durationMs = 0L

    val isRunning: Boolean get() = _remainingMs.value > 0

    fun start(minutes: Int, onElapsed: () -> Unit) {
        handler.removeCallbacksAndMessages(null)
        durationMs = minutes * 60L * 1000L
        startTime = System.currentTimeMillis()
        _remainingMs.value = durationMs
        handler.post(object : Runnable {
            override fun run() {
                val left = durationMs - (System.currentTimeMillis() - startTime)
                if (left <= 0) {
                    _remainingMs.value = 0
                    onElapsed()
                } else {
                    _remainingMs.value = left
                    handler.postDelayed(this, 1000L)
                }
            }
        })
    }

    fun cancel() {
        handler.removeCallbacksAndMessages(null)
        startTime = 0L
        durationMs = 0L
        _remainingMs.value = 0
    }
}
