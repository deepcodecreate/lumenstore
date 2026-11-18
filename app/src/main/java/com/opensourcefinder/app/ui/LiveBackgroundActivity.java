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

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.opensourcefinder.app.R;

public class LiveBackgroundActivity extends BaseActivity {
    private static final int SPAN = 2;
    private static final int TYPE_PREVIEW = 0, TYPE_TOGGLE = 1, TYPE_STYLE = 2;
    private static final int HEADER_COUNT = 2;

    private String selected;
    private RecyclerView.Adapter<RecyclerView.ViewHolder> adapter;
    private PreviewHolder previewHolder;
    private ToggleHolder toggleHolder;

    @Override protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        MotionHelper.applyDistinctActivityTransition(this);
        setContentView(R.layout.activity_live_background);
        ThemeManager.applyWindow(this);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        selected = LiveBackgroundStyles.normalize(AppPreferences.getLiveBackgroundStyle(this));

        RecyclerView rv = findViewById(R.id.recyclerView);
        GridLayoutManager glm = new GridLayoutManager(this, SPAN);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return position < HEADER_COUNT ? SPAN : 1; }
        });
        rv.setLayoutManager(glm);
        adapter = new StyleAdapter();
        rv.setAdapter(adapter);
        rv.setItemAnimator(null);
    }

    private void select(String id, View source) {
        if (id.equals(selected)) return;
        String old = selected;
        selected = id;
        AppPreferences.setLiveBackgroundStyle(this, id);
        if (!AppPreferences.isLiveBackground(this)) {
            AppPreferences.setLiveBackground(this, true);
            if (toggleHolder != null) toggleHolder.bind();
        }
        AppPreferences.performHaptic(source);
        adapter.notifyItemChanged(indexOf(old) + HEADER_COUNT, "sel");
        adapter.notifyItemChanged(indexOf(id) + HEADER_COUNT, "sel");
        if (previewHolder != null) previewHolder.bind();
    }

    private static int indexOf(String id) {
        for (int i = 0; i < LiveBackgroundStyles.IDS.length; i++) {
            if (LiveBackgroundStyles.IDS[i].equals(id)) return i;
        }
        return 0;
    }

    private final class PreviewHolder extends RecyclerView.ViewHolder {
        final LiveBackgroundView view;
        final TextView name;

        PreviewHolder(View v) {
            super(v);
            view = v.findViewById(R.id.previewView);
            name = v.findViewById(R.id.previewName);
            bind();
        }

        void bind() {
            view.setPreviewStyle(selected);
            name.setText(LiveBackgroundStyles.labelRes(selected));
        }
    }

    private final class ToggleHolder extends RecyclerView.ViewHolder {
        final MaterialSwitch toggle;

        ToggleHolder(View v) {
            super(v);
            View row = v.findViewById(R.id.rowToggle);
            ImageView icon = row.findViewById(R.id.rowIcon);
            TextView title = row.findViewById(R.id.rowTitle);
            TextView subtitle = row.findViewById(R.id.rowSubtitle);
            toggle = row.findViewById(R.id.rowSwitch);
            icon.setImageResource(R.drawable.ic_settings_live_background);
            icon.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(icon,
                    com.google.android.material.R.attr.colorOnPrimaryContainer, Color.TRANSPARENT)));
            title.setText(R.string.settings_live_background);
            subtitle.setText(R.string.live_bg_toggle_subtitle);
            toggle.setSaveEnabled(false);
            row.setOnClickListener(x -> {
                toggle.setChecked(!toggle.isChecked());
                AppPreferences.performHaptic(toggle);
            });
            bind();
        }

        void bind() {
            toggle.setOnCheckedChangeListener(null);
            toggle.setChecked(AppPreferences.isLiveBackground(LiveBackgroundActivity.this));
            toggle.setOnCheckedChangeListener((b, checked) ->
                    AppPreferences.setLiveBackground(LiveBackgroundActivity.this, checked));
        }
    }

    private final class StyleHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final LiveBackgroundView view;
        final View check;
        final TextView label;

        StyleHolder(View v) {
            super(v);
            card = v.findViewById(R.id.styleCard);
            view = v.findViewById(R.id.styleView);
            check = v.findViewById(R.id.styleCheck);
            label = v.findViewById(R.id.styleLabel);
            MotionHelper.attachPressScale(v);
        }

        void bind(String id) {
            boolean isSelected = id.equals(selected);
            view.setPreviewStyle(id);
            label.setText(LiveBackgroundStyles.labelRes(id));
            label.setTextColor(MaterialColors.getColor(itemView, isSelected
                    ? androidx.appcompat.R.attr.colorPrimary
                    : com.google.android.material.R.attr.colorOnSurface, Color.WHITE));
            card.setStrokeWidth(isSelected ? (int) (2 * getResources().getDisplayMetrics().density) : 0);
            check.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            itemView.setOnClickListener(v -> select(id, v));
        }
    }

    private final class StyleAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemViewType(int position) {
            return position == 0 ? TYPE_PREVIEW : position == 1 ? TYPE_TOGGLE : TYPE_STYLE;
        }

        @Override public int getItemCount() { return LiveBackgroundStyles.IDS.length + HEADER_COUNT; }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (type == TYPE_PREVIEW) {
                previewHolder = new PreviewHolder(inflater.inflate(R.layout.item_live_bg_preview, parent, false));
                return previewHolder;
            }
            if (type == TYPE_TOGGLE) {
                toggleHolder = new ToggleHolder(inflater.inflate(R.layout.item_live_bg_toggle, parent, false));
                return toggleHolder;
            }
            return new StyleHolder(inflater.inflate(R.layout.item_live_bg_style, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof StyleHolder) {
                ((StyleHolder) holder).bind(LiveBackgroundStyles.IDS[position - HEADER_COUNT]);
            }
        }
    }
}
