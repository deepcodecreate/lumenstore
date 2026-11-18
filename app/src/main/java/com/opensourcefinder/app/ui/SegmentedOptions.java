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
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.TypedValue;
import android.widget.LinearLayout;
import androidx.core.widget.TextViewCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

public final class SegmentedOptions {

    public interface Listener {
        void onSelected(int index);
    }

    private SegmentedOptions() {}

    public static void bind(LinearLayout container, String[] labels, int selected, Listener listener) {
        if (container == null) return;
        Context ctx = container.getContext();
        float d = ctx.getResources().getDisplayMetrics().density;
        container.removeAllViews();
        container.setOrientation(LinearLayout.HORIZONTAL);
        final MaterialButton[] buttons = new MaterialButton[labels.length];
        final int[] current = {selected};
        for (int i = 0; i < labels.length; i++) {
            MaterialButton b = new MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            b.setText(labels[i]);
            b.setAllCaps(false);
            b.setSingleLine(true);
            b.setInsetTop(0);
            b.setInsetBottom(0);
            b.setMinHeight(0);
            b.setMinimumHeight(0);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setLetterSpacing(0f);
            b.setCornerRadius(Math.round(12f * d));
            b.setPaddingRelative(Math.round(4f * d), 0, Math.round(4f * d), 0);
            b.setRippleColor(ColorStateList.valueOf(Color.TRANSPARENT));
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(b, 10, 13, 1, TypedValue.COMPLEX_UNIT_SP);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Math.round(36f * d), 1f);
            if (i > 0) lp.setMarginStart(Math.round(6f * d));
            container.addView(b, lp);
            buttons[i] = b;
            final int index = i;
            b.setOnClickListener(v -> {
                if (current[0] == index) return;
                current[0] = index;
                style(container, buttons, index, d);
                listener.onSelected(index);
            });
        }
        style(container, buttons, selected, d);
    }

    private static void style(LinearLayout container, MaterialButton[] buttons, int selected, float d) {
        int onSurface = MaterialColors.getColor(container, com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
        int onSelected = MaterialColors.getColor(container, com.google.android.material.R.attr.colorOnPrimaryContainer, Color.WHITE);
        int primary = MaterialColors.getColor(container, androidx.appcompat.R.attr.colorPrimary, Color.WHITE);
        int selectedFill = MaterialColors.getColor(container, com.google.android.material.R.attr.colorPrimaryContainer, Color.DKGRAY);
        int idleFill = MaterialColors.getColor(container, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.DKGRAY);
        int idleStroke = MaterialColors.getColor(container, com.google.android.material.R.attr.colorOutlineVariant, Color.GRAY);
        for (int i = 0; i < buttons.length; i++) {
            boolean on = i == selected;
            MaterialButton b = buttons[i];
            b.setBackgroundTintList(ColorStateList.valueOf(on ? selectedFill : idleFill));
            b.setStrokeColor(ColorStateList.valueOf(on ? primary : idleStroke));
            b.setStrokeWidth(Math.round((on ? 1.5f : 1f) * d));
            b.setTextColor(on ? onSelected : onSurface);
            b.setSelected(on);
        }
    }
}
