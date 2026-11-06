package com.simple.carlauncher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Modern FM screen on top of the factory radio service. */
public class RadioActivity extends Activity {

    private static final String FACTORY_RADIO = "com.nwd.radio/.home_horizontalActivity";
    private static final int PRESET_SLOTS = 6;

    private final RadioClient radio = new RadioClient();
    private final Handler handler = new Handler();

    private TextView band, station, freq, unit;
    private DialView dial;
    private final List<TextView[]> presetViews = new ArrayList<>();
    private final List<LinearLayout> presetTiles = new ArrayList<>();
    private List<RadioClient.Station> presets = new ArrayList<>();
    private int tick;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(16));
        root.setBackground(Ui.background(this));

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(56)));

        LinearLayout body = new LinearLayout(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 1f);
        blp.topMargin = dp(12);
        root.addView(body, blp);

        body.addView(buildTuner(), new LinearLayout.LayoutParams(0, -1, 1.45f));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, -1, 1f);
        plp.leftMargin = dp(12);
        body.addView(buildPresets(), plp);

        setContentView(root);
        radio.bind(this);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(Ui.iconButton(this, R.drawable.ic_back, 48, false, this::finish),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = Ui.text(this, "Radio", 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(16), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);

        TextView classic = chip("Vista clásica", 0x26FFFFFF);
        classic.setOnClickListener(v -> Ui.launch(this, FACTORY_RADIO));
        bar.addView(classic);

        band = chip("FM1", Ui.ACCENT);
        band.setTypeface(Typeface.DEFAULT_BOLD);
        band.setOnClickListener(v -> {
            radio.changeBand();
            handler.postDelayed(this::refresh, 400);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(10);
        bar.addView(band, lp);
        return bar;
    }

    private TextView chip(String s, int color) {
        TextView c = Ui.text(this, s, 16, Color.WHITE);
        c.setPadding(dp(20), dp(10), dp(20), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(22));
        c.setBackground(bg);
        Ui.pressable(c, 0.92f);
        return c;
    }

    private View buildTuner() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(20), dp(16), dp(20), dp(20));
        card.setBackground(Ui.card(this));

        station = Ui.text(this, "", 18, 0xCCFFFFFF);
        card.addView(station);

        LinearLayout big = new LinearLayout(this);
        big.setGravity(Gravity.CENTER);
        freq = Ui.text(this, "--.-", 104, Color.WHITE);
        freq.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        freq.setIncludeFontPadding(false);
        big.addView(freq);
        unit = Ui.text(this, "MHz", 22, 0x99FFFFFF);
        unit.setPadding(dp(10), dp(30), 0, 0);
        big.addView(unit);
        card.addView(big);

        dial = new DialView(this);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, dp(80));
        dlp.topMargin = dp(4);
        card.addView(dial, dlp);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(14), 0, 0);
        controls.addView(control(R.drawable.ic_rewind, "Buscar", false, () -> { radio.seek(false); soon(); }));
        controls.addView(control(R.drawable.ic_prev, "Anterior", true, () -> { radio.preset(false); soon(); }));
        controls.addView(control(R.drawable.ic_next, "Siguiente", true, () -> { radio.preset(true); soon(); }));
        controls.addView(control(R.drawable.ic_forward, "Buscar", false, () -> { radio.seek(true); soon(); }));
        card.addView(controls);
        return card;
    }

    private View control(int icon, String label, boolean primary, Runnable action) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        int size = primary ? 64 : 56;
        ImageView b = Ui.iconButton(this, icon, size, primary, action);
        c.addView(b, new LinearLayout.LayoutParams(dp(size), dp(size)));
        TextView t = Ui.text(this, label, 12, 0x99FFFFFF);
        t.setPadding(0, dp(6), 0, 0);
        c.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(12), 0, dp(12), 0);
        c.setLayoutParams(lp);
        return c;
    }

    private View buildPresets() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(Ui.card(this));

        TextView title = Ui.text(this, "Emisoras guardadas", 17, Color.WHITE);
        title.setGravity(Gravity.START);
        title.setPadding(dp(4), 0, 0, dp(10));
        card.addView(title);

        for (int r = 0; r < PRESET_SLOTS / 2; r++) {
            LinearLayout row = new LinearLayout(this);
            for (int col = 0; col < 2; col++) {
                int index = r * 2 + col;
                LinearLayout tile = new LinearLayout(this);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER);
                TextView f = Ui.text(this, "--", 26, Color.WHITE);
                f.setTypeface(Typeface.DEFAULT_BOLD);
                TextView n = Ui.text(this, "P" + (index + 1), 12, 0x99FFFFFF);
                tile.addView(f);
                tile.addView(n);
                Ui.pressable(tile, 0.94f);
                tile.setOnClickListener(v -> {
                    if (index < presets.size()) {
                        radio.tune(presets.get(index), index);
                        soon();
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
                lp.setMargins(dp(5), dp(5), dp(5), dp(5));
                row.addView(tile, lp);
                presetViews.add(new TextView[]{f, n});
                presetTiles.add(tile);
            }
            card.addView(row, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
        return card;
    }

    private void soon() {
        handler.postDelayed(this::refresh, 300);
        handler.postDelayed(this::refresh, 1200);
    }

    private void refresh() {
        RadioClient.Station cur = radio.current();
        if (tick++ % 4 == 0 || presets.isEmpty()) presets = radio.presets();
        if (cur == null) {
            station.setText(radio.isConnected() ? "" : "Conectando con la radio…");
            return;
        }
        band.setText(cur.bandLabel());
        station.setText(cur.psName.isEmpty() ? "Radio " + cur.bandLabel() : cur.psName);
        freq.setText(cur.frequencyLabel());
        unit.setText(cur.isAm() ? "kHz" : "MHz");
        dial.setVisibility(cur.isAm() ? View.INVISIBLE : View.VISIBLE);
        dial.setFrequency(cur.frequency);

        for (int i = 0; i < presetTiles.size(); i++) {
            RadioClient.Station p = i < presets.size() ? presets.get(i) : null;
            boolean active = p != null && p.frequency == cur.frequency;
            presetViews.get(i)[0].setText(p == null ? "--" : p.frequencyLabel());
            presetViews.get(i)[1].setText(p == null || p.psName.isEmpty() ? "P" + (i + 1) : p.psName);
            GradientDrawable bg = active
                    ? new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Ui.ACCENT_LIGHT, Ui.ACCENT})
                    : new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x26FFFFFF, 0x14FFFFFF});
            bg.setCornerRadius(dp(14));
            presetTiles.get(i).setBackground(bg);
        }
    }

    private final Runnable poll = new Runnable() {
        public void run() {
            refresh();
            handler.postDelayed(this, 700);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        // Coming from Bluetooth/USB: switch to the radio and wake the tuner.
        if (!Ui.radioIsSource(this) || !radio.isConnected()) {
            handler.postDelayed(() -> Ui.selectRadio(this, radio), 300);
        }
        handler.post(poll);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(poll);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        radio.unbind(this);
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
