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

import android.graphics.Outline;
import android.graphics.Path;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;
import androidx.annotation.NonNull;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.opensourcefinder.app.R;

public final class IconShapeHelper {
    private IconShapeHelper() {}

    public static void apply(@NonNull View view) {
        apply(view, AppPreferences.getIconShape(view.getContext()));
    }

    public static void apply(@NonNull View view, String shapeId) {
        if (view == null) return;
        if (!IconShapes.isValid(shapeId)) shapeId = IconShapes.DEFAULT;
        if (shapeId.equals(view.getTag(R.id.tag_icon_shape))) return;
        if (view instanceof ShapedIconView) {

            ((ShapedIconView) view).setShape(shapeId);
            view.setTag(R.id.tag_icon_shape, shapeId);
            return;
        }
        if (view instanceof ShapeableImageView) {
            ((ShapeableImageView) view).setShapeAppearanceModel(
                    ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 0f).build());
        }
        view.setOutlineProvider(createOutlineProvider(shapeId));
        view.setClipToOutline(true);
        view.setTag(R.id.tag_icon_shape, shapeId);
        view.invalidateOutline();
    }

    public static void applyContainer(@NonNull View container) {
        String id = AppPreferences.getIconShape(container.getContext());
        apply(container, id);
        int color = MaterialColors.getColor(container, com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xFF444444);
        container.setBackground(new ShapeFillDrawable(id, color));
    }

    public static ViewOutlineProvider createOutlineProvider(final String shapeId) {
        final float[] radii = IconShapes.radii(shapeId);
        return new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                int w = view.getWidth(), h = view.getHeight();
                if (w <= 0 || h <= 0) { outline.setEmpty(); return; }
                float size = Math.min(w, h);
                float ox = (w - size) / 2f, oy = (h - size) / 2f;
                Path path = IconShapes.build(new Path(), radii, radii, 0f, size, ox, oy);
                if (Build.VERSION.SDK_INT >= 30) {
                    outline.setPath(path);
                } else if (path.isConvex()) {
                    outline.setConvexPath(path);
                } else {
                    outline.setRoundRect(Math.round(ox), Math.round(oy), Math.round(ox + size), Math.round(oy + size), size * 0.28f);
                }
            }
        };
    }
}
