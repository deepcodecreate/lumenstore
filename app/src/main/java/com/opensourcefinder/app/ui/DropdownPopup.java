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
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.radiobutton.MaterialRadioButton;

import java.util.ArrayList;
import java.util.List;

public final class DropdownPopup {
    public interface OnChoice { void onChoice(int index); }
    public interface OnCheck { void onCheck(boolean checked); }

    private DropdownPopup() {}

    public static void show(@NonNull View anchor, @NonNull String[] labels, int selected,
                            @NonNull OnChoice onChoice,
                            @Nullable String checkLabel, boolean checked, @Nullable OnCheck onCheck) {
        Builder b = builder(anchor).radios(null, labels, selected, onChoice);
        if (checkLabel != null && onCheck != null) b.divider().check(checkLabel, checked, onCheck);
        b.show();
    }

    public static Builder builder(@NonNull View anchor) {
        return new Builder(anchor);
    }

    private static final int KIND_TITLE = 0, KIND_RADIO = 1, KIND_CHECK = 2, KIND_ACTION = 3, KIND_DIVIDER = 4;

    private static final class Item {
        int kind;
        String label;
        int group;
        int index;
        boolean checked;
        int iconRes;
        int tint;
        OnChoice onChoice;
        OnCheck onCheck;
        Runnable action;
    }

    public static final class Builder {
        private final View anchor;
        private final List<Item> items = new ArrayList<>();
        private int groups = 0;
        private int fixedWidthDp = 0;

        private Builder(View anchor) { this.anchor = anchor; }

        public Builder width(int dp) { fixedWidthDp = dp; return this; }

        public Builder title(String text) {
            Item i = new Item(); i.kind = KIND_TITLE; i.label = text; items.add(i); return this;
        }

        public Builder divider() {
            if (items.isEmpty()) return this;
            Item i = new Item(); i.kind = KIND_DIVIDER; items.add(i); return this;
        }

        public Builder radios(@Nullable String title, String[] labels, int selected, OnChoice onChoice) {
            if (title != null) title(title);
            int group = groups++;
            for (int n = 0; n < labels.length; n++) {
                Item i = new Item();
                i.kind = KIND_RADIO; i.label = labels[n]; i.group = group; i.index = n;
                i.checked = n == selected; i.onChoice = onChoice;
                items.add(i);
            }
            return this;
        }

        public Builder check(String label, boolean checked, OnCheck onCheck) {
            Item i = new Item(); i.kind = KIND_CHECK; i.label = label; i.checked = checked; i.onCheck = onCheck;
            items.add(i); return this;
        }

        public Builder action(String label, int iconRes, int tint, Runnable action) {
            Item i = new Item(); i.kind = KIND_ACTION; i.label = label; i.iconRes = iconRes; i.tint = tint;
            i.action = action; items.add(i); return this;
        }

        public void show() { DropdownPopup.display(anchor, items, fixedWidthDp); }
    }

    private static void display(View anchor, List<Item> items, int fixedWidthDp) {
        if (items.isEmpty()) return;
        final Context context = anchor.getContext();
        final float density = context.getResources().getDisplayMetrics().density;

        final MaterialCardView card = new MaterialCardView(context);
        card.setRadius(20f * density);
        card.setCardElevation(0f);
        card.setStrokeWidth(0);
        card.setCardBackgroundColor(MaterialColors.getColor(anchor,
                com.google.android.material.R.attr.colorSurfaceContainerHigh));

        final LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        int vPad = (int) (6 * density);
        column.setPadding(0, vPad, 0, vPad);
        card.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final Holder holder = new Holder();
        final int minRow = (int) (44 * density);
        final int hPad = (int) (16 * density);
        final int textColor = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorOnSurface);
        final int primary = MaterialColors.getColor(anchor, androidx.appcompat.R.attr.colorPrimary);
        final List<List<MaterialRadioButton>> radioGroups = new ArrayList<>();

