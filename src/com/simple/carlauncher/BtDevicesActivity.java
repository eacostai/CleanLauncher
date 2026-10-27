package com.simple.carlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.bt.BTDevice;

import java.util.ArrayList;
import java.util.List;

/** Paired phones, search, connect/disconnect/forget, car name and PIN (factory BT module). */
public class BtDevicesActivity extends Activity {

    private final Handler handler = new Handler();
    private BtClient bt;

    private final List<BTDevice> paired = new ArrayList<>();
    private final List<BTDevice> found = new ArrayList<>();
    private boolean searching;
    private String connecting; // address being connected

    private LinearLayout list;
    private TextView carName, pin, searchChip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bt = NowPlaying.get(this).btClient;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(16));
        root.setBackground(Ui.background(this));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(Ui.iconButton(this, R.drawable.ic_back, 48, false, this::finish),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = Ui.text(this, "Dispositivos Bluetooth", 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setPadding(dp(16), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        searchChip = Ui.text(this, "Buscar", 16, Color.WHITE);
        searchChip.setPadding(dp(20), dp(10), dp(20), dp(10));
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(Ui.ACCENT);
        sbg.setCornerRadius(dp(22));
        searchChip.setBackground(sbg);
        Ui.pressable(searchChip, 0.92f);
        searchChip.setOnClickListener(v -> toggleSearch());
        bar.addView(searchChip);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(56)));

        LinearLayout body = new LinearLayout(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 1f);
        blp.topMargin = dp(12);
        root.addView(body, blp);
        body.addView(buildCarCard(), new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, -1, 1.5f);
        llp.leftMargin = dp(12);
        body.addView(buildListCard(), llp);

        setContentView(root);
    }

    private View buildCarCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(22));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                Ui.pick(this, new int[]{0xFF2E6BFF, 0xFF6A3BFF, 0xFFB04CE8}, new int[]{Ui.ACCENT_DEEP, Ui.PANEL, Ui.PANEL}));
        bg.setCornerRadius(dp(18));
        card.setBackground(bg);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_bluetooth);
        icon.setColorFilter(Color.WHITE);
        icon.setPadding(dp(18), dp(18), dp(18), dp(18));
        icon.setBackground(Ui.circle(0x33FFFFFF));
        card.addView(icon, new LinearLayout.LayoutParams(dp(84), dp(84)));

        TextView label = Ui.text(this, "TU CARRO SE LLAMA", 12, 0xCCFFFFFF);
        label.setLetterSpacing(0.12f);
        label.setPadding(0, dp(16), 0, 0);
        card.addView(label);
        carName = Ui.text(this, "—", 28, Color.WHITE);
        carName.setTypeface(Typeface.DEFAULT_BOLD);
        Ui.pressable(carName, 0.95f);
        carName.setOnClickListener(v -> ask("Nombre del carro", carName.getText().toString(), false, n -> {
            bt.setLocalName(n);
            handler.postDelayed(this::loadIdentity, 600);
        }));
        card.addView(carName);

        pin = Ui.text(this, "PIN —", 18, 0xE6FFFFFF);
        pin.setPadding(dp(16), dp(6), dp(16), dp(6));
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(0x33000000);
        pbg.setCornerRadius(dp(16));
        pin.setBackground(pbg);
        Ui.pressable(pin, 0.95f);
        pin.setOnClickListener(v -> ask("PIN de emparejamiento", "", true, p -> {
            bt.setPinCode(p);
            handler.postDelayed(this::loadIdentity, 600);
        }));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
        plp.topMargin = dp(8);
        card.addView(pin, plp);

        TextView help = Ui.text(this, "Para conectar un teléfono nuevo: abre el Bluetooth del teléfono, "
                + "busca este nombre y escribe el PIN.", 14, 0xCCFFFFFF);
        help.setSingleLine(false);
        help.setPadding(0, dp(18), 0, 0);
        card.addView(help);

        TextView edit = Ui.text(this, "Toca el nombre o el PIN para cambiarlos", 12, 0x99FFFFFF);
        edit.setPadding(0, dp(10), 0, 0);
        card.addView(edit);
        return card;
    }

    private View buildListCard() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Ui.card(this));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(12), dp(14), dp(12));
        scroll.addView(list);
        return scroll;
    }

    // ---------- List ----------

    private void render() {
        list.removeAllViews();
        String connected = bt.isPhoneConnected() ? bt.connectedAddress() : null;

        list.addView(section("Mis teléfonos"));
        if (paired.isEmpty()) list.addView(empty("Todavía no hay teléfonos emparejados"));
        for (BTDevice d : paired) {
            boolean isConnected = d.mAddress != null && d.mAddress.equalsIgnoreCase(connected);
            list.addView(row(d, isConnected, true));
        }

        if (searching || !found.isEmpty()) {
            list.addView(section(searching ? "Buscando teléfonos cercanos…" : "Encontrados"));
            if (found.isEmpty()) list.addView(empty("Asegúrate de que el Bluetooth del teléfono esté visible"));
            for (BTDevice d : found) list.addView(row(d, false, false));
        }
    }

    private View row(BTDevice d, boolean isConnected, boolean isPaired) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(12), dp(10), dp(10), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(isConnected ? 0x4D2ECC71 : 0x1AFFFFFF);
        if (isConnected) bg.setStroke(dp(1), 0x992ECC71);
        r.setBackground(bg);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_smartphone);
        icon.setColorFilter(isConnected ? 0xFF2ECC71 : Color.WHITE);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackground(Ui.circle(0x26FFFFFF));
        r.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, dp(8), 0);
        TextView name = Ui.text(this, d.mName == null || d.mName.isEmpty() ? d.mAddress : d.mName, 18, Color.WHITE);
        name.setGravity(Gravity.START);
        texts.addView(name);
        String state = isConnected ? "Conectado"
                : d.mAddress != null && d.mAddress.equals(connecting) ? "Conectando…"
                : isPaired ? "Toca para conectar" : "Toca para emparejar";
        TextView sub = Ui.text(this, state, 13, isConnected ? 0xFF7CF0A8 : 0xB3FFFFFF);
        sub.setGravity(Gravity.START);
        texts.addView(sub);
        r.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        if (isConnected) {
            TextView off = Ui.text(this, "Desconectar", 14, Color.WHITE);
            off.setPadding(dp(14), dp(8), dp(14), dp(8));
            GradientDrawable obg = new GradientDrawable();
            obg.setColor(0x33FFFFFF);
            obg.setCornerRadius(dp(16));
            off.setBackground(obg);
            Ui.pressable(off, 0.92f);
            off.setOnClickListener(v -> {
                bt.disconnect();
                handler.postDelayed(this::render, 1500);
            });
            r.addView(off);
        }
        if (isPaired) {
            ImageView del = new ImageView(this);
            del.setImageResource(R.drawable.ic_delete);
            del.setColorFilter(0xFFFF6B6B);
            del.setPadding(dp(10), dp(10), dp(10), dp(10));
            Ui.pressable(del, 0.85f);
            del.setOnClickListener(v -> new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("¿Olvidar " + d.mName + "?")
                    .setMessage("Tendrás que volver a emparejarlo desde el teléfono.")
                    .setPositiveButton("Olvidar", (x, y) -> {
                        bt.deletePaired(d.mPairRecordIndex);
                        handler.postDelayed(this::reload, 800);
                    })
                    .setNegativeButton("Cancelar", null)
                    .show());
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(46), dp(46));
            dlp.leftMargin = dp(6);
            r.addView(del, dlp);
        }

        if (!isConnected) {
            Ui.pressable(r, 0.97f);
            r.setOnClickListener(v -> {
                connecting = d.mAddress;
                if (searching) toggleSearch();
                bt.connect(d);
                render();
            });
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        r.setLayoutParams(lp);
        return r;
    }

    private TextView section(String s) {
        TextView t = Ui.text(this, s, 15, 0xCCFFFFFF);
        t.setGravity(Gravity.START);
        t.setPadding(dp(4), dp(6), 0, dp(10));
        return t;
    }

    private TextView empty(String s) {
        TextView t = Ui.text(this, s, 14, 0x80FFFFFF);
        t.setSingleLine(false);
        t.setPadding(dp(4), 0, 0, dp(12));
        return t;
    }

    // ---------- Actions ----------

    private void toggleSearch() {
        searching = !searching;
        if (searching) {
            found.clear();
            bt.startSearch();
        } else {
            bt.stopSearch();
        }
        searchChip.setText(searching ? "Detener" : "Buscar");
        render();
    }

    private void reload() {
        paired.clear();
        bt.requestPairedDevices();
        render();
    }

    private void loadIdentity() {
        String n = bt.localName(), p = bt.pinCode();
        carName.setText(n == null || n.isEmpty() ? "—" : n);
        pin.setText("PIN " + (p == null || p.isEmpty() ? "—" : p));
    }

    interface TextListener {
        void onText(String s);
    }

    private void ask(String title, String initial, boolean numeric, TextListener l) {
        EditText input = new EditText(this);
        input.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        input.setText(initial);
        input.setSelectAllOnFocus(true);
        input.setTextSize(22);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("Guardar", (d, w) -> {
                    String s = input.getText().toString().trim();
                    if (!s.isEmpty()) l.onText(s);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private final BroadcastReceiver events = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            i.setExtrasClassLoader(BTDevice.class.getClassLoader());
            String a = i.getAction();
            try {
                if ("com.bt.ACTION_GET_PAIR_DEVICE_RECORD".equals(a)) {
                    BTDevice d = i.getParcelableExtra("extra_pair_device");
                    if (d != null && d.mAddress != null) {
                        for (BTDevice e : paired) if (d.mAddress.equalsIgnoreCase(e.mAddress)) return;
                        paired.add(d);
                    }
                } else if ("com.bt.ACTION_BT_DEVICE_FOUND".equals(a)) {
                    String addr = i.getStringExtra("extra_bt_device_address");
                    if (addr == null) return;
                    for (BTDevice e : paired) if (addr.equalsIgnoreCase(e.mAddress)) return;
                    for (BTDevice e : found) if (addr.equalsIgnoreCase(e.mAddress)) return;
                    BTDevice d = new BTDevice(addr, i.getStringExtra("extra_bt_device_name"));
                    d.mDeviceClass = i.getIntExtra("extra_bt_device_class", 0);
                    found.add(d);
                } else if ("com.bt.ACTION_BT_DISCOVER_FINISH".equals(a)) {
                    searching = false;
                    searchChip.setText("Buscar");
                } else { // connection changed
                    connecting = null;
                    handler.postDelayed(BtDevicesActivity.this::reload, 1000);
                    return;
                }
            } catch (RuntimeException ignored) {
                return;
            }
            render();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction("com.bt.ACTION_GET_PAIR_DEVICE_RECORD");
        f.addAction("com.bt.ACTION_BT_DEVICE_FOUND");
        f.addAction("com.bt.ACTION_BT_DISCOVER_FINISH");
        f.addAction("com.bt.ACTION_BT_CONNECTION_CHANGE");
        f.addAction("com.bt.ACTION_BT_HFP_ESTABLISHED");
        f.addAction("com.bt.ACTION_BT_HFP_RELEASE");
        registerReceiver(events, f);
        loadIdentity();
        reload();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(events);
        if (searching) toggleSearch();
        handler.removeCallbacksAndMessages(null);
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
