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
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

public final class PanelTransition {
    private static final String HIDING = "panel_hiding";
    private static final String SHOWING = "panel_showing";

    private PanelTransition() {}

    public static void swap(final View from, final View to, boolean forward) {
        final Context context = to.getContext();
        final long duration = MotionHelper.getDuration(context, 220, 340);
        from.animate().cancel();
        to.animate().cancel();
        to.setTag(SHOWING);
        if (duration <= 0 || from.getVisibility() != View.VISIBLE || !from.isLaidOut()) {
            reset(from);
            from.setVisibility(View.GONE);
            reset(to);
            to.setVisibility(View.VISIBLE);
            return;
        }
        boolean rtl = context.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        float shift = 28f * context.getResources().getDisplayMetrics().density * (forward != rtl ? 1f : -1f);
        from.setTag(HIDING);
        to.setAlpha(0f);
        to.setTranslationX(shift);
        to.setVisibility(View.VISIBLE);
        to.animate().alpha(1f).translationX(0f).setStartDelay(40)
                .setDuration(duration).setInterpolator(new DecelerateInterpolator(1.8f)).start();
        from.animate().alpha(0f).translationX(-shift * 0.6f).setStartDelay(0)
                .setDuration((long) (duration * 0.7f)).setInterpolator(new AccelerateInterpolator(1.4f))
                .withEndAction(() -> {
                    if (HIDING.equals(from.getTag())) from.setVisibility(View.GONE);
                    reset(from);
                }).start();
    }

    public static boolean isShown(View v) {
        return v != null && v.getVisibility() == View.VISIBLE && !HIDING.equals(v.getTag());
    }

    private static void reset(View v) {
        v.setAlpha(1f);
        v.setTranslationX(0f);
    }
}
