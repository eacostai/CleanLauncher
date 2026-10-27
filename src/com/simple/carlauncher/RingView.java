package com.simple.carlauncher;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.SweepGradient;
import android.view.View;

/** Glowing electric-blue ring used as the center dock button. */
public class RingView extends View {

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);

    public RingView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(4 * d);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(8 * d);
        glow.setMaskFilter(new BlurMaskFilter(8 * d, BlurMaskFilter.Blur.NORMAL));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        SweepGradient g = new SweepGradient(w / 2f, h / 2f,
                new int[]{Ui.ACCENT_LIGHT, Ui.ACCENT, 0xFF0A5FD6, Ui.ACCENT_LIGHT}, null);
        ring.setShader(g);
        glow.setShader(g);
    }

    @Override
    protected void onDraw(Canvas c) {
        float rad = Math.min(getWidth(), getHeight()) / 2f - glow.getStrokeWidth() * 1.5f;
        c.drawCircle(getWidth() / 2f, getHeight() / 2f, rad, glow);
        c.drawCircle(getWidth() / 2f, getHeight() / 2f, rad, ring);
    }
}
