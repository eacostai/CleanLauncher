package com.simple.carlauncher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Modern Bluetooth screen: phone music + dialer, on top of the factory BT service. */
public class BtActivity extends Activity implements NowPlaying.Listener {

    private static final String FACTORY_PHONE = "com.nwd.android.phone/.home_horizontalActivity";

    private final Handler handler = new Handler();
    private NowPlaying nowPlaying;
    private BtClient bt;

    private TextView status, title, artist, number;
    private ImageView playBtn;
    private GradientDrawable statusBg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        nowPlaying = NowPlaying.get(this);
        bt = nowPlaying.btClient;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(16));
        root.setBackground(Ui.background(this));

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(56)));

        LinearLayout body = new LinearLayout(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 1f);
        blp.topMargin = dp(12);
        root.addView(body, blp);
        body.addView(buildMusic(), new LinearLayout.LayoutParams(0, -1, 1.25f));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, -1, 1f);
        dlp.leftMargin = dp(12);
        body.addView(buildDialer(), dlp);

        setContentView(root);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(Ui.iconButton(this, R.drawable.ic_back, 48, false, this::finish),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView t = Ui.text(this, "Bluetooth", 26, Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        t.setPadding(dp(16), 0, 0, 0);
        bar.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));

        status = Ui.text(this, "Buscando teléfono…", 15, Color.WHITE);
        status.setPadding(dp(18), dp(10), dp(18), dp(10));
        statusBg = new GradientDrawable();
        statusBg.setCornerRadius(dp(22));
        statusBg.setColor(0x26FFFFFF);
        status.setBackground(statusBg);
        Ui.pressable(status, 0.94f);
        status.setOnClickListener(v -> startActivity(new android.content.Intent(this, BtDevicesActivity.class)));
        bar.addView(status);
        return bar;
    }

    private View buildMusic() {
        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                Ui.pick(this, new int[]{0xFF2E6BFF, 0xFF6A3BFF, 0xFFB04CE8}, new int[]{Ui.ACCENT_DEEP, Ui.PANEL, Ui.PANEL}));
        bg.setCornerRadius(dp(18));
        card.setBackground(bg);

        ImageView big = new ImageView(this);
        big.setImageResource(R.drawable.ic_bluetooth);
        big.setColorFilter(Color.WHITE);
        big.setAlpha(0.14f);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(260), dp(260), Gravity.END | Gravity.TOP);
        blp.setMargins(0, dp(-30), dp(-50), 0);
        card.addView(big, blp);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.BOTTOM);
        content.setPadding(dp(28), dp(24), dp(28), dp(26));

        TextView label = Ui.text(this, "MÚSICA DEL TELÉFONO", 13, 0xCCFFFFFF);
        label.setLetterSpacing(0.12f);
        label.setGravity(Gravity.START);
        content.addView(label);

        title = Ui.text(this, "", 30, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.START);
        title.setSingleLine(false);
        title.setMaxLines(2);
        title.setPadding(0, dp(8), 0, 0);
        content.addView(title);

        artist = Ui.text(this, "", 19, 0xCCFFFFFF);
        artist.setGravity(Gravity.START);
        artist.setPadding(0, dp(4), 0, dp(22));
        content.addView(artist);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(Ui.iconButton(this, R.drawable.ic_prev, 60, false,
                () -> bt.playControl(BtClient.PREVIOUS)), new LinearLayout.LayoutParams(dp(60), dp(60)));
        playBtn = Ui.iconButton(this, R.drawable.ic_play, 80, true, this::togglePlay);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(80), dp(80));
        plp.setMargins(dp(22), 0, dp(22), 0);
        controls.addView(playBtn, plp);
        controls.addView(Ui.iconButton(this, R.drawable.ic_next, 60, false,
                () -> bt.playControl(BtClient.NEXT)), new LinearLayout.LayoutParams(dp(60), dp(60)));
        content.addView(controls);

        card.addView(content, new FrameLayout.LayoutParams(-1, -1));
        return card;
    }

    private void togglePlay() {
        NowPlaying.Track t = nowPlaying.track(NowPlaying.Source.BT);
        if (t.playing) {
            bt.playControl(BtClient.PAUSE);
        } else {
            Ui.selectBluetooth(this);
            bt.playControl(BtClient.PLAY);
        }
    }

    private View buildDialer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(14));
        card.setBackground(Ui.card(this));

        LinearLayout display = new LinearLayout(this);
        display.setGravity(Gravity.CENTER_VERTICAL);
        number = Ui.text(this, "", 30, Color.WHITE);
        number.setHint("Marcar número");
        number.setHintTextColor(0x66FFFFFF);
        display.addView(number, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView del = new ImageView(this);
        del.setImageResource(R.drawable.ic_backspace);
        del.setColorFilter(0xB3FFFFFF);
        del.setPadding(dp(10), dp(10), dp(10), dp(10));
        Ui.pressable(del, 0.85f);
        del.setOnClickListener(v -> {
            CharSequence s = number.getText();
            if (s.length() > 0) number.setText(s.subSequence(0, s.length() - 1));
        });
        del.setOnLongClickListener(v -> {
            number.setText("");
            return true;
        });
        display.addView(del, new LinearLayout.LayoutParams(dp(48), dp(48)));
        card.addView(display, new LinearLayout.LayoutParams(-1, dp(52)));

        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(this);
            for (int c = 0; c < 3; c++) {
                String k = keys[r * 3 + c];
                TextView key = Ui.text(this, k, 26, Color.WHITE);
                GradientDrawable kb = new GradientDrawable();
                kb.setColor(0x1AFFFFFF);
                kb.setCornerRadius(dp(14));
                key.setBackground(kb);
                Ui.pressable(key, 0.92f);
                key.setOnClickListener(v -> number.append(k));
                if (k.equals("0")) key.setOnLongClickListener(v -> { number.append("+"); return true; });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                row.addView(key, lp);
            }
            card.addView(row, new LinearLayout.LayoutParams(-1, 0, 1f));
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, dp(8), 0, 0);
        ImageView contacts = Ui.iconButton(this, R.drawable.ic_contacts, 52, false, () -> Ui.launch(this, FACTORY_PHONE));
        actions.addView(contacts, new LinearLayout.LayoutParams(dp(52), dp(52)));
        ImageView call = Ui.iconButton(this, R.drawable.ic_call, 60, false, this::call);
        call.setBackground(Ui.circle(0xFF2ECC71));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(60), dp(60));
        clp.leftMargin = dp(28);
        actions.addView(call, clp);
        card.addView(actions);
        return card;
    }

    private void call() {
        String n = number.getText().toString();
        if (n.isEmpty()) return;
        if (!bt.isPhoneConnected()) {
            Toast.makeText(this, "Conecta tu teléfono por Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }
        bt.dial(n);
    }

    private void refreshStatus() {
        String name = bt.isPhoneConnected() ? bt.deviceName() : null;
        boolean connected = bt.isPhoneConnected();
        status.setText(connected ? "●  " + (name == null || name.isEmpty() ? "Teléfono conectado" : name)
                : "Sin teléfono · Conectar");
        statusBg.setColor(connected ? 0xCC1E9E5A : 0x26FFFFFF);
    }

    @Override
    public void onNowPlayingChanged() {
        NowPlaying.Track t = nowPlaying.track(NowPlaying.Source.BT);
        title.setText(t.title.isEmpty() ? (bt.isPhoneConnected() ? "Pon música en tu teléfono" : "Sin conexión") : t.title);
        artist.setText(t.title.isEmpty() ? "" : t.artist);
        playBtn.setImageResource(t.playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private final Runnable poll = new Runnable() {
        public void run() {
            refreshStatus();
            handler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        nowPlaying.addListener(this);
        onNowPlayingChanged();
        handler.post(poll);
    }

    @Override
    protected void onPause() {
        super.onPause();
        nowPlaying.removeListener(this);
        handler.removeCallbacks(poll);
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
