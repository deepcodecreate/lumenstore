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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MatchStore {
    private static final String PREFS = "found_apps";
    private static final String KEY = "matches_v3";
    private static final String INDEX_KEY = "matches_v4_keys";
    private static final String MIGRATED_KEY = "matches_v4_migrated";
    private static final String ENTRY_PREFIX = "match_v4_";
    private static final Gson gson = new Gson();
    private static final Map<String, MatchedApp> cache = new LinkedHashMap<>();
    private static boolean loaded;
    private MatchStore() {}

    public static synchronized List<MatchedApp> get(Context context) {
        ensureLoaded(context);
        if (cache.keySet().removeIf(PackageBlocklist::isBlocked)) persistAll(context);
        return new ArrayList<>(cache.values());
    }

    public static volatile long revision;

    public static synchronized void replaceAll(Context context, List<MatchedApp> matches) {
        ensureLoaded(context);
        revision++;
        Map<String, MatchedApp> previous = new LinkedHashMap<>(cache);
        cache.clear();
        if (matches != null) {
            for (MatchedApp match : matches) {
                if (match != null && match.getPackageName() != null && !PackageBlocklist.isBlocked(match.getPackageName())) {
                    inherit(match, previous.get(match.getStoreKey()));
                    cache.put(match.getStoreKey(), match);
                }
            }
        }
        persistAll(context);
    }

    public static synchronized void upsert(Context context, MatchedApp match) {
        if (match == null || match.getPackageName() == null || PackageBlocklist.isBlocked(match.getPackageName())) return;
        ensureLoaded(context);
        inherit(match, cache.get(match.getStoreKey()));
        cache.put(match.getStoreKey(), match);
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putString(entryKey(match.getStoreKey()), gson.toJson(match));
        editor.putStringSet(INDEX_KEY, new java.util.HashSet<>(cache.keySet()));
        editor.putBoolean(MIGRATED_KEY, true);
        editor.apply();
    }

    public static synchronized void upsertReplacing(Context context, MatchedApp match, String... oldKeys) {
        if (match == null || match.getPackageName() == null) return;
        if (oldKeys != null) {
            for (String oldKey : oldKeys) {
                if (oldKey != null && !oldKey.equals(match.getStoreKey())) remove(context, oldKey);
            }
        }
        upsert(context, match);
    }

    public static synchronized void remove(Context context, String packageName) {
        if (packageName == null) return;
        ensureLoaded(context);
        if (cache.remove(packageName) == null) return;
        prefs(context).edit()
                .remove(entryKey(packageName))
                .putStringSet(INDEX_KEY, new java.util.HashSet<>(cache.keySet()))
                .putBoolean(MIGRATED_KEY, true)
                .apply();
    }

    public static synchronized void removeIfNeverInstalled(Context context, String packageName) {
        if (packageName == null) return;
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return;
        } catch (Exception notInstalled) {}
        remove(context, packageName);
    }

    public static synchronized void updateIfPresent(Context context, MatchedApp match) {
        if (match == null || match.getPackageName() == null || PackageBlocklist.isBlocked(match.getPackageName())) return;
        ensureLoaded(context);
        if (!cache.containsKey(match.getStoreKey())) return;
        cache.put(match.getStoreKey(), match);
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putString(entryKey(match.getStoreKey()), gson.toJson(match));
        editor.apply();
    }

    private static void inherit(MatchedApp match, MatchedApp old) {
        if (match == null || old == null || match == old) return;
        if (match.getLatestVersionCode() <= 0 && old.getLatestVersionCode() <= 0
                && old.getLatestTag() != null && match.getLatestTag() != null
                && old.getDownloadUrl() != null
                && !match.isNotInstalled()
                && com.opensourcefinder.app.install.InstallState.isPreRelease(match.getInstalledVersionName())
                && sameRepo(match, old)
                && com.opensourcefinder.app.install.InstallState.tagIsNewer(old.getLatestTag(), match.getLatestTag())) {
            match.setLatestTag(old.getLatestTag());
            match.setDownloadUrl(old.getDownloadUrl());
        }
        if (match.getInstalledTag() == null && old.getInstalledTag() != null) {
            match.setInstalledTag(old.getInstalledTag());
            match.setInstalledTagVersionCode(old.getInstalledTagVersionCode());
        }
    }

    public static synchronized void restoreInstalledTags(Context context, java.util.Collection<MatchedApp> apps) {
        ensureLoaded(context);
        if (apps == null) return;
        for (MatchedApp app : apps) {
            if (app == null || app.getPackageName() == null) continue;
            inherit(app, cache.get(app.getStoreKey()));
        }
    }

    public static boolean sameRepo(MatchedApp a, MatchedApp b) {
        if (a == null || b == null || a.getRepo() == null || b.getRepo() == null) return false;
        String ownerA = a.getRepo().getOwnerLogin();
        String ownerB = b.getRepo().getOwnerLogin();
        String nameA = a.getRepo().getName();
        String nameB = b.getRepo().getName();
        return ownerA != null && ownerB != null && nameA != null && nameB != null
                && ownerA.equalsIgnoreCase(ownerB) && nameA.equalsIgnoreCase(nameB);
    }

    public static void putManual(Map<String, MatchedApp> map, MatchedApp incoming) {
        if (incoming == null || incoming.getPackageName() == null) return;
        incoming.setOrigin("manual");
        incoming.setSecondary(false);

        String packageName = incoming.getPackageName();
        MatchedApp sameRepository = null;
        boolean hasDifferentRepository = false;
        List<String> repositoryKeys = new ArrayList<>();
        for (Map.Entry<String, MatchedApp> entry : map.entrySet()) {
            MatchedApp existing = entry.getValue();
            if (existing == null || !packageName.equals(existing.getPackageName())) continue;
            if (existing.getRepo() == null || incoming.getRepo() == null) continue;
            repositoryKeys.add(entry.getKey());
            if (sameRepo(existing, incoming)) {
                sameRepository = existing;
            } else {
                hasDifferentRepository = true;
            }
        }

        if (hasDifferentRepository) {
            for (String oldKey : repositoryKeys) {
                MatchedApp existing = map.get(oldKey);
                if (existing == null || existing.getRepo() == null) continue;
                existing.setSecondary(true);
                String repoKey = existing.getStoreKey();
                if (!oldKey.equals(repoKey)) {
                    map.remove(oldKey);
                    map.put(repoKey, existing);
                }
            }
            incoming.setSecondary(true);
        } else if (sameRepository != null) {
            incoming.setSecondary(sameRepository.isSecondary());
        }
        map.put(incoming.getStoreKey(), incoming);
    }

    public static boolean coveredBySameRepo(Map<String, MatchedApp> map, MatchedApp old) {
        if (old == null || old.getRepo() == null) return false;
        for (MatchedApp candidate : map.values()) {
            if (candidate != null && old.getPackageName() != null
                    && old.getPackageName().equals(candidate.getPackageName()) && sameRepo(candidate, old)) return true;
        }
        return false;
    }

    private static void ensureLoaded(Context context) {
        if (loaded) return;
        SharedPreferences prefs = prefs(context);
        cache.clear();
        if (prefs.getBoolean(MIGRATED_KEY, false)) {
            Set<String> keys = prefs.getStringSet(INDEX_KEY, java.util.Collections.emptySet());
            for (String key : keys) {
                String raw = prefs.getString(entryKey(key), null);
                if (raw == null) continue;
                try {
                    MatchedApp match = gson.fromJson(raw, MatchedApp.class);
                    if (match != null && match.getPackageName() != null) cache.put(match.getStoreKey(), match);
                } catch (Exception ignored) {}
            }
            loaded = true;
            return;
        }

        String raw = prefs.getString(KEY, "[]");
        try {
            Type type = new TypeToken<List<MatchedApp>>(){}.getType();
            List<MatchedApp> list = gson.fromJson(raw, type);
            if (list != null) {
                for (MatchedApp match : list) {
                    if (match != null && match.getPackageName() != null) {
                        cache.put(match.getStoreKey(), match);
                    }
                }
            }
        } catch (Exception ignored) {}
        migrate(context);
        loaded = true;
    }

    private static void migrate(Context context) {
        SharedPreferences.Editor editor = prefs(context).edit();
        for (MatchedApp match : cache.values()) {
            editor.putString(entryKey(match.getStoreKey()), gson.toJson(match));
        }
        editor.putStringSet(INDEX_KEY, new java.util.HashSet<>(cache.keySet()));
        editor.putBoolean(MIGRATED_KEY, true);
        editor.apply();
    }

    private static void persistAll(Context context) {
        SharedPreferences prefs = prefs(context);
        Set<String> previousKeys = prefs.getStringSet(INDEX_KEY, java.util.Collections.emptySet());
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : previousKeys) {
            if (!cache.containsKey(key)) editor.remove(entryKey(key));
        }
        for (MatchedApp match : cache.values()) {
            editor.putString(entryKey(match.getStoreKey()), gson.toJson(match));
        }
        editor.putStringSet(INDEX_KEY, new java.util.HashSet<>(cache.keySet()));
        editor.putBoolean(MIGRATED_KEY, true);
        editor.apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String entryKey(String packageName) {
        return ENTRY_PREFIX + Integer.toHexString(packageName.hashCode()) + "_" + packageName;
    }
}
