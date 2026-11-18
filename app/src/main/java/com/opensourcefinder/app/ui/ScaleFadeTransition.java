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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.view.View;
import android.view.ViewGroup;

public class ScaleFadeTransition extends Visibility {
    private final boolean horizontal;

    public ScaleFadeTransition(boolean horizontal) {
        this.horizontal = horizontal;
    }

    @Override
    public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
        preparePivot(view);
        float from = horizontal ? 0f : 0.82f;
        view.setAlpha(0f);
        view.setScaleX(from);
        if (!horizontal) view.setScaleY(from);
        return build(view, 1f, 1f);
    }

    @Override
    public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
        preparePivot(view);
        float to = horizontal ? 0f : 0.82f;
        Animator a = build(view, 0f, to);
        a.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {

                view.post(() -> {
                    view.setAlpha(1f);
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                });
            }
        });
        return a;
    }

    private void preparePivot(View view) {
        boolean rtl = view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        view.setPivotX(horizontal ? (rtl ? view.getWidth() : 0f) : view.getWidth() / 2f);
        view.setPivotY(view.getHeight() / 2f);
    }

    private Animator build(View view, float alpha, float scale) {
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator fade = ObjectAnimator.ofFloat(view, View.ALPHA, alpha);
        ObjectAnimator sx = ObjectAnimator.ofFloat(view, View.SCALE_X, scale);
        if (horizontal) {
            set.playTogether(fade, sx);
        } else {
            set.playTogether(fade, sx, ObjectAnimator.ofFloat(view, View.SCALE_Y, scale));
        }
        return set;
    }
}
