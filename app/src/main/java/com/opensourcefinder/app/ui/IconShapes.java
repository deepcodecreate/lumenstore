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

import android.graphics.Path;
import com.opensourcefinder.app.R;
import java.util.HashMap;
import java.util.Map;

public final class IconShapes {
    private IconShapes() {}

    public static final String DEFAULT = "default";
    public static final String[] IDS = {
            "default", "arch", "cloudy", "cylinder", "flower", "four_sided_cookie", "heart", "hexagon",
            "ios", "leaf", "meow", "pebble", "rounded_hexagon", "rounded_rectangle", "samsung", "scroll",
            "seven_sided_cookie", "square", "squircle", "stretched", "tapered_rect", "teardrop", "vessel"
    };

    static final int N = 240;
    private static final float[] COS = new float[N];
    private static final float[] SIN = new float[N];
    private static final Map<String, float[]> CACHE = new HashMap<>();

    static {
        for (int i = 0; i < N; i++) {
            double a = Math.toRadians(-90.0 + i * 360.0 / N);
            COS[i] = (float) Math.cos(a);
            SIN[i] = (float) Math.sin(a);
        }
    }

    public static boolean isValid(String id) {
        if (id == null) return false;
        for (String s : IDS) if (s.equals(id)) return true;
        return false;
    }

    public static String migrate(String old) {
        if (old == null) return DEFAULT;
        if (isValid(old)) return old;
        switch (old) {
            case "rounded": return "rounded_rectangle";
            case "circle": return "default";
            case "cookie4": return "four_sided_cookie";
            case "clover4": return "flower";
            case "sunny": return "seven_sided_cookie";
            case "gem": return "hexagon";
            case "pentagon": return "rounded_hexagon";
            default: return DEFAULT;
        }
    }

    public static float[] radii(String id) {
        if (!isValid(id)) id = DEFAULT;
        synchronized (CACHE) {
            float[] r = CACHE.get(id);
            if (r != null) return r;
            String[] parts = IconShapeData.data(id).split(",");
            r = new float[N];
            for (int i = 0; i < N; i++) r[i] = Integer.parseInt(parts[i]) / 10f;
            CACHE.put(id, r);
            return r;
        }
    }

    public static float[] lerp(float[] a, float[] b, float t) {
        float[] out = new float[N];
        for (int i = 0; i < N; i++) out[i] = a[i] + (b[i] - a[i]) * t;
        return out;
    }

    public static Path build(Path out, float[] a, float[] b, float t, float size, float ox, float oy) {
        if (out == null) out = new Path();
        out.rewind();
        float k = size / 100f, c = size / 2f;
        float[][] scratch = SCRATCH.get();
        float[] px = scratch[0], py = scratch[1];
        for (int i = 0; i < N; i++) {
            float r = (a[i] + (b[i] - a[i]) * t) * k;
            px[i] = ox + c + r * COS[i];
            py[i] = oy + c + r * SIN[i];
        }
        out.moveTo((px[N - 1] + px[0]) / 2f, (py[N - 1] + py[0]) / 2f);
        for (int i = 0; i < N; i++) {
            int j = (i + 1) % N;
            out.quadTo(px[i], py[i], (px[i] + px[j]) / 2f, (py[i] + py[j]) / 2f);
        }
        out.close();
        return out;
    }

    private static final ThreadLocal<float[][]> SCRATCH = new ThreadLocal<float[][]>() {
        @Override protected float[][] initialValue() { return new float[][]{new float[N], new float[N]}; }
    };

    public static float maxRadius(float[] radii, float[] other, float t) {
        float max = 0f;
        for (int i = 0; i < N; i++) {
            float r = other == null ? radii[i] : radii[i] + (other[i] - radii[i]) * t;
            if (r > max) max = r;
        }
        return max;
    }

    public static float spinFit(float[] radii) {
        float max = maxRadius(radii, null, 0f);
        return max > 50f ? 50f / max : 1f;
    }

    private static final Map<String, Path> UNIT_PATHS = new HashMap<>();

    public static synchronized Path unitPath(String id) {
        if (!isValid(id)) id = DEFAULT;
        Path p = UNIT_PATHS.get(id);
        if (p == null) {
            float[] r = radii(id);
            p = build(new Path(), r, r, 0f, 100f, 0f, 0f);
            UNIT_PATHS.put(id, p);
        }
        return p;
    }

    public static void fit(Path out, String id, float size, float ox, float oy, android.graphics.Matrix scratch) {
        out.rewind();
        scratch.setScale(size / 100f, size / 100f);
        scratch.postTranslate(ox, oy);
        unitPath(id).transform(scratch, out);
    }

    public static int labelRes(String id) {
        switch (id == null ? DEFAULT : id) {
            case "arch": return R.string.icon_shape_arch;
            case "cloudy": return R.string.icon_shape_cloudy;
            case "cylinder": return R.string.icon_shape_cylinder;
            case "flower": return R.string.icon_shape_flower;
            case "four_sided_cookie": return R.string.icon_shape_four_sided_cookie;
            case "heart": return R.string.icon_shape_heart;
            case "hexagon": return R.string.icon_shape_hexagon;
            case "ios": return R.string.icon_shape_ios;
            case "leaf": return R.string.icon_shape_leaf;
            case "meow": return R.string.icon_shape_meow;
            case "pebble": return R.string.icon_shape_pebble;
            case "rounded_hexagon": return R.string.icon_shape_rounded_hexagon;
            case "rounded_rectangle": return R.string.icon_shape_rounded_rectangle;
            case "samsung": return R.string.icon_shape_samsung;
            case "scroll": return R.string.icon_shape_scroll;
            case "seven_sided_cookie": return R.string.icon_shape_seven_sided_cookie;
            case "square": return R.string.icon_shape_square;
            case "squircle": return R.string.icon_shape_squircle;
            case "stretched": return R.string.icon_shape_stretched;
            case "tapered_rect": return R.string.icon_shape_tapered_rect;
            case "teardrop": return R.string.icon_shape_teardrop;
            case "vessel": return R.string.icon_shape_vessel;
            default: return R.string.icon_shape_default;
        }
    }
}
