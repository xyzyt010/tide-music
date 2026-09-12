package com.example.tidemusic.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.example.tidemusic.playback.AquaDynamicsService

object AquaDynamicsHelper {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_AQUA_PROMPTED = "aqua_dynamics_prompted_v2"

    /** Checks if the overlay permission is granted. */
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /** Launches the system settings screen to allow drawing over other apps. */
    fun requestOverlayPermission(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                context.startActivity(fallback)
            } catch (_: Exception) {}
        }
    }

    /**
     * Prompts the user to enable the Aqua Dynamics / Fluid Cloud Status Bar Capsule.
     */
    fun promptIfNeeded(activity: Activity) {
        if (hasOverlayPermission(activity)) {
            AquaDynamicsService.startIfEnabled(activity)
            return
        }

        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasPrompted = prefs.getBoolean(KEY_AQUA_PROMPTED, false)
        if (hasPrompted) return

        prefs.edit().putBoolean(KEY_AQUA_PROMPTED, true).apply()

        android.app.AlertDialog.Builder(activity)
            .setTitle("Enable Fluid Dynamics Status Bar Pill")
            .setMessage("Display the Spotify-style status bar capsule around your camera punch hole with live 4-bar equalizer and media controls while using other apps.\n\nEnable this feature now?")
            .setPositiveButton("Enable") { _, _ ->
                requestOverlayPermission(activity)
            }
            .setNegativeButton("Later", null)
            .show()
    }
}
