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
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.TextViewCompat;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.opensourcefinder.app.R;

import java.util.ArrayList;
import java.util.List;

public final class HomeMenuPanel {
    public interface OnChoice { void onChoice(int index); }
    public interface OnCheck { void onCheck(boolean checked); }

    public static final class Config {
        public String sortTitle;
        public String[] sortLabels;
        public int sortSelected;
        public OnChoice onSort;
        public String sourceTitle;
        public String[] sourceLabels;
        public int sourceSelected;
        public OnChoice onSource;
        public String listTitle;
        public String updatesFirstLabel;
        public boolean updatesFirst;
        public OnCheck onUpdatesFirst;
        public String notInstalledLastLabel;
        public boolean notInstalledLast;
        public OnCheck onNotInstalledLast;
        public int installedCount;
        public int updatesCount;
        public int availableCount;
        public String installedLabel;
        public String updatesLabel;
        public String availableLabel;
        public List<Action> actions = new ArrayList<>();
    }

    public static final class Action {
        public final String iconName;
        public final String label;
        public final String badge;
        public final Runnable run;
        public Action(String iconName, String label, String badge, Runnable run) {
            this.iconName = iconName;
            this.label = label;
            this.badge = badge;
            this.run = run;
        }
    }

    private HomeMenuPanel() {}

    public static void populate(LinearLayout container, Config c) {
        container.removeAllViews();
        addStats(container, c);
        for (Action action : c.actions) addAction(container, action);
        if (!c.actions.isEmpty()) addDivider(container);
        final List<Section> sections = new ArrayList<>();
        sections.add(radioSection(container, c.sortTitle, "ic_arrows_sort", c.sortLabels, c.sortSelected, c.onSort));
        sections.add(radioSection(container, c.sourceTitle, "ic_cloud", c.sourceLabels, c.sourceSelected, c.onSource));
        sections.add(checkSection(container, c));
        for (final Section section : sections) {
            section.header.setOnClickListener(v -> {
                AppPreferences.performHaptic(v);
                boolean expand = !section.expanded;
                for (Section other : sections) {
                    if (other != section && other.expanded) toggle(container, other, false);
                }
                toggle(container, section, expand);
            });
        }
    }

