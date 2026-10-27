package com.simple.carlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small live map for the home screen: map tiles around the current position with a
 * direction arrow. Tiles come from CARTO (OpenStreetMap data) and are cached on disk,
 * so places already driven show without internet. Navigation itself opens in Google Maps.
 */
public class MapView extends View {

    private static final String DARK = "https://basemaps.cartocdn.com/dark_all/%d/%d/%d%s.png";
    private static final String LIGHT = "https://basemaps.cartocdn.com/rastertiles/voyager/%d/%d/%d%s.png";
    private static final long RETRY_MS = 30_000;

    private final LruCache<String, Bitmap> cache = new LruCache<>(40);
    private final Set<String> loading = new HashSet<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final File dir;
    private final boolean dark, retina;
    private final float tileSize;

    private final Paint tilePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint empty = new Paint();
    private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint note = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint credit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrowPath = new Path();
    private final RectF dst = new RectF();

    private double lat, lon;
    private float bearing;
    private boolean hasFix;
    private int zoom = 16;

    public MapView(Context c, boolean dark) {
        super(c);
        this.dark = dark;
        float d = c.getResources().getDisplayMetrics().density;
        retina = d >= 1.5f;
        tileSize = 256 * d;
        dir = new File(c.getCacheDir(), "tiles");
        dir.mkdirs();
        pool.execute(this::trimDiskCache);

        empty.setColor(dark ? 0xFF1A1F27 : 0xFFE8ECF0);
        arrow.setColor(Ui.ACCENT);
        arrowEdge.setColor(Color.WHITE);
        arrowEdge.setStyle(Paint.Style.STROKE);
        arrowEdge.setStrokeWidth(2.5f * d);
        arrowEdge.setStrokeJoin(Paint.Join.ROUND);
        halo.setColor(0x331E90FF);
        note.setColor(dark ? 0xB3FFFFFF : 0xB3000000);
        note.setTextAlign(Paint.Align.CENTER);
        note.setTextSize(15 * c.getResources().getDisplayMetrics().scaledDensity);
        credit.setColor(dark ? 0x80FFFFFF : 0x99000000);
        credit.setTextSize(9 * c.getResources().getDisplayMetrics().scaledDensity);
        credit.setTextAlign(Paint.Align.RIGHT);

        // Navigation arrow pointing up, centred on (0, 0).
        float a = 13 * d;
        arrowPath.moveTo(0, -a);
        arrowPath.lineTo(a * 0.8f, a);
        arrowPath.lineTo(0, a * 0.5f);
        arrowPath.lineTo(-a * 0.8f, a);
        arrowPath.close();
    }

    void setLocation(Location l) {
        lat = l.getLatitude();
        lon = l.getLongitude();
        if (l.hasBearing() && l.hasSpeed() && l.getSpeed() > 1.5f) bearing = l.getBearing();
        hasFix = true;
        invalidate();
    }

    /** Zooms out a little on the highway so more of the road ahead is visible. */
    void setSpeed(float kmh) {
        int z = kmh > 70 ? 15 : 16;
        if (z != zoom) {
            zoom = z;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawRect(0, 0, w, h, empty);
        if (!hasFix) {
            c.drawText("Buscando tu ubicación…", w / 2f, h / 2f, note);
            return;
        }
        int n = 1 << zoom;
        double latR = Math.toRadians(lat);
        double px = (lon + 180) / 360 * n;
        double py = (1 - Math.log(Math.tan(latR) + 1 / Math.cos(latR)) / Math.PI) / 2 * n;
        // Position a bit below centre: more road ahead than behind.
        float cx = w / 2f, cy = h * 0.6f;

        int tx0 = (int) Math.floor(px - cx / tileSize), tx1 = (int) Math.floor(px + (w - cx) / tileSize);
        int ty0 = (int) Math.floor(py - cy / tileSize), ty1 = (int) Math.floor(py + (h - cy) / tileSize);
        for (int ty = Math.max(0, ty0); ty <= Math.min(n - 1, ty1); ty++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                Bitmap b = tile(zoom, ((tx % n) + n) % n, ty);
                if (b == null) continue;
                float left = cx + (float) ((tx - px) * tileSize), top = cy + (float) ((ty - py) * tileSize);
                dst.set(left, top, left + tileSize, top + tileSize);
                c.drawBitmap(b, null, dst, tilePaint);
            }
        }

        c.drawCircle(cx, cy, arrowEdge.getStrokeWidth() * 9, halo);
        c.save();
        c.translate(cx, cy);
        c.rotate(bearing);
        c.drawPath(arrowPath, arrow);
        c.drawPath(arrowPath, arrowEdge);
        c.restore();

        c.drawText("© OpenStreetMap · © CARTO", w - credit.getTextSize() * 0.6f, h - credit.getTextSize() * 0.6f, credit);
    }

    private Bitmap tile(int z, int x, int y) {
        String key = (dark ? "d" : "l") + (retina ? "2_" : "_") + z + "_" + x + "_" + y;
        Bitmap b = cache.get(key);
        if (b != null || loading.contains(key)) return b;
        loading.add(key);
        pool.execute(() -> {
            Bitmap got = load(key, z, x, y);
            main.post(() -> {
                if (got != null) {
                    cache.put(key, got);
                    loading.remove(key);
                    invalidate();
                } else {
                    // Offline or server error: try this tile again later, not on every frame.
                    main.postDelayed(() -> loading.remove(key), RETRY_MS);
                }
            });
        });
        return null;
    }

    private Bitmap load(String key, int z, int x, int y) {
        File f = new File(dir, key + ".png");
        if (f.exists()) {
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b != null) return b;
        }
        HttpURLConnection con = null;
        try {
            URL url = new URL(String.format(Locale.US, dark ? DARK : LIGHT, z, x, y, retina ? "@2x" : ""));
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            con.setRequestProperty("User-Agent", "CleanLauncher (car head unit)");
            if (con.getResponseCode() != 200) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = con.getInputStream()) {
                byte[] buf = new byte[8192];
                for (int r; (r = in.read(buf)) > 0; ) out.write(buf, 0, r);
            }
            byte[] bytes = out.toByteArray();
            Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (b != null) {
                try (FileOutputStream fo = new FileOutputStream(f)) {
                    fo.write(bytes);
                }
            }
            return b;
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    /** Keeps the tile cache to roughly 60 MB by dropping the oldest tiles. */
    private void trimDiskCache() {
        File[] files = dir.listFiles();
        if (files == null || files.length < 3000) return;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < files.length / 2; i++) files[i].delete();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        pool.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }
}
