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

package com.opensourcefinder.app.scanner;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.opensourcefinder.app.model.SourceRepository;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class RepositoryManager {
    private static final String PREFS = "repositories";
    private static final String KEY = "sources";
    private RepositoryManager() {}

    public static synchronized List<SourceRepository> get(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
        try {
            Type type = new TypeToken<List<SourceRepository>>(){}.getType();
            List<SourceRepository> list = new Gson().fromJson(raw, type);
            return list == null ? new ArrayList<>() : new ArrayList<>(list);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static synchronized void add(Context context, SourceRepository source) {
        List<SourceRepository> list = get(context);
        for (SourceRepository item : list) {
            if (item.getUrl() != null && item.getUrl().equalsIgnoreCase(source.getUrl())) return;
        }
        list.add(source);
        save(context, list);
    }

    public static synchronized void remove(Context context, String url) {
        List<SourceRepository> list = get(context);
        list.removeIf(item -> item.getUrl() != null && item.getUrl().equalsIgnoreCase(url));
        save(context, list);
        ManualScanCache.remove(context, url);
    }

    public static synchronized void replaceAll(Context context, List<SourceRepository> list) {
        save(context, list == null ? new ArrayList<>() : new ArrayList<>(list));
    }

    private static void save(Context context, List<SourceRepository> list) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, new Gson().toJson(list)).apply();
    }
}
