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

import android.app.Activity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.color.MaterialColors;
import com.opensourcefinder.app.R;

final class DetailHero {
    private DetailHero() {}

    private static final int[] PRESSABLE = {
            R.id.updateButton, R.id.openInstalledButton, R.id.uninstallInstalledButton,
            R.id.exploreDetailInstall, R.id.exploreDetailOpen, R.id.exploreDetailUninstall
    };

    static void setup(Activity activity, int iconId) {
        final ShapeView halo = activity.findViewById(R.id.heroHalo);
        final View hero = activity.findViewById(R.id.heroContainer);
        final View icon = activity.findViewById(iconId);
        if (halo != null) {
            halo.setFillColor(MaterialColors.getColor(halo, com.google.android.material.R.attr.colorSecondaryContainer, 0xFF444444));
            halo.setShape(AppPreferences.getIconShape(activity), false);
            halo.setIdleSpin(true);
        }
        for (int id : PRESSABLE) {
            View v = activity.findViewById(id);
            if (v != null) MotionHelper.attachPressScale(v);
        }
        if (icon instanceof ShapedIconView) ((ShapedIconView) icon).setSpinEnabled(false);
        boolean reduced = AppPreferences.isReduceMotion(activity);
        if (!reduced) {
            long d = MotionHelper.getDuration(activity, 320, 640);
            if (halo != null) {
                halo.setScaleX(0.4f);
                halo.setScaleY(0.4f);
                halo.setAlpha(0f);
                halo.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(60).setDuration(d)
                        .setInterpolator(new OvershootInterpolator(1.8f)).start();
            }
            if (icon != null) {
                icon.setScaleX(0.3f);
                icon.setScaleY(0.3f);
                icon.setAlpha(0f);
                icon.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(140).setDuration(d)
                        .setInterpolator(new OvershootInterpolator(2.4f)).start();
            }
        }
        NestedScrollView scroll = activity.findViewById(R.id.detailScroll);
        if (scroll != null && hero != null && !reduced) {
            final float density = activity.getResources().getDisplayMetrics().density;
            scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, x, y, ox, oy) -> {
                float p = Math.min(1f, Math.max(0f, y / (150f * density)));
                hero.setTranslationY(y * 0.4f);
                hero.setScaleX(1f - 0.22f * p);
                hero.setScaleY(1f - 0.22f * p);
                hero.setAlpha(1f - 0.9f * p);
            });
        }
    }
}
