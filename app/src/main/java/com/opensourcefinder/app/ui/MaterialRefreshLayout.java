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
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MaterialRefreshLayout extends SwipeRefreshLayout {
    private static final int TRIGGER_DP = 88;
    private static final int OFFSET_START_DP = 0;
    private static final int OFFSET_END_DP = 80;

    private boolean desiredRefreshing = false;
    private boolean touching = false;
    private final int offsetStart;
    private final int offsetEnd;

    private final Runnable softSettle = () -> settle(false);
    private final Runnable hardSettle = () -> settle(true);

    public MaterialRefreshLayout(@NonNull Context context) {
        this(context, null);
    }

    public MaterialRefreshLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        offsetStart = Math.round(OFFSET_START_DP * density);
        offsetEnd = Math.round(OFFSET_END_DP * density);
        setDistanceToTriggerSync(Math.round(TRIGGER_DP * density));
        setProgressViewOffset(true, offsetStart, offsetEnd);
    }

    @Nullable
    private View findCircle() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if ("CircleImageView".equals(child.getClass().getSimpleName())) return child;
        }
        return null;
    }

    private void scheduleSettle() {
        removeCallbacks(softSettle);
        removeCallbacks(hardSettle);
        postDelayed(softSettle, 700L);
        postDelayed(hardSettle, 1800L);
    }

    private void settle(boolean force) {
        if (desiredRefreshing || isRefreshing() || touching) return;
        View circle = findCircle();
        if (circle == null || circle.getVisibility() != View.VISIBLE) return;
        if (!force && circle.getAnimation() != null && !circle.getAnimation().hasEnded()) return;
        setProgressViewOffset(true, offsetStart, offsetEnd);
    }

    @Override public void setRefreshing(boolean refreshing) {
        desiredRefreshing = refreshing;
        removeCallbacks(softSettle);
        removeCallbacks(hardSettle);
        if (!isLaidOut()) {
            post(() -> {
                super.setRefreshing(desiredRefreshing);
                if (!desiredRefreshing) scheduleSettle();
            });
            return;
        }
        super.setRefreshing(refreshing);
        if (!refreshing) scheduleSettle();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            touching = true;
            removeCallbacks(softSettle);
            removeCallbacks(hardSettle);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            touching = false;
            if (!desiredRefreshing) scheduleSettle();
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE && !desiredRefreshing && !isRefreshing()) scheduleSettle();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(softSettle);
        removeCallbacks(hardSettle);
        touching = false;
        super.onDetachedFromWindow();
    }
}
