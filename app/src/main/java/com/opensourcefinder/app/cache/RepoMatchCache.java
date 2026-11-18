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

package com.opensourcefinder.app.cache;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.opensourcefinder.app.model.GitHubRepo;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RepoMatchCache {
    public static final long NEGATIVE_RECHECK_MILLIS = 7L * 24L * 60L * 60L * 1000L;

    public static final class CachedEntry {
        private final boolean found;
        private final GitHubRepo repo;
        private final long checkedAt;

        CachedEntry(boolean found, GitHubRepo repo) {
            this.found = found;
            this.repo = repo;
            this.checkedAt = System.currentTimeMillis();
        }

        public boolean isFound() { return found && repo != null; }
        public GitHubRepo getRepo() { return repo; }
        public boolean shouldRetryNegative() {
            return !found && System.currentTimeMillis() - checkedAt >= NEGATIVE_RECHECK_MILLIS;
        }
    }

    private static final String PREFS_NAME = "repo_match_cache";
    private static final String KEY_BLOB = "cache_blob_v2";
    private static final String KEY_INDEX = "cache_keys_v3";
    private static final String KEY_MIGRATED = "cache_migrated_v3";
    private static final String ENTRY_PREFIX = "cache_entry_v3_";
    private static final Gson gson = new Gson();
    private static volatile Map<String, CachedEntry> cache;
    private static Context appContext;

    public static synchronized void init(Context context) {
        if (cache != null) return;
        appContext = context.getApplicationContext();
        SharedPreferences preferences = prefs();
        cache = new HashMap<>();
        if (preferences.getBoolean(KEY_MIGRATED, false)) {
            Set<String> keys = preferences.getStringSet(KEY_INDEX, java.util.Collections.emptySet());
            for (String key : keys) {
                String raw = preferences.getString(entryKey(key), null);
                if (raw == null) continue;
                try {
                    CachedEntry entry = gson.fromJson(raw, CachedEntry.class);
                    if (entry != null) cache.put(key, entry);
                } catch (Exception ignored) {}
            }
            return;
        }
        String json = preferences.getString(KEY_BLOB, null);
        if (json == null) return;
        try {
            Type type = new TypeToken<HashMap<String, CachedEntry>>() {}.getType();
            Map<String, CachedEntry> loaded = gson.fromJson(json, type);
            if (loaded != null) cache.putAll(loaded);
        } catch (Exception e) {
        }
        migrate();
    }

    public static synchronized CachedEntry get(String packageName) {
        return cache != null ? cache.get(packageName) : null;
    }

    public static synchronized void put(String packageName, GitHubRepo repo, boolean found) {
        if (cache == null) return;
        CachedEntry entry = new CachedEntry(found, repo);
        cache.put(packageName, entry);
        prefs().edit()
                .putString(entryKey(packageName), gson.toJson(entry))
                .putStringSet(KEY_INDEX, new java.util.HashSet<>(cache.keySet()))
                .putBoolean(KEY_MIGRATED, true)
                .apply();
    }

    public static synchronized void clearAll() {
        if (cache == null) return;
        SharedPreferences.Editor editor = prefs().edit();
        Set<String> keys = prefs().getStringSet(KEY_INDEX, java.util.Collections.emptySet());
        for (String key : keys) editor.remove(entryKey(key));
        cache.clear();
        editor.putStringSet(KEY_INDEX, java.util.Collections.emptySet())
                .putBoolean(KEY_MIGRATED, true)
                .apply();
    }

    private static void migrate() {
        SharedPreferences.Editor editor = prefs().edit();
        for (Map.Entry<String, CachedEntry> entry : cache.entrySet()) {
            editor.putString(entryKey(entry.getKey()), gson.toJson(entry.getValue()));
        }
        editor.putStringSet(KEY_INDEX, new java.util.HashSet<>(cache.keySet()));
        editor.putBoolean(KEY_MIGRATED, true);
        editor.apply();
    }

    private static SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String entryKey(String packageName) {
        return ENTRY_PREFIX + Integer.toHexString(packageName.hashCode()) + "_" + packageName;
    }
}
