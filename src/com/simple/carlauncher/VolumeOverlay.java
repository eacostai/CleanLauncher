package com.simple.carlauncher;

import android.app.Service;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Replaces the factory volume popup: watches the NWD system volume setting
 * (mcu_system_volume, 0..mcu_max_volume) and shows a graphite pill at the top.
 */
public class VolumeOverlay extends Service {

    private static final String KEY_VOLUME = "mcu_system_volume";
    private static final String KEY_MAX = "mcu_max_volume";
    private static final long SHOW_MS = 2500;

    private final Handler handler = new Handler();
    private WindowManager wm;
    private LinearLayout pill;
    private ImageView icon;
    private Bar bar;
    private TextView number;
    private boolean attached;
    private int lastVolume = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        buildView();
        lastVolume = read(KEY_VOLUME, -1);
        getContentResolver().registerContentObserver(Settings.System.getUriFor(KEY_VOLUME), false, observer);
        // The NWD kernel writes the setting without notifying observers, so also listen to its broadcasts.
        IntentFilter f = new IntentFilter();
        f.addAction("com.nwd.action.ACTION_VOLUME_STATE_CHANGE");
        f.addAction("com.nwd.android.ACTION_OS_VOLUME_STATE_CHANGE");
        f.addAction("android.media.VOLUME_CHANGED_ACTION");
        registerReceiver(volumeReceiver, f);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private final ContentObserver observer = new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
            check();
        }
    };

    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            // The setting is written right after the broadcast; read it a moment later.
            // Only the NWD volume-key broadcast forces the popup (e.g. at min/max). The other two
            // also fire when any audio starts playing, so for them show only a real change.
            boolean key = "com.nwd.action.ACTION_VOLUME_STATE_CHANGE".equals(i.getAction());
            handler.postDelayed(() -> {
                if (key) lastVolume = -1;
                check();
            }, 60);
            handler.postDelayed(VolumeOverlay.this::check, 250);
        }
    };

    private void check() {
        int v = read(KEY_VOLUME, -1);
        if (v < 0 || v == lastVolume) return;
        lastVolume = v;
        show(v, Math.max(1, read(KEY_MAX, 40)));
    }

    private void buildView() {
        pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(12), 0, dp(26), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2151A21);
        bg.setCornerRadius(dp(36));
        bg.setStroke(dp(1), 0x661E90FF);
        pill.setBackground(bg);
        pill.setElevation(dp(12));

        icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_volume);
        icon.setColorFilter(Color.WHITE);
        icon.setPadding(dp(12), dp(12), dp(12), dp(12));
        icon.setBackground(Ui.circle(Ui.PANEL_ACTIVE));
        pill.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        bar = new Bar(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dp(14), 1f);
        blp.setMargins(dp(18), 0, dp(18), 0);
        pill.addView(bar, blp);

        number = Ui.text(this, "", 28, Color.WHITE);
        number.setTypeface(Typeface.DEFAULT_BOLD);
        pill.addView(number, new LinearLayout.LayoutParams(dp(48), -2));
    }

    private void show(int volume, int max) {
        number.setText(String.valueOf(volume));
        icon.setImageResource(volume == 0 ? R.drawable.ic_volume_off : R.drawable.ic_volume);
        bar.setLevel(volume / (float) max);

        if (!attached) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    dp(480), dp(72),
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = dp(64);
            try {
                wm.addView(pill, lp);
                attached = true;
            } catch (Exception e) {
                android.util.Log.e("VolumeOverlay", "addView failed", e);
                return; // overlay permission missing
            }
            pill.setAlpha(0f);
            pill.setTranslationY(-dp(20));
        }
        pill.animate().alpha(1f).translationY(0).setDuration(160).start();
        handler.removeCallbacks(hide);
        handler.postDelayed(hide, SHOW_MS);
    }

    private final Runnable hide = () -> pill.animate().alpha(0f).translationY(-dp(20)).setDuration(220)
            .withEndAction(() -> {
                if (attached) {
                    wm.removeView(pill);
                    attached = false;
                }
            }).start();

    private int read(String key, int def) {
        return Settings.System.getInt(getContentResolver(), key, def);
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(observer);
        unregisterReceiver(volumeReceiver);
        handler.removeCallbacksAndMessages(null);
        if (attached) wm.removeView(pill);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Rounded gradient level bar. */
    private static class Bar extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float level, shown;

        Bar(Context c) {
            super(c);
            track.setColor(Ui.PANEL_ACTIVE);
        }

        void setLevel(float l) {
            level = Math.max(0, Math.min(1, l));
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            fill.setShader(new LinearGradient(0, 0, w, 0, Ui.ACCENT, Ui.ACCENT_LIGHT, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas c) {
            shown += (level - shown) * 0.3f;
            if (Math.abs(level - shown) > 0.002f) postInvalidateOnAnimation();
            else shown = level;
            float h = getHeight(), rad = h / 2;
            r.set(0, 0, getWidth(), h);
            c.drawRoundRect(r, rad, rad, track);
            if (shown > 0) {
                r.set(0, 0, Math.max(h, getWidth() * shown), h);
                c.drawRoundRect(r, rad, rad, fill);
            }
        }
    }
}
