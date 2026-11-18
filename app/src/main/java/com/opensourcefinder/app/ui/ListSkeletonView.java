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
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

public class ListSkeletonView extends View {
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final Matrix shapeMatrix = new Matrix();
    private final Path iconPath = new Path();
    private final RectF rect = new RectF();
    private final float density;
    private final boolean animated;
    private LinearGradient sweepShader;
    private ValueAnimator animator;
    private float sweep;

    public ListSkeletonView(Context context) { this(context, null); }

    public ListSkeletonView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        animated = !AppPreferences.isReduceMotion(context);
        cardPaint.setColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorSurfaceContainer, 0xFF2A2A2A));
        blockPaint.setColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xFF3A3A3A));
        int onSurface = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF);
        sweepPaint.setColor((onSurface & 0x00FFFFFF) | 0x26000000);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0) return;
        int clear = sweepPaint.getColor() & 0x00FFFFFF;
        int mid = sweepPaint.getColor();
        sweepShader = new LinearGradient(0, 0, w * 0.7f, 0, new int[]{clear, mid, clear},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        float w = getWidth(), h = getHeight();
        if (w <= 0f || h <= 0f) return;
        float margin = 12f * density, gap = 5f * density, rowH = 64f * density;
        float iconSize = 46f * density, radius = 20f * density;
        String shape = AppPreferences.getIconShape(getContext());
        int rows = (int) Math.ceil((h - 2f * density) / (rowH + gap));
        for (int pass = 0; pass < 2; pass++) {
            Paint p = pass == 0 ? blockPaint : sweepPaint;
            if (pass == 1) {
                if (sweepShader == null) break;
                matrix.setTranslate(-w * 0.7f + sweep * (w * 1.7f), 0f);
                sweepShader.setLocalMatrix(matrix);
                sweepPaint.setShader(sweepShader);
            }
            for (int i = 0; i < rows; i++) {
                float top = 2f * density + i * (rowH + gap);
                float left = margin, right = w - margin;
                if (pass == 0) {
                    rect.set(left, top, right, top + rowH);
                    canvas.drawRoundRect(rect, radius, radius, cardPaint);
                }
                float ix = left + 10f * density, iy = top + (rowH - iconSize) / 2f;
                IconShapes.fit(iconPath, shape, iconSize, ix, iy, shapeMatrix);
                canvas.drawPath(iconPath, p);
                float tx = ix + iconSize + 12f * density;
                float btnW = 72f * density;
                float textRight = right - 10f * density - btnW - 12f * density;
                rect.set(tx, top + 17f * density, tx + Math.max(40f * density, (textRight - tx) * (0.55f + 0.08f * (i % 4))), top + 27f * density);
                canvas.drawRoundRect(rect, 5f * density, 5f * density, p);
                rect.set(tx, top + 35f * density, tx + Math.max(30f * density, (textRight - tx) * (0.35f + 0.06f * ((i + 2) % 4))), top + 43f * density);
                canvas.drawRoundRect(rect, 4f * density, 4f * density, p);
                rect.set(right - 10f * density - btnW, top + 16f * density, right - 10f * density, top + rowH - 16f * density);
                canvas.drawRoundRect(rect, 16f * density, 16f * density, p);
            }
        }
        sweepPaint.setShader(null);
    }

    private void startSweep() {
        if (!animated || animator != null || getVisibility() != VISIBLE || !isAttachedToWindow()) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1300L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> { sweep = (float) a.getAnimatedValue(); invalidate(); });
        animator.start();
    }

    private void stopSweep() {
        if (animator != null) { animator.cancel(); animator = null; }
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); startSweep(); }
    @Override protected void onDetachedFromWindow() { stopSweep(); super.onDetachedFromWindow(); }
    @Override protected void onVisibilityChanged(@NonNull View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        if (visibility == VISIBLE) startSweep(); else stopSweep();
    }
}
