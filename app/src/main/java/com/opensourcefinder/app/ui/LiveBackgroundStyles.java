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
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.ColorUtils;

import com.opensourcefinder.app.R;

import java.util.Random;

public final class LiveBackgroundStyles {
    public static final String CLASSIC = "classic";
    public static final String AURORA = "aurora";
    public static final String BUBBLES = "bubbles";
    public static final String STARS = "stars";
    public static final String RIPPLES = "ripples";
    public static final String FIREFLIES = "fireflies";
    public static final String WAVES = "waves";

    public static final String[] IDS = {CLASSIC, AURORA, BUBBLES, STARS, RIPPLES, FIREFLIES, WAVES};

    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private LiveBackgroundStyles() {}

    public static String normalize(@Nullable String id) {
        if (id != null) for (String s : IDS) if (s.equals(id)) return s;
        return CLASSIC;
    }

    @StringRes
    public static int labelRes(String id) {
        switch (normalize(id)) {
            case AURORA: return R.string.live_style_aurora;
            case BUBBLES: return R.string.live_style_bubbles;
            case STARS: return R.string.live_style_stars;
            case RIPPLES: return R.string.live_style_ripples;
            case FIREFLIES: return R.string.live_style_fireflies;
            case WAVES: return R.string.live_style_waves;
            default: return R.string.live_style_classic;
        }
    }

    @Nullable
    public static Painter create(String id) {
        switch (normalize(id)) {
            case AURORA: return new Aurora();
            case BUBBLES: return new Bubbles();
            case STARS: return new Stars();
            case RIPPLES: return new Ripples();
            case FIREFLIES: return new Fireflies();
            case WAVES: return new Waves();
            default: return null;
        }
    }

    public interface Painter {
        void draw(@NonNull Canvas canvas, float w, float h, double time, @NonNull int[] colors,
                  float alphaScale, float density);
    }

    private static int withAlpha(int color, float alpha) {
        return ColorUtils.setAlphaComponent(color, Math.round(255f * Math.max(0f, Math.min(1f, alpha))));
    }

    private static final class Sprite {
        private Bitmap bitmap;
        private int color;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final RectF dst = new RectF();

        void ensure(int c) {
            if (bitmap != null && color == c) return;
            color = c;
            int size = 64;
            if (bitmap == null) bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new RadialGradient(size / 2f, size / 2f, size / 2f,
                    new int[]{withAlpha(c, 1f), withAlpha(c, 0.45f), withAlpha(c, 0f)},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, p);
        }

