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

import android.text.TextUtils;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.opensourcefinder.app.model.DeviceAbi;
import com.opensourcefinder.app.model.ExploreApp;
import com.opensourcefinder.app.model.InstalledAppInfo;
import com.opensourcefinder.app.model.MatchedApp;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FdroidIndexV2Parser {

    private static final String[] LOCALES = {"en-US", "en", "fa", "fa-IR"};
    private static final String[] SCREENSHOT_TYPES = {"phone", "sevenInch", "tenInch"};

    private FdroidIndexV2Parser() {}

    private interface PackageFilter {
        boolean accept(String packageName);
    }

    private static final class Version {
        String name;
        long code = -1;
        long size;
        String file;
        String arch;
        String signer;
        String hash;
        boolean prerelease;
        List<String> permissions = new ArrayList<>();
        List<String> antiFeatures = new ArrayList<>();
    }

    private static final class Entry {
        String name;
        String summary;
        String description;
        String icon;
        String license;
        String sourceCode;
        String website;
        String issueTracker;
        String translation;
        String changelog;
        String authorName;
        String donate;
        long added;
        long lastUpdated;
        List<String> categories = new ArrayList<>();
        List<String> screenshots = new ArrayList<>();
        List<Version> versions = new ArrayList<>();
        String releases;
    }

    private static final class Parsed {
        final Map<String, String> categoryNames = new HashMap<>();
        final Map<String, Entry> entries = new LinkedHashMap<>();
    }

    public static boolean isV2(String indexUrl) {
        if (indexUrl == null) return false;
        return stripQuery(indexUrl).endsWith("/index-v2.json");
    }

    public static String baseOf(String indexUrl) {
        String clean = stripQuery(indexUrl);
        int slash = clean.lastIndexOf('/');
        return slash > 8 ? clean.substring(0, slash) : clean;
    }

    public static Map<String, MatchedApp> parseInstalled(File file, Map<String, InstalledAppInfo> installed, String indexUrl) throws Exception {
        return parseInstalled(file, installed, indexUrl, null);
    }

    public static Map<String, MatchedApp> parseInstalled(File file, Map<String, InstalledAppInfo> installed, String indexUrl, String repoName) throws Exception {
        Parsed parsed = parse(file, installed::containsKey);
        String base = baseOf(indexUrl);
        Map<String, MatchedApp> result = new HashMap<>();
        for (Map.Entry<String, Entry> e : parsed.entries.entrySet()) {
            String pkg = e.getKey();
            InstalledAppInfo app = installed.get(pkg);
            Entry entry = e.getValue();
            if (app == null) continue;
            Version latest = latest(entry);
            String slug = releaseSlug(entry);
            if (latest == null && slug == null) continue;
            if (latest == null) {
                MatchedApp pending = new MatchedApp();
                pending.setPackageName(app.getPackageName());
                pending.setAppLabel(!isEmpty(entry.name) ? entry.name : app.getAppLabel());
                pending.setInstalledVersionName(app.getVersionName());
                pending.setSourceType("fdroid");
                pending.setOrigin("auto");
                pending.setIndexRepo(repoName);
                pending.setGithubSlug(slug);
                pending.setSourceUrl("https://github.com/" + slug);
                pending.setDescription(!isEmpty(entry.description) ? entry.description : entry.summary);
                pending.setIconUrl(resolve(base, entry.icon));
                pending.setUpdateAvailable(false);
                result.put(pkg, pending);
                continue;
            }
            MatchedApp matched = new MatchedApp();
            matched.setPackageName(app.getPackageName());
            matched.setAppLabel(app.getAppLabel());
            matched.setInstalledVersionName(app.getVersionName());
            matched.setLatestTag(latest.name != null ? latest.name : String.valueOf(latest.code));
            matched.setLatestVersionCode(latest.code);
            matched.setUpdateAvailable(latest.code > app.getVersionCode()
                    && !com.opensourcefinder.app.install.InstallState.sameVersionName(latest.name, app.getVersionName()));
            matched.setSourceType("fdroid");
            matched.setOrigin("auto");
            matched.setIndexRepo(repoName);
            matched.setSourceUrl(sourceUrl(entry, base));
            matched.setDownloadUrl(resolve(base, latest.file));
            matched.setDescription(!isEmpty(entry.description) ? entry.description : entry.summary);
            if (!isEmpty(entry.name)) matched.setAppLabel(entry.name);
            result.put(pkg, matched);
        }
        return result;
    }

    public static List<ExploreApp> parseExplore(File file, String indexUrl, String repoName, Set<String> installedPackages) throws Exception {
        Parsed parsed = parse(file, null);
        String base = baseOf(indexUrl);
        List<ExploreApp> result = new ArrayList<>();
        for (Map.Entry<String, Entry> e : parsed.entries.entrySet()) {
            String pkg = e.getKey();
            Entry meta = e.getValue();
            Version latest = latest(meta);
            String slug = releaseSlug(meta);
            if (latest == null && slug == null) continue;

            ExploreApp app = new ExploreApp();
            app.setPackageName(pkg);
            app.setName(!isEmpty(meta.name) ? meta.name : pkg);
            app.setSummary(meta.summary);
            app.setDescription(!isEmpty(meta.description) ? meta.description : meta.summary);
            if (latest != null) {
                app.setLatestVersionCode(latest.code);
                app.setLatestVersionName(latest.name);
                app.setDownloadUrl(resolve(base, latest.file));
            } else {
                app.setGithubSlug(slug);
            }
            app.setSourceUrl(sourceUrl(meta, base));
            app.setWebsiteUrl(meta.website);
            app.setIssueTrackerUrl(meta.issueTracker);
            app.setTranslationUrl(meta.translation);
            app.setChangelogUrl(meta.changelog);
            app.setIconUrl(resolve(base, meta.icon));

            List<String> shots = new ArrayList<>();
            for (String shot : meta.screenshots) {
                String url = resolve(base, shot);
                if (url != null) shots.add(url);
            }
            if (!shots.isEmpty()) app.setScreenshotUrls(shots);

            List<String> categories = new ArrayList<>();
            for (String id : meta.categories) {
                String label = parsed.categoryNames.get(id);
                categories.add(!isEmpty(label) ? label : id);
            }
            app.setCategories(categories);
            app.setAdded(meta.added);
            app.setLastUpdated(meta.lastUpdated);
            app.setDonateUrl(meta.donate);
            app.setAuthorName(meta.authorName);
            app.setLicense(meta.license);
            if (latest == null) {
                app.setPreviousVersions(new ArrayList<>());
                app.setRepoName(repoName);
                app.setInstalled(installedPackages != null && installedPackages.contains(pkg));
                result.add(app);
                continue;
            }
            app.setApkSize(latest.size);
            app.setAntiFeatures(new ArrayList<>(latest.antiFeatures));
            app.setPermissions(latest.permissions.isEmpty() ? null : latest.permissions);

            List<Version> ordered = new ArrayList<>(meta.versions);
            Collections.sort(ordered, new Comparator<Version>() {
                @Override public int compare(Version a, Version b) {
                    return Long.compare(b.code, a.code);
                }
            });
            List<ExploreApp.VersionInfo> history = new ArrayList<>();
            for (Version v : ordered) {
                if (v.code < 0 || isEmpty(v.file)) continue;
                history.add(new ExploreApp.VersionInfo(v.name, v.code, v.size,
                        resolve(base, v.file), v.arch, v.signer));
            }
            app.setPreviousVersions(history);
            app.setArchitecture(latest.arch);
            app.setSigner(latest.signer);
            app.setApkHash(latest.hash);
            app.setRepoName(repoName);
            app.setInstalled(installedPackages != null && installedPackages.contains(pkg));
            result.add(app);
        }
        return result;
    }

    private static final Pattern GITHUB_REPO = Pattern.compile(
            "github\\.com/([^/\\s]+)/([^/#?\\s]+)", Pattern.CASE_INSENSITIVE);

    private static String githubSlug(Entry entry) {
        String[] candidates = {entry.releases, entry.sourceCode, entry.website, entry.changelog, entry.issueTracker};
        for (String candidate : candidates) {
            if (isEmpty(candidate)) continue;
            Matcher m = GITHUB_REPO.matcher(candidate);
            if (m.find()) {
                String repo = m.group(2).replaceAll("(?i)\\.git$", "");
                if (!repo.isEmpty()) return m.group(1) + "/" + repo;
            }
        }
        return null;
    }

    private static String releaseSlug(Entry entry) {
        if (latest(entry) != null) return null;
        return githubSlug(entry);
    }

    private static Version latest(Entry entry) {
        List<Version> stable = new ArrayList<>();
        List<Version> all = new ArrayList<>();
        for (Version v : entry.versions) {
            if (v.code < 0 || isEmpty(v.file)) continue;
            all.add(v);
            if (!v.prerelease) stable.add(v);
        }
        List<Version> pool = stable.isEmpty() ? all : stable;
        if (pool.isEmpty()) return null;
        long[] codes = new long[pool.size()];
        String[] names = new String[pool.size()];
        String[] archs = new String[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            codes[i] = pool.get(i).code;
            names[i] = pool.get(i).name;
            archs[i] = pool.get(i).arch;
        }
        int index = DeviceAbi.pickBest(codes, names, archs, pool.size());
        return index < 0 ? null : pool.get(index);
    }

    private static String sourceUrl(Entry entry, String base) {
        if (!isEmpty(entry.sourceCode)) return entry.sourceCode;
        if (!isEmpty(entry.website)) return entry.website;
        return base;
    }

    private static String stripQuery(String url) {
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }

    private static String resolve(String base, String name) {
        if (isEmpty(name)) return null;
        if (name.startsWith("http://") || name.startsWith("https://")) return name;
        return name.startsWith("/") ? base + name : base + "/" + name;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static Parsed parse(File file, PackageFilter filter) throws Exception {
        Parsed parsed = new Parsed();
        try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("repo".equals(name) && reader.peek() == JsonToken.BEGIN_OBJECT) {
                    readRepo(reader, parsed);
                } else if ("packages".equals(name) && reader.peek() == JsonToken.BEGIN_OBJECT) {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String pkg = reader.nextName();
                        if (filter != null && !filter.accept(pkg)) {
                            reader.skipValue();
                            continue;
                        }
                        Entry entry = readPackage(reader);
                        if (entry != null) parsed.entries.put(pkg, entry);
                    }
                    reader.endObject();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }
        return parsed;
    }

    private static void readRepo(JsonReader reader, Parsed parsed) throws Exception {
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if ("categories".equals(key) && reader.peek() == JsonToken.BEGIN_OBJECT) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String id = reader.nextName();
                    if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                        reader.skipValue();
                        continue;
                    }
                    String label = null;
                    reader.beginObject();
                    while (reader.hasNext()) {
                        if ("name".equals(reader.nextName())) label = readLocalized(reader);
                        else reader.skipValue();
                    }
                    reader.endObject();
                    if (!isEmpty(label)) parsed.categoryNames.put(id, label);
                }
                reader.endObject();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
    }

    private static Entry readPackage(JsonReader reader) throws Exception {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue();
            return null;
        }
        Entry entry = new Entry();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if ("metadata".equals(key) && reader.peek() == JsonToken.BEGIN_OBJECT) {
                readMetadata(reader, entry);
            } else if ("versions".equals(key) && reader.peek() == JsonToken.BEGIN_OBJECT) {
                reader.beginObject();
                while (reader.hasNext()) {
                    reader.nextName();
                    Version v = readVersion(reader);
                    if (v != null) entry.versions.add(v);
                }
                reader.endObject();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return entry;
    }

    private static void readMetadata(JsonReader reader, Entry entry) throws Exception {
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            switch (key) {
                case "name": entry.name = readLocalized(reader); break;
                case "summary": entry.summary = readLocalized(reader); break;
                case "description": entry.description = readLocalized(reader); break;
                case "icon": entry.icon = readLocalizedFile(reader); break;
                case "screenshots": entry.screenshots = readScreenshots(reader); break;
                case "categories": entry.categories = readStringArray(reader); break;
                case "license": entry.license = readString(reader); break;
                case "sourceCode": entry.sourceCode = readString(reader); break;
                case "webSite": entry.website = readString(reader); break;
                case "issueTracker": entry.issueTracker = readString(reader); break;
                case "translation": entry.translation = readString(reader); break;
                case "changelog": entry.changelog = readString(reader); break;
                case "releases": entry.releases = readString(reader); break;
                case "authorName": entry.authorName = readString(reader); break;
                case "donate": entry.donate = readFirstString(reader); break;
                case "added": entry.added = readLong(reader); break;
                case "lastUpdated": entry.lastUpdated = readLong(reader); break;
                default: reader.skipValue();
            }
        }
        reader.endObject();
    }

    private static Version readVersion(JsonReader reader) throws Exception {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue();
            return null;
        }
        Version v = new Version();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if ("file".equals(key) && reader.peek() == JsonToken.BEGIN_OBJECT) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String field = reader.nextName();
                    switch (field) {
                        case "name": v.file = readString(reader); break;
                        case "size": v.size = readLong(reader); break;
                        case "sha256": v.hash = readString(reader); break;
                        default: reader.skipValue();
                    }
                }
                reader.endObject();
            } else if ("manifest".equals(key) && reader.peek() == JsonToken.BEGIN_OBJECT) {
                readManifest(reader, v);
            } else if ("antiFeatures".equals(key) && reader.peek() == JsonToken.BEGIN_OBJECT) {
                reader.beginObject();
                while (reader.hasNext()) {
                    v.antiFeatures.add(reader.nextName());
                    reader.skipValue();
                }
                reader.endObject();
            } else if ("releaseChannels".equals(key)) {
                v.prerelease = !readStringArray(reader).isEmpty();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return v;
    }

    private static void readManifest(JsonReader reader, Version v) throws Exception {
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            switch (key) {
                case "versionName": v.name = readString(reader); break;
                case "versionCode":
                    if (reader.peek() == JsonToken.NUMBER || reader.peek() == JsonToken.STRING) v.code = readLong(reader);
                    else reader.skipValue();
                    break;
                case "nativecode": {
                    List<String> abis = readStringArray(reader);
                    v.arch = abis.isEmpty() ? null : TextUtils.join(", ", abis);
                    break;
                }
                case "signer":
                    if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                        reader.beginObject();
                        while (reader.hasNext()) {
                            if ("sha256".equals(reader.nextName())) {
                                List<String> hashes = readStringArray(reader);
                                if (!hashes.isEmpty()) v.signer = hashes.get(0);
                            } else {
                                reader.skipValue();
                            }
                        }
                        reader.endObject();
                    } else {
                        reader.skipValue();
                    }
                    break;
                case "usesPermission":
                case "usesPermissionSdk23":
                    readPermissions(reader, v.permissions);
                    break;
                default: reader.skipValue();
            }
        }
        reader.endObject();
    }

    private static void readPermissions(JsonReader reader, List<String> target) throws Exception {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue();
            return;
        }
        reader.beginArray();
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue();
                continue;
            }
            String permission = null;
            reader.beginObject();
            while (reader.hasNext()) {
                if ("name".equals(reader.nextName())) permission = readString(reader);
                else reader.skipValue();
            }
            reader.endObject();
            if (!isEmpty(permission)) target.add(permission);
        }
        reader.endArray();
    }

    private static String readLocalized(JsonReader reader) throws Exception {
        JsonToken token = reader.peek();
        if (token == JsonToken.STRING) return reader.nextString();
        if (token != JsonToken.BEGIN_OBJECT) {
            reader.skipValue();
            return null;
        }
        Map<String, String> values = new LinkedHashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String locale = reader.nextName();
            if (reader.peek() == JsonToken.STRING) values.put(locale, reader.nextString());
            else reader.skipValue();
        }
        reader.endObject();
        return pick(values);
    }

    private static String readLocalizedFile(JsonReader reader) throws Exception {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue();
            return null;
        }
        Map<String, String> values = new LinkedHashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String locale = reader.nextName();
            String name = readFileName(reader);
            if (name != null) values.put(locale, name);
        }
        reader.endObject();
        return pick(values);
    }

    private static List<String> readScreenshots(JsonReader reader) throws Exception {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue();
            return new ArrayList<>();
        }
        Map<String, List<String>> byType = new HashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String type = reader.nextName();
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue();
                continue;
            }
            Map<String, List<String>> byLocale = new LinkedHashMap<>();
            reader.beginObject();
            while (reader.hasNext()) {
                String locale = reader.nextName();
                List<String> files = new ArrayList<>();
                if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        String name = readFileName(reader);
                        if (name != null) files.add(name);
                    }
                    reader.endArray();
                } else {
                    reader.skipValue();
                }
                if (!files.isEmpty()) byLocale.put(locale, files);
            }
            reader.endObject();
            List<String> chosen = null;
            for (String pref : LOCALES) {
                if (byLocale.containsKey(pref)) { chosen = byLocale.get(pref); break; }
            }
            if (chosen == null && !byLocale.isEmpty()) chosen = byLocale.values().iterator().next();
            if (chosen != null) byType.put(type, chosen);
        }
        reader.endObject();
        for (String type : SCREENSHOT_TYPES) {
            List<String> list = byType.get(type);
            if (list != null) return list;
        }
        return new ArrayList<>();
    }

    private static String readFileName(JsonReader reader) throws Exception {
        JsonToken token = reader.peek();
        if (token == JsonToken.STRING) return reader.nextString();
        if (token != JsonToken.BEGIN_OBJECT) {
            reader.skipValue();
            return null;
        }
        String name = null;
        reader.beginObject();
        while (reader.hasNext()) {
            if ("name".equals(reader.nextName())) name = readString(reader);
            else reader.skipValue();
        }
        reader.endObject();
        return name;
    }

    private static String pick(Map<String, String> values) {
        for (String pref : LOCALES) {
            String v = values.get(pref);
            if (!isEmpty(v)) return v;
        }
        for (String v : values.values()) {
            if (!isEmpty(v)) return v;
        }
        return null;
    }

    private static String readString(JsonReader reader) throws Exception {
        if (reader.peek() == JsonToken.STRING) return reader.nextString();
        reader.skipValue();
        return null;
    }

    private static String readFirstString(JsonReader reader) throws Exception {
        if (reader.peek() == JsonToken.STRING) return reader.nextString();
        List<String> all = readStringArray(reader);
        return all.isEmpty() ? null : all.get(0);
    }

    private static List<String> readStringArray(JsonReader reader) throws Exception {
        List<String> list = new ArrayList<>();
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue();
            return list;
        }
        reader.beginArray();
        while (reader.hasNext()) {
            if (reader.peek() == JsonToken.STRING) list.add(reader.nextString());
            else reader.skipValue();
        }
        reader.endArray();
        return list;
    }

    private static long readLong(JsonReader reader) throws Exception {
        JsonToken token = reader.peek();
        if (token == JsonToken.NUMBER || token == JsonToken.STRING) {
            try {
                return reader.nextLong();
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        reader.skipValue();
        return 0L;
    }
}
