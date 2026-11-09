package com.simple.carlauncher;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/** FM scale 87.5–108 MHz with a glowing needle at the current frequency. */
public class DialView extends View {

    private static final int MIN = 8750, MAX = 10800;

    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private float shown = -1, target = -1;

    public DialView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        tick.setStrokeWidth(1.5f * d);
        label.setColor(0x99FFFFFF);
        label.setTextSize(12 * d);
        label.setTextAlign(Paint.Align.CENTER);
        needle.setStrokeWidth(3 * d);
        needle.setStrokeCap(Paint.Cap.ROUND);
        needle.setColor(0xFFFFFFFF);
        glow.setStrokeWidth(10 * d);
        glow.setColor(Ui.ACCENT_LIGHT);
        glow.setMaskFilter(new BlurMaskFilter(10 * d, BlurMaskFilter.Blur.NORMAL));
    }

    /** frequency in MHz*100; ignored for AM. */
    public void setFrequency(int f) {
        target = Math.max(MIN, Math.min(MAX, f));
        if (shown < 0) shown = target;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), pad = 16 * d;
        float base = h * 0.62f;
        tick.setShader(new LinearGradient(0, 0, w, 0, Ui.ACCENT_LIGHT, Ui.ACCENT, Shader.TileMode.CLAMP));

        for (int f = 8800; f <= MAX; f += 20) {
            float x = x(f, w, pad);
            boolean major = f % 100 == 0, big = f % 400 == 0;
            float len = big ? h * 0.42f : major ? h * 0.26f : h * 0.12f;
            tick.setAlpha(big ? 255 : major ? 180 : 90);
            c.drawLine(x, base - len, x, base, tick);
            if (big) c.drawText(String.valueOf(f / 100), x, base + label.getTextSize() * 1.5f, label);
        }

        if (target < 0) return;
        shown += (target - shown) * 0.2f;
        if (Math.abs(target - shown) > 0.5f) postInvalidateOnAnimation();
        else shown = target;
        float x = x(shown, w, pad);
        c.drawLine(x, h * 0.08f, x, base + 4 * d, glow);
        c.drawLine(x, h * 0.08f, x, base + 4 * d, needle);
    }

    private static float x(float f, float w, float pad) {
        return pad + (f - MIN) / (MAX - MIN) * (w - 2 * pad);
    }
}