        void draw(Canvas canvas, int c, float cx, float cy, float halfW, float halfH, float alpha) {
            ensure(c);
            paint.setAlpha(Math.round(255f * Math.max(0f, Math.min(1f, alpha))));
            dst.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH);
            canvas.drawBitmap(bitmap, null, dst, paint);
        }
    }

    private static final class Aurora implements Painter {
        private final Sprite[] sprites = {new Sprite(), new Sprite(), new Sprite(), new Sprite()};
        private final float[][] bands = {
                {0.22f, 0.20f, 0.30f, 19f, 0f, 0.30f, -8f, 0},
                {0.38f, 0.30f, 0.26f, 23f, 1.7f, 0.26f, 7f, 1},
                {0.62f, 0.62f, 0.34f, 29f, 3.1f, 0.30f, -6f, 0},
                {0.80f, 0.78f, 0.28f, 31f, 4.4f, 0.26f, 9f, 1},
        };

        @Override public void draw(@NonNull Canvas canvas, float w, float h, double t, @NonNull int[] colors,
                                   float alphaScale, float density) {
            for (int i = 0; i < bands.length; i++) {
                float[] b = bands[i];
                float cx = w * (0.5f + 0.34f * (float) Math.sin(TWO_PI * t / b[3] + b[4]));
                float cy = h * (b[1] + 0.05f * (float) Math.cos(TWO_PI * t / (b[3] * 1.3) + b[4]));
                float halfW = w * (0.75f + 0.1f * (float) Math.sin(TWO_PI * t / (b[3] * 0.8) + b[4]));
                float halfH = h * b[2] * 0.5f;
                int save = canvas.save();
                canvas.rotate(b[6] + 4f * (float) Math.sin(TWO_PI * t / (b[3] * 1.7)), cx, cy);
                sprites[i].draw(canvas, colors[(int) b[7]], cx, cy, halfW, halfH, b[5] * alphaScale * 0.75f);
                canvas.restoreToCount(save);
            }
        }
    }

    private static final class Bubbles implements Painter {
        private static final int N = 16;
        private final float[][] data = new float[N][6];
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

        Bubbles() {
            Random r = new Random(11);
            for (int i = 0; i < N; i++) {
                data[i][0] = r.nextFloat();
                data[i][1] = 7f + r.nextFloat() * 26f;
                data[i][2] = 16f + r.nextFloat() * 20f;
                data[i][3] = r.nextFloat();
                data[i][4] = 6f + r.nextFloat() * 16f;
                data[i][5] = r.nextFloat() * TWO_PI;
            }
            stroke.setStyle(Paint.Style.STROKE);
        }

        @Override public void draw(@NonNull Canvas canvas, float w, float h, double t, @NonNull int[] colors,
                                   float alphaScale, float density) {
            stroke.setStrokeWidth(1.4f * density);
            for (int i = 0; i < N; i++) {
                float[] d = data[i];
                float radius = d[1] * density;
                float progress = (float) ((t / d[2] + d[3]) % 1.0);
                float travel = h + radius * 4f;
                float y = h + radius * 2f - travel * progress;
                float x = w * d[0] + d[4] * density * (float) Math.sin(TWO_PI * t / 9.0 + d[5]);
                float fade = (float) Math.min(1.0, Math.min(progress * 6f, (1f - progress) * 6f));
                int color = colors[i % 2];
                fill.setColor(withAlpha(color, 0.10f * alphaScale * fade));
                stroke.setColor(withAlpha(color, 0.26f * alphaScale * fade));
                canvas.drawCircle(x, y, radius, fill);
                canvas.drawCircle(x, y, radius, stroke);
            }
        }
    }

    private static final class Stars implements Painter {
        private static final int N = 48;
        private final float[][] data = new float[N][5];
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Stars() {
            Random r = new Random(23);
            for (int i = 0; i < N; i++) {
                data[i][0] = r.nextFloat();
                data[i][1] = r.nextFloat();
                data[i][2] = 0.7f + r.nextFloat() * (i < 7 ? 2.2f : 0.9f);
                data[i][3] = 2.2f + r.nextFloat() * 4.5f;
                data[i][4] = r.nextFloat() * TWO_PI;
            }
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override public void draw(@NonNull Canvas canvas, float w, float h, double t, @NonNull int[] colors,
                                   float alphaScale, float density) {
            for (int i = 0; i < N; i++) {
                float[] d = data[i];
                float twinkle = 0.5f + 0.5f * (float) Math.sin(TWO_PI * t / d[3] + d[4]);
                float alpha = (0.14f + 0.46f * twinkle) * alphaScale;
                float x = w * d[0];
                float y = h * d[1] + 3f * density * (float) Math.sin(TWO_PI * t / 41.0 + d[4]);
                float r = d[2] * density;
                paint.setColor(withAlpha(colors[i % 3 == 0 ? 1 : 0], alpha));
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(x, y, r, paint);
                if (i < 7) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(1.2f * density);
                    float arm = r * (2.2f + 1.4f * twinkle);
                    canvas.drawLine(x - arm, y, x + arm, y, paint);
                    canvas.drawLine(x, y - arm, x, y + arm, paint);
                }
            }
        }
    }

    private static final class Ripples implements Painter {
        private final float[][] origins = {
                {0.22f, 0.24f, 13f, 0f},
                {0.80f, 0.52f, 15f, 0.35f},
                {0.34f, 0.84f, 17f, 0.7f},
        };
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Ripples() { paint.setStyle(Paint.Style.STROKE); }

        @Override public void draw(@NonNull Canvas canvas, float w, float h, double t, @NonNull int[] colors,
                                   float alphaScale, float density) {
            float maxR = Math.max(w, h) * 0.55f;
            paint.setStrokeWidth(2f * density);
            for (int i = 0; i < origins.length; i++) {
                float[] o = origins[i];
                for (int ring = 0; ring < 3; ring++) {
                    float phase = (float) ((t / o[2] + o[3] + ring / 3f) % 1.0);
                    float eased = 1f - (1f - phase) * (1f - phase);
                    paint.setColor(withAlpha(colors[(i + ring) % 2], 0.26f * alphaScale * (1f - phase)));
                    canvas.drawCircle(w * o[0], h * o[1], maxR * eased, paint);
                }
            }
        }
    }

    private static final class Fireflies implements Painter {
        private static final int N = 16;
        private final float[][] data = new float[N][9];
        private final Sprite sprite = new Sprite();
        private final Sprite spriteAlt = new Sprite();

        Fireflies() {
            Random r = new Random(5);
            for (int i = 0; i < N; i++) {
                data[i][0] = 0.06f + r.nextFloat() * 0.88f;
                data[i][1] = 0.06f + r.nextFloat() * 0.88f;
                data[i][2] = 18f + r.nextFloat() * 40f;
                data[i][3] = 14f + r.nextFloat() * 40f;
                data[i][4] = 11f + r.nextFloat() * 15f;
                data[i][5] = 13f + r.nextFloat() * 17f;
                data[i][6] = r.nextFloat() * TWO_PI;
                data[i][7] = 2.5f + r.nextFloat() * 3.5f;
                data[i][8] = 9f + r.nextFloat() * 11f;
            }
        }

        @Override public void draw(@NonNull Canvas canvas, float w, float h, double t, @NonNull int[] colors,
                                   float alphaScale, float density) {
            for (int i = 0; i < N; i++) {
                float[] d = data[i];
                float x = w * d[0] + d[2] * density * (float) Math.sin(TWO_PI * t / d[4] + d[6]);
                float y = h * d[1] + d[3] * density * (float) Math.cos(TWO_PI * t / d[5] + d[6]);
                float pulse = 0.5f + 0.5f * (float) Math.sin(TWO_PI * t / d[7] + d[6]);
                float half = d[8] * density * (0.8f + 0.5f * pulse);
                (i % 2 == 0 ? sprite : spriteAlt).draw(canvas, colors[i % 2], x, y, half, half,
                        (0.16f + 0.40f * pulse) * alphaScale);
            }
        }
    }

    private static final class Waves implements Painter {
        private final float[][] layers = {
                {0.70f, 20f, 1.30f, 17f, 0.0f, 0.06f, 0},
                {0.78f, 24f, 0.95f, 13f, 1.4f, 0.08f, 1},
                {0.86f, 20f, 0.75f, 11f, 2.8f, 0.10f, 0},
                {0.94f, 16f, 0.60f, 9f, 4.2f, 0.12f, 1},
        };
        private final Path path = new Path();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override public void draw(@NonNull Canvas canvas, float w, float h, double t, @NonNull int[] colors,
                                   float alphaScale, float density) {
            int n = Math.max(12, (int) (w / (26f * density)));
            float step = w / n;
            for (float[] l : layers) {
                float amp = l[1] * density;
                float k = TWO_PI / (w * l[2]);
                float shift = (float) (TWO_PI * t / l[3]) + l[4];
                path.rewind();
                path.moveTo(0f, h);
                for (int i = 0; i <= n; i++) {
                    float x = i * step;
                    path.lineTo(x, h * l[0] + amp * (float) Math.sin(k * x + shift)
                            + amp * 0.35f * (float) Math.sin(k * 2.1f * x - shift * 0.7f));
                }
                path.lineTo(w, h);
                path.close();
                paint.setColor(withAlpha(colors[(int) l[6]], l[5] * alphaScale));
                canvas.drawPath(path, paint);
            }
        }
    }
}
