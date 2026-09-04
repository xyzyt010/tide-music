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
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.example.tidemusic.MainActivity;
import com.example.tidemusic.R;
import com.example.tidemusic.util.AudioArtworkFetcher;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pure Java system overlay service for the top Dynamic Island capsule widget.
 * Renders the mini black capsule at the top camera punch hole cutout across all apps.
 */
public class FloatingPillService extends Service {

    private static final String TAG = "FloatingPillService";
    private static final String ACTION_UPDATE = "com.example.tidemusic.action.UPDATE_FLOATING_PILL";
    private static final String ACTION_STOP = "com.example.tidemusic.action.STOP_FLOATING_PILL";

    private static final String EXTRA_FILE_PATH = "extra_file_path";
    private static final String EXTRA_URI = "extra_uri";
    private static final String EXTRA_IS_PLAYING = "extra_is_playing";

    private WindowManager windowManager;
    private View pillRootView;
    private ImageView artView;
    private SevenBarEqualizerView equalizerView;
    private WindowManager.LayoutParams layoutParams;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String currentFilePath = "";
    private String currentUriString = "";
    private boolean isPlaying = false;

    public static void showOrUpdate(Context context, @Nullable String filePath, @Nullable String uriString, boolean isPlaying) {
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
            currentFilePath = intent.getStringExtra(EXTRA_FILE_PATH);
            currentUriString = intent.getStringExtra(EXTRA_URI);
            isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false);

            if (!Settings.canDrawOverlays(this)) {
                removeOverlay();
                stopSelf();
                return START_NOT_STICKY;
            }

            ensureOverlayCreated();
            updateContent();
        }

        return START_STICKY;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void ensureOverlayCreated() {
        if (pillRootView != null) return;

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
        layoutParams.y = dpToPx(8);

        // Crucial for modern Android 9-16 punch hole and camera cutout support
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }

        // Build the pill layout
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dpToPx(8), dpToPx(4), dpToPx(9), dpToPx(4));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(16));
        bg.setColor(Color.parseColor("#0C0C0C"));
        bg.setStroke(dpToPx(1), Color.parseColor("#262626"));
        root.setBackground(bg);

        // Artwork image
        artView = new ImageView(this);
        int artSize = dpToPx(20);
        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(artSize, artSize);
        artLp.setMarginEnd(dpToPx(7));
        artView.setLayoutParams(artLp);
        artView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artView.setImageResource(R.drawable.ic_music_notification);

        GradientDrawable artBg = new GradientDrawable();
        artBg.setShape(GradientDrawable.RECTANGLE);
        artBg.setCornerRadius(dpToPx(5));
        artBg.setColor(Color.parseColor("#181818"));
        artView.setBackground(artBg);
        artView.setClipToOutline(true);
        root.addView(artView);

        // 7 thin equalizer bars
        equalizerView = new SevenBarEqualizerView(this);
        LinearLayout.LayoutParams eqLp = new LinearLayout.LayoutParams(dpToPx(22), dpToPx(14));
        equalizerView.setLayoutParams(eqLp);
        root.addView(equalizerView);

        // Touch listener supporting both tap (open player) and drag (reposition along top bar)
        root.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isClick = true;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isClick = true;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isClick = false;
                            layoutParams.x = initialX + (int) dx;
                            layoutParams.y = Math.max(0, initialY + (int) dy);
                            if (windowManager != null && pillRootView != null) {
                                windowManager.updateViewLayout(pillRootView, layoutParams);
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (isClick) {
                            try {
                                Intent launchIntent = new Intent(FloatingPillService.this, MainActivity.class);
                                launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                launchIntent.setData(Uri.parse("tidemusic://player"));
                                startActivity(launchIntent);
                            } catch (Exception e) {
                                Log.e(TAG, "Error launching player from floating pill", e);
                            }
                        }
                        return true;
                }
                return false;
            }
        });

        pillRootView = root;
        try {
            windowManager.addView(pillRootView, layoutParams);
            equalizerView.start();
        } catch (Exception e) {
            Log.e(TAG, "Failed to add floating pill overlay", e);
            pillRootView = null;
        }
    }

    private void updateContent() {
        if (pillRootView == null) return;

        if (equalizerView != null) {
            if (isPlaying) {
                equalizerView.start();
            } else {
                equalizerView.stop();
            }
        }

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

                final Bitmap finalBmp = bmp;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (artView != null) {
                            if (finalBmp != null) {
                                artView.setImageBitmap(finalBmp);
                            } else {
                                artView.setImageResource(R.drawable.ic_music_notification);
                            }
                        }
                    }
                });
            }
        });
    }

    private void removeOverlay() {
        if (equalizerView != null) {
            equalizerView.stop();
        }
        if (pillRootView != null && windowManager != null) {
            try {
                windowManager.removeView(pillRootView);
            } catch (Exception ignored) {}
            pillRootView = null;
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

    private int dpToPx(int dp) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, dm);
    }

    /**
     * Native Java view rendering 7 thin animated equalizer bars.
     */
    public static class SevenBarEqualizerView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float[] barFractions = new float[]{0.4f, 0.7f, 0.3f, 0.9f, 0.5f, 0.8f, 0.35f};
        private ValueAnimator animator;

        public SevenBarEqualizerView(Context context) {
            super(context);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.FILL);

            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(1600L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float progress = animation.getAnimatedFraction();
                    barFractions[0] = (float) ((Math.sin(progress * 2 * Math.PI * 2.3 + 0.3) + 1.0) / 2.0 * 0.7 + 0.2);
                    barFractions[1] = (float) ((Math.sin(progress * 2 * Math.PI * 3.1 + 1.2) + 1.0) / 2.0 * 0.8 + 0.15);
                    barFractions[2] = (float) ((Math.sin(progress * 2 * Math.PI * 1.8 + 2.5) + 1.0) / 2.0 * 0.85 + 0.15);
                    barFractions[3] = (float) ((Math.sin(progress * 2 * Math.PI * 2.7 + 0.7) + 1.0) / 2.0 * 0.75 + 0.25);
                    barFractions[4] = (float) ((Math.sin(progress * 2 * Math.PI * 3.4 + 1.9) + 1.0) / 2.0 * 0.8 + 0.2);
                    barFractions[5] = (float) ((Math.sin(progress * 2 * Math.PI * 2.1 + 3.1) + 1.0) / 2.0 * 0.7 + 0.2);
                    barFractions[6] = (float) ((Math.sin(progress * 2 * Math.PI * 2.9 + 0.4) + 1.0) / 2.0 * 0.75 + 0.15);
                    invalidate();
                }
            });
        }

        public void start() {
            if (animator != null && !animator.isStarted()) {
                animator.start();
            }
        }

        public void stop() {
            if (animator != null) {
                animator.cancel();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            int barCount = 7;
            float barWidth = dpToPx(1.6f);
            float gap = Math.max(1f, (w - (barCount * barWidth)) / (barCount - 1));
            float corner = dpToPx(0.8f);

            for (int i = 0; i < barCount; i++) {
                float left = i * (barWidth + gap);
                float right = left + barWidth;
                float barHeight = Math.max(dpToPx(2.5f), h * barFractions[i]);
                float top = h - barHeight;
                rect.set(left, top, right, h);
                canvas.drawRoundRect(rect, corner, corner, paint);
            }
        }

        private float dpToPx(float dp) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, dm);
        }

        @Override
        protected void onDetachedFromWindow() {
            stop();
            super.onDetachedFromWindow();
        }
    }
}
