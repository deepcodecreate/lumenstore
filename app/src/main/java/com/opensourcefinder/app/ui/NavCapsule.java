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

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.MaterialColors;
import com.opensourcefinder.app.R;

public final class NavCapsule {
    private static final float BASE_MARGIN_BOTTOM_DP = 12f;
    private static final float INDICATOR_INSET_X_DP = 6f;
    private static final float INDICATOR_INSET_Y_DP = 5f;
    private static final float COMPACT_BAR_HEIGHT_DP = 64f;
    private static final float COMPACT_ICON_DP = 22f;
    private static final float COMPACT_PILL_WIDTH_DP = 64f;
    private static final float COMPACT_PILL_HEIGHT_DP = 32f;

    private final BottomNavigationView nav;
    private final IndicatorDrawable drawable = new IndicatorDrawable();
    private boolean compact;
    private ValueAnimator animator;
    private int currentId = View.NO_ID;

    private NavCapsule(BottomNavigationView nav) {
        this.nav = nav;
    }

    public static void install(@NonNull BottomNavigationView nav) {
        if (nav.getTag(R.id.bottomNav) instanceof NavCapsule) return;
        final NavCapsule capsule = new NavCapsule(nav);
        nav.setTag(R.id.bottomNav, capsule);
        capsule.compact = AppPreferences.isNavLabelsHidden(nav.getContext());
        if (capsule.compact) applyCompact(nav);
        capsule.drawable.setColor(MaterialColors.getColor(nav,
                com.google.android.material.R.attr.colorSecondaryContainer, 0xFFE8DEF8));
        applyFloatingBackground(nav, capsule.drawable);
        nav.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (capsule.animator == null || !capsule.animator.isRunning()) {
                capsule.snapTo(nav.getSelectedItemId());
            }
        });
        applyBottomInset(nav);
    }

    private static void applyCompact(BottomNavigationView nav) {
        final float density = nav.getResources().getDisplayMetrics().density;
        nav.setLabelVisibilityMode(com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_UNLABELED);
        nav.setItemIconSize(Math.round(COMPACT_ICON_DP * density));
        nav.setItemPaddingTop(0);
        nav.setItemPaddingBottom(0);
        int height = Math.round(COMPACT_BAR_HEIGHT_DP * density);
        nav.setMinimumHeight(height);
        ViewGroup.LayoutParams params = nav.getLayoutParams();
        if (params != null) {
            params.height = height;
            nav.setLayoutParams(params);
        }
    }

    private static void applyFloatingBackground(BottomNavigationView nav, Drawable indicator) {
        final float density = nav.getResources().getDisplayMetrics().density;
        int surface = MaterialColors.getColor(nav,
                com.google.android.material.R.attr.colorSurfaceContainer, 0xFF211F26);
        int stroke = MaterialColors.getColor(nav,
                com.google.android.material.R.attr.colorOutlineVariant, 0x33000000);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(48f * density);
        bg.setColor(ColorUtils.setAlphaComponent(surface, 0xEB));
        bg.setStroke(Math.max(1, Math.round(0.5f * density)), ColorUtils.setAlphaComponent(stroke, 0x99));

        nav.setBackground(new android.graphics.drawable.LayerDrawable(new Drawable[]{bg, indicator}));
    }

    public static void moveTo(@NonNull BottomNavigationView nav, int itemId, boolean animate) {
        Object tag = nav.getTag(R.id.bottomNav);
        if (!(tag instanceof NavCapsule)) return;
        NavCapsule capsule = (NavCapsule) tag;
        if (animate) capsule.animateTo(itemId);
        else capsule.snapTo(itemId);
    }

    private static void applyBottomInset(BottomNavigationView nav) {
        final float density = nav.getResources().getDisplayMetrics().density;
        final int base = Math.round(BASE_MARGIN_BOTTOM_DP * density);
        ViewCompat.setOnApplyWindowInsetsListener(nav, (v, insets) -> {
            int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            ViewGroup.LayoutParams params = v.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margin = (ViewGroup.MarginLayoutParams) params;
                int target = base + bottom;
                if (margin.bottomMargin != target) {
                    margin.bottomMargin = target;
                    v.setLayoutParams(margin);
                }
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(nav);
    }

    @Nullable
    private RectF targetFor(int itemId) {
        View item = nav.findViewById(itemId);
        if (item == null || item.getWidth() == 0) return null;
        Rect rect = new Rect();
        item.getDrawingRect(rect);
        nav.offsetDescendantRectToMyCoords(item, rect);
        float density = nav.getResources().getDisplayMetrics().density;
        if (compact) {
            float width = Math.min(COMPACT_PILL_WIDTH_DP * density, rect.width());
            float height = COMPACT_PILL_HEIGHT_DP * density;
            float cx = rect.exactCenterX();
            float cy = rect.exactCenterY();
            return new RectF(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f);
        }
        float dx = INDICATOR_INSET_X_DP * density;
        float dy = INDICATOR_INSET_Y_DP * density;
        return new RectF(rect.left + dx, rect.top + dy, rect.right - dx, rect.bottom - dy);
    }

    private void snapTo(int itemId) {
        RectF target = targetFor(itemId);
        if (target == null) return;
        currentId = itemId;
        drawable.setRect(target);
    }

    private void animateTo(int itemId) {
        final RectF target = targetFor(itemId);
        if (target == null) return;

        if (currentId == itemId && animator != null && animator.isRunning()) return;
        Context context = nav.getContext();
        long duration = MotionHelper.getDuration(context, 260, 360);
        final RectF from = new RectF(drawable.rect);
        if (duration == 0L || from.isEmpty() || currentId == View.NO_ID || currentId == itemId) {
            if (animator != null) animator.cancel();
            currentId = itemId;
            drawable.setRect(target);
            return;
        }
        if (animator != null) animator.cancel();
        currentId = itemId;
        final boolean movingRight = target.centerX() > from.centerX();

        final android.view.animation.Interpolator leading = new DecelerateInterpolator(1.6f);
        final android.view.animation.Interpolator trailing = new AccelerateDecelerateInterpolator();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.addUpdateListener(a -> {
            float p = (float) a.getAnimatedValue();
            float fast = leading.getInterpolation(p);
            float slow = trailing.getInterpolation(p);
            float leftP = movingRight ? slow : fast;
            float rightP = movingRight ? fast : slow;
            RectF now = new RectF(
                    from.left + (target.left - from.left) * leftP,
                    target.top,
                    from.right + (target.right - from.right) * rightP,
                    target.bottom);
            drawable.setRect(now);
        });
        animator.start();
    }

    private static final class IndicatorDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        void setColor(int color) {
            paint.setColor(color);
        }

        void setRect(RectF value) {
            rect.set(value);
            invalidateSelf();
        }

        @Override public void draw(@NonNull Canvas canvas) {
            if (rect.isEmpty()) return;
            float radius = rect.height() / 2f;
            canvas.drawRoundRect(rect, radius, radius, paint);
        }

        @Override public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
