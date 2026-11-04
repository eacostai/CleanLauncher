package com.simple.carlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.view.View;

/** Speedometer arc fed by the GPS speed; shows "--" while there is no GPS fix. */
public class SpeedView extends View {

    private static final float MAX_KMH = 180f;
    private static final float START = 150f, SWEEP = 240f;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint note = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float speed, shown;
    private boolean hasFix;
    private String noFixLabel = "buscando GPS";

    public SpeedView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setStrokeWidth(7 * d);
        track.setColor(0x33FFFFFF);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setStrokeWidth(7 * d);
        number.setColor(Color.WHITE);
        number.setTypeface(Typeface.DEFAULT_BOLD);
        number.setTextAlign(Paint.Align.CENTER);
        unit.setColor(0xB3FFFFFF);
        unit.setTextAlign(Paint.Align.CENTER);
        unit.setTextSize(14 * c.getResources().getDisplayMetrics().scaledDensity);
        unit.setLetterSpacing(0.15f);
        unit.setTypeface(Typeface.DEFAULT_BOLD);
        note.setColor(0x99FFFFFF);
        note.setTextAlign(Paint.Align.CENTER);
        note.setTextSize(11 * c.getResources().getDisplayMetrics().scaledDensity);
    }

    /** Speed in km/h from the GPS. */
    void setSpeed(float kmh) {
        speed = Math.max(0, kmh);
        hasFix = true;
        invalidate();
    }

    /** No current GPS speed; label says why (searching, GPS off, no permission). */
    void setNoFix(String label) {
        hasFix = false;
        noFixLabel = label;
        speed = 0;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float pad = arc.getStrokeWidth();
        float size = Math.min(w, h) - pad * 2;
        r.set((w - size) / 2f, pad, (w + size) / 2f, pad + size);
        SweepGradient g = new SweepGradient(r.centerX(), r.centerY(),
                new int[]{Ui.ACCENT_LIGHT, Ui.ACCENT, Ui.ACCENT_LIGHT}, null);
        arc.setShader(g);
        number.setTextSize(size * 0.32f);
    }

    @Override
    protected void onDraw(Canvas c) {
        shown += (speed - shown) * 0.25f;
        if (Math.abs(speed - shown) > 0.3f) postInvalidateOnAnimation();
        else shown = speed;

        c.drawArc(r, START, SWEEP, false, track);
        float level = Math.min(1f, shown / MAX_KMH);
        if (hasFix && level > 0.004f) c.drawArc(r, START, SWEEP * level, false, arc);

        // Always "<speed> KM/H"; without a GPS fix the number is dimmed and the reason shown below.
        float cy = r.centerY() + number.getTextSize() * 0.3f;
        number.setAlpha(hasFix ? 255 : 110);
        c.drawText(String.valueOf(hasFix ? Math.round(shown) : 0), r.centerX(), cy, number);
        float uy = cy + unit.getTextSize() * 1.35f;
        c.drawText("KM/H", r.centerX(), uy, unit);
        if (!hasFix) c.drawText(noFixLabel, r.centerX(), uy + note.getTextSize() * 1.5f, note);
    }
}
