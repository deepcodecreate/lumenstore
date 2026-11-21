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
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.Choreographer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public final class ShimmerDrawable extends Drawable {
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();
    private final boolean circle;
    private final float cornerRadius;
    private final boolean animate;
    private final long duration;
    private LinearGradient gradient;
    private boolean running;
    private float sweep;

    private static final class Palette {
        final WeakReference<Resources.Theme> theme;
        final int uiMode;
        final int base;
        final int highlight;

        Palette(Resources.Theme theme, int uiMode, int base, int highlight) {
            this.theme = new WeakReference<>(theme);
            this.uiMode = uiMode;
            this.base = base;
            this.highlight = highlight;
        }
    }

    private static volatile Palette palette;
    private static final ArrayList<ShimmerDrawable> RUNNING = new ArrayList<>();
    private static boolean clockPosted;

    private static final Choreographer.FrameCallback CLOCK = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            clockPosted = false;
            long millis = frameTimeNanos / 1_000_000L;
            for (int i = RUNNING.size() - 1; i >= 0; i--) {
                ShimmerDrawable drawable = RUNNING.get(i);
                if (drawable.getCallback() == null) {
                    RUNNING.remove(i);
                    drawable.running = false;
                    continue;
                }
                drawable.sweep = (millis % drawable.duration) / (float) drawable.duration;
                drawable.invalidateSelf();
            }
            if (!RUNNING.isEmpty()) {
                clockPosted = true;
                Choreographer.getInstance().postFrameCallback(this);
            }
        }
    };

    private static Palette paletteFor(Context context) {
        Palette cached = palette;
        Resources.Theme theme = context.getTheme();
        int uiMode = context.getResources().getConfiguration().uiMode;
        if (cached != null && cached.uiMode == uiMode && cached.theme.get() == theme) return cached;
        int base = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHighest, 0x33FFFFFF);
        int highlight = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF);
        highlight = (highlight & 0x00FFFFFF) | 0x30000000;
        Palette fresh = new Palette(theme, uiMode, base, highlight);
        palette = fresh;
        return fresh;
    }

    public ShimmerDrawable(@NonNull Context context, boolean circle) {
        this(context, circle, true);
    }

    public ShimmerDrawable(@NonNull Context context, boolean circle, boolean animate) {
        this.circle = circle;
        this.cornerRadius = 8f * context.getResources().getDisplayMetrics().density;
        this.animate = animate && !AppPreferences.isReduceMotion(context);
        this.duration = AppPreferences.MOTION_EXPRESSIVE.equals(AppPreferences.getMotionStyle(context)) ? 1350 : 1100;
        Palette colors = paletteFor(context);
        basePaint.setColor(colors.base);
        highlightPaint.setColor(colors.highlight);
    }

    @Override
    protected void onBoundsChange(@NonNull android.graphics.Rect bounds) {
        rect.set(bounds);
        if (bounds.width() > 0) {
            int highlight = highlightPaint.getColor();
            int transparent = highlight & 0x00FFFFFF;
            gradient = new LinearGradient(0, 0, bounds.width() * 1.6f, 0,
                    new int[]{transparent, highlight, transparent}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            highlightPaint.setShader(gradient);
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (rect.isEmpty()) return;
        float radius = circle ? Math.min(rect.width(), rect.height()) / 2f : cornerRadius;
        canvas.drawRoundRect(rect, radius, radius, basePaint);
        if (gradient != null) {
            matrix.setTranslate(rect.left - rect.width() + sweep * (rect.width() * 2.6f), rect.top);
            gradient.setLocalMatrix(matrix);
            canvas.drawRoundRect(rect, radius, radius, highlightPaint);
        }
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (visible) start(); else stop();
        return changed;
    }

    private void start() {
        if (!animate || running) return;
        running = true;
        RUNNING.add(this);
        if (!clockPosted) {
            clockPosted = true;
            Choreographer.getInstance().postFrameCallback(CLOCK);
        }
    }

    private void stop() {
        if (!running) return;
        running = false;
        RUNNING.remove(this);
    }

    @Override public void setAlpha(int alpha) { basePaint.setAlpha(alpha); highlightPaint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
