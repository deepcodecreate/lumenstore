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
import android.content.SharedPreferences;
import android.app.Activity;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import com.opensourcefinder.app.R;

public final class ThemeManager {
    private static final String PREFS = "app_theme";
    private static final String KEY = "theme_id";

    public static final class ThemeDef {
        public final String id;
        public final int titleRes;
        public final int darkStyleRes;
        public final int lightStyleRes;
        public final int previewPrimary;
        public final int previewSurface;
        public ThemeDef(String id, int titleRes, int darkStyleRes, int lightStyleRes,
                        int previewPrimary, int previewSurface) {
            this.id = id;
            this.titleRes = titleRes;
            this.darkStyleRes = darkStyleRes;
            this.lightStyleRes = lightStyleRes;
            this.previewPrimary = previewPrimary;
            this.previewSurface = previewSurface;
        }
    }

    public static final ThemeDef[] THEMES = new ThemeDef[] {
            new ThemeDef("ember", R.string.theme_ember, R.style.Theme_OpenSourceFinder_Ember,
                    R.style.Theme_OpenSourceFinder_EmberLight, 0xFFFFB4A2, 0xFF1C1010),
            new ThemeDef("stone", R.string.theme_stone, R.style.Theme_OpenSourceFinder_Stone,
                    R.style.Theme_OpenSourceFinder_StoneLight, 0xFFB0B0B0, 0xFF121212),
            new ThemeDef("dusk", R.string.theme_dusk, R.style.Theme_OpenSourceFinder_Dusk,
                    R.style.Theme_OpenSourceFinder_DuskLight, 0xFF4DD0C8, 0xFF0E1A22),
            new ThemeDef("midnight", R.string.theme_midnight, R.style.Theme_OpenSourceFinder_Midnight,
                    R.style.Theme_OpenSourceFinder_MidnightLight, 0xFF8B9CF7, 0xFF0D0F1A),
            new ThemeDef("sand", R.string.theme_sand, R.style.Theme_OpenSourceFinder_SandDark,
                    R.style.Theme_OpenSourceFinder_Sand, 0xFFC8B89A, 0xFFF5F0E6),
            new ThemeDef("sky", R.string.theme_sky, R.style.Theme_OpenSourceFinder_SkyDark,
                    R.style.Theme_OpenSourceFinder_Sky, 0xFF5BA3C9, 0xFFF0F6FA),
            new ThemeDef("autumn", R.string.theme_autumn, R.style.Theme_OpenSourceFinder_AutumnDark,
                    R.style.Theme_OpenSourceFinder_Autumn, 0xFFE07A3D, 0xFFFFF8F0),
            new ThemeDef("neon", R.string.theme_neon, R.style.Theme_OpenSourceFinder_Neon,
                    R.style.Theme_OpenSourceFinder_NeonLight, 0xFF39FF14, 0xFF0A0A0A),
            new ThemeDef("mint", R.string.theme_mint, R.style.Theme_OpenSourceFinder_Mint,
                    R.style.Theme_OpenSourceFinder_MintLight, 0xFF7FE7C4, 0xFF0B1A18),
            new ThemeDef("rose", R.string.theme_rose, R.style.Theme_OpenSourceFinder_Rose,
                    R.style.Theme_OpenSourceFinder_RoseLight, 0xFFFF8FAB, 0xFF1A0F14),
            new ThemeDef("plum", R.string.theme_plum, R.style.Theme_OpenSourceFinder_Plum,
                    R.style.Theme_OpenSourceFinder_PlumLight, 0xFFD0A8FF, 0xFF180F1F),
            new ThemeDef("ocean", R.string.theme_ocean, R.style.Theme_OpenSourceFinder_Ocean,
                    R.style.Theme_OpenSourceFinder_OceanLight, 0xFF6FC3FF, 0xFF0A141F),
            new ThemeDef("blossom", R.string.theme_blossom, R.style.Theme_OpenSourceFinder_BlossomDark,
                    R.style.Theme_OpenSourceFinder_Blossom, 0xFFB23A6E, 0xFFFFF5F8),
            new ThemeDef("forest", R.string.theme_forest, R.style.Theme_OpenSourceFinder_ForestDark,
                    R.style.Theme_OpenSourceFinder_Forest, 0xFF2F6B3A, 0xFFF3FAF1),
            new ThemeDef("default_teal", R.string.theme_default_teal, R.style.Theme_OpenSourceFinder,
                    R.style.Theme_OpenSourceFinder_Light, 0xFF4FD1C5, 0xFF0F1416),
    };

    private ThemeManager() {}

