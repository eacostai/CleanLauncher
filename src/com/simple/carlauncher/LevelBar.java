package com.simple.carlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Thin rounded progress line in electric blue (song position). */
public class LevelBar extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float level;

    public LevelBar(Context c) {
        super(c);
        track.setColor(0x40FFFFFF);
    }

    void setLevel(float l) {
        level = Math.max(0, Math.min(1, l));
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        fill.setShader(new LinearGradient(0, 0, w, 0, Ui.ACCENT, Ui.ACCENT_LIGHT, Shader.TileMode.CLAMP));
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
