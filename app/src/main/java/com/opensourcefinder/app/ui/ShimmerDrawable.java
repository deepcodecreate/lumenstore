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

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

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
    private ValueAnimator animator;
    private float sweep;

    public ShimmerDrawable(@NonNull Context context, boolean circle) {
        this(context, circle, true);
    }

    public ShimmerDrawable(@NonNull Context context, boolean circle, boolean animate) {
        this.circle = circle;
        this.cornerRadius = 8f * context.getResources().getDisplayMetrics().density;
        this.animate = animate && !AppPreferences.isReduceMotion(context);
        this.duration = AppPreferences.MOTION_EXPRESSIVE.equals(AppPreferences.getMotionStyle(context)) ? 1350 : 1100;
        int base = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHighest, 0x33FFFFFF);
        int highlight = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF);
        highlight = (highlight & 0x00FFFFFF) | 0x30000000;
        basePaint.setColor(base);
        highlightPaint.setColor(highlight);
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
        if (!animate || animator != null) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            if (getCallback() == null) { stop(); return; }
            sweep = (float) a.getAnimatedValue();
            invalidateSelf();
        });
        animator.start();
    }

    private void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override public void setAlpha(int alpha) { basePaint.setAlpha(alpha); highlightPaint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
