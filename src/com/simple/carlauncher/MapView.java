package com.simple.carlauncher;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.SystemClock;
import android.view.Gravity;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Google map of the current position on the home screen, using Google's public map embed
 * (the same one websites use, no API key). The map can be moved and zoomed with the finger;
 * links inside it open the Google Maps app. It follows the car by reloading after it has
 * moved a few hundred metres.
 */
public class MapView extends FrameLayout {

    private static final float RELOAD_METERS = 250;
    private static final long RELOAD_MIN_MS = 20_000;

    private final WebView web;
    private final TextView status;
    private Location shown;
    private long shownAt;
    private int zoom = 16;

    public MapView(Context c) {
        super(c);
        setBackgroundColor(0xFF1A1F27);

        web = new WebView(c);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setBackgroundColor(0xFF1A1F27);
        web.setWebViewClient(new WebViewClient() {
            @Override
            @SuppressWarnings("deprecation") // the default request-based version calls this one
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                // The map lives in an iframe; anything that replaces the whole page is a link out.
                openInMaps(url); // "Ver mapa más grande", directions, place links
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                status.setVisibility(GONE);
            }

            @Override
            public void onReceivedError(WebView v, int code, String desc, String url) {
                status.setText("Sin internet para el mapa");
                status.setVisibility(VISIBLE);
            }
        });
        addView(web, new LayoutParams(-1, -1));

        status = Ui.text(c, "Buscando tu ubicación…", 15, 0xB3FFFFFF);
        addView(status, new LayoutParams(-1, -2, Gravity.CENTER));
    }

    void setLocation(Location l) {
        long now = SystemClock.elapsedRealtime();
        if (shown != null && (shown.distanceTo(l) < RELOAD_METERS || now - shownAt < RELOAD_MIN_MS)) return;
        shown = l;
        shownAt = now;
        // Same markup a web page uses to embed a Google map: Google serves it only inside a frame.
        String src = String.format(Locale.US,
                "https://www.google.com/maps?q=%.6f,%.6f&z=%d&hl=es&output=embed",
                l.getLatitude(), l.getLongitude(), zoom);
        String html = "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>html,body{margin:0;height:100%;background:#1A1F27}"
                + "iframe{border:0;width:100%;height:100%;display:block}</style></head>"
                + "<body><iframe src=\"" + src + "\" allowfullscreen></iframe></body></html>";
        web.loadDataWithBaseURL("https://www.google.com/", html, "text/html", "utf-8", null);
    }

    /** Zooms out a little on the highway so more of the road ahead is visible. */
    void setSpeed(float kmh) {
        zoom = kmh > 70 ? 15 : 16;
    }

    void onResume() {
        web.onResume();
    }

    void onPause() {
        web.onPause();
    }

    private void openInMaps(String url) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.google.android.apps.maps");
        try {
            getContext().startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        web.destroy();
    }
}
