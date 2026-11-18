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
import android.view.HapticFeedbackConstants;
import android.view.View;

public final class AppPreferences {
    private static final String PREFS = "app_preferences";
    private static final String KEY_FONT_SCALE_LEVEL = "font_scale_level";
    private static final String KEY_REDUCE_MOTION = "reduce_motion";
    private static final String KEY_MOTION_STYLE = "motion_style";
    private static final String KEY_HAPTIC_FEEDBACK = "haptic_feedback";
    private static final String KEY_LIVE_BACKGROUND = "live_background";
    private static final String KEY_LIVE_BACKGROUND_STYLE = "live_background_style";
    private static final String KEY_NAV_HIDE_LABELS = "nav_hide_labels";
    private static final String KEY_WELCOME_DONE = "welcome_done";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_WALLPAPER_COLORS = "wallpaper_colors";
    private static final String KEY_PALETTE_STYLE = "palette_style";
    private static final String KEY_SEED_COLOR = "seed_color";
    private static final String KEY_CONTRAST_LEVEL = "contrast_level";
    private static final String KEY_PURE_BLACK = "pure_black";
    private static final String KEY_CORNER_SCALE = "corner_scale";
    private static final String KEY_DEFAULT_TAB = "default_tab";
    private static final String KEY_AUTO_CHECK_UPDATES = "auto_check_updates_on_launch";
    private static final String KEY_WIFI_ONLY_BACKGROUND_SYNC = "wifi_only_background_sync";
    private static final String KEY_BACKGROUND_SYNC_ENABLED = "background_sync_enabled";
    private static final String KEY_LAZY_INDEX_ENABLED = "lazy_index_enabled";
    private static final String KEY_SAVE_DOWNLOADS = "save_downloads_history";
    private static final String KEY_KEEP_APK = "keep_apk_after_install";
    private static final String KEY_HOME_SORT = "home_sort_mode";
    private static final String KEY_HOME_UPDATES_FIRST = "home_updates_first";
    private static final String KEY_HOME_NOT_INSTALLED_LAST = "home_not_installed_last";
    private static final String KEY_DOWNLOADS_SORT = "downloads_sort_mode";
    private static final String KEY_ICON_SHAPE = "icon_shape";

    public static final float[] FONT_SCALE_STEPS = {0.9f, 1.0f, 1.15f, 1.3f};
    public static final float[] CORNER_SCALE_STEPS = {0.8f, 1.0f, 1.2f};
    public static final int FONT_SCALE_DEFAULT_LEVEL = 1;

    public static final String THEME_MODE_SYSTEM = "system";
    public static final String THEME_MODE_LIGHT = "light";
    public static final String THEME_MODE_DARK = "dark";

    public static final String MOTION_EXPRESSIVE = "expressive";
    public static final String MOTION_STANDARD = "standard";
    public static final String MOTION_REDUCED = "reduced";

    public static final String PALETTE_TONAL_SPOT = "tonal_spot";
    public static final String PALETTE_VIBRANT = "vibrant";
    public static final String PALETTE_EXPRESSIVE = "expressive";
    public static final String PALETTE_RAINBOW = "rainbow";
    public static final String PALETTE_FRUIT_SALAD = "fruit_salad";
    public static final String PALETTE_CONTENT = "content";
    public static final String PALETTE_FIDELITY = "fidelity";
    public static final String PALETTE_NEUTRAL = "neutral";
    public static final String PALETTE_MONOCHROME = "monochrome";

    public static final String[] ICON_SHAPES = IconShapes.IDS;

    public static final String TAB_HOME = "tab_home";
    public static final String TAB_EXPLORE = "tab_explore";
    public static final String TAB_SEARCH = "tab_search";
    public static final String TAB_DOWNLOADS = "tab_downloads";
    public static final String TAB_SETTINGS = "tab_settings";
    public static final String TAB_GITHUB = "tab_github";

