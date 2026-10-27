package com.simple.carlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Calendar;

public class MainActivity extends Activity implements NowPlaying.Listener {

    private static final String SETTINGS = "com.android.car.setting/.home_horizontalActivity";

    private static final long WEATHER_EVERY_MS = 30 * 60 * 1000;
    private static final int FADE_MS = 200;

    private final Handler handler = new Handler();
    private final RadioClient radio = new RadioClient();
    private NowPlaying nowPlaying;
    private boolean nwd;

    private boolean createdDark;
    private String createdProfile;

    private String profileStamp() {
        return Profile.name(this) + "|" + Profile.carPhotoFile(this).lastModified();
    }

    private TextView greeting;
    private TextView btChip, netChip, weatherChip, oilChip;
    private GradientDrawable oilBg;
    private long lastWeatherFetch;

    private TextView fmBand, fmFreq, fmUnit;
    private ImageView art, playBtn;
    private TextView songSource, songTitle, songArtist;

    private TextView btTileState;
    private TextView volumeValue;
    private VolumeBar volumeBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        nwd = Profile.isNwd(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(10), dp(16), dp(4));
        root.setBackground(Ui.background(this));

        root.addView(buildStatusBar(), new LinearLayout.LayoutParams(-1, -2));

        LinearLayout main = new LinearLayout(this);
        main.addView(buildMusicCard(), weight(1f, 0));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.addView(buildClockCard(), vweight(0.9f, 0));
        right.addView(buildFmCard(), vweight(1.1f, dp(12)));
        LinearLayout tiles = new LinearLayout(this);
        tiles.addView(buildBtTile(), weight(1f, 0));
        tiles.addView(buildVolumeTile(), weight(1f, dp(12)));
        right.addView(tiles, vweight(1f, dp(12)));
        main.addView(right, weight(1f, dp(12)));

        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        mlp.topMargin = dp(10);
        root.addView(main, mlp);

        root.addView(buildDock(), new LinearLayout.LayoutParams(-1, dp(84)));
        setContentView(root);

