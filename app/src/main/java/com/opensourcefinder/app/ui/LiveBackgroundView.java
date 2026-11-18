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
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.animation.ValueAnimator;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import com.google.android.material.color.MaterialColors;

public class LiveBackgroundView extends View {
    private static final long FRAME_MS = 50L;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private enum Accent { RISE, WAVE, TWINKLE }

    private static final float[][] GLOWS = {
            {0.08f, 0.06f, 0.14f, 0.05f, 23f, 31f, 0.0f, 0.95f, 0},
            {0.98f, 0.52f, 0.10f, 0.10f, 29f, 37f, 1.7f, 0.80f, 1},
            {0.18f, 1.02f, 0.16f, 0.04f, 33f, 27f, 3.1f, 0.70f, 3},
    };

    private static final float[][] SHAPES = {
            {0.90f, 0.08f, 150f, 22f, 16f, 17f, 5.2f, 26f, 0, 0.10f},
            {0.06f, 0.36f, 92f, 16f, 22f, 21f, 6.0f, -21f, 1, 0.11f},
            {0.86f, 0.70f, 124f, 20f, 18f, 19f, 6.8f, 31f, 2, 0.34f},
            {0.14f, 0.90f, 72f, 14f, 14f, 15f, 5.6f, -17f, 0, 0.09f},
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wavePath = new Path();
    private final RectF rect = new RectF();
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    private Bitmap glowBitmap;
    private final RectF glowDst = new RectF();

    private static final int GLOW_DOWNSCALE = 6;

    private static final float GLOW_MARGIN = 0.06f;

    private final int[] colors = new int[4];
    private int surface;
    private boolean light;
    private boolean pureBlack;
    private Accent accent = Accent.WAVE;
    private String styleId = LiveBackgroundStyles.CLASSIC;
    private LiveBackgroundStyles.Painter painter;
    private String styleOverride;
    private boolean forceEnabled;

    private boolean enabledByUser = true;
    private boolean reduceMotion;
    private boolean systemSavesPower;
    private float speed = 1f;
    private float density = 1f;
    private double time = 4.0;
    private long lastFrame;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (prefs, key) -> post(this::refreshConfig);

    public LiveBackgroundView(@NonNull Context context) { this(context, null); }

    public LiveBackgroundView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        resolveTheme();
        refreshConfig();
        INSTANCES.put(this, Boolean.TRUE);
    }

