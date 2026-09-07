package com.example.tidemusic.playback;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.tidemusic.MainActivity;
import com.example.tidemusic.R;
import com.example.tidemusic.di.ServiceLocator;
import com.example.tidemusic.util.AudioArtworkFetcher;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pure Java overlay service providing the Dynamic Island top widget.
 *
 * Requirements fulfilled:
 * 1. Waves in mini pill: Slower, liquid-smooth multi-frequency animation with gentle spring dampening.
 * 2. Play/pause button: Borderless morphing icon without circular outline, matching PlayerScreen.
 * 3. Expanded widget shape: Full-width squircle card with 36dp rounded corners matching user reference image,
 *    frosted glass blurred artwork background, and compact ~164dp height.
 * 4. Cross button in top-left: Session dismissal preventing the mini pill from appearing again until app restart.
 * 5. Feature toggle: Fully integrated with SettingsScreen and SettingsManager.
 */
public class FloatingPillService extends Service {

    private static final String TAG = "FloatingPillService";
    private static final String ACTION_UPDATE = "com.example.tidemusic.action.UPDATE_FLOATING_PILL";
    private static final String ACTION_STOP = "com.example.tidemusic.action.STOP_FLOATING_PILL";

    private static final String EXTRA_FILE_PATH = "extra_file_path";
    private static final String EXTRA_URI = "extra_uri";
    private static final String EXTRA_TITLE = "extra_title";
    private static final String EXTRA_ARTIST = "extra_artist";
    private static final String EXTRA_MEDIA_ID = "extra_media_id";
    private static final String EXTRA_IS_PLAYING = "extra_is_playing";

    // Refined darker grey used for both the capsule outline and the soundwave bars
    public static final int ACCENT_GREY = Color.parseColor("#555558");

    private static volatile boolean isAppInForeground = false;
    private static volatile boolean isSessionDismissed = false;
    private static FloatingPillService sInstance = null;

    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;

    // View Hierarchy
    private FrameLayout rootContainer;
    private LinearLayout miniPillView;
    private ImageView miniArtView;
    private SixBarEqualizerView miniEqualizerView;

    private FrameLayout expandedCardContainer;
    private ImageView cardBackdropView;
    private View cardScrimView;
    private LinearLayout expandedCardContent;
    private ImageView btnDismissSession;
    private ImageView expandedArtView;
    private TextView tvTitle;
    private TextView tvArtist;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private ProfessionalScrubberView scrubberView;
    private ImageView btnFavorite;
    private ImageView btnShuffle;
    private ImageView btnPrev;
    private ImageView btnPlayPause;
    private ImageView btnNext;
    private ImageView btnRepeat;

    // Volume controls
    private ImageView btnVolumeMute;
    private VolumeScrubberView volumeScrubber;
    private ImageView btnVolumeMax;
    private android.media.AudioManager audioManager = null;