    private static GradientDrawable roundedFill(Context context, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusDp * context.getResources().getDisplayMetrics().density);
        return g;
    }

    private static void addStats(LinearLayout container, Config c) {
        Context context = container.getContext();
        float d = context.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(3f);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        int tile = MaterialColors.getColor(container, com.google.android.material.R.attr.colorSurfaceContainerHighest);
        int highlight = MaterialColors.getColor(container, com.google.android.material.R.attr.colorPrimaryContainer);
        int[] counts = {c.installedCount, c.updatesCount, c.availableCount};
        String[] labels = {c.installedLabel, c.updatesLabel, c.availableLabel};
        java.text.NumberFormat nf = java.text.NumberFormat.getInstance();
        for (int i = 0; i < 3; i++) {
            boolean accent = i == 1 && counts[i] > 0;
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding((int) (4 * d), (int) (10 * d), (int) (4 * d), (int) (10 * d));
            cell.setBackground(roundedFill(context, accent ? highlight : tile, 16));
            TextView number = new TextView(context);
            number.setText(nf.format(counts[i]));
            number.setSingleLine(true);
            number.setGravity(Gravity.CENTER);
            number.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            appearance(context, number, com.google.android.material.R.attr.textAppearanceTitleLarge);
            number.setTextColor(accent
                    ? MaterialColors.getColor(container, com.google.android.material.R.attr.colorOnPrimaryContainer)
                    : MaterialColors.getColor(container, androidx.appcompat.R.attr.colorPrimary));
            cell.addView(number, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView label = new TextView(context);
            label.setText(labels[i]);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            label.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            appearance(context, label, com.google.android.material.R.attr.textAppearanceLabelSmall);
            label.setTextColor(accent
                    ? MaterialColors.getColor(container, com.google.android.material.R.attr.colorOnPrimaryContainer)
                    : MaterialColors.getColor(container, com.google.android.material.R.attr.colorOnSurfaceVariant));
            cell.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.setMarginStart((int) (6 * d));
            row.addView(cell, lp);
        }
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = (int) (8 * d);
        container.addView(row, rp);
    }

    private static ImageView iconBadge(Context context, View anchor, String iconName) {
        float d = context.getResources().getDisplayMetrics().density;
        ImageView icon = new ImageView(context);
        icon.setImageResource(TablerIconRes.get(context, iconName));
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorOnPrimaryContainer)));
        int pad = (int) (7 * d);
        icon.setPadding(pad, pad, pad, pad);
        icon.setBackground(roundedFill(context,
                MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorPrimaryContainer), 16));
        return icon;
    }

    private static void addAction(LinearLayout container, final Action action) {
        Context context = container.getContext();
        float d = context.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight((int) (48 * d));
        row.setPaddingRelative((int) (6 * d), (int) (4 * d), (int) (10 * d), (int) (4 * d));
        row.setClickable(true);
        row.setFocusable(true);
        android.util.TypedValue tv = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            row.setForeground(androidx.appcompat.content.res.AppCompatResources.getDrawable(context, tv.resourceId));
        }
        int size = (int) (32 * d);
        row.addView(iconBadge(context, container, action.iconName), new LinearLayout.LayoutParams(size, size));
        TextView text = new TextView(context);
        text.setText(action.label);
        text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        appearance(context, text, com.google.android.material.R.attr.textAppearanceBodyLarge);
        text.setTextColor(MaterialColors.getColor(container, com.google.android.material.R.attr.colorOnSurface));
        text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMarginStart((int) (12 * d));
        row.addView(text, tp);
        if (action.badge != null) {
            TextView badge = new TextView(context);
            badge.setText(action.badge);
            badge.setSingleLine(true);
            appearance(context, badge, com.google.android.material.R.attr.textAppearanceLabelMedium);
            badge.setTextColor(MaterialColors.getColor(container, com.google.android.material.R.attr.colorOnPrimary));
            badge.setBackground(roundedFill(context,
                    MaterialColors.getColor(container, androidx.appcompat.R.attr.colorPrimary), 12));
            badge.setPadding((int) (8 * d), (int) (2 * d), (int) (8 * d), (int) (2 * d));
            row.addView(badge, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        row.setOnClickListener(v -> {
            AppPreferences.performHaptic(v);
            if (action.run != null) action.run.run();
        });
        container.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static void addDivider(LinearLayout container) {
        Context context = container.getContext();
        float d = context.getResources().getDisplayMetrics().density;
        View line = new View(context);
        line.setBackgroundColor(MaterialColors.getColor(container, com.google.android.material.R.attr.colorOutlineVariant));
        line.setAlpha(0.5f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) d));
        lp.topMargin = (int) (6 * d);
        lp.bottomMargin = (int) (4 * d);
        lp.setMarginStart((int) (8 * d));
        lp.setMarginEnd((int) (8 * d));
        container.addView(line, lp);
    }

    private static final class Section {
        View header;
        View body;
        ImageView chevron;
        boolean expanded;
    }

    private static void toggle(ViewGroup root, Section section, boolean expand) {
        section.expanded = expand;
        AccordionHelper.setExpanded(root, section.body, section.chevron, expand, true);
    }

    private static Section newSection(LinearLayout container, String title, String iconName, String summary) {
        Context context = container.getContext();
        float d = context.getResources().getDisplayMetrics().density;
        Section section = new Section();

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight((int) (48 * d));
        header.setPaddingRelative((int) (10 * d), 0, (int) (10 * d), 0);
        header.setClickable(true);
        header.setFocusable(true);

        ImageView lead = new ImageView(context);
        lead.setImageResource(TablerIconRes.get(context, iconName));
        lead.setImageTintList(android.content.res.ColorStateList.valueOf(
                MaterialColors.getColor(container, com.google.android.material.R.attr.colorOnSurfaceVariant)));
        int leadSize = (int) (20 * d);
        LinearLayout.LayoutParams leadParams = new LinearLayout.LayoutParams(leadSize, leadSize);
        leadParams.setMarginEnd((int) (12 * d));
        header.addView(lead, leadParams);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setSingleLine(true);
        appearance(context, titleView, com.google.android.material.R.attr.textAppearanceTitleSmall);
        titleView.setTextColor(MaterialColors.getColor(container, com.google.android.material.R.attr.colorOnSurface));
        titleView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        header.addView(titleView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(context);
        value.setText(summary);
        value.setSingleLine(true);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        appearance(context, value, com.google.android.material.R.attr.textAppearanceBodySmall);
        value.setTextColor(MaterialColors.getColor(container, androidx.appcompat.R.attr.colorPrimary));
        value.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.setMarginStart((int) (12 * d));
        vp.setMarginEnd((int) (6 * d));
        header.addView(value, vp);

        ImageView chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.ic_chevron_down);
        chevron.setImageTintList(android.content.res.ColorStateList.valueOf(
                MaterialColors.getColor(container, com.google.android.material.R.attr.colorOnSurfaceVariant)));
        int size = (int) (20 * d);
        header.addView(chevron, new LinearLayout.LayoutParams(size, size));

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(View.GONE);

        container.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        section.header = header;
        section.body = body;
        section.chevron = chevron;
        section.chevron.setTag(value);
        return section;
    }

    private static Section radioSection(LinearLayout container, String title, String iconName, String[] labels,
                                        int selected, OnChoice onChoice) {
        Section section = newSection(container, title, iconName,
                selected >= 0 && selected < labels.length ? labels[selected] : "");
        final List<MaterialRadioButton> group = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            final MaterialRadioButton radio = new MaterialRadioButton(container.getContext());
            radio.setChecked(i == selected);
            group.add(radio);
            LinearLayout row = optionRow(container, radio, labels[i]);
            row.setOnClickListener(v -> {
                AppPreferences.performHaptic(v);
                for (MaterialRadioButton r : group) r.setChecked(r == radio);
                if (onChoice != null) onChoice.onChoice(index);
            });
            ((ViewGroup) section.body).addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return section;
    }

    private static Section checkSection(LinearLayout container, Config c) {
        Section section = newSection(container, c.listTitle, "ic_checks", "");
        ((TextView) section.chevron.getTag()).setVisibility(View.GONE);
        addCheck(container, section, c.updatesFirstLabel, c.updatesFirst, c.onUpdatesFirst);
        addCheck(container, section, c.notInstalledLastLabel, c.notInstalledLast, c.onNotInstalledLast);
        return section;
    }

    private static void addCheck(LinearLayout container, Section section, String label,
                                 boolean checked, OnCheck onCheck) {
        final MaterialCheckBox box = new MaterialCheckBox(container.getContext());
        box.setChecked(checked);
        LinearLayout row = optionRow(container, box, label);
        row.setOnClickListener(v -> {
            AppPreferences.performHaptic(v);
            box.toggle();
            if (onCheck != null) onCheck.onCheck(box.isChecked());
        });
        ((ViewGroup) section.body).addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static LinearLayout optionRow(View themeAnchor, CompoundButton lead, String label) {
        Context context = themeAnchor.getContext();
        float d = context.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        row.setMinimumHeight((int) (44 * d));
        row.setPaddingRelative((int) (4 * d), 0, (int) (12 * d), 0);
        row.setClickable(true);
        row.setFocusable(true);
        lead.setClickable(false);
        lead.setFocusable(false);
        lead.setBackground(null);
        row.addView(lead, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView text = new TextView(context);
        text.setText(label);
        text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        appearance(context, text, com.google.android.material.R.attr.textAppearanceBodyLarge);
        text.setTextColor(MaterialColors.getColor(themeAnchor, com.google.android.material.R.attr.colorOnSurface));
        text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMarginStart((int) (8 * d));
        row.addView(text, tp);
        return row;
    }

    private static void appearance(Context context, TextView text, int attr) {
        TypedValue tv = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, tv, true)) {
            TextViewCompat.setTextAppearance(text, tv.resourceId);
        }
    }
}