    private void resolveTheme() {
        Context ctx = getContext();
        light = ThemeManager.isLightTheme(ctx);
        pureBlack = AppPreferences.isPureBlack(ctx) && !light;
        surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface,
                light ? Color.WHITE : Color.BLACK);
        colors[0] = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, Color.GRAY);
        colors[1] = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiary, colors[0]);
        colors[2] = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, colors[0]);
        colors[3] = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer, colors[2]);
        accent = accentFor(ThemeManager.getThemeId(ctx));
        rebuildGlows();
    }

    private static Accent accentFor(String themeId) {
        if (themeId == null) return Accent.WAVE;
        switch (themeId) {
            case "ember":
            case "autumn":
            case "neon":
            case "rose":
            case "blossom":
            case "forest":
                return Accent.RISE;
            case "midnight":
            case "plum":
            case "stone":
                return Accent.TWINKLE;
            case "dusk":
            case "ocean":
            case "sky":
            case "mint":
            case "sand":
            case "default_teal":
            default:
                return Accent.WAVE;
        }
    }

    public void setPreviewStyle(@Nullable String id) {
        styleOverride = id == null ? null : LiveBackgroundStyles.normalize(id);
        forceEnabled = id != null;
        refreshConfig();
    }

    private void refreshConfig() {
        Context ctx = getContext();
        enabledByUser = forceEnabled || AppPreferences.isLiveBackground(ctx);
        String wanted = styleOverride != null ? styleOverride
                : LiveBackgroundStyles.normalize(AppPreferences.getLiveBackgroundStyle(ctx));
        if (!wanted.equals(styleId) || (painter == null && !LiveBackgroundStyles.CLASSIC.equals(wanted))) {
            styleId = wanted;
            painter = LiveBackgroundStyles.create(wanted);
        }
        reduceMotion = AppPreferences.isReduceMotion(ctx);
        speed = AppPreferences.MOTION_EXPRESSIVE.equals(AppPreferences.getMotionStyle(ctx)) ? 1f : 0.7f;

        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        systemSavesPower = (pm != null && pm.isPowerSaveMode()) || !ValueAnimator.areAnimatorsEnabled();
        if (!enabledByUser || painter != null) releaseGlow();
        else if (glowBitmap == null) rebuildGlows();
        lastFrame = 0L;
        invalidate();
        updateTicker();
    }

    private static volatile boolean scrollPaused;
    private static final java.util.WeakHashMap<LiveBackgroundView, Boolean> INSTANCES = new java.util.WeakHashMap<>();

    public static void setScrollPaused(boolean paused) {
        if (scrollPaused == paused) return;
        scrollPaused = paused;
        if (!paused) {
            for (LiveBackgroundView v : new java.util.ArrayList<>(INSTANCES.keySet())) {
                v.lastFrame = 0L;
                v.updateTicker();
            }
        }
    }

    private boolean tickerRunning;
    private long lastTickUptime;

    private final Choreographer.FrameCallback ticker = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            tickerRunning = false;
            if (!shouldAnimate()) return;
            long now = SystemClock.uptimeMillis();
            if (now - lastTickUptime >= FRAME_MS - 4L) {
                lastTickUptime = now;
                invalidate();
            }
            tickerRunning = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private void updateTicker() {
        if (shouldAnimate()) {
            if (!tickerRunning) {
                tickerRunning = true;
                Choreographer.getInstance().postFrameCallback(ticker);
            }
        } else if (tickerRunning) {
            Choreographer.getInstance().removeFrameCallback(ticker);
            tickerRunning = false;
        }
    }

    private void stopTicker() {
        Choreographer.getInstance().removeFrameCallback(ticker);
        tickerRunning = false;
    }

    private boolean shouldAnimate() {
        return !scrollPaused && enabledByUser && !reduceMotion && !systemSavesPower && isAttachedToWindow()
                && getWindowVisibility() == VISIBLE && getVisibility() == VISIBLE;
    }

    private void releaseGlow() {
        glowBitmap = null;
    }

    private void rebuildGlows() {
        releaseGlow();
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || !enabledByUser || painter != null) return;
        float fullW = w * (1f + 2f * GLOW_MARGIN);
        float fullH = h * (1f + 2f * GLOW_MARGIN);
        int bw = Math.max(1, Math.round(fullW / GLOW_DOWNSCALE));
        int bh = Math.max(1, Math.round(fullH / GLOW_DOWNSCALE));
        try {
            glowBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError oom) {
            glowBitmap = null;
            return;
        }
        Canvas c = new Canvas(glowBitmap);
        float sx = bw / fullW;
        float sy = bh / fullH;
        float glowAlpha = (light ? 0.17f : 0.16f) * (pureBlack ? 0.75f : 1f);
        for (int i = 0; i < GLOWS.length; i++) {
            float r = Math.max(1f, w * GLOWS[i][7]);
            int color = colors[(int) GLOWS[i][8]];
            int center = ColorUtils.setAlphaComponent(color, Math.round(255 * glowAlpha));
            int edge = ColorUtils.setAlphaComponent(color, 0);
            float cx = (w * GLOWS[i][0] + w * GLOW_MARGIN) * sx;
            float cy = (h * GLOWS[i][1] + h * GLOW_MARGIN) * sy;
            glowPaint.setShader(new RadialGradient(cx, cy, r * sx, center, edge, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r * sx, glowPaint);
        }
        glowPaint.setShader(null);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildGlows();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        resolveTheme();
        AppPreferences.registerChangeListener(getContext(), prefListener);
        refreshConfig();
    }

    @Override protected void onDetachedFromWindow() {
        AppPreferences.unregisterChangeListener(getContext(), prefListener);
        stopTicker();
        lastFrame = 0L;
        releaseGlow();
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) refreshConfig();
        lastFrame = 0L;
        invalidate();
        updateTicker();
    }

    @Override protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        lastFrame = 0L;
        updateTicker();
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        canvas.drawColor(surface);
        if (!enabledByUser) return;

        boolean animate = shouldAnimate();
        if (animate) {
            long now = SystemClock.uptimeMillis();
            if (lastFrame != 0L) {
                double dt = Math.min(0.1, (now - lastFrame) / 1000.0);
                time += dt * speed;
            }
            lastFrame = now;
        }

        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (painter != null) {
            float alphaScale = (light ? 1.15f : 1f) * (pureBlack ? 0.75f : 1f);
            painter.draw(canvas, w, h, time, colors, alphaScale, density);
        } else {
            drawGlows(canvas, w, h);
            drawShapes(canvas, w, h);
            if (accent == Accent.WAVE) drawWaves(canvas, w, h);
        }

        if (animate && !tickerRunning) updateTicker();
    }

    private void drawGlows(Canvas canvas, float w, float h) {
        if (glowBitmap == null) return;
        float dx = w * GLOW_MARGIN * 0.8f * (float) Math.sin(TWO_PI * time / 29.0);
        float dy = h * GLOW_MARGIN * 0.8f * (float) Math.cos(TWO_PI * time / 37.0);
        float left = -w * GLOW_MARGIN + dx;
        float top = -h * GLOW_MARGIN + dy;
        glowDst.set(left, top, left + w * (1f + 2f * GLOW_MARGIN), top + h * (1f + 2f * GLOW_MARGIN));
        canvas.drawBitmap(glowBitmap, null, glowDst, bitmapPaint);
    }

    private void drawShapes(Canvas canvas, float w, float h) {
        float alphaScale = (light ? 1.15f : 1f) * (pureBlack ? 0.75f : 1f);
        for (float[] s : SHAPES) {
            float size = s[2] * density;
            float cx = w * s[0] + s[3] * density * (float) Math.sin(TWO_PI * time / s[5]);
            float cy = h * s[1] + s[4] * density * (float) Math.cos(TWO_PI * time / s[5] * 0.83);
            float morph = 0.5f - 0.26f * (0.5f - 0.5f * (float) Math.cos(TWO_PI * time / (2f * s[6])));
            float radius = size * morph;
            float rotation = (float) (360.0 * (time / s[7]));
            int color = colors[(int) s[8]];
            paint.setColor(ColorUtils.setAlphaComponent(color, Math.round(255 * Math.min(1f, s[9] * alphaScale))));
            int save = canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(rotation % 360f);
            rect.set(-size / 2f, -size / 2f, size / 2f, size / 2f);
            canvas.drawRoundRect(rect, radius, radius, paint);
            canvas.restoreToCount(save);
        }
    }

    private void drawWaves(Canvas canvas, float w, float h) {
        float alphaScale = (light ? 1.1f : 1f) * (pureBlack ? 0.8f : 1f);
        drawWave(canvas, w, h, 0.925f, 13f, 1.10f, 11.0, 0f, colors[0], 0.075f * alphaScale);
        drawWave(canvas, w, h, 0.955f, 17f, 0.80f, 15.0, 1.9f, colors[1], 0.085f * alphaScale);
    }

    private void drawWave(Canvas canvas, float w, float h, float baseY, float ampDp,
                          float lengthFactor, double period, float phase, int color, float alpha) {
        int n = Math.max(10, (int) (w / (30f * density)));
        float step = w / n;
        float amp = ampDp * density;
        float k = TWO_PI / (w * lengthFactor);
        float shift = (float) (TWO_PI * time / period) + phase;
        wavePath.rewind();
        wavePath.moveTo(0f, h);
        for (int i = 0; i <= n; i++) {
            float x = i * step;
            wavePath.lineTo(x, h * baseY + amp * (float) Math.sin(k * x + shift));
        }
        wavePath.lineTo(w, h);
        wavePath.close();
        paint.setColor(ColorUtils.setAlphaComponent(color, Math.round(255 * Math.min(1f, alpha))));
        canvas.drawPath(wavePath, paint);
    }
}
