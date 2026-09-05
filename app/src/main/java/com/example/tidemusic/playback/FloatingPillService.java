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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
 * Requirements fulfilled:
 * 1. Stationary micro pill (no accidental dragging; swipes left/right smoothly dismiss).
 * 2. 6 thin equalizer sound wave bars expanding symmetrically from the center (bright grey-white #F0F0F0).
 * 3. Hidden when Tide Music is in foreground (MainActivity active).
 * 4. Smooth morphing transition between collapsed pill and expanded card.
 * 5. Professional Canvas scrubber with timestamps, rounded track, and smooth thumb.
 * 6. Blurred artwork backdrop or pitch solid black when no art.
 * 7. Screen never flashes black or vanishes on pause or track transition.
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

    private static volatile boolean isAppInForeground = false;
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
    private ImageView expandedArtView;
    private TextView tvTitle;
    private TextView tvArtist;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private ProfessionalScrubberView scrubberView;
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

    public static void setAppInForeground(boolean inForeground) {
        isAppInForeground = inForeground;
        FloatingPillService instance = sInstance;
        if (instance != null) {
            instance.mainHandler.post(instance::applyAppForegroundVisibility);
        }
    }

    private void applyAppForegroundVisibility() {
        if (rootContainer != null) {
            if (isAppInForeground) {
                rootContainer.setVisibility(View.GONE);
            } else {
                rootContainer.setVisibility(View.VISIBLE);
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

        layoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        layoutParams.x = 0;
        // Positioned cleanly below status bar so notification icons never overlap
        layoutParams.y = getStatusBarHeight() + dpToPx(4);

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
                    } else if (!isPlaying) {
                        // Dismiss stationary pill on outside tap when paused
                        hide(FloatingPillService.this);
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
                            if (dy < -dpToPx(40) || Math.abs(dx) > dpToPx(80)) {
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
        expandedCardContainer.setVisibility(View.GONE);
        rootContainer.addView(expandedCardContainer);

        applyAppForegroundVisibility();

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
        miniPillView.setPadding(dpToPx(8), dpToPx(5), dpToPx(9), dpToPx(5));

        // Stadium pill background with subtle grey outline #444446
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setShape(GradientDrawable.RECTANGLE);
        pillBg.setCornerRadius(dpToPx(17));
        pillBg.setColor(Color.parseColor("#000000"));
        pillBg.setStroke(dpToPx(1.2f), Color.parseColor("#444446"));
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

        // Spacer to provide breathing room between artwork and equalizer bars (matching user image)
        View breathingSpacer = new View(this);
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(dpToPx(18), 1);
        breathingSpacer.setLayoutParams(spacerLp);
        miniPillView.addView(breathingSpacer);

        // 6 thin equalizer sound wave bars expanding symmetrically from center in bright grey-white #F0F0F0
        miniEqualizerView = new SixBarEqualizerView(this);
        LinearLayout.LayoutParams eqLp = new LinearLayout.LayoutParams(dpToPx(20), dpToPx(14));
        miniEqualizerView.setLayoutParams(eqLp);
        miniPillView.addView(miniEqualizerView);

        // Stationary touch interaction: Swiping left/right dismisses; tap expands. No casual dragging.
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
                            // Tactile slide feedback as finger drags horizontally
                            miniPillView.setTranslationX(dx * 0.7f);
                            float alpha = 1f - (Math.abs(dx) / (float) dpToPx(150));
                            miniPillView.setAlpha(Math.max(0.2f, alpha));
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        float totalDx = event.getRawX() - downX;
                        float totalDy = event.getRawY() - downY;
                        long elapsed = System.currentTimeMillis() - downTime;

                        if (isSwiping && Math.abs(totalDx) > dpToPx(36)) {
                            // Swipe dismiss left or right: smoothly animate away and hide
                            float targetX = totalDx > 0 ? dpToPx(160) : -dpToPx(160);
                            miniPillView.animate()
                                    .translationX(targetX)
                                    .alpha(0f)
                                    .setDuration(220)
                                    .withEndAction(new Runnable() {
                                        @Override
                                        public void run() {
                                            hide(FloatingPillService.this);
                                        }
                                    })
                                    .start();
                            return true;
                        }

                        // Reset translation/alpha smoothly if not dismissed
                        miniPillView.animate()
                                .translationX(0f)
                                .alpha(1f)
                                .setDuration(180)
                                .start();

                        // Single tap detected: expand into rich floating player card
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
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int cardWidth = Math.min(dpToPx(356), dm.widthPixels - dpToPx(24));

        expandedCardContainer = new FrameLayout(this);
        FrameLayout.LayoutParams containerLp = new FrameLayout.LayoutParams(cardWidth, FrameLayout.LayoutParams.WRAP_CONTENT);
        expandedCardContainer.setLayoutParams(containerLp);

        // 28dp rounded stadium outline with subtle border
        GradientDrawable containerBg = new GradientDrawable();
        containerBg.setShape(GradientDrawable.RECTANGLE);
        containerBg.setCornerRadius(dpToPx(28));
        containerBg.setColor(Color.parseColor("#000000"));
        containerBg.setStroke(dpToPx(1.2f), Color.parseColor("#333333"));
        expandedCardContainer.setBackground(containerBg);
        expandedCardContainer.setClipToOutline(true);

        // Blurred artwork backdrop image filling the card
        cardBackdropView = new ImageView(this);
        cardBackdropView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        cardBackdropView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cardBackdropView.setAlpha(0.65f);
        expandedCardContainer.addView(cardBackdropView);

        // Dark scrim overlay for crisp contrast over blurred artwork
        cardScrimView = new View(this);
        cardScrimView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        cardScrimView.setBackgroundColor(Color.parseColor("#000000"));
        expandedCardContainer.addView(cardScrimView);

        // Main content layout
        expandedCardContent = new LinearLayout(this);
        expandedCardContent.setOrientation(LinearLayout.VERTICAL);
        expandedCardContent.setPadding(dpToPx(18), dpToPx(16), dpToPx(18), dpToPx(14));
        expandedCardContainer.addView(expandedCardContent);

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

        // App badge icon
        ImageView ivLogo = new ImageView(this);
        int logoSize = dpToPx(22);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(logoSize, logoSize);
        ivLogo.setLayoutParams(logoLp);
        ivLogo.setImageResource(R.drawable.ic_music_notification);
        ivLogo.setColorFilter(Color.parseColor("#26B8FF"));
        headerRow.addView(ivLogo);

        headerRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchPlayerScreen();
                collapseToPill();
            }
        });
        expandedCardContent.addView(headerRow);

        // ── Timeline / Custom Scrubber Row ───────────────────────────────────
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

        // Professional Canvas scrubber with smooth scrubbing
        scrubberView = new ProfessionalScrubberView(this);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(0, dpToPx(26), 1f);
        sbLp.setMarginStart(dpToPx(8));
        sbLp.setMarginEnd(dpToPx(8));
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
        tvTotalTime.setTextColor(Color.parseColor("#B3B3B3"));
        tvTotalTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        timelineRow.addView(tvTotalTime);

        expandedCardContent.addView(timelineRow);

        // ── Controls Row (Favorite, Previous, Play/Pause, Next, Close) ────────
        LinearLayout controlsRow = new LinearLayout(this);
        controlsRow.setOrientation(LinearLayout.HORIZONTAL);
        controlsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cLp.topMargin = dpToPx(8);
        controlsRow.setLayoutParams(cLp);

        // 1. Favorite Button
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

        // 3. Play / Pause Button (52dp large circular white button with black icon)
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

        expandedCardContent.addView(controlsRow);

        // ── Sub-row: Audio Format / Track Info Badge ─────────────────────────
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

        expandedCardContent.addView(qualityRow);
    }

    private void expandCard() {
        if (isExpanded || rootContainer == null) return;
        isExpanded = true;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int cardWidth = Math.min(dpToPx(356), dm.widthPixels - dpToPx(24));
        int sbHeight = getStatusBarHeight();

        layoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        layoutParams.x = 0;
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
            Log.e(TAG, "Error updating window layout for expand", e);
        }

        // Smooth morphing transition: crossfade & scale between mini pill and card
        expandedCardContainer.setAlpha(0f);
        expandedCardContainer.setScaleX(0.88f);
        expandedCardContainer.setScaleY(0.88f);
        expandedCardContainer.setVisibility(View.VISIBLE);

        miniPillView.animate()
                .alpha(0f)
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(200)
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
                .setDuration(260)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        updateTimelineProgress();
        checkFavoriteStatus();
        mainHandler.post(progressUpdater);
    }

    private void collapseToPill() {
        if (!isExpanded || rootContainer == null) return;
        isExpanded = false;
        mainHandler.removeCallbacks(progressUpdater);

        miniPillView.setAlpha(0f);
        miniPillView.setScaleX(0.88f);
        miniPillView.setScaleY(0.88f);
        miniPillView.setVisibility(View.VISIBLE);

        expandedCardContainer.animate()
                .alpha(0f)
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(180)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (!isExpanded) {
                            expandedCardContainer.setVisibility(View.GONE);
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
                                if (windowManager != null && rootContainer != null) {
                                    windowManager.updateViewLayout(rootContainer, layoutParams);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                })
                .start();

        miniPillView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(240)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        if (isPlaying && miniEqualizerView != null) {
            miniEqualizerView.start();
        }
    }

    private void updateContent(boolean songChanged) {
        if (rootContainer == null) return;

        // 1. Equalizer animation state: smoothly runs while playing, rests at center line when paused
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

                                // Blurred backdrop from song artwork
                                if (cardBackdropView != null) {
                                    try {
                                        Bitmap downscaled = Bitmap.createScaledBitmap(cachedArtwork, 48, 48, true);
                                        cardBackdropView.setImageBitmap(downscaled);
                                        cardBackdropView.setVisibility(View.VISIBLE);
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            cardBackdropView.setRenderEffect(
                                                    android.graphics.RenderEffect.createBlurEffect(50f, 50f, Shader.TileMode.CLAMP)
                                            );
                                        }
                                    } catch (Throwable ignored) {}
                                }
                                if (cardScrimView != null) {
                                    cardScrimView.setBackgroundColor(Color.parseColor("#B30A0A0A"));
                                }
                            } else {
                                if (miniArtView != null) miniArtView.setImageResource(R.drawable.ic_music_notification);
                                if (expandedArtView != null) expandedArtView.setImageResource(R.drawable.ic_music_notification);
                                if (cardBackdropView != null) {
                                    cardBackdropView.setImageDrawable(null);
                                    cardBackdropView.setVisibility(View.GONE);
                                }
                                if (cardScrimView != null) {
                                    // Solid pitch black when song has no image
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
     * Native Java view rendering 6 thin equalizer sound wave bars.
     * Expands symmetrically up and down from the vertical center in bright grey-white #F0F0F0.
     */
    public static class SixBarEqualizerView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float[] barFractions = new float[]{0.35f, 0.75f, 0.50f, 0.95f, 0.65f, 0.40f};
        private ValueAnimator animator;

        public SixBarEqualizerView(Context context) {
            super(context);
            // Bright grey-white #F0F0F0 matching user requirement
            paint.setColor(Color.parseColor("#F0F0F0"));
            paint.setStyle(Paint.Style.FILL);
            setupAnimator();
        }

        private void setupAnimator() {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(950);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float phase = (float) animation.getAnimatedValue();
                    for (int i = 0; i < barFractions.length; i++) {
                        float offset = (float) i / (float) barFractions.length;
                        float val = (float) Math.sin((phase * 2 * Math.PI) + (offset * Math.PI * 1.3f));
                        barFractions[i] = Math.max(0.18f, Math.min(1.0f, (val + 1f) / 2f));
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
            // Reset to resting center height
            for (int i = 0; i < barFractions.length; i++) {
                barFractions[i] = 0.22f;
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
            float spacing = dpToPx(1.4f);
            float totalSpacing = (count - 1) * spacing;
            float barWidth = Math.max(dpToPx(1.6f), (width - totalSpacing) / count);
            float cornerRadius = barWidth / 2f;
            float centerY = height / 2f;

            float currentX = 0f;
            for (int i = 0; i < count; i++) {
                float barHeight = Math.max(dpToPx(2.2f), height * barFractions[i]);
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
     * Custom Canvas-rendered scrubber view matching PlayerScreen.kt aesthetic.
     * Prevents OEM seekbar glitches, provides smooth thumb dragging, and avoids clipping.
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
            float trackHeight = dpToPx(3.2f);
            float trackCorner = trackHeight / 2f;
            float thumbRadius = isDragging ? dpToPx(7f) : dpToPx(5f);

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
            float thumbRadius = isDragging ? dpToPx(7f) : dpToPx(5f);
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
}
