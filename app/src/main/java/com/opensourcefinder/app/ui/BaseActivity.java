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
import android.content.res.Configuration;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.annotation.NonNull;
import com.google.android.material.shape.CornerSize;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.shape.Shapeable;
import com.opensourcefinder.app.R;

public abstract class BaseActivity extends AppCompatActivity {
    private boolean cornerScaleListenerInstalled;
    private String appliedThemeSignature;
    private LiveBackgroundView liveBackground;
    private boolean cornersWereScaled;

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appliedThemeSignature = ThemeManager.signature(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (appliedThemeSignature != null
                && !appliedThemeSignature.equals(ThemeManager.signature(this))) {
            recreate();
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(wrapWithFontScale(newBase));
    }

    public static Context wrapWithFontScale(Context context) {
        float scale = AppPreferences.getFontScale(context);
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.fontScale = scale;
        return context.createConfigurationContext(configuration);
    }

    protected boolean wantsLiveBackground() {
        return true;
    }

    public boolean drawsBehindSystemBars() {
        return wantsLiveBackground();
    }

    protected boolean handlesNavigationBarInset() {
        return false;
    }

    private void installSystemBarInsets(@NonNull ViewGroup content) {
        if (!drawsBehindSystemBars()) return;
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottom = Math.max(bars.bottom, ime.bottom);
            for (int i = 0; i < content.getChildCount(); i++) {
                View child = content.getChildAt(i);
                if (child == liveBackground || child.getId() == R.id.bottomNav) continue;

                boolean hostsNav = handlesNavigationBarInset() || child.findViewById(R.id.bottomNav) != null;
                child.setPadding(bars.left, bars.top, bars.right, hostsNav ? ime.bottom : bottom);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(content);
    }

    private void installLiveBackground() {
        if (!wantsLiveBackground()) return;
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null) return;
        if (liveBackground == null) liveBackground = new LiveBackgroundView(this);
        if (liveBackground.getParent() != content) {
            if (liveBackground.getParent() instanceof ViewGroup) {
                ((ViewGroup) liveBackground.getParent()).removeView(liveBackground);
            }
            content.addView(liveBackground, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        installSystemBarInsets(content);
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        installLiveBackground();
        View decor = getWindow().getDecorView();
        if (!cornerScaleListenerInstalled) {

            decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                float scale = AppPreferences.getCornerScale(this);
                if (scale == 1f && !cornersWereScaled) return;
                applyCornerScale(decor, scale);
                cornersWereScaled = scale != 1f;
            });
            cornerScaleListenerInstalled = true;
        }
        float initial = AppPreferences.getCornerScale(this);
        if (initial != 1f || cornersWereScaled) {
            applyCornerScale(decor, initial);
            cornersWereScaled = initial != 1f;
        }
    }

    private static void applyCornerScale(View view, float scale) {
        if (view instanceof Shapeable) {
            Shapeable shapeable = (Shapeable) view;
            Object stored = view.getTag(R.id.tag_strata_original_shape_appearance);
            ShapeAppearanceModel original = stored instanceof ShapeAppearanceModel
                    ? (ShapeAppearanceModel) stored : shapeable.getShapeAppearanceModel();
            if (stored == null) view.setTag(R.id.tag_strata_original_shape_appearance, original);

            Object previousScale = view.getTag(R.id.tag_strata_corner_scale);
            if (!(previousScale instanceof Float) || ((Float) previousScale) != scale) {
                shapeable.setShapeAppearanceModel(original.toBuilder()
                        .setTopLeftCornerSize(new ScaledCornerSize(original.getTopLeftCornerSize(), scale))
                        .setTopRightCornerSize(new ScaledCornerSize(original.getTopRightCornerSize(), scale))
                        .setBottomRightCornerSize(new ScaledCornerSize(original.getBottomRightCornerSize(), scale))
                        .setBottomLeftCornerSize(new ScaledCornerSize(original.getBottomLeftCornerSize(), scale))
                        .build());
                view.setTag(R.id.tag_strata_corner_scale, scale);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyCornerScale(group.getChildAt(i), scale);
            }
        }
    }

    private static final class ScaledCornerSize implements CornerSize {
        private final CornerSize source;
        private final float scale;

        ScaledCornerSize(CornerSize source, float scale) {
            this.source = source;
            this.scale = scale;
        }

        @Override
        public float getCornerSize(@NonNull RectF bounds) {
            return source.getCornerSize(bounds) * scale;
        }
    }
}
