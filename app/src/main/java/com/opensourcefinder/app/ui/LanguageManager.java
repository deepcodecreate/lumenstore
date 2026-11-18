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
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public final class LanguageManager {
    private static final String PREFS = "app_language";
    private static final String KEY = "language";
    public static final String ENGLISH = "en";
    public static final String PERSIAN = "fa";

    private LanguageManager() {}

    public static void init(Context context) {
        String language = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, ENGLISH);
        apply(language);
    }

    public static String get(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, ENGLISH);
    }

    public static void set(Context context, String language) {
        if (!ENGLISH.equals(language) && !PERSIAN.equals(language)) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, language).apply();
        apply(language);
    }

    public static void showDialog(Context context, Runnable onChanged) {
        String current = get(context);
        String[] languages = {
                context.getString(com.opensourcefinder.app.R.string.language_english),
                context.getString(com.opensourcefinder.app.R.string.language_persian)
        };
        int checked = PERSIAN.equals(current) ? 1 : 0;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle(com.opensourcefinder.app.R.string.language_title)
                .setSingleChoiceItems(languages, checked, (dialog, which) -> {
                    String selected = which == 0 ? ENGLISH : PERSIAN;
                    if (selected.equals(get(context))) {
                        dialog.dismiss();
                        return;
                    }
                    set(context, selected);
                    dialog.dismiss();
                    if (onChanged != null) onChanged.run();
                })
                .setNegativeButton(com.opensourcefinder.app.R.string.cancel, null)
                .show();
    }

    private static void apply(String language) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language));
    }
}
