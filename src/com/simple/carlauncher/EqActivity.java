package com.simple.carlauncher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Equalizer: presets, 10-band EQ, bass/mid/treble, loudness, balance/fader and auto mode. */
public class EqActivity extends Activity {

    private final SoundClient sound = new SoundClient();
    private final Handler handler = new Handler();

    private final LevelSlider[] bands = new LevelSlider[SoundClient.BANDS];
    private final TextView[] bandValues = new TextView[SoundClient.BANDS];
    private final TextView[] presetChips = new TextView[SoundClient.PRESET_NAMES.length];
    private LevelSlider bass, middle, treble;
    private BalancePad pad;
    private TextView autoChip, loudChip, autoInfo;
    private int preset = -1;
    private boolean loudness;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(14));
        root.setBackground(Ui.background(this));

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(56)));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(48));
        plp.topMargin = dp(8);
        root.addView(buildPresets(), plp);

        LinearLayout body = new LinearLayout(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 1f);
        blp.topMargin = dp(10);
        root.addView(body, blp);
        body.addView(buildBands(), new LinearLayout.LayoutParams(0, -1, 1.55f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.addView(buildTones(), new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout.LayoutParams padLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        padLp.topMargin = dp(10);
        right.addView(buildBalance(), padLp);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -1, 1f);
        rlp.leftMargin = dp(10);
        body.addView(right, rlp);

        setContentView(root);
        sound.bind(this, () -> handler.post(this::loadAll));
    }

    // ---------- Building ----------

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(Ui.iconButton(this, R.drawable.ic_back, 48, false, this::finish),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = Ui.text(this, "Ecualizador", 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setPadding(dp(16), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        autoInfo = Ui.text(this, "", 13, 0xB3FFFFFF);
        autoInfo.setPadding(0, 0, dp(12), 0);
        bar.addView(autoInfo);

        autoChip = chip("Automático");
        autoChip.setOnClickListener(v -> {
            EqAuto.setEnabled(this, !EqAuto.isEnabled(this));
            EqAuto.apply(this, sound);
            handler.postDelayed(this::loadAll, 900);
        });
        bar.addView(autoChip);

        loudChip = chip("Loudness");
        loudChip.setOnClickListener(v -> {
            EqAuto.setEnabled(this, false);
            loudness = !loudness;
            sound.setAudio(SoundClient.LOUDNESS, loudness ? 1 : 0);
            refreshChips();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(8);
        bar.addView(loudChip, lp);
        return bar;
    }

    private TextView chip(String s) {
        TextView c = Ui.text(this, s, 15, Color.WHITE);
        c.setPadding(dp(16), dp(9), dp(16), dp(9));
        Ui.pressable(c, 0.92f);
        return c;
    }

    private View buildPresets() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // Standard, Pop, Rock, ... first; "Personalizado" last.
        int[] order = {1, 2, 3, 4, 5, 6, 7, 8, 0};
        for (int idx : order) {
            TextView c = chip(SoundClient.PRESET_NAMES[idx]);
            c.setOnClickListener(v -> {
                EqAuto.setEnabled(this, false);
                sound.setAudio(SoundClient.EQ_PRESET, idx);
                preset = idx;
                refreshChips();
                handler.postDelayed(this::loadBands, 700);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            row.addView(c, lp);
            presetChips[idx] = c;
        }
        scroll.addView(row);
        return scroll;
    }

    private View buildBands() {
        LinearLayout card = new LinearLayout(this);
        card.setPadding(dp(8), dp(12), dp(8), dp(10));
        card.setBackground(Ui.card(this));
        for (int b = 0; b < SoundClient.BANDS; b++) {
            int band = b;
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            bandValues[b] = Ui.text(this, "0", 13, Color.WHITE);
            col.addView(bandValues[b]);
            bands[b] = new LevelSlider(this, true, SoundClient.BAND_MAX);
            bands[b].setListener((s, v, done) -> {
                bandValues[band].setText(signed(v - 3));
                if (done) {
                    EqAuto.setEnabled(this, false);
                    if (preset != SoundClient.EQ_CUSTOM) {
                        sound.setAudio(SoundClient.EQ_PRESET, SoundClient.EQ_CUSTOM);
                        preset = SoundClient.EQ_CUSTOM;
                        refreshChips();
                    }
                    sound.setBand(band, v);
                }
            });
            col.addView(bands[b], new LinearLayout.LayoutParams(-1, 0, 1f));
            TextView hz = Ui.text(this, SoundClient.BAND_LABELS[b], 12, 0x99FFFFFF);
            col.addView(hz);
            card.addView(col, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        return card;
    }

    private View buildTones() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(8), dp(16), dp(8));
        card.setBackground(Ui.card(this));
        bass = tone(card, "Graves", SoundClient.BASS);
        middle = tone(card, "Medios", SoundClient.MIDDLE);
        treble = tone(card, "Agudos", SoundClient.TREBLE);
        return card;
    }

    private LevelSlider tone(LinearLayout parent, String label, int type) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.text(this, label, 15, Color.WHITE);
        t.setGravity(Gravity.START);
        row.addView(t, new LinearLayout.LayoutParams(dp(70), -2));
        LevelSlider s = new LevelSlider(this, false, SoundClient.TONE_MAX);
        TextView val = Ui.text(this, "0", 14, 0xCCFFFFFF);
        s.setTag(val);
        s.setListener((sl, v, done) -> {
            val.setText(signed(v - 7));
            if (done) sound.setAudio(type, v);
        });
        row.addView(s, new LinearLayout.LayoutParams(0, dp(40), 1f));
        row.addView(val, new LinearLayout.LayoutParams(dp(34), -2));
        parent.addView(row, new LinearLayout.LayoutParams(-1, 0, 1f));
        return s;
    }

    private View buildBalance() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(6), dp(10), dp(6));
        card.setBackground(Ui.card(this));
        LinearLayout head = new LinearLayout(this);
        TextView t = Ui.text(this, "Balance", 14, 0xCCFFFFFF);
        t.setGravity(Gravity.START);
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView centre = Ui.text(this, "Centrar", 13, Color.WHITE);
        centre.setPadding(dp(12), dp(4), dp(12), dp(4));
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(0x26FFFFFF);
        cbg.setCornerRadius(dp(12));
        centre.setBackground(cbg);
        Ui.pressable(centre, 0.92f);
        centre.setOnClickListener(v -> {
            int mid = SoundClient.BALANCE_MAX / 2;
            pad.setValues(mid, mid);
            sound.setAudio(SoundClient.BALANCE, mid);
            sound.setAudio(SoundClient.FADER, mid);
        });
        head.addView(centre);
        card.addView(head);
        pad = new BalancePad(this, SoundClient.BALANCE_MAX);
        pad.setListener((b, f, done) -> {
            if (done) {
                sound.setAudio(SoundClient.BALANCE, b);
                sound.setAudio(SoundClient.FADER, f);
            }
        });
        card.addView(pad, new LinearLayout.LayoutParams(-1, 0, 1f));
        return card;
    }

    // ---------- State ----------

    private void loadAll() {
        if (!sound.isConnected()) return;
        preset = sound.getAudio(SoundClient.EQ_PRESET);
        loudness = sound.getAudio(SoundClient.LOUDNESS) == 1;
        setTone(bass, sound.getAudio(SoundClient.BASS));
        setTone(middle, sound.getAudio(SoundClient.MIDDLE));
        setTone(treble, sound.getAudio(SoundClient.TREBLE));
        pad.setValues(clamp(sound.getAudio(SoundClient.BALANCE)), clamp(sound.getAudio(SoundClient.FADER)));
        loadBands();
        refreshChips();
    }

    private void loadBands() {
        for (int b = 0; b < SoundClient.BANDS; b++) {
            int v = sound.getBand(b);
            if (v < 0) continue;
            bands[b].setValue(v);
            bandValues[b].setText(signed(v - 3));
        }
    }

    private void setTone(LevelSlider s, int v) {
        if (v < 0) return;
        s.setValue(v);
        ((TextView) s.getTag()).setText(signed(v - 7));
    }

    private void refreshChips() {
        boolean auto = EqAuto.isEnabled(this);
        for (int i = 0; i < presetChips.length; i++) {
            paint(presetChips[i], !auto && i == preset);
        }
        paint(autoChip, auto);
        paint(loudChip, loudness);
        autoInfo.setText(auto ? EqAuto.currentLabel(this) : "");
    }

    private void paint(TextView chip, boolean on) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        if (on) {
            bg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            bg.setColors(new int[]{Ui.ACCENT_LIGHT, Ui.ACCENT});
        } else {
            bg.setColor(0x26FFFFFF);
        }
        chip.setBackground(bg);
    }

    private static int clamp(int v) {
        return v < 0 ? SoundClient.BALANCE_MAX / 2 : v;
    }

    private static String signed(int v) {
        return v > 0 ? "+" + v : String.valueOf(v);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sound.unbind(this);
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
