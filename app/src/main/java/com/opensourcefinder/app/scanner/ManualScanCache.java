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
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.opensourcefinder.app.model.MatchedApp;

import java.lang.reflect.Type;
import java.util.List;

public final class ManualScanCache {
    private static final String PREFS = "manual_scan_cache_v1";
    private static final String KEY_PREFIX = "src_";
    private static final Gson GSON = new Gson();
    private static final Type APPS_TYPE = new TypeToken<List<MatchedApp>>() {}.getType();

    public static final class Entry {
        public long checkedAt;
        public long analysedAt;

        public String probeTag;
        public String appsJson;

        public List<MatchedApp> apps() {
            try {
                List<MatchedApp> list = GSON.fromJson(appsJson, APPS_TYPE);
                return list;
            } catch (Exception e) {
                return null;
            }
        }
    }

    private ManualScanCache() {}

    public static synchronized Entry get(Context context, String sourceUrl) {
        if (sourceUrl == null) return null;
        String raw = prefs(context).getString(key(sourceUrl), null);
        if (raw == null) return null;
        try {
            return GSON.fromJson(raw, Entry.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static synchronized void putAnalysed(Context context, String sourceUrl, String probeTag, List<MatchedApp> apps) {
        if (sourceUrl == null || apps == null) return;
        long now = System.currentTimeMillis();
        write(context, sourceUrl, now, now, probeTag, apps);
    }

    public static synchronized void putRefreshed(Context context, String sourceUrl, Entry previous, String probeTag, List<MatchedApp> apps) {
        if (sourceUrl == null || apps == null) return;
        long now = System.currentTimeMillis();
        write(context, sourceUrl, now, previous != null ? previous.analysedAt : now, probeTag, apps);
    }

    public static synchronized void touch(Context context, String sourceUrl, Entry entry) {
        if (sourceUrl == null || entry == null) return;
        entry.checkedAt = System.currentTimeMillis();
        prefs(context).edit().putString(key(sourceUrl), GSON.toJson(entry)).apply();
    }

    public static synchronized void remove(Context context, String sourceUrl) {
        if (sourceUrl == null) return;
        prefs(context).edit().remove(key(sourceUrl)).apply();
    }

    public static synchronized void clearAll(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static void write(Context context, String sourceUrl, long checkedAt, long analysedAt, String probeTag, List<MatchedApp> apps) {
        Entry entry = new Entry();
        entry.checkedAt = checkedAt;
        entry.analysedAt = analysedAt;
        entry.probeTag = probeTag;
        entry.appsJson = GSON.toJson(apps, APPS_TYPE);
        prefs(context).edit().putString(key(sourceUrl), GSON.toJson(entry)).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(String sourceUrl) {
        return KEY_PREFIX + sourceUrl.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
