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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

public abstract class SwipeToDeleteCallback extends ItemTouchHelper.SimpleCallback {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Drawable icon;
    private final int background;
    private final float radius;
    private final float density;

    public SwipeToDeleteCallback(@NonNull Context context) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        density = context.getResources().getDisplayMetrics().density;
        background = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorErrorContainer, Color.parseColor("#8C1D18"));
        int foreground = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnErrorContainer, Color.WHITE);
        Drawable d = AppCompatResources.getDrawable(context, TablerIconRes.get(context, "ic_trash"));
        if (d != null) {
            d = DrawableCompat.wrap(d.mutate());
            DrawableCompat.setTint(d, foreground);
        }
        icon = d;
        radius = 20f * density;
    }

    private final java.util.Set<RecyclerView.ViewHolder> restored = new java.util.HashSet<>();

    public void restore(@NonNull RecyclerView.ViewHolder holder) {
        restored.add(holder);
        View v = holder.itemView;
        v.setTranslationX(0f);
        v.setAlpha(1f);
        v.setScaleX(1f);
        v.setScaleY(1f);
        repaint(v);
    }

    private static void repaint(View child) {
        android.view.ViewParent parent = child.getParent();
        if (parent instanceof RecyclerView) {
            final RecyclerView rv = (RecyclerView) parent;
            rv.invalidate();
            rv.post(rv::invalidate);
            rv.postDelayed(rv::invalidate, 120L);
        } else {
            child.invalidate();
        }
    }

    @Override public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder holder) {
        restored.remove(holder);
        super.clearView(recyclerView, holder);
        holder.itemView.setTranslationX(0f);
        holder.itemView.setAlpha(1f);
        recyclerView.invalidate();
    }

    protected abstract boolean canSwipe(@NonNull RecyclerView.ViewHolder holder);

    @Override public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder holder) {
        if (!canSwipe(holder)) return 0;
        return super.getMovementFlags(recyclerView, holder);
    }

    @Override public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                                    @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return 0.4f;
    }

    @Override public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                      @NonNull RecyclerView.ViewHolder holder, float dX, float dY,
                                      int actionState, boolean isCurrentlyActive) {
        if (isCurrentlyActive) restored.remove(holder);
        if (restored.contains(holder)) {
            super.onChildDraw(c, recyclerView, holder, 0f, 0f, actionState, false);
            return;
        }
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f) {
            View v = holder.itemView;
            float left = dX > 0 ? v.getLeft() : v.getRight() + dX;
            float right = dX > 0 ? v.getLeft() + dX : v.getRight();
            rect.set(left, v.getTop(), right, v.getBottom());
            float progress = Math.min(1f, Math.abs(dX) / Math.max(1f, v.getWidth() * 0.35f));
            paint.setColor(background);
            paint.setAlpha((int) (255 * progress));
            c.drawRoundRect(rect, radius, radius, paint);
            if (icon != null) {
                int size = (int) (24 * density);
                int margin = (int) (22 * density);
                if (Math.abs(dX) > margin + size) {
                    int top = v.getTop() + (v.getHeight() - size) / 2;
                    int iconLeft = dX > 0 ? v.getLeft() + margin : v.getRight() - margin - size;
                    icon.setBounds(iconLeft, top, iconLeft + size, top + size);
                    icon.setAlpha((int) (255 * progress));
                    icon.draw(c);
                }
            }
        }
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            holder.itemView.setTranslationX(dX);
            holder.itemView.setTranslationY(dY);
            return;
        }
        super.onChildDraw(c, recyclerView, holder, dX, dY, actionState, isCurrentlyActive);
    }
}
