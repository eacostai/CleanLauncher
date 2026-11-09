package com.simple.carlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

/**
 * Home screen in a glossy, Tesla-like style. Thin top bar: time and date, Kia logo, Wi-Fi,
 * Bluetooth and the theme pill. Left gloss card: greeting and weather. Right: a small HOY card
 * (today's trip) over a faint, frameless live map. Centre: the Sorento (headlights on at night or
 * on touch) with its reflection on the floor. Bottom: the music card, the GPS speedometer and the
 * gloss icon dock. Day (light) and night (dark) looks; auto switches at 6 p.m. / car lights.
 */
public class MainActivity extends Activity implements NowPlaying.Listener {

    private static final String EQ = "com.nwd.audioset/.home_horizontalActivity";
    private static final String SETTINGS = "com.android.car.setting/.home_horizontalActivity";

    private static final long WEATHER_EVERY_MS = 30 * 60 * 1000;

    private final Handler handler = new Handler();
    private NowPlaying nowPlaying;

    // Home palette (day or night).
    private boolean dark;
    private int txt, sub, sep, bgTop, bgBottom, card0, card1, outline, highlight;

    private TextView greeting, nameLine, askLine, weatherTemp, weatherDesc, weatherCity, weatherFeels, weatherMinMax,
            weatherHumidity, weatherWind;
    private ImageView weatherIcon;
    private ImageView car, carLights, reflection;
    private boolean hasLightsImage;
    private TextView statKm, statMoving, statOil, themePill;
    private boolean createdDark;
    private String createdProfile;
    private long lastWeatherFetch;

    /** Headlights: null = automatic (car lights or night by the clock), otherwise the driver's choice. */
    private Boolean lightsManual;
    private boolean lightsAutoLast;

    private ImageView playBtn, sourceIcon;
    private TextView songTitle, songArtist;
    private LinearLayout musicApps;
    private LevelBar progress;

    private static final long GPS_STALE_MS = 5000;
    private static final int GAUGE_DP = 100;
    private GaugeView gauge;
    private MapView map;
    private Location lastGps;
    private long lastGpsAt = -GPS_STALE_MS;