    // Queue Carousel
    private TextView tvQueueLabel;
    private TextView tvQueueCount;
    private android.widget.HorizontalScrollView queueScrollView;
    private LinearLayout queueContainer;
    private final android.util.LruCache<Long, Bitmap> queueThumbCache = new android.util.LruCache<>(40);

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String currentFilePath = "";
    private String currentUriString = "";
    private String currentTitle = "";
    private String currentArtist = "";
    private long currentMediaId = -1L;
    private boolean isPlaying = false;
    private boolean isExpanded = false;
    private boolean isCurrentSongFavorite = false;
    private boolean isUserSeeking = false;
    private Bitmap cachedArtwork = null;
    private Bitmap blurredArtwork = null;

    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (isExpanded && rootContainer != null) {
                updateTimelineProgress();
                if (isPlaying) {
                    mainHandler.postDelayed(this, 500);
                }
            }
        }
    };

    private boolean isScreenOn = true;
    private final android.content.BroadcastReceiver screenStateReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                isScreenOn = false;
                if (miniEqualizerView != null) {
                    miniEqualizerView.stop();
                }
                mainHandler.removeCallbacks(progressUpdater);
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                isScreenOn = true;
                if (isPlaying && !isAppInForeground && !isSessionDismissed && isFeatureEnabled(FloatingPillService.this)) {
                    if (miniEqualizerView != null) {
                        miniEqualizerView.start();
                    }
                }
                if (isExpanded) {
                    mainHandler.post(progressUpdater);
                    updateContent(false);
                }
            }
        }
    };

    /** Resets the cross-button session dismissal when the app restarts fresh. */
    public static void resetSessionDismissed() {
        isSessionDismissed = false;
    }

    public static void setAppInForeground(boolean inForeground) {
        isAppInForeground = inForeground;
        FloatingPillService instance = sInstance;
        if (instance != null) {
            instance.mainHandler.post(instance::updateVisibility);
        }
    }

    public static boolean isFeatureEnabled(Context context) {
        try {
            return context.getSharedPreferences("tide_settings", Context.MODE_PRIVATE)
                    .getBoolean("floating_pill_enabled", true);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Visibility rules:
     * - If session dismissed via cross button -> NEVER show until app restarts.
     * - If user disabled feature in Settings -> NEVER show.
     * - If Tide Music is in foreground -> hide.
     * - If expanded widget open -> show.
     * - If mini pill -> ONLY show when actively playing.
     */
    private void updateVisibility() {
        if (rootContainer == null) return;

        if (isSessionDismissed || !isFeatureEnabled(this)) {
            rootContainer.setVisibility(View.GONE);
            if (miniEqualizerView != null) miniEqualizerView.stop();
            return;
        }

        if (isAppInForeground) {
            rootContainer.setVisibility(View.GONE);
            if (miniEqualizerView != null) miniEqualizerView.stop();
            return;
        }

        if (isExpanded) {
            rootContainer.setVisibility(View.VISIBLE);
            return;
        }

        boolean hasMedia = (!currentFilePath.isEmpty() || !currentUriString.isEmpty() || currentMediaId > 0);
        if (isPlaying && hasMedia) {
            rootContainer.setVisibility(View.VISIBLE);
            if (miniPillView != null) {
                miniPillView.setVisibility(View.VISIBLE);
                miniPillView.setAlpha(1f);
                miniPillView.setScaleX(1f);
                miniPillView.setScaleY(1f);
                miniPillView.setTranslationX(0f);
            }
            if (miniEqualizerView != null) {
                miniEqualizerView.start();
            }
        } else {
            rootContainer.setVisibility(View.GONE);
            if (miniEqualizerView != null) {
                miniEqualizerView.stop();
            }
        }
    }

    private int getStatusBarHeight() {
        int result = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowManager != null) {
            try {
                android.view.WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
                android.view.WindowInsets insets = metrics.getWindowInsets();
                android.view.DisplayCutout cutout = insets.getDisplayCutout();
                if (cutout != null) {
                    result = cutout.getSafeInsetTop();
                }
                if (result == 0) {
                    android.graphics.Insets sb = insets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars());
                    result = sb.top;
                }
            } catch (Exception ignored) {}
        }
        if (result == 0) {
            int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                result = getResources().getDimensionPixelSize(resourceId);
            }
        }
        if (result == 0) {
            result = dpToPx(34);
        }
        return result;
    }

    public static void showOrUpdate(Context context, @Nullable String filePath, @Nullable String uriString, boolean isPlaying) {
        showOrUpdate(context, filePath, uriString, null, null, -1L, isPlaying);
    }

    public static void showOrUpdate(Context context, @Nullable String filePath, @Nullable String uriString,
                                    @Nullable String title, @Nullable String artist, long mediaId, boolean isPlaying) {
        if (isSessionDismissed || !isFeatureEnabled(context)) {
            return;
        }
        if (!Settings.canDrawOverlays(context)) {
            Log.d(TAG, "Cannot draw overlays: permission not granted");
            return;
        }

        try {
            Intent intent = new Intent(context, FloatingPillService.class);
            intent.setAction(ACTION_UPDATE);
            intent.putExtra(EXTRA_FILE_PATH, filePath != null ? filePath : "");
            intent.putExtra(EXTRA_URI, uriString != null ? uriString : "");
            intent.putExtra(EXTRA_TITLE, title != null ? title : "");
            intent.putExtra(EXTRA_ARTIST, artist != null ? artist : "");
            intent.putExtra(EXTRA_MEDIA_ID, mediaId);
            intent.putExtra(EXTRA_IS_PLAYING, isPlaying);
            context.startService(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error triggering FloatingPillService", e);
        }
    }

    public static void hide(Context context) {
        try {
            Intent intent = new Intent(context, FloatingPillService.class);
            intent.setAction(ACTION_STOP);
            context.startService(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error stopping FloatingPillService", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        audioManager = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
        try {
            android.content.IntentFilter filter = new android.content.IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            registerReceiver(screenStateReceiver, filter);
        } catch (Exception e) {
            Log.e(TAG, "Error registering screen state receiver", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            removeOverlay();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_UPDATE.equals(intent.getAction())) {
            if (isSessionDismissed || !isFeatureEnabled(this)) {
                removeOverlay();
                return START_STICKY;
            }

            String newFilePath = intent.getStringExtra(EXTRA_FILE_PATH);
            String newUriString = intent.getStringExtra(EXTRA_URI);
            currentTitle = intent.getStringExtra(EXTRA_TITLE);
            currentArtist = intent.getStringExtra(EXTRA_ARTIST);
            long newMediaId = intent.getLongExtra(EXTRA_MEDIA_ID, -1L);
            isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false);

            boolean songChanged = (newMediaId != currentMediaId) || (!newFilePath.equals(currentFilePath));
            currentFilePath = newFilePath != null ? newFilePath : "";
            currentUriString = newUriString != null ? newUriString : "";
            currentMediaId = newMediaId;

            if (!Settings.canDrawOverlays(this)) {
                removeOverlay();
                stopSelf();
                return START_NOT_STICKY;
            }

            ensureOverlayCreated();
            updateContent(songChanged);
        }

        return START_STICKY;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void ensureOverlayCreated() {
        if (rootContainer != null) return;

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            @SuppressWarnings("deprecation")
            int oldFlag = WindowManager.LayoutParams.TYPE_PHONE;
            layoutFlag = oldFlag;
        }

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        layoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        layoutParams.x = 0;
        layoutParams.y = getStatusBarHeight() + dpToPx(4);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }

        rootContainer = new FrameLayout(this) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    if (isExpanded) {
                        collapseOrDismiss();
                        return true;
                    }
                }
                return super.onTouchEvent(event);
            }
        };
        rootContainer.setClipChildren(false);
        rootContainer.setClipToPadding(false);

        // 1. Build Collapsed Mini Pill View
        buildMiniPillView();
        rootContainer.addView(miniPillView);

        // 2. Build Expanded Island Widget View
        buildExpandedCardView();
        expandedCardContainer.setVisibility(View.GONE);
        rootContainer.addView(expandedCardContainer);

        updateVisibility();

        try {
            windowManager.addView(rootContainer, layoutParams);
            if (isPlaying && miniEqualizerView != null) {
                miniEqualizerView.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to add floating capsule overlay", e);
            rootContainer = null;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void buildMiniPillView() {
        miniPillView = new LinearLayout(this);
        miniPillView.setOrientation(LinearLayout.HORIZONTAL);
        miniPillView.setGravity(Gravity.CENTER_VERTICAL);
        miniPillView.setPadding(dpToPx(7), dpToPx(4), dpToPx(8), dpToPx(4));

        // Stadium pill background with darker refined grey outline #555558
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setShape(GradientDrawable.RECTANGLE);
        pillBg.setCornerRadius(dpToPx(16));
        pillBg.setColor(Color.parseColor("#000000"));
        pillBg.setStroke(dpToPx(1.2f), ACCENT_GREY);
        miniPillView.setBackground(pillBg);

        // Thumbnail artwork (22dp x 22dp, rounded 6dp)
        miniArtView = new ImageView(this);
        int artSize = dpToPx(22);
        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(artSize, artSize);
        miniArtView.setLayoutParams(artLp);
        miniArtView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniArtView.setImageResource(R.drawable.ic_music_notification);

        GradientDrawable artBg = new GradientDrawable();
        artBg.setShape(GradientDrawable.RECTANGLE);
        artBg.setCornerRadius(dpToPx(6));
        artBg.setColor(Color.parseColor("#181818"));
        miniArtView.setBackground(artBg);
        miniArtView.setClipToOutline(true);
        miniPillView.addView(miniArtView);

        // Breathing spacer
        View breathingSpacer = new View(this);
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(dpToPx(16), 1);
        breathingSpacer.setLayoutParams(spacerLp);
        miniPillView.addView(breathingSpacer);

        // Liquid-smooth multi-harmonic soundwave in darker refined grey #555558
        miniEqualizerView = new SixBarEqualizerView(this);
        LinearLayout.LayoutParams eqLp = new LinearLayout.LayoutParams(dpToPx(20), dpToPx(14));
        miniEqualizerView.setLayoutParams(eqLp);
        miniPillView.addView(miniEqualizerView);

        miniPillView.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private long downTime;
            private boolean isSwiping = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        downTime = System.currentTimeMillis();
                        isSwiping = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (Math.abs(dx) > dpToPx(8) && Math.abs(dx) > Math.abs(dy)) {
                            isSwiping = true;
                            miniPillView.setTranslationX(dx * 0.7f);
                            float alpha = 1f - (Math.abs(dx) / (float) dpToPx(140));
                            miniPillView.setAlpha(Math.max(0.15f, alpha));
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        float totalDx = event.getRawX() - downX;
                        float totalDy = event.getRawY() - downY;
                        long elapsed = System.currentTimeMillis() - downTime;

                        if (isSwiping && Math.abs(totalDx) > dpToPx(34)) {
                            float targetX = totalDx > 0 ? dpToPx(160) : -dpToPx(160);
                            miniPillView.animate()
                                    .translationX(targetX)
                                    .alpha(0f)
                                    .setDuration(200)
                                    .withEndAction(new Runnable() {
                                        @Override
                                        public void run() {
                                            hide(FloatingPillService.this);
                                        }
                                    })
                                    .start();
                            return true;
                        }

                        miniPillView.animate()
                                .translationX(0f)
                                .alpha(1f)
                                .setDuration(160)
                                .start();

                        if (Math.abs(totalDx) < dpToPx(10) && Math.abs(totalDy) < dpToPx(10) && elapsed < 350) {
                            expandCard();
                            return true;
                        }
                        return true;
                }
                return false;
            }
        });
    }

    /**
     * Builds the expanded widget matching the user's reference image:
     * - Wide squircle card with 36dp rounded corners spanning almost full width.
     * - Frosted glass blurred artwork background.
     * - Top-left cross button (session dismissal).
     * - Borderless morphing play/pause button (no round outline).
     * - Compact ~164dp height.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void buildExpandedCardView() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int islandWidth = dm.widthPixels - dpToPx(24);
        int islandHeight = dpToPx(295);

        expandedCardContainer = new FrameLayout(this);
        FrameLayout.LayoutParams containerLp = new FrameLayout.LayoutParams(islandWidth, islandHeight);
        expandedCardContainer.setLayoutParams(containerLp);

        // 36dp continuous rounded squircle shape with #555558 border
        GradientDrawable islandBg = new GradientDrawable();
        islandBg.setShape(GradientDrawable.RECTANGLE);
        islandBg.setCornerRadius(dpToPx(36));
        islandBg.setColor(Color.parseColor("#000000"));
        islandBg.setStroke(dpToPx(1.2f), ACCENT_GREY);
        expandedCardContainer.setBackground(islandBg);
        expandedCardContainer.setClipToOutline(true);

        // 1. Frosted glass blurred artwork background
        cardBackdropView = new ImageView(this);
        cardBackdropView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        cardBackdropView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cardBackdropView.setAlpha(0.85f);
        expandedCardContainer.addView(cardBackdropView);

        // 2. Dark tint overlay scrim matching PlayerScreen and CommonUi
        cardScrimView = new View(this);
        cardScrimView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        GradientDrawable scrimDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(165, 20, 20, 20), Color.argb(225, 10, 10, 10)}
        );
        cardScrimView.setBackground(scrimDrawable);
        expandedCardContainer.addView(cardScrimView);

        // 3. Main content layout
        expandedCardContent = new LinearLayout(this);
        expandedCardContent.setOrientation(LinearLayout.VERTICAL);
        expandedCardContent.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        expandedCardContainer.addView(expandedCardContent);

        // Absorb touches inside card so clicking anywhere inside never collapses it
        expandedCardContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {}
        });
        expandedCardContent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {}
        });

        // Swipe up gesture detection on the island card to dismiss
        expandedCardContainer.setOnTouchListener(new View.OnTouchListener() {
            private float startY = 0f;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = event.getRawY();
                        break;
                    case MotionEvent.ACTION_UP:
                        float dy = event.getRawY() - startY;
                        if (dy < -dpToPx(35)) {
                            collapseOrDismiss();
                            return true;
                        }
                        break;
                }
                return false;
            }
        });

        // ── Row 1: Header (Cross Button, Artwork, Title & Artist, Open App Button) ──
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(46));
        headerRow.setLayoutParams(hLp);

        // Cross button in top-left: permanently stops mini pill for this session until app restarts
        btnDismissSession = new ImageView(this);
        int closeBtnSize = dpToPx(30);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(closeBtnSize, closeBtnSize);
        closeLp.setMarginEnd(dpToPx(10));
        btnDismissSession.setLayoutParams(closeLp);
        btnDismissSession.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
        btnDismissSession.setImageResource(R.drawable.ic_close_notification);
        btnDismissSession.setColorFilter(Color.parseColor("#A0A0A5"));
        btnDismissSession.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Session dismissal: user explicitly closed the pill widget for this app session
                isSessionDismissed = true;
                expandedCardContainer.animate()
                        .alpha(0f)
                        .scaleX(0.82f)
                        .scaleY(0.82f)
                        .setDuration(180)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                expandedCardContainer.setVisibility(View.GONE);
                                if (miniPillView != null) {
                                    miniPillView.setVisibility(View.GONE);
                                }
                                if (rootContainer != null) {
                                    rootContainer.setVisibility(View.GONE);
                                }
                                resetLayoutParamsToMini();
                            }
                        })
                        .start();
            }
        });
        headerRow.addView(btnDismissSession);

        // Artwork thumbnail (44dp x 44dp with 12dp rounded corners)
        expandedArtView = new ImageView(this);
        int artSize = dpToPx(44);
        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(artSize, artSize);
        expandedArtView.setLayoutParams(artLp);
        expandedArtView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        expandedArtView.setImageResource(R.drawable.ic_music_notification);
        GradientDrawable artBg = new GradientDrawable();
        artBg.setShape(GradientDrawable.RECTANGLE);
        artBg.setCornerRadius(dpToPx(12));
        artBg.setColor(Color.parseColor("#1E1E1E"));
        expandedArtView.setBackground(artBg);
        expandedArtView.setClipToOutline(true);
        headerRow.addView(expandedArtView);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginStart(dpToPx(10));
        textLp.setMarginEnd(dpToPx(8));
        textCol.setLayoutParams(textLp);

        tvTitle = new TextView(this);
        tvTitle.setText(currentTitle.isEmpty() ? "Tide Music" : currentTitle);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textCol.addView(tvTitle);

        tvArtist = new TextView(this);
        tvArtist.setText(currentArtist.isEmpty() ? "Ready to play" : currentArtist);
        tvArtist.setTextColor(Color.parseColor("#A0A0A5"));
        tvArtist.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tvArtist.setSingleLine(true);
        tvArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvArtist.setPadding(0, dpToPx(1), 0, 0);
        textCol.addView(tvArtist);

        headerRow.addView(textCol);

        View.OnClickListener openAppListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchPlayerScreen();
            }
        };
        expandedArtView.setOnClickListener(openAppListener);
        textCol.setOnClickListener(openAppListener);

        // Favorite Button in Header Row
        btnFavorite = new ImageView(this);
        int favSize = dpToPx(28);
        LinearLayout.LayoutParams favLp = new LinearLayout.LayoutParams(favSize, favSize);
        favLp.setMarginEnd(dpToPx(6));
        btnFavorite.setLayoutParams(favLp);
        btnFavorite.setPadding(dpToPx(3), dpToPx(3), dpToPx(3), dpToPx(3));
        btnFavorite.setImageResource(R.drawable.ic_notif_favorite_border);
        btnFavorite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    btnFavorite.animate().scaleX(0.82f).scaleY(0.82f).setDuration(80).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            btnFavorite.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                        }
                    }).start();

                    PlaybackController controller = ServiceLocator.INSTANCE.getPlaybackController();
                    controller.toggleFavoriteCurrentSong(new kotlin.jvm.functions.Function1<Boolean, kotlin.Unit>() {
                        @Override
                        public kotlin.Unit invoke(Boolean isFav) {
                            isCurrentSongFavorite = Boolean.TRUE.equals(isFav);
                            updateFavoriteIcon();
                            return kotlin.Unit.INSTANCE;
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error toggling favorite", e);
                }
            }
        });
        headerRow.addView(btnFavorite);

        // Open App button (right-aligned)
        ImageView btnOpenApp = new ImageView(this);
        int appIconSize = dpToPx(28);
        LinearLayout.LayoutParams appIconLp = new LinearLayout.LayoutParams(appIconSize, appIconSize);
        btnOpenApp.setLayoutParams(appIconLp);
        btnOpenApp.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        btnOpenApp.setImageResource(R.drawable.ic_music_notification);
        btnOpenApp.setColorFilter(Color.parseColor("#26B8FF"));
        btnOpenApp.setOnClickListener(openAppListener);
        headerRow.addView(btnOpenApp);

        expandedCardContent.addView(headerRow);

        // ── Row 2: Timeline Scrubber ─────────────────────────────────────────
        LinearLayout timelineRow = new LinearLayout(this);
        timelineRow.setOrientation(LinearLayout.HORIZONTAL);
        timelineRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tlLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(28));
        tlLp.topMargin = dpToPx(8);
        timelineRow.setLayoutParams(tlLp);

        tvCurrentTime = new TextView(this);
        tvCurrentTime.setText("00:00");
        tvCurrentTime.setTextColor(Color.parseColor("#9E9EA2"));
        tvCurrentTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        tvCurrentTime.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams curLp = new LinearLayout.LayoutParams(dpToPx(34), LinearLayout.LayoutParams.WRAP_CONTENT);
        tvCurrentTime.setLayoutParams(curLp);
        timelineRow.addView(tvCurrentTime);

        scrubberView = new ProfessionalScrubberView(this);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(0, dpToPx(26), 1f);
        sbLp.setMarginStart(dpToPx(6));
        sbLp.setMarginEnd(dpToPx(6));
        scrubberView.setLayoutParams(sbLp);
        scrubberView.setOnScrubListener(new ProfessionalScrubberView.OnScrubListener() {
            @Override
            public void onProgressChanged(long progressMs, boolean fromUser) {
                if (fromUser) {
                    tvCurrentTime.setText(formatTime(progressMs));
                }
            }

            @Override
            public void onStartTracking() {
                isUserSeeking = true;
            }

            @Override
            public void onStopTracking(long progressMs) {
                isUserSeeking = false;
                try {
                    PlaybackController controller = ServiceLocator.INSTANCE.getPlaybackController();
                    controller.seekTo(progressMs);
                } catch (Exception e) {
                    Log.e(TAG, "Error seeking playback", e);
                }
            }
        });
        timelineRow.addView(scrubberView);

        tvTotalTime = new TextView(this);
        tvTotalTime.setText("00:00");
        tvTotalTime.setTextColor(Color.parseColor("#9E9EA2"));
        tvTotalTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        tvTotalTime.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        LinearLayout.LayoutParams totLp = new LinearLayout.LayoutParams(dpToPx(34), LinearLayout.LayoutParams.WRAP_CONTENT);
        tvTotalTime.setLayoutParams(totLp);
        timelineRow.addView(tvTotalTime);

        expandedCardContent.addView(timelineRow);

        // ── Row 3: Playback Controls (Shuffle, Previous, Play/Pause, Next, Repeat) ──
        LinearLayout controlsRow = new LinearLayout(this);
        controlsRow.setOrientation(LinearLayout.HORIZONTAL);
        controlsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(46));
        cLp.topMargin = dpToPx(4);
        controlsRow.setLayoutParams(cLp);

        // 1. Shuffle Button (matching PlayerScreen icon, colors, and toast)
        btnShuffle = new ImageView(this);
        int shuffleSize = dpToPx(36);
        btnShuffle.setLayoutParams(new LinearLayout.LayoutParams(shuffleSize, shuffleSize));
        btnShuffle.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
        btnShuffle.setImageResource(R.drawable.ic_notif_shuffle_off);
        btnShuffle.setColorFilter(Color.parseColor("#A0A0A5"));
        btnShuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    btnShuffle.animate().scaleX(0.82f).scaleY(0.82f).setDuration(80).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            btnShuffle.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                        }
                    }).start();

                    PlaybackController controller = ServiceLocator.INSTANCE.getPlaybackController();
                    boolean next = controller.toggleShuffle();
                    updateShuffleButton();
                    Toast.makeText(FloatingPillService.this,
                            next ? "Shuffle mode is on" : "Shuffle mode is off",
                            Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Error toggling shuffle", e);
                }
            }
        });
        controlsRow.addView(btnShuffle);

        View spacerLeft = new View(this);
        controlsRow.addView(spacerLeft, new LinearLayout.LayoutParams(0, 1, 1f));

        // 2. Previous Track Button
        btnPrev = new ImageView(this);
        int navSize = dpToPx(40);
        btnPrev.setLayoutParams(new LinearLayout.LayoutParams(navSize, navSize));
        btnPrev.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
        btnPrev.setImageResource(R.drawable.ic_notif_prev);
        btnPrev.setColorFilter(Color.WHITE);
        btnPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    btnPrev.animate().scaleX(0.85f).scaleY(0.85f).setDuration(70).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            btnPrev.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
                        }
                    }).start();
                    ServiceLocator.INSTANCE.getPlaybackController().previous();
                } catch (Exception e) {
                    Log.e(TAG, "Error seeking previous", e);
                }
            }
        });
        controlsRow.addView(btnPrev);

        View sp1 = new View(this);
        controlsRow.addView(sp1, new LinearLayout.LayoutParams(dpToPx(18), 1));

        // 3. Play / Pause Button: Borderless morphing icon without circular outline
        btnPlayPause = new ImageView(this);
        int playSize = dpToPx(46);
        btnPlayPause.setLayoutParams(new LinearLayout.LayoutParams(playSize, playSize));
        btnPlayPause.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause_black : R.drawable.ic_play_black);
        btnPlayPause.setColorFilter(Color.WHITE);
        btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    btnPlayPause.animate()
                            .scaleX(0.86f)
                            .scaleY(0.86f)
                            .setDuration(90)
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    btnPlayPause.animate()
                                            .scaleX(1f)
                                            .scaleY(1f)
                                            .setDuration(150)
                                            .setInterpolator(new OvershootInterpolator(1.4f))
                                            .start();
                                }
                            })
                            .start();

                    isPlaying = !isPlaying;
                    btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause_black : R.drawable.ic_play_black);
                    btnPlayPause.setColorFilter(Color.WHITE);

                    if (miniEqualizerView != null) {
                        if (isPlaying) miniEqualizerView.start();
                        else miniEqualizerView.stop();
                    }
                    if (isPlaying) {
                        mainHandler.post(progressUpdater);
                    } else {
                        mainHandler.removeCallbacks(progressUpdater);
                    }
                    ServiceLocator.INSTANCE.getPlaybackController().playPause();
                } catch (Exception e) {
                    Log.e(TAG, "Error toggling play/pause", e);
                }
            }
        });
        controlsRow.addView(btnPlayPause);

        View sp2 = new View(this);
        controlsRow.addView(sp2, new LinearLayout.LayoutParams(dpToPx(18), 1));

        // 4. Next Track Button
        btnNext = new ImageView(this);
        btnNext.setLayoutParams(new LinearLayout.LayoutParams(navSize, navSize));
        btnNext.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
        btnNext.setImageResource(R.drawable.ic_notif_next);
        btnNext.setColorFilter(Color.WHITE);
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    btnNext.animate().scaleX(0.85f).scaleY(0.85f).setDuration(70).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            btnNext.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
                        }
                    }).start();
                    ServiceLocator.INSTANCE.getPlaybackController().next();
                } catch (Exception e) {
                    Log.e(TAG, "Error seeking next", e);
                }
            }
        });
        controlsRow.addView(btnNext);

        View spacerRight = new View(this);
        controlsRow.addView(spacerRight, new LinearLayout.LayoutParams(0, 1, 1f));

        // 5. Repeat Button (matching PlayerScreen icon, colors, and toast)
        btnRepeat = new ImageView(this);
        btnRepeat.setLayoutParams(new LinearLayout.LayoutParams(shuffleSize, shuffleSize));
        btnRepeat.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
        btnRepeat.setImageResource(R.drawable.ic_notif_repeat_all);
        btnRepeat.setColorFilter(Color.parseColor("#A0A0A5"));
        btnRepeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    btnRepeat.animate().scaleX(0.82f).scaleY(0.82f).setDuration(80).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            btnRepeat.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                        }
                    }).start();

                    PlaybackController controller = ServiceLocator.INSTANCE.getPlaybackController();
                    int nextMode = controller.cycleRepeat();
                    updateRepeatButton();
                    Toast.makeText(FloatingPillService.this,
                            nextMode == androidx.media3.common.Player.REPEAT_MODE_ONE ? "Repeating current song" : "Repeating queue",
                            Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Error cycling repeat", e);
                }
            }
        });
        controlsRow.addView(btnRepeat);

        expandedCardContent.addView(controlsRow);

        // ── Row 4: Volume Control Slider ─────────────────────────────────────────
        LinearLayout volumeRow = new LinearLayout(this);
        volumeRow.setOrientation(LinearLayout.HORIZONTAL);
        volumeRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(24));
        vLp.topMargin = dpToPx(5);
        volumeRow.setLayoutParams(vLp);

        btnVolumeMute = new ImageView(this);
        int volIconSize = dpToPx(24);
        btnVolumeMute.setLayoutParams(new LinearLayout.LayoutParams(volIconSize, volIconSize));
        btnVolumeMute.setPadding(dpToPx(3), dpToPx(3), dpToPx(3), dpToPx(3));
        btnVolumeMute.setImageResource(R.drawable.ic_volume_down);
        btnVolumeMute.setColorFilter(Color.parseColor("#A0A0A5"));
        btnVolumeMute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleVolumeMute();
            }
        });
        volumeRow.addView(btnVolumeMute);

        volumeScrubber = new VolumeScrubberView(this);
        LinearLayout.LayoutParams vSbLp = new LinearLayout.LayoutParams(0, dpToPx(22), 1f);
        vSbLp.setMarginStart(dpToPx(8));
        vSbLp.setMarginEnd(dpToPx(8));
        volumeScrubber.setLayoutParams(vSbLp);
        volumeScrubber.setOnVolumeChangeListener(new VolumeScrubberView.OnVolumeChangeListener() {
            @Override
            public void onVolumeChanged(int volume, boolean fromUser) {
                if (fromUser && audioManager != null) {
                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, volume, 0);
                    updateVolumeIcons(volume);
                }
            }
        });
        volumeRow.addView(volumeScrubber);

        btnVolumeMax = new ImageView(this);
        btnVolumeMax.setLayoutParams(new LinearLayout.LayoutParams(volIconSize, volIconSize));
        btnVolumeMax.setPadding(dpToPx(3), dpToPx(3), dpToPx(3), dpToPx(3));
        btnVolumeMax.setImageResource(R.drawable.ic_volume_up);
        btnVolumeMax.setColorFilter(Color.parseColor("#A0A0A5"));
        btnVolumeMax.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setVolumeMax();
            }
        });
        volumeRow.addView(btnVolumeMax);

        expandedCardContent.addView(volumeRow);

        // ── Row 5: Queue Carousel ─────────────────────────────────────────
        LinearLayout queueSection = new LinearLayout(this);
        queueSection.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams qSectionLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        qSectionLp.topMargin = dpToPx(8);
        queueSection.setLayoutParams(qSectionLp);

        LinearLayout queueHeader = new LinearLayout(this);
        queueHeader.setOrientation(LinearLayout.HORIZONTAL);
        queueHeader.setGravity(Gravity.CENTER_VERTICAL);
        queueHeader.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        tvQueueLabel = new TextView(this);
        tvQueueLabel.setText("UP NEXT");
        tvQueueLabel.setTextColor(Color.parseColor("#8E8E93"));
        tvQueueLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        tvQueueLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        queueHeader.addView(tvQueueLabel);

        tvQueueCount = new TextView(this);
        tvQueueCount.setText("");
        tvQueueCount.setTextColor(Color.parseColor("#636366"));
        tvQueueCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        tvQueueCount.setPadding(dpToPx(6), 0, 0, 0);
        queueHeader.addView(tvQueueCount);

        queueSection.addView(queueHeader);

        queueScrollView = new android.widget.HorizontalScrollView(this);
        queueScrollView.setHorizontalScrollBarEnabled(false);
        queueScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(76)
        );
        svLp.topMargin = dpToPx(4);
        queueScrollView.setLayoutParams(svLp);

        queueContainer = new LinearLayout(this);
        queueContainer.setOrientation(LinearLayout.HORIZONTAL);
        queueContainer.setGravity(Gravity.CENTER_VERTICAL);
        queueScrollView.addView(queueContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        queueSection.addView(queueScrollView);

        expandedCardContent.addView(queueSection);
    }

    private void expandCard() {
        if (isExpanded || rootContainer == null) return;
        isExpanded = true;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int islandWidth = dm.widthPixels - dpToPx(24);
        int islandHeight = dpToPx(295);
        int sbHeight = getStatusBarHeight();

        layoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        layoutParams.x = 0;
        layoutParams.y = sbHeight + dpToPx(4);
        layoutParams.width = islandWidth;
        layoutParams.height = islandHeight;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

        try {
            windowManager.updateViewLayout(rootContainer, layoutParams);
        } catch (Exception e) {
            Log.e(TAG, "Error updating window layout for expand", e);
        }

        expandedCardContainer.setAlpha(0f);
        expandedCardContainer.setScaleX(0.90f);
        expandedCardContainer.setScaleY(0.90f);
        expandedCardContainer.setVisibility(View.VISIBLE);

        miniPillView.animate()
                .alpha(0f)
                .scaleX(0.82f)
                .scaleY(0.82f)
                .setDuration(180)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        miniPillView.setVisibility(View.GONE);
                    }
                })
                .start();

        expandedCardContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(240)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        updateTimelineProgress();
        checkFavoriteStatus();
        updateShuffleButton();
        updateRepeatButton();
        updateVolumeSlider();
        populateQueueCarousel();
        mainHandler.post(progressUpdater);
    }

    /**
     * Closes the expanded island widget:
     * - If paused: smoothly vanishes completely, leaving no pill at the top.
     * - If playing: smoothly morphs back to the mini pill.
     */
    private void collapseOrDismiss() {
        if (!isExpanded || rootContainer == null) return;
        isExpanded = false;
        mainHandler.removeCallbacks(progressUpdater);

        if (!isPlaying) {
            expandedCardContainer.animate()
                    .alpha(0f)
                    .scaleX(0.85f)
                    .scaleY(0.85f)
                    .setDuration(180)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            expandedCardContainer.setVisibility(View.GONE);
                            if (miniPillView != null) {
                                miniPillView.setVisibility(View.GONE);
                            }
                            if (rootContainer != null) {
                                rootContainer.setVisibility(View.GONE);
                            }
                            resetLayoutParamsToMini();
                        }
                    })
                    .start();
        } else {
            collapseToPill();
        }
    }

    private void collapseToPill() {
        if (rootContainer == null) return;
        isExpanded = false;
        mainHandler.removeCallbacks(progressUpdater);

        miniPillView.setAlpha(0f);
        miniPillView.setScaleX(0.88f);
        miniPillView.setScaleY(0.88f);
        miniPillView.setVisibility(View.VISIBLE);

        expandedCardContainer.animate()
                .alpha(0f)
                .scaleX(0.88f)
                .scaleY(0.88f)
                .setDuration(180)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (!isExpanded) {
                            expandedCardContainer.setVisibility(View.GONE);
                            resetLayoutParamsToMini();
                        }
                    }
                })
                .start();

        miniPillView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        if (isPlaying && isScreenOn && miniEqualizerView != null) {
            miniEqualizerView.start();
        }
    }

    private void resetLayoutParamsToMini() {
        if (layoutParams == null || windowManager == null || rootContainer == null) return;
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        layoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        layoutParams.x = 0;
        layoutParams.y = getStatusBarHeight() + dpToPx(4);
        try {
            windowManager.updateViewLayout(rootContainer, layoutParams);
        } catch (Exception ignored) {}
    }

    private void updateContent(boolean songChanged) {
        if (rootContainer == null) return;

        updateVisibility();

        // 1. Equalizer animation state
        if (miniEqualizerView != null) {
            if (isPlaying && isScreenOn && !isAppInForeground && !isSessionDismissed && isFeatureEnabled(this)) {
                miniEqualizerView.start();
            } else {
                miniEqualizerView.stop();
            }
        }

        // 2. Play/pause button in expanded island
        if (btnPlayPause != null) {
            btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause_black : R.drawable.ic_play_black);
            btnPlayPause.setColorFilter(Color.WHITE);
        }

        // 3. Text info
        if (tvTitle != null && currentTitle != null && !currentTitle.isEmpty()) {
            tvTitle.setText(currentTitle);
        }
        if (tvArtist != null && currentArtist != null && !currentArtist.isEmpty()) {
            tvArtist.setText(currentArtist);
        }

        // 4. Favorite status
        if (songChanged) {
            checkFavoriteStatus();
        }

        // 5. Asynchronous artwork loading & frosted glass background blurring
        if (songChanged || cachedArtwork == null) {
            ioExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    Bitmap bmp = null;
                    try {
                        byte[] bytes = AudioArtworkFetcher.Companion.extractEmbeddedPicture(
                                currentFilePath,
                                currentUriString,
                                FloatingPillService.this
                        );
                        if (bytes != null && bytes.length > 0) {
                            bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        }
                    } catch (Throwable ignored) {}

                    cachedArtwork = bmp;
                    if (cachedArtwork != null) {
                        try {
                            Bitmap downscaled = Bitmap.createScaledBitmap(cachedArtwork, 80, 80, true);
                            blurredArtwork = fastBlur(downscaled, 20);
                        } catch (Throwable ignored) {
                            blurredArtwork = null;
                        }
                    } else {
                        blurredArtwork = null;
                    }

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (cachedArtwork != null) {
                                if (miniArtView != null) miniArtView.setImageBitmap(cachedArtwork);
                                if (expandedArtView != null) expandedArtView.setImageBitmap(cachedArtwork);

                                if (cardBackdropView != null) {
                                    cardBackdropView.setImageBitmap(blurredArtwork != null ? blurredArtwork : cachedArtwork);
                                    cardBackdropView.setVisibility(View.VISIBLE);
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        cardBackdropView.setRenderEffect(
                                                RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP)
                                        );
                                    }
                                }
                                if (cardScrimView != null) {
                                    GradientDrawable scrim = new GradientDrawable(
                                            GradientDrawable.Orientation.TOP_BOTTOM,
                                            new int[]{Color.argb(165, 20, 20, 20), Color.argb(225, 10, 10, 10)}
                                    );
                                    cardScrimView.setBackground(scrim);
                                }
                            } else {
                                if (miniArtView != null) miniArtView.setImageResource(R.drawable.ic_music_notification);
                                if (expandedArtView != null) expandedArtView.setImageResource(R.drawable.ic_music_notification);
                                if (cardBackdropView != null) {
                                    cardBackdropView.setImageDrawable(null);
                                    cardBackdropView.setVisibility(View.GONE);
                                }
                                if (cardScrimView != null) {
                                    cardScrimView.setBackgroundColor(Color.parseColor("#000000"));
                                }
                            }
                        }
                    });
                }
            });
        }

        if (isExpanded) {
            updateTimelineProgress();
            updateShuffleButton();
            updateRepeatButton();
            if (songChanged) {
                populateQueueCarousel();
            }
        }
    }

    private void updateTimelineProgress() {
        if (!isExpanded || isUserSeeking) return;

        try {
            PlaybackController controller = ServiceLocator.INSTANCE.getPlaybackController();
            long pos = controller.getCurrentPosition();
            long dur = controller.getDuration();

            if (tvCurrentTime != null) tvCurrentTime.setText(formatTime(pos));
            if (tvTotalTime != null) tvTotalTime.setText(formatTime(dur));

            if (scrubberView != null && dur > 0) {
                scrubberView.setProgress(pos, dur);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating timeline progress", e);
        }
    }

    private void checkFavoriteStatus() {
        if (currentMediaId <= 0) return;

        try {
            PlaybackController controller = ServiceLocator.INSTANCE.getPlaybackController();
            controller.checkIsFavorite(currentMediaId, new kotlin.jvm.functions.Function1<Boolean, kotlin.Unit>() {
                @Override
                public kotlin.Unit invoke(Boolean isFav) {
                    isCurrentSongFavorite = Boolean.TRUE.equals(isFav);
                    updateFavoriteIcon();
                    return kotlin.Unit.INSTANCE;
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error checking favorite status", e);
        }
    }

    private void updateFavoriteIcon() {
        if (btnFavorite != null) {
            btnFavorite.setImageResource(
                    isCurrentSongFavorite ? R.drawable.ic_notif_favorite_filled : R.drawable.ic_notif_favorite_border
            );
            if (isCurrentSongFavorite) {
                btnFavorite.setColorFilter(Color.parseColor("#FF3B30"));
            } else {
                btnFavorite.setColorFilter(Color.WHITE);
            }
        }
    }

    private void updateShuffleButton() {
        if (btnShuffle == null) return;
        try {
            PlaybackController controller = ServiceLocator.INSTANCE.getPlaybackController();
            boolean shuffleOn = controller.isShuffleEnabled();
            if (shuffleOn) {
                btnShuffle.setImageResource(R.drawable.ic_notif_shuffle_on);
                btnShuffle.setColorFilter(Color.parseColor("#26B8FF"));
            } else {
                btnShuffle.setImageResource(R.drawable.ic_notif_shuffle_off);
                btnShuffle.setColorFilter(Color.parseColor("#A0A0A5"));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating shuffle button", e);
        }
    }

    private void updateRepeatButton() {
        if (btnRepeat == null) return;
        try {
            PlaybackController controller = ServiceLocator.INSTANCE.getPlaybackController();
            int mode = controller.getRepeatMode();
            if (mode == androidx.media3.common.Player.REPEAT_MODE_ONE) {
                btnRepeat.setImageResource(R.drawable.ic_notif_repeat_one);
                btnRepeat.setColorFilter(Color.parseColor("#26B8FF"));
            } else {
                btnRepeat.setImageResource(R.drawable.ic_notif_repeat_all);
                btnRepeat.setColorFilter(Color.parseColor("#A0A0A5"));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating repeat button", e);
        }
    }

    private void updateVolumeSlider() {
        if (audioManager == null) {
            audioManager = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
        if (audioManager == null || volumeScrubber == null) return;
        try {
            int maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
            int curVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
            volumeScrubber.setMaxVolume(maxVol);
            volumeScrubber.setVolume(curVol);
            updateVolumeIcons(curVol);
        } catch (Exception e) {
            Log.e(TAG, "Error updating volume slider", e);
        }
    }

    private void updateVolumeIcons(int currentVol) {
        if (btnVolumeMute != null) {
            if (currentVol == 0) {
                btnVolumeMute.setImageResource(R.drawable.ic_volume_mute);
                btnVolumeMute.setColorFilter(Color.parseColor("#FF3B30"));
            } else {
                btnVolumeMute.setImageResource(R.drawable.ic_volume_down);
                btnVolumeMute.setColorFilter(Color.parseColor("#A0A0A5"));
            }
        }
        if (btnVolumeMax != null) {
            btnVolumeMax.setColorFilter(Color.parseColor("#A0A0A5"));
        }
    }

    private int preMuteVolume = -1;
    private void toggleVolumeMute() {
        if (audioManager == null) {
            audioManager = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
        if (audioManager == null) return;
        try {
            int curVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
            int maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
            if (curVol > 0) {
                preMuteVolume = curVol;
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 0, 0);
                if (volumeScrubber != null) volumeScrubber.setVolume(0);
                updateVolumeIcons(0);
            } else {
                int restoreVol = (preMuteVolume > 0) ? preMuteVolume : Math.max(1, maxVol / 3);
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, restoreVol, 0);
                if (volumeScrubber != null) volumeScrubber.setVolume(restoreVol);
                updateVolumeIcons(restoreVol);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error toggling mute", e);
        }
    }

    private void setVolumeMax() {
        if (audioManager == null) {
            audioManager = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
        if (audioManager == null) return;
        try {
            int maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, maxVol, 0);
            if (volumeScrubber != null) volumeScrubber.setVolume(maxVol);
            updateVolumeIcons(maxVol);
        } catch (Exception e) {
            Log.e(TAG, "Error setting max volume", e);
        }
    }

    private void populateQueueCarousel() {
        if (queueContainer == null) return;
        java.util.List<PlaybackController.QueueSongItem> queueItems = java.util.Collections.emptyList();
        try {
            PlaybackController controller = ServiceLocator.INSTANCE.getPlaybackController();
            queueItems = controller.getQueueItems();
        } catch (Exception e) {
            Log.e(TAG, "Error fetching queue items", e);
        }

        if (tvQueueCount != null) {
            tvQueueCount.setText(queueItems.isEmpty() ? "" : "(" + queueItems.size() + ")");
        }

        queueContainer.removeAllViews();
        if (queueItems.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("Queue is empty");
            emptyTv.setTextColor(Color.parseColor("#636366"));
            emptyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            emptyTv.setPadding(dpToPx(4), dpToPx(8), 0, 0);
            queueContainer.addView(emptyTv);
            return;
        }

        View currentSelectedView = null;
        for (int i = 0; i < queueItems.size(); i++) {
            final PlaybackController.QueueSongItem item = queueItems.get(i);
            final int itemIndex = item.getIndex();

            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(dpToPx(56), LinearLayout.LayoutParams.MATCH_PARENT);
            itemLp.setMarginEnd(dpToPx(8));
            itemLayout.setLayoutParams(itemLp);

            // Thumbnail container with rounded outline
            FrameLayout thumbContainer = new FrameLayout(this);
            int thumbSize = dpToPx(44);
            LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(thumbSize, thumbSize);
            thumbContainer.setLayoutParams(tcLp);

            GradientDrawable thumbBg = new GradientDrawable();
            thumbBg.setShape(GradientDrawable.RECTANGLE);
            thumbBg.setCornerRadius(dpToPx(8));
            thumbBg.setColor(Color.parseColor("#1C1C1E"));
            if (item.isCurrent()) {
                thumbBg.setStroke(dpToPx(2), Color.parseColor("#26B8FF"));
            }
            thumbContainer.setBackground(thumbBg);
            thumbContainer.setClipToOutline(true);

            ImageView ivArt = new ImageView(this);
            ivArt.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            ivArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
            ivArt.setImageResource(R.drawable.ic_music_notification);
            thumbContainer.addView(ivArt);

            // Load art from cache or async
            final long itemId = item.getId();
            Bitmap cached = queueThumbCache.get(itemId);
            if (cached != null) {
                ivArt.setImageBitmap(cached);
            } else {
                final String fPath = item.getFilePath();
                final String uStr = item.getUri();
                ioExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            byte[] bytes = AudioArtworkFetcher.Companion.extractEmbeddedPicture(
                                    fPath != null ? fPath : "",
                                    uStr != null ? uStr : "",
                                    FloatingPillService.this
                            );
                            if (bytes != null && bytes.length > 0) {
                                Bitmap raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                if (raw != null) {
                                    final Bitmap scaled = Bitmap.createScaledBitmap(raw, 96, 96, true);
                                    queueThumbCache.put(itemId, scaled);
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            ivArt.setImageBitmap(scaled);
                                        }
                                    });
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                });
            }

            itemLayout.addView(thumbContainer);

            // Song title
            TextView tvSongTitle = new TextView(this);
            tvSongTitle.setText(item.getTitle());
            tvSongTitle.setTextColor(item.isCurrent() ? Color.parseColor("#26B8FF") : Color.WHITE);
            tvSongTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
            tvSongTitle.setSingleLine(true);
            tvSongTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvSongTitle.setGravity(Gravity.CENTER_HORIZONTAL);
            tvSongTitle.setPadding(0, dpToPx(2), 0, 0);
            itemLayout.addView(tvSongTitle);

            // Song artist
            TextView tvSongArtist = new TextView(this);
            tvSongArtist.setText(item.getArtist());
            tvSongArtist.setTextColor(Color.parseColor("#8E8E93"));
            tvSongArtist.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f);
            tvSongArtist.setSingleLine(true);
            tvSongArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvSongArtist.setGravity(Gravity.CENTER_HORIZONTAL);
            itemLayout.addView(tvSongArtist);

            if (item.isCurrent()) {
                currentSelectedView = itemLayout;
            }

            // Click to play from queue
            itemLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        itemLayout.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                itemLayout.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                            }
                        }).start();

                        ServiceLocator.INSTANCE.getPlaybackController().playQueueItem(itemIndex);
                    } catch (Exception e) {
                        Log.e(TAG, "Error playing item " + itemIndex + " from queue", e);
                    }
                }
            });

            queueContainer.addView(itemLayout);
        }

        if (currentSelectedView != null && queueScrollView != null) {
            final View targetView = currentSelectedView;
            queueScrollView.post(new Runnable() {
                @Override
                public void run() {
                    int scrollX = targetView.getLeft() - dpToPx(16);
                    queueScrollView.smoothScrollTo(Math.max(0, scrollX), 0);
                }
            });
        }
    }

    private void launchPlayerScreen() {
        try {
            Intent launchIntent = new Intent(this, MainActivity.class);
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            launchIntent.setData(Uri.parse("tidemusic://player"));
            startActivity(launchIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error launching player from dynamic island", e);
        }
    }

    private void removeOverlay() {
        mainHandler.removeCallbacks(progressUpdater);
        if (miniEqualizerView != null) {
            miniEqualizerView.stop();
        }
        if (rootContainer != null && windowManager != null) {
            try {
                windowManager.removeView(rootContainer);
            } catch (Exception ignored) {}
            rootContainer = null;
        }
    }

    @Override
    public void onDestroy() {
        if (sInstance == this) {
            sInstance = null;
        }
        try {
            unregisterReceiver(screenStateReceiver);
        } catch (Exception ignored) {}
        removeOverlay();
        ioExecutor.shutdown();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private int dpToPx(float dp) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, dm);
    }

    private int dpToPx(int dp) {
        return dpToPx((float) dp);
    }

    private static String formatTime(long ms) {
        if (ms <= 0) return "00:00";
        long totalSecs = ms / 1000;
        long mins = totalSecs / 60;
        long secs = totalSecs % 60;
        return String.format(Locale.US, "%02d:%02d", mins, secs);
    }

    /**
     * Ultra-fast pure Java StackBlur algorithm by Mario Klingemann.
     */
    public static Bitmap fastBlur(Bitmap sentBitmap, int radius) {
        if (sentBitmap == null || radius < 1) return null;
        try {
            Bitmap bitmap = sentBitmap.copy(Bitmap.Config.ARGB_8888, true);
            if (bitmap == null) return null;

            int w = bitmap.getWidth();
            int h = bitmap.getHeight();

            int[] pix = new int[w * h];
            bitmap.getPixels(pix, 0, w, 0, 0, w, h);

            int wm = w - 1;
            int hm = h - 1;
            int wh = w * h;
            int div = radius + radius + 1;

            int[] r = new int[wh];
            int[] g = new int[wh];
            int[] b = new int[wh];
            int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
            int[] vmin = new int[Math.max(w, h)];

            int divsum = (div + 1) >> 1;
            divsum *= divsum;
            int[] dv = new int[256 * divsum];
            for (i = 0; i < 256 * divsum; i++) {
                dv[i] = (i / divsum);
            }

            yw = yi = 0;

            int[][] stack = new int[div][3];
            int stackpointer;
            int stackstart;
            int[] sir;
            int rbs;
            int r1 = radius + 1;
            int routsum, goutsum, boutsum;
            int rinsum, ginsum, binsum;

            for (y = 0; y < h; y++) {
                rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
                for (i = -radius; i <= radius; i++) {
                    p = pix[yi + Math.min(wm, Math.max(i, 0))];
                    sir = stack[i + radius];
                    sir[0] = (p & 0xff0000) >> 16;
                    sir[1] = (p & 0x00ff00) >> 8;
                    sir[2] = (p & 0x0000ff);
                    rbs = r1 - Math.abs(i);
                    rsum += sir[0] * rbs;
                    gsum += sir[1] * rbs;
                    bsum += sir[2] * rbs;
                    if (i > 0) {
                        rinsum += sir[0];
                        ginsum += sir[1];
                        binsum += sir[2];
                    } else {
                        routsum += sir[0];
                        goutsum += sir[1];
                        boutsum += sir[2];
                    }
                }
                stackpointer = radius;

                for (x = 0; x < w; x++) {
                    r[yi] = dv[rsum];
                    g[yi] = dv[gsum];
                    b[yi] = dv[bsum];

                    rsum -= routsum;
                    gsum -= goutsum;
                    bsum -= boutsum;

                    stackstart = stackpointer - radius + div;
                    sir = stack[stackstart % div];

                    routsum -= sir[0];
                    goutsum -= sir[1];
                    boutsum -= sir[2];

                    if (y == 0) {
                        vmin[x] = Math.min(x + radius + 1, wm);
                    }
                    p = pix[yw + vmin[x]];

                    sir[0] = (p & 0xff0000) >> 16;
                    sir[1] = (p & 0x00ff00) >> 8;
                    sir[2] = (p & 0x0000ff);

                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];

                    rsum += rinsum;
                    gsum += ginsum;
                    bsum += binsum;

                    stackpointer = (stackpointer + 1) % div;
                    sir = stack[(stackpointer) % div];

                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];

                    rinsum -= sir[0];
                    ginsum -= sir[1];
                    binsum -= sir[2];

                    yi++;
                }
                yw += w;
            }
            for (x = 0; x < w; x++) {
                rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
                yp = -radius * w;
                for (i = -radius; i <= radius; i++) {
                    yi = Math.max(0, yp) + x;

                    sir = stack[i + radius];

                    sir[0] = r[yi];
                    sir[1] = g[yi];
                    sir[2] = b[yi];

                    rbs = r1 - Math.abs(i);

                    rsum += r[yi] * rbs;
                    gsum += g[yi] * rbs;
                    bsum += b[yi] * rbs;

                    if (i > 0) {
                        rinsum += sir[0];
                        ginsum += sir[1];
                        binsum += sir[2];
                    } else {
                        routsum += sir[0];
                        goutsum += sir[1];
                        boutsum += sir[2];
                    }

                    if (i < hm) {
                        yp += w;
                    }
                }
                yi = x;
                stackpointer = radius;
                for (y = 0; y < h; y++) {
                    pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];

                    rsum -= routsum;
                    gsum -= goutsum;
                    bsum -= boutsum;

                    stackstart = stackpointer - radius + div;
                    sir = stack[stackstart % div];

                    routsum -= sir[0];
                    goutsum -= sir[1];
                    boutsum -= sir[2];

                    if (x == 0) {
                        vmin[y] = Math.min(y + r1, hm) * w;
                    }
                    p = x + vmin[y];

                    sir[0] = r[p];
                    sir[1] = g[p];
                    sir[2] = b[p];

                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];

                    rsum += rinsum;
                    gsum += ginsum;
                    bsum += binsum;

                    stackpointer = (stackpointer + 1) % div;
                    sir = stack[stackpointer % div];

                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];

                    rinsum -= sir[0];
                    ginsum -= sir[1];
                    binsum -= sir[2];

                    yi += w;
                }
            }

            bitmap.setPixels(pix, 0, w, 0, 0, w, h);
            return bitmap;
        } catch (Throwable t) {
            Log.e("FloatingPillService", "fastBlur error", t);
            return null;
        }
    }

    /**
     * Slower, liquid-smooth 6-bar audio visualizer in darker refined grey (#555558).
     *
     * Features:
     * - Multi-harmonic superimposed audio frequency bands with organic, graceful tempo.
     * - Fluid exponential spring dampening for liquid-smooth transitions without rough/fast jumps.
     * - Symmetrical expansion from vertical center (up and down).
     * - Rests neatly at subtle center dots when paused.
     */
    public static class SixBarEqualizerView extends View {

        private static final int BAR_COUNT = 6;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float[] currentHeights = new float[]{0.18f, 0.18f, 0.18f, 0.18f, 0.18f, 0.18f};

        // Gentle, musical, slower tempo frequencies across the 6 frequency bands
        private final float[] f1 = new float[]{0.80f, 1.20f, 1.65f, 1.40f, 1.85f, 2.15f};
        private final float[] f2 = new float[]{0.40f, 0.70f, 1.05f, 1.95f, 1.20f, 1.55f};
        private final float[] f3 = new float[]{1.25f, 1.65f, 0.60f, 0.85f, 2.35f, 2.65f};
        private final float[] phase1 = new float[]{0.0f, 1.2f, 2.4f, 0.8f, 1.9f, 3.1f};
        private final float[] phase2 = new float[]{1.7f, 0.5f, 2.9f, 3.6f, 0.3f, 1.4f};
        private final float[] phase3 = new float[]{2.1f, 3.8f, 1.1f, 0.4f, 2.7f, 0.9f};
        private final float[] minH = new float[]{0.18f, 0.22f, 0.20f, 0.22f, 0.18f, 0.16f};
        private final float[] maxH = new float[]{0.88f, 1.00f, 0.92f, 0.98f, 0.85f, 0.75f};

        private ValueAnimator animator;
        private long startTimeMs = 0L;

        public SixBarEqualizerView(Context context) {
            super(context);
            paint.setColor(ACCENT_GREY);
            paint.setStyle(Paint.Style.FILL);
            setupAnimator();
        }

        private void setupAnimator() {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(1200);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.RESTART);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    long now = System.currentTimeMillis();
                    if (startTimeMs == 0) startTimeMs = now;
                    float t = (now - startTimeMs) / 1000f;

                    for (int i = 0; i < BAR_COUNT; i++) {
                        float w1 = (float) Math.sin(t * f1[i] * 2 * Math.PI + phase1[i]);
                        float w2 = (float) Math.sin(t * f2[i] * 2 * Math.PI + phase2[i]);
                        float w3 = (float) Math.cos(t * f3[i] * 2 * Math.PI + phase3[i]);

                        float blended = (w1 * 0.52f) + (w2 * 0.32f) + (w3 * 0.16f);
                        float norm = Math.max(0f, Math.min(1f, (blended + 1f) * 0.5f));
                        float punch = (float) Math.pow(norm, 1.35);

                        float target = minH[i] + punch * (maxH[i] - minH[i]);
                        // Smooth exponential spring dampening for liquid-smooth flow
                        currentHeights[i] += (target - currentHeights[i]) * 0.18f;
                    }
                    invalidate();
                }
            });
        }

        public void start() {
            if (animator != null && !animator.isRunning()) {
                startTimeMs = System.currentTimeMillis();
                animator.start();
            }
        }

        public void stop() {
            if (animator != null && animator.isRunning()) {
                animator.cancel();
            }
            for (int i = 0; i < BAR_COUNT; i++) {
                currentHeights[i] = 0.18f;
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) return;

            float spacing = dpToPx(1.3f);
            float totalSpacing = (BAR_COUNT - 1) * spacing;
            float barWidth = Math.max(dpToPx(1.6f), (width - totalSpacing) / BAR_COUNT);
            float cornerRadius = barWidth / 2f;
            float centerY = height / 2f;

            float currentX = 0f;
            for (int i = 0; i < BAR_COUNT; i++) {
                float barHeight = Math.max(dpToPx(2.2f), height * currentHeights[i]);
                float halfHeight = barHeight / 2f;
                // Expands symmetrically from the vertical center
                rect.set(currentX, centerY - halfHeight, currentX + barWidth, centerY + halfHeight);
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
                currentX += barWidth + spacing;
            }
        }

        private float dpToPx(float dp) {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
        }

        @Override
        protected void onDetachedFromWindow() {
            stop();
            super.onDetachedFromWindow();
        }
    }

    /**
     * Professional Canvas scrubber view.
     */
    public static class ProfessionalScrubberView extends View {

        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF trackRect = new RectF();

        private long maxDuration = 1000L;
        private long currentProgress = 0L;
        private boolean isDragging = false;
        private OnScrubListener scrubListener;

        public interface OnScrubListener {
            void onProgressChanged(long progressMs, boolean fromUser);
            void onStartTracking();
            void onStopTracking(long progressMs);
        }

        public ProfessionalScrubberView(Context context) {
            super(context);
            trackPaint.setColor(0x38FFFFFF);
            trackPaint.setStyle(Paint.Style.FILL);

            progressPaint.setColor(0xFFFFFFFF);
            progressPaint.setStyle(Paint.Style.FILL);

            thumbPaint.setColor(0xFFFFFFFF);
            thumbPaint.setStyle(Paint.Style.FILL);
        }

        public void setOnScrubListener(OnScrubListener listener) {
            this.scrubListener = listener;
        }

        public void setProgress(long progressMs, long durationMs) {
            if (!isDragging) {
                this.currentProgress = Math.max(0L, progressMs);
                this.maxDuration = Math.max(1L, durationMs);
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) return;

            float centerY = height / 2f;
            float trackHeight = dpToPx(2.5f);
            float trackCorner = trackHeight / 2f;
            float thumbRadius = isDragging ? dpToPx(6.5f) : dpToPx(4.5f);

            float left = thumbRadius;
            float right = width - thumbRadius;
            float usableWidth = Math.max(1f, right - left);

            // Inactive track
            trackRect.set(left, centerY - (trackHeight / 2f), right, centerY + (trackHeight / 2f));
            canvas.drawRoundRect(trackRect, trackCorner, trackCorner, trackPaint);

            // Active progress track
            float fraction = (float) currentProgress / (float) maxDuration;
            fraction = Math.max(0f, Math.min(1f, fraction));
            float progressX = left + (usableWidth * fraction);

            trackRect.set(left, centerY - (trackHeight / 2f), progressX, centerY + (trackHeight / 2f));
            canvas.drawRoundRect(trackRect, trackCorner, trackCorner, progressPaint);

            // Draggable thumb dot
            canvas.drawCircle(progressX, centerY, thumbRadius, thumbPaint);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float thumbRadius = isDragging ? dpToPx(6.5f) : dpToPx(4.5f);
            float left = thumbRadius;
            float right = getWidth() - thumbRadius;
            float usableWidth = Math.max(1f, right - left);

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isDragging = true;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    if (scrubListener != null) scrubListener.onStartTracking();
                    updateFromTouch(event.getX(), left, usableWidth);
                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    updateFromTouch(event.getX(), left, usableWidth);
                    invalidate();
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    updateFromTouch(event.getX(), left, usableWidth);
                    invalidate();
                    if (scrubListener != null) scrubListener.onStopTracking(currentProgress);
                    return true;
            }
            return super.onTouchEvent(event);
        }

        private void updateFromTouch(float touchX, float left, float usableWidth) {
            float fraction = (touchX - left) / usableWidth;
            fraction = Math.max(0f, Math.min(1f, fraction));
            currentProgress = (long) (fraction * maxDuration);
            if (scrubListener != null) {
                scrubListener.onProgressChanged(currentProgress, true);
            }
        }

        private float dpToPx(float dp) {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
        }
    }

    /**
     * Sleek horizontal volume scrubber with smooth dragging and rounded pill track.
     */
    public static class VolumeScrubberView extends View {
        public interface OnVolumeChangeListener {
            void onVolumeChanged(int volume, boolean fromUser);
        }

        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF trackRect = new RectF();

        private int maxVolume = 15;
        private int currentVolume = 7;
        private boolean isDragging = false;
        private OnVolumeChangeListener volumeChangeListener;

        public VolumeScrubberView(Context context) {
            super(context);
            trackPaint.setColor(0x28FFFFFF);
            trackPaint.setStyle(Paint.Style.FILL);

            progressPaint.setColor(Color.parseColor("#26B8FF"));
            progressPaint.setStyle(Paint.Style.FILL);

            thumbPaint.setColor(Color.WHITE);
            thumbPaint.setStyle(Paint.Style.FILL);
        }

        public void setOnVolumeChangeListener(OnVolumeChangeListener listener) {
            this.volumeChangeListener = listener;
        }

        public void setMaxVolume(int max) {
            this.maxVolume = Math.max(1, max);
            invalidate();
        }

        public void setVolume(int volume) {
            if (!isDragging) {
                this.currentVolume = Math.max(0, Math.min(volume, maxVolume));
                invalidate();
            }
        }

        public int getVolume() {
            return currentVolume;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) return;

            float centerY = height / 2f;
            float trackHeight = dpToPx(4f);
            float trackCorner = trackHeight / 2f;
            float thumbRadius = isDragging ? dpToPx(6f) : dpToPx(4.5f);

            float left = thumbRadius;
            float right = width - thumbRadius;
            float usableWidth = Math.max(1f, right - left);

            // Inactive track
            trackRect.set(left, centerY - (trackHeight / 2f), right, centerY + (trackHeight / 2f));
            canvas.drawRoundRect(trackRect, trackCorner, trackCorner, trackPaint);

            // Active progress
            float fraction = (float) currentVolume / (float) maxVolume;
            fraction = Math.max(0f, Math.min(1f, fraction));
            float progressX = left + (usableWidth * fraction);

            trackRect.set(left, centerY - (trackHeight / 2f), progressX, centerY + (trackHeight / 2f));
            canvas.drawRoundRect(trackRect, trackCorner, trackCorner, progressPaint);

            // Thumb
            canvas.drawCircle(progressX, centerY, thumbRadius, thumbPaint);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float thumbRadius = isDragging ? dpToPx(6f) : dpToPx(4.5f);
            float left = thumbRadius;
            float right = getWidth() - thumbRadius;
            float usableWidth = Math.max(1f, right - left);

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isDragging = true;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    updateFromTouch(event.getX(), left, usableWidth);
                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    updateFromTouch(event.getX(), left, usableWidth);
                    invalidate();
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    updateFromTouch(event.getX(), left, usableWidth);
                    invalidate();
                    return true;
            }
            return super.onTouchEvent(event);
        }

        private void updateFromTouch(float touchX, float left, float usableWidth) {
            float fraction = (touchX - left) / usableWidth;
            fraction = Math.max(0f, Math.min(1f, fraction));
            int newVol = Math.round(fraction * maxVolume);
            if (newVol != currentVolume) {
                currentVolume = newVol;
                if (volumeChangeListener != null) {
                    volumeChangeListener.onVolumeChanged(currentVolume, true);
                }
            }
        }

        private float dpToPx(float dp) {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
        }
    }
}
