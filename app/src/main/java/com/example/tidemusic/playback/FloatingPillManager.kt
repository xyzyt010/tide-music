package com.example.tidemusic.playback

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.media3.common.MediaItem
import android.graphics.BitmapFactory
import com.example.tidemusic.MainActivity
import com.example.tidemusic.R
import com.example.tidemusic.util.AudioArtworkFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Manages the system-wide top floating dynamic island capsule widget.
 *
 * Appears at the top of the screen (around the camera cutout) ONLY when:
 * 1. The user has granted "Display over other apps" (SYSTEM_ALERT_WINDOW).
 * 2. Tide Music is in the background / minimized (outside the app).
 * 3. Music is currently playing.
 *
 * Automatically hides whenever Tide Music enters the foreground or playback stops.
 */
object FloatingPillManager {

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var pillView: View? = null
    private var equalizerView: SevenBarEqualizerView? = null
    private var artImageView: ImageView? = null

    private var isAppInForeground: Boolean = false
    private var isPlaying: Boolean = false
    private var currentMediaItem: MediaItem? = null

    fun setAppInForeground(context: Context, inForeground: Boolean) {
        handler.post {
            if (isAppInForeground != inForeground) {
                isAppInForeground = inForeground
                updateOverlay(context.applicationContext)
            }
        }
    }

    fun updatePlayback(context: Context, isPlaying: Boolean, mediaItem: MediaItem?) {
        handler.post {
            this.isPlaying = isPlaying
            this.currentMediaItem = mediaItem
            updateOverlay(context.applicationContext)
        }
    }

    private fun updateOverlay(context: Context) {
        // Must have overlay permission and playback active while app is in background
        val canDraw = Settings.canDrawOverlays(context)
        val shouldShow = canDraw && isPlaying && !isAppInForeground && currentMediaItem != null

        if (shouldShow) {
            showOrUpdatePill(context)
        } else {
            removePill()
        }
    }

    private fun showOrUpdatePill(context: Context) {
        if (windowManager == null) {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        }
        val wm = windowManager ?: return

        if (pillView == null) {
            val pill = createPillView(context)
            pillView = pill

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dpToPx(context, 10f) // Sits neatly around camera punch-hole
            }

            try {
                wm.addView(pill, params)
            } catch (e: Exception) {
                android.util.Log.e("FloatingPillManager", "Failed to add floating pill", e)
                pillView = null
                return
            }
        }

        // Update artwork and equalizer
        updatePillContent(context)
        equalizerView?.startAnimation()
    }

    private fun removePill() {
        equalizerView?.stopAnimation()
        pillView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {}
        }
        pillView = null
        equalizerView = null
        artImageView = null
    }

    private fun createPillView(context: Context): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dpToPx(context, 8f),
                dpToPx(context, 4f),
                dpToPx(context, 9f),
                dpToPx(context, 4f)
            )

            // Black capsule background with subtle border
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(context, 16f).toFloat()
                setColor(Color.parseColor("#0C0C0C"))
                setStroke(dpToPx(context, 1f), Color.parseColor("#242424"))
            }

            // Clicking opens Tide Music directly to the player screen
            setOnClickListener {
                try {
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        data = Uri.parse("tidemusic://player")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("FloatingPillManager", "Error launching player from pill", e)
                }
            }
        }

        val art = ImageView(context).apply {
            val size = dpToPx(context, 20f)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dpToPx(context, 7f)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_music_notification)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(context, 5f).toFloat()
                setColor(Color.parseColor("#1A1A1A"))
            }
            clipToOutline = true
        }
        artImageView = art
        root.addView(art)

        val eq = SevenBarEqualizerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(context, 22f),
                dpToPx(context, 14f)
            )
        }
        equalizerView = eq
        root.addView(eq)

        return root
    }

    private fun updatePillContent(context: Context) {
        val mediaItem = currentMediaItem ?: return
        val artView = artImageView ?: return
        val extras = mediaItem.mediaMetadata.extras
        val filePath = extras?.getString(PlaybackController.EXTRA_FILE_PATH).orEmpty()
        val uriString = mediaItem.localConfiguration?.uri?.toString().orEmpty()

        CoroutineScope(Dispatchers.IO).launch {
            val bytes = AudioArtworkFetcher.extractEmbeddedPicture(filePath, uriString, context)
            val bmp = if (bytes != null && bytes.isNotEmpty()) {
                try {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (_: Throwable) { null }
            } else null

            handler.post {
                if (bmp != null) {
                    artView.setImageBitmap(bmp)
                } else {
                    artView.setImageResource(R.drawable.ic_music_notification)
                }
            }
        }
    }

    private fun dpToPx(context: Context, dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt()
}

/**
 * Custom 7-band thin animated sound equalizer bars for the system top pill.
 */
private class SevenBarEqualizerView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val rect = RectF()
    private val barFractions = FloatArray(7) { 0.5f }
    private var animator: ValueAnimator? = null

    init {
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val progress = anim.animatedFraction
                // 7 distinct wave phases creating an organic sound spectrum animation
                barFractions[0] = (sin(progress * 2 * PI * 2.3 + 0.3).toFloat() + 1f) / 2f * 0.7f + 0.2f
                barFractions[1] = (sin(progress * 2 * PI * 3.1 + 1.2).toFloat() + 1f) / 2f * 0.8f + 0.15f
                barFractions[2] = (sin(progress * 2 * PI * 1.8 + 2.5).toFloat() + 1f) / 2f * 0.85f + 0.15f
                barFractions[3] = (sin(progress * 2 * PI * 2.7 + 0.7).toFloat() + 1f) / 2f * 0.75f + 0.25f
                barFractions[4] = (sin(progress * 2 * PI * 3.4 + 1.9).toFloat() + 1f) / 2f * 0.8f + 0.2f
                barFractions[5] = (sin(progress * 2 * PI * 2.1 + 3.1).toFloat() + 1f) / 2f * 0.7f + 0.2f
                barFractions[6] = (sin(progress * 2 * PI * 2.9 + 0.4).toFloat() + 1f) / 2f * 0.75f + 0.15f
                invalidate()
            }
        }
    }

    fun startAnimation() {
        if (animator?.isStarted != true) {
            animator?.start()
        }
    }

    fun stopAnimation() {
        animator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val barCount = 7
        val barWidth = dpToPx(1.6f)
        val gap = (w - (barCount * barWidth)) / (barCount - 1).coerceAtLeast(1)
        val corner = dpToPx(0.8f)

        for (i in 0 until barCount) {
            val left = i * (barWidth + gap)
            val right = left + barWidth
            val barHeight = (h * barFractions[i]).coerceAtLeast(dpToPx(2.5f))
            val top = h - barHeight
            rect.set(left, top, right, h)
            canvas.drawRoundRect(rect, corner, corner, paint)
        }
    }

    private fun dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }
}
