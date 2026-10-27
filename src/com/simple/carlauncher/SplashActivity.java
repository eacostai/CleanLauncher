package com.simple.carlauncher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Start-up animation shown when the car (or the screen) turns on and when the launcher opens:
 * the Kia logo fades in over a blue light line, a shine sweeps across it, then the greeting,
 * and the home screen appears. Touch to skip.
 */
public class SplashActivity extends Activity {

    private static final long DURATION_MS = 3200;
    private static final long MIN_INTERVAL_MS = 30_000;
    private static long lastShown = -MIN_INTERVAL_MS;

    private final Handler handler = new Handler();
    private boolean closing;

    /** Shows the animation unless it ran in the last 30 s (several on-signals arrive together). */
    static void show(Context c) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastShown < MIN_INTERVAL_MS) return;
        lastShown = now;
        try {
            c.startActivity(new Intent(c, SplashActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        } catch (Exception e) {
            android.util.Log.w("Splash", "cannot show", e); // background start blocked (Android 10+)
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        LogoView logo = new LogoView(this);
        root.addView(logo, new FrameLayout.LayoutParams(-1, -1));

        TextView hello = Ui.text(this, Profile.welcome(this), 24, Ui.TEXT);
        hello.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        hello.setLetterSpacing(0.04f);
        hello.setAlpha(0f);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        hlp.bottomMargin = Ui.dp(this, 70);
        root.addView(hello, hlp);
        setContentView(root);

        hello.animate().alpha(1f).setStartDelay(1700).setDuration(600).start();
        root.setOnClickListener(v -> close());
        handler.postDelayed(this::close, DURATION_MS);
    }

    private void close() {
        if (closing) return;
        closing = true;
        View root = findViewById(android.R.id.content);
        root.animate().alpha(0f).setDuration(450).withEndAction(() -> {
            finish();
            overridePendingTransition(0, android.R.anim.fade_out);
        }).start();
    }

    @Override
    public void onBackPressed() {
        close();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    /** Logo with the light line, fade-in and shine, driven by the time since it was created. */
    private static class LogoView extends View {
        private final Bitmap logo;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF dst = new RectF();
        private final long start = SystemClock.uptimeMillis();

        LogoView(Context c) {
            super(c);
            logo = BitmapFactory.decodeResource(c.getResources(), R.drawable.kia_logo);
            shine.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }

        private static float clamp(float v) {
            return Math.max(0f, Math.min(1f, v));
        }

        private static float ease(float t) {
            return 1 - (1 - t) * (1 - t) * (1 - t);
        }

        @Override
        protected void onDraw(Canvas c) {
            float t = SystemClock.uptimeMillis() - start;
            int w = getWidth(), h = getHeight();
            float lw = Math.min(w * 0.5f, h * 1.4f);
            float lh = lw * logo.getHeight() / logo.getWidth();
            float cx = w / 2f, cy = h * 0.45f;

            // 1. Electric-blue line opens from the centre (0–700 ms).
            float open = ease(clamp(t / 700f));
            float half = lw * 0.62f * open;
            float ly = cy + lh * 0.95f;
            float lineAlpha = 1f - clamp((t - 2200) / 600f) * 0.6f;
            line.setShader(new LinearGradient(cx - half, 0, cx + half, 0,
                    new int[]{0x001E90FF, Ui.ACCENT_LIGHT, 0x001E90FF}, null, Shader.TileMode.CLAMP));
            line.setAlpha(Math.round(255 * lineAlpha));
            c.drawRect(cx - half, ly - 1.5f, cx + half, ly + 1.5f, line);
            glow.setShader(new LinearGradient(0, ly - lh * 0.6f, 0, ly,
                    0x001E90FF, 0x331E90FF, Shader.TileMode.CLAMP));
            glow.setAlpha(Math.round(255 * open * lineAlpha));
            c.drawRect(cx - half, ly - lh * 0.6f, cx + half, ly, glow);

            // 2. Logo fades in and settles (400–1400 ms).
            float in = ease(clamp((t - 400) / 1000f));
            float scale = 0.88f + 0.12f * in;
            dst.set(cx - lw * scale / 2, cy - lh * scale / 2, cx + lw * scale / 2, cy + lh * scale / 2);
            int layer = c.saveLayer(dst.left - 2, dst.top - 2, dst.right + 2, dst.bottom + 2, null);
            paint.setAlpha(Math.round(255 * in));
            c.drawBitmap(logo, null, dst, paint);

            // 3. Shine sweeps across the letters (1300–2300 ms).
            float s = clamp((t - 1300) / 1000f);
            if (s > 0 && s < 1) {
                float x = dst.left - lw * 0.3f + (dst.width() + lw * 0.6f) * s;
                shine.setShader(new LinearGradient(x - lw * 0.15f, dst.top, x + lw * 0.15f, dst.bottom,
                        new int[]{0x00FFFFFF, 0xCC9FDBFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
                c.drawRect(dst, shine);
            }
            c.restoreToCount(layer);

            if (t < 3000) postInvalidateOnAnimation();
        }
    }
}
