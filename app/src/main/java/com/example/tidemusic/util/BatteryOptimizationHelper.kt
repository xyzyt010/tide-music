package com.example.tidemusic.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationHelper {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_BATTERY_PROMPTED = "battery_optimization_prompted"

    /**
     * Checks if Tide Music is exempted from Android's background battery restrictions / Doze mode.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Launches the system dialog or settings to allow unrestricted background execution.
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {}
        }
    }

    /**
     * Shows a polite, informative prompt explaining why unrestricted background access
     * is beneficial for uninterrupted music playback when closed from Recents or when screen is locked.
     */
    fun promptIfNeeded(activity: Activity) {
        if (isIgnoringBatteryOptimizations(activity)) return

        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasPrompted = prefs.getBoolean(KEY_BATTERY_PROMPTED, false)
        if (hasPrompted) return

        prefs.edit().putBoolean(KEY_BATTERY_PROMPTED, true).apply()

        android.app.AlertDialog.Builder(activity)
            .setTitle("Keep Music Playing in Background")
            .setMessage("Android power saving can pause playback when you swipe Tide Music away from Recent Apps or lock your screen.\n\nAllow unrestricted background battery usage so your music never stops?")
            .setPositiveButton("Allow") { _, _ ->
                requestIgnoreBatteryOptimizations(activity)
            }
            .setNegativeButton("Not now", null)
            .show()
    }
}
