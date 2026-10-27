package com.simple.carlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/** Shared look (graphite + electric blue, or the light neon theme) and small helpers for all screens. */
final class Ui {

    // MCU source ids for ACTION_REQUEST_CHANGE_SOURCE: the "Source" column of
    // /nwdappconfig/app/ApplicationList.xml (not the AppID).
    static final int SOURCE_MUSIC = -128;
    static final int SOURCE_RADIO = 4;
    static final int SOURCE_BT_MUSIC = -125;

    // Dark theme palette: graphite panels with electric-blue accents.
    static final int BG = 0xFF0B0D10;
    static final int PANEL = 0xFF151A21;
    static final int PANEL_ACTIVE = 0xFF1C2330;
    static final int STROKE = 0xFF252C36;
    static final int ACCENT = 0xFF1E90FF;
    static final int ACCENT_LIGHT = 0xFF5CC8FF;
    static final int ACCENT_DEEP = 0xFF123A6B;
    static final int TEXT = 0xFFF2F5F8;
    static final int TEXT_2 = 0xFF9AA4B2;
    static final int WARN = 0xFFFFB020;
    static final int ERROR = 0xFFFF4D4F;

    private Ui() { }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    // ---------- Theme: light (blue/purple neon) or dark (black) ----------

    static final String THEME_AUTO = "auto", THEME_LIGHT = "light", THEME_DARK = "dark";

    static String themeMode(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE).getString("theme", THEME_DARK);
    }

    static void setThemeMode(Context c, String mode) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putString("theme", mode).apply();
    }

    /** Auto = dark while the car's lights are on (ILL wire) or at night. */
    static boolean isDark(Context c) {
        String m = themeMode(c);
        if (THEME_DARK.equals(m)) return true;
        if (THEME_LIGHT.equals(m)) return false;
        int ill = android.provider.Settings.System.getInt(c.getContentResolver(), "mcu_no_source_device_state", 0);
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return (ill & 2) != 0 || h >= 19 || h < 6;
    }

    /** Picks the light or dark variant of a gradient. */
    static int[] pick(Context c, int[] light, int[] dark) {
        return isDark(c) ? dark : light;
    }

    static Drawable background(Context c) {
        if (isDark(c)) {
            GradientDrawable base = new GradientDrawable();
            base.setColor(BG);
            // A faint blue light from the top-left corner, barely visible on graphite.
            GradientDrawable glow = new GradientDrawable();
            glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            glow.setGradientRadius(dp(c, 560));
            glow.setGradientCenter(0.1f, 0f);
            glow.setColors(new int[]{0x1A1E90FF, 0x001E90FF});
            return new LayerDrawable(new Drawable[]{base, glow});
        }
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1B2C8C, 0xFF20206E, 0xFF371764});
        GradientDrawable glowLeft = new GradientDrawable();
        glowLeft.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowLeft.setGradientRadius(dp(c, 520));
        glowLeft.setGradientCenter(0.15f, 0.1f);
        glowLeft.setColors(new int[]{0x553FA9FF, 0x003FA9FF});
        GradientDrawable glowRight = new GradientDrawable();
        glowRight.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowRight.setGradientRadius(dp(c, 560));
        glowRight.setGradientCenter(0.95f, 0.95f);
        glowRight.setColors(new int[]{0x66B04CFF, 0x00B04CFF});
        return new LayerDrawable(new Drawable[]{base, glowLeft, glowRight});
    }

    static GradientDrawable card(Context c) {
        if (isDark(c)) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(PANEL);
            bg.setCornerRadius(dp(c, 16));
            bg.setStroke(dp(c, 1), STROKE);
            return bg;
        }
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x4D5B6CFF, 0x333B3FB8});
        bg.setCornerRadius(dp(c, 18));
        bg.setStroke(dp(c, 1), 0x337F9CFF);
        return bg;
    }

    /** Fill for secondary buttons and chips. */
    static int fill(Context c) {
        return isDark(c) ? PANEL_ACTIVE : 0x26FFFFFF;
    }

    /** Text colours: primary and secondary. */
    static int text1(Context c) {
        return isDark(c) ? TEXT : Color.WHITE;
    }

    static int text2(Context c) {
        return isDark(c) ? TEXT_2 : 0xB3FFFFFF;
    }

    static GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    static TextView text(Context c, CharSequence s, float sp, int color) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    /** Round icon button. filled = electric-blue disc (primary action). */
    static ImageView iconButton(Context c, int icon, int sizeDp, boolean filled, Runnable action) {
        ImageView b = new ImageView(c);
        b.setImageResource(icon);
        int pad = Math.round(sizeDp * 0.24f);
        b.setPadding(dp(c, pad), dp(c, pad), dp(c, pad), dp(c, pad));
        b.setBackground(circle(filled ? ACCENT : fill(c)));
        b.setColorFilter(filled ? Color.WHITE : text1(c));
        pressable(b, 0.88f);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    static void pressable(View v, float scale) {
        v.setClickable(true);
        v.setOnTouchListener((view, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(scale).scaleY(scale).alpha(0.8f).setDuration(90).start();
            } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(140).start();
            }
            return false;
        });
    }

    static void launch(Context c, String component) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(ComponentName.unflattenFromString(component));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            c.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(c, "No disponible", Toast.LENGTH_SHORT).show();
        }
    }

    /** When the user last picked the radio; the BT auto-switch leaves it alone for a while. */
    static long userChoseRadioAt;

    /** True while the FM radio is the active audio source. */
    static boolean radioIsSource(Context c) {
        return android.provider.Settings.System.getInt(c.getContentResolver(), "mcu_current_source", 0)
                == SOURCE_RADIO;
    }

    /**
     * User picked the radio: route it, power the FM chip up and unmute it, and pause the
     * phone so it does not keep streaming (and trigger the Bluetooth auto-switch).
     */
    static void selectRadio(Context c, RadioClient radio) {
        userChoseRadioAt = android.os.SystemClock.elapsedRealtime();
        NowPlaying np = NowPlaying.get(c);
        if (np.track(NowPlaying.Source.BT).playing) np.btClient.playControl(BtClient.PAUSE);
        requestSource(c, SOURCE_RADIO);
        new android.os.Handler().postDelayed(() -> radio.wakeTuner(c), 800);
    }

    /**
     * Makes Bluetooth music the source. Source id -125 is shared by the BT phone (AppID 10) and
     * BT music (AppID 15); a plain source request picks the phone entry, which mutes the
     * amplifier. Announcing the BT music app (KernelUtils.appStart: id 15, op 1, event 0) is what
     * the factory apps do and routes the music properly.
     */
    static void selectBluetooth(Context c) {
        Intent i = new Intent("com.nwd.action.ACTION_APP_IN_OUT");
        i.putExtra("extra_app_id", 15);
        i.putExtra("extra_app_operation", 1);
        i.putExtra("extra_app_event", 0);
        c.sendBroadcast(i);
        // Picking the radio mutes Bluetooth audio (A2DP mute = amplifier mute flag 32) and only
        // the factory BT screen lifts it. Lift it after the source change has gone through.
        new android.os.Handler().postDelayed(
                () -> c.sendBroadcast(new Intent("com.bt.ACTION_A2DP_UNMUTE")), 500);
    }

    /** Asks the NWD kernel to route this audio source to the speakers. */
    static void requestSource(Context c, int sourceId) {
        Intent i = new Intent("com.nwd.action.ACTION_REQUEST_CHANGE_SOURCE");
        i.putExtra("extra_source_id", (byte) sourceId);
        c.sendBroadcast(i);
    }
}
