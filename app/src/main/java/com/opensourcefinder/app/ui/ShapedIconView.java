/*
 * This file is part of OpenSourceFinder.
 *
 * Copyright (C) 2026 deepcodecreate
 *
 * OpenSourceFinder is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenSourceFinder is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenSourceFinder. If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.opensourcefinder.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;
import com.google.android.material.color.MaterialColors;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ShapedIconView extends AppCompatImageView {
    private static final int NEUTRAL = 0, FILLED = 1, EMPTY = 2, PENDING = 3;
    private static final int SAMPLE = 64;
    private static final float FILLED_COVERAGE = 0.70f;
    private static final float FILLED_BOX = 0.94f;

    private static final float FILLED_TRIM = 0.05f;

    private static final float GRADIENT_DISTANCE = 34f;
    private static final float EMPTY_BOX = 0.58f;
    private static final float NEUTRAL_BOX = 0.50f;

    private static final class Result {
        final int kind, color;
        final RectF bounds;

        final int color2, gradDir;
        int bitmapGeneration;
        Result(int kind, int color, RectF bounds) { this(kind, color, bounds, 0, 0); }
        Result(int kind, int color, RectF bounds, int color2, int gradDir) {
            this.kind = kind; this.color = color; this.bounds = bounds; this.color2 = color2; this.gradDir = gradDir;
        }
    }
    private static final RectF FULL = new RectF(0f, 0f, 1f, 1f);
    private static final Result NEUTRAL_RESULT = new Result(NEUTRAL, 0, FULL);
    private static final Result PENDING_RESULT = new Result(PENDING, 0, FULL);
    private static final Map<Drawable, Result> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Bitmap, Result> BITMAP_CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ExecutorService ANALYZER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "icon-analyzer");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final Object LOCK = new Object();
    private static Bitmap sampleBitmap;
    private static Canvas sampleCanvas;
    private static final int[] PIXELS = new int[SAMPLE * SAMPLE];

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Matrix matrix = new Matrix();
    private String shapeId = IconShapes.DEFAULT;
    private String pathShape;
    private float pathSize = -1f, pathOx, pathOy;
    private Drawable lastDrawable;
    private Result lastResult = NEUTRAL_RESULT;

    public ShapedIconView(Context context) { this(context, null); }
    public ShapedIconView(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public ShapedIconView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setShape(AppPreferences.getIconShape(context));
    }

    public void setShape(String id) {
        if (!IconShapes.isValid(id)) id = IconShapes.DEFAULT;
        if (id.equals(shapeId)) return;
        shapeId = id;
        pathShape = null;
        invalidate();
    }

    public void setSpinEnabled(boolean enabled) {}

    private Path shapePath(float size, float ox, float oy) {
        if (!shapeId.equals(pathShape) || size != pathSize || ox != pathOx || oy != pathOy) {
            IconShapes.fit(path, shapeId, size, ox, oy, matrix);
            pathShape = shapeId; pathSize = size; pathOx = ox; pathOy = oy;
            gradShader = null;
        }
        return path;
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        int h = getHeight() - getPaddingTop() - getPaddingBottom();
        float size = Math.min(w, h);
        if (size <= 0f) return;
        float cx = getPaddingLeft() + w / 2f, cy = getPaddingTop() + h / 2f;
        Path shape = shapePath(size, cx - size / 2f, cy - size / 2f);
        Drawable d = getDrawable();
        Drawable shown = unwrap(d);
        if (shown instanceof ShimmerDrawable) {

            Rect oldBounds = shown.copyBounds();
            int saved = canvas.save();
            canvas.clipPath(shape);
            shown.setBounds(Math.round(cx - size / 2f), Math.round(cy - size / 2f),
                    Math.round(cx + size / 2f), Math.round(cy + size / 2f));
            shown.draw(canvas);
            canvas.restoreToCount(saved);
            shown.setBounds(oldBounds);
            lastDrawable = d;
            lastResult = NEUTRAL_RESULT;
            return;
        }
        if (d != lastDrawable) { lastDrawable = d; lastResult = resolve(shown); }
        Result r = lastResult;

        float box;
        if (r.kind == FILLED) {
            paint.setShader(r.gradDir == 0 ? null : gradient(r, cx, cy, size));
            paint.setColor(r.color);
            canvas.drawPath(shape, paint);
            paint.setShader(null);
            box = FILLED_BOX;
        } else if (r.kind == EMPTY) {
            paint.setColor(MaterialColors.getColor(this,
                    com.google.android.material.R.attr.colorPrimaryContainer, 0xFF5A5A5A));
            canvas.drawPath(shape, paint);
            box = EMPTY_BOX;
        } else {
            paint.setColor(MaterialColors.getColor(this,
                    com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xFF444444));
            canvas.drawPath(shape, paint);
            box = NEUTRAL_BOX;
            if (r.kind == PENDING) return;
        }
        if (d == null) return;

        RectF cb = r.bounds;
        float cw = Math.max(cb.width(), 0.3f), ch = Math.max(cb.height(), 0.3f);
        float full = size * box / Math.max(cw, ch);
        float left = cx - (cb.left + cb.width() / 2f) * full;
        float top = cy - (cb.top + cb.height() / 2f) * full;
        Rect old = d.copyBounds();
        int save = canvas.save();
        if (r.kind == FILLED) {
            canvas.clipPath(shape);

            float halfW = cb.width() * full / 2f, halfH = cb.height() * full / 2f;
            canvas.clipRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH);
        }
        d.setBounds(Math.round(left), Math.round(top), Math.round(left + full), Math.round(top + full));
        d.draw(canvas);
        d.setBounds(old);
        canvas.restoreToCount(save);
    }

    private LinearGradient gradShader;
    private float gradCx, gradCy, gradSize;
    private Result gradResult;

    private Shader gradient(Result r, float cx, float cy, float size) {
        if (gradShader == null || gradResult != r || gradCx != cx || gradCy != cy || gradSize != size) {
            float l = cx - size / 2f, t = cy - size / 2f, rt = cx + size / 2f, b = cy + size / 2f;
            float x0, y0, x1, y1;
            switch (r.gradDir) {
                case 1: x0 = cx; y0 = t; x1 = cx; y1 = b; break;
                case 2: x0 = l; y0 = cy; x1 = rt; y1 = cy; break;
                case 3: x0 = l; y0 = t; x1 = rt; y1 = b; break;
                default: x0 = rt; y0 = t; x1 = l; y1 = b; break;
            }
            gradShader = new LinearGradient(x0, y0, x1, y1, r.color, r.color2, Shader.TileMode.CLAMP);
            gradResult = r; gradCx = cx; gradCy = cy; gradSize = size;
        }
        return gradShader;
    }

    private static Drawable unwrap(Drawable d) {
        if (d instanceof android.graphics.drawable.TransitionDrawable) {
            android.graphics.drawable.TransitionDrawable t = (android.graphics.drawable.TransitionDrawable) d;
            int n = t.getNumberOfLayers();
            if (n > 0) return unwrap(t.getDrawable(n - 1));
        }
        return d;
    }

    private Result resolve(final Drawable d) {
        if (d == null || d instanceof ShimmerDrawable) return NEUTRAL_RESULT;
        String cls = d.getClass().getSimpleName();
        if (cls.contains("VectorDrawable") || cls.contains("StateListDrawable")) return NEUTRAL_RESULT;
        Result cached = lookup(d);
        if (cached != null) return cached;
        final Source source = Source.of(d);
        if (source == null) {
            Result r = safeAnalyze(null, d);
            store(d, r);
            return r;
        }
        ANALYZER.execute(() -> {
            Result r = lookup(d);
            if (r == null) {
                r = safeAnalyze(source.bitmap, source.copy);
                store(d, r);
            }
            final Result done = r;
            MAIN.post(() -> {
                if (unwrap(getDrawable()) == d) { lastDrawable = getDrawable(); lastResult = done; invalidate(); }
            });
        });
        return PENDING_RESULT;
    }

    public static void prewarm(Drawable icon) {
        Drawable d = unwrap(icon);
        if (d == null || d instanceof ShimmerDrawable || lookup(d) != null) return;
        String cls = d.getClass().getSimpleName();
        if (cls.contains("VectorDrawable") || cls.contains("StateListDrawable")) return;
        Source source = Source.of(d);
        if (source == null) return;
        store(d, safeAnalyze(source.bitmap, source.copy));
    }

    private static final class Source {
        final Bitmap bitmap;
        final Drawable copy;
        Source(Bitmap b, Drawable c) { bitmap = b; copy = c; }
        static Source of(Drawable d) {
            if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
                return new Source(((BitmapDrawable) d).getBitmap(), null);
            }
            Drawable.ConstantState cs = d.getConstantState();
            if (cs == null) return null;
            try { return new Source(null, cs.newDrawable().mutate()); } catch (Throwable t) { return null; }
        }
    }

    private static Result lookup(Drawable d) {
        Result r = CACHE.get(d);
        if (r != null) return r;
        if (d instanceof BitmapDrawable) {
            Bitmap b = ((BitmapDrawable) d).getBitmap();
            if (b != null) {
                Result byBitmap = BITMAP_CACHE.get(b);

                if (byBitmap != null && byBitmap.bitmapGeneration == b.getGenerationId()) return byBitmap;
            }
        }
        return null;
    }

    private static void store(Drawable d, Result r) {
        CACHE.put(d, r);
        if (d instanceof BitmapDrawable) {
            Bitmap b = ((BitmapDrawable) d).getBitmap();
            if (b != null) { r.bitmapGeneration = b.getGenerationId(); BITMAP_CACHE.put(b, r); }
        }
    }

    private static Result safeAnalyze(Bitmap bitmap, Drawable drawable) {
        try {
            return analyze(bitmap, drawable);
        } catch (Throwable t) {
            return NEUTRAL_RESULT;
        }
    }

    private static Result analyze(Bitmap srcBitmap, Drawable d) {
        int iw, ih;
        if (srcBitmap != null) { iw = srcBitmap.getWidth(); ih = srcBitmap.getHeight(); }
        else { iw = d.getIntrinsicWidth(); ih = d.getIntrinsicHeight(); }
        boolean square = iw <= 0 || ih <= 0 || Math.abs(iw - ih) <= 0.15f * Math.max(iw, ih);
        int[] px = PIXELS;
        synchronized (LOCK) {
            if (sampleBitmap == null) {
                sampleBitmap = Bitmap.createBitmap(SAMPLE, SAMPLE, Bitmap.Config.ARGB_8888);
                sampleCanvas = new Canvas(sampleBitmap);
            }
            sampleBitmap.eraseColor(0);
            if (srcBitmap != null) {
                Bitmap src = srcBitmap;
                if (src.getConfig() == Bitmap.Config.HARDWARE) {
                    src = src.copy(Bitmap.Config.ARGB_8888, false);
                    if (src == null) return NEUTRAL_RESULT;
                }
                sampleCanvas.drawBitmap(src, null, new Rect(0, 0, SAMPLE, SAMPLE), new Paint(Paint.FILTER_BITMAP_FLAG));
                if (src != srcBitmap) src.recycle();
            } else {
                d.setBounds(0, 0, SAMPLE, SAMPLE);
                d.draw(sampleCanvas);
            }
            sampleBitmap.getPixels(px, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE);
            return analyzePixels(px, square);
        }
    }

    private static Result analyzePixels(int[] px, boolean square) {

        int minX = SAMPLE, minY = SAMPLE, maxX = -1, maxY = -1;
        int cMinX = SAMPLE, cMinY = SAMPLE, cMaxX = -1, cMaxY = -1, coreCount = 0;
        for (int y = 0; y < SAMPLE; y++) {
            for (int x = 0; x < SAMPLE; x++) {
                int a = px[y * SAMPLE + x] >>> 24;
                if (a >= 24) {
                    if (x < minX) minX = x; if (x > maxX) maxX = x;
                    if (y < minY) minY = y; if (y > maxY) maxY = y;
                }
                if (a >= 240) {
                    coreCount++;
                    if (x < cMinX) cMinX = x; if (x > cMaxX) cMaxX = x;
                    if (y < cMinY) cMinY = y; if (y > cMaxY) cMaxY = y;
                }
            }
        }
        if (maxX < 0) return NEUTRAL_RESULT;
        RectF visible = square
                ? new RectF(minX / (float) SAMPLE, minY / (float) SAMPLE, (maxX + 1) / (float) SAMPLE, (maxY + 1) / (float) SAMPLE)
                : FULL;
        if (coreCount == 0) return new Result(EMPTY, 0, visible);
        RectF core = square
                ? new RectF(cMinX / (float) SAMPLE, cMinY / (float) SAMPLE, (cMaxX + 1) / (float) SAMPLE, (cMaxY + 1) / (float) SAMPLE)
                : FULL;
        float coverage = coreCount / (float) ((cMaxX - cMinX + 1) * (cMaxY - cMinY + 1));

        if (coverage < FILLED_COVERAGE) return new Result(EMPTY, 0, visible);

        int tw = cMaxX - cMinX + 1, th = cMaxY - cMinY + 1;
        int ix = Math.max(1, Math.round(tw * FILLED_TRIM)), iy = Math.max(1, Math.round(th * FILLED_TRIM));
        int tx0 = cMinX + ix, ty0 = cMinY + iy, tx1 = cMaxX - ix, ty1 = cMaxY - iy;
        if (tx1 - tx0 < 8 || ty1 - ty0 < 8) { tx0 = cMinX; ty0 = cMinY; tx1 = cMaxX; ty1 = cMaxY; }
        RectF trimmed = square
                ? new RectF(tx0 / (float) SAMPLE, ty0 / (float) SAMPLE, (tx1 + 1) / (float) SAMPLE, (ty1 + 1) / (float) SAMPLE)
                : FULL;

        int patch = Math.max(2, (tx1 - tx0) / 9);
        int mx = (tx0 + tx1) / 2 - patch / 2, my = (ty0 + ty1) / 2 - patch / 2;
        int[] cT = patchColor(px, mx, ty0, patch), cB = patchColor(px, mx, ty1 - patch + 1, patch);
        int[] cL = patchColor(px, tx0, my, patch), cR = patchColor(px, tx1 - patch + 1, my, patch);
        int[] cTL = patchColor(px, tx0, ty0, patch), cBR = patchColor(px, tx1 - patch + 1, ty1 - patch + 1, patch);
        int[] cTR = patchColor(px, tx1 - patch + 1, ty0, patch), cBL = patchColor(px, tx0, ty1 - patch + 1, patch);
        int[][][] pairs = {{cT, cB}, {cL, cR}, {cTL, cBR}, {cTR, cBL}};
        int bestPair = -1; float bestDist = GRADIENT_DISTANCE;
        for (int i = 0; i < pairs.length; i++) {
            if (pairs[i][0] == null || pairs[i][1] == null) continue;
            float dist = colorDistance(pairs[i][0], pairs[i][1]);
            if (dist > bestDist) { bestDist = dist; bestPair = i; }
        }
        if (bestPair >= 0) {
            int ca = 0xFF000000 | (pairs[bestPair][0][0] << 16) | (pairs[bestPair][0][1] << 8) | pairs[bestPair][0][2];
            int cb2 = 0xFF000000 | (pairs[bestPair][1][0] << 16) | (pairs[bestPair][1][1] << 8) | pairs[bestPair][1][2];
            return new Result(FILLED, ca, trimmed, cb2, bestPair + 1);
        }

        java.util.HashMap<Integer, long[]> buckets = new java.util.HashMap<>();
        int band = Math.max(2, (tx1 - tx0) / 10);
        int bestKey = -1; long bestCount = 0;
        for (int y = ty0; y <= ty1; y++) {
            for (int x = tx0; x <= tx1; x++) {
                if (x - tx0 >= band && tx1 - x >= band && y - ty0 >= band && ty1 - y >= band) continue;
                int p = px[y * SAMPLE + x];
                if ((p >>> 24) < 250) continue;
                int rr = (p >> 16) & 0xFF, gg = (p >> 8) & 0xFF, bb = p & 0xFF;
                int key = ((rr >> 4) << 8) | ((gg >> 4) << 4) | (bb >> 4);
                long[] acc = buckets.get(key);
                if (acc == null) { acc = new long[4]; buckets.put(key, acc); }
                acc[0]++; acc[1] += rr; acc[2] += gg; acc[3] += bb;
                if (acc[0] > bestCount) { bestCount = acc[0]; bestKey = key; }
            }
        }
        if (bestKey < 0) return new Result(EMPTY, 0, visible);
        long[] best = buckets.get(bestKey);
        int color = 0xFF000000 | ((int) (best[1] / best[0]) << 16) | ((int) (best[2] / best[0]) << 8) | (int) (best[3] / best[0]);
        return new Result(FILLED, color, trimmed);
    }

    private static int[] patchColor(int[] px, int x0, int y0, int size) {
        long r = 0, g = 0, b = 0; int n = 0;
        for (int y = Math.max(0, y0); y < Math.min(SAMPLE, y0 + size); y++) {
            for (int x = Math.max(0, x0); x < Math.min(SAMPLE, x0 + size); x++) {
                int p = px[y * SAMPLE + x];
                if ((p >>> 24) < 250) continue;
                r += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF; n++;
            }
        }
        if (n < Math.max(1, size * size * 2 / 3)) return null;
        return new int[]{(int) (r / n), (int) (g / n), (int) (b / n)};
    }

    private static float colorDistance(int[] a, int[] b) {
        float dr = a[0] - b[0], dg = a[1] - b[1], db = a[2] - b[2];
        return (float) Math.sqrt(dr * dr + dg * dg + db * db);
    }
}
