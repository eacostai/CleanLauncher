package com.simple.carlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;

/**
 * Compass for the home screen: the dial turns so the arrow at the top always shows where
 * the car is heading, with the direction written below ("NE · 45°"). The heading comes from
 * the GPS while driving and from the compass sensor when parked (if the unit has one).
 */
public class CompassView extends View {

    private static final String[] NAMES = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ticks = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letters = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint north = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tri = new Path();
    private final float d;

    private float heading, shown;
    private boolean known;

    public CompassView(Context c, int text, int sub) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        float sp = c.getResources().getDisplayMetrics().scaledDensity;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(1.2f * d);
        ring.setColor(sub);
        ticks.setColor(sub);
        ticks.setStrokeWidth(1 * d);
        letters.setColor(text);
        letters.setTextAlign(Paint.Align.CENTER);
        letters.setTextSize(11 * sp);
        north.setColor(0xFFE5484D);
        north.setTextAlign(Paint.Align.CENTER);
        north.setTextSize(12 * sp);
        north.setTypeface(Typeface.DEFAULT_BOLD);
        arrow.setColor(Ui.ACCENT);
        label.setColor(sub);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(11 * sp);
        label.setLetterSpacing(0.12f);
        value.setColor(text);
        value.setTextAlign(Paint.Align.CENTER);
        value.setTextSize(17 * sp);
        value.setTypeface(Typeface.DEFAULT_BOLD);
    }

    /** Heading in degrees from north (clockwise); NaN = unknown. */
    void setHeading(float deg) {
        if (Float.isNaN(deg)) {
            if (!known) return;
            known = false;
        } else {
            deg = (deg % 360 + 360) % 360;
            if (known && Math.abs(diff(deg, heading)) < 0.8f) return;
            if (!known) shown = deg;
            heading = deg;
            known = true;
        }
        invalidate();
    }

    static String name(float deg) {
        return NAMES[Math.round(((deg % 360) + 360) % 360 / 45f) % 8];
    }

    private static float diff(float a, float b) {
        float x = (a - b) % 360;
        if (x > 180) x -= 360;
        if (x < -180) x += 360;
        return x;
    }

    @Override
    protected void onDraw(Canvas c) {
        float delta = diff(heading, shown);
        shown += delta * 0.2f;
        if (Math.abs(delta) > 0.3f) postInvalidateOnAnimation();
        else shown = heading;

        float w = getWidth(), h = getHeight();
        float textH = value.getTextSize() * 2.4f;
        float r = Math.min(w, h - textH) / 2f - 4 * d;
        float cx = w / 2f, cy = r + 4 * d;

        c.drawCircle(cx, cy, r, ring);
        c.save();
        c.rotate(-shown, cx, cy);
        for (int i = 0; i < 72; i++) {
            float len = i % 18 == 0 ? 0 : i % 2 == 0 ? 6 * d : 3 * d;
            if (len > 0) c.drawLine(cx, cy - r, cx, cy - r + len, ticks);
            c.rotate(5, cx, cy);
        }
        c.restore();
        for (int i = 0; i < 4; i++) {
            // Letters move with the dial but stay upright.
            double a = Math.toRadians(i * 90 - shown);
            float lx = cx + (float) Math.sin(a) * (r - 12 * d);
            float ly = cy - (float) Math.cos(a) * (r - 12 * d) + letters.getTextSize() * 0.36f;
            c.drawText(NAMES[i * 2], lx, ly, i == 0 ? north : letters);
        }

        // Arrow in the middle: always pointing up, the way the car is going.
        arrow.setAlpha(known ? 255 : 90);
        float s = r * 0.42f;
        tri.reset();
        tri.moveTo(cx, cy - s);
        tri.lineTo(cx + s * 0.55f, cy + s * 0.7f);
        tri.lineTo(cx, cy + s * 0.35f);
        tri.lineTo(cx - s * 0.55f, cy + s * 0.7f);
        tri.close();
        c.drawPath(tri, arrow);

        float ty = cy + r + label.getTextSize() * 1.7f;
        c.drawText("RUMBO", cx, ty, label);
        c.drawText(known ? name(shown) + " · " + Math.round((shown % 360 + 360) % 360) % 360 + "°" : "--",
                cx, ty + value.getTextSize() * 1.25f, value);
    }
}
