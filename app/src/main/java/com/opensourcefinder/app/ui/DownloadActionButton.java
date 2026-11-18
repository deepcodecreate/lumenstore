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

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.format.Formatter;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.opensourcefinder.app.R;

public class DownloadActionButton extends MaterialButton {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF measure = new RectF();
    private final Matrix shineMatrix = new Matrix();
    private final ArgbEvaluator argb = new ArgbEvaluator();

    private boolean progressMode;
    private boolean indeterminate;
    private boolean cancelable = true;
    private Runnable cancelAction;

    private float shownFraction;
    private float targetFraction;

    private float modeAmount;
    private float sweep;

    private ValueAnimator fractionAnimator;
    private ValueAnimator modeAnimator;
    private ValueAnimator sweepAnimator;

    private boolean idleCaptured;
    private ColorStateList idleBackground;
    private ColorStateList idleStroke;
    private ColorStateList idleText;
    private int idleStrokeWidth;
    private int idleBackgroundColor;
    private int idleStrokeColor;
    private int idleTextColor;

    private int progressBackground;
    private int progressStroke;
    private int progressText;
    private int progressFill;
    private int progressShine;
    private int progressStrokeWidth;

    private LinearGradient shineGradient;
    private float shineWidth;

    public DownloadActionButton(@NonNull Context context) { this(context, null); }

