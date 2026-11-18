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

package com.opensourcefinder.app.install;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;

public final class InstallShortcutHelper {
    private static final int MAX_LABEL_CODE_POINTS = 40;
    private static final int ADAPTIVE_ICON_SIZE = 432;
    private static final int LEGACY_ICON_SIZE = 192;

    private InstallShortcutHelper() {}

    public static boolean requestPinForInstalledApp(Activity activity, String packageName) {
        if (activity == null || packageName == null || packageName.trim().isEmpty()
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        if (activity.isFinishing() || activity.isDestroyed()) return false;

        try {
            Context appContext = activity.getApplicationContext();
            ShortcutManager shortcutManager =
                    (ShortcutManager) appContext.getSystemService(Context.SHORTCUT_SERVICE);
            if (shortcutManager == null || !shortcutManager.isRequestPinShortcutSupported()) return true;

            String shortcutId = "installed_app_" + packageName;
            for (ShortcutInfo pinned : shortcutManager.getPinnedShortcuts()) {
                if (shortcutId.equals(pinned.getId())) return true;
            }

            PackageManager packageManager = appContext.getPackageManager();
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
            if (launchIntent == null) return true;
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            String label = safeLabel(packageManager.getApplicationLabel(appInfo));
            ShortcutInfo.Builder builder = new ShortcutInfo.Builder(appContext, shortcutId)
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIntent(launchIntent);

            builder.setIcon(buildIcon(packageManager, appInfo));
            return shortcutManager.requestPinShortcut(builder.build(), null);
        } catch (IllegalStateException notForeground) {
            return false;
        } catch (Exception ignored) {

            return true;
        }
    }

    private static String safeLabel(CharSequence source) {
        String label = source == null ? "" : source.toString().trim();
        if (label.isEmpty()) label = "App";
        int codePoints = label.codePointCount(0, label.length());
        if (codePoints > MAX_LABEL_CODE_POINTS) {
            label = label.substring(0, label.offsetByCodePoints(0, MAX_LABEL_CODE_POINTS));
        }
        return label;
    }

    private static Icon buildIcon(PackageManager packageManager, ApplicationInfo appInfo) {
        Drawable drawable = packageManager.getApplicationIcon(appInfo);
        if (drawable instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) drawable;
            Bitmap bitmap = Bitmap.createBitmap(ADAPTIVE_ICON_SIZE, ADAPTIVE_ICON_SIZE, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawLayer(adaptive.getBackground(), canvas, ADAPTIVE_ICON_SIZE);
            drawLayer(adaptive.getForeground(), canvas, ADAPTIVE_ICON_SIZE);
            return Icon.createWithAdaptiveBitmap(bitmap);
        }
        Bitmap bitmap = Bitmap.createBitmap(LEGACY_ICON_SIZE, LEGACY_ICON_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawLayer(drawable, canvas, LEGACY_ICON_SIZE);
        return Icon.createWithBitmap(bitmap);
    }

    private static void drawLayer(Drawable layer, Canvas canvas, int size) {
        if (layer == null) return;
        layer.setBounds(0, 0, size, size);
        layer.draw(canvas);
    }
}
