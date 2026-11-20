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
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.NestedScrollingChild3;
import androidx.core.view.NestedScrollingChildHelper;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;

import com.opensourcefinder.app.R;

public class MaterialRefreshLayout extends FrameLayout implements NestedScrollingParent3, NestedScrollingChild3 {
    public interface OnRefreshListener {
        void onRefresh();
    }

    public interface OnChildScrollUpCallback {
        boolean canChildScrollUp(@NonNull MaterialRefreshLayout parent, @Nullable View child);
    }

    private static final float TRIGGER_DP = 80f;
    private static final float HOLD_DP = 72f;
    private static final float MAX_PULL_DP = 168f;
    private static final float DRAG_FACTOR = 0.5f;

    private final NestedScrollingParentHelper parentHelper = new NestedScrollingParentHelper(this);
    private final NestedScrollingChildHelper childHelper = new NestedScrollingChildHelper(this);
    private final int[] parentOffsetInWindow = new int[2];
    private final int[] parentScrollConsumed = new int[2];
    private final float trigger;
    private final float hold;
    private final float maxPull;

    private View indicator;
    private OnRefreshListener listener;
    private OnChildScrollUpCallback childScrollUpCallback;
    private ValueAnimator animator;
    private boolean refreshing;
    private float pull;

    public MaterialRefreshLayout(@NonNull Context context) {
        this(context, null);
    }

