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
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.opensourcefinder.app.R;

public final class MotionHelper {
    private MotionHelper() {}

    public static long getDuration(Context context, int standardMs, int expressiveMs) {
        String style = AppPreferences.getMotionStyle(context);
        if (AppPreferences.MOTION_REDUCED.equals(style)) return 0L;
        return AppPreferences.MOTION_EXPRESSIVE.equals(style) ? expressiveMs : standardMs;
    }

    public static Interpolator getInterpolator(Context context) {
        return AppPreferences.MOTION_EXPRESSIVE.equals(AppPreferences.getMotionStyle(context))
                ? new OvershootInterpolator(0.55f) : new DecelerateInterpolator(1.7f);
    }

    public static void applyActivityTransition(@NonNull Activity activity, boolean forward) {
        if (com.opensourcefinder.app.ui.AppPreferences.isReduceMotion(activity)) {
            applyReducedActivityTransition(activity);
            return;
        }
        int enterGravity = forward ? android.view.Gravity.END : android.view.Gravity.START;
        int exitGravity = forward ? android.view.Gravity.START : android.view.Gravity.END;

        android.transition.Slide enter = new android.transition.Slide(enterGravity);
        android.transition.Slide exit = new android.transition.Slide(exitGravity);
        android.transition.Slide reenter = new android.transition.Slide(exitGravity);
        android.transition.Slide returnTransition = new android.transition.Slide(enterGravity);

        enter.excludeTarget(R.id.bottomNav, true);
        exit.excludeTarget(R.id.bottomNav, true);
        reenter.excludeTarget(R.id.bottomNav, true);
        returnTransition.excludeTarget(R.id.bottomNav, true);

        long duration = getDuration(activity, 210, 310);
        enter.setDuration(duration);
        exit.setDuration(duration);
        reenter.setDuration(duration);
        returnTransition.setDuration(duration);
        Interpolator tabInterpolator = getInterpolator(activity);
        enter.setInterpolator(tabInterpolator);
        exit.setInterpolator(tabInterpolator);
        reenter.setInterpolator(tabInterpolator);
        returnTransition.setInterpolator(tabInterpolator);

        activity.getWindow().setEnterTransition(enter);
        activity.getWindow().setExitTransition(exit);
        activity.getWindow().setReenterTransition(reenter);
        activity.getWindow().setReturnTransition(returnTransition);
        activity.getWindow().setAllowEnterTransitionOverlap(true);
        activity.getWindow().setAllowReturnTransitionOverlap(true);
    }

    public static void bounceNavItem(@NonNull com.google.android.material.bottomnavigation.BottomNavigationView nav, int itemId) {
        NavCapsule.moveTo(nav, itemId, true);
        long duration = getDuration(nav.getContext(), 300, 460);
        if (duration == 0L) return;
        final float density = nav.getResources().getDisplayMetrics().density;
        android.view.Menu menu = nav.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            int id = menu.getItem(i).getItemId();
            View item = nav.findViewById(id);
            if (item == null) continue;
            View icon = item.findViewById(com.google.android.material.R.id.navigation_bar_item_icon_view);
            if (icon == null) continue;
            icon.animate().cancel();
            if (id == itemId) {
                icon.setScaleX(0.72f);
                icon.setScaleY(0.72f);
                icon.setTranslationY(0f);
                icon.animate().scaleX(1.1f).scaleY(1.1f).translationY(-1.5f * density).setDuration(duration)
                        .setInterpolator(new OvershootInterpolator(3.2f)).start();
            } else if (icon.getScaleX() != 1f || icon.getTranslationY() != 0f) {
                icon.animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(duration / 2)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
        }
    }

