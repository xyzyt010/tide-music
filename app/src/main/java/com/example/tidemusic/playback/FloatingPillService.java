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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.tidemusic.MainActivity;
import com.example.tidemusic.R;
import com.example.tidemusic.di.ServiceLocator;
import com.example.tidemusic.util.AudioArtworkFetcher;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pure Java system overlay service providing the Dynamic Island capsule widget.
 *
 * Supports:
 * - Collapsed mini black capsule around the top camera cutout with 7 animated green equalizer bars.
 * - Tap to expand into a rich floating music player card with seekable scrubber, playback controls & favorite toggle.
 * - Swipe right-to-left to dismiss/close.
 * - Tap outside or swipe up to collapse back to the mini capsule.
 * - Tap artwork/title to open full Tide Music player.
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

    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;

    private static final String PREFS_NAME = "floating_pill_prefs";
    private static final String KEY_PILL_X = "pill_x";
    private static final String KEY_PILL_Y = "pill_y";
    private static final String KEY_HAS_CUSTOM_POS = "has_custom_pos";

    // View Hierarchy
    private FrameLayout rootContainer;
    private LinearLayout miniPillView;
    private ImageView miniArtView;
    private SevenBarEqualizerView miniEqualizerView;

    private LinearLayout expandedCardView;
    private GradientDrawable expandedCardBg;
    private ImageView expandedArtView;
    private TextView tvTitle;
    private TextView tvArtist;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private SeekBar seekBar;
    private ImageView btnFavorite;
    private ImageView btnPlayPause;

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

    private void saveCustomPosition(int x, int y) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_PILL_X, x)
                .putInt(KEY_PILL_Y, y)
                .putBoolean(KEY_HAS_CUSTOM_POS, true)
                .apply();
    }

    private void resetCustomPosition() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PILL_X)
                .remove(KEY_PILL_Y)
                .putBoolean(KEY_HAS_CUSTOM_POS, false)
                .apply();
    }

    private boolean hasCustomPosition() {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_HAS_CUSTOM_POS, false);
    }

    private int getSavedPillX() {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_PILL_X, 0);
    }

    private int getSavedPillY() {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_PILL_Y, getStatusBarHeight() + dpToPx(4));
    }

    private int extractArtworkTint(@Nullable Bitmap bitmap) {
        if (bitmap == null) return Color.parseColor("#141414");
        try {
            int cx = bitmap.getWidth() / 2;
            int cy = bitmap.getHeight() / 2;
            int pixel = bitmap.getPixel(cx, cy);
            float[] hsv = new float[3];
            Color.colorToHSV(pixel, hsv);
            hsv[1] = Math.min(hsv[1], 0.55f);
            hsv[2] = 0.14f;
            return Color.HSVToColor(hsv);
        } catch (Exception e) {
            return Color.parseColor("#141414");
        }
    }

    public static void showOrUpdate(Context context, @Nullable String filePath, @Nullable String uriString, boolean isPlaying) {
        showOrUpdate(context, filePath, uriString, null, null, -1L, isPlaying);
    }

    public static void showOrUpdate(Context context, @Nullable String filePath, @Nullable String uriString,
                                    @Nullable String title, @Nullable String artist, long mediaId, boolean isPlaying) {
        if (!Settings.canDrawOverlays(context)) {
            Log.d(TAG, "Cannot draw overlays: permission not granted");
            return;
        }

        try {
            Intent intent = new Intent(context, FloatingPillService.class);
            if (isPlaying) {
                intent.setAction(ACTION_UPDATE);
                intent.putExtra(EXTRA_FILE_PATH, filePath != null ? filePath : "");
                intent.putExtra(EXTRA_URI, uriString != null ? uriString : "");
                intent.putExtra(EXTRA_TITLE, title != null ? title : "");
                intent.putExtra(EXTRA_ARTIST, artist != null ? artist : "");
                intent.putExtra(EXTRA_MEDIA_ID, mediaId);
                intent.putExtra(EXTRA_IS_PLAYING, true);
                context.startService(intent);
            } else {
                intent.setAction(ACTION_STOP);
                context.startService(intent);
            }
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
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            removeOverlay();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_UPDATE.equals(intent.getAction())) {
            String newFilePath = intent.getStringExtra(EXTRA_FILE_PATH);
            String newUriString = intent.getStringExtra(EXTRA_URI);
            currentTitle = intent.getStringExtra(EXTRA_TITLE);
            currentArtist = intent.getStringExtra(EXTRA_ARTIST);
            long newMediaId = intent.getLongExtra(EXTRA_MEDIA_ID, -1L);
            isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false);

            boolean songChanged = (newMediaId != currentMediaId) || (!newFilePath.equals(currentFilePath));
            currentFilePath = newFilePath;
            currentUriString = newUriString;
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

        if (hasCustomPosition()) {
            layoutParams.gravity = Gravity.TOP | Gravity.START;
            layoutParams.x = getSavedPillX();
            layoutParams.y = getSavedPillY();
        } else {
            layoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            layoutParams.x = 0;
            // Positioned cleanly below the status bar so status bar icons never overlap!
            layoutParams.y = getStatusBarHeight() + dpToPx(4);
        }

        // Crucial for modern Android 9-16 punch hole and camera cutout support
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }

        rootContainer = new FrameLayout(this);
        rootContainer.setClipChildren(false);
        rootContainer.setClipToPadding(false);

        // Outside touch & swipe detection
        rootContainer.setOnTouchListener(new View.OnTouchListener() {
            private float downY, downX;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    if (isExpanded) {
                        collapseToPill();
                        return true;
                    }
                }
                if (isExpanded) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            downY = event.getRawY();
                            downX = event.getRawX();
                            break;
                        case MotionEvent.ACTION_UP:
                            float dy = event.getRawY() - downY;
                            float dx = event.getRawX() - downX;
                            if (dy < -dpToPx(40) || dx < -dpToPx(60)) {
                                collapseToPill();
                                return true;
                            }
                            break;
                    }
                }
                return false;
            }
        });

        // 1. Build Collapsed Mini Pill View
        buildMiniPillView();
        rootContainer.addView(miniPillView);

        // 2. Build Expanded Card View
        buildExpandedCardView();
        expandedCardView.setVisibility(View.GONE);
        rootContainer.addView(expandedCardView);

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
        miniPillView.setPadding(dpToPx(8), dpToPx(4), dpToPx(9), dpToPx(4));

        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setShape(GradientDrawable.RECTANGLE);
        pillBg.setCornerRadius(dpToPx(16));
        pillBg.setColor(Color.parseColor("#000000"));
        pillBg.setStroke(dpToPx(1), Color.parseColor("#1F1F1F"));
        miniPillView.setBackground(pillBg);

        // Thumbnail artwork
        miniArtView = new ImageView(this);
        int artSize = dpToPx(22);
        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(artSize, artSize);
        artLp.setMarginEnd(dpToPx(7));
        miniArtView.setLayoutParams(artLp);
        miniArtView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniArtView.setImageResource(R.drawable.ic_music_notification);

        GradientDrawable artBg = new GradientDrawable();
        artBg.setShape(GradientDrawable.RECTANGLE);
        artBg.setCornerRadius(dpToPx(5));
        artBg.setColor(Color.parseColor("#181818"));
        miniArtView.setBackground(artBg);
        miniArtView.setClipToOutline(true);
        miniPillView.addView(miniArtView);

        // 4 thin animated equalizer bars matching Fluid Cloud
        miniEqualizerView = new SevenBarEqualizerView(this);
        LinearLayout.LayoutParams eqLp = new LinearLayout.LayoutParams(dpToPx(16), dpToPx(13));
        miniEqualizerView.setLayoutParams(eqLp);
        miniPillView.addView(miniEqualizerView);

        // Interactive Gestures on Mini Pill: Tap to Expand, Double Tap to Reset, Drag with Position Memory, Swipe Left to Dismiss
        miniPillView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long touchDownTime;
            private long lastTapTime = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchDownTime = System.currentTimeMillis();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        if (Math.abs(dx) > dpToPx(6) || Math.abs(dy) > dpToPx(6)) {
                            layoutParams.gravity = Gravity.TOP | Gravity.START;
                            layoutParams.x = initialX + (int) dx;
                            layoutParams.y = Math.max(0, initialY + (int) dy);
                            if (windowManager != null && rootContainer != null) {
                                windowManager.updateViewLayout(rootContainer, layoutParams);
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        float totalDx = event.getRawX() - initialTouchX;
                        float totalDy = event.getRawY() - initialTouchY;
                        long elapsed = System.currentTimeMillis() - touchDownTime;

                        // Check swipe right-to-left dismissal first
                        if (totalDx < -dpToPx(50) && Math.abs(totalDy) < dpToPx(40)) {
                            hide(FloatingPillService.this);
                            return true;
                        }

                        // Drag reposition completed: persist coordinates if dragged significantly
                        if (Math.abs(totalDx) > dpToPx(12) || Math.abs(totalDy) > dpToPx(12)) {
                            saveCustomPosition(layoutParams.x, layoutParams.y);
                            return true;
                        }

                        // Double tap detection: reset position to default sub-status bar center
                        long now = System.currentTimeMillis();
                        if (now - lastTapTime < 320) {
                            resetCustomPosition();
                            layoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                            layoutParams.x = 0;
                            layoutParams.y = getStatusBarHeight() + dpToPx(4);
                            if (windowManager != null && rootContainer != null) {
                                windowManager.updateViewLayout(rootContainer, layoutParams);
                            }
                            lastTapTime = 0;
                            return true;
                        }
                        lastTapTime = now;

                        // Single tap to expand into rich floating player card
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

    private void buildExpandedCardView() {
        expandedCardView = new LinearLayout(this);
        expandedCardView.setOrientation(LinearLayout.VERTICAL);
        expandedCardView.setPadding(dpToPx(18), dpToPx(16), dpToPx(18), dpToPx(14));

        expandedCardBg = new GradientDrawable();
        expandedCardBg.setShape(GradientDrawable.RECTANGLE);
        expandedCardBg.setCornerRadius(dpToPx(28));
        expandedCardBg.setColor(Color.parseColor("#141414"));
        expandedCardBg.setStroke(dpToPx(1), Color.parseColor("#2A2A2A"));
        expandedCardView.setBackground(expandedCardBg);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int cardWidth = Math.min(dpToPx(356), dm.widthPixels - dpToPx(24));
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(cardWidth, FrameLayout.LayoutParams.WRAP_CONTENT);
        expandedCardView.setLayoutParams(cardLp);

        // ── Header Row (Art, Title & Artist, Tide Badge) ─────────────────────
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        expandedArtView = new ImageView(this);
        int artSize = dpToPx(48);
        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(artSize, artSize);
        expandedArtView.setLayoutParams(artLp);
        expandedArtView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        expandedArtView.setImageResource(R.drawable.ic_music_notification);
        GradientDrawable artBg = new GradientDrawable();
        artBg.setShape(GradientDrawable.RECTANGLE);
        artBg.setCornerRadius(dpToPx(10));
        artBg.setColor(Color.parseColor("#1E1E1E"));
        expandedArtView.setBackground(artBg);
        expandedArtView.setClipToOutline(true);
        headerRow.addView(expandedArtView);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginStart(dpToPx(12));
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
        tvArtist.setTextColor(Color.parseColor("#B3B3B3"));
        tvArtist.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tvArtist.setSingleLine(true);
        tvArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvArtist.setPadding(0, dpToPx(2), 0, 0);
        textCol.addView(tvArtist);

        headerRow.addView(textCol);

        // App logo / badge on far right
        ImageView ivLogo = new ImageView(this);
        int logoSize = dpToPx(22);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(logoSize, logoSize);
        ivLogo.setLayoutParams(logoLp);
        ivLogo.setImageResource(R.drawable.ic_music_notification);
        ivLogo.setColorFilter(Color.parseColor("#26B8FF"));
        headerRow.addView(ivLogo);

        // Tapping header opens full Tide Music player screen
        headerRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchPlayerScreen();
                collapseToPill();
            }
        });
        expandedCardView.addView(headerRow);

        // ── Timeline / Scrubber Row ──────────────────────────────────────────
        LinearLayout timelineRow = new LinearLayout(this);
        timelineRow.setOrientation(LinearLayout.HORIZONTAL);
        timelineRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tlLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlLp.topMargin = dpToPx(10);
        timelineRow.setLayoutParams(tlLp);

        tvCurrentTime = new TextView(this);
        tvCurrentTime.setText("00:00");
        tvCurrentTime.setTextColor(Color.parseColor("#B3B3B3"));
        tvCurrentTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        timelineRow.addView(tvCurrentTime);

        seekBar = new SeekBar(this);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        sbLp.setMarginStart(dpToPx(8));
        sbLp.setMarginEnd(dpToPx(8));
        seekBar.setLayoutParams(sbLp);
        seekBar.setProgressDrawable(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.seek_progress_drawable));
        seekBar.setThumb(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.seek_thumb));
        seekBar.setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvCurrentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                try {
                    PlaybackController controller = ServiceLocator.INSTANCE.getPlaybackController();
                    controller.seekTo(seekBar.getProgress());
                } catch (Exception e) {
                    Log.e(TAG, "Error seeking playback", e);
                }
            }
        });
        timelineRow.addView(seekBar);

        tvTotalTime = new TextView(this);
        tvTotalTime.setText("00:00");
        tvTotalTime.setTextColor(Color.parseColor("#B3B3B3"));
        tvTotalTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        timelineRow.addView(tvTotalTime);

        expandedCardView.addView(timelineRow);

        // ── Controls Row (Favorite, Previous, Play/Pause, Next, Close) ────────
        LinearLayout controlsRow = new LinearLayout(this);
        controlsRow.setOrientation(LinearLayout.HORIZONTAL);
        controlsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cLp.topMargin = dpToPx(8);
        controlsRow.setLayoutParams(cLp);

        // 1. Favorite Heart Button
        btnFavorite = new ImageView(this);
        int favSize = dpToPx(36);
        btnFavorite.setLayoutParams(new LinearLayout.LayoutParams(favSize, favSize));
        btnFavorite.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
        btnFavorite.setImageResource(R.drawable.ic_notif_favorite_border);
        btnFavorite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
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
                    Log.e(TAG, "Error toggling favorite from capsule", e);
                }
            }
        });
        controlsRow.addView(btnFavorite);

        // Spacer
        View s1 = new View(this);
        controlsRow.addView(s1, new LinearLayout.LayoutParams(0, 1, 1f));

        // 2. Previous Button
        ImageView btnPrev = new ImageView(this);
        int navSize = dpToPx(38);
        btnPrev.setLayoutParams(new LinearLayout.LayoutParams(navSize, navSize));
        btnPrev.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
        btnPrev.setImageResource(R.drawable.ic_notif_prev);
        btnPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    ServiceLocator.INSTANCE.getPlaybackController().previous();
                } catch (Exception e) {
                    Log.e(TAG, "Error seeking previous", e);
                }
            }
        });
        controlsRow.addView(btnPrev);

        View s2 = new View(this);
        controlsRow.addView(s2, new LinearLayout.LayoutParams(dpToPx(14), 1));

        // 3. Play / Pause Button (52dp large circular white button with black icon matching Spotify)
        btnPlayPause = new ImageView(this);
        int playSize = dpToPx(52);
        btnPlayPause.setLayoutParams(new LinearLayout.LayoutParams(playSize, playSize));
        btnPlayPause.setBackgroundResource(R.drawable.bg_play_circle_white);
        btnPlayPause.setPadding(dpToPx(13), dpToPx(13), dpToPx(13), dpToPx(13));
        btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause_black : R.drawable.ic_play_black);
        btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    ServiceLocator.INSTANCE.getPlaybackController().playPause();
                } catch (Exception e) {
                    Log.e(TAG, "Error toggling play/pause from capsule", e);
                }
            }
        });
        controlsRow.addView(btnPlayPause);

        View s3 = new View(this);
        controlsRow.addView(s3, new LinearLayout.LayoutParams(dpToPx(14), 1));

        // 4. Next Button
        ImageView btnNext = new ImageView(this);
        btnNext.setLayoutParams(new LinearLayout.LayoutParams(navSize, navSize));
        btnNext.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
        btnNext.setImageResource(R.drawable.ic_notif_next);
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    ServiceLocator.INSTANCE.getPlaybackController().next();
                } catch (Exception e) {
                    Log.e(TAG, "Error seeking next", e);
                }
            }
        });
        controlsRow.addView(btnNext);

        // Spacer
        View s4 = new View(this);
        controlsRow.addView(s4, new LinearLayout.LayoutParams(0, 1, 1f));

        // 5. Close / Collapse Button
        ImageView btnClose = new ImageView(this);
        btnClose.setLayoutParams(new LinearLayout.LayoutParams(favSize, favSize));
        btnClose.setPadding(dpToPx(7), dpToPx(7), dpToPx(7), dpToPx(7));
        btnClose.setImageResource(R.drawable.ic_close_notification);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                collapseToPill();
            }
        });
        controlsRow.addView(btnClose);

        expandedCardView.addView(controlsRow);

        // ── Sub-row: Audio Format / Track Info Badge (Fluid Cloud aesthetic) ─
        LinearLayout qualityRow = new LinearLayout(this);
        qualityRow.setOrientation(LinearLayout.HORIZONTAL);
        qualityRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams qLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        qLp.topMargin = dpToPx(10);
        qualityRow.setLayoutParams(qLp);

        TextView tvBadge = new TextView(this);
        tvBadge.setText("TIDE LOSSLESS AUDIO");
        tvBadge.setTextColor(Color.parseColor("#80FFFFFF"));
        tvBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        tvBadge.setLetterSpacing(0.08f);
        tvBadge.setPadding(dpToPx(10), dpToPx(3), dpToPx(10), dpToPx(3));

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.RECTANGLE);
        badgeBg.setCornerRadius(dpToPx(10));
        badgeBg.setColor(Color.parseColor("#1CFFFFFF"));
        tvBadge.setBackground(badgeBg);
        qualityRow.addView(tvBadge);

        expandedCardView.addView(qualityRow);
    }

    private void expandCard() {
        if (isExpanded || rootContainer == null) return;
        isExpanded = true;

        miniPillView.setVisibility(View.GONE);
        expandedCardView.setVisibility(View.VISIBLE);

        int sbHeight = getStatusBarHeight();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int cardWidth = Math.min(dpToPx(356), dm.widthPixels - dpToPx(24));

        layoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        layoutParams.x = 0;
        // Positioned cleanly below the status bar, matching Spotify expanded screenshot
        layoutParams.y = sbHeight + dpToPx(6);
        layoutParams.width = cardWidth;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

        try {
            windowManager.updateViewLayout(rootContainer, layoutParams);
        } catch (Exception e) {
            Log.e(TAG, "Error updating window to expanded", e);
        }

        updateTimelineProgress();
        checkFavoriteStatus();
        mainHandler.post(progressUpdater);
    }

    private void collapseToPill() {
        if (!isExpanded || rootContainer == null) return;
        isExpanded = false;
        mainHandler.removeCallbacks(progressUpdater);

        expandedCardView.setVisibility(View.GONE);
        miniPillView.setVisibility(View.VISIBLE);

        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        if (hasCustomPosition()) {
            layoutParams.gravity = Gravity.TOP | Gravity.START;
            layoutParams.x = getSavedPillX();
            layoutParams.y = getSavedPillY();
        } else {
            layoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            layoutParams.x = 0;
            layoutParams.y = getStatusBarHeight() + dpToPx(4);
        }

        try {
            windowManager.updateViewLayout(rootContainer, layoutParams);
            if (isPlaying && miniEqualizerView != null) {
                miniEqualizerView.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating window to collapsed", e);
        }
    }

    private void updateContent(boolean songChanged) {
        if (rootContainer == null) return;

        // 1. Equalizer animation state
        if (miniEqualizerView != null) {
            if (isPlaying) {
                miniEqualizerView.start();
            } else {
                miniEqualizerView.stop();
            }
        }

        // 2. Play/pause button in expanded card
        if (btnPlayPause != null) {
            btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause_black : R.drawable.ic_play_black);
        }

        // 3. Text info in expanded card
        if (tvTitle != null && currentTitle != null && !currentTitle.isEmpty()) {
            tvTitle.setText(currentTitle);
        }
        if (tvArtist != null && currentArtist != null && !currentArtist.isEmpty()) {
            tvArtist.setText(currentArtist);
        }

        // 4. Check favorite state on song transition
        if (songChanged) {
            checkFavoriteStatus();
        }

        // 5. Asynchronous Artwork loading
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
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (cachedArtwork != null) {
                                if (miniArtView != null) miniArtView.setImageBitmap(cachedArtwork);
                                if (expandedArtView != null) expandedArtView.setImageBitmap(cachedArtwork);
                                if (expandedCardBg != null) {
                                    expandedCardBg.setColor(extractArtworkTint(cachedArtwork));
                                }
                            } else {
                                if (miniArtView != null) miniArtView.setImageResource(R.drawable.ic_music_notification);
                                if (expandedArtView != null) expandedArtView.setImageResource(R.drawable.ic_music_notification);
                                if (expandedCardBg != null) {
                                    expandedCardBg.setColor(Color.parseColor("#141414"));
                                }
                            }
                        }
                    });
                }
            });
        }

        if (isExpanded) {
            updateTimelineProgress();
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

            if (seekBar != null && dur > 0) {
                seekBar.setMax((int) dur);
                seekBar.setProgress((int) pos);
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
     * Native Java view rendering thin animated Spotify/Fluid-Cloud-style green equalizer bars.
     */
    public static class SevenBarEqualizerView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float[] barFractions = new float[]{0.35f, 0.85f, 0.55f, 0.90f};
        private ValueAnimator animator;

        public SevenBarEqualizerView(Context context) {
            super(context);
            // Spotify vibrant green color (#1ED760)
            paint.setColor(Color.parseColor("#1ED760"));
            paint.setStyle(Paint.Style.FILL);
            setupAnimator();
        }

        private void setupAnimator() {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(1100);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float phase = (float) animation.getAnimatedValue();
                    for (int i = 0; i < barFractions.length; i++) {
                        float offset = (float) i / (float) barFractions.length;
                        float val = (float) Math.sin((phase * 2 * Math.PI) + (offset * Math.PI));
                        barFractions[i] = Math.max(0.2f, Math.min(1.0f, (val + 1f) / 2f));
                    }
                    invalidate();
                }
            });
        }

        public void start() {
            if (animator != null && !animator.isRunning()) {
                animator.start();
            }
        }

        public void stop() {
            if (animator != null && animator.isRunning()) {
                animator.cancel();
            }
            // Reset to resting height
            for (int i = 0; i < barFractions.length; i++) {
                barFractions[i] = 0.25f;
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) return;

            int count = barFractions.length;
            float spacing = dpToPx(1.8f);
            float totalSpacing = (count - 1) * spacing;
            float barWidth = (width - totalSpacing) / count;
            float cornerRadius = barWidth / 2f;

            float currentX = 0f;
            for (int i = 0; i < count; i++) {
                float barHeight = Math.max(dpToPx(2.5f), height * barFractions[i]);
                float top = height - barHeight;
                rect.set(currentX, top, currentX + barWidth, height);
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
}
