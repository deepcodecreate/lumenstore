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
import com.opensourcefinder.app.model.MatchedApp;
import com.opensourcefinder.app.model.SourceRepository;
import com.opensourcefinder.app.network.PlayStoreClient;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RepositoryManager {
    private static final String PREFS = "repositories";
    private static final String KEY = "sources";
    private RepositoryManager() {}

    public static synchronized List<SourceRepository> get(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
        List<SourceRepository> result = new ArrayList<>();
        try {
            Type type = new TypeToken<List<SourceRepository>>(){}.getType();
            List<SourceRepository> list = new Gson().fromJson(raw, type);
            if (list != null) result.addAll(list);
        } catch (Exception ignored) {}
        if (result.isEmpty()) {
            List<SourceRepository> recovered = recoverFromMatches(context);
            if (!recovered.isEmpty()) {
                save(context, recovered);
                result.addAll(recovered);
            }
        }
        return result;
    }

    private static List<SourceRepository> recoverFromMatches(Context context) {
        List<SourceRepository> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try {
            for (MatchedApp app : MatchStore.get(context)) {
                if (app == null || !"manual".equals(app.getOrigin())) continue;
                String url = app.getSourceUrl();
                String sourceType = app.getSourceType();
                if (url == null || url.isEmpty() || sourceType == null) continue;
                String type;
                String name;
                if (PlayStoreClient.SOURCE_TYPE.equalsIgnoreCase(sourceType)) {
                    type = PlayStoreClient.SOURCE_TYPE;
                    name = app.getAppLabel() != null && !app.getAppLabel().isEmpty() ? app.getAppLabel() : url;
                } else if ("github".equalsIgnoreCase(sourceType)) {
                    type = "github";
                    name = url.replaceFirst("(?i)^https?://[^/]+/", "").replaceAll("/+$", "");
                } else if ("gitlab".equalsIgnoreCase(sourceType) || "codeberg".equalsIgnoreCase(sourceType)) {
                    type = "auto";
                    name = url.replaceFirst("(?i)^https?://[^/]+/", "").replaceAll("/+$", "");
                } else {
                    continue;
                }
                if (!url.toLowerCase(Locale.ROOT).startsWith("http")) continue;
                if (!seen.add(url.toLowerCase(Locale.ROOT).replaceAll("/+$", ""))) continue;
                out.add(new SourceRepository(url, name, type));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static synchronized void add(Context context, SourceRepository source) {
        List<SourceRepository> list = get(context);
        for (SourceRepository item : list) {
            if (item.getUrl() != null && item.getUrl().equalsIgnoreCase(source.getUrl())) return;
        }
        list.add(source);
        save(context, list);
    }

    public static String normalizeUrl(String url) {
        if (url == null) return "";
        String value = url.trim().toLowerCase(Locale.ROOT).replaceFirst("^[a-z][a-z0-9+.-]*://", "");
        if (value.startsWith("www.")) value = value.substring(4);
        int cut = value.indexOf('#');
        if (cut >= 0) value = value.substring(0, cut);
        if (value.startsWith("github.com/") || value.startsWith("codeberg.org/")) {
            cut = value.indexOf('?');
            if (cut >= 0) value = value.substring(0, cut);
            String[] parts = value.split("/");
            if (parts.length >= 3) value = parts[0] + "/" + parts[1] + "/" + parts[2];
        }
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.endsWith(".git")) value = value.substring(0, value.length() - 4);
        return value;
    }

    public static boolean sameUrl(String a, String b) {
        return a != null && b != null && normalizeUrl(a).equals(normalizeUrl(b));
    }

    public static synchronized void remove(Context context, String url) {
        List<SourceRepository> list = get(context);
        list.removeIf(item -> item.getUrl() != null && sameUrl(item.getUrl(), url));
        save(context, list);
        ManualScanCache.remove(context, url);
    }

    public static synchronized void update(Context context, SourceRepository changed) {
        if (changed == null || changed.getUrl() == null) return;
        List<SourceRepository> list = get(context);
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            SourceRepository item = list.get(i);
            if (item.getUrl() != null && item.getUrl().equalsIgnoreCase(changed.getUrl())) {
                list.set(i, changed);
                found = true;
            }
        }
        if (!found) list.add(changed);
        save(context, list);
        ManualScanCache.remove(context, changed.getUrl());
    }

    public static synchronized void replaceAll(Context context, List<SourceRepository> list) {
        save(context, list == null ? new ArrayList<>() : new ArrayList<>(list));
    }

    private static void save(Context context, List<SourceRepository> list) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, new Gson().toJson(list)).apply();
    }
}
