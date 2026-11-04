package com.simple.carlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * Wide speedometer along the bottom of the home screen: a smooth hill-shaped line from 0 to
 * 140 km/h, filled in blue up to the current GPS speed, with the 100 km/h limit marked in red
 * (the number turns red above it).
 * Without a GPS fix the number is dimmed and the reason is written under KM/H.
 */
public class GaugeView extends View {

    private static final float MAX = 140f;
    private static final float LIMIT = 100f;

    private final Paint curve = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curveOn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint note = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint maxLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path line = new Path(), area = new Path();
    private final float d;
    private final int textColor;

    private float speed, shown;
    private boolean hasFix;
    private String noFixLabel = "buscando GPS";

    public GaugeView(Context c, int text, int sub) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        textColor = text;
        float sp = c.getResources().getDisplayMetrics().scaledDensity;
        curve.setStyle(Paint.Style.STROKE);
        curve.setStrokeWidth(1.4f * d);
        curve.setColor(sub);
        curveOn.setStyle(Paint.Style.STROKE);
        curveOn.setStrokeWidth(2.6f * d);
        curveOn.setStrokeCap(Paint.Cap.ROUND);
        curveOn.setColor(Ui.ACCENT);
        needle.setStrokeWidth(2.5f * d);
        needle.setColor(text);
        tick.setColor(sub);
        tick.setTextAlign(Paint.Align.CENTER);
        tick.setTextSize(12 * sp);
        number.setColor(text);
        number.setTextAlign(Paint.Align.RIGHT);
        number.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        unit.setColor(sub);
        unit.setTextSize(16 * sp);
        unit.setLetterSpacing(0.05f);
        note.setColor(sub);
        note.setTextSize(11 * sp);
        maxLine.setColor(0xFFE5484D);
        maxLine.setStrokeWidth(1.5f * d);
        maxLine.setTextSize(10 * sp);
    }

    void setSpeed(float kmh) {
        speed = Math.max(0, kmh);
        hasFix = true;
        invalidate();
    }

    void setNoFix(String label) {
        hasFix = false;
        noFixLabel = label;
        speed = 0;
        invalidate();
    }

    private float x0, x1, top, bottom;

    private float xOf(float v) {
        return x0 + (x1 - x0) * Math.min(1f, v / MAX);
    }

    private static float smooth(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Height of the hill (0 = bottom, 1 = plateau) at a position 0..1 across the gauge. */
    private static float hill(float t) {
        return t < 0.36f ? smooth(t / 0.36f) : t > 0.64f ? smooth((1 - t) / 0.36f) : 1f;
    }

    private float yOf(float x) {
        return bottom - (bottom - top) * hill((x - x0) / (x1 - x0));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        x0 = 0;
        x1 = w;
        top = h * 0.06f;
        bottom = h - 2 * d;
        number.setTextSize(h * 0.42f);
        line.reset();
        for (int i = 0; i <= 120; i++) {
            float x = x0 + (x1 - x0) * i / 120f;
            if (i == 0) line.moveTo(x, yOf(x));
            else line.lineTo(x, yOf(x));
        }
        fill.setShader(new LinearGradient(0, top, 0, bottom, 0x663E82F7, 0x1A3E82F7, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        shown += (speed - shown) * 0.22f;
        if (Math.abs(speed - shown) > 0.3f) postInvalidateOnAnimation();
        else shown = speed;

        float xs = xOf(hasFix ? shown : 0);
        // Blue area and line up to the current speed.
        if (hasFix && shown > 0.5f) {
            area.reset();
            area.moveTo(x0, bottom);
            for (float x = x0; x <= xs; x += 3 * d) area.lineTo(x, yOf(x));
            area.lineTo(xs, yOf(xs));
            area.lineTo(xs, bottom);
            area.close();
            c.drawPath(area, fill);
        }
        c.drawPath(line, curve);
        if (hasFix && shown > 0.5f) {
            c.save();
            c.clipRect(x0, 0, xs, getHeight());
            c.drawPath(line, curveOn);
            c.restore();
        }

        // Scale under the line.
        for (int v = 20; v <= 120; v += 20) {
            float x = xOf(v);
            c.drawText(String.valueOf(v), x, yOf(x) + tick.getTextSize() * 2.1f, tick);
        }

        // Speed limit.
        float xm = xOf(LIMIT);
        float ym = yOf(xm);
        c.drawLine(xm, ym - 6 * d, xm, ym + getHeight() * 0.5f, maxLine);
        c.drawText("LÍMITE " + Math.round(LIMIT), xm + 6 * d, ym + getHeight() * 0.33f, maxLine);

        // Needle, number to its left and KM/H to its right; kept away from the edges.
        float xn = Math.max(number.getTextSize() * 1.6f, Math.min(x1 - 90 * d, xs));
        c.drawLine(xn, yOf(xn) - 4 * d, xn, bottom, needle);
        number.setColor(hasFix && shown > LIMIT ? 0xFFE5484D : textColor);
        number.setAlpha(hasFix ? 255 : 110);
        float base = bottom - getHeight() * 0.08f;
        c.drawText(String.valueOf(hasFix ? Math.round(shown) : 0), xn - 8 * d, base, number);
        c.drawText("KM/H", xn + 12 * d, base - unit.getTextSize() * 0.2f, unit);
        if (!hasFix) c.drawText(noFixLabel, xn + 12 * d, base - unit.getTextSize() * 1.6f, note);
    }
}
