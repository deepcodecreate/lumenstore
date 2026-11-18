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
import java.util.HashSet;
import java.util.Set;

public final class FavoriteManager {
    private static final String PREFS = "app_favorites";
    private static final String KEY = "packages";
    private FavoriteManager() {}
    private static Set<String> get(Context c) {
        return new HashSet<>(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, new HashSet<>()));
    }
    public static boolean isFavorite(Context c, String pkg) { return pkg != null && get(c).contains(pkg); }
    public static boolean toggle(Context c, String pkg) {
        Set<String> s = get(c);
        boolean next = !s.contains(pkg);
        if (next) s.add(pkg); else s.remove(pkg);
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, s).apply();
        return next;
    }
    public static Set<String> all(Context c) { return get(c); }
}