        radio.bind(this);
        nowPlaying = NowPlaying.get(this);
        createdDark = Ui.isDark(this);
        createdProfile = profileStamp();
        // Shared installs: ask for name, car photo and permissions on first start.
        if (!Profile.isSetupDone(this) && Profile.bundledCar(this, false) == 0) {
            startActivity(new Intent(this, SetupActivity.class));
        }
        if (nwd) startService(new Intent(this, VolumeOverlay.class));
        startService(new Intent(this, GreeterService.class));
        startService(new Intent(this, Odometer.class));
    }

    // ---------- Status bar: greeting + indicators ----------

    private View buildStatusBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        // The user's own car photo (set in the setup screen), or a car button that opens it.
        ImageView car = new ImageView(this);
        Bitmap own = Profile.carPhoto(this);
        if (own != null) {
            car.setImageBitmap(own);
            car.setScaleType(ImageView.ScaleType.FIT_CENTER);
            bar.addView(car, new LinearLayout.LayoutParams(dp(96), dp(54)));
        } else {
            car.setImageResource(R.drawable.ic_car);
            car.setColorFilter(Ui.text2(this));
            car.setPadding(dp(12), dp(12), dp(12), dp(12));
            car.setBackground(Ui.circle(Ui.fill(this)));
            bar.addView(car, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        Ui.pressable(car, 0.92f);
        car.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));

        LinearLayout hello = new LinearLayout(this);
        hello.setOrientation(LinearLayout.VERTICAL);
        hello.setPadding(dp(14), 0, 0, 0);
        TextView welcome = left(Ui.text(this, Profile.welcome(this), 26, Ui.text1(this)));
        welcome.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hello.addView(welcome);
        greeting = left(Ui.text(this, "", 14, Ui.text2(this)));
        hello.addView(greeting);
        bar.addView(hello, new LinearLayout.LayoutParams(0, -2, 1f));

        // Only indicators backed by real data; each stays hidden while there is none.
        btChip = chip(R.drawable.ic_bluetooth);
        btChip.setMaxWidth(dp(180));
        btChip.setOnClickListener(v -> openBluetooth());
        bar.addView(btChip, chipParams());
        netChip = chip(R.drawable.ic_wifi);
        bar.addView(netChip, chipParams());
        weatherChip = chip(R.drawable.ic_w_cloud);
        weatherChip.setMaxWidth(dp(200));
        weatherChip.setOnClickListener(v -> fetchWeather(true));
        bar.addView(weatherChip, chipParams());
        oilChip = chip(0);
        oilBg = (GradientDrawable) oilChip.getBackground();
        oilChip.setOnClickListener(v -> startActivity(new Intent(this, OilActivity.class)));
        bar.addView(oilChip, chipParams());
        return bar;
    }

    private TextView chip(int icon) {
        TextView c = Ui.text(this, "", 14, Ui.text1(this));
        c.setPadding(dp(12), dp(8), dp(14), dp(8));
        c.setCompoundDrawablePadding(dp(6));
        if (icon != 0) setChipIcon(c, icon, Ui.ACCENT_LIGHT);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.fill(this));
        bg.setCornerRadius(dp(20));
        c.setBackground(bg);
        c.setVisibility(View.GONE);
        Ui.pressable(c, 0.94f);
        return c;
    }

    private void setChipIcon(TextView c, int icon, int tint) {
        Drawable d = getDrawable(icon).mutate();
        d.setBounds(0, 0, dp(18), dp(18));
        d.setTint(tint);
        c.setCompoundDrawables(d, null, null, null);
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(8);
        return lp;
    }

    private void updateGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(h < 12 ? "Buenos días" : h < 19 ? "Buenas tardes" : "Buenas noches");
    }

    private void showWeather(Weather w) {
        if (w == null) return;
        setChipIcon(weatherChip, w.icon(), w.icon() == R.drawable.ic_w_sun ? Ui.WARN : Ui.ACCENT_LIGHT);
        weatherChip.setText(w.temp + "° " + w.description());
        weatherChip.setVisibility(View.VISIBLE);
    }

    private void fetchWeather(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && lastWeatherFetch != 0 && now - lastWeatherFetch < WEATHER_EVERY_MS) return;
        lastWeatherFetch = now;
        Weather.fetch(this, this::showWeather);
    }

    private void updateOil() {
        oilChip.setVisibility(View.VISIBLE);
        if (!Odometer.isConfigured(this)) {
            oilChip.setText("Aceite: configurar");
            oilChip.setTextColor(Ui.text1(this));
            oilBg.setColor(Ui.fill(this));
            return;
        }
        int left = Odometer.remainingKm(this);
        boolean warn = left < 0 || left <= Odometer.WARN_KM;
        oilChip.setText(left < 0 ? "Aceite: ¡ya toca el cambio!" : "Aceite: faltan " + OilActivity.fmt(left) + " km");
        oilChip.setTextColor(warn ? Ui.BG : Ui.text1(this));
        oilBg.setColor(left < 0 ? Ui.ERROR : warn ? Ui.WARN : Ui.fill(this));
    }

    private void updateNetwork() {
        NetworkInfo n = null;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            n = cm.getActiveNetworkInfo();
        } catch (SecurityException ignored) { }
        boolean online = n != null && n.isConnected();
        netChip.setText(!online ? "Sin red" : n.getType() == ConnectivityManager.TYPE_WIFI ? "Wi-Fi" : "Datos");
        netChip.setTextColor(online ? Ui.text1(this) : Ui.text2(this));
        setChipIcon(netChip, R.drawable.ic_wifi, online ? Ui.ACCENT_LIGHT : Ui.text2(this));
        netChip.setVisibility(View.VISIBLE);
    }

    /** Phone name from the NWD Bluetooth module; other head units only get the shortcut. */
    private void updateBluetooth() {
        BtClient bt = nowPlaying.btClient;
        if (!nwd || !bt.isBound()) {
            btChip.setVisibility(View.GONE);
            btTileState.setText(nwd ? "No disponible" : "Abrir Bluetooth");
            return;
        }
        String phone = bt.isPhoneConnected() ? bt.deviceName() : null;
        boolean connected = phone != null;
        if (connected && phone.isEmpty()) phone = "Teléfono";
        btChip.setText(connected ? phone : "Sin teléfono");
        btChip.setTextColor(connected ? Ui.text1(this) : Ui.text2(this));
        btChip.setVisibility(View.VISIBLE);
        btTileState.setText(connected ? phone : "Sin teléfono");
        btTileState.setTextColor(connected ? Ui.text1(this) : Ui.text2(this));
    }

    private final Runnable periodic = new Runnable() {
        public void run() {
            if (Ui.isDark(MainActivity.this) != createdDark) recreate();
            updateGreeting();
            updateOil();
            updateNetwork();
            if (!nwd) updateVolume(); // NWD units refresh it every second in radioPoll
            fetchWeather(false);
            handler.postDelayed(this, 60 * 1000);
        }
    };

    // ---------- Car lights (ILL wire): auto theme ----------

    private final android.content.BroadcastReceiver illReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            // Auto theme follows the car's lights; give the kernel a moment to store the state.
            handler.postDelayed(() -> { if (Ui.isDark(MainActivity.this) != createdDark) recreate(); }, 1500);
        }
    };

    // ---------- Clock ----------

    private View buildClockCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(22), 0, dp(22), 0);
        card.setBackground(Ui.card(this));

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("hh:mm");
        time.setTextSize(56);
        time.setTextColor(Ui.text1(this));
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        card.addView(time);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d 'de' MMMM");
        date.setFormat12Hour("EEEE, d 'de' MMMM");
        date.setTextSize(15);
        date.setTextColor(Ui.text2(this));
        date.setPadding(0, dp(4), 0, 0);
        card.addView(date);

        Ui.pressable(card, 0.97f);
        card.setOnClickListener(v -> openSettings());
        return card;
    }

    // ---------- FM ----------

    private View buildFmCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(10), dp(16), dp(10));
        card.setBackground(Ui.card(this));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView radioIcon = new ImageView(this);
        radioIcon.setImageResource(R.drawable.ic_radio);
        radioIcon.setColorFilter(Ui.ACCENT_LIGHT);
        head.addView(radioIcon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        fmBand = left(Ui.text(this, nwd ? "RADIO FM" : "RADIO · TOCA PARA ABRIR", 13, Ui.text2(this)));
        fmBand.setLetterSpacing(0.08f);
        fmBand.setPadding(dp(8), 0, 0, 0);
        head.addView(fmBand, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(head);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.addView(Ui.iconButton(this, R.drawable.ic_prev, 52, false, () -> stationStep(false)),
                new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout freqBox = new LinearLayout(this);
        freqBox.setGravity(Gravity.CENTER | Gravity.BOTTOM);
        fmFreq = Ui.text(this, "--.-", 42, Ui.text1(this));
        fmFreq.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        fmFreq.setIncludeFontPadding(false);
        freqBox.addView(fmFreq);
        fmUnit = Ui.text(this, "MHz", 15, Ui.text2(this));
        fmUnit.setPadding(dp(6), 0, 0, dp(4));
        freqBox.addView(fmUnit);
        row.addView(freqBox, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(Ui.iconButton(this, R.drawable.ic_next, 52, false, () -> stationStep(true)),
                new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = dp(6);
        card.addView(row, rlp);

        Ui.pressable(card, 0.97f);
        card.setOnClickListener(v -> openRadio());
        return card;
    }

    private void stationStep(boolean next) {
        if (!nwd) {
            openRadio();
            return;
        }
        if (!Ui.radioIsSource(this)) {
            Ui.selectRadio(this, radio); // first tap from Bluetooth: bring the radio back
            handler.postDelayed(this::updateRadio, 2500);
            return;
        }
        radio.preset(next);
        handler.postDelayed(this::updateRadio, 300);
    }

    private void updateRadio() {
        RadioClient.Station st = radio.current();
        if (st == null) {
            if (!radio.isConnected()) fmBand.setText("RADIO NO DISPONIBLE");
            return;
        }
        fmBand.setText(st.psName.isEmpty() ? "RADIO " + st.bandLabel() : st.bandLabel() + " · " + st.psName);
        fmFreq.setText(st.frequencyLabel());
        fmUnit.setText(st.isAm() ? "kHz" : "MHz");
    }

    private final Runnable radioPoll = new Runnable() {
        public void run() {
            updateRadio();
            updateBluetooth();
            updateVolume();
            handler.postDelayed(this, 1000);
        }
    };

    // ---------- Bluetooth + volume tiles ----------

    private View buildBtTile() {
        LinearLayout tile = tile(R.drawable.ic_bluetooth, "Bluetooth");
        btTileState = left(Ui.text(this, "", 15, Ui.text2(this)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        tile.addView(btTileState, lp);
        tile.setOnClickListener(v -> openBluetooth());
        return tile;
    }

    /** Shows the current volume (read-only); tapping opens the equalizer. */
    private View buildVolumeTile() {
        LinearLayout tile = tile(R.drawable.ic_volume, "Volumen");
        volumeValue = (TextView) ((LinearLayout) tile.getChildAt(0)).getChildAt(1);
        volumeBar = new VolumeBar(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(8));
        blp.topMargin = dp(12);
        tile.addView(volumeBar, blp);
        tile.setOnClickListener(v -> openEqualizer());
        return tile;
    }

    private void updateVolume() {
        int v, max;
        if (nwd) {
            // The NWD MCU volume (the knob and steering-wheel keys).
            v = Settings.System.getInt(getContentResolver(), "mcu_system_volume", -1);
            max = Settings.System.getInt(getContentResolver(), "mcu_max_volume", 40);
        } else {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            v = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        }
        if (v < 0) {
            volumeValue.setText("Volumen");
            volumeBar.setLevel(0);
            return;
        }
        volumeValue.setText("Volumen " + v);
        volumeBar.setLevel(v / (float) Math.max(1, max));
    }

    private LinearLayout tile(int icon, String title) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(16), 0, dp(16), 0);
        tile.setBackground(Ui.card(this));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(Ui.ACCENT_LIGHT);
        head.addView(iv, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView t = left(Ui.text(this, title, 17, Ui.text1(this)));
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(dp(8), 0, 0, 0);
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        tile.addView(head);
        Ui.pressable(tile, 0.96f);
        return tile;
    }

    /** Thin level bar for the volume tile. */
    private static class VolumeBar extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float level;

        VolumeBar(Context c) {
            super(c);
            track.setColor(Ui.fill(c));
            fill.setColor(Ui.ACCENT);
        }

        void setLevel(float l) {
            l = Math.max(0, Math.min(1, l));
            if (l == level) return;
            level = l;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight(), rad = h / 2;
            r.set(0, 0, getWidth(), h);
            c.drawRoundRect(r, rad, rad, track);
            if (level > 0) {
                r.set(0, 0, Math.max(h, getWidth() * level), h);
                c.drawRoundRect(r, rad, rad, fill);
            }
        }
    }

    // ---------- Music (any source) ----------

    private View buildMusicCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(20));
        card.setBackground(Ui.card(this));

        songSource = left(Ui.text(this, "MÚSICA", 13, Ui.ACCENT_LIGHT));
        songSource.setLetterSpacing(0.1f);
        card.addView(songSource);

        LinearLayout info = new LinearLayout(this);
        info.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout artBox = new FrameLayout(this);
        GradientDrawable artBg = new GradientDrawable();
        artBg.setColor(Ui.fill(this));
        artBg.setCornerRadius(dp(12));
        artBox.setBackground(artBg);
        artBox.setClipToOutline(true);
        art = new ImageView(this);
        artBox.addView(art, new FrameLayout.LayoutParams(-1, -1));
        info.addView(artBox, new LinearLayout.LayoutParams(dp(132), dp(132)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(18), 0, 0, 0);
        songTitle = left(Ui.text(this, "", 22, Ui.text1(this)));
        songTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        songTitle.setSingleLine(false);
        songTitle.setMaxLines(2);
        texts.addView(songTitle);
        songArtist = left(Ui.text(this, "", 16, Ui.text2(this)));
        songArtist.setPadding(0, dp(4), 0, 0);
        texts.addView(songArtist);
        info.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, 0, 1f);
        ilp.topMargin = dp(12);
        card.addView(info, ilp);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.addView(Ui.iconButton(this, R.drawable.ic_prev, 64, false, () -> nowPlaying.skip(false)),
                new LinearLayout.LayoutParams(dp(64), dp(64)));
        playBtn = Ui.iconButton(this, R.drawable.ic_play, 76, true, () -> nowPlaying.playPause());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(76), dp(76));
        plp.setMargins(dp(28), 0, dp(28), 0);
        controls.addView(playBtn, plp);
        controls.addView(Ui.iconButton(this, R.drawable.ic_next, 64, false, () -> nowPlaying.skip(true)),
                new LinearLayout.LayoutParams(dp(64), dp(64)));
        card.addView(controls, new LinearLayout.LayoutParams(-1, -2));

        Ui.pressable(card, 0.98f);
        card.setOnClickListener(v -> nowPlaying.open(this));
        return card;
    }

    @Override
    public void onNowPlayingChanged() {
        NowPlaying.Source s = nowPlaying.current();
        NowPlaying.Track t = s == null ? null : nowPlaying.track(s);
        boolean empty = t == null || t.title.isEmpty();
        songSource.setText(s == null ? "MÚSICA" : nowPlaying.label(s).toUpperCase());
        String title = empty ? "Nada sonando" : t.title;
        if (!title.contentEquals(songTitle.getText())) {
            // Discreet fade when the track changes.
            songTitle.setAlpha(0f);
            songArtist.setAlpha(0f);
            songTitle.animate().alpha(1f).setDuration(FADE_MS).start();
            songArtist.animate().alpha(1f).setDuration(FADE_MS).start();
        }
        songTitle.setText(title);
        songArtist.setText(empty ? "Toca para abrir la música" : t.artist);
        playBtn.setImageResource(t != null && t.playing ? R.drawable.ic_pause : R.drawable.ic_play);

        if (t != null && t.art != null) {
            art.setPadding(0, 0, 0, 0);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.clearColorFilter();
            art.setImageBitmap(t.art);
        } else {
            art.setPadding(dp(36), dp(36), dp(36), dp(36));
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setImageResource(s == NowPlaying.Source.BT ? R.drawable.ic_bluetooth : R.drawable.ic_music);
            art.setColorFilter(Ui.ACCENT);
        }
    }

    // ---------- Dock ----------

    private View buildDock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.addView(dockItem(R.drawable.ic_compass, "Navegación", this::openMaps), weight(1f, 0));
        dock.addView(dockItem(R.drawable.ic_radio, "Radio", this::openRadio), weight(1f, 0));
        dock.addView(dockItem(R.drawable.ic_bluetooth, "Bluetooth", this::openBluetooth), weight(1f, 0));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        RingView ring = new RingView(this);
        center.addView(ring, new LinearLayout.LayoutParams(dp(60), dp(60)));
        center.addView(Ui.text(this, "Apps", 12, Ui.text1(this)));
        Ui.pressable(center, 0.9f);
        center.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        dock.addView(center, weight(1f, 0));

        dock.addView(dockItem(R.drawable.ic_w_moon, "Pantalla off",
                () -> startActivity(new Intent(this, ScreenOffActivity.class))), weight(1f, 0));
        dock.addView(dockItem(R.drawable.ic_eq, "Ecualizador", this::openEqualizer), weight(1f, 0));
        dock.addView(dockItem(R.drawable.ic_gear, "Ajustes", this::openSettings), weight(1f, 0));
        return dock;
    }

    private View dockItem(int icon, String label, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(Ui.text1(this));
        iv.setPadding(dp(14), dp(10), dp(14), dp(6));
        item.addView(iv, new LinearLayout.LayoutParams(dp(56), dp(44)));
        item.addView(Ui.text(this, label, 12, Ui.text2(this)));
        Ui.pressable(item, 0.9f);
        item.setOnClickListener(v -> action.run());
        return item;
    }

    // ---------- Shortcuts: NWD screens, or the head unit's own apps elsewhere ----------

    private void openRadio() {
        if (nwd) startActivity(new Intent(this, RadioActivity.class));
        else if (!launchByKeyword("radio", "fm", "tuner")) toast("No se encontró una app de radio");
    }

    private void openBluetooth() {
        if (nwd) startActivity(new Intent(this, BtActivity.class));
        else if (!launchByKeyword("bluetooth", "bt music", "btmusic", "phone link"))
            safeStart(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
    }

    private void openEqualizer() {
        if (nwd) {
            startActivity(new Intent(this, EqActivity.class));
            return;
        }
        Intent eq = new Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
        if (!safeStart(eq) && !launchByKeyword("ecualiz", "equaliz", "audio", "sonido", "dsp"))
            toast("No se encontró un ecualizador");
    }

    private void openSettings() {
        if (nwd) Ui.launch(this, SETTINGS);
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
        updateNetwork();
        updateBluetooth();
        updateVolume();
        showWeather(Weather.cached(this));
        registerReceiver(illReceiver, new android.content.IntentFilter("com.nwd.ACTION_ILL_STATE_CHANGE"));
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        handler.post(periodic);
        if (nwd) handler.post(radioPoll);
        nowPlaying.addListener(this);
        onNowPlayingChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(illReceiver);
        } catch (IllegalArgumentException ignored) {
            // onResume returned early to recreate() before registering it
        }
        handler.removeCallbacks(periodic);
        handler.removeCallbacks(radioPoll);
        nowPlaying.removeListener(this);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) fetchWeather(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        radio.unbind(this);
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