    public static void attachPressScale(@NonNull View target) {
        if (AppPreferences.isReduceMotion(target.getContext())) return;
        target.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                view.animate().cancel();
                view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(110)
                        .setInterpolator(new DecelerateInterpolator()).start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                view.animate().cancel();
                view.animate().scaleX(1f).scaleY(1f)
                        .setDuration(getDuration(view.getContext(), 220, 380))
                        .setInterpolator(new OvershootInterpolator(2.4f)).start();
            }
            return false;
        });
    }

    public static void animateEntrance(@NonNull View target, int index) {
        if (AppPreferences.isReduceMotion(target.getContext())) return;
        float density = target.getResources().getDisplayMetrics().density;
        target.animate().cancel();
        target.setAlpha(0f);
        target.setScaleX(0.88f);
        target.setScaleY(0.88f);
        target.setTranslationY(18f * density);
        target.animate()
                .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setStartDelay(Math.min(index, 10) * 45L)
                .setDuration(getDuration(target.getContext(), 280, 460))
                .setInterpolator(getInterpolator(target.getContext()))
                .start();
    }

    public static void keepBottomNavStatic(@NonNull Activity activity) {
        View bottomNav = activity.findViewById(R.id.bottomNav);
        if (bottomNav == null) return;
        excludeFromTransition(activity.getWindow().getEnterTransition(), bottomNav);
        excludeFromTransition(activity.getWindow().getExitTransition(), bottomNav);
        excludeFromTransition(activity.getWindow().getReenterTransition(), bottomNav);
        excludeFromTransition(activity.getWindow().getReturnTransition(), bottomNav);
    }

    private static void excludeFromTransition(android.transition.Transition transition, View view) {
        if (transition == null) return;
        transition.excludeTarget(view, true);
        transition.excludeChildren(view, true);
    }

    public static void applyModalTransition(@NonNull Activity activity) {
        applyActivityTransition(activity, true);
    }

    private static void applyReducedActivityTransition(@NonNull Activity activity) {
        android.transition.Fade fade = new android.transition.Fade();
        fade.setDuration(0);
        fade.excludeTarget(R.id.bottomNav, true);
        activity.getWindow().setEnterTransition(fade);
        activity.getWindow().setExitTransition(fade);
        activity.getWindow().setReenterTransition(fade);
        activity.getWindow().setReturnTransition(fade);
    }

    public static void applyDistinctActivityTransition(@NonNull Activity activity) {
        if (com.opensourcefinder.app.ui.AppPreferences.isReduceMotion(activity)) {
            applyReducedActivityTransition(activity);
            return;
        }
        android.transition.TransitionSet enter = new android.transition.TransitionSet();
        android.transition.Fade fade = new android.transition.Fade();
        android.transition.Slide slide = new android.transition.Slide(android.view.Gravity.BOTTOM);
        enter.addTransition(fade);
        enter.addTransition(slide);
        enter.setDuration(getDuration(activity, 220, 320));
        enter.setInterpolator(getInterpolator(activity));

        android.transition.TransitionSet exit = new android.transition.TransitionSet();
        exit.addTransition(new android.transition.Fade());
        exit.addTransition(new android.transition.Slide(android.view.Gravity.TOP));
        exit.setDuration(getDuration(activity, 180, 240));
        exit.setInterpolator(getInterpolator(activity));

        android.transition.TransitionSet reenter = new android.transition.TransitionSet();
        reenter.addTransition(new android.transition.Fade());
        reenter.addTransition(new android.transition.Slide(android.view.Gravity.TOP));
        reenter.setDuration(getDuration(activity, 210, 280));
        reenter.setInterpolator(getInterpolator(activity));

        android.transition.TransitionSet returnTransition = new android.transition.TransitionSet();
        returnTransition.addTransition(new android.transition.Fade());
        returnTransition.addTransition(new android.transition.Slide(android.view.Gravity.BOTTOM));
        returnTransition.setDuration(getDuration(activity, 180, 240));
        returnTransition.setInterpolator(getInterpolator(activity));

        activity.getWindow().setEnterTransition(enter);
        activity.getWindow().setExitTransition(exit);
        activity.getWindow().setReenterTransition(reenter);
        activity.getWindow().setReturnTransition(returnTransition);
        activity.getWindow().setAllowEnterTransitionOverlap(true);
        activity.getWindow().setAllowReturnTransitionOverlap(true);
    }

    public static void setupListAnimator(@NonNull RecyclerView rv) {
        if (com.opensourcefinder.app.ui.AppPreferences.isReduceMotion(rv.getContext())) {
            rv.setItemAnimator(null);
            return;
        }
        DefaultItemAnimator animator = new DefaultItemAnimator();
        long duration = getDuration(rv.getContext(), 180, 260);
        animator.setAddDuration(duration);
        animator.setRemoveDuration(duration);
        animator.setChangeDuration(duration);
        animator.setMoveDuration(duration);
        animator.setSupportsChangeAnimations(false);
        rv.setItemAnimator(animator);
    }

    public static void animateActivityContent(@NonNull Activity activity) {
    }

    public static void hideFab(@NonNull View fab) {
        if (fab.getVisibility() != View.VISIBLE && fab.getScaleX() == 0f) return;
        fab.animate().cancel();
        if (AppPreferences.isReduceMotion(fab.getContext())) {
            fab.animate().setListener(null);
            fab.setAlpha(0f);
            fab.setScaleX(0f);
            fab.setScaleY(0f);
            fab.setVisibility(View.INVISIBLE);
            return;
        }
        fab.animate()
                .scaleX(0f).scaleY(0f).alpha(0f)
                .setDuration(getDuration(fab.getContext(), 150, 210))
                .setInterpolator(getInterpolator(fab.getContext()))
                .setListener(new android.animation.AnimatorListenerAdapter() {
                    private boolean cancelled;
                    @Override public void onAnimationCancel(android.animation.Animator animation) { cancelled = true; }
                    @Override public void onAnimationEnd(android.animation.Animator animation) {
                        fab.animate().setListener(null);
                        if (cancelled) return;
                        if (fab instanceof FloatingActionButton) {
                            ((FloatingActionButton) fab).hide();
                        } else {
                            fab.setVisibility(View.INVISIBLE);
                        }
                    }
                })
                .start();
    }

    public static void showFab(@NonNull View fab) {
        fab.animate().cancel();
        fab.animate().setListener(null);
        if (AppPreferences.isReduceMotion(fab.getContext())) {
            fab.setVisibility(View.VISIBLE);
            fab.setScaleX(1f);
            fab.setScaleY(1f);
            fab.setAlpha(1f);
            return;
        }
        if (fab instanceof FloatingActionButton) {
            FloatingActionButton f = (FloatingActionButton) fab;
            if (f.getVisibility() != View.VISIBLE) {
                f.setScaleX(0f);
                f.setScaleY(0f);
                f.setAlpha(0f);
                f.show();
            }
        } else {
            fab.setVisibility(View.VISIBLE);
        }
        fab.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(getDuration(fab.getContext(), 180, 300))
                .setInterpolator(getInterpolator(fab.getContext()))
                .start();
    }
}
