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
import android.view.ViewTreeObserver;
import android.widget.TextView;
import com.opensourcefinder.app.R;

public final class ReadMoreHelper {
    private static final int MAX_LINES = 4;
    private ReadMoreHelper() {}

    public static void setup(TextView content, TextView action) {
        if (content == null || action == null) return;
        action.setVisibility(View.GONE);
        content.setMaxLines(Integer.MAX_VALUE);
        content.setEllipsize(null);

        Runnable measure = () -> {
            if (content.getLineCount() > MAX_LINES) {
                content.setMaxLines(MAX_LINES);
                content.setEllipsize(android.text.TextUtils.TruncateAt.END);
                action.setVisibility(View.VISIBLE);
                action.setText(R.string.read_more);
                action.setOnClickListener(v -> {
                    if (content.getMaxLines() == MAX_LINES) {
                        animateLines(content, Integer.MAX_VALUE);
                        action.setText(R.string.show_less);
                    } else {
                        animateLines(content, MAX_LINES);
                        action.setText(R.string.read_more);
                    }
                });
            } else {
                action.setVisibility(View.GONE);
            }
        };

        content.post(measure);
        content.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (content.getLineCount() > 0) {
                    content.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    measure.run();
                }
            }
        });
    }

    private static void animateLines(TextView content, int lines) {
        long duration = MotionHelper.getDuration(content.getContext(), 220, 320);
        Object running = content.getTag(R.id.tag_readmore_animator);
        if (running instanceof ValueAnimator) ((ValueAnimator) running).cancel();
        ViewGroup.LayoutParams params = content.getLayoutParams();
        int from = content.getHeight();
        content.setMaxLines(lines);
        content.setEllipsize(lines == Integer.MAX_VALUE ? null : android.text.TextUtils.TruncateAt.END);
        if (duration <= 0 || params == null || content.getWidth() <= 0) {
            if (params != null) params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            content.requestLayout();
            return;
        }
        content.measure(View.MeasureSpec.makeMeasureSpec(content.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int to = content.getMeasuredHeight();
        params.height = from;
        content.setLayoutParams(params);
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator(1.7f));
        animator.addUpdateListener(a -> {
            params.height = (int) a.getAnimatedValue();
            content.setLayoutParams(params);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (cancelled) return;
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                content.setLayoutParams(params);
            }
        });
        content.setTag(R.id.tag_readmore_animator, animator);
        animator.start();
    }
}
