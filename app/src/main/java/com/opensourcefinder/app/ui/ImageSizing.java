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

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ImageSizing {

    static final float NONE = -1f;

    private static final Pattern DIMENSION = Pattern.compile(
            "^\\s*([0-9]*\\.?[0-9]+)\\s*(%|px|em|rem|pt|in|cm|mm)?\\s*$", Pattern.CASE_INSENSITIVE);

    private ImageSizing() {}

    static final class Dimension {
        final float value;
        final String unit;

        Dimension(float value, @Nullable String unit) {
            this.value = value;
            this.unit = unit;
        }
    }

    @Nullable
    static Dimension parse(@Nullable String spec) {
        if (spec == null) return null;
        Matcher matcher = DIMENSION.matcher(spec);
        if (!matcher.matches()) return null;
        try {
            float value = Float.parseFloat(matcher.group(1));
            if (value <= 0f) return null;
            return new Dimension(value, matcher.group(2));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static float resolve(@Nullable Dimension dimension, float percentBase, float fontPx, float density) {
        if (dimension == null) return NONE;
        return resolve(dimension.value, dimension.unit, percentBase, fontPx, density);
    }

    static float resolve(float value, @Nullable String unit, float percentBase, float fontPx, float density) {
        if (!(value > 0f)) return NONE;
        if (unit == null || unit.isEmpty()) return value * density;
        switch (unit.toLowerCase(Locale.ROOT)) {
            case "%":
                return percentBase > 0f ? percentBase * value / 100f : NONE;
            case "em":
            case "rem":
                return value * fontPx;
            case "pt":
                return value * 4f / 3f * density;
            case "in":
                return value * 96f * density;
            case "cm":
                return value * 96f / 2.54f * density;
            case "mm":
                return value * 96f / 25.4f * density;
            case "px":
            default:
                return value * density;
        }
    }

    @Nullable
    static float[] natural(@Nullable Drawable drawable, float density) {
        if (drawable == null) return null;
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0 || height <= 0) return null;
        return new float[]{width * density, height * density};
    }

    @Nullable
    static float[] fit(@Nullable float[] natural, float requestedWidth, float requestedHeight,
                       float maxWidth, float fallbackSide) {
        float width;
        float height;
        if (requestedWidth > 0f && requestedHeight > 0f) {
            width = requestedWidth;
            height = requestedHeight;
        } else if (requestedWidth > 0f) {
            width = requestedWidth;
            height = natural != null ? width * natural[1] / natural[0] : width;
        } else if (requestedHeight > 0f) {
            height = requestedHeight;
            width = natural != null ? height * natural[0] / natural[1] : height;
        } else if (natural != null) {
            width = natural[0];
            height = natural[1];
        } else if (fallbackSide > 0f) {
            width = fallbackSide;
            height = fallbackSide;
        } else {
            return null;
        }
        if (maxWidth > 0f && width > maxWidth) {
            height = height * maxWidth / width;
            width = maxWidth;
        }
        return new float[]{Math.max(width, 1f), Math.max(height, 1f)};
    }

    @NonNull
    static float percentBaseForHeight(@Nullable float[] natural, float maxWidth) {
        return natural != null ? natural[1] : maxWidth;
    }
}
