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
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.google.android.material.color.MaterialColors;
import com.opensourcefinder.app.R;

public class ShimmerView extends View {

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix gradientMatrix = new Matrix();

    private float cornerRadius;
    private boolean circleShape;
    private LinearGradient gradient;
    private ValueAnimator animator;
    private float sweep;

    public ShimmerView(Context context) {
        this(context, null);
    }

    public ShimmerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        int base = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHighest,
                0x33FFFFFF);
        int highlight = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF);
        highlight = (highlight & 0x00FFFFFF) | 0x30000000;

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ShimmerView);
            cornerRadius = a.getDimension(R.styleable.ShimmerView_shimmerCornerRadius, dp(8));
            circleShape = a.getBoolean(R.styleable.ShimmerView_shimmerCircle, false);
            base = a.getColor(R.styleable.ShimmerView_shimmerBaseColor, base);
            highlight = a.getColor(R.styleable.ShimmerView_shimmerHighlightColor, highlight);
            a.recycle();
        } else {
            cornerRadius = dp(8);
        }

        basePaint.setColor(base);
        highlightPaint.setColor(highlight);
    }

    public void setCornerRadius(float px) {
        cornerRadius = px;
        invalidate();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rect.set(0, 0, w, h);
        if (w > 0) {
            int transparent = highlightPaint.getColor() & 0x00FFFFFF;
            int highlight = highlightPaint.getColor();
            gradient = new LinearGradient(0, 0, w * 1.6f, 0,
                    new int[]{transparent, highlight, transparent},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            highlightPaint.setShader(gradient);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float radius = circleShape ? Math.min(rect.width(), rect.height()) / 2f : cornerRadius;
        canvas.drawRoundRect(rect, radius, radius, basePaint);
        if (gradient != null) {
            gradientMatrix.setTranslate(-rect.width() + sweep * (rect.width() * 2.6f), 0);
            gradient.setLocalMatrix(gradientMatrix);
            canvas.drawRoundRect(rect, radius, radius, highlightPaint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startShimmer();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopShimmer();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) startShimmer();
        else stopShimmer();
    }

    private void startShimmer() {
        if (animator != null || AppPreferences.isReduceMotion(getContext())) {
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(AppPreferences.MOTION_EXPRESSIVE.equals(AppPreferences.getMotionStyle(getContext()))
                ? 1350 : 1100);
        animator.setStartDelay(0);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            sweep = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stopShimmer() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }
}
