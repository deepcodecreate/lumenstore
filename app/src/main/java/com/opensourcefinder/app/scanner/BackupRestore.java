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
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.opensourcefinder.app.model.MatchedApp;
import com.opensourcefinder.app.model.SourceRepository;
import com.opensourcefinder.app.ui.LanguageManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BackupRestore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_VERSION = 2;

    private static final Map<String, Set<String>> EXCLUDED_KEYS = new LinkedHashMap<>();
    private static final Map<String, Set<String>> INCLUDED_ONLY_KEYS = new LinkedHashMap<>();
    private static final List<String> PREFERENCE_FILES = Arrays.asList(
            "app_preferences",
            "app_theme",
            "app_language",
            "app_favorites",
            "auto_backup");

    static {
        INCLUDED_ONLY_KEYS.put("auto_backup", new HashSet<>(Arrays.asList("interval_days")));
    }

    public static final class PrefEntry {
        public String key;
        public String type;
        public String value;
        public List<String> values;
    }

    public static final class BackupPayload {
        public int version = CURRENT_VERSION;
        public long exportedAt;
        public List<MatchedApp> matches = new ArrayList<>();
        public List<MatchedApp> apps = new ArrayList<>();
        public List<SourceRepository> repositories = new ArrayList<>();
        public Map<String, List<PrefEntry>> preferences = new LinkedHashMap<>();
    }

    private BackupRestore() {}

    public static void exportToUri(Context context, Uri uri) throws Exception {
        exportToUri(context, uri, AutoBackupManager.isIncludeSettings(context));
    }

    public static void exportToUri(Context context, Uri uri, boolean includeSettings) throws Exception {
        BackupPayload payload = buildPayload(context, includeSettings, AutoBackupManager.isIncludeApps(context));
        try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("Cannot open output");
            writePayload(payload, out);
        }
    }

    private static BackupPayload buildPayload(Context context, boolean includeSettings, boolean includeApps) {
        BackupPayload payload = new BackupPayload();
        payload.exportedAt = System.currentTimeMillis();
        payload.matches = new ArrayList<>();
        payload.apps = new ArrayList<>();
        for (MatchedApp match : MatchStore.get(context)) {
            if (match == null) continue;
            if ("manual".equalsIgnoreCase(match.getOrigin())) payload.matches.add(match);
            else if (includeApps) payload.apps.add(match);
        }
        payload.repositories = RepositoryManager.get(context);
        payload.preferences = includeSettings ? collectPreferences(context) : new LinkedHashMap<>();
        return payload;
    }

    private static void writePayload(BackupPayload payload, OutputStream out) throws Exception {
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        GSON.toJson(payload, BackupPayload.class, writer);
        writer.flush();
    }

    public static BackupPayload readPayload(Context context, Uri uri) throws Exception {
        InputStream in = context.getContentResolver().openInputStream(uri);
        if (in == null) throw new IllegalStateException("Cannot open input");
        try (InputStream input = in) {
            return parsePayload(input);
        }
    }

    private static BackupPayload parsePayload(InputStream input) throws Exception {
        BackupPayload payload;
        try (JsonReader reader = new JsonReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            payload = GSON.fromJson(reader, BackupPayload.class);
        }
        if (payload == null || payload.exportedAt <= 0) throw new IllegalStateException("Invalid backup file");
        if (payload.matches == null) payload.matches = new ArrayList<>();
        if (payload.apps == null) payload.apps = new ArrayList<>();
        if (payload.repositories == null) payload.repositories = new ArrayList<>();
        return payload;
    }

    public static int countApps(BackupPayload payload) {
        if (payload == null) return 0;
        return mergedMatches(payload, false).size();
    }

    private static List<MatchedApp> mergedMatches(BackupPayload payload, boolean markFromBackup) {
        Map<String, MatchedApp> merged = new LinkedHashMap<>();
        List<MatchedApp> all = new ArrayList<>();
        if (payload.matches != null) all.addAll(payload.matches);
        if (payload.apps != null) all.addAll(payload.apps);
        for (MatchedApp match : all) {
            if (match == null || match.getPackageName() == null) continue;
            if (markFromBackup && !"manual".equalsIgnoreCase(match.getOrigin())) match.setFromBackup(true);
            merged.put(match.getStoreKey(), match);
        }
        return new ArrayList<>(merged.values());
    }

    public static boolean hasSettings(BackupPayload payload) {
        if (payload == null || payload.preferences == null) return false;
        for (List<PrefEntry> entries : payload.preferences.values()) {
            if (entries != null && !entries.isEmpty()) return true;
        }
        return false;
    }

    public static void applyPayload(Context context, BackupPayload payload) {
        snapshotBeforeRestore(context);
        if (payload.repositories != null) {
            RepositoryManager.replaceAll(context, payload.repositories);
        }
        MatchStore.replaceAll(context, mergedMatches(payload, true));
        if (payload.preferences != null && !payload.preferences.isEmpty()) {
            applyPreferences(context, payload.preferences);
        }
    }

    public static BackupPayload importFromUri(Context context, Uri uri) throws Exception {
        BackupPayload payload = readPayload(context, uri);
        applyPayload(context, payload);
        return payload;
    }

    private static File snapshotFile(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), "lumenstore-pre-restore.json");
    }

    private static void snapshotBeforeRestore(Context context) {
        try (OutputStream out = new FileOutputStream(snapshotFile(context))) {
            writePayload(buildPayload(context, true, true), out);
        } catch (Exception ignored) {
        }
    }

    public static boolean hasUndoSnapshot(Context context) {
        File file = snapshotFile(context);
        return file.isFile() && file.length() > 0;
    }

    public static BackupPayload undoLastRestore(Context context) throws Exception {
        File file = snapshotFile(context);
        if (!file.isFile()) throw new IllegalStateException("No snapshot available");
        BackupPayload payload;
        try (InputStream input = new FileInputStream(file)) {
            payload = parsePayload(input);
        }
        if (payload.repositories != null) {
            RepositoryManager.replaceAll(context, payload.repositories);
        }
        MatchStore.replaceAll(context, mergedMatches(payload, false));
        if (payload.preferences != null && !payload.preferences.isEmpty()) {
            applyPreferences(context, payload.preferences);
        }
        file.delete();
        return payload;
    }

    private static Map<String, List<PrefEntry>> collectPreferences(Context context) {
        Map<String, List<PrefEntry>> result = new LinkedHashMap<>();
        Context app = context.getApplicationContext();
        for (String file : PREFERENCE_FILES) {
            SharedPreferences prefs = app.getSharedPreferences(file, Context.MODE_PRIVATE);
            List<PrefEntry> entries = new ArrayList<>();
            for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
                if (!isAllowed(file, e.getKey())) continue;
                PrefEntry entry = toEntry(e.getKey(), e.getValue());
                if (entry != null) entries.add(entry);
            }
            result.put(file, entries);
        }
        return result;
    }

    private static void applyPreferences(Context context, Map<String, List<PrefEntry>> data) {
        Context app = context.getApplicationContext();
        for (String file : PREFERENCE_FILES) {
            List<PrefEntry> entries = data.get(file);
            if (entries == null) continue;
            SharedPreferences prefs = app.getSharedPreferences(file, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            for (String existing : prefs.getAll().keySet()) {
                if (isAllowed(file, existing)) editor.remove(existing);
            }
            for (PrefEntry entry : entries) {
                if (entry == null || entry.key == null || entry.type == null) continue;
                if (!isAllowed(file, entry.key)) continue;
                writeEntry(editor, entry);
            }
            editor.commit();
        }
        new Handler(Looper.getMainLooper()).post(() -> {
            LanguageManager.init(app);
            AutoBackupManager.rescheduleIfEnabled(app);
        });
    }

    private static boolean isAllowed(String file, String key) {
        Set<String> only = INCLUDED_ONLY_KEYS.get(file);
        if (only != null) return only.contains(key);
        Set<String> excluded = EXCLUDED_KEYS.get(file);
        return excluded == null || !excluded.contains(key);
    }

    private static PrefEntry toEntry(String key, Object value) {
        PrefEntry entry = new PrefEntry();
        entry.key = key;
        if (value instanceof Boolean) {
            entry.type = "boolean";
            entry.value = String.valueOf(value);
        } else if (value instanceof Integer) {
            entry.type = "int";
            entry.value = String.valueOf(value);
        } else if (value instanceof Long) {
            entry.type = "long";
            entry.value = String.valueOf(value);
        } else if (value instanceof Float) {
            entry.type = "float";
            entry.value = String.valueOf(value);
        } else if (value instanceof String) {
            entry.type = "string";
            entry.value = (String) value;
        } else if (value instanceof Set) {
            entry.type = "set";
            entry.values = new ArrayList<>();
            for (Object o : (Set<?>) value) {
                if (o != null) entry.values.add(o.toString());
            }
        } else {
            return null;
        }
        return entry;
    }

    private static void writeEntry(SharedPreferences.Editor editor, PrefEntry entry) {
        try {
            switch (entry.type) {
                case "boolean":
                    editor.putBoolean(entry.key, Boolean.parseBoolean(entry.value));
                    break;
                case "int":
                    editor.putInt(entry.key, Integer.parseInt(entry.value));
                    break;
                case "long":
                    editor.putLong(entry.key, Long.parseLong(entry.value));
                    break;
                case "float":
                    editor.putFloat(entry.key, Float.parseFloat(entry.value));
                    break;
                case "string":
                    editor.putString(entry.key, entry.value);
                    break;
                case "set":
                    editor.putStringSet(entry.key, entry.values == null
                            ? new HashSet<>() : new HashSet<>(entry.values));
                    break;
                default:
                    break;
            }
        } catch (RuntimeException ignored) {
        }
    }
}
