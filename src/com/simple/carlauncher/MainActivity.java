package com.simple.carlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
 * Home screen: one glass panel with the Kia logo on top. Left: time, greeting and the music
 * playing. Centre: the Sorento on a city highway (headlights on at night or
 * on touch) and a wide GPS speedometer. Right: weather, today's trip, theme pill and music controls.
 * Icon dock below. City background by day or by night; auto switches at 6 p.m. / car lights.
 */
public class MainActivity extends Activity implements NowPlaying.Listener {

    private static final String EQ = "com.nwd.audioset/.home_horizontalActivity";
    private static final String SETTINGS = "com.android.car.setting/.home_horizontalActivity";

    private static final long WEATHER_EVERY_MS = 30 * 60 * 1000;

    private final Handler handler = new Handler();
    private NowPlaying nowPlaying;

    // Home palette (day or night).
    private boolean dark;
    private int txt, sub, glass, line;
    private SceneView scene;

    private TextView greeting, nameLine, weatherTemp, weatherDesc, weatherCity, weatherFeels, weatherMinMax,
            weatherHumidity, weatherWind;
    private ImageView weatherIcon;
    private ImageView car, carLights;
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
    private GaugeView gauge;
    private Location lastGps;
    private long lastGpsAt = -GPS_STALE_MS;

    private String profileStamp() {
        return Profile.name(this) + "|" + Profile.carPhotoFile(this).lastModified();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dark = Ui.isDark(this);
        // Light text on the city picture, day and night.
        txt = 0xFFF4F6F8; sub = 0xFFCDD2DA; glass = 0x0EFFFFFF; line = 0x3CFFFFFF;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        scene = new SceneView(this, dark);
        root.addView(scene, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(26), dp(12), dp(8));

        FrameLayout panel = new FrameLayout(this);
        panel.setBackground(glassBg(22));
        panel.setClipToOutline(true);
        LinearLayout cols = new LinearLayout(this);
        cols.addView(buildLeft(), new LinearLayout.LayoutParams(0, -1, 0.3f));
        cols.addView(buildCenter(), new LinearLayout.LayoutParams(0, -1, 0.5f));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(220), -1);
        rlp.setMargins(dp(8), dp(14), dp(18), dp(10));
        cols.addView(buildRight(), rlp);
        panel.addView(cols, new FrameLayout.LayoutParams(-1, -1));
        content.addView(panel, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, dp(62));
        dlp.topMargin = dp(8);
        content.addView(buildDock(), dlp);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        root.addView(buildLogo(), new FrameLayout.LayoutParams(-1, dp(34), Gravity.TOP));
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