    private String profileStamp() {
        return Profile.name(this) + "|" + Profile.carPhotoFile(this).lastModified();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dark = Ui.isDark(this);
        if (dark) {
            txt = 0xFFF0F2F5; sub = 0xFF8C929C; sep = 0xFF2A2D33; bgTop = 0xFF181A1F; bgBottom = 0xFF050608;
            card0 = 0xFF282B32; card1 = 0xFF121317; outline = 0xFF383C44; highlight = 0x22FFFFFF;
        } else {
            txt = 0xFF17191C; sub = 0xFF6E747E; sep = 0xFFDCDFE4; bgTop = 0xFFFAFBFC; bgBottom = 0xFFD8DDE4;
            card0 = 0xFFFFFFFF; card1 = 0xFFECEFF3; outline = 0xFFCDD2DA; highlight = 0x78FFFFFF;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{bgTop, bgBottom}));
        // Soft diagonal sheen across the screen, like light on glass.
        View sheen = new View(this);
        sheen.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0x00FFFFFF, dark ? 0x0CFFFFFF : 0x30FFFFFF, 0x00FFFFFF, 0x00FFFFFF}));
        root.addView(sheen, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), 0, dp(14), dp(6));
        content.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(38)));
        content.addView(buildMiddle(), new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, dp(50));
        dlp.topMargin = dp(8);
        content.addView(buildDock(), dlp);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        nowPlaying = NowPlaying.get(this);
        createdDark = dark;
        createdProfile = profileStamp();
        lightsAutoLast = lightsAuto();
        // Shared installs: ask for name, car photo and permissions on first start.
        if (!Profile.isSetupDone(this) && Profile.bundledCar(this, false) == 0) {
            startActivity(new Intent(this, SetupActivity.class));
        }
        if (Profile.isNwd(this)) startService(new Intent(this, VolumeOverlay.class));
        // Greet when the launcher opens (not when it is only recreated for a theme change).
        startService(new Intent(this, GreeterService.class)
                .putExtra(GreeterService.EXTRA_GREET_NOW, savedInstanceState == null));
        startService(new Intent(this, Odometer.class));
        if (savedInstanceState == null) SplashActivity.show(this);
    }

    /** Gloss card: vertical gradient, thin outline, a shine on the upper half and a soft drop shadow. */
    private Drawable glossBg(int radius) {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{card0, card1});
        base.setCornerRadius(dp(radius));
        base.setStroke(dp(1), outline);
        GradientDrawable shine = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{highlight, 0x00FFFFFF, 0x00FFFFFF});
        shine.setCornerRadius(dp(radius));
        return new LayerDrawable(new Drawable[]{base, shine});
    }

    private <T extends View> T gloss(T v, int radius) {
        v.setBackground(glossBg(radius));
        v.setElevation(dp(dark ? 10 : 6));
        return v;
    }

    // ---------- Top bar: time and date, Kia logo, Wi-Fi / Bluetooth, theme ----------

    private View buildTopBar() {
        FrameLayout bar = new FrameLayout(this);

        LinearLayout clock = new LinearLayout(this);
        clock.setGravity(Gravity.CENTER_VERTICAL);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm a");
        time.setFormat24Hour("H:mm");
        time.setTextSize(15);
        time.setTextColor(txt);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        clock.addView(time);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE d 'de' MMMM");
        date.setFormat24Hour("EEEE d 'de' MMMM");
        date.setTextSize(12);
        date.setTextColor(sub);
        date.setPadding(dp(14), 0, 0, 0);
        clock.addView(date);
        bar.addView(clock, new FrameLayout.LayoutParams(-2, -1, Gravity.START));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.kia_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setColorFilter(txt, PorterDuff.Mode.SRC_IN);
        bar.addView(logo, new FrameLayout.LayoutParams(dp(78), dp(22), Gravity.CENTER));

        LinearLayout status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.addView(barIcon(R.drawable.ic_wifi, "WiFi", this::openWifi), new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(30), dp(30));
        blp.setMargins(dp(4), 0, dp(12), 0);
        status.addView(barIcon(R.drawable.ic_bluetooth, "Bluetooth", this::openBluetooth), blp);
        themePill = Ui.text(this, "", 10, txt);
        themePill.setTypeface(Typeface.DEFAULT_BOLD);
        themePill.setLetterSpacing(0.06f);
        themePill.setPadding(dp(12), dp(4), dp(12), dp(4));
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(12));
        pill.setColor(card0);
        pill.setStroke(dp(1), outline);
        themePill.setBackground(pill);
        Ui.pressable(themePill, 0.94f);
        themePill.setOnClickListener(v -> cycleTheme());
        status.addView(themePill);
        updateThemePill();
        bar.addView(status, new FrameLayout.LayoutParams(-2, -1, Gravity.END));
        return bar;
    }

    private ImageView barIcon(int icon, String name, Runnable action) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(txt);
        iv.setContentDescription(name);
        iv.setPadding(dp(6), dp(6), dp(6), dp(6));
        Ui.pressable(iv, 0.85f);
        iv.setOnClickListener(v -> action.run());
        return iv;
    }

    // ---------- Middle: cards around the car, music and speedometer at the bottom ----------

    private View buildMiddle() {
        FrameLayout mid = new FrameLayout(this);
        mid.setClipChildren(false);

        // Car first, so the cards and their shadows sit above it.
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-1, -1);
        clp.setMargins(dp(250), 0, dp(250), dp(GAUGE_DP));
        mid.addView(buildCar(), clp);

        gauge = new GaugeView(this, txt, sub);
        gauge.setOnClickListener(v -> fixGps());
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(-1, dp(GAUGE_DP), Gravity.BOTTOM);
        glp.leftMargin = dp(336);
        glp.bottomMargin = dp(2);
        mid.addView(gauge, glp);

        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(dp(234), -2, Gravity.TOP | Gravity.START);
        llp.topMargin = dp(8);
        mid.addView(buildGreetingCard(), llp);

        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(dp(250), -1, Gravity.TOP | Gravity.END);
        rlp.setMargins(0, dp(8), 0, dp(GAUGE_DP + 4));
        mid.addView(buildRight(), rlp);

        mid.addView(buildMusicCard(), new FrameLayout.LayoutParams(dp(307), -2, Gravity.BOTTOM | Gravity.START));
        return mid;
    }

    // ---------- Left card: greeting and weather ----------

    private View buildGreetingCard() {
        LinearLayout c = gloss(new LinearLayout(this), 18);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(10));

        greeting = left(Ui.text(this, "", 11, sub));
        greeting.setAllCaps(true);
        greeting.setLetterSpacing(0.08f);
        c.addView(greeting);
        nameLine = left(Ui.text(this, "", 34, txt));
        nameLine.setTypeface(Typeface.DEFAULT_BOLD);
        nameLine.setIncludeFontPadding(false);
        nameLine.setPadding(0, dp(6), 0, dp(4));
        c.addView(nameLine);
        askLine = left(Ui.text(this, "", 12, sub));
        c.addView(askLine);

        LinearLayout now = new LinearLayout(this);
        now.setGravity(Gravity.CENTER_VERTICAL);
        now.setPadding(0, dp(22), 0, dp(14));
        weatherIcon = new ImageView(this);
        weatherIcon.setImageResource(R.drawable.ic_w_cloud);
        weatherIcon.setColorFilter(txt);
        now.addView(weatherIcon, new LinearLayout.LayoutParams(dp(36), dp(36)));
        weatherTemp = Ui.text(this, "--°", 36, txt);
        weatherTemp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        weatherTemp.setIncludeFontPadding(false);
        weatherTemp.setPadding(dp(10), 0, dp(12), 0);
        now.addView(weatherTemp);
        LinearLayout desc = new LinearLayout(this);
        desc.setOrientation(LinearLayout.VERTICAL);
        weatherDesc = left(Ui.text(this, "Cargando el clima…", 13, txt));
        desc.addView(weatherDesc);
        weatherCity = left(Ui.text(this, "", 10, sub));
        weatherCity.setPadding(0, dp(2), 0, 0);
        desc.addView(weatherCity);
        now.addView(desc, new LinearLayout.LayoutParams(0, -2, 1f));
        c.addView(now);
        weatherFeels = Ui.text(this, "", 10, sub); // kept for showWeather; not shown in this layout

        weatherMinMax = weatherRow(c, "Máx / mín", true);
        weatherHumidity = weatherRow(c, "Humedad", true);
        weatherWind = weatherRow(c, "Viento", false);
        Ui.pressable(c, 0.98f);
        c.setOnClickListener(v -> fetchWeather(true));
        return c;
    }

    private TextView weatherRow(LinearLayout parent, String caption, boolean divider) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(5), 0, dp(5));
        r.addView(left(Ui.text(this, caption, 11, sub)), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView v = Ui.text(this, "", 11, txt);
        r.addView(v);
        parent.addView(r);
        if (divider) {
            View d = new View(this);
            d.setBackgroundColor(sep);
            parent.addView(d, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        return v;
    }

    private void updateGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean night = h >= 19 || h < 5;
        greeting.setText(h < 5 ? "Buenas noches" : h < 12 ? "Buenos días" : h < 19 ? "Buenas tardes" : "Buenas noches");
        nameLine.setText(Profile.name(this));
        askLine.setText(night ? "¿A dónde vamos esta noche?" : "¿A dónde vamos hoy?");
    }

    // ---------- Centre: the car with its reflection ----------

    private View buildCar() {
        FrameLayout carArea = new FrameLayout(this);
        View shadow = new View(this);
        GradientDrawable sh = new GradientDrawable();
        sh.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        sh.setColors(new int[]{dark ? 0xCC000000 : 0x66000000, 0x00000000});
        shadow.setBackground(sh);
        reflection = new ImageView(this);
        reflection.setScaleType(ImageView.ScaleType.FIT_XY);
        carArea.addView(reflection, new FrameLayout.LayoutParams(0, 0));
        carArea.addView(shadow, new FrameLayout.LayoutParams(0, 0));
        FrameLayout carBox = new FrameLayout(this);
        car = new ImageView(this);
        car.setScaleType(ImageView.ScaleType.FIT_CENTER);
        carLights = new ImageView(this);
        carLights.setScaleType(ImageView.ScaleType.FIT_CENTER);
        carLights.setAlpha(0f);
        Bitmap own = Profile.carPhoto(this);
        int bundled = Profile.bundledCar(this, false), bundledLights = Profile.bundledCar(this, true);
        if (own != null) {
            car.setImageBitmap(own);
        } else if (bundled != 0) {
            car.setImageResource(bundled);
            if (bundledLights != 0) {
                carLights.setImageResource(bundledLights);
                hasLightsImage = true;
            }
        } else {
            car.setImageResource(R.drawable.ic_car);
            car.setColorFilter(dark ? 0x40FFFFFF : 0x40000000);
        }
        carBox.addView(car, new FrameLayout.LayoutParams(-1, -1));
        carBox.addView(carLights, new FrameLayout.LayoutParams(-1, -1));
        carArea.addView(carBox, new FrameLayout.LayoutParams(0, 0));
        // Touch: headlights on / off. Long press: profile (name, car photo).
        carBox.setOnClickListener(v -> {
            lightsManual = !lightsShown();
            showCarLights(lightsManual, true);
        });
        carBox.setOnLongClickListener(v -> {
            startActivity(new Intent(this, SetupActivity.class));
            return true;
        });

        TextView model = Ui.text(this, spaced(Profile.MODEL), 12, sub);
        model.setLetterSpacing(0.25f);
        carArea.addView(model, new FrameLayout.LayoutParams(-1, -2));
        carArea.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) carArea.post(() -> placeCar(carArea, carBox, model, shadow));
        });
        return carArea;
    }

    private static String spaced(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) b.append(b.length() == 0 ? "" : " ").append(ch);
        return b.toString();
    }

    /** Car as large as fits, a soft shadow and a faint mirror image under the wheels, the model name below. */
    private void placeCar(FrameLayout area, View carBox, TextView model, View shadow) {
        int w = area.getWidth(), h = area.getHeight();
        if (w == 0 || h == 0) return;
        Drawable d = car.getDrawable();
        float ratio = d != null && d.getIntrinsicWidth() > 0
                ? d.getIntrinsicHeight() / (float) d.getIntrinsicWidth() : 0.56f;
        model.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int mh = model.getMeasuredHeight(), gap = dp(16);
        int cw = w, ch = Math.round(cw * ratio);
        int max = Math.round((h - mh - gap) * 0.9f);
        if (ch > max) {
            ch = max;
            cw = Math.round(ch / ratio);
        }
        int top = Math.max(dp(4), Math.round((h - ch - mh - gap) * 0.4f));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(cw, ch, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        clp.topMargin = top;
        carBox.setLayoutParams(clp);

        int sw = Math.round(cw * 0.9f), shh = Math.round(ch * 0.22f);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(sw, shh, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        slp.topMargin = top + ch - shh * 2 / 3;
        shadow.setLayoutParams(slp);
        ((GradientDrawable) shadow.getBackground()).setGradientRadius(sw / 2f);
        shadow.setScaleY(0.35f);

        Bitmap mirror = mirror(d, cw, ch, 0.45f, dark ? 70 : 60);
        if (mirror != null) {
            reflection.setImageBitmap(mirror);
            FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(cw, mirror.getHeight(),
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            rlp.topMargin = top + ch - Math.round(ch * 0.1f);
            reflection.setLayoutParams(rlp);
        }

        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        mlp.topMargin = top + ch + gap;
        model.setLayoutParams(mlp);
    }

    /** The lower part of the car flipped upside down, fading out: its reflection on a glossy floor. */
    private static Bitmap mirror(Drawable src, int w, int h, float part, int alpha) {
        if (src == null || src.getConstantState() == null || w <= 0 || h <= 0) return null;
        int rh = Math.max(1, Math.round(h * part));
        try {
            Drawable d = src.getConstantState().newDrawable().mutate();
            Bitmap b = Bitmap.createBitmap(w, rh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.save();
            c.translate(0, h);
            c.scale(1, -1);
            d.setBounds(0, 0, w, h);
            d.draw(c);
            c.restore();
            Paint mask = new Paint();
            mask.setShader(new LinearGradient(0, 0, 0, rh, alpha << 24, 0, Shader.TileMode.CLAMP));
            mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            c.drawRect(0, 0, w, rh, mask);
            return b;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    // ---------- Right: small HOY card over a faint live map ----------

    private View buildRight() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);

        LinearLayout hoy = gloss(new LinearLayout(this), 14);
        hoy.setOrientation(LinearLayout.VERTICAL);
        hoy.setPadding(dp(10), dp(10), dp(11), dp(10));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        TextView title = Ui.text(this, "H O Y", 8, sub);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(title);
        FrameLayout dot = new FrameLayout(this);
        View glow = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[]{0x993E82F7, 0x003E82F7});
        g.setGradientRadius(dp(7));
        glow.setBackground(g);
        dot.addView(glow, new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER));
        View core = new View(this);
        core.setBackground(Ui.circle(Ui.ACCENT));
        dot.addView(core, new FrameLayout.LayoutParams(dp(5), dp(5), Gravity.CENTER));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(14), dp(14));
        dlp.leftMargin = dp(3);
        head.addView(dot, dlp);
        hoy.addView(head, new LinearLayout.LayoutParams(-1, -2));

        statKm = tripRow(hoy, "RUTA DE HOY", true);
        statMoving = tripRow(hoy, "EN MOVIMIENTO", true);
        statOil = tripRow(hoy, "MANTENIMIENTO EN", false);
        View oilRow = (View) statOil.getParent();
        Ui.pressable(oilRow, 0.96f);
        oilRow.setOnClickListener(v -> startActivity(new Intent(this, OilActivity.class)));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(112), -2);
        hlp.gravity = Gravity.END;
        col.addView(hoy, hlp);

        // Live map with no frame: its edges fade into the background; a touch opens Google Maps.
        FrameLayout mapBox = new FrameLayout(this);
        map = new MapView(this);
        map.setDark(dark);
        map.setFaded(sub);
        map.setAlpha(dark ? 0.6f : 0.55f);
        mapBox.addView(map, new FrameLayout.LayoutParams(-1, -1));
        View tap = new View(this);
        tap.setContentDescription("Abrir mapa");
        tap.setOnClickListener(v -> openMaps());
        mapBox.addView(tap, new FrameLayout.LayoutParams(-1, -1));
        TextView here = Ui.text(this, "Tu ubicación", 9, sub);
        FrameLayout.LayoutParams hlp2 = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        hlp2.setMargins(0, dp(12), dp(14), 0);
        mapBox.addView(here, hlp2);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        mlp.topMargin = dp(6);
        col.addView(mapBox, mlp);
        return col;
    }

    /** One HOY row: value (unit in small grey) above a tiny caption, right-aligned, with a blue fading line. */
    private TextView tripRow(LinearLayout parent, String caption, boolean divider) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setGravity(Gravity.END);
        r.setPadding(0, dp(6), 0, divider ? dp(6) : 0);
        TextView v = Ui.text(this, "", 17, txt);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setIncludeFontPadding(false);
        r.addView(v);
        TextView c = Ui.text(this, caption, 7, sub);
        c.setLetterSpacing(0.04f);
        c.setPadding(0, dp(3), 0, 0);
        r.addView(c);
        parent.addView(r);
        if (divider) {
            View d = new View(this);
            d.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0x003E82F7, 0x553E82F7, 0xDD3E82F7}));
            parent.addView(d, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        return v;
    }

    /** "42,6" big and bold with "km" small and grey beside it. */
    private CharSequence withUnit(String value, String unit) {
        SpannableStringBuilder sb = new SpannableStringBuilder(value);
        if (unit.isEmpty()) return sb;
        int s = sb.length();
        sb.append(" ").append(unit);
        sb.setSpan(new AbsoluteSizeSpan(9, true), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(sub), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new android.text.style.StyleSpan(Typeface.NORMAL), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    private void updateStats() {
        float km = Odometer.todayKm(this);
        statKm.setText(withUnit(String.format(new Locale("es", "EC"), "%.1f", km).replace('.', ','), "km"));
        int min = Odometer.todayMovingMinutes(this);
        statMoving.setText(min < 60 ? withUnit(String.valueOf(min), "min")
                : withUnit(min / 60 + " h " + (min % 60 < 10 ? "0" : "") + min % 60, ""));
        if (!Odometer.isConfigured(this)) {
            statOil.setText("Configurar");
            statOil.setTextColor(txt);
        } else {
            int left = Odometer.remainingKm(this);
            statOil.setText(left < 0 ? "¡Ya toca!" : withUnit(OilActivity.fmt(left), "km"));
            statOil.setTextColor(left < 0 ? Ui.ERROR : left <= Odometer.WARN_KM ? Ui.WARN : txt);
        }
    }

    // ---------- Bottom left: music card (mini player) ----------

    private View buildMusicCard() {
        LinearLayout c = gloss(new LinearLayout(this), 16);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(13), dp(13), dp(13), dp(8));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        sourceIcon = new ImageView(this);
        sourceIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        sourceIcon.setPadding(dp(6), dp(6), dp(6), dp(6));
        GradientDrawable art = new GradientDrawable();
        art.setCornerRadius(dp(8));
        art.setColor(dark ? 0x1FFFFFFF : 0x12000000);
        sourceIcon.setBackground(art);
        top.addView(sourceIcon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);
        songTitle = left(Ui.text(this, "", 15, txt));
        songTitle.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(songTitle);
        songArtist = left(Ui.text(this, "", 11, sub));
        songArtist.setPadding(0, dp(3), 0, 0);
        texts.addView(songArtist);
        top.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        Ui.pressable(top, 0.98f);
        top.setOnClickListener(v -> openMusic());
        c.addView(top);

        progress = new LevelBar(this);
        progress.setTrack(sep);
        LinearLayout.LayoutParams prl = new LinearLayout.LayoutParams(-1, dp(3));
        prl.setMargins(0, dp(12), 0, dp(4));
        c.addView(progress, prl);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(control(R.drawable.ic_prev, false, () -> nowPlaying.skip(false)), new LinearLayout.LayoutParams(dp(44), dp(44)));
        playBtn = control(R.drawable.ic_play, true, () -> nowPlaying.playPause());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(40), dp(40));
        plp.setMargins(dp(10), 0, dp(10), 0);
        controls.addView(playBtn, plp);
        controls.addView(control(R.drawable.ic_next, false, () -> nowPlaying.skip(true)), new LinearLayout.LayoutParams(dp(44), dp(44)));
        controls.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        View div = new View(this);
        div.setBackgroundColor(sep);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(dp(1), dp(28));
        vlp.rightMargin = dp(10);
        controls.addView(div, vlp);
        musicApps = new LinearLayout(this);
        musicApps.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        controls.addView(musicApps, new LinearLayout.LayoutParams(-2, dp(40)));
        c.addView(controls);
        fillMusicApps();
        return c;
    }

    private ImageView control(int icon, boolean round, Runnable action) {
        ImageView b = new ImageView(this);
        b.setImageResource(icon);
        b.setColorFilter(round ? Color.WHITE : txt);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = dp(round ? 10 : 10);
        b.setPadding(pad, pad, pad, pad);
        if (round) {
            b.setBackground(Ui.circle(Ui.ACCENT));
            b.setElevation(dp(3));
        }
        Ui.pressable(b, 0.88f);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void cycleTheme() {
        String m = Ui.themeMode(this);
        Ui.setThemeMode(this, Ui.THEME_AUTO.equals(m) ? Ui.THEME_LIGHT
                : Ui.THEME_LIGHT.equals(m) ? Ui.THEME_DARK : Ui.THEME_AUTO);
        if (Ui.isDark(this) != createdDark) recreate();
        else updateThemePill();
    }

    private void updateThemePill() {
        String m = Ui.themeMode(this);
        String look = dark ? "NOCHE" : "DÍA";
        themePill.setText(Ui.THEME_AUTO.equals(m) ? "AUTO · " + look : look);
    }

    private void showWeather(Weather w) {
        if (w == null) return;
        weatherIcon.setImageResource(w.icon());
        weatherIcon.setColorFilter(txt);
        weatherTemp.setText(w.temp + "°");
        weatherDesc.setText(w.description());
        weatherCity.setText(w.city.isEmpty() ? "Clima" : w.city);
        weatherFeels.setText("Sensación " + w.feelsLike + "°");
        weatherMinMax.setText(w.max + "° / " + w.min + "°");
        weatherHumidity.setText(w.humidity + "%");
        weatherWind.setText(w.wind + " km/h");
    }

    private void fetchWeather(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && lastWeatherFetch != 0 && now - lastWeatherFetch < WEATHER_EVERY_MS) return;
        lastWeatherFetch = now;
        Weather.fetch(this, this::showWeather);
    }

    private final Runnable periodic = new Runnable() {
        public void run() {
            if (Ui.isDark(MainActivity.this) != createdDark) {
                recreate();
                return;
            }
            updateGreeting();
            updateStats();
            updateLights();
            fetchWeather(false);
            handler.postDelayed(this, 30 * 1000);
        }
    };

    // ---------- Car lights: ILL wire, night by the clock, or touch ----------

    private final android.content.BroadcastReceiver illReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, Intent i) {
            android.util.Log.d("CarLights", "ILL broadcast extras=" + i.getExtras());
            // Give the kernel a moment to store the state, then follow it (theme and headlights).
            handler.postDelayed(() -> {
                if (Ui.isDark(MainActivity.this) != createdDark) recreate();
                else updateLights();
            }, 1500);
        }
    };

    /** The kernel keeps the light state as bit 2 of mcu_no_source_device_state. */
    private boolean carLightsOn() {
        int state = android.provider.Settings.System.getInt(getContentResolver(), "mcu_no_source_device_state", 0);
        return (state & 2) != 0;
    }

    /** Headlights on by themselves: the car's lights are on, or it is 6 p.m. to 6 a.m. */
    private boolean lightsAuto() {
        return carLightsOn() || Ui.isNightHour();
    }

    private boolean lightsShown() {
        return lightsManual != null ? lightsManual : lightsAuto();
    }

    /** Follows the automatic rule; a touch overrides it until the rule itself changes. */
    private void updateLights() {
        boolean auto = lightsAuto();
        if (auto != lightsAutoLast) {
            lightsAutoLast = auto;
            lightsManual = null;
        }
        showCarLights(lightsShown(), true);
    }

    private void showCarLights(boolean on, boolean animate) {
        // Cross-fade between the "lights off" and "lights on" photos.
        if (!hasLightsImage) return;
        if (!animate) {
            carLights.setAlpha(on ? 1f : 0f);
            car.setAlpha(on ? 0f : 1f);
            return;
        }
        carLights.animate().alpha(on ? 1f : 0f).setDuration(600).start();
        car.animate().alpha(on ? 0f : 1f).setDuration(600).start();
    }

    // ---------- Music (any source: Bluetooth, USB or any music app) ----------

    private void fillMusicApps() {
        musicApps.removeAllViews();
        java.util.List<MusicApps.App> apps = MusicApps.find(this);
        int shown = Math.min(apps.size(), apps.size() > 3 ? 2 : 3);
        for (int i = 0; i < shown; i++) {
            MusicApps.App a = apps.get(i);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(a.icon);
            if ("Bluetooth".equals(a.label) && a.launch.getComponent() != null
                    && a.launch.getComponent().getPackageName().equals(getPackageName())) {
                iv.setColorFilter(Ui.ACCENT);
            }
            iv.setPadding(dp(3), dp(3), dp(3), dp(3));
            iv.setContentDescription(a.label);
            Ui.pressable(iv, 0.85f);
            iv.setOnClickListener(v -> MusicApps.play(this, a));
            iv.setOnLongClickListener(v -> {
                MusicApps.launch(this, a);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
            lp.leftMargin = dp(4);
            musicApps.addView(iv, lp);
        }
        if (apps.size() > shown) {
            ImageView more = control(R.drawable.ic_apps, false, () -> MusicApps.choose(this, apps));
            more.setPadding(dp(6), dp(6), dp(6), dp(6));
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(30), dp(30));
            mlp.leftMargin = dp(4);
            musicApps.addView(more, mlp);
        }
    }

    /** Opens what is playing; with nothing played yet, lets the driver pick a music app. */
    private void openMusic() {
        if (!nowPlaying.hasAccess()) {
            safeStart(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            return;
        }
        if (nowPlaying.current() != null) {
            nowPlaying.open(this);
            return;
        }
        java.util.List<MusicApps.App> apps = MusicApps.find(this);
        if (apps.isEmpty()) toast("No se encontró una app de música");
        else MusicApps.choose(this, apps);
    }

    @Override
    public void onNowPlayingChanged() {
        NowPlaying.Source s = nowPlaying.current();
        NowPlaying.Track t = s == null ? null : nowPlaying.track(s);
        boolean empty = t == null || t.title.isEmpty();
        String app = s == null ? "" : nowPlaying.label(s);
        songTitle.setText(empty ? "Nada sonando" : t.title);
        songArtist.setText(empty ? "Elige una app de música"
                : t.artist.isEmpty() ? app : app.isEmpty() ? t.artist : t.artist + " · " + app);
        if (!nowPlaying.hasAccess() && s != NowPlaying.Source.BT && s != NowPlaying.Source.LOCAL) {
            // Without notification access the launcher cannot see YouTube, Spotify…
            songTitle.setText("Permite ver la música");
            songArtist.setText("Toca aquí para activarlo");
        }

        // The playing app's own icon; a note or Bluetooth symbol otherwise.
        android.graphics.drawable.Drawable appIcon = null;
        if (s == NowPlaying.Source.APP) {
            try {
                appIcon = getPackageManager().getApplicationIcon(nowPlaying.appPackage());
            } catch (Exception ignored) { }
        }
        if (appIcon != null) {
            sourceIcon.clearColorFilter();
            sourceIcon.setImageDrawable(appIcon);
        } else {
            sourceIcon.setImageResource(s == NowPlaying.Source.BT ? R.drawable.ic_bluetooth : R.drawable.ic_music);
            sourceIcon.setColorFilter(txt);
        }
        updateProgress();
        playBtn.setImageResource(t != null && t.playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private void updateProgress() {
        NowPlaying.Source s = nowPlaying.current();
        NowPlaying.Track t = s == null ? null : nowPlaying.track(s);
        boolean show = s == NowPlaying.Source.APP && t != null && t.duration > 0;
        progress.setLevel(show ? t.positionNow() / (float) t.duration : 0);
    }

    private final Runnable progressTick = new Runnable() {
        public void run() {
            updateProgress();
            handler.postDelayed(this, 1000);
        }
    };

    // ---------- GPS: speedometer and map position ----------

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location l) {
            onFix(l);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) { }

        @Override
        public void onProviderEnabled(String provider) { }

        @Override
        public void onProviderDisabled(String provider) { }
    };

    private void onFix(Location l) {
        boolean gps = LocationManager.GPS_PROVIDER.equals(l.getProvider());
        // A recent GPS fix wins over the coarse network position for the map.
        if (!gps && SystemClock.elapsedRealtime() - lastGpsAt < GPS_STALE_MS) return;
        map.setLocation(l);
        if (!gps) return;
        float kmh;
        if (l.hasSpeed()) {
            kmh = l.getSpeed() * 3.6f;
        } else if (lastGps != null && l.getTime() > lastGps.getTime()) {
            kmh = lastGps.distanceTo(l) / ((l.getTime() - lastGps.getTime()) / 1000f) * 3.6f;
        } else {
            kmh = 0;
        }
        if (kmh < 2) kmh = 0; // GPS jitter while parked
        lastGps = l;
        lastGpsAt = SystemClock.elapsedRealtime();
        gauge.setSpeed(kmh);
        map.setSpeed(kmh);
    }

    private final Runnable gpsWatch = new Runnable() {
        public void run() {
            if (SystemClock.elapsedRealtime() - lastGpsAt > GPS_STALE_MS) gauge.setNoFix(gpsProblem());
            handler.postDelayed(this, 2000);
        }
    };

    /** Why there is no GPS speed right now, as shown next to the speedometer. */
    private String gpsProblem() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "toca para permitir";
        }
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm.getProvider(LocationManager.GPS_PROVIDER) == null) return "sin GPS";
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return "activa la ubicación";
        return "buscando GPS";
    }

    /** Tap on the speedometer: fix whatever keeps the GPS from working. */
    private void fixGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm.getProvider(LocationManager.GPS_PROVIDER) != null && !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            safeStart(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        }
    }

    private void startLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location last = null;
        for (String p : lm.getAllProviders()) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (last == null || l.getTime() > last.getTime())) last = l;
            } catch (SecurityException ignored) { }
        }
        if (last != null) map.setLocation(last);
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener);
        } catch (Exception ignored) { } // no GPS receiver
        try {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10_000, 0, locationListener);
        } catch (Exception ignored) { }
        handler.post(gpsWatch);
    }

    private void stopLocation() {
        handler.removeCallbacks(gpsWatch);
        ((LocationManager) getSystemService(LOCATION_SERVICE)).removeUpdates(locationListener);
    }

    // ---------- Dock ----------

    private View buildDock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        gloss(dock, 16);
        dock.addView(dockIcon(R.drawable.ic_compass, "Mapa", this::openMaps), weight());
        dock.addView(dockIcon(R.drawable.ic_bluetooth, "Bluetooth", this::openBluetooth), weight());
        dock.addView(dockIcon(R.drawable.ic_wifi, "WiFi", this::openWifi), weight());
        dock.addView(dockIcon(R.drawable.ic_music, "Música", this::openMusic), weight());

        FrameLayout center = new FrameLayout(this);
        RingView ring = new RingView(this);
        ring.setContentDescription("Apps");
        center.addView(ring, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
        Ui.pressable(ring, 0.88f);
        ring.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        dock.addView(center, weight());

        dock.addView(dockIcon(R.drawable.ic_video, "Video", this::openVideo), weight());
        dock.addView(dockIcon(R.drawable.ic_settings, "Ecualizador", this::openEqualizer), weight());
        dock.addView(dockIcon(R.drawable.ic_w_moon, "Apagar pantalla",
                () -> startActivity(new Intent(this, ScreenOffActivity.class))), weight());
        dock.addView(dockIcon(R.drawable.ic_gear, "Ajustes", this::openSettings), weight());
        return dock;
    }

    private View dockIcon(int icon, String name, Runnable action) {
        FrameLayout f = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(txt);
        iv.setContentDescription(name);
        iv.setPadding(dp(11), dp(11), dp(11), dp(11));
        f.addView(iv, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
        Ui.pressable(iv, 0.85f);
        iv.setOnClickListener(v -> action.run());
        return f;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, -1, 1f);
    }

    // ---------- Shortcuts: NWD screens, or the head unit's own apps elsewhere ----------

    /** Wi-Fi settings: the system screen, or the wireless settings on units without it. */
    private void openWifi() {
        if (!safeStart(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                && !safeStart(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))) {
            openSettings();
        }
    }

    private void openBluetooth() {
        if (Profile.isNwd(this)) startActivity(new Intent(this, BtActivity.class));
        else if (!launchByKeyword("bluetooth", "bt music", "btmusic", "phone link"))
            safeStart(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
    }

    private void openVideo() {
        Intent yt = getPackageManager().getLaunchIntentForPackage("com.google.android.youtube");
        if (yt != null && safeStart(yt)) return;
        if (!launchByKeyword("youtube", "video", "vídeo", "movie")) toast("No se encontró una app de video");
    }

    private void openEqualizer() {
        if (Profile.isNwd(this)) {
            startActivity(new Intent(this, EqActivity.class));
            return;
        }
        Intent eq = new Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
        if (!safeStart(eq) && !launchByKeyword("ecualiz", "equaliz", "audio", "sonido", "dsp"))
            toast("No se encontró un ecualizador");
    }

    private void openSettings() {
        if (Profile.isNwd(this)) Ui.launch(this, SETTINGS);
        else safeStart(new Intent(android.provider.Settings.ACTION_SETTINGS));
    }

    private void openMaps() {
        Intent maps = getPackageManager().getLaunchIntentForPackage("com.google.android.apps.maps");
        if (maps == null || !safeStart(maps)) {
            safeStart(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=")));
        }
    }

    /** Opens the first installed app whose name or package matches one of the keywords. */
    private boolean launchByKeyword(String... keywords) {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            String label = (ri.loadLabel(pm) + " " + ri.activityInfo.packageName).toLowerCase();
            for (String k : keywords) {
                if (label.contains(k)) {
                    Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
                    return safeStart(i);
                }
            }
        }
        return false;
    }

    private boolean safeStart(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    // ---------- Lifecycle & helpers ----------

    @Override
    protected void onResume() {
        super.onResume();
        if (Ui.isDark(this) != createdDark || !profileStamp().equals(createdProfile)) {
            // theme (setting, night or car lights) or profile (name, car photo) changed
            recreate();
            return;
        }
        updateGreeting();
        updateStats();
        updateThemePill();
        showWeather(Weather.cached(this));
        registerReceiver(illReceiver, new android.content.IntentFilter("com.nwd.ACTION_ILL_STATE_CHANGE"));
        boolean auto = lightsAuto();
        if (auto != lightsAutoLast) {
            lightsAutoLast = auto;
            lightsManual = null;
        }
        android.util.Log.d("CarLights", "onResume lights=" + lightsShown());
        showCarLights(lightsShown(), false);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        handler.post(periodic);
        startLocation();
        map.onResume();
        nowPlaying.addListener(this);
        onNowPlayingChanged();
        handler.post(progressTick);
        fillMusicApps(); // apps installed or removed meanwhile
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(illReceiver);
        handler.removeCallbacks(periodic);
        stopLocation();
        handler.removeCallbacks(progressTick);
        map.onPause();
        nowPlaying.removeListener(this);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            fetchWeather(true);
            stopLocation();
            startLocation();
        }
    }

    private TextView left(TextView tv) {
        tv.setGravity(Gravity.START);
        return tv;
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }

    static void pressable(View v) {
        Ui.pressable(v, 0.94f);
    }

    @Override
    public void onBackPressed() {
        // Home screen: nothing to go back to.
    }
}