    public DownloadActionButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.materialButtonStyle);
    }

    public DownloadActionButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
    }

    public void setOnCancelListener(@Nullable Runnable action) { cancelAction = action; }

    public boolean isProgressMode() { return progressMode; }

    public void showProgress(int percent, CharSequence label) {
        enterProgressMode();
        indeterminate = false;
        cancelable = true;
        if (!isEnabled()) setEnabled(true);
        setLabel(label);
        animateFractionTo(Math.max(0, Math.min(100, percent)) / 100f);
        updateSweep();
        invalidate();
    }

    public void showIndeterminate(CharSequence label) {
        enterProgressMode();
        indeterminate = true;
        cancelable = false;
        setLabel(label);
        updateSweep();
        invalidate();
    }

    public void hideProgress() {
        if (!progressMode) return;
        progressMode = false;
        indeterminate = false;
        stopSweep();
        animateModeTo(0f);
    }

    public static String progressLabel(Context context, int percent) {
        int p = Math.max(0, Math.min(100, percent));
        return context.getString(R.string.download_button_progress, context.getString(R.string.cancel), p);
    }

    public static String caption(Context context, int percent, long totalBytes) {
        int p = Math.max(0, Math.min(100, percent));
        if (totalBytes > 0) {
            long done = Math.round(totalBytes * (p / 100.0));
            return context.getString(R.string.download_caption, p,
                    Formatter.formatShortFileSize(context, done),
                    Formatter.formatShortFileSize(context, totalBytes));
        }
        return context.getString(R.string.download_caption_short, p);
    }

    private void setLabel(CharSequence label) {
        if (label != null && !android.text.TextUtils.equals(getText(), label)) setText(label);
    }

    private void enterProgressMode() {
        if (progressMode) return;
        progressMode = true;
        captureIdleStyle();
        resolveProgressColors();
        if (modeAmount <= 0f) {
            shownFraction = 0f;
            targetFraction = 0f;
        }
        animateModeTo(1f);
    }

    private void captureIdleStyle() {
        if (idleCaptured) return;
        idleCaptured = true;
        idleBackground = getBackgroundTintList();
        idleStroke = getStrokeColor();
        idleText = getTextColors();
        idleStrokeWidth = getStrokeWidth();
        int[] state = getDrawableState();
        idleBackgroundColor = idleBackground == null ? Color.TRANSPARENT
                : idleBackground.getColorForState(state, idleBackground.getDefaultColor());
        idleStrokeColor = idleStroke == null ? Color.TRANSPARENT
                : idleStroke.getColorForState(state, idleStroke.getDefaultColor());
        idleTextColor = idleText == null ? Color.WHITE
                : idleText.getColorForState(state, idleText.getDefaultColor());
    }

    private void resolveProgressColors() {
        int primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, Color.GRAY);
        int surface = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.DKGRAY);
        float density = getResources().getDisplayMetrics().density;
        progressBackground = surface;
        progressStroke = ColorUtils.setAlphaComponent(primary, 150);
        progressText = primary;
        progressFill = primary;
        progressShine = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(primary, Color.WHITE, 0.55f), 110);
        trackColor = idleTextColor == 0 ? Color.BLACK : idleTextColor;
        progressStrokeWidth = Math.max(1, Math.round(1.5f * density));
    }

    private void animateModeTo(float target) {
        if (modeAnimator != null) { modeAnimator.cancel(); modeAnimator = null; }
        long duration = MotionHelper.getDuration(getContext(), 320, 480);
        if (duration <= 0L || !isLaidOut()) {
            applyMode(target);
            if (target == 0f) finishExit();
            return;
        }
        modeAnimator = ValueAnimator.ofFloat(modeAmount, target);
        modeAnimator.setDuration(Math.round(duration * Math.abs(target - modeAmount) + 60));
        modeAnimator.setInterpolator(new DecelerateInterpolator(1.6f));
        modeAnimator.addUpdateListener(a -> applyMode((Float) a.getAnimatedValue()));
        final boolean exiting = target == 0f;
        modeAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (cancelled) return;
                modeAnimator = null;
                if (exiting && !progressMode) finishExit();
            }
        });
        modeAnimator.start();
    }

    private void applyMode(float amount) {

        modeAmount = amount;
        invalidate();
    }

    private void finishExit() {
        modeAmount = 0f;
        if (idleCaptured) {
            setBackgroundTintList(idleBackground);
            setStrokeColor(idleStroke);
            setStrokeWidth(idleStrokeWidth);
            setTextColor(idleText);
            idleCaptured = false;
        }
        invalidate();
    }

    private void animateFractionTo(float target) {
        targetFraction = target;
        if (fractionAnimator != null) { fractionAnimator.cancel(); fractionAnimator = null; }
        if (AppPreferences.isReduceMotion(getContext()) || !isLaidOut() || target < shownFraction) {
            shownFraction = target;
            invalidate();
            return;
        }
        fractionAnimator = ValueAnimator.ofFloat(shownFraction, target);
        fractionAnimator.setDuration(420);
        fractionAnimator.setInterpolator(new DecelerateInterpolator(1.4f));
        fractionAnimator.addUpdateListener(a -> {
            shownFraction = (Float) a.getAnimatedValue();
            invalidate();
        });
        fractionAnimator.start();
    }

    private void updateSweep() {
        if (!progressMode || AppPreferences.isReduceMotion(getContext()) || !isAttachedToWindow()) {
            stopSweep();
            return;
        }
        if (sweepAnimator != null) return;
        sweepAnimator = ValueAnimator.ofFloat(0f, 1f);
        sweepAnimator.setDuration(indeterminate ? 1250 : 1700);
        sweepAnimator.setRepeatCount(ValueAnimator.INFINITE);
        sweepAnimator.setInterpolator(new LinearInterpolator());
        sweepAnimator.addUpdateListener(a -> {
            sweep = (Float) a.getAnimatedValue();
            invalidate();
        });
        sweepAnimator.start();
    }

    private void stopSweep() {
        if (sweepAnimator != null) { sweepAnimator.cancel(); sweepAnimator = null; }
    }

    @Override public boolean performClick() {
        if (progressMode) {
            if (cancelable && cancelAction != null) {
                AppPreferences.performHaptic(this);
                cancelAction.run();
                return true;
            }
            return false;
        }
        return super.performClick();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateSweep();
    }

    @Override protected void onDetachedFromWindow() {
        stopSweep();
        if (fractionAnimator != null) { fractionAnimator.cancel(); fractionAnimator = null; shownFraction = targetFraction; }
        if (modeAnimator != null) {
            modeAnimator.cancel();
            modeAnimator = null;
            if (progressMode) applyMode(1f); else finishExit();
        }
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (modeAmount > 0f) drawProgress(canvas);
        super.onDraw(canvas);
    }

    private final android.graphics.Path clipPath = new android.graphics.Path();

    private void drawProgress(Canvas canvas) {
        float w = getWidth();
        float boxTop = getInsetTop();
        float boxBottom = getHeight() - getInsetBottom();
        float boxH = boxBottom - boxTop;
        if (w <= 0f || boxH <= 0f) return;

        measure.set(0, boxTop, w, boxBottom);
        float corner = boxH / 2f;
        try {
            corner = getShapeAppearanceModel().getTopLeftCornerSize().getCornerSize(measure);
        } catch (RuntimeException ignored) {  }
        float radius = Math.max(0f, Math.min(corner, boxH / 2f));

        rect.set(0f, boxTop, w, boxBottom);
        clipPath.rewind();
        clipPath.addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clipPath);

        if (indeterminate) {
            float seg = w * 0.38f;
            float x = -seg + sweep * (w + seg);
            rect.set(x, boxTop, x + seg, boxBottom);
            shinePaint.setShader(null);
            shinePaint.setColor(ColorUtils.setAlphaComponent(progressShine, Math.round(150 * modeAmount)));
            canvas.drawRect(rect, shinePaint);
            canvas.restoreToCount(save);
            return;
        }

        float fillWidth = w * shownFraction;

        fillPaint.setColor(ColorUtils.setAlphaComponent(trackColor, Math.round(90 * modeAmount)));
        if (fillWidth < w) {
            rect.set(fillWidth, boxTop, w, boxBottom);
            canvas.drawRect(rect, fillPaint);
        }

        if (fillWidth > 0.5f && !AppPreferences.isReduceMotion(getContext())) {
            float width = Math.max(boxH * 1.4f, 1f);
            if (shineGradient == null || shineWidth != width) {
                shineWidth = width;
                int clear = progressShine & 0x00FFFFFF;
                shineGradient = new LinearGradient(0, 0, width, 0,
                        new int[]{clear, progressShine, clear}, new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP);
            }
            shinePaint.setShader(shineGradient);
            float travel = fillWidth + width;
            shineMatrix.setTranslate(-width + sweep * travel, 0f);
            shineGradient.setLocalMatrix(shineMatrix);
            shinePaint.setAlpha(Math.round(255 * modeAmount));
            rect.set(0f, boxTop, fillWidth, boxBottom);
            canvas.drawRect(rect, shinePaint);
        }
        canvas.restoreToCount(save);
    }

    private int trackColor = Color.BLACK;
}
