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

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.ActionMenuView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;

public final class ToolbarCircleHelper {
    private static final int DIAMETER_DP = 40;

    private ToolbarCircleHelper() {}

    public static void apply(MaterialToolbar toolbar) {
        if (toolbar == null) return;
        toolbar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> style(toolbar));
        toolbar.post(() -> style(toolbar));
    }

    private static void style(MaterialToolbar toolbar) {
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof ImageButton) {
                circle(child);
            } else if (child instanceof ActionMenuView) {
                ViewGroup menu = (ViewGroup) child;
                for (int j = 0; j < menu.getChildCount(); j++) circle(menu.getChildAt(j));
            }
        }
    }

    private static void circle(View view) {
        if (view.getBackground() instanceof CenteredCircle) return;
        float density = view.getResources().getDisplayMetrics().density;
        int fill = MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFF333333);
        float radius = DIAMETER_DP * density / 2f;
        view.setBackground(new CenteredCircle(fill, radius));
        int pad = Math.round(12 * density);
        view.setPadding(pad, pad, pad, pad);
    }

    private static final class CenteredCircle extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float radius;

        CenteredCircle(int color, float radius) {
            this.radius = radius;
            paint.setColor(color);
        }

        @Override public void draw(@NonNull Canvas canvas) {
            canvas.drawCircle(getBounds().exactCenterX(), getBounds().exactCenterY(), radius, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(@Nullable ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
