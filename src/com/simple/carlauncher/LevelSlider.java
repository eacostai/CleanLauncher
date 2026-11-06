package com.simple.carlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/** Rounded neon slider (vertical for EQ bands, horizontal for tones) with integer steps. */
public class LevelSlider extends View {

    public interface Listener {
        void onChanged(LevelSlider s, int value, boolean done);
    }

    private final boolean vertical;
    private final int max;
    private int value;
    private Listener listener;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centre = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;

    public LevelSlider(Context c, boolean vertical, int max) {
        super(c);
        this.vertical = vertical;
        this.max = max;
        d = c.getResources().getDisplayMetrics().density;
        track.setColor(0x26FFFFFF);
        knob.setColor(0xFFFFFFFF);
        knob.setShadowLayer(6 * d, 0, 0, 0x882EE6FF);
        centre.setColor(0x55FFFFFF);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setValue(int v) {
        value = Math.max(0, Math.min(max, v));
        invalidate();
    }

    public int getValue() {
        return value;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        fill.setShader(vertical
                ? new LinearGradient(0, h, 0, 0, Ui.ACCENT_LIGHT, Ui.ACCENT, Shader.TileMode.CLAMP)
                : new LinearGradient(0, 0, w, 0, Ui.ACCENT_LIGHT, Ui.ACCENT, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), t = 8 * d, k = 11 * d;
        float frac = value / (float) max;
        if (vertical) {
            float cx = w / 2, top = k, bottom = h - k, y = bottom - frac * (bottom - top);
            r.set(cx - t / 2, top, cx + t / 2, bottom);
            c.drawRoundRect(r, t / 2, t / 2, track);
            c.drawLine(cx - 9 * d, (top + bottom) / 2, cx + 9 * d, (top + bottom) / 2, centre);
            r.set(cx - t / 2, y, cx + t / 2, bottom);
            c.drawRoundRect(r, t / 2, t / 2, fill);
            c.drawCircle(cx, y, k, knob);
        } else {
            float cy = h / 2, left = k, right = w - k, x = left + frac * (right - left);
            r.set(left, cy - t / 2, right, cy + t / 2);
            c.drawRoundRect(r, t / 2, t / 2, track);
            c.drawLine((left + right) / 2, cy - 9 * d, (left + right) / 2, cy + 9 * d, centre);
            r.set(left, cy - t / 2, x, cy + t / 2);
            c.drawRoundRect(r, t / 2, t / 2, fill);
            c.drawCircle(x, cy, k, knob);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float k = 11 * d;
        float frac = vertical
                ? 1 - (e.getY() - k) / (getHeight() - 2 * k)
                : (e.getX() - k) / (getWidth() - 2 * k);
        int v = Math.round(Math.max(0, Math.min(1, frac)) * max);
        boolean done = e.getAction() == MotionEvent.ACTION_UP;
        if (e.getAction() == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
        if (v != value || done) {
            value = v;
            invalidate();
            if (listener != null) listener.onChanged(this, v, done);
        }
        return true;
    }
}
