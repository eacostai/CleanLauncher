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

    private TextView greeting, weatherTemp, weatherDesc, weatherCity, weatherExtra;
    private ImageView weatherIcon;
    private GradientDrawable weatherBg;
    private ImageView car, carLights;
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
    private TextView songSource, songTitle, songArtist;
    private LinearLayout musicApps;

    private static final long GPS_STALE_MS = 5000;
    private SpeedView speedView;
    private MapView map;
    private Location lastGps;
    private long lastGpsAt = -GPS_STALE_MS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(4));
        root.setBackground(Ui.background(this));

        LinearLayout cards = new LinearLayout(this);
        cards.addView(buildWeatherCard(), weight(1.2f, 0));

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.addView(buildClockCard(), vweight(0.62f, 0));
        middle.addView(buildMusicCard(), vweight(1.38f, dp(12)));
        cards.addView(middle, weight(1f, dp(12)));

        cards.addView(buildMapCard(), weight(1.05f, dp(12)));
        root.addView(cards, new LinearLayout.LayoutParams(-1, 0, 1f));

        root.addView(buildDock(), new LinearLayout.LayoutParams(-1, dp(84)));
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

    // ---------- Greeting + weather ----------

    private View buildWeatherCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(26), dp(24), dp(26), dp(22));
        weatherBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                Ui.pick(this, new int[]{0x993FA9FF, 0x667B4DFF}, new int[]{Ui.ACCENT_DEEP, Ui.PANEL}));
        weatherBg.setCornerRadius(dp(18));
        weatherBg.setStroke(dp(1), 0x447FD8FF);
        card.setBackground(weatherBg);

        TextView hello = left(Ui.text(this, Profile.welcome(this), 28, Color.WHITE));
        hello.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(hello);

        greeting = left(Ui.text(this, "", 17, 0xCCFFFFFF));
        greeting.setPadding(0, dp(4), 0, 0);
        card.addView(greeting);

        // The user's own car (photo with background removed); headlights glow when the car's lights are on.
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
        // Speedometer above the car, fed by the GPS.
        speedView = new SpeedView(this);
        speedView.setOnClickListener(v -> fixGps());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, dp(88));
        slp.topMargin = dp(6);
        card.addView(speedView, slp);

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, 0, 1f);
        clp.setMargins(dp(30), dp(4), dp(30), dp(8));
        card.addView(carBox, clp);

        // Model name under the car, spaced out like the badge on the tailgate.
        TextView model = Ui.text(this, Profile.MODEL, 15, 0xE6FFFFFF);
        model.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        model.setLetterSpacing(0.6f);
        model.setShadowLayer(dp(8), 0, 0, 0x991E90FF);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.bottomMargin = dp(6);
        card.addView(model, mlp);

        // Compact weather row: icon + temperature + description, leaving room for the car.
        LinearLayout now = new LinearLayout(this);
        now.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new ImageView(this);
        weatherIcon.setImageResource(R.drawable.ic_w_cloud);
        weatherIcon.setColorFilter(0xFFFFE07A);
        now.addView(weatherIcon, new LinearLayout.LayoutParams(dp(54), dp(54)));
        weatherTemp = left(Ui.text(this, "--°", 50, Color.WHITE));
        weatherTemp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        weatherTemp.setIncludeFontPadding(false);
        weatherTemp.setPadding(dp(10), 0, dp(12), 0);
        now.addView(weatherTemp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        weatherDesc = left(Ui.text(this, "Cargando el clima…", 16, Color.WHITE));
        weatherDesc.setSingleLine(false);
        weatherDesc.setMaxLines(2);
        texts.addView(weatherDesc);
        weatherCity = left(Ui.text(this, "", 13, 0xB3FFFFFF));
        weatherCity.setPadding(0, dp(2), 0, 0);
        texts.addView(weatherCity);
        now.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(now);

        weatherExtra = left(Ui.text(this, "", 14, 0xE6FFFFFF));
        weatherExtra.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(0x26FFFFFF);
        chip.setCornerRadius(dp(20));
        weatherExtra.setBackground(chip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(10);
        card.addView(weatherExtra, lp);

        Ui.pressable(card, 0.98f);
        card.setOnClickListener(v -> fetchWeather(true));
        return card;
    }

    private void updateGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(h < 12 ? "Buenos días" : h < 19 ? "Buenas tardes" : "Buenas noches");
    }

    private void showWeather(Weather w) {
        if (w == null) return;
        weatherIcon.setImageResource(w.icon());
        weatherIcon.setColorFilter(w.icon() == R.drawable.ic_w_sun ? 0xFFFFD25A
                : w.icon() == R.drawable.ic_w_moon ? 0xFFDDE4FF : Color.WHITE);
        weatherTemp.setText(w.temp + "°");
        weatherDesc.setText(w.description());
        weatherCity.setText(w.city.isEmpty() ? "Sensación " + w.feelsLike + "°"
                : w.city + " · Sensación " + w.feelsLike + "°");
        weatherExtra.setText("↑ " + w.max + "°   ↓ " + w.min + "°   💧 " + w.humidity + "%   " + w.wind + " km/h");
        weatherBg.setColors(Ui.isDark(this) ? new int[]{Ui.ACCENT_DEEP, Ui.PANEL}
                : w.isDay ? new int[]{0x993FA9FF, 0x667B4DFF} : new int[]{0x8C2A3A9E, 0x80261A6E});
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
    }

    private void updateOil() {
        if (!Odometer.isConfigured(this)) {
            oilInfo.setText("Aceite: configurar");
            oilBg.setColor(0x26FFFFFF);
            return;
        }
        int left = Odometer.remainingKm(this);
        oilInfo.setText(left < 0 ? "Aceite: ¡ya toca el cambio!" : "Aceite: faltan " + OilActivity.fmt(left) + " km");
        oilBg.setColor(left < 0 ? 0xCCE53935 : left <= Odometer.WARN_KM ? 0xCCFF9800 : 0x26FFFFFF);
    }

    // ---------- Clock ----------

    private View buildClockCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.card(this));

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("hh:mm");
        time.setTextSize(40);
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        time.setIncludeFontPadding(false);
        time.setGravity(Gravity.CENTER);
        card.addView(time);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d 'de' MMMM");
        date.setFormat12Hour("EEEE, d 'de' MMMM");
        date.setTextSize(14);
        date.setTextColor(0xCCFFFFFF);
        date.setPadding(0, dp(6), 0, 0);
        date.setGravity(Gravity.CENTER);
        card.addView(date);

        // Oil-change status pill; opens the oil screen.
        oilInfo = Ui.text(this, "", 13, Color.WHITE);
        oilInfo.setPadding(dp(14), dp(6), dp(14), dp(6));
        oilBg = new GradientDrawable();
        oilBg.setCornerRadius(dp(14));
        oilInfo.setBackground(oilBg);
        Ui.pressable(oilInfo, 0.92f);
        oilInfo.setOnClickListener(v -> startActivity(new Intent(this, OilActivity.class)));
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-2, -2);
        olp.topMargin = dp(8);
        card.addView(oilInfo, olp);

        Ui.pressable(card, 0.97f);
        card.setOnClickListener(v -> openSettings());
        return card;
    }

    // ---------- Music (any source: Bluetooth, USB or any music app) ----------

    private View buildMusicCard() {
        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                Ui.pick(this, new int[]{0xFF6A3BFF, 0xFFC04DE8, 0xFF2B1A7A},
                        new int[]{Ui.ACCENT_DEEP, Ui.PANEL, Ui.BG}));
        bg.setCornerRadius(dp(18));
        card.setBackground(bg);
        card.setClipToOutline(true);

        art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(art, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x33000000, 0x66000000, 0xE6100A30}));
        card.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(10));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView note = new ImageView(this);
        note.setImageResource(R.drawable.ic_music);
        note.setColorFilter(Ui.ACCENT_LIGHT);
        head.addView(note, new LinearLayout.LayoutParams(dp(20), dp(20)));
        songSource = left(Ui.text(this, "MÚSICA", 14, Color.WHITE));
        songSource.setTypeface(Typeface.DEFAULT_BOLD);
        songSource.setLetterSpacing(0.08f);
        songSource.setPadding(dp(8), 0, 0, 0);
        head.addView(songSource, new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(head);

        songTitle = left(Ui.text(this, "", 19, Color.WHITE));
        songTitle.setTypeface(Typeface.DEFAULT_BOLD);
        songTitle.setPadding(0, dp(8), 0, 0);
        box.addView(songTitle);
        songArtist = left(Ui.text(this, "", 14, 0xCCFFFFFF));
        box.addView(songArtist);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.addView(Ui.iconButton(this, R.drawable.ic_prev, 46, false, () -> nowPlaying.skip(false)),
                new LinearLayout.LayoutParams(dp(46), dp(46)));
        playBtn = Ui.iconButton(this, R.drawable.ic_play, 58, true, () -> nowPlaying.playPause());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(58), dp(58));
        plp.setMargins(dp(16), 0, dp(16), 0);
        controls.addView(playBtn, plp);
        controls.addView(Ui.iconButton(this, R.drawable.ic_next, 46, false, () -> nowPlaying.skip(true)),
                new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout.LayoutParams ctl = new LinearLayout.LayoutParams(-1, 0, 1f);
        box.addView(controls, ctl);

        // Shortcuts to the music apps on the head unit (YouTube, YouTube Music, Spotify, USB player…).
        musicApps = new LinearLayout(this);
        musicApps.setGravity(Gravity.CENTER);
        box.addView(musicApps, new LinearLayout.LayoutParams(-1, dp(44)));
        fillMusicApps();

        card.addView(box, new FrameLayout.LayoutParams(-1, -1));
        Ui.pressable(card, 0.98f);
        card.setOnClickListener(v -> openMusic());
        return card;
    }

    private void fillMusicApps() {
        musicApps.removeAllViews();
        java.util.List<MusicApps.App> apps = MusicApps.find(this);
        int shown = Math.min(apps.size(), apps.size() > 5 ? 4 : 5);
        for (int i = 0; i < shown; i++) {
            MusicApps.App a = apps.get(i);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(a.icon);
            iv.setPadding(dp(5), dp(5), dp(5), dp(5));
            iv.setBackground(Ui.circle(0x33FFFFFF));
            iv.setContentDescription(a.label);
            Ui.pressable(iv, 0.85f);
            iv.setOnClickListener(v -> MusicApps.launch(this, a));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
            lp.setMargins(dp(5), 0, dp(5), 0);
            musicApps.addView(iv, lp);
        }
        if (apps.size() > shown) {
            ImageView more = Ui.iconButton(this, R.drawable.ic_apps, 40, false, () -> MusicApps.choose(this, apps));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
            lp.setMargins(dp(5), 0, dp(5), 0);
            musicApps.addView(more, lp);
        }
    }

    /** Opens what is playing; with nothing played yet, lets the driver pick a music app. */
    private void openMusic() {
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
        speedView.setSpeed(kmh);
        map.setSpeed(kmh);
    }

    private final Runnable gpsWatch = new Runnable() {
        public void run() {
            if (SystemClock.elapsedRealtime() - lastGpsAt > GPS_STALE_MS) speedView.setNoFix(gpsProblem());
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
        songSource.setText(s == null ? "MÚSICA" : nowPlaying.label(s).toUpperCase());
        songTitle.setText(empty ? "Nada sonando" : t.title);
        songArtist.setText(empty ? "Elige una app de música" : t.artist);
        playBtn.setImageResource(t != null && t.playing ? R.drawable.ic_pause : R.drawable.ic_play);

        if (t != null && t.art != null) {
            art.setPadding(0, 0, 0, 0);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.clearColorFilter();
            art.setAlpha(1f);
            art.setImageBitmap(t.art);
        } else {
            art.setPadding(dp(70), dp(36), dp(70), dp(110));
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setImageResource(s == NowPlaying.Source.BT ? R.drawable.ic_bluetooth : R.drawable.ic_music);
            art.setColorFilter(Color.WHITE);
            art.setAlpha(0.25f);
        }
    }

    // ---------- Dock ----------

    private View buildDock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.addView(dockIcon(R.drawable.ic_compass, this::openMaps), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_bluetooth, this::openBluetooth), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_radio, this::openRadio), weight(1f, 0));

        FrameLayout center = new FrameLayout(this);
        RingView ring = new RingView(this);
        center.addView(ring, new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));
        Ui.pressable(ring, 0.88f);
        ring.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        dock.addView(center, weight(1f, 0));

        dock.addView(dockIcon(R.drawable.ic_w_moon,
                () -> startActivity(new Intent(this, ScreenOffActivity.class))), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_settings, this::openEqualizer), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_gear, this::openSettings), weight(1f, 0));
        return dock;
    }

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

    private View dockIcon(int icon, Runnable action) {
        FrameLayout f = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(0xE6FFFFFF);
        iv.setPadding(dp(12), dp(12), dp(12), dp(12));
        f.addView(iv, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER));
        Ui.pressable(iv, 0.85f);
        iv.setOnClickListener(v -> action.run());
        return f;
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
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        handler.post(periodic);
        startLocation();
        map.onResume();
        nowPlaying.addListener(this);
        onNowPlayingChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(illReceiver);
        handler.removeCallbacks(periodic);
        stopLocation();
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

    private LinearLayout.LayoutParams vweight(float w, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, w);
        lp.topMargin = top;
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
