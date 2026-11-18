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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

public class ShapeView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float[] from = IconShapes.radii(IconShapes.DEFAULT);
    private float[] to = from;
    private float progress = 1f;
    private String shapeId = IconShapes.DEFAULT;
    private ValueAnimator morphAnimator;
    private ObjectAnimator spinAnimator;
    private boolean idleSpin;
    private Drawable icon;
    private float iconFraction = 1f;

    public ShapeView(Context context) { this(context, null); }
    public ShapeView(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public ShapeView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF7A727A);
    }

    public String getShapeId() { return shapeId; }

    public void setFillColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    public void setIcon(Drawable drawable) {
        icon = drawable;
        invalidate();
    }

    public void setIconFraction(float fraction) {
        iconFraction = fraction;
        invalidate();
    }

    public void setIdleSpin(boolean enabled) {
        idleSpin = enabled;
        updateSpin();
    }

    public void setShape(String id, boolean animate) {
        if (!IconShapes.isValid(id)) id = IconShapes.DEFAULT;
        if (id.equals(shapeId) && morphAnimator == null) return;
        float[] current = currentRadii();
        float[] target = IconShapes.radii(id);
        shapeId = id;
        if (morphAnimator != null) { morphAnimator.cancel(); morphAnimator = null; }
        long duration = MotionHelper.getDuration(getContext(), 320, 560);
        if (!animate || duration == 0L || !isLaidOut()) {
            from = target; to = target; progress = 1f;
            invalidate();
            return;
        }
        from = current; to = target; progress = 0f;
        morphAnimator = ValueAnimator.ofFloat(0f, 1f);
        morphAnimator.setDuration(duration);
        morphAnimator.setInterpolator(AppPreferences.MOTION_EXPRESSIVE.equals(AppPreferences.getMotionStyle(getContext()))
                ? new OvershootInterpolator(1.7f) : new DecelerateInterpolator(1.6f));
        morphAnimator.addUpdateListener(a -> {
            progress = (Float) a.getAnimatedValue();
            invalidate();
        });
        morphAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (cancelled) return;
                from = to; progress = 1f; morphAnimator = null;
                invalidate();
            }
        });
        morphAnimator.start();
    }

    private float[] currentRadii() {
        return progress >= 1f ? to : IconShapes.lerp(from, to, progress);
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        int h = getHeight() - getPaddingTop() - getPaddingBottom();
        float size = Math.min(w, h);
        if (size <= 0) return;
        float ox = getPaddingLeft() + (w - size) / 2f, oy = getPaddingTop() + (h - size) / 2f;
        IconShapes.build(path, from, to, progress, size, ox, oy);

        boolean rotating = idleSpin || spinAnimator != null || (getRotation() % 360f) != 0f;
        float fit = rotating ? Math.min(1f, 50f / Math.max(50f, IconShapes.maxRadius(from, to, progress))) : 1f;
        int outer = canvas.save();
        if (fit < 1f) canvas.scale(fit, fit, ox + size / 2f, oy + size / 2f);
        canvas.drawPath(path, paint);
        if (icon != null) {
            int save = canvas.save();
            canvas.clipPath(path);
            float inset = size * (1f - iconFraction) / 2f;
            icon.setBounds(Math.round(ox + inset), Math.round(oy + inset),
                    Math.round(ox + size - inset), Math.round(oy + size - inset));
            icon.draw(canvas);
            canvas.restoreToCount(save);
        }
        canvas.restoreToCount(outer);
    }

    private void updateSpin() {
        if (spinAnimator != null) { spinAnimator.cancel(); spinAnimator = null; }
        if (!idleSpin || !isAttachedToWindow() || AppPreferences.isReduceMotion(getContext())) return;
        spinAnimator = ObjectAnimator.ofFloat(this, View.ROTATION, getRotation(), getRotation() + 360f);
        spinAnimator.setDuration(MotionHelper.getDuration(getContext(), 36000, 26000));
        spinAnimator.setInterpolator(new LinearInterpolator());
        spinAnimator.setRepeatCount(ValueAnimator.INFINITE);
        spinAnimator.start();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateSpin();
    }

    @Override protected void onDetachedFromWindow() {
        if (spinAnimator != null) { spinAnimator.cancel(); spinAnimator = null; }
        if (morphAnimator != null) { morphAnimator.cancel(); morphAnimator = null; from = to; progress = 1f; }
        super.onDetachedFromWindow();
    }
}