    private GradientDrawable glassBg(int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(glass);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), line);
        return g;
    }

    /** Kia logo centred on the panel's top edge, between two thin lines. */
    private View buildLogo() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        View l1 = new View(this);
        l1.setBackgroundColor(0x66FFFFFF);
        row.addView(l1, new LinearLayout.LayoutParams(dp(110), dp(1)));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.kia_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(130), dp(32));
        llp.setMargins(dp(14), 0, dp(14), 0);
        row.addView(logo, llp);
        View l2 = new View(this);
        l2.setBackgroundColor(0x66FFFFFF);
        row.addView(l2, new LinearLayout.LayoutParams(dp(110), dp(1)));
        return row;
    }

    // ---------- Left: time, greeting, music ----------

    private View buildLeft() {
        FrameLayout col = new FrameLayout(this);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(20), dp(14), 0, 0);
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setGravity(Gravity.BOTTOM);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("H:mm");
        time.setTextSize(46);
        time.setTextColor(txt);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        time.setIncludeFontPadding(false);
        timeRow.addView(time);
        TextClock ampm = new TextClock(this);
        ampm.setFormat12Hour("a");
        ampm.setFormat24Hour("");
        ampm.setAllCaps(true);
        ampm.setTextSize(15);
        ampm.setTextColor(txt);
        ampm.setTypeface(Typeface.DEFAULT_BOLD);
        ampm.setPadding(dp(6), 0, 0, dp(6));
        timeRow.addView(ampm);
        top.addView(timeRow);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE  d 'de' MMMM");
        date.setFormat24Hour("EEEE  d 'de' MMMM");
        date.setAllCaps(true);
        date.setTextSize(12);
        date.setTextColor(sub);
        date.setLetterSpacing(0.04f);
        date.setPadding(0, dp(4), 0, 0);
        top.addView(date);

        greeting = left(Ui.text(this, "", 12, sub));
        greeting.setAllCaps(true);
        greeting.setLetterSpacing(0.06f);
        greeting.setPadding(0, dp(14), 0, 0);
        top.addView(greeting);
        nameLine = left(Ui.text(this, "", 28, txt));
        nameLine.setIncludeFontPadding(false);
        nameLine.setPadding(0, dp(2), 0, 0);
        top.addView(nameLine);
        col.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        // Music now playing (title, artist · app) with the music apps above it.
        LinearLayout music = new LinearLayout(this);
        music.setOrientation(LinearLayout.VERTICAL);
        music.setPadding(dp(20), 0, dp(8), dp(14));
        musicApps = new LinearLayout(this);
        musicApps.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, dp(36));
        alp.bottomMargin = dp(6);
        music.addView(musicApps, alp);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        sourceIcon = new ImageView(this);
        titleRow.addView(sourceIcon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        songTitle = left(Ui.text(this, "", 18, txt));
        songTitle.setTypeface(Typeface.DEFAULT_BOLD);
        songTitle.setPadding(dp(10), 0, 0, 0);
        titleRow.addView(songTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        music.addView(titleRow);
        songArtist = left(Ui.text(this, "", 12, sub));
        songArtist.setPadding(0, dp(4), 0, 0);
        music.addView(songArtist);
        Ui.pressable(music, 0.98f);
        music.setOnClickListener(v -> openMusic());
        col.addView(music, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        fillMusicApps();
        return col;
    }

    private void updateGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean night = h >= 19 || h < 5;
        greeting.setText(h < 5 ? "Buenas noches" : h < 12 ? "Buenos días" : h < 19 ? "Buenas tardes" : "Buenas noches");
        String name = Profile.name(this).toUpperCase(Locale.getDefault());
        SpannableStringBuilder sb = new SpannableStringBuilder(name);
        sb.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int s = sb.length();
        sb.append("   ").append(night ? "¿A dónde vamos esta noche?" : "¿A dónde vamos hoy?");
        sb.setSpan(new AbsoluteSizeSpan(13, true), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(sub), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        nameLine.setText(sb);
    }

    // ---------- Centre: today's trip, the car, the speedometer ----------

    private View buildCenter() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        // The user's own car (photo with background removed); headlights glow at night or on touch.
        FrameLayout carArea = new FrameLayout(this);
        FrameLayout carBox = new FrameLayout(this);
        car = new ImageView(this);
        car.setScaleType(ImageView.ScaleType.FIT_CENTER);
        carLights = new ImageView(this);
        carLights.setScaleType(ImageView.ScaleType.FIT_CENTER);
        carLights.setAlpha(0f);
        android.graphics.Bitmap own = Profile.carPhoto(this);
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
            car.setColorFilter(0x40FFFFFF);
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

        TextView model = Ui.text(this, spaced(Profile.MODEL), 14, sub);
        model.setLetterSpacing(0.2f);
        carArea.addView(model, new FrameLayout.LayoutParams(-1, -2));
        carArea.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) carArea.post(() -> placeCar(carArea, carBox, model));
        });
        col.addView(carArea, new LinearLayout.LayoutParams(-1, 0, 1f));

        gauge = new GaugeView(this, txt, sub);
        gauge.setOnClickListener(v -> fixGps());
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, dp(128));
        glp.bottomMargin = dp(6);
        col.addView(gauge, glp);
        return col;
    }

    private static String spaced(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) b.append(b.length() == 0 ? "" : " ").append(ch);
        return b.toString();
    }

    private void updateStats() {
        float km = Odometer.todayKm(this);
        statKm.setText(String.format(new Locale("es", "EC"), "%.1f", km).replace('.', ',') + " km");
        int min = Odometer.todayMovingMinutes(this);
        statMoving.setText(min < 60 ? min + " min" : min / 60 + " h " + (min % 60 < 10 ? "0" : "") + min % 60);
        if (!Odometer.isConfigured(this)) {
            statOil.setText("Configurar");
            statOil.setTextColor(txt);
        } else {
            int left = Odometer.remainingKm(this);
            statOil.setText(left < 0 ? "¡Ya toca!" : OilActivity.fmt(left) + " km");
            statOil.setTextColor(left < 0 ? Ui.ERROR : left <= Odometer.WARN_KM ? Ui.WARN : txt);
        }
    }

    /** Car as large as fits, the model name just below; the road and shadow are drawn to match. */
    private void placeCar(FrameLayout area, View carBox, TextView model) {
        int w = area.getWidth(), h = area.getHeight();
        if (w == 0 || h == 0) return;
        android.graphics.drawable.Drawable d = car.getDrawable();
        float ratio = d != null && d.getIntrinsicWidth() > 0
                ? d.getIntrinsicHeight() / (float) d.getIntrinsicWidth() : 0.56f;
        model.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int mh = model.getMeasuredHeight(), gap = dp(6);
        int cw = Math.round(w * 0.86f), ch = Math.round(cw * ratio);
        int max = Math.round((h - mh - gap) * 0.94f);
        if (ch > max) {
            ch = max;
            cw = Math.round(ch / ratio);
        }
        int top = Math.round((h - ch - mh - gap) * 0.5f);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(cw, ch, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        clp.topMargin = top;
        carBox.setLayoutParams(clp);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        mlp.topMargin = top + ch + gap;
        model.setLayoutParams(mlp);
        // Tell the background where the car stands so the road runs under its wheels.
        int[] at = new int[2], sc = new int[2];
        area.getLocationInWindow(at);
        scene.getLocationInWindow(sc);
        float left = at[0] - sc[0] + (w - cw) / 2f, topY = at[1] - sc[1] + top;
        scene.setCar(left, topY, left + cw, topY + ch);
    }

    // ---------- Right: weather, today's trip, theme, music controls ----------

    private View buildRight() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout weather = new LinearLayout(this);
        weather.setOrientation(LinearLayout.VERTICAL);
        weatherCity = left(Ui.text(this, "", 17, txt));
        weather.addView(weatherCity);
        LinearLayout now = new LinearLayout(this);
        now.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new ImageView(this);
        weatherIcon.setImageResource(R.drawable.ic_w_cloud);
        weatherIcon.setColorFilter(txt);
        now.addView(weatherIcon, new LinearLayout.LayoutParams(dp(56), dp(56)));
        weatherTemp = Ui.text(this, "--°", 52, txt);
        weatherTemp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        weatherTemp.setIncludeFontPadding(false);
        weatherTemp.setGravity(Gravity.END);
        now.addView(weatherTemp, new LinearLayout.LayoutParams(0, -2, 1f));
        weather.addView(now);
        weatherFeels = Ui.text(this, "", 11, sub);
        weatherFeels.setAllCaps(true);
        weatherFeels.setGravity(Gravity.END);
        weather.addView(weatherFeels);
        weatherDesc = left(Ui.text(this, "Cargando el clima…", 17, txt));
        weatherDesc.setPadding(0, dp(8), 0, dp(2));
        weather.addView(weatherDesc);
        weatherMinMax = row(weather, "MÁX / MÍN");
        weatherHumidity = row(weather, "HUMEDAD");
        weatherWind = row(weather, "VIENTO");
        Ui.pressable(weather, 0.98f);
        weather.setOnClickListener(v -> fetchWeather(true));
        col.addView(weather);

        View div = new View(this);
        div.setBackgroundColor(line);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, dp(1));
        dlp.setMargins(0, dp(8), 0, dp(8));
        col.addView(div, dlp);

        // Today's trip, in small rows like the weather ones; the last one opens the oil screen.
        LinearLayout trip = new LinearLayout(this);
        trip.setOrientation(LinearLayout.VERTICAL);
        statKm = row(trip, "RUTA DE HOY");
        statMoving = row(trip, "EN MOVIMIENTO");
        statOil = row(trip, "MANTENIMIENTO EN");
        View oilRow = (View) statOil.getParent();
        Ui.pressable(oilRow, 0.96f);
        oilRow.setOnClickListener(v -> startActivity(new Intent(this, OilActivity.class)));
        col.addView(trip);
        col.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1f));

        themePill = Ui.text(this, "", 12, txt);
        themePill.setTypeface(Typeface.DEFAULT_BOLD);
        themePill.setLetterSpacing(0.06f);
        themePill.setPadding(dp(14), dp(6), dp(14), dp(6));
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(16));
        pill.setStroke(dp(1), sub);
        themePill.setBackground(pill);
        Ui.pressable(themePill, 0.94f);
        themePill.setOnClickListener(v -> cycleTheme());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
        plp.setMargins(dp(40), dp(8), 0, dp(8));
        col.addView(themePill, plp);
        updateThemePill();

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(control(R.drawable.ic_prev, false, () -> nowPlaying.skip(false)), new LinearLayout.LayoutParams(0, dp(48), 1f));
        playBtn = control(R.drawable.ic_play, true, () -> nowPlaying.playPause());
        controls.addView(playBtn, new LinearLayout.LayoutParams(dp(50), dp(50)));
        controls.addView(control(R.drawable.ic_next, false, () -> nowPlaying.skip(true)), new LinearLayout.LayoutParams(0, dp(48), 1f));
        col.addView(controls);

        progress = new LevelBar(this);
        progress.setTrack(0x33FFFFFF);
        LinearLayout.LayoutParams prl = new LinearLayout.LayoutParams(-1, dp(3));
        prl.topMargin = dp(8);
        col.addView(progress, prl);
        return col;
    }

    private TextView row(LinearLayout parent, String caption) {
        LinearLayout r = new LinearLayout(this);
        r.setPadding(0, dp(3), 0, 0);
        TextView c = left(Ui.text(this, caption, 11, sub));
        r.addView(c, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView v = Ui.text(this, "", 13, txt);
        r.addView(v);
        parent.addView(r);
        return v;
    }

    private ImageView control(int icon, boolean round, Runnable action) {
        ImageView b = new ImageView(this);
        b.setImageResource(icon);
        b.setColorFilter(txt);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = dp(round ? 13 : 12);
        b.setPadding(pad, pad, pad, pad);
        if (round) {
            GradientDrawable c = Ui.circle(0x1AFFFFFF);
            c.setStroke(dp(1), sub);
            b.setBackground(c);
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
        int shown = Math.min(apps.size(), apps.size() > 5 ? 4 : 5);
        for (int i = 0; i < shown; i++) {
            MusicApps.App a = apps.get(i);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(a.icon);
            if ("Bluetooth".equals(a.label) && a.launch.getComponent() != null
                    && a.launch.getComponent().getPackageName().equals(getPackageName())) {
                iv.setColorFilter(Ui.ACCENT);
            }
            iv.setPadding(dp(5), dp(5), dp(5), dp(5));
            iv.setBackground(Ui.circle(0x1FFFFFFF));
            iv.setContentDescription(a.label);
            Ui.pressable(iv, 0.85f);
            iv.setOnClickListener(v -> MusicApps.play(this, a));
            iv.setOnLongClickListener(v -> {
                MusicApps.launch(this, a);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
            lp.rightMargin = dp(8);
            musicApps.addView(iv, lp);
        }
        if (apps.size() > shown) {
            ImageView more = control(R.drawable.ic_apps, false, () -> MusicApps.choose(this, apps));
            more.setPadding(dp(7), dp(7), dp(7), dp(7));
            more.setBackground(Ui.circle(0x1FFFFFFF));
            musicApps.addView(more, new LinearLayout.LayoutParams(dp(34), dp(34)));
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

    // ---------- GPS: speedometer ----------

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
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener);
        } catch (Exception ignored) { } // no GPS receiver
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
        dock.setBackground(glassBg(18));
        dock.addView(dockIcon(R.drawable.ic_compass, "Mapa", this::openMaps), weight());
        dock.addView(dockIcon(R.drawable.ic_bluetooth, "Bluetooth", this::openBluetooth), weight());
        dock.addView(dockIcon(R.drawable.ic_wifi, "WiFi", this::openWifi), weight());
        dock.addView(dockIcon(R.drawable.ic_music, "Música", this::openMusic), weight());

        FrameLayout center = new FrameLayout(this);
        RingView ring = new RingView(this);
        ring.setContentDescription("Apps");
        center.addView(ring, new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER));
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
        iv.setColorFilter(0xE6FFFFFF);
        iv.setContentDescription(name);
        iv.setPadding(dp(12), dp(12), dp(12), dp(12));
        f.addView(iv, new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER));
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