    public static void applyWindow(android.app.Activity activity) {
        android.view.Window window = activity.getWindow();
        android.util.TypedValue tv = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, tv, true);
        boolean edgeToEdge = activity instanceof BaseActivity && ((BaseActivity) activity).drawsBehindSystemBars();
        if (edgeToEdge) {

            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false);
            window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                window.setNavigationBarContrastEnforced(false);
                window.setStatusBarContrastEnforced(false);
            }
        } else {
            window.setStatusBarColor(tv.data);
            window.setNavigationBarColor(tv.data);
        }
        boolean light = isLightTheme(activity);
        androidx.core.view.WindowInsetsControllerCompat c =
                new androidx.core.view.WindowInsetsControllerCompat(window, window.getDecorView());
        c.setAppearanceLightStatusBars(light);
        c.setAppearanceLightNavigationBars(light);
    }

    public static boolean isLightTheme(Context context) {
        String mode = AppPreferences.getThemeMode(context);
        if (AppPreferences.THEME_MODE_LIGHT.equals(mode)) return true;
        if (AppPreferences.THEME_MODE_DARK.equals(mode)) return false;
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
    }

    public static boolean isLightThemeId(String id) {
        return "sand".equals(id) || "sky".equals(id) || "autumn".equals(id) || "blossom".equals(id) || "forest".equals(id);
    }

    public static void apply(Context context) {
        ThemeDef selected = getTheme(getThemeId(context));
        context.setTheme(isLightTheme(context) ? selected.lightStyleRes : selected.darkStyleRes);

        context.getTheme().applyStyle(R.style.ThemeOverlay_OpenSourceFinder_StrataShapes, true);

        if (context instanceof Activity && DynamicColors.isDynamicColorAvailable()) {
            DynamicColorsOptions.Builder options = new DynamicColorsOptions.Builder();
            options.setContentBasedSource(getPaletteSeed(context));
            DynamicColors.applyToActivityIfAvailable((Activity) context, options.build());
        }

        if (AppPreferences.isPureBlack(context) && !isLightTheme(context)) {
            context.getTheme().applyStyle(R.style.ThemeOverlay_OpenSourceFinder_PureBlack, true);
        }

        float contrast = AppPreferences.getContrastLevel(context);
        if (contrast >= 0.5f) {
            context.getTheme().applyStyle(isLightTheme(context)
                    ? R.style.ThemeOverlay_OpenSourceFinder_ContrastLight
                    : R.style.ThemeOverlay_OpenSourceFinder_ContrastDark, true);
        } else if (contrast <= -0.5f) {
            context.getTheme().applyStyle(isLightTheme(context)
                    ? R.style.ThemeOverlay_OpenSourceFinder_ContrastSoftLight
                    : R.style.ThemeOverlay_OpenSourceFinder_ContrastSoftDark, true);
        }
    }

    public static String signature(Context context) {
        return getThemeId(context) + "|" + AppPreferences.getThemeMode(context)
                + "|" + AppPreferences.getPaletteStyle(context)
                + "|" + AppPreferences.getSeedColor(context)
                + "|" + AppPreferences.isWallpaperColors(context)
                + "|" + AppPreferences.isPureBlack(context)
                + "|" + AppPreferences.getContrastLevel(context)
                + "|" + AppPreferences.getCornerScaleLevel(context)
                + "|" + AppPreferences.getFontScaleLevel(context)
                + "|" + AppPreferences.getMotionStyle(context)
                + "|" + AppPreferences.getIconShape(context)
                + "|" + AppPreferences.isNavLabelsHidden(context);
    }

    public static final class Swatch {
        public int primary, onPrimary, primaryContainer, onPrimaryContainer;
        public int secondaryContainer, tertiary;
        public int surface, surfaceContainerLow, surfaceContainer, surfaceContainerHigh;
        public int onSurface, onSurfaceVariant, outline;
    }

    public static Context previewContext(Context context, ThemeDef def, String paletteStyle) {
        boolean light = isLightTheme(context);
        Context w = new android.view.ContextThemeWrapper(
                context.getApplicationContext(), light ? def.lightStyleRes : def.darkStyleRes);
        w.getTheme().applyStyle(R.style.ThemeOverlay_OpenSourceFinder_StrataShapes, true);
        if (DynamicColors.isDynamicColorAvailable()) {
            DynamicColorsOptions.Builder options = new DynamicColorsOptions.Builder();
            options.setContentBasedSource(paletteSeed(context, def, paletteStyle));
            w = DynamicColors.wrapContextIfAvailable(w, options.build());
        }
        if (AppPreferences.isPureBlack(context) && !light) {
            w.getTheme().applyStyle(R.style.ThemeOverlay_OpenSourceFinder_PureBlack, true);
        }
        return w;
    }

    public static Swatch resolveSwatch(Context context, ThemeDef def) {
        return resolveSwatch(context, def, AppPreferences.getPaletteStyle(context));
    }

    public static Swatch resolveSwatch(Context context, ThemeDef def, String paletteStyle) {
        boolean light = isLightTheme(context);
        Context w = previewContext(context, def, paletteStyle);
        Swatch s = new Swatch();
        s.primary = c(w, androidx.appcompat.R.attr.colorPrimary, def.previewPrimary);
        s.onPrimary = c(w, com.google.android.material.R.attr.colorOnPrimary, Color.WHITE);
        s.primaryContainer = c(w, com.google.android.material.R.attr.colorPrimaryContainer, s.primary);
        s.onPrimaryContainer = c(w, com.google.android.material.R.attr.colorOnPrimaryContainer, s.onPrimary);
        s.secondaryContainer = c(w, com.google.android.material.R.attr.colorSecondaryContainer, s.primaryContainer);
        s.tertiary = c(w, com.google.android.material.R.attr.colorTertiary, s.primary);
        s.surface = c(w, com.google.android.material.R.attr.colorSurface, def.previewSurface);
        s.surfaceContainerLow = c(w, com.google.android.material.R.attr.colorSurfaceContainerLow, s.surface);
        s.surfaceContainer = c(w, com.google.android.material.R.attr.colorSurfaceContainer, s.surface);
        s.surfaceContainerHigh = c(w, com.google.android.material.R.attr.colorSurfaceContainerHigh, s.surfaceContainer);
        s.onSurface = c(w, com.google.android.material.R.attr.colorOnSurface, light ? Color.BLACK : Color.WHITE);
        s.onSurfaceVariant = c(w, com.google.android.material.R.attr.colorOnSurfaceVariant, s.onSurface);
        s.outline = c(w, com.google.android.material.R.attr.colorOutline, s.onSurfaceVariant);
        return s;
    }

    private static int c(Context ctx, int attr, int fallback) {
        return com.google.android.material.color.MaterialColors.getColor(ctx, attr, fallback);
    }

    public static ThemeDef getTheme(String id) {
        for (ThemeDef theme : THEMES) if (theme.id.equals(id)) return theme;
        return THEMES[THEMES.length - 1];
    }

    private static int getPaletteSeed(Context context) {
        return paletteSeed(context, getTheme(getThemeId(context)), AppPreferences.getPaletteStyle(context));
    }

    public static int paletteSeed(Context context, ThemeDef def, String paletteStyle) {
        int color = def.previewPrimary;
        if (def.id.equals(getThemeId(context))) {
            if (AppPreferences.hasSavedSeedColor(context)) color = AppPreferences.getSeedColor(context);
            if (AppPreferences.isWallpaperColors(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                try {
                    WallpaperColors wallpaperColors = WallpaperManager.getInstance(context)
                            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                    if (wallpaperColors != null && wallpaperColors.getPrimaryColor() != null) {
                        color = wallpaperColors.getPrimaryColor().toArgb();
                    }
                } catch (RuntimeException ignored) {

                }
            }
        }
        return tuneSeed(color, paletteStyle);
    }

    private static int tuneSeed(int color, String paletteStyle) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        switch (paletteStyle) {
            case AppPreferences.PALETTE_VIBRANT:
                hsv[1] = Math.min(1f, Math.max(0.72f, hsv[1] * 1.25f));
                break;
            case AppPreferences.PALETTE_EXPRESSIVE:
                hsv[0] = (hsv[0] + 60f) % 360f;
                hsv[1] = Math.max(0.58f, hsv[1]);
                break;
            case AppPreferences.PALETTE_RAINBOW:
                hsv[0] = (hsv[0] + 30f) % 360f;
                hsv[1] = Math.max(0.48f, hsv[1]);
                break;
            case AppPreferences.PALETTE_FRUIT_SALAD:
                hsv[0] = (hsv[0] + 50f) % 360f;
                hsv[1] = Math.max(0.55f, hsv[1]);
                break;
            case AppPreferences.PALETTE_CONTENT:
                hsv[1] *= 0.82f;
                break;
            case AppPreferences.PALETTE_NEUTRAL:
                hsv[1] *= 0.18f;
                break;
            case AppPreferences.PALETTE_MONOCHROME:
                hsv[1] = 0f;
                break;
            case AppPreferences.PALETTE_FIDELITY:
                break;
            case AppPreferences.PALETTE_TONAL_SPOT:
            default:
                hsv[1] *= 0.52f;
                break;
        }
        return Color.HSVToColor(Color.alpha(color), hsv);
    }

    public static String getThemeId(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "default_teal");
    }

    public static void setThemeId(Context context, String id) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, id).apply();
        for (ThemeDef theme : THEMES) {
            if (theme.id.equals(id)) {
                AppPreferences.setSeedColor(context, theme.previewPrimary);
                AppPreferences.setWallpaperColors(context, false);
                break;
            }
        }
    }
}
