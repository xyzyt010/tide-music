package com.example.tidemusic.playback

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.tidemusic.MainActivity
import com.example.tidemusic.R
import com.example.tidemusic.di.ServiceLocator
import com.example.tidemusic.domain.Song
import com.example.tidemusic.util.AudioArtworkFetcher
import com.example.tidemusic.util.PlaceholderArt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin

/**
 * Engine 2: Dedicated Aqua Dynamics Status Bar Capsule Service.
 *
 * Positions an ultra-sleek, zero-battery punch-hole capsule at the top center of the screen
 * (matching ColorOS / Realme UI Live Alerts & Dynamic Island):
 * - Left: 24dp album art square with rounded corners.
 * - Center: Punch-hole camera transparent cutout gap.
 * - Right: 4 animated white equalizer bars (||||) dancing to the music.
 * - Tap / Pull down: Morphs into the frosted floating media card with seekbar & controls.
 * - Tap outside: Collapses smoothly back to the status bar capsule.
 * - Automatically freezes animator when paused (0% CPU / battery) and hides when stopped.
 */
class AquaDynamicsService : Service() {

    private var windowManager: WindowManager? = null
    private var rootContainer: FrameLayout? = null
    private var capsuleView: LinearLayout? = null
    private var expandedCardView: LinearLayout? = null

    // Capsule subviews
    private var capsuleArt: ImageView? = null
    private var capsuleEqualizer: AquaEqualizerView? = null

    // Expanded card subviews
    private var cardArt: ImageView? = null
    private var cardTitle: TextView? = null
    private var cardArtist: TextView? = null
    private var cardTimeCurrent: TextView? = null
    private var cardTimeTotal: TextView? = null
    private var cardSeekBar: AquaSeekBarView? = null
    private var cardPlayPauseBtn: ImageView? = null
    private var cardFavBtn: ImageView? = null
    private var cardShuffleBtn: ImageView? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isExpanded = false
    private var isViewAttached = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var progressUpdateJob: Runnable? = null
    private var currentSong: Song? = null
    private var currentArtBitmap: Bitmap? = null