        for (final Item item : items) {
            switch (item.kind) {
                case KIND_TITLE: {
                    TextView t = new TextView(context);
                    t.setText(item.label);
                    t.setSingleLine(true);
                    t.setTextColor(primary);
                    applyAppearance(context, t, com.google.android.material.R.attr.textAppearanceLabelLarge);
                    t.setTextColor(primary);
                    t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                    t.setPadding(hPad, (int) (10 * density), hPad, (int) (4 * density));
                    column.addView(t, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    break;
                }
                case KIND_DIVIDER: {
                    MaterialDivider d = new MaterialDivider(context);
                    LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    dp.setMargins(hPad, vPad / 2, hPad, vPad / 2);
                    column.addView(d, dp);
                    break;
                }
                case KIND_RADIO: {
                    while (radioGroups.size() <= item.group) radioGroups.add(new ArrayList<>());
                    final List<MaterialRadioButton> group = radioGroups.get(item.group);
                    final MaterialRadioButton radio = new MaterialRadioButton(context);
                    radio.setChecked(item.checked);
                    group.add(radio);
                    LinearLayout row = row(context, radio, item.label, textColor, minRow, density);
                    row.setOnClickListener(v -> {
                        AppPreferences.performHaptic(v);
                        for (MaterialRadioButton r : group) r.setChecked(r == radio);
                        if (item.onChoice != null) item.onChoice.onChoice(item.index);
                        holder.dismiss();
                    });
                    column.addView(row, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    break;
                }
                case KIND_CHECK: {
                    final MaterialCheckBox box = new MaterialCheckBox(context);
                    box.setChecked(item.checked);
                    LinearLayout row = row(context, box, item.label, textColor, minRow, density);
                    row.setOnClickListener(v -> {
                        AppPreferences.performHaptic(v);
                        box.toggle();
                        if (item.onCheck != null) item.onCheck.onCheck(box.isChecked());
                        holder.dismiss();
                    });
                    column.addView(row, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    break;
                }
                default: {
                    ImageView icon = null;
                    if (item.iconRes != 0) {
                        icon = new ImageView(context);
                        icon.setImageResource(item.iconRes);
                        icon.setImageTintList(android.content.res.ColorStateList.valueOf(item.tint != 0 ? item.tint : textColor));
                    }
                    LinearLayout row = row(context, icon, item.label, item.tint != 0 ? item.tint : textColor, minRow, density);
                    row.setOnClickListener(v -> {
                        AppPreferences.performHaptic(v);
                        holder.dismiss();
                        if (item.action != null) item.action.run();
                    });
                    column.addView(row, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    break;
                }
            }
        }

        int margin = (int) (8 * density);
        Wrapper wrapper = new Wrapper(context);
        wrapper.setPadding(margin, margin, margin, margin);
        wrapper.addView(card, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int minWidth = (int) (200 * density);
        boolean narrow = fixedWidthDp > 0 || anchor.getWidth() < minWidth;
        int contentWidth = fixedWidthDp > 0 ? (int) (fixedWidthDp * density) : Math.max(anchor.getWidth(), minWidth);
        PopupWindow window = new PopupWindow(wrapper, contentWidth + 2 * margin, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setOutsideTouchable(true);
        window.setElevation(0f);
        window.setAnimationStyle(0);
        holder.window = window;
        holder.card = card;
        holder.reduced = AppPreferences.isReduceMotion(context);
        holder.duration = MotionHelper.getDuration(context, 170, 300);

        final long duration = holder.duration;
        if (duration > 0) {
            card.setAlpha(0f);
            card.setScaleX(0.85f);
            card.setScaleY(0.85f);
            for (int i = 0; i < column.getChildCount(); i++) column.getChildAt(i).setAlpha(0f);
        }
        try {
            int dir = anchor.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -1 : 1;
            if (narrow) window.showAsDropDown(anchor, dir * margin, -margin, Gravity.END);
            else window.showAsDropDown(anchor, -dir * margin, -margin, Gravity.START);
        } catch (Exception e) {
            return;
        }
        if (duration > 0) {
            final boolean above = window.isAboveAnchor();
            final boolean fromEnd = narrow;
            final boolean rtlPopup = anchor.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            final ViewTreeObserver observer = card.getViewTreeObserver();
            observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    ViewTreeObserver vto = card.getViewTreeObserver();
                    if (vto.isAlive()) vto.removeOnPreDrawListener(this);
                    float pivotFraction = fromEnd ? 0.9f : 0.15f;
                    card.setPivotX(card.getWidth() * (rtlPopup ? 1f - pivotFraction : pivotFraction));
                    card.setPivotY(above ? card.getHeight() : 0f);
                    card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(duration)
                            .setInterpolator(new OvershootInterpolator(0.9f)).start();
                    float shift = (above ? 6f : -6f) * density;
                    for (int i = 0; i < column.getChildCount(); i++) {
                        View child = column.getChildAt(i);
                        child.setTranslationY(shift);
                        child.animate().alpha(1f).translationY(0f)
                                .setStartDelay(30L + i * 28L)
                                .setDuration((long) (duration * 0.9f))
                                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
                    }
                    return true;
                }
            });
        }
    }

    private static LinearLayout row(Context context, @Nullable View lead, String label, int textColor,
                                    int minRow, float density) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        row.setMinimumHeight(minRow);
        row.setPaddingRelative((int) (12 * density), 0, (int) (16 * density), 0);
        android.util.TypedValue tv = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            row.setBackgroundResource(tv.resourceId);
        }
        row.setClickable(true);
        row.setFocusable(true);
        if (lead instanceof android.widget.CompoundButton) {
            lead.setClickable(false);
            lead.setFocusable(false);
            lead.setBackground(null);
            row.addView(lead, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else if (lead != null) {
            int size = (int) (22 * density);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(size, size);
            ip.setMarginStart((int) (4 * density));
            row.addView(lead, ip);
        }
        TextView text = new TextView(context);
        text.setText(label);
        text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        applyAppearance(context, text, com.google.android.material.R.attr.textAppearanceBodyLarge);
        text.setTextColor(textColor);
        text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMarginStart((int) ((lead == null ? 4 : 8) * density));
        row.addView(text, tp);
        return row;
    }

    private static void applyAppearance(Context context, TextView text, int attr) {
        android.util.TypedValue appearance = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(attr, appearance, true)) {
            androidx.core.widget.TextViewCompat.setTextAppearance(text, appearance.resourceId);
        }
    }

    private static final class Holder {
        PopupWindow window;
        MaterialCardView card;
        boolean reduced;
        long duration;
        boolean dismissing;

        void dismiss() {
            if (window == null || dismissing) return;
            dismissing = true;
            if (reduced || duration <= 0 || card == null) { safeDismiss(); return; }
            card.animate().cancel();
            card.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f)
                    .setDuration((long) (duration * 0.6f))
                    .withEndAction(this::safeDismiss).start();
        }

        private void safeDismiss() {
            try { window.dismiss(); } catch (Exception ignored) {}
        }
    }

    private static final class Wrapper extends android.widget.FrameLayout {
        Wrapper(Context c) { super(c); setClipToPadding(false); setClipChildren(false); }
    }
}