    public MaterialRefreshLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        trigger = TRIGGER_DP * density;
        hold = HOLD_DP * density;
        maxPull = MAX_PULL_DP * density;
        childHelper.setNestedScrollingEnabled(true);
        setWillNotDraw(false);
    }

    @Override protected void onFinishInflate() {
        super.onFinishInflate();
        indicator = LayoutInflater.from(getContext()).inflate(R.layout.view_refresh_indicator, this, false);
        indicator.setVisibility(INVISIBLE);
        indicator.setAlpha(0f);
        addView(indicator);
    }

    public void setOnRefreshListener(@Nullable OnRefreshListener listener) {
        this.listener = listener;
    }

    public void setOnChildScrollUpCallback(@Nullable OnChildScrollUpCallback callback) {
        this.childScrollUpCallback = callback;
    }

    public boolean isRefreshing() {
        return refreshing;
    }

    public void setRefreshing(boolean value) {
        if (refreshing == value) return;
        refreshing = value;
        animatePullTo(value ? hold : 0f);
    }

    @Nullable
    private View content() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child != indicator) return child;
        }
        return null;
    }

    private boolean canChildScrollUp() {
        View content = content();
        if (childScrollUpCallback != null) return childScrollUpCallback.canChildScrollUp(this, content);
        return content != null && content.canScrollVertically(-1);
    }

    private void setPull(float value) {
        pull = Math.max(0f, Math.min(maxPull, value));
        View content = content();
        if (content != null) content.setTranslationY(pull);
        if (indicator == null) return;
        float fraction = Math.min(1f, pull / hold);
        boolean visible = pull > 0f || refreshing;
        indicator.setVisibility(visible ? VISIBLE : INVISIBLE);
        indicator.setAlpha(refreshing ? 1f : fraction);
        float scale = 0.4f + 0.6f * (refreshing ? 1f : fraction);
        indicator.setScaleX(scale);
        indicator.setScaleY(scale);
        indicator.setTranslationY(pull / 2f - indicator.getHeight() / 2f);
    }

    private void animatePullTo(float target) {
        cancelAnimation();
        animator = ValueAnimator.ofFloat(pull, target);
        animator.setDuration(260L);
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(a -> setPull((float) a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                if (animator == a) animator = null;
            }
        });
        animator.start();
    }

    private void cancelAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (indicator != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) indicator.getLayoutParams();
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            setPull(pull);
        }
    }

    @Override protected void onDetachedFromWindow() {
        cancelAnimation();
        super.onDetachedFromWindow();
    }

    @Override public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes, int type) {
        return isEnabled() && (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes, int type) {
        parentHelper.onNestedScrollAccepted(child, target, axes, type);
        startNestedScroll(axes & ViewCompat.SCROLL_AXIS_VERTICAL, type);
        if (type == ViewCompat.TYPE_TOUCH && !refreshing) cancelAnimation();
    }

    @Override public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
        if (type == ViewCompat.TYPE_TOUCH && !refreshing && dy > 0 && pull > 0f) {
            int used = (int) Math.min(dy, pull);
            setPull(pull - used);
            consumed[1] += used;
        }
        int[] parentConsumed = parentScrollConsumed;
        parentConsumed[0] = 0;
        parentConsumed[1] = 0;
        if (dispatchNestedPreScroll(dx - consumed[0], dy - consumed[1], parentConsumed, null, type)) {
            consumed[0] += parentConsumed[0];
            consumed[1] += parentConsumed[1];
        }
    }

    @Override public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed,
                                         int dxUnconsumed, int dyUnconsumed, int type, @NonNull int[] consumed) {
        int before = consumed[1];
        childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
                parentOffsetInWindow, type, consumed);
        int remaining = dyUnconsumed - (consumed[1] - before);
        if (type == ViewCompat.TYPE_TOUCH && !refreshing && remaining < 0 && !canChildScrollUp()) {
            float damping = 1f - Math.min(1f, pull / maxPull);
            setPull(pull + (-remaining) * DRAG_FACTOR * damping);
            consumed[1] += remaining;
        }
    }

    @Override public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed,
                                         int dxUnconsumed, int dyUnconsumed, int type) {
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, parentScrollConsumed);
    }

    @Override public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed,
                                         int dxUnconsumed, int dyUnconsumed) {
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, ViewCompat.TYPE_TOUCH, parentScrollConsumed);
    }

    @Override public void onStopNestedScroll(@NonNull View target, int type) {
        parentHelper.onStopNestedScroll(target, type);
        if (type == ViewCompat.TYPE_TOUCH && !refreshing && pull > 0f) {
            if (pull >= trigger) {
                refreshing = true;
                animatePullTo(hold);
                if (listener != null) listener.onRefresh();
            } else {
                animatePullTo(0f);
            }
        }
        stopNestedScroll(type);
    }

    @Override public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes) {
        return onStartNestedScroll(child, target, axes, ViewCompat.TYPE_TOUCH);
    }

    @Override public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes) {
        onNestedScrollAccepted(child, target, axes, ViewCompat.TYPE_TOUCH);
    }

    @Override public void onStopNestedScroll(@NonNull View target) {
        onStopNestedScroll(target, ViewCompat.TYPE_TOUCH);
    }

    @Override public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed) {
        onNestedPreScroll(target, dx, dy, consumed, ViewCompat.TYPE_TOUCH);
    }

    @Override public int getNestedScrollAxes() {
        return parentHelper.getNestedScrollAxes();
    }

    @Override public boolean onNestedPreFling(@NonNull View target, float velocityX, float velocityY) {
        return dispatchNestedPreFling(velocityX, velocityY);
    }

    @Override public boolean onNestedFling(@NonNull View target, float velocityX, float velocityY, boolean consumed) {
        return dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override public void setNestedScrollingEnabled(boolean enabled) {
        childHelper.setNestedScrollingEnabled(enabled);
    }

    @Override public boolean isNestedScrollingEnabled() {
        return childHelper.isNestedScrollingEnabled();
    }

    @Override public boolean startNestedScroll(int axes) {
        return childHelper.startNestedScroll(axes);
    }

    @Override public boolean startNestedScroll(int axes, int type) {
        return childHelper.startNestedScroll(axes, type);
    }

    @Override public void stopNestedScroll() {
        childHelper.stopNestedScroll();
    }

    @Override public void stopNestedScroll(int type) {
        childHelper.stopNestedScroll(type);
    }

    @Override public boolean hasNestedScrollingParent() {
        return childHelper.hasNestedScrollingParent();
    }

    @Override public boolean hasNestedScrollingParent(int type) {
        return childHelper.hasNestedScrollingParent(type);
    }

    @Override public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
                                                  int dyUnconsumed, @Nullable int[] offsetInWindow) {
        return childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow);
    }

    @Override public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
                                                  int dyUnconsumed, @Nullable int[] offsetInWindow, int type) {
        return childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type);
    }

    @Override public void dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
                                               int dyUnconsumed, @Nullable int[] offsetInWindow, int type,
                                               @NonNull int[] consumed) {
        childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type, consumed);
    }

    @Override public boolean dispatchNestedPreScroll(int dx, int dy, @Nullable int[] consumed,
                                                     @Nullable int[] offsetInWindow) {
        return childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow);
    }

    @Override public boolean dispatchNestedPreScroll(int dx, int dy, @Nullable int[] consumed,
                                                     @Nullable int[] offsetInWindow, int type) {
        return childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type);
    }

    @Override public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return childHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return childHelper.dispatchNestedPreFling(velocityX, velocityY);
    }
}
