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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.SystemClock;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import com.google.android.material.color.MaterialColors;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class IconBackdropDrawable extends Drawable {
    private static final int NEUTRAL = 0, FILLED = 1, EMPTY = 2;
    private static final int SAMPLE = 24;
    private static final float FILLED_COVERAGE = 0.70f;
    private static final float GLYPH_INSET = 0.20f;
    private static final long SPIN_PERIOD_MS = 18000L;

    private static final class Result {
        final int kind, color;
        Result(int kind, int color) { this.kind = kind; this.color = color; }
    }
    private static final Result NEUTRAL_RESULT = new Result(NEUTRAL, 0);
    private static final Map<Drawable, Result> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private final ImageView host;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path scratch = new Path();
    private final int themeColor;
    private final int basePadL, basePadT, basePadR, basePadB;
    private float[] radii;
    private float shapeScale = 1f;
    private final boolean spinEnabled;

    private Drawable lastDrawable;
    private Result lastResult = NEUTRAL_RESULT;
    private int appliedKind = -1;
    private int appliedSize = -1;

    IconBackdropDrawable(@NonNull ImageView host, String shapeId) {
        this.host = host;
        setShape(shapeId);
        themeColor = MaterialColors.getColor(host,
                com.google.android.material.R.attr.colorPrimaryContainer, 0xFF5A5A5A);
        basePadL = host.getPaddingLeft(); basePadT = host.getPaddingTop();
        basePadR = host.getPaddingRight(); basePadB = host.getPaddingBottom();
        spinEnabled = !AppPreferences.isReduceMotion(host.getContext());
    }

    void setShape(String shapeId) {
        radii = IconShapes.radii(shapeId);
        float max = 0f;
        for (float r : radii) if (r > max) max = r;

        shapeScale = max > 50f ? 50f / max : 1f;
        invalidateSelf();
    }

    @Override public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        float size = Math.min(b.width(), b.height());
        if (size <= 0f) return;
        Drawable current = host.getDrawable();
        if (current != lastDrawable) {
            lastDrawable = current;
            lastResult = classify(current);
        }
        syncHost(lastResult.kind, Math.round(size));
        float ox = b.left + (b.width() - size) / 2f, oy = b.top + (b.height() - size) / 2f;
        if (lastResult.kind == FILLED) {
            IconShapes.build(path, radii, radii, 0f, size, ox, oy);
            paint.setColor(lastResult.color);
            canvas.drawPath(path, paint);
        } else if (lastResult.kind == EMPTY) {
            IconShapes.build(scratch, radii, radii, 0f, size, 0f, 0f);
            paint.setColor(themeColor);
            int save = canvas.save();
            canvas.translate(ox + size / 2f, oy + size / 2f);
            if (spinEnabled) {
                canvas.rotate((SystemClock.uptimeMillis() % SPIN_PERIOD_MS) * 360f / SPIN_PERIOD_MS);
            }
            canvas.scale(shapeScale, shapeScale);
            canvas.translate(-size / 2f, -size / 2f);
            canvas.drawPath(scratch, paint);
            canvas.restoreToCount(save);
            if (spinEnabled) invalidateSelf();
        }
    }

    private void syncHost(int kind, int size) {
        if (kind == appliedKind && (kind != EMPTY || size == appliedSize)) return;
        appliedKind = kind;
        appliedSize = size;
        host.post(() -> {
            if (kind == EMPTY) {
                int inset = Math.round(size * GLYPH_INSET);
                host.setClipToOutline(false);
                host.setPadding(basePadL + inset, basePadT + inset, basePadR + inset, basePadB + inset);
            } else {
                host.setClipToOutline(true);
                host.setPadding(basePadL, basePadT, basePadR, basePadB);
            }
        });
    }

    private static Result classify(Drawable d) {
        if (d == null) return NEUTRAL_RESULT;
        String cls = d.getClass().getSimpleName();
        if (cls.contains("VectorDrawable") || cls.contains("StateListDrawable")) return NEUTRAL_RESULT;
        Result cached = CACHE.get(d);
        if (cached != null) return cached;
        Result r;
        try {
            r = analyze(d);
        } catch (Throwable t) {
            return NEUTRAL_RESULT;
        }
        if (!(d instanceof TransitionDrawable)) CACHE.put(d, r);
        return r;
    }

    private static Result analyze(Drawable d) {
        Bitmap bmp = Bitmap.createBitmap(SAMPLE, SAMPLE, Bitmap.Config.ARGB_8888);
        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
            Bitmap src = ((BitmapDrawable) d).getBitmap();
            if (src.getConfig() == Bitmap.Config.HARDWARE) {
                src = src.copy(Bitmap.Config.ARGB_8888, false);
                if (src == null) return NEUTRAL_RESULT;
            }
            Canvas c = new Canvas(bmp);
            c.drawBitmap(src, null, new Rect(0, 0, SAMPLE, SAMPLE), new Paint(Paint.FILTER_BITMAP_FLAG));
        } else {
            Rect old = d.copyBounds();
            d.setBounds(0, 0, SAMPLE, SAMPLE);
            d.draw(new Canvas(bmp));
            d.setBounds(old);
        }
        int[] px = new int[SAMPLE * SAMPLE];
        bmp.getPixels(px, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE);
        bmp.recycle();

        int opaque = 0, edgeCount = 0, allCount = 0;
        long er = 0, eg = 0, eb = 0, ar = 0, ag = 0, ab = 0;
        for (int y = 0; y < SAMPLE; y++) {
            for (int x = 0; x < SAMPLE; x++) {
                int p = px[y * SAMPLE + x];
                if ((p >>> 24) < 200) continue;
                opaque++;
                int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, bl = p & 0xFF;
                ar += r; ag += g; ab += bl; allCount++;
                if (x < 3 || y < 3 || x >= SAMPLE - 3 || y >= SAMPLE - 3) {
                    er += r; eg += g; eb += bl; edgeCount++;
                }
            }
        }
        float coverage = opaque / (float) (SAMPLE * SAMPLE);
        if (coverage < FILLED_COVERAGE || allCount == 0) return new Result(EMPTY, 0);
        int color = edgeCount > 0
                ? 0xFF000000 | ((int) (er / edgeCount) << 16) | ((int) (eg / edgeCount) << 8) | (int) (eb / edgeCount)
                : 0xFF000000 | ((int) (ar / allCount) << 16) | ((int) (ag / allCount) << 8) | (int) (ab / allCount);
        return new Result(FILLED, color);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
