package com.simple.carlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Home screen background: the city skyline (day or night) and a highway drawn in the same
 * perspective as the car photo, so the Sorento stands with its wheels on the road instead of
 * floating over a straight-on road. The road follows the line of the car's wheels to its
 * vanishing point; a soft shadow sits under the four wheels. Dark scrims keep text readable.
 */
public class SceneView extends View {

    // Reference points in the bundled car photo (1600 x 901 px): ground contact of the near front
    // and rear wheels and of the far front wheel, and the horizon (camera at roof height).
    private static final float IW = 1600, IH = 901;
    private static final float[] NEAR_FRONT = {850, 894}, NEAR_REAR = {1450, 734}, FAR_FRONT = {150, 791};
    private static final float HORIZON = 60;
    /** Lane borders across the road, in car track widths (0 = far wheels, 1 = near wheels). */
    private static final float[] LANES = {-1.7f, -0.35f, 1.35f, 2.7f};

    private final boolean night;
    private final float d;
    private final Bitmap sky;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final RectF car = new RectF();
    private boolean hasCar;

    public SceneView(Context c, boolean night) {
        super(c);
        this.night = night;
        d = c.getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_SOFTWARE, null); // blur mask filters
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        sky = BitmapFactory.decodeResource(c.getResources(), night ? R.drawable.city_night : R.drawable.city_day, o);
        shadow.setColor(0xFF000000);
        trail.setStyle(Paint.Style.STROKE);
        trail.setStrokeCap(Paint.Cap.ROUND);
    }

    /** Where the car photo is drawn, in this view's coordinates. */
    void setCar(float l, float t, float r, float b) {
        car.set(l, t, r, b);
        hasCar = r > l && b > t;
        invalidate();
    }

    private PointF map(float[] p) {
        return new PointF(car.left + p[0] * car.width() / IW, car.top + p[1] * car.height() / IH);
    }

    /** Where the line a-b meets the horizon line y. */
    private static PointF atHorizon(PointF a, PointF b, float y) {
        return new PointF(a.x + (y - a.y) * (b.x - a.x) / (b.y - a.y), y);
    }

    private static PointF intersect(PointF p1, PointF p2, PointF p3, PointF p4) {
        float den = (p1.x - p2.x) * (p3.y - p4.y) - (p1.y - p2.y) * (p3.x - p4.x);
        float t = ((p1.x - p3.x) * (p3.y - p4.y) - (p1.y - p3.y) * (p3.x - p4.x)) / den;
        return new PointF(p1.x + t * (p2.x - p1.x), p1.y + t * (p2.y - p1.y));
    }

    private PointF vp, ff, fw;

    /** A point on the line from the vanishing point through the road position f, k times as far. */
    private PointF along(float f, float k) {
        float x = ff.x + (fw.x - ff.x) * f, y = ff.y + (fw.y - ff.y) * f;
        return new PointF(vp.x + (x - vp.x) * k, vp.y + (y - vp.y) * k);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (!hasCar) car.set(w * 0.3f, h * 0.2f, w * 0.7f, h * 0.2f + w * 0.4f * IH / IW);
        fw = map(NEAR_FRONT);
        PointF rw = map(NEAR_REAR);
        ff = map(FAR_FRONT);
        float hy = map(new float[]{0, HORIZON}).y;
        vp = atHorizon(fw, rw, hy);
        PointF vp2 = atHorizon(fw, ff, hy);
        PointF fr = intersect(vp, ff, vp2, rw);

        // Sky and skyline, the skyline's base on the horizon.
        float s = (float) w / sky.getWidth();
        float skyH = sky.getHeight() * s;
        paint.setShader(null);
        paint.setColor(sky.getPixel(4, 2));
        c.drawRect(0, 0, w, hy, paint);
        src.set(0, 0, sky.getWidth(), sky.getHeight());
        dst.set(0, hy - skyH, w, hy);
        c.drawBitmap(sky, src, dst, paint);

        // Ground.
        paint.setShader(new LinearGradient(0, hy, 0, h, night ? 0xFF22242A : 0xFF848886,
                night ? 0xFF0C0D10 : 0xFF5C6060, Shader.TileMode.CLAMP));
        c.drawRect(0, hy, w, h, paint);
        paint.setShader(null);

        // Road surface and its edge lines.
        path.reset();
        path.moveTo(vp.x, vp.y);
        PointF a = along(LANES[0], 40), b = along(LANES[LANES.length - 1], 40);
        path.lineTo(a.x, a.y);
        path.lineTo(b.x, b.y);
        path.close();
        paint.setColor(night ? 0xFF22232A : 0xFF484A50);
        c.drawPath(path, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setColor(night ? 0xFF9696A0 : 0xFFE1E1E6);
        paint.setStrokeWidth(3 * d);
        for (float f : new float[]{LANES[0], LANES[LANES.length - 1]}) {
            PointF e = along(f, 40);
            c.drawLine(vp.x, vp.y, e.x, e.y, paint);
        }
        // Lane dashes, longer and thicker towards the viewer.
        for (int i = 1; i < LANES.length - 1; i++) {
            paint.setColor(i == 1 ? 0xFFF0C85A : 0xFFEBEBF0);
            PointF e = along(LANES[i], 40);
            for (int k = 0; k < 60; k++) {
                float t0 = (k / 60f) * (k / 60f), t1 = ((k + 0.45f) / 60f) * ((k + 0.45f) / 60f);
                float x0 = vp.x + (e.x - vp.x) * t0, y0 = vp.y + (e.y - vp.y) * t0;
                float x1 = vp.x + (e.x - vp.x) * t1, y1 = vp.y + (e.y - vp.y) * t1;
                if (y0 > h) break;
                paint.setStrokeWidth(Math.max(1.5f * d, (y0 - hy) / 26f));
                c.drawLine(x0, y0, x1, y1, paint);
            }
        }

        // Night traffic: red tail-light and white head-light trails in the other lanes.
        if (night) {
            float[] where = {-1.2f, -0.9f, 1.85f, 2.2f};
            int[] colors = {0xC8FF3C32, 0xC8FF5F3C, 0xC8FFF5DC, 0xC8D7E6FF};
            for (int i = 0; i < where.length; i++) {
                PointF e = along(where[i], 40);
                trail.setColor(colors[i]);
                trail.setStrokeWidth(4 * d);
                trail.setMaskFilter(new BlurMaskFilter(5 * d, BlurMaskFilter.Blur.NORMAL));
                c.drawLine(vp.x, vp.y, e.x, e.y, trail);
                trail.setMaskFilter(null);
                trail.setStrokeWidth(1.5f * d);
                c.drawLine(vp.x, vp.y, e.x, e.y, trail);
            }
        }

        // Shadow under the car, darker at each wheel.
        float blur = Math.max(4 * d, car.width() * 0.02f);
        shadow.setMaskFilter(new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL));
        shadow.setAlpha(night ? 200 : 150);
        path.reset();
        path.moveTo(fw.x, fw.y);
        path.lineTo(rw.x, rw.y);
        path.lineTo(fr.x, fr.y);
        path.lineTo(ff.x, ff.y);
        path.close();
        c.drawPath(path, shadow);
        shadow.setAlpha(235);
        float rx = car.width() * 0.058f, ry = rx * 0.27f;
        for (PointF p : new PointF[]{fw, rw, fr, ff}) {
            dst.set(p.x - rx, p.y - ry, p.x + rx, p.y + ry);
            c.drawOval(dst, shadow);
        }
        shadow.setMaskFilter(null);

        // Scrims: darker at the sides (text columns) and along the bottom (speedometer, dock).
        int edge = night ? 0x96000000 : 0x6E000000, mid = night ? 0x46000000 : 0x1E000000;
        paint.setShader(new LinearGradient(0, 0, w, 0, new int[]{edge, mid, edge}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(new LinearGradient(0, h * 0.66f, 0, h, 0x00000000, 0x96000000, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.66f, w, h, paint);
        paint.setShader(null);
    }
}
