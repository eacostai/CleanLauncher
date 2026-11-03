package com.simple.carlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Calendar;

public class MainActivity extends Activity implements NowPlaying.Listener {


    private static final String MAPS = "com.google.android.apps.maps/com.google.android.maps.MapsActivity";
    private static final String EQ = "com.nwd.audioset/.home_horizontalActivity";
    private static final String SETTINGS = "com.android.car.setting/.home_horizontalActivity";

    private static final long WEATHER_EVERY_MS = 30 * 60 * 1000;

    private final Handler handler = new Handler();
    private NowPlaying nowPlaying;

    private TextView greeting, weatherTemp, weatherDesc;
    private ImageView weatherIcon;
    private ImageView car, carLights;
    private View beam;
    private boolean hasLightsImage;
    private TextView oilInfo;
    private boolean createdDark;
    private String createdProfile;

    private String profileStamp() {
        return Profile.name(this) + "|" + Profile.carPhotoFile(this).lastModified();
    }
    private GradientDrawable oilBg;
    private long lastWeatherFetch;

    private ImageView art, playBtn;
    private TextView songTitle, songArtist;
    private LinearLayout musicApps;
    private LevelBar progress;

    private static final long GPS_STALE_MS = 5000;
    private TextView speedNum, gpsNote;
    private MapView map;
    private Location lastGps;
    private long lastGpsAt = -GPS_STALE_MS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Tesla-style layout: car panel on the left, map filling the rest, fixed bar at the bottom.
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.background(this));

        LinearLayout top = new LinearLayout(this);
        top.addView(buildCarPanel(), new LinearLayout.LayoutParams(0, -1, 0.36f));
        top.addView(buildMap(), new LinearLayout.LayoutParams(0, -1, 0.64f));
        root.addView(top, new LinearLayout.LayoutParams(-1, 0, 1f));

        root.addView(buildBar(), new LinearLayout.LayoutParams(-1, dp(76)));
        setContentView(root);

        nowPlaying = NowPlaying.get(this);
        createdDark = Ui.isDark(this);
        createdProfile = profileStamp();
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

    // ---------- Left panel: speed, time, weather, car, greeting ----------

    private View buildCarPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(18), dp(20), dp(12));

        // Speed (GPS) on the left, time and weather on the right.
        LinearLayout head = new LinearLayout(this);
        LinearLayout speedBox = new LinearLayout(this);
        speedBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout speedRow = new LinearLayout(this);
        speedRow.setGravity(Gravity.BOTTOM);
        speedNum = left(Ui.text(this, "0", 64, Ui.text1(this)));
        speedNum.setTypeface(Typeface.DEFAULT_BOLD);
        speedNum.setIncludeFontPadding(false);
        speedRow.addView(speedNum);
        TextView unit = Ui.text(this, "KM/H", 16, Ui.text2(this));
        unit.setTypeface(Typeface.DEFAULT_BOLD);
        unit.setLetterSpacing(0.12f);
        unit.setPadding(dp(8), 0, 0, dp(8));
        speedRow.addView(unit);
        speedBox.addView(speedRow);
        gpsNote = left(Ui.text(this, "", 12, Ui.text2(this)));
        speedBox.addView(gpsNote);
        Ui.pressable(speedBox, 0.96f);
        speedBox.setOnClickListener(v -> fixGps());
        head.addView(speedBox, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setGravity(Gravity.END);
        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("hh:mm");
        time.setTextSize(30);
        time.setTextColor(Ui.text1(this));
        time.setIncludeFontPadding(false);
        side.addView(time);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEE d MMM");
        date.setFormat12Hour("EEE d MMM");
        date.setTextSize(13);
        date.setTextColor(Ui.text2(this));
        side.addView(date);
        LinearLayout weather = new LinearLayout(this);
        weather.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        weatherIcon = new ImageView(this);
        weatherIcon.setImageResource(R.drawable.ic_w_cloud);
        weatherIcon.setColorFilter(0xFFFFE07A);
        weather.addView(weatherIcon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        weatherTemp = Ui.text(this, "--°", 15, Ui.text1(this));
        weatherTemp.setPadding(dp(6), 0, dp(6), 0);
        weather.addView(weatherTemp);
        weatherDesc = Ui.text(this, "", 13, Ui.text2(this));
        weather.addView(weatherDesc);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-2, -2);
        wlp.topMargin = dp(4);
        side.addView(weather, wlp);
        Ui.pressable(side, 0.96f);
        side.setOnClickListener(v -> fetchWeather(true));
        head.addView(side, new LinearLayout.LayoutParams(-2, -2));
        panel.addView(head);

        // The user's own car (photo with background removed). When the car's lights are on the
        // lights-on photo fades in, with the beam lighting the floor in front of it.
        // Own photo (shared builds) > bundled car with lights-on variant (owner's build) > icon.
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
        carBox.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
        // Long press: preview the lights on a unit without the lights wire (tablet, tests).
        carBox.setOnLongClickListener(v -> {
            boolean on = carLights.getAlpha() < 0.5f;
            showCarLights(on);
            if (on) handler.postDelayed(() -> showCarLights(carLightsOn()), 5000);
            return true;
        });

        beam = new View(this);
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setShape(GradientDrawable.OVAL);
        glow.setColors(new int[]{0x66FFF4D6, 0x22FFF4D6, 0x00FFF4D6});
        beam.setBackground(glow);
        beam.setAlpha(0f);
        // Floor shadow under the car, always on.
        View floor = new View(this);
        GradientDrawable shade = new GradientDrawable();
        shade.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        shade.setShape(GradientDrawable.OVAL);
        shade.setColors(new int[]{0x99000000, 0x00000000});
        floor.setBackground(shade);

        // Model badge and greeting under the car.
        LinearLayout label = new LinearLayout(this);
        label.setOrientation(LinearLayout.VERTICAL);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView model = Ui.text(this, Profile.MODEL, 18, Ui.text1(this));
        model.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        model.setLetterSpacing(0.6f);
        label.addView(model);
        TextView hello = Ui.text(this, Profile.welcome(this), 22, Ui.text1(this));
        hello.setTypeface(Typeface.DEFAULT_BOLD);
        hello.setPadding(0, dp(8), 0, 0);
        label.addView(hello);
        greeting = Ui.text(this, "", 15, Ui.text2(this));
        label.addView(greeting);

        FrameLayout carArea = new FrameLayout(this);
        carArea.addView(floor, new FrameLayout.LayoutParams(0, 0));
        carArea.addView(beam, new FrameLayout.LayoutParams(0, 0));
        carArea.addView(carBox, new FrameLayout.LayoutParams(0, 0));
        carArea.addView(label, new FrameLayout.LayoutParams(-1, -2));
        carArea.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) carArea.post(() -> placeCar(carArea, carBox, floor, label));
        });
        panel.addView(carArea, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Oil status and the music apps of the head unit.
        LinearLayout chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        oilInfo = Ui.text(this, "", 13, Ui.text1(this));
        oilInfo.setPadding(dp(14), dp(8), dp(14), dp(8));
        oilBg = new GradientDrawable();
        oilBg.setCornerRadius(dp(18));
        oilInfo.setBackground(oilBg);
        Ui.pressable(oilInfo, 0.92f);
        oilInfo.setOnClickListener(v -> startActivity(new Intent(this, OilActivity.class)));
        chips.addView(oilInfo);
        musicApps = new LinearLayout(this);
        musicApps.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        chips.addView(musicApps, new LinearLayout.LayoutParams(0, dp(40), 1f));
        panel.addView(chips, new LinearLayout.LayoutParams(-1, -2));
        fillMusicApps();
        return panel;
    }

    /**
     * Sizes the car as large as fits the panel width and places it a little above the middle with
     * the badge and greeting just below; the floor shadow and the headlight beam follow it.
     */
    private void placeCar(FrameLayout area, View carBox, View floor, View label) {
        int w = area.getWidth(), h = area.getHeight();
        if (w == 0 || h == 0) return;
        android.graphics.drawable.Drawable d = car.getDrawable();
        float ratio = d != null && d.getIntrinsicWidth() > 0
                ? d.getIntrinsicHeight() / (float) d.getIntrinsicWidth() : 0.56f;
        label.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int lh = label.getMeasuredHeight(), gap = dp(10);
        int cw = w, ch = Math.round(cw * ratio);
        int max = Math.round((h - lh - gap) * 0.92f);
        if (ch > max) {
            ch = max;
            cw = Math.round(ch / ratio);
        }
        int top = Math.round((h - ch - lh - gap) * 0.4f);
        int left = (w - cw) / 2;
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(cw, ch, Gravity.TOP | Gravity.START);
        clp.setMargins(left, top, 0, 0);
        carBox.setLayoutParams(clp);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Math.round(cw * 0.95f), Math.round(ch * 0.22f),
                Gravity.TOP | Gravity.START);
        flp.setMargins(left + Math.round(cw * 0.03f), top + Math.round(ch * 0.86f), 0, 0);
        floor.setLayoutParams(flp);
        // The car faces front-left: light the floor ahead of the headlights.
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(Math.round(cw * 0.8f), Math.round(ch * 0.45f),
                Gravity.TOP | Gravity.START);
        blp.setMargins(left - Math.round(cw * 0.25f), top + Math.round(ch * 0.62f), 0, 0);
        beam.setLayoutParams(blp);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        llp.topMargin = top + ch + gap;
        label.setLayoutParams(llp);
    }

    private void updateGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(h < 12 ? "Buenos días" : h < 19 ? "Buenas tardes" : "Buenas noches");
    }

    private void showWeather(Weather w) {
        if (w == null) return;
        weatherIcon.setImageResource(w.icon());
        weatherIcon.setColorFilter(w.icon() == R.drawable.ic_w_sun ? 0xFFFFD25A
                : w.icon() == R.drawable.ic_w_moon ? 0xFFDDE4FF : Ui.text1(this));
        weatherTemp.setText(w.temp + "°");
        weatherDesc.setText(w.description());
    }

    private void fetchWeather(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && lastWeatherFetch != 0 && now - lastWeatherFetch < WEATHER_EVERY_MS) return;
        lastWeatherFetch = now;
        Weather.fetch(this, this::showWeather);
    }

    private final Runnable periodic = new Runnable() {
        public void run() {
            if (Ui.isDark(MainActivity.this) != createdDark) recreate();
            updateGreeting();
            fetchWeather(false);
            handler.postDelayed(this, 60 * 1000);
        }
    };

    // ---------- Car lights (ILL wire) ----------

    private final android.content.BroadcastReceiver illReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, Intent i) {
            android.util.Log.d("CarLights", "ILL broadcast extras=" + i.getExtras());
            showCarLights(i.getByteExtra("extra_ill_state", (byte) 0) != 0);
            // Auto theme follows the car's lights; give the kernel a moment to store the state.
            handler.postDelayed(() -> { if (Ui.isDark(MainActivity.this) != createdDark) recreate(); }, 1500);
        }
    };

    /** The kernel keeps the light state as bit 2 of mcu_no_source_device_state. */
    private boolean carLightsOn() {
        int state = android.provider.Settings.System.getInt(getContentResolver(), "mcu_no_source_device_state", 0);
        return (state & 2) != 0;
    }

    private void showCarLights(boolean on) {
        // Cross-fade between the "lights off" and "lights on" photos.
        if (!hasLightsImage) return;
        carLights.animate().alpha(on ? 1f : 0f).setDuration(600).start();
        car.animate().alpha(on ? 0f : 1f).setDuration(600).start();
        beam.animate().alpha(on ? 1f : 0f).setDuration(900).start();
    }

    private void updateOil() {
        if (!Odometer.isConfigured(this)) {
            oilInfo.setText("Aceite: configurar");
            oilBg.setColor(Ui.fill(this));
            return;
        }
        int left = Odometer.remainingKm(this);
        oilInfo.setText(left < 0 ? "Aceite: ¡ya toca el cambio!" : "Aceite: faltan " + OilActivity.fmt(left) + " km");
        oilBg.setColor(left < 0 ? 0xCCE53935 : left <= Odometer.WARN_KM ? 0xCCFF9800 : Ui.fill(this));
    }

    // ---------- Map (fills the right side) ----------

    private View buildMap() {
        FrameLayout box = new FrameLayout(this);
        map = new MapView(this);
        box.addView(map, new FrameLayout.LayoutParams(-1, -1));

        // Search pill, like the car's own navigation: opens Google Maps to type the destination.
        TextView search = left(Ui.text(this, "¿A dónde vamos?", 17, Ui.text2(this)));
        search.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        search.setCompoundDrawablePadding(dp(12));
        search.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Ui.isDark(this) ? Ui.TEXT_2 : 0xFF5F6670));
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPadding(dp(18), 0, dp(24), 0);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(Ui.isDark(this) ? 0xF2151A21 : 0xF2FFFFFF);
        sbg.setCornerRadius(dp(26));
        search.setBackground(sbg);
        search.setElevation(dp(6));
        if (!Ui.isDark(this)) search.setTextColor(0xFF5F6670);
        Ui.pressable(search, 0.97f);
        search.setOnClickListener(v -> openMapsSearch());
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(dp(340), dp(52), Gravity.TOP | Gravity.START);
        slp.setMargins(dp(18), dp(18), 0, 0);
        box.addView(search, slp);

        TextView go = Ui.text(this, "Navegar", 15, Color.WHITE);
        go.setTypeface(Typeface.DEFAULT_BOLD);
        go.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_nav, 0, 0, 0);
        go.setCompoundDrawablePadding(dp(8));
        go.setPadding(dp(18), dp(12), dp(20), dp(12));
        GradientDrawable gbg = new GradientDrawable();
        gbg.setColor(Ui.ACCENT);
        gbg.setCornerRadius(dp(24));
        go.setBackground(gbg);
        go.setElevation(dp(6));
        Ui.pressable(go, 0.94f);
        go.setOnClickListener(v -> openMaps());
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        glp.setMargins(0, 0, dp(18), dp(18));
        box.addView(go, glp);
        return box;
    }

    private void openMapsSearch() {
        Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q="))
                .setPackage("com.google.android.apps.maps");
        if (!safeStart(i)) openMaps();
    }

    // ---------- Bottom bar: apps, mini player, shortcuts ----------

    private View buildBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), 0, dp(10), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.isDark(this) ? 0xFF08090B : 0x33000000);
        bg.setStroke(dp(1), Ui.isDark(this) ? 0xFF1F242C : 0x33FFFFFF);
        bar.setBackground(bg);

        RingView ring = new RingView(this);
        Ui.pressable(ring, 0.88f);
        ring.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        bar.addView(ring, new LinearLayout.LayoutParams(dp(58), dp(58)));

        bar.addView(buildPlayer(), new LinearLayout.LayoutParams(0, dp(58), 1.25f));

        LinearLayout shortcuts = new LinearLayout(this);
        shortcuts.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        shortcuts.addView(barIcon(R.drawable.ic_radio, this::openRadio), weight(1f, 0));
        shortcuts.addView(barIcon(R.drawable.ic_bluetooth, this::openBluetooth), weight(1f, 0));
        shortcuts.addView(barIcon(R.drawable.ic_settings, this::openEqualizer), weight(1f, 0));
        shortcuts.addView(barIcon(R.drawable.ic_w_moon,
                () -> startActivity(new Intent(this, ScreenOffActivity.class))), weight(1f, 0));
        shortcuts.addView(barIcon(R.drawable.ic_gear, this::openSettings), weight(1f, 0));
        bar.addView(shortcuts, new LinearLayout.LayoutParams(0, -1, 1f));
        return bar;
    }

    /** Mini player for whatever plays (Bluetooth, USB or any app), controls always at hand. */
    private View buildPlayer() {
        LinearLayout p = new LinearLayout(this);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(dp(6), dp(5), dp(10), dp(5));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.isDark(this) ? Ui.PANEL : 0x26FFFFFF);
        bg.setCornerRadius(dp(14));
        p.setBackground(bg);

        art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setClipToOutline(true);
        GradientDrawable artBg = new GradientDrawable();
        artBg.setColor(Ui.ACCENT_DEEP);
        artBg.setCornerRadius(dp(10));
        art.setBackground(artBg);
        p.addView(art, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, dp(8), 0);
        songTitle = left(Ui.text(this, "", 15, Ui.text1(this)));
        songTitle.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(songTitle);
        songArtist = left(Ui.text(this, "", 12, Ui.text2(this)));
        texts.addView(songArtist);
        progress = new LevelBar(this);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(3));
        plp.topMargin = dp(5);
        texts.addView(progress, plp);
        p.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        p.addView(Ui.iconButton(this, R.drawable.ic_prev, 40, false, () -> nowPlaying.skip(false)),
                new LinearLayout.LayoutParams(dp(40), dp(40)));
        playBtn = Ui.iconButton(this, R.drawable.ic_play, 46, true, () -> nowPlaying.playPause());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(46), dp(46));
        blp.setMargins(dp(8), 0, dp(8), 0);
        p.addView(playBtn, blp);
        p.addView(Ui.iconButton(this, R.drawable.ic_next, 40, false, () -> nowPlaying.skip(true)),
                new LinearLayout.LayoutParams(dp(40), dp(40)));

        Ui.pressable(texts, 0.97f);
        texts.setOnClickListener(v -> openMusic());
        art.setOnClickListener(v -> openMusic());
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(p, new FrameLayout.LayoutParams(-1, -1));
        wrap.setPadding(dp(12), 0, dp(12), 0);
        return wrap;
    }

    private View barIcon(int icon, Runnable action) {
        FrameLayout f = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(Ui.text1(this));
        iv.setPadding(dp(12), dp(12), dp(12), dp(12));
        f.addView(iv, new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER));
        Ui.pressable(iv, 0.85f);
        iv.setOnClickListener(v -> action.run());
        return f;
    }

    private void fillMusicApps() {
        musicApps.removeAllViews();
        java.util.List<MusicApps.App> apps = MusicApps.find(this);
        int shown = Math.min(apps.size(), apps.size() > 4 ? 3 : 4);
        for (int i = 0; i < shown; i++) {
            MusicApps.App a = apps.get(i);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(a.icon);
            iv.setPadding(dp(5), dp(5), dp(5), dp(5));
            iv.setBackground(Ui.circle(Ui.fill(this)));
            iv.setContentDescription(a.label);
            Ui.pressable(iv, 0.85f);
            iv.setOnClickListener(v -> MusicApps.play(this, a));
            iv.setOnLongClickListener(v -> {
                MusicApps.launch(this, a);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(38), dp(38));
            lp.setMargins(dp(6), 0, 0, 0);
            musicApps.addView(iv, lp);
        }
        if (apps.size() > shown) {
            ImageView more = Ui.iconButton(this, R.drawable.ic_apps, 38, false, () -> MusicApps.choose(this, apps));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(38), dp(38));
            lp.setMargins(dp(6), 0, 0, 0);
            musicApps.addView(more, lp);
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

    // ---------- Map ----------

    private View buildMapCard() {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.card(this));
        card.setClipToOutline(true);

        map = new MapView(this);
        card.addView(map, new FrameLayout.LayoutParams(-1, -1));

        TextView chip = Ui.text(this, "MAPA", 12, Color.WHITE);
        chip.setLetterSpacing(0.1f);
        chip.setPadding(dp(12), dp(5), dp(12), dp(5));
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(0x99000000);
        cbg.setCornerRadius(dp(14));
        chip.setBackground(cbg);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        clp.setMargins(dp(12), dp(12), 0, 0);
        card.addView(chip, clp);

        TextView go = Ui.text(this, "Navegar con Google Maps", 15, Color.WHITE);
        go.setTypeface(Typeface.DEFAULT_BOLD);
        go.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_nav, 0, 0, 0);
        go.setCompoundDrawablePadding(dp(8));
        go.setPadding(dp(18), dp(12), dp(18), dp(12));
        GradientDrawable gbg = new GradientDrawable();
        gbg.setColor(Ui.ACCENT);
        gbg.setCornerRadius(dp(24));
        go.setBackground(gbg);
        Ui.pressable(go, 0.94f);
        go.setOnClickListener(v -> openMaps());
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        glp.bottomMargin = dp(22);
        card.addView(go, glp);

        card.setOnClickListener(v -> openMaps());
        return card;
    }

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
        speedNum.setText(String.valueOf(Math.round(kmh)));
        speedNum.setAlpha(1f);
        gpsNote.setText("");
        map.setSpeed(kmh);
    }

    private final Runnable gpsWatch = new Runnable() {
        public void run() {
            if (SystemClock.elapsedRealtime() - lastGpsAt > GPS_STALE_MS) {
                speedNum.setText("0");
                speedNum.setAlpha(0.45f);
                gpsNote.setText(gpsProblem());
            }
            handler.postDelayed(this, 2000);
        }
    };

    /** Why there is no GPS speed right now, as shown under the speedometer. */
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

    @Override
    public void onNowPlayingChanged() {
        NowPlaying.Source s = nowPlaying.current();
        NowPlaying.Track t = s == null ? null : nowPlaying.track(s);
        boolean empty = t == null || t.title.isEmpty();
        String source = s == null ? "" : nowPlaying.label(s);
        songTitle.setText(empty ? "Nada sonando" : t.title);
        songArtist.setText(empty ? "Toca para elegir la música"
                : t.artist.isEmpty() ? source : t.artist + " · " + source);
        if (!nowPlaying.hasAccess() && s != NowPlaying.Source.BT && s != NowPlaying.Source.LOCAL) {
            // Without notification access the player cannot see YouTube, Spotify…
            songTitle.setText("Permite ver la música");
            songArtist.setText("Toca aquí para activarlo");
        }
        playBtn.setImageResource(t != null && t.playing ? R.drawable.ic_pause : R.drawable.ic_play);

        // Album art; else the playing app's icon; else a note or Bluetooth symbol.
        android.graphics.drawable.Drawable appIcon = null;
        if (s == NowPlaying.Source.APP) {
            try {
                appIcon = getPackageManager().getApplicationIcon(nowPlaying.appPackage());
            } catch (Exception ignored) { }
        }
        if (t != null && t.art != null) {
            art.setPadding(0, 0, 0, 0);
            art.clearColorFilter();
            art.setImageBitmap(t.art);
        } else if (appIcon != null) {
            art.setPadding(dp(6), dp(6), dp(6), dp(6));
            art.clearColorFilter();
            art.setImageDrawable(appIcon);
        } else {
            art.setPadding(dp(12), dp(12), dp(12), dp(12));
            art.setImageResource(s == NowPlaying.Source.BT ? R.drawable.ic_bluetooth : R.drawable.ic_music);
            art.setColorFilter(Color.WHITE);
        }
        updateProgress();
    }

    private void updateProgress() {
        NowPlaying.Source s = nowPlaying.current();
        NowPlaying.Track t = s == null ? null : nowPlaying.track(s);
        boolean show = s == NowPlaying.Source.APP && t != null && t.duration > 0;
        progress.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        if (show) progress.setLevel(t.positionNow() / (float) t.duration);
    }


    private final Runnable progressTick = new Runnable() {
        public void run() {
            updateProgress();
            handler.postDelayed(this, 1000);
        }
    };

    // ---------- Shortcuts: NWD screens, or the head unit's own apps elsewhere ----------

    private void openRadio() {
        if (Profile.isNwd(this)) startActivity(new Intent(this, RadioActivity.class));
        else if (!launchByKeyword("radio", "fm", "tuner")) toast("No se encontró una app de radio");
    }

    private void openBluetooth() {
        if (Profile.isNwd(this)) startActivity(new Intent(this, BtActivity.class));
        else if (!launchByKeyword("bluetooth", "bt music", "btmusic", "phone link"))
            safeStart(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
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
        updateOil();
        showWeather(Weather.cached(this));
        registerReceiver(illReceiver, new android.content.IntentFilter("com.nwd.ACTION_ILL_STATE_CHANGE"));
        boolean lights = hasLightsImage && carLightsOn();
        android.util.Log.d("CarLights", "onResume lights=" + lights);
        carLights.setAlpha(lights ? 1f : 0f);
        car.setAlpha(lights ? 0f : 1f);
        beam.setAlpha(lights ? 1f : 0f);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private TextView left(TextView tv) {
        tv.setGravity(Gravity.START);
        return tv;
    }

    private LinearLayout.LayoutParams weight(float w, int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, w);
        lp.leftMargin = left;
        return lp;
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
