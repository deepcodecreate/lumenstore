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
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;
import com.opensourcefinder.app.R;

public class IconShapeActivity extends BaseActivity {
    private static final int SPAN = 3;
    private String selected;
    private PreviewHolder preview;
    private ShapeAdapter adapter;

    @Override protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        MotionHelper.applyDistinctActivityTransition(this);
        setContentView(R.layout.activity_icon_shape);
        ThemeManager.applyWindow(this);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        selected = AppPreferences.getIconShape(this);

        RecyclerView rv = findViewById(R.id.recyclerView);
        GridLayoutManager glm = new GridLayoutManager(this, SPAN);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return position == 0 ? SPAN : 1; }
        });
        rv.setLayoutManager(glm);
        adapter = new ShapeAdapter();
        rv.setAdapter(adapter);
        MotionHelper.setupListAnimator(rv);
    }

    private int color(View v, int attr, int fallback) {
        return MaterialColors.getColor(v, attr, fallback);
    }

    private Drawable sampleIcon() {
        try {
            Drawable d = getPackageManager().getApplicationIcon(getApplicationInfo());
            Drawable.ConstantState cs = d.getConstantState();
            return cs != null ? cs.newDrawable(getResources()).mutate() : d;
        } catch (Exception e) {
            return null;
        }
    }

    private void select(String id, View source) {
        if (id.equals(selected)) return;
        String old = selected;
        selected = id;
        AppPreferences.setIconShape(this, id);
        AppPreferences.performHaptic(source);
        adapter.notifyItemChanged(indexOf(old) + 1, "sel");
        adapter.notifyItemChanged(indexOf(id) + 1, "sel");
        if (preview != null) preview.bind(true);
    }

    private static int indexOf(String id) {
        for (int i = 0; i < IconShapes.IDS.length; i++) if (IconShapes.IDS[i].equals(id)) return i;
        return 0;
    }

    private final class PreviewHolder extends RecyclerView.ViewHolder {
        final ShapeView halo, hero, rowIcon;
        final ShapeView[] swatches = new ShapeView[5];
        final TextView name;

        PreviewHolder(View v) {
            super(v);
            halo = v.findViewById(R.id.previewHalo);
            hero = v.findViewById(R.id.previewHero);
            rowIcon = v.findViewById(R.id.previewRowIcon);
            name = v.findViewById(R.id.previewName);
            int[] ids = {R.id.previewSw1, R.id.previewSw2, R.id.previewSw3, R.id.previewSw4, R.id.previewSw5};
            for (int i = 0; i < 5; i++) swatches[i] = v.findViewById(ids[i]);
            int primary = color(v, androidx.appcompat.R.attr.colorPrimary, 0xFF6750A4);
            int pc = color(v, com.google.android.material.R.attr.colorPrimaryContainer, 0xFFEADDFF);
            int sc = color(v, com.google.android.material.R.attr.colorSecondaryContainer, 0xFFE8DEF8);
            int tc = color(v, com.google.android.material.R.attr.colorTertiaryContainer, 0xFFFFD8E4);
            int sec = color(v, com.google.android.material.R.attr.colorSecondary, 0xFF625B71);
            halo.setFillColor(sc);
            halo.setIdleSpin(true);
            hero.setFillColor(pc);
            hero.setIcon(sampleIcon());
            rowIcon.setFillColor(pc);
            rowIcon.setIcon(sampleIcon());
            int[] cols = {primary, sec, tc, pc, sc};
            for (int i = 0; i < 5; i++) swatches[i].setFillColor(cols[i]);
        }

        void bind(boolean animate) {
            halo.setShape(selected, animate);
            hero.setShape(selected, animate);
            rowIcon.setShape(selected, animate);
            for (ShapeView s : swatches) s.setShape(selected, animate);
            name.setText(IconShapes.labelRes(selected));
            if (animate && !AppPreferences.isReduceMotion(itemView.getContext())) {
                hero.animate().cancel();
                hero.setScaleX(0.86f);
                hero.setScaleY(0.86f);
                hero.animate().scaleX(1f).scaleY(1f)
                        .setDuration(MotionHelper.getDuration(itemView.getContext(), 260, 480))
                        .setInterpolator(new OvershootInterpolator(2.6f)).start();
            }
        }
    }

    private final class TileHolder extends RecyclerView.ViewHolder {
        final ShapeView swatch;
        final ImageView check;
        final TextView label;
        boolean entered;

        TileHolder(View v) {
            super(v);
            swatch = v.findViewById(R.id.shapeSwatch);
            check = v.findViewById(R.id.shapeCheck);
            label = v.findViewById(R.id.shapeLabel);
            MotionHelper.attachPressScale(v);
        }

        void bind(String id, boolean animateSelection) {
            boolean isSel = id.equals(selected);
            swatch.setShape(id, false);
            swatch.setFillColor(isSel
                    ? color(itemView, androidx.appcompat.R.attr.colorPrimary, 0xFF6750A4)
                    : color(itemView, com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xFF49454F));
            check.setVisibility(isSel ? View.VISIBLE : View.INVISIBLE);
            label.setText(IconShapes.labelRes(id));
            label.setTextColor(color(itemView, isSel ? androidx.appcompat.R.attr.colorPrimary
                    : com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF));
            itemView.setOnClickListener(v -> select(id, v));
            if (animateSelection && isSel && !AppPreferences.isReduceMotion(itemView.getContext())) {
                swatch.setScaleX(0.78f);
                swatch.setScaleY(0.78f);
                swatch.animate().scaleX(1f).scaleY(1f)
                        .setDuration(MotionHelper.getDuration(itemView.getContext(), 240, 460))
                        .setInterpolator(new OvershootInterpolator(3f)).start();
                check.setScaleX(0f);
                check.setScaleY(0f);
                check.animate().scaleX(1f).scaleY(1f).setStartDelay(60)
                        .setDuration(MotionHelper.getDuration(itemView.getContext(), 200, 360))
                        .setInterpolator(new OvershootInterpolator(3.4f)).start();
            }
        }
    }

    private final class ShapeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemViewType(int position) { return position == 0 ? 0 : 1; }
        @Override public int getItemCount() { return IconShapes.IDS.length + 1; }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (type == 0) {
                preview = new PreviewHolder(inflater.inflate(R.layout.item_icon_shape_preview, parent, false));
                return preview;
            }
            return new TileHolder(inflater.inflate(R.layout.item_icon_shape, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof PreviewHolder) {
                ((PreviewHolder) holder).bind(false);
            } else {
                TileHolder h = (TileHolder) holder;
                h.bind(IconShapes.IDS[position - 1], false);
                if (!h.entered) {
                    h.entered = true;
                    MotionHelper.animateEntrance(h.itemView, position / SPAN);
                }
            }
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull java.util.List<Object> payloads) {
            if (!payloads.isEmpty() && holder instanceof TileHolder) {
                ((TileHolder) holder).bind(IconShapes.IDS[position - 1], true);
                return;
            }
            super.onBindViewHolder(holder, position, payloads);
        }
    }
}
