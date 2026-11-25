package com.example.tidemusic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.tidemusic.MainActivity
import com.example.tidemusic.R
import com.example.tidemusic.di.ServiceLocator

class TidePillWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_tide_pill)

            // Open app intent
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            // Play/Pause intent via playbackController
            val playPauseIntent = Intent(context, TidePillWidget::class.java).apply {
                action = "ACTION_PLAY_PAUSE"
            }
            val playPausePending = PendingIntent.getBroadcast(
                context, 1, playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_play_pause, playPausePending)

            try {
                val controller = ServiceLocator.playbackController
                val isPlaying = controller.isPlaying
                val title = controller.currentTitle.orEmpty()
                val artist = controller.currentArtist.orEmpty()

                if (title.isNotBlank()) {
                    views.setTextViewText(R.id.widget_title, title)
                    views.setTextViewText(R.id.widget_artist, artist.ifBlank { "Unknown Artist" })
                } else {
                    views.setTextViewText(R.id.widget_title, "Tide Music")
                    views.setTextViewText(R.id.widget_artist, "Tap to play music")
                }

                views.setImageViewResource(
                    R.id.widget_play_pause,
                    if (isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play
                )
            } catch (_: Exception) {}

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val component = ComponentName(context, TidePillWidget::class.java)
                val ids = appWidgetManager.getAppWidgetIds(component)
                if (ids.isNotEmpty()) {
                    for (id in ids) {
                        updateAppWidget(context, appWidgetManager, id)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "ACTION_PLAY_PAUSE") {
            try {
                ServiceLocator.playbackController.togglePlayPause()
                updateAllWidgets(context)
            } catch (_: Exception) {}
        }
    }
}