    companion object {
        const val ACTION_START = "com.example.tidemusic.AQUA_START"
        const val ACTION_STOP = "com.example.tidemusic.AQUA_STOP"

        fun startIfEnabled(context: Context) {
            try {
                if (Settings.canDrawOverlays(context) && ServiceLocator.settingsManager.isAquaDynamicsPillEnabled.value) {
                    val intent = Intent(context, AquaDynamicsService::class.java).apply {
                        action = ACTION_START
                    }
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("AquaDynamicsService", "Error starting AquaDynamicsService", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, AquaDynamicsService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                android.util.Log.e("AquaDynamicsService", "Error stopping AquaDynamicsService", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
        buildViews()
        observePlayback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            hideCapsule()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this) || !ServiceLocator.settingsManager.isAquaDynamicsPillEnabled.value) {
            hideCapsule()
            stopSelf()
            return START_NOT_STICKY
        }
        val controller = ServiceLocator.playbackController
        val id = controller.currentMediaId ?: controller.currentPlayingSongId.value
        if (id != null) {
            serviceScope.launch {
                val song = ServiceLocator.repository.getSong(id)
                withContext(Dispatchers.Main) {
                    currentSong = song
                    updateSongMetadata(song)
                    showCapsule()
                    if (controller.isPlaying) {
                        capsuleEqualizer?.startAnimation()
                    }
                }
            }
        } else {
            showCapsule()
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildViews() {
        val wm = windowManager ?: return

        rootContainer = object : FrameLayout(this) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                if (isExpanded && ev.action == MotionEvent.ACTION_OUTSIDE) {
                    collapse()
                    return true
                }
                return super.dispatchTouchEvent(ev)
            }
        }

        // 1. Build Collapsed Capsule (Matching User's Screenshots 2 & 3)
        capsuleView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(4), dp(8), dp(4))

            val bg = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dp(19).toFloat()
            }
            background = bg
            elevation = dp(8).toFloat()

            // Left: Album art square with smooth rounded corners
            capsuleArt = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(6).toFloat())
                    }
                }
                clipToOutline = true
                setImageResource(R.drawable.ic_music_note)
            }
            addView(capsuleArt)

            // Center: Punch-hole camera gap
            val cameraGap = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(24))
            }
            addView(cameraGap)

            // Right: 4 animated white equalizer bars (||||)
            capsuleEqualizer = AquaEqualizerView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(18)).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
            }
            addView(capsuleEqualizer)

            // Tap on capsule expands into media card
            setOnClickListener {
                expand()
            }
        }

        // 2. Build Expanded Media Card (Matching User's Screenshots 0 & 1)
        expandedCardView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(18), dp(16), dp(18), dp(16))

            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#EE16181F")) // Deep dark frosted slate
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), Color.parseColor("#2BFFFFFF")) // Subtle border
            }
            background = bg
            elevation = dp(16).toFloat()

            // Header Row: Artwork, Title/Artist, Close Button
            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

                cardArt = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, dp(10).toFloat())
                        }
                    }
                    clipToOutline = true
                    setImageResource(R.drawable.ic_music_note)
                    setOnClickListener {
                        openApp()
                    }
                }
                addView(cardArt)

                val titleCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dp(14)
                        marginEnd = dp(10)
                    }

                    cardTitle = TextView(context).apply {
                        textSize = 15f
                        setTextColor(Color.WHITE)
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        text = "Tide Music"
                        setOnClickListener { openApp() }
                    }
                    addView(cardTitle)

                    cardArtist = TextView(context).apply {
                        textSize = 12.5f
                        setTextColor(Color.parseColor("#B3FFFFFF"))
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        text = "Ready to play"
                        setOnClickListener { openApp() }
                    }
                    addView(cardArtist)
                }
                addView(titleCol)

                val collapseBtn = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                    setImageResource(R.drawable.ic_close_notification)
                    setColorFilter(Color.parseColor("#99FFFFFF"))
                    setOnClickListener {
                        collapse()
                    }
                }
                addView(collapseBtn)
            }
            addView(headerRow)

            // Row 2: Progress Seek Bar & Timestamps
            val progressLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(14)
                }

                cardTimeCurrent = TextView(context).apply {
                    textSize = 11f
                    setTextColor(Color.parseColor("#80FFFFFF"))
                    text = "00:00"
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                addView(cardTimeCurrent)

                cardSeekBar = AquaSeekBarView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(22), 1f).apply {
                        marginStart = dp(8)
                        marginEnd = dp(8)
                    }
                    setOnSeekListener { positionMs ->
                        ServiceLocator.playbackController.seekTo(positionMs)
                    }
                }
                addView(cardSeekBar)

                cardTimeTotal = TextView(context).apply {
                    textSize = 11f
                    setTextColor(Color.parseColor("#80FFFFFF"))
                    text = "00:00"
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                addView(cardTimeTotal)
            }
            addView(progressLayout)

            // Row 3: Playback Controls (Favorite, Prev, White Play/Pause Circle, Next, Shuffle)
            val controlsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(14)
                }

                cardFavBtn = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                    setPadding(dp(7), dp(7), dp(7), dp(7))
                    setImageResource(R.drawable.ic_notif_favorite_border)
                    setColorFilter(Color.WHITE)
                    setOnClickListener {
                        ServiceLocator.playbackController.toggleFavoriteCurrentSong { isFav ->
                            updateFavoriteIcon(isFav)
                        }
                    }
                }
                addView(cardFavBtn)

                val prevBtn = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                        marginStart = dp(18)
                    }
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    setImageResource(R.drawable.ic_notif_prev)
                    setColorFilter(Color.WHITE)
                    setOnClickListener {
                        MusicManager.get(applicationContext).previous()
                    }
                }
                addView(prevBtn)

                // Central circular white Play/Pause button
                val playPauseContainer = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                        marginStart = dp(20)
                        marginEnd = dp(20)
                    }
                    val circleBg = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.WHITE)
                    }
                    background = circleBg
                    elevation = dp(4).toFloat()

                    cardPlayPauseBtn = ImageView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER)
                        setImageResource(R.drawable.ic_play_black)
                    }
                    addView(cardPlayPauseBtn)

                    setOnClickListener {
                        MusicManager.get(applicationContext).togglePlayPause()
                    }
                }
                addView(playPauseContainer)

                val nextBtn = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                        marginEnd = dp(18)
                    }
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    setImageResource(R.drawable.ic_notif_next)
                    setColorFilter(Color.WHITE)
                    setOnClickListener {
                        MusicManager.get(applicationContext).next()
                    }
                }
                addView(nextBtn)

                cardShuffleBtn = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                    setPadding(dp(7), dp(7), dp(7), dp(7))
                    setImageResource(R.drawable.ic_notif_shuffle_off)
                    setColorFilter(Color.WHITE)
                    setOnClickListener {
                        val nextShuffle = !ServiceLocator.playbackController.isShuffleEnabled
                        ServiceLocator.playbackController.setShuffleMode(nextShuffle)
                        updateShuffleIcon(nextShuffle)
                    }
                }
                addView(cardShuffleBtn)
            }
            addView(controlsRow)
        }

        rootContainer?.addView(capsuleView)
        rootContainer?.addView(expandedCardView)
    }

    private fun getStatusBarTopOffset(): Int {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val sbHeight = if (resId > 0) resources.getDimensionPixelSize(resId) else dp(36)
        val diff = (sbHeight - dp(38)) / 2
        return diff.coerceAtLeast(0)
    }

    private fun getCollapsedLayoutParams(): WindowManager.LayoutParams {
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        return WindowManager.LayoutParams(
            dp(168),
            dp(38),
            type,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            y = getStatusBarTopOffset()
        }
    }

    private fun getExpandedLayoutParams(): WindowManager.LayoutParams {
        val dm = resources.displayMetrics
        val width = minOf((dm.widthPixels * 0.94f).toInt(), dp(360))

        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        return WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            y = dp(10)
        }
    }

    private fun showCapsule() {
        if (isViewAttached || rootContainer == null || windowManager == null) return
        try {
            windowManager?.addView(rootContainer, getCollapsedLayoutParams())
            isViewAttached = true
            isExpanded = false
            capsuleView?.visibility = View.VISIBLE
            expandedCardView?.visibility = View.GONE
        } catch (e: Exception) {
            android.util.Log.e("AquaDynamicsService", "Error showing capsule", e)
        }
    }

    private fun hideCapsule() {
        if (!isViewAttached || rootContainer == null || windowManager == null) return
        try {
            capsuleEqualizer?.stopAnimation()
            stopProgressUpdater()
            windowManager?.removeView(rootContainer)
            isViewAttached = false
        } catch (e: Exception) {
            android.util.Log.e("AquaDynamicsService", "Error hiding capsule", e)
        }
    }

    private fun expand() {
        if (!isViewAttached || isExpanded) return
        try {
            isExpanded = true
            capsuleView?.visibility = View.GONE
            expandedCardView?.visibility = View.VISIBLE
            windowManager?.updateViewLayout(rootContainer, getExpandedLayoutParams())
            startProgressUpdater()
        } catch (e: Exception) {
            android.util.Log.e("AquaDynamicsService", "Error expanding card", e)
        }
    }

    private fun collapse() {
        if (!isViewAttached || !isExpanded) return
        try {
            isExpanded = false
            stopProgressUpdater()
            expandedCardView?.visibility = View.GONE
            capsuleView?.visibility = View.VISIBLE
            windowManager?.updateViewLayout(rootContainer, getCollapsedLayoutParams())
        } catch (e: Exception) {
            android.util.Log.e("AquaDynamicsService", "Error collapsing card", e)
        }
    }

    private fun openApp() {
        collapse()
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("AquaDynamicsService", "Error opening app", e)
        }
    }

    private fun observePlayback() {
        val controller = ServiceLocator.playbackController

        // 1. Observe Playing state
        serviceScope.launch {
            controller.isPlayingState.collectLatest { isPlaying ->
                withContext(Dispatchers.Main) {
                    if (isPlaying) {
                        showCapsule()
                        capsuleEqualizer?.startAnimation()
                        cardPlayPauseBtn?.setImageResource(R.drawable.ic_pause_black)
                        if (isExpanded) startProgressUpdater()
                    } else {
                        capsuleEqualizer?.stopAnimation()
                        cardPlayPauseBtn?.setImageResource(R.drawable.ic_play_black)
                        stopProgressUpdater()
                    }
                }
            }
        }

        // 2. Observe Active Song
        serviceScope.launch {
            controller.currentPlayingSongId.collectLatest { songId ->
                val activeId = songId ?: controller.currentMediaId
                if (activeId == null) {
                    if (!controller.isPlaying) {
                        hideCapsule()
                    }
                    return@collectLatest
                }
                val song = ServiceLocator.repository.getSong(activeId)
                withContext(Dispatchers.Main) {
                    currentSong = song
                    updateSongMetadata(song)
                    if (controller.isPlaying) {
                        showCapsule()
                    }
                }
            }
        }
    }

    private fun updateSongMetadata(song: Song?) {
        if (song == null) return
        cardTitle?.text = song.title.ifBlank { "Unknown" }
        cardArtist?.text = song.artist ?: "Unknown Artist"
        cardTimeTotal?.text = formatTime(song.durationMs)
        cardSeekBar?.setMax(song.durationMs)

        updateFavoriteIcon(song.isFavorite)
        updateShuffleIcon(ServiceLocator.playbackController.isShuffleEnabled)

        // Load artwork asynchronously
        serviceScope.launch(Dispatchers.IO) {
            val bmp = loadArtwork(song)
            withContext(Dispatchers.Main) {
                currentArtBitmap = bmp
                if (bmp != null) {
                    capsuleArt?.setImageBitmap(bmp)
                    cardArt?.setImageBitmap(bmp)
                } else {
                    capsuleArt?.setImageResource(R.drawable.ic_music_note)
                    cardArt?.setImageResource(R.drawable.ic_music_note)
                }
            }
        }
    }

    private fun loadArtwork(song: Song): Bitmap? {
        val bytes = AudioArtworkFetcher.extractEmbeddedPicture(song.filePath, song.uri, this)
        if (bytes != null) {
            try {
                val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (raw != null) return raw
            } catch (_: Exception) {}
        }
        return PlaceholderArt.bitmapFor(song.id)
    }

    private fun updateFavoriteIcon(isFav: Boolean) {
        cardFavBtn?.setImageResource(
            if (isFav) R.drawable.ic_notif_favorite_filled else R.drawable.ic_notif_favorite_border
        )
        cardFavBtn?.setColorFilter(
            if (isFav) Color.parseColor("#FF4B6E") else Color.WHITE
        )
    }

    private fun updateShuffleIcon(shuffleOn: Boolean) {
        cardShuffleBtn?.setImageResource(
            if (shuffleOn) R.drawable.ic_notif_shuffle_on else R.drawable.ic_notif_shuffle_off
        )
        cardShuffleBtn?.setColorFilter(
            if (shuffleOn) Color.parseColor("#26B8FF") else Color.WHITE
        )
    }

    private fun startProgressUpdater() {
        stopProgressUpdater()
        progressUpdateJob = object : Runnable {
            override fun run() {
                val pos = ServiceLocator.playbackController.currentPosition
                cardTimeCurrent?.text = formatTime(pos)
                cardSeekBar?.setProgress(pos)
                if (isExpanded && ServiceLocator.playbackController.isPlaying) {
                    mainHandler.postDelayed(this, 1000L)
                }
            }
        }.also {
            mainHandler.post(it)
        }
    }

    private fun stopProgressUpdater() {
        progressUpdateJob?.let { mainHandler.removeCallbacks(it) }
        progressUpdateJob = null
    }

    private fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }

    private fun dp(dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()

    override fun onDestroy() {
        hideCapsule()
        serviceScope.cancel()
        stopProgressUpdater()
        super.onDestroy()
    }

    /**
     * 4-bar dynamic animated equalizer view matching ColorOS / Realme UI punch-hole pill.
     */
    class AquaEqualizerView(context: Context) : View(context) {
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeCap = Paint.Cap.ROUND
        }
        private var animator: ValueAnimator? = null
        private var phase = 0f
        private var isAnimating = false

        private val barHeights = floatArrayOf(0.4f, 0.9f, 0.5f, 0.8f)

        init {
            barPaint.strokeWidth = dpToPx(2.5f)
        }

        fun startAnimation() {
            if (isAnimating) return
            isAnimating = true
            animator = ValueAnimator.ofFloat(0f, 6.283f).apply {
                duration = 850L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { va ->
                    phase = va.animatedValue as Float
                    barHeights[0] = (0.35f + 0.60f * sin(phase + 0.0f)).coerceIn(0.15f, 1.0f)
                    barHeights[1] = (0.35f + 0.60f * sin(phase + 1.8f)).coerceIn(0.15f, 1.0f)
                    barHeights[2] = (0.35f + 0.60f * sin(phase + 3.4f)).coerceIn(0.15f, 1.0f)
                    barHeights[3] = (0.35f + 0.60f * sin(phase + 4.9f)).coerceIn(0.15f, 1.0f)
                    invalidate()
                }
                start()
            }
        }

        fun stopAnimation() {
            if (!isAnimating) return
            isAnimating = false
            animator?.cancel()
            animator = null
            // Resting baseline bars
            barHeights[0] = 0.25f
            barHeights[1] = 0.25f
            barHeights[2] = 0.25f
            barHeights[3] = 0.25f
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val numBars = 4
            val barW = barPaint.strokeWidth
            val totalBarsW = numBars * barW
            val spacing = (w - totalBarsW) / (numBars - 1).coerceAtLeast(1)

            val centerY = h / 2f
            val maxHalfH = (h / 2f) - dpToPx(1.5f)

            for (i in 0 until numBars) {
                val x = (barW / 2f) + i * (barW + spacing)
                val halfH = maxHalfH * barHeights[i]
                canvas.drawLine(x, centerY - halfH, x, centerY + halfH, barPaint)
            }
        }

        private fun dpToPx(dp: Float): Float =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    }

    /**
     * Draggable scrubber seekbar for the expanded card.
     */
    class AquaSeekBarView(context: Context) : View(context) {
        private var onSeekListener: ((Long) -> Unit)? = null
        private var currentPos: Long = 0
        private var maxPos: Long = 1
        private var isDragging = false

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#33FFFFFF")
            style = Paint.Style.FILL
        }
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        private val trackRect = RectF()

        fun setOnSeekListener(listener: (Long) -> Unit) {
            this.onSeekListener = listener
        }

        fun setMax(max: Long) {
            this.maxPos = max.coerceAtLeast(1L)
            invalidate()
        }

        fun setProgress(pos: Long) {
            if (!isDragging) {
                this.currentPos = pos.coerceIn(0L, maxPos)
                invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            val centerY = h / 2f
            val trackH = dpToPx(4f)
            val corner = trackH / 2f
            val thumbR = if (isDragging) dpToPx(6f) else dpToPx(4.5f)

            val left = thumbR
            val right = w - thumbR
            val usableW = (right - left).coerceAtLeast(1f)

            // Background track
            trackRect.set(left, centerY - corner, right, centerY + corner)
            canvas.drawRoundRect(trackRect, corner, corner, trackPaint)

            // Active progress track
            val fraction = (currentPos.toFloat() / maxPos.toFloat()).coerceIn(0f, 1f)
            val progressX = left + (usableW * fraction)
            trackRect.set(left, centerY - corner, progressX, centerY + corner)
            canvas.drawRoundRect(trackRect, corner, corner, progressPaint)

            // Thumb
            canvas.drawCircle(progressX, centerY, thumbR, thumbPaint)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val thumbR = dpToPx(6f)
            val left = thumbR
            val right = width - thumbR
            val usableW = (right - left).coerceAtLeast(1f)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    updateFromTouch(event.x, left, usableW)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    updateFromTouch(event.x, left, usableW)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    updateFromTouch(event.x, left, usableW)
                    invalidate()
                    onSeekListener?.invoke(currentPos)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun updateFromTouch(touchX: Float, left: Float, usableW: Float) {
            val fraction = ((touchX - left) / usableW).coerceIn(0f, 1f)
            currentPos = (fraction * maxPos).toLong()
        }

        private fun dpToPx(dp: Float): Float =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    }
}