    private AppPreferences() {}

    public static String getThemeMode(Context context) {
        String mode = prefs(context).getString(KEY_THEME_MODE, THEME_MODE_SYSTEM);
        if (!THEME_MODE_SYSTEM.equals(mode) && !THEME_MODE_LIGHT.equals(mode)
                && !THEME_MODE_DARK.equals(mode)) return THEME_MODE_SYSTEM;
        return mode;
    }

    public static void setThemeMode(Context context, String mode) {
        if (!THEME_MODE_SYSTEM.equals(mode) && !THEME_MODE_LIGHT.equals(mode)
                && !THEME_MODE_DARK.equals(mode)) return;
        prefs(context).edit().putString(KEY_THEME_MODE, mode).apply();
    }

    public static boolean isWallpaperColors(Context context) {
        return prefs(context).getBoolean(KEY_WALLPAPER_COLORS, true);
    }

    public static void setWallpaperColors(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_WALLPAPER_COLORS, enabled).apply();
    }

    public static String getPaletteStyle(Context context) {
        String style = prefs(context).getString(KEY_PALETTE_STYLE, PALETTE_TONAL_SPOT);
        if (PALETTE_TONAL_SPOT.equals(style) || PALETTE_VIBRANT.equals(style)
                || PALETTE_EXPRESSIVE.equals(style) || PALETTE_RAINBOW.equals(style)
                || PALETTE_FRUIT_SALAD.equals(style) || PALETTE_CONTENT.equals(style)
                || PALETTE_FIDELITY.equals(style) || PALETTE_NEUTRAL.equals(style)
                || PALETTE_MONOCHROME.equals(style)) return style;
        return PALETTE_TONAL_SPOT;
    }

    public static void setPaletteStyle(Context context, String style) {
        if (PALETTE_TONAL_SPOT.equals(style) || PALETTE_VIBRANT.equals(style)
                || PALETTE_EXPRESSIVE.equals(style) || PALETTE_RAINBOW.equals(style)
                || PALETTE_FRUIT_SALAD.equals(style) || PALETTE_CONTENT.equals(style)
                || PALETTE_FIDELITY.equals(style) || PALETTE_NEUTRAL.equals(style)
                || PALETTE_MONOCHROME.equals(style)) {
            prefs(context).edit().putString(KEY_PALETTE_STYLE, style).apply();
        }
    }

    public static int getSeedColor(Context context) {
        return prefs(context).getInt(KEY_SEED_COLOR, 0xFF4FD1C5);
    }

    public static boolean hasSavedSeedColor(Context context) {
        return prefs(context).contains(KEY_SEED_COLOR);
    }

    public static void setSeedColor(Context context, int color) {
        prefs(context).edit().putInt(KEY_SEED_COLOR, color).apply();
    }

    public static float getContrastLevel(Context context) {
        return Math.max(-1f, Math.min(1f, prefs(context).getFloat(KEY_CONTRAST_LEVEL, 0f)));
    }

    public static void setContrastLevel(Context context, float level) {
        prefs(context).edit().putFloat(KEY_CONTRAST_LEVEL, Math.max(-1f, Math.min(1f, level))).apply();
    }

    public static boolean isPureBlack(Context context) {
        return prefs(context).getBoolean(KEY_PURE_BLACK, false);
    }

    public static void setPureBlack(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_PURE_BLACK, enabled).apply();
    }

    public static int getCornerScaleLevel(Context context) {
        int level = prefs(context).getInt(KEY_CORNER_SCALE, 1);
        return level < 0 || level >= CORNER_SCALE_STEPS.length ? 1 : level;
    }

    public static float getCornerScale(Context context) {
        return CORNER_SCALE_STEPS[getCornerScaleLevel(context)];
    }

    public static void setCornerScaleLevel(Context context, int level) {
        if (level < 0 || level >= CORNER_SCALE_STEPS.length) return;
        prefs(context).edit().putInt(KEY_CORNER_SCALE, level).apply();
    }

    public static boolean isWelcomeDone(Context context) {
        return prefs(context).getBoolean(KEY_WELCOME_DONE, false);
    }

    public static void setWelcomeDone(Context context) {
        prefs(context).edit().putBoolean(KEY_WELCOME_DONE, true).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int getFontScaleLevel(Context context) {
        int level = prefs(context).getInt(KEY_FONT_SCALE_LEVEL, FONT_SCALE_DEFAULT_LEVEL);
        if (level < 0 || level >= FONT_SCALE_STEPS.length) return FONT_SCALE_DEFAULT_LEVEL;
        return level;
    }

    public static void setFontScaleLevel(Context context, int level) {
        if (level < 0 || level >= FONT_SCALE_STEPS.length) return;
        prefs(context).edit().putInt(KEY_FONT_SCALE_LEVEL, level).apply();
    }

    public static float getFontScale(Context context) {
        return FONT_SCALE_STEPS[getFontScaleLevel(context)];
    }

    public static boolean isReduceMotion(Context context) {
        return MOTION_REDUCED.equals(getMotionStyle(context));
    }

    public static void setReduceMotion(Context context, boolean enabled) {
        setMotionStyle(context, enabled ? MOTION_REDUCED : MOTION_EXPRESSIVE);
    }

    public static String getMotionStyle(Context context) {
        String style = prefs(context).getString(KEY_MOTION_STYLE, null);
        if (MOTION_EXPRESSIVE.equals(style) || MOTION_STANDARD.equals(style)
                || MOTION_REDUCED.equals(style)) return style;
        return prefs(context).getBoolean(KEY_REDUCE_MOTION, false)
                ? MOTION_REDUCED : MOTION_EXPRESSIVE;
    }

    public static void setMotionStyle(Context context, String style) {
        if (!MOTION_EXPRESSIVE.equals(style) && !MOTION_STANDARD.equals(style)
                && !MOTION_REDUCED.equals(style)) return;
        prefs(context).edit()
                .putString(KEY_MOTION_STYLE, style)
                .putBoolean(KEY_REDUCE_MOTION, MOTION_REDUCED.equals(style))
                .apply();
    }

    public static boolean isNavLabelsHidden(Context context) {
        return prefs(context).getBoolean(KEY_NAV_HIDE_LABELS, false);
    }

    public static void setNavLabelsHidden(Context context, boolean hidden) {
        prefs(context).edit().putBoolean(KEY_NAV_HIDE_LABELS, hidden).apply();
    }

    public static boolean isLiveBackground(Context context) {
        return prefs(context).getBoolean(KEY_LIVE_BACKGROUND, true);
    }

    public static void setLiveBackground(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_LIVE_BACKGROUND, enabled).apply();
    }

    public static String getLiveBackgroundStyle(Context context) {
        return prefs(context).getString(KEY_LIVE_BACKGROUND_STYLE, LiveBackgroundStyles.CLASSIC);
    }

    public static void setLiveBackgroundStyle(Context context, String style) {
        prefs(context).edit().putString(KEY_LIVE_BACKGROUND_STYLE, LiveBackgroundStyles.normalize(style)).apply();
    }

    public static void registerChangeListener(Context context, SharedPreferences.OnSharedPreferenceChangeListener l) {
        prefs(context).registerOnSharedPreferenceChangeListener(l);
    }

    public static void unregisterChangeListener(Context context, SharedPreferences.OnSharedPreferenceChangeListener l) {
        prefs(context).unregisterOnSharedPreferenceChangeListener(l);
    }

    public static boolean isHapticFeedback(Context context) {
        return prefs(context).getBoolean(KEY_HAPTIC_FEEDBACK, true);
    }

    public static void setHapticFeedback(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply();
    }

    public static void performHaptic(View view) {
        if (view == null || !isHapticFeedback(view.getContext())) return;
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    public static String getDefaultTab(Context context) {
        String tag = prefs(context).getString(KEY_DEFAULT_TAB, TAB_HOME);
        if (!TAB_HOME.equals(tag) && !TAB_EXPLORE.equals(tag) && !TAB_SEARCH.equals(tag)
                && !TAB_DOWNLOADS.equals(tag) && !TAB_GITHUB.equals(tag)) return TAB_HOME;
        return tag;
    }

    public static void setDefaultTab(Context context, String tag) {
        prefs(context).edit().putString(KEY_DEFAULT_TAB, tag).apply();
    }

    public static boolean isAutoCheckUpdatesOnLaunch(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_CHECK_UPDATES, true);
    }

    public static void setAutoCheckUpdatesOnLaunch(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_CHECK_UPDATES, enabled).apply();
    }

    public static boolean isWifiOnlyBackgroundSync(Context context) {
        return prefs(context).getBoolean(KEY_WIFI_ONLY_BACKGROUND_SYNC, true);
    }

    public static void setWifiOnlyBackgroundSync(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_WIFI_ONLY_BACKGROUND_SYNC, enabled).apply();
    }

    public static boolean isBackgroundSyncEnabled(Context context) {
        return prefs(context).getBoolean(KEY_BACKGROUND_SYNC_ENABLED, true);
    }

    public static void setBackgroundSyncEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_BACKGROUND_SYNC_ENABLED, enabled).apply();
    }

    public static boolean isFdroidIndexEnabled(Context context) {
        return true;
    }

    public static boolean isLazyIndexEnabled(Context context) {
        return prefs(context).getBoolean(KEY_LAZY_INDEX_ENABLED, false);
    }

    public static void setLazyIndexEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_LAZY_INDEX_ENABLED, enabled).apply();
    }

    public static boolean isSaveDownloads(Context context) {
        return prefs(context).getBoolean(KEY_SAVE_DOWNLOADS, false);
    }

    public static void setSaveDownloads(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SAVE_DOWNLOADS, enabled).apply();
    }

    public static boolean isKeepApk(Context context) {
        return prefs(context).getBoolean(KEY_KEEP_APK, false);
    }

    public static void setKeepApk(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_KEEP_APK, enabled).apply();
    }

    public static String getHomeSort(Context context) {
        return prefs(context).getString(KEY_HOME_SORT, "LAST_UPDATED");
    }

    public static void setHomeSort(Context context, String sort) {
        prefs(context).edit().putString(KEY_HOME_SORT, sort).apply();
    }

    public static boolean isHomeUpdatesFirst(Context context) {
        return prefs(context).getBoolean(KEY_HOME_UPDATES_FIRST, false);
    }

    public static void setHomeUpdatesFirst(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_HOME_UPDATES_FIRST, enabled).apply();
    }

    public static boolean isHomeNotInstalledLast(Context context) {
        return prefs(context).getBoolean(KEY_HOME_NOT_INSTALLED_LAST, false);
    }

    public static void setHomeNotInstalledLast(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_HOME_NOT_INSTALLED_LAST, enabled).apply();
    }

    public static String getDownloadsSort(Context context) {
        return prefs(context).getString(KEY_DOWNLOADS_SORT, "NEWEST");
    }

    public static void setDownloadsSort(Context context, String sort) {
        prefs(context).edit().putString(KEY_DOWNLOADS_SORT, sort).apply();
    }

    public static String getIconShape(Context context) {
        String saved = prefs(context).getString(KEY_ICON_SHAPE, null);

        return saved == null ? IconShapes.DEFAULT : IconShapes.migrate(saved);
    }

    public static void setIconShape(Context context, String shape) {
        if (!IconShapes.isValid(shape)) return;
        prefs(context).edit().putString(KEY_ICON_SHAPE, shape).apply();
    }
}
