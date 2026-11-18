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
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import com.opensourcefinder.app.R;

public final class AccordionHelper {

    private AccordionHelper() {}

    public static void setExpanded(ViewGroup animationRoot, View content, ImageView chevron, boolean expand, boolean animate) {
        if (content == null) return;
        long duration = MotionHelper.getDuration(content.getContext(), 220, 320);
        boolean shouldAnimate = animate && duration > 0;
        Object running = content.getTag(R.id.tag_accordion_animator);
        if (running instanceof ValueAnimator) ((ValueAnimator) running).cancel();
        ViewGroup.LayoutParams params = content.getLayoutParams();
        if (!shouldAnimate || params == null) {
            if (params != null) params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            content.setVisibility(expand ? View.VISIBLE : View.GONE);
        } else if (expand) {
            View parent = (View) content.getParent();
            int width = parent != null && parent.getWidth() > 0
                    ? parent.getWidth() : content.getResources().getDisplayMetrics().widthPixels;
            content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int target = content.getMeasuredHeight();
            params.height = 0;
            content.setVisibility(View.VISIBLE);
            content.setAlpha(0f);
            run(content, params, 0, target, 0f, 1f, duration, false);
        } else {
            int from = content.getHeight();
            if (content.getVisibility() != View.VISIBLE || from <= 0) {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                content.setVisibility(View.GONE);
            } else {
                run(content, params, from, 0, content.getAlpha(), 0f, duration, true);
            }
        }
        if (chevron != null) {
            float target = expand ? 180f : 0f;
            if (shouldAnimate) {
                chevron.animate().rotation(target).setDuration(duration)
                        .setInterpolator(new DecelerateInterpolator(1.7f)).start();
            } else {
                chevron.setRotation(target);
            }
        }
    }

    private static void run(View content, ViewGroup.LayoutParams params, int from, int to,
                            float alphaFrom, float alphaTo, long duration, boolean hideAtEnd) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator(1.7f));
        animator.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            params.height = from + Math.round((to - from) * f);
            content.setAlpha(alphaFrom + (alphaTo - alphaFrom) * f);
            content.setLayoutParams(params);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (cancelled) return;
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                content.setAlpha(1f);
                if (hideAtEnd) content.setVisibility(View.GONE);
                content.setLayoutParams(params);
            }
        });
        content.setTag(R.id.tag_accordion_animator, animator);
        animator.start();
    }

    public interface ExpandedState {
        boolean isExpanded();
        void setExpanded(boolean expanded);
    }

    public static void bind(View header, ViewGroup animationRoot, View content, ImageView chevron, ExpandedState state) {
        if (header == null) return;
        setExpanded(animationRoot, content, chevron, state.isExpanded(), false);
        header.setOnClickListener(v -> {
            boolean next = !state.isExpanded();
            state.setExpanded(next);
            setExpanded(animationRoot, content, chevron, next, true);
        });
    }
}
