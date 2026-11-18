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
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.opensourcefinder.app.R;
import java.util.ArrayList;
import java.util.List;

public class ThemeActivity extends BaseActivity {
    private static final int SPAN_TOTAL = 6;
    private ThemeAdapter themeAdapter;

    private static final String[] PALETTE_IDS = {
            AppPreferences.PALETTE_TONAL_SPOT, AppPreferences.PALETTE_VIBRANT,
            AppPreferences.PALETTE_EXPRESSIVE, AppPreferences.PALETTE_RAINBOW,
            AppPreferences.PALETTE_FRUIT_SALAD, AppPreferences.PALETTE_CONTENT,
            AppPreferences.PALETTE_FIDELITY, AppPreferences.PALETTE_NEUTRAL,
            AppPreferences.PALETTE_MONOCHROME
    };
    private static final int[] PALETTE_LABELS = {
            R.string.palette_tonal_spot, R.string.palette_vibrant, R.string.palette_expressive,
            R.string.palette_rainbow, R.string.palette_fruit_salad, R.string.palette_content,
            R.string.palette_fidelity, R.string.palette_neutral, R.string.palette_monochrome
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        MotionHelper.applyDistinctActivityTransition(this);
        setContentView(R.layout.activity_theme);
        MotionHelper.animateActivityContent(this);
        ThemeManager.applyWindow(this);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        final int columns = getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2;
        final int tileSpan = SPAN_TOTAL / columns;
        RecyclerView rv = findViewById(R.id.recyclerView);
        GridLayoutManager glm = new GridLayoutManager(this, SPAN_TOTAL);
        ThemeAdapter adapter = new ThemeAdapter();
        themeAdapter = adapter;
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                return adapter.isTile(position) ? tileSpan : SPAN_TOTAL;
            }
        });
        rv.setLayoutManager(glm);
        rv.setAdapter(adapter);
    }

    @Override protected void onResume() {
        super.onResume();
        if (themeAdapter != null) themeAdapter.notifyItemChanged(1);
    }

    private void applyNow(View source) {
        AppPreferences.performHaptic(source);
        recreate();
    }

    private final class ThemeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_PREVIEW = 0, TYPE_CONTROLS = 1, TYPE_HEADER = 2, TYPE_THEME = 3;
        private final List<Object> items = new ArrayList<>();
        private final String selectedId = ThemeManager.getThemeId(ThemeActivity.this);
        private final String appliedPalette = AppPreferences.getPaletteStyle(ThemeActivity.this);
        private String palette = appliedPalette;

        ThemeAdapter() {
            items.add(TYPE_PREVIEW);
            items.add(TYPE_CONTROLS);
            items.add(getString(R.string.theme_all_themes));

            for (ThemeManager.ThemeDef t : ThemeManager.THEMES) items.add(t);
        }

        boolean isTile(int position) { return items.get(position) instanceof ThemeManager.ThemeDef; }

        @Override public int getItemViewType(int position) {
            Object item = items.get(position);
            if (item instanceof ThemeManager.ThemeDef) return TYPE_THEME;
            if (item instanceof String) return TYPE_HEADER;
            return (Integer) item;
        }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            switch (type) {
                case TYPE_PREVIEW:
                    FrameLayout holder = new FrameLayout(parent.getContext());
                    holder.setLayoutParams(new RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return new PreviewVH(holder);
                case TYPE_CONTROLS:
                    return new ControlsVH(inflater.inflate(R.layout.item_theme_controls, parent, false));
                case TYPE_HEADER:
                    return new HeaderVH(inflater.inflate(R.layout.item_theme_header, parent, false));
                default:
                    return new ThemeVH(inflater.inflate(R.layout.item_theme, parent, false));
            }
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object item = items.get(position);
            if (holder instanceof PreviewVH) {
                bindPreview((PreviewVH) holder);
            } else if (holder instanceof HeaderVH) {
                ((HeaderVH) holder).text.setText((String) item);
            } else if (holder instanceof ControlsVH) {
                bindControls((ControlsVH) holder);
            } else if (holder instanceof ThemeVH) {
                bindTheme((ThemeVH) holder, (ThemeManager.ThemeDef) item);
            }
        }

        @Override public int getItemCount() { return items.size(); }

        private void bindPreview(PreviewVH h) {
            Context previewContext = ThemeManager.previewContext(
                    ThemeActivity.this, ThemeManager.getTheme(selectedId), palette);
            h.container.removeAllViews();
            View content = LayoutInflater.from(previewContext).inflate(R.layout.item_theme_preview, h.container, false);
            content.setClickable(false);
            content.setFocusable(false);
            content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            h.container.addView(content);
        }

        private void bindControls(ControlsVH h) {

            String mode = AppPreferences.getThemeMode(ThemeActivity.this);
            int checkedId = AppPreferences.THEME_MODE_LIGHT.equals(mode) ? R.id.modeLight
                    : AppPreferences.THEME_MODE_DARK.equals(mode) ? R.id.modeDark : R.id.modeSystem;
            h.modeGroup.clearOnButtonCheckedListeners();
            h.modeGroup.check(checkedId);
            h.modeGroup.addOnButtonCheckedListener((group, id, isChecked) -> {
                if (!isChecked) return;
                String next = id == R.id.modeLight ? AppPreferences.THEME_MODE_LIGHT
                        : id == R.id.modeDark ? AppPreferences.THEME_MODE_DARK
                        : AppPreferences.THEME_MODE_SYSTEM;
                if (next.equals(AppPreferences.getThemeMode(ThemeActivity.this))) return;
                AppPreferences.setThemeMode(ThemeActivity.this, next);
                applyNow(group);
            });

            h.paletteGroup.setOnCheckedStateChangeListener(null);
            h.paletteGroup.removeAllViews();
            String currentStyle = palette;
            LayoutInflater inflater = LayoutInflater.from(ThemeActivity.this);
            for (int i = 0; i < PALETTE_IDS.length; i++) {
                Chip chip = (Chip) inflater.inflate(R.layout.item_theme_chip, h.paletteGroup, false);
                chip.setId(View.generateViewId());
                chip.setText(PALETTE_LABELS[i]);
                chip.setTag(PALETTE_IDS[i]);
                chip.setChecked(PALETTE_IDS[i].equals(currentStyle));
                h.paletteGroup.addView(chip);
            }
            h.paletteGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty()) return;
                Chip chip = group.findViewById(checkedIds.get(0));
                if (chip == null || !(chip.getTag() instanceof String)) return;
                String style = (String) chip.getTag();
                if (style.equals(palette)) return;
                AppPreferences.performHaptic(group);
                AppPreferences.setPaletteStyle(ThemeActivity.this, style);
                palette = style;
                applyNow(group);
            });

            int pc = MaterialColors.getColor(h.itemView, com.google.android.material.R.attr.colorPrimaryContainer, Color.GRAY);
            int sc = MaterialColors.getColor(h.itemView, com.google.android.material.R.attr.colorSecondaryContainer, Color.LTGRAY);
            String shapeId = AppPreferences.getIconShape(ThemeActivity.this);
            h.iconShapeHalo.setFillColor(sc);
            h.iconShapeHalo.setShape(shapeId, false);
            h.iconShapeHalo.setIdleSpin(true);
            h.iconShapePreview.setFillColor(pc);
            h.iconShapePreview.setShape(shapeId, false);
            try {
                android.graphics.drawable.Drawable d = getPackageManager().getApplicationIcon(getApplicationInfo());
                android.graphics.drawable.Drawable.ConstantState cs = d.getConstantState();
                h.iconShapePreview.setIcon(cs != null ? cs.newDrawable(getResources()).mutate() : d);
            } catch (Exception ignored) { }
            h.iconShapeName.setText(IconShapes.labelRes(shapeId));
            h.rowIconShape.setOnClickListener(v -> {
                AppPreferences.performHaptic(v);
                startActivity(new android.content.Intent(ThemeActivity.this, IconShapeActivity.class));
            });

            bindSwitch(h.rowWallpaper, "ic_color_swatch", R.string.settings_wallpaper_colors,
                    R.string.settings_wallpaper_colors_subtitle,
                    AppPreferences.isWallpaperColors(ThemeActivity.this), checked -> {
                        AppPreferences.setWallpaperColors(ThemeActivity.this, checked);
                    });

            bindSwitch(h.rowPureBlack, "ic_moon", R.string.settings_pure_black,
                    R.string.settings_pure_black_subtitle,
                    AppPreferences.isPureBlack(ThemeActivity.this), checked -> {
                        AppPreferences.setPureBlack(ThemeActivity.this, checked);
                    });
            ImageView liveIcon = h.rowLiveBackground.findViewById(R.id.rowIcon);
            TextView liveTitle = h.rowLiveBackground.findViewById(R.id.rowTitle);
            TextView liveSubtitle = h.rowLiveBackground.findViewById(R.id.rowSubtitle);
            liveIcon.setImageResource(R.drawable.ic_settings_live_background);
            liveTitle.setText(R.string.settings_live_background);
            liveSubtitle.setVisibility(View.VISIBLE);
            liveSubtitle.setText(AppPreferences.isLiveBackground(ThemeActivity.this)
                    ? getString(LiveBackgroundStyles.labelRes(AppPreferences.getLiveBackgroundStyle(ThemeActivity.this)))
                    : getString(R.string.live_bg_off));
            h.rowLiveBackground.setOnClickListener(v -> {
                AppPreferences.performHaptic(v);
                startActivity(new android.content.Intent(ThemeActivity.this, LiveBackgroundActivity.class));
            });
            boolean dark = !ThemeManager.isLightTheme(ThemeActivity.this);
            h.rowPureBlack.setAlpha(dark ? 1f : 0.45f);
            h.rowPureBlack.setEnabled(dark);
            h.pureBlackSwitch.setEnabled(dark);
        }

        private void bindSwitch(View row, String icon, int title, int subtitle, boolean initial,
                                java.util.function.Consumer<Boolean> onChange) {
            ImageView iv = row.findViewById(R.id.rowIcon);
            TextView tv = row.findViewById(R.id.rowTitle);
            TextView sub = row.findViewById(R.id.rowSubtitle);
            MaterialSwitch toggle = row.findViewById(R.id.rowSwitch);
            iv.setImageResource(TablerIconRes.get(ThemeActivity.this, icon));
            iv.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(iv,
                    com.google.android.material.R.attr.colorOnPrimaryContainer, Color.TRANSPARENT)));
            tv.setText(title);
            sub.setText(subtitle);
            toggle.setSaveEnabled(false);
            toggle.setOnCheckedChangeListener(null);
            toggle.setChecked(initial);
            toggle.setOnCheckedChangeListener((b, checked) -> {
                onChange.accept(checked);
                applyNow(b);
            });
            row.setOnClickListener(v -> { if (row.isEnabled()) toggle.setChecked(!toggle.isChecked()); });
        }

        private void bindTheme(ThemeVH h, ThemeManager.ThemeDef t) {
            ThemeManager.Swatch s = ThemeManager.resolveSwatch(ThemeActivity.this, t, palette);
            boolean selected = t.id.equals(selectedId);
            float d = getResources().getDisplayMetrics().density;

            h.title.setText(t.titleRes);
            h.preview.setBackgroundColor(s.surface);
            h.barDot.setBackground(shape(s.primary, 10 * d, true));
            h.bar.setBackground(shape(androidx.core.graphics.ColorUtils.setAlphaComponent(s.onSurface, 200), 4 * d, false));
            h.action1.setBackground(shape(s.surfaceContainerHigh, 9 * d, true));
            h.action2.setBackground(shape(s.surfaceContainerHigh, 9 * d, true));
            h.search.setBackground(shape(s.primaryContainer, 16 * d, false));
            h.heroBlob.setBackground(shape(androidx.core.graphics.ColorUtils.setAlphaComponent(s.tertiary, 150), 10 * d, false));
            h.heroLine.setBackground(shape(s.onPrimaryContainer, 4 * d, false));
            h.heroLineSub.setBackground(shape(androidx.core.graphics.ColorUtils.setAlphaComponent(s.onPrimaryContainer, 150), 3 * d, false));
            int line = androidx.core.graphics.ColorUtils.setAlphaComponent(s.onSurface, 210);
            int lineSub = androidx.core.graphics.ColorUtils.setAlphaComponent(s.onSurfaceVariant, 190);
            h.cardOne.setBackground(shape(s.surfaceContainer, 12 * d, false));
            h.cardTwo.setBackground(shape(s.surfaceContainer, 12 * d, false));
            h.iconOne.setBackground(shape(s.primaryContainer, 6 * d, false));
            h.iconTwo.setBackground(shape(s.secondaryContainer, 6 * d, false));
            h.lineOne.setBackground(shape(line, 3 * d, false));
            h.lineTwo.setBackground(shape(line, 3 * d, false));
            h.lineOneSub.setBackground(shape(lineSub, 2 * d, false));
            h.lineTwoSub.setBackground(shape(lineSub, 2 * d, false));
            h.btnOne.setBackground(shape(s.primary, 6 * d, false));
            h.btnTwo.setBackground(shape(s.secondaryContainer, 6 * d, false));
            h.nav.setBackground(shape(s.surfaceContainerHigh, 15 * d, false));
            h.navIndicator.setBackground(shape(s.primaryContainer, 10 * d, false));
            h.navDot1.setBackground(shape(lineSub, 4.5f * d, true));
            h.navDot2.setBackground(shape(lineSub, 4.5f * d, true));
            h.navDot3.setBackground(shape(lineSub, 4.5f * d, true));
            h.fab.setBackground(shape(s.primary, 7 * d, false));

            h.card.setCardBackgroundColor(s.surfaceContainerLow);
            h.title.setTextColor(s.onSurface);
            int primaryNow = MaterialColors.getColor(h.card, androidx.appcompat.R.attr.colorPrimary, s.primary);
            int outlineNow = MaterialColors.getColor(h.card, com.google.android.material.R.attr.colorOutlineVariant, s.outline);
            h.card.setStrokeColor(selected ? primaryNow : outlineNow);
            h.card.setStrokeWidth((int) ((selected ? 3 : 1) * d));
            if (selected) {
                h.check.setVisibility(View.VISIBLE);
                h.check.setBackground(shape(s.primary, 12 * d, true));
                h.checkIcon.setImageTintList(ColorStateList.valueOf(s.onPrimary));
            } else {
                h.check.setVisibility(View.GONE);
            }

            h.card.setOnClickListener(v -> {
                if (selected) {
                    if (!palette.equals(appliedPalette)) applyNow(v);
                    return;
                }
                ThemeManager.setThemeId(ThemeActivity.this, t.id);
                applyNow(v);
            });
        }

        private GradientDrawable shape(int color, float radiusPx, boolean oval) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(oval ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE);
            g.setColor(color);
            if (!oval) g.setCornerRadius(radiusPx);
            return g;
        }

        class PreviewVH extends RecyclerView.ViewHolder {
            final FrameLayout container;
            PreviewVH(FrameLayout v) { super(v); container = v; }
        }

        class HeaderVH extends RecyclerView.ViewHolder {
            final TextView text;
            HeaderVH(View v) { super(v); text = (TextView) v; }
        }

        class ControlsVH extends RecyclerView.ViewHolder {
            final MaterialButtonToggleGroup modeGroup;
            final ChipGroup paletteGroup;
            final View rowIconShape;
            final ShapeView iconShapeHalo, iconShapePreview;
            final TextView iconShapeName;
            final View rowWallpaper, rowPureBlack, rowLiveBackground;
            final MaterialSwitch pureBlackSwitch;
            ControlsVH(View v) {
                super(v);
                modeGroup = v.findViewById(R.id.modeGroup);
                paletteGroup = v.findViewById(R.id.paletteGroup);
                rowIconShape = v.findViewById(R.id.rowIconShape);
                iconShapeHalo = v.findViewById(R.id.iconShapePreviewHalo);
                iconShapePreview = v.findViewById(R.id.iconShapePreview);
                iconShapeName = v.findViewById(R.id.iconShapeName);
                rowWallpaper = v.findViewById(R.id.rowWallpaper);
                rowPureBlack = v.findViewById(R.id.rowPureBlack);
                rowLiveBackground = v.findViewById(R.id.rowLiveBackground);
                pureBlackSwitch = rowPureBlack.findViewById(R.id.rowSwitch);
            }
        }

        class ThemeVH extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final View preview, bar, barDot, search, cardOne, cardTwo, iconOne, iconTwo, nav, navIndicator, check;
            final View action1, action2, heroBlob, heroLine, heroLineSub, lineOne, lineOneSub, lineTwo, lineTwoSub, btnOne, btnTwo, navDot1, navDot2, navDot3, fab;
            final ImageView checkIcon;
            final TextView title;
            ThemeVH(View v) {
                super(v);
                card = v.findViewById(R.id.themeCard);
                preview = v.findViewById(R.id.themePreview);
                bar = v.findViewById(R.id.themeBar);
                barDot = v.findViewById(R.id.themeBarDot);
                search = v.findViewById(R.id.themeSearch);
                cardOne = v.findViewById(R.id.themeCardOne);
                cardTwo = v.findViewById(R.id.themeCardTwo);
                iconOne = v.findViewById(R.id.themeIconOne);
                iconTwo = v.findViewById(R.id.themeIconTwo);
                nav = v.findViewById(R.id.themeNav);
                navIndicator = v.findViewById(R.id.themeNavIndicator);
                check = v.findViewById(R.id.themeCheck);
                checkIcon = v.findViewById(R.id.themeCheckIcon);
                title = v.findViewById(R.id.themeTitle);
                action1 = v.findViewById(R.id.themeAction1);
                action2 = v.findViewById(R.id.themeAction2);
                heroBlob = v.findViewById(R.id.themeHeroBlob);
                heroLine = v.findViewById(R.id.themeHeroLine);
                heroLineSub = v.findViewById(R.id.themeHeroLineSub);
                lineOne = v.findViewById(R.id.themeLineOne);
                lineOneSub = v.findViewById(R.id.themeLineOneSub);
                lineTwo = v.findViewById(R.id.themeLineTwo);
                lineTwoSub = v.findViewById(R.id.themeLineTwoSub);
                btnOne = v.findViewById(R.id.themeBtnOne);
                btnTwo = v.findViewById(R.id.themeBtnTwo);
                navDot1 = v.findViewById(R.id.themeNavDot1);
                navDot2 = v.findViewById(R.id.themeNavDot2);
                navDot3 = v.findViewById(R.id.themeNavDot3);
                fab = v.findViewById(R.id.themeFab);
            }
        }
    }
}
