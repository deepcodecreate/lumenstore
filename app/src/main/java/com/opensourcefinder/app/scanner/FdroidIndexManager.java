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
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.opensourcefinder.app.model.ExploreApp;
import com.opensourcefinder.app.model.InstalledAppInfo;
import com.opensourcefinder.app.model.MatchedApp;
import com.opensourcefinder.app.R;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.lang.reflect.Type;

public class FdroidIndexManager {

    public interface ProgressListener {
        void onProgress(int percent, String message);
    }

    public static final class RepoDef {
        public final String name;
        public final String indexUrl;
        public final String cacheKey;
        public RepoDef(String name, String indexUrl, String cacheKey) {
            this.name = name;
            this.indexUrl = indexUrl;
            this.cacheKey = cacheKey;
        }
    }

    public static final String LUMEN_REPO_NAME = MatchedApp.LUMEN_REPO_NAME;
    public static final String LUMEN_INDEX_URL = "https://raw.githubusercontent.com/deepcodecreate/lumenrepo/main/index-v2.json";

    public static final List<RepoDef> DEFAULT_REPOS = new ArrayList<>();
    static {
        DEFAULT_REPOS.add(new RepoDef("F-Droid", "https://f-droid.org/repo/index-v1.json", "fdroid_main"));
        DEFAULT_REPOS.add(new RepoDef("IzzyOnDroid", "https://apt.izzysoft.de/fdroid/repo/index-v1.json", "izzysoft"));
        DEFAULT_REPOS.add(new RepoDef(LUMEN_REPO_NAME, LUMEN_INDEX_URL, "lumenrepo"));
    }

    private static final String LAZY_CACHE_KEY = "izzysoft";
    private static final String FILE_PREFIX = "fdroid-index-v1.json";
    private static final long MAX_INDEX_BYTES = 180L * 1024L * 1024L;

    private static final long EXPLORE_CACHE_TTL_MS = 6L * 60L * 60L * 1000L;
    private static final Object CATALOG_BUILD_LOCK = new Object();
    private static final String EXPLORE_PARSED_CACHE_FILE = "explore-catalog-v7.json";
    private static final Object EXPLORE_CACHE_LOCK = new Object();
    private static final Object INDEX_DOWNLOAD_LOCK = new Object();
    private static volatile List<ExploreApp> exploreCatalogCache;
    private static volatile long exploreCatalogCachedAt;
    private final Context context;
    private volatile StopCondition stopCondition;

    public interface StopCondition {
        boolean shouldStop();
    }

    public FdroidIndexManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public FdroidIndexManager withStopCondition(StopCondition condition) {
        this.stopCondition = condition;
        return this;
    }

    private boolean stopRequested() {
        StopCondition condition = stopCondition;
        return condition != null && condition.shouldStop();
    }

    public static boolean isEnabled(Context context) {
        return context != null;
    }

    public static List<RepoDef> activeRepos(Context context) {
        List<RepoDef> repos = new ArrayList<>();
        boolean lazy = context != null
                && com.opensourcefinder.app.ui.AppPreferences.isLazyIndexEnabled(context);
        for (RepoDef repo : DEFAULT_REPOS) {
            if (LAZY_CACHE_KEY.equals(repo.cacheKey) && !lazy) continue;
            repos.add(repo);
        }
        return repos;
    }

    public static boolean isLazyRepoName(String name) {
        return name != null && name.equalsIgnoreCase("IzzyOnDroid");
    }

    public static void deleteLocalIndexes(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        clearExploreCatalogCache(app);
        for (RepoDef repo : DEFAULT_REPOS) {
            if (!LAZY_CACHE_KEY.equals(repo.cacheKey)) continue;
            String suffix = Integer.toHexString(repo.indexUrl.hashCode());
            new File(app.getFilesDir(), FILE_PREFIX + "_" + suffix).delete();
            new File(app.getFilesDir(), FILE_PREFIX + "_" + suffix + ".tmp").delete();
            app.getSharedPreferences("fdroid_index_" + Integer.toHexString(repo.cacheKey.hashCode()),
                    Context.MODE_PRIVATE).edit().clear().apply();
        }
    }

    public static String repoNameFor(String indexUrl) {
        if (indexUrl == null) return null;
        for (RepoDef repo : DEFAULT_REPOS) {
            if (indexUrl.equals(repo.indexUrl)) return repo.name;
        }
        return null;
    }

    private static final String RELEASE_CACHE_PREFS = "index_github_releases";
    private static final long RELEASE_CACHE_TTL_MS = 5L * 60L * 1000L;

    private boolean forceReleases;

    public FdroidIndexManager setForceReleases(boolean force) {
        this.forceReleases = force;
        return this;
    }

    private CustomRepositoryScanner.ResolvedRelease cachedRelease(String slug, boolean force) {
        return cachedRelease(slug, force, false);
    }

    private CustomRepositoryScanner.ResolvedRelease cachedRelease(String slug, boolean force, boolean ignoreTtl) {
        if (force) return null;
        try {
            String raw = context.getSharedPreferences(RELEASE_CACHE_PREFS, Context.MODE_PRIVATE).getString(slug, null);
            if (raw == null) return null;
            org.json.JSONObject json = new org.json.JSONObject(raw);
            if (!ignoreTtl && System.currentTimeMillis() - json.optLong("at", 0L) > RELEASE_CACHE_TTL_MS) return null;
            String tag = json.optString("tag", null);
            String url = json.optString("url", null);
            if (tag == null || tag.isEmpty() || url == null || url.isEmpty()) return null;
            return new CustomRepositoryScanner.ResolvedRelease(tag, json.optString("asset", null), url);
        } catch (Exception e) {
            return null;
        }
    }

    private void storeRelease(String slug, CustomRepositoryScanner.ResolvedRelease release) {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("tag", release.tag);
            json.put("asset", release.assetName);
            json.put("url", release.downloadUrl);
            json.put("at", System.currentTimeMillis());
            context.getSharedPreferences(RELEASE_CACHE_PREFS, Context.MODE_PRIVATE)
                    .edit().putString(slug, json.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void resolveGithubReleases(Map<String, MatchedApp> apps, boolean force) {
        if (apps == null || apps.isEmpty()) return;
        List<MatchedApp> previous = null;
        CustomRepositoryScanner scanner = null;
        for (MatchedApp app : apps.values()) {
            if (stopRequested()) return;
            if (app == null || !app.isGithubRelease() || app.getDownloadUrl() != null) continue;
            String slug = app.getGithubSlug();
            int cut = slug.indexOf('/');
            if (cut <= 0 || cut >= slug.length() - 1) continue;
            CustomRepositoryScanner.ResolvedRelease release = cachedRelease(slug, force || forceReleases);
            if (release == null) {
                if (scanner == null) scanner = new CustomRepositoryScanner(context);
                try {
                    release = scanner.resolveLatestRelease(slug.substring(0, cut), slug.substring(cut + 1));
                } catch (Exception ignored) {}
                if (release != null) storeRelease(slug, release);
                else release = cachedRelease(slug, false, true);
            }
            if (release != null) {
                app.setLatestTag(release.tag);
                app.setDownloadUrl(release.downloadUrl);
            } else {
                if (previous == null) previous = MatchStore.get(context);
                for (MatchedApp old : previous) {
                    if (old != null && app.getPackageName().equals(old.getPackageName())
                            && slug.equalsIgnoreCase(old.getGithubSlug())
                            && old.getLatestTag() != null && old.getDownloadUrl() != null) {
                        app.setLatestTag(old.getLatestTag());
                        app.setDownloadUrl(old.getDownloadUrl());
                        break;
                    }
                }
            }
            if (app.getLatestTag() != null) {
                String have = app.getInstalledVersionName();
                app.setUpdateAvailable(have != null && !have.isEmpty()
                        && !com.opensourcefinder.app.install.InstallState.sameVersionName(app.getLatestTag(), have)
                        && com.opensourcefinder.app.install.InstallState.isNewer(app.getLatestTag(), have));
            }
        }
    }

    public Map<String, MatchedApp> refreshAndMatchAllDefaults(List<InstalledAppInfo> installedApps, ProgressListener progress) {
        return refreshAndMatchAllDefaults(installedApps, progress, false);
    }

    public Map<String, MatchedApp> refreshAndMatchAllDefaults(List<InstalledAppInfo> installedApps, ProgressListener progress, boolean forceRefresh) {
        Map<String, MatchedApp> merged = new LinkedHashMap<>();
        if (!isEnabled(context)) {
            if (progress != null) progress.onProgress(100, context.getString(R.string.fdroid_index_disabled_short));
            return merged;
        }
        final List<RepoDef> repos = activeRepos(context);
        int repoCount = repos.size();
        int ri = 0;
        for (RepoDef repo : repos) {
            if (stopRequested()) break;
            final int repoIndex = ri;
            try {
                if (progress != null) {
                    progress.onProgress((repoIndex * 100) / repoCount, context.getString(R.string.fdroid_download_index, repo.name));
                }
                Map<String, MatchedApp> part = refreshAndMatch(installedApps, repo.indexUrl, repo.cacheKey, percent -> {
                    if (progress != null) {
                        int overall = (repoIndex * 100 + percent) / repoCount;
                        progress.onProgress(Math.min(99, overall), context.getString(R.string.fdroid_index_progress, repo.name, percent));
                    }
                }, forceRefresh);
                resolveGithubReleases(part, forceRefresh);
                for (Map.Entry<String, MatchedApp> e : part.entrySet()) {
                    MatchedApp incoming = e.getValue();
                    MatchedApp existing = merged.get(e.getKey());

                    if (existing == null
                            || incoming.isLumenSource()
                            || (!existing.isLumenSource()
                                && (!isFdropSource(existing)
                                    || (existing.isLazyIndexSource() && !incoming.isLazyIndexSource())))) {
                        merged.put(e.getKey(), incoming);
                    }
                }
            } catch (Exception ignored) {}
            ri++;
        }
        if (progress != null) progress.onProgress(100, context.getString(R.string.done));
        return merged;
    }

    public Map<String, MatchedApp> refreshAndMatch(List<InstalledAppInfo> installedApps, String indexUrl, String cacheKey) throws Exception {
        return refreshAndMatch(installedApps, indexUrl, cacheKey, null);
    }

    public interface DownloadProgress {
        void onPercent(int percent);
    }

    public Map<String, MatchedApp> refreshAndMatch(List<InstalledAppInfo> installedApps, String indexUrl, String cacheKey, DownloadProgress dlProgress) throws Exception {
        return refreshAndMatch(installedApps, indexUrl, cacheKey, dlProgress, false);
    }

    public Map<String, MatchedApp> refreshAndMatch(List<InstalledAppInfo> installedApps, String indexUrl, String cacheKey, DownloadProgress dlProgress, boolean forceRefresh) throws Exception {
        File file = new File(context.getFilesDir(), FILE_PREFIX + "_" + Integer.toHexString(indexUrl.hashCode()));
        downloadIfNeeded(file, indexUrl, cacheKey, forceRefresh, dlProgress);
        Map<String, InstalledAppInfo> installed = new HashMap<>();
        for (InstalledAppInfo app : installedApps) installed.put(app.getPackageName(), app);
        try {
            return parseInstalled(file, installed, indexUrl);
        } catch (Exception | OutOfMemoryError corrupt) {
            if (forceRefresh) throw corrupt instanceof Exception ? (Exception) corrupt
                    : new IllegalStateException("Corrupt F-Droid index for " + indexUrl, corrupt);
            file.delete();
            downloadIfNeeded(file, indexUrl, cacheKey, true, dlProgress);
            return parseInstalled(file, installed, indexUrl);
        }
    }

    public List<ExploreApp> loadExploreCatalog(Set<String> installedPackages, ProgressListener progress) {
        if (!isEnabled(context)) return new ArrayList<>();
        if (progress == null) {
            List<ExploreApp> cached = getCachedExploreCatalog(installedPackages);
            if (cached != null) return cached;
        }

        synchronized (CATALOG_BUILD_LOCK) {
            if (progress == null) {
                List<ExploreApp> cached = getCachedExploreCatalog(installedPackages);
                if (cached != null) return cached;
            }
            return buildExploreCatalog(installedPackages, progress);
        }
    }

    private List<ExploreApp> buildExploreCatalog(Set<String> installedPackages, ProgressListener progress) {
        Map<String, ExploreApp> merged = new LinkedHashMap<>();
        final List<RepoDef> repos = activeRepos(context);
        int repoCount = repos.size();
        ExecutorService downloadPool = Executors.newFixedThreadPool(Math.max(1, Math.min(2, repoCount)));
        List<Future<File>> downloads = new ArrayList<>();
        try {

            for (int i = 0; i < repoCount; i++) {
                final int repoIndex = i;
                final RepoDef repo = repos.get(i);
                downloads.add(downloadPool.submit(new Callable<File>() {
                    @Override public File call() throws Exception {
                        if (progress != null) {
                            progress.onProgress((repoIndex * 100) / repoCount,
                                    context.getString(R.string.fdroid_loading_index, repo.name));
                        }
                        File file = new File(context.getFilesDir(), FILE_PREFIX + "_" + Integer.toHexString(repo.indexUrl.hashCode()));
                        DownloadProgress dlProgress = percent -> {
                            if (progress != null) {
                                int overall = (repoIndex * 100 + percent) / repoCount;
                                progress.onProgress(Math.min(99, overall),
                                        context.getString(R.string.fdroid_index_progress, repo.name, percent));
                            }
                        };
                        downloadIfNeeded(file, repo.indexUrl, repo.cacheKey, false, dlProgress);
                        return file;
                    }
                }));
            }

            for (int i = 0; i < repoCount; i++) {
                final int repoIndex = i;
                final RepoDef repo = repos.get(i);
                File file;
                try {
                    file = downloads.get(i).get();
                } catch (Exception failedDownload) {
                    continue;
                }
                List<ExploreApp> part;
                try {
                    part = parseExplore(file, repo.indexUrl, repo.name, installedPackages);
                } catch (Exception | OutOfMemoryError corrupt) {
                    try {
                        file.delete();
                        DownloadProgress dlProgress = percent -> {
                            if (progress != null) {
                                int overall = (repoIndex * 100 + percent) / repoCount;
                                progress.onProgress(Math.min(99, overall),
                                        context.getString(R.string.fdroid_index_progress, repo.name, percent));
                            }
                        };
                        downloadIfNeeded(file, repo.indexUrl, repo.cacheKey, true, dlProgress);
                        part = parseExplore(file, repo.indexUrl, repo.name, installedPackages);
                    } catch (Exception | OutOfMemoryError retryFailed) {
                        continue;
                    }
                }
                for (ExploreApp a : part) {
                    ExploreApp existing = merged.get(a.getPackageName());

                    boolean incomingLumen = LUMEN_REPO_NAME.equalsIgnoreCase(a.getRepoName());
                    boolean existingLumen = existing != null && LUMEN_REPO_NAME.equalsIgnoreCase(existing.getRepoName());
                    if (existing == null
                            || incomingLumen
                            || (!existingLumen
                                && ("F-Droid".equalsIgnoreCase(a.getRepoName())
                                    || !"F-Droid".equalsIgnoreCase(existing.getRepoName())))) {
                        merged.put(a.getPackageName(), a);
                    }
                }
            }
        } finally {
            downloadPool.shutdownNow();
        }

        List<ExploreApp> catalog = new ArrayList<>(merged.values());
        List<String> popularPackages = loadPopularPackages();
        Map<String, Integer> popularRank = new HashMap<>();
        for (int i = 0; i < popularPackages.size(); i++) popularRank.put(popularPackages.get(i), i);
        for (ExploreApp app : catalog) {
            Integer rank = popularRank.get(app.getPackageName());
            app.setPopularRank(rank == null ? Integer.MAX_VALUE : rank);
        }
        if (!catalog.isEmpty()) {
            synchronized (EXPLORE_CACHE_LOCK) {
                exploreCatalogCache = new ArrayList<>(catalog.size());
                for (ExploreApp app : catalog) exploreCatalogCache.add(app.copy());
                exploreCatalogCachedAt = System.currentTimeMillis();
            }
            writeParsedExploreCache(catalog);
        }
        if (progress != null) progress.onProgress(100, context.getString(R.string.done));
        return catalog;
    }

    public static void clearExploreCatalogCache(Context context) {
        if (context == null) return;
        synchronized (EXPLORE_CACHE_LOCK) {
            exploreCatalogCache = null;
            exploreCatalogCachedAt = 0L;
        }
        new File(context.getApplicationContext().getFilesDir(), EXPLORE_PARSED_CACHE_FILE).delete();
    }

    private List<ExploreApp> getCachedExploreCatalog(Set<String> installedPackages) {
        List<ExploreApp> cached = exploreCatalogCache;
        if (cached == null || System.currentTimeMillis() - exploreCatalogCachedAt > EXPLORE_CACHE_TTL_MS) {
            cached = readParsedExploreCache();
            if (cached != null && !cached.isEmpty()) {
                synchronized (EXPLORE_CACHE_LOCK) {
                    exploreCatalogCache = new ArrayList<>(cached.size());
                    for (ExploreApp app : cached) exploreCatalogCache.add(app.copy());
                    exploreCatalogCachedAt = System.currentTimeMillis();
                }
            }
        }
        if (cached == null || cached.isEmpty()) return null;
        Set<String> installed = installedPackages == null
                ? java.util.Collections.emptySet() : installedPackages;
        List<ExploreApp> result = new ArrayList<>(cached.size());
        for (ExploreApp cachedApp : cached) {
            ExploreApp app = cachedApp.copy();
            app.setInstalled(installed.contains(app.getPackageName()));
            app.setInstalledVersionCode(0L);
            result.add(app);
        }
        return result;
    }

    private List<ExploreApp> readParsedExploreCache() {
        File file = new File(context.getFilesDir(), EXPLORE_PARSED_CACHE_FILE);
        if (!file.isFile() || file.length() <= 0
                || System.currentTimeMillis() - file.lastModified() > EXPLORE_CACHE_TTL_MS) return null;
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<ExploreApp>>(){}.getType();
            List<ExploreApp> result = new Gson().fromJson(reader, type);
            return result == null ? null : result;
        } catch (Exception ignored) {
            file.delete();
            return null;
        }
    }

    private void writeParsedExploreCache(List<ExploreApp> catalog) {
        if (catalog == null || catalog.isEmpty()) return;
        File file = new File(context.getFilesDir(), EXPLORE_PARSED_CACHE_FILE);
        File temp = new File(context.getFilesDir(), EXPLORE_PARSED_CACHE_FILE + ".tmp");
        try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8)) {
            new Gson().toJson(catalog, writer);
        } catch (Exception ignored) {
            temp.delete();
            return;
        }
        if (file.exists()) file.delete();
        if (!temp.renameTo(file)) temp.delete();
    }

    private List<String> loadPopularPackages() {
        File cache = new File(context.getFilesDir(), "fdroid-most-downloaded.json");
        long now = System.currentTimeMillis();
        if (!cache.exists() || now - cache.lastModified() > 7L * 24 * 60 * 60 * 1000) {
            try {
                HttpURLConnection connection = com.opensourcefinder.app.network.HttpClients.open(new URL("https://grote.gitlab.io/fdroid-metrics-distilled/top/50.json"));
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent", "OpenSourceFinder/1.0");
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    File temp = new File(context.getFilesDir(), "fdroid-most-downloaded.json.tmp");
                    try (InputStream in = new BufferedInputStream(connection.getInputStream()); FileOutputStream out = new FileOutputStream(temp)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                    }
                    if (temp.length() > 0) {
                        if (cache.exists()) cache.delete();
                        temp.renameTo(cache);
                    } else {
                        temp.delete();
                    }
                }
                connection.disconnect();
            } catch (Exception ignored) {}
        }
        if (!cache.exists()) return readPopularAsset();
        try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(cache), StandardCharsets.UTF_8))) {
            return readStringArray(reader);
        } catch (Exception e) {
            return readPopularAsset();
        }
    }

    private List<String> readPopularAsset() {
        List<String> result = new ArrayList<>();
        try (InputStream in = context.getResources().openRawResource(R.raw.most_downloaded_apps);
             JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return readStringArray(reader);
        } catch (Exception ignored) {
            return result;
        }
    }

    private void downloadIfNeeded(File file, String indexUrl, String cacheKey, boolean forceRefresh, DownloadProgress dlProgress) throws Exception {
        Exception last = null;
        for (String candidate : mirrorCandidates(indexUrl)) {
            if (stopRequested()) throw new java.io.InterruptedIOException("index download stopped");
            try {
                downloadFrom(candidate, file, indexUrl, cacheKey, forceRefresh, dlProgress);
                return;
            } catch (java.io.InterruptedIOException e) {
                throw e;
            } catch (Exception e) {
                last = e;
            }
        }
        if (file.exists() && isHealthyIndexFile(file)) {
            if (dlProgress != null) dlProgress.onPercent(100);
            return;
        }
        throw last != null ? last : new IllegalStateException("F-Droid index unavailable");
    }

    private static List<String> mirrorCandidates(String indexUrl) {
        List<String> urls = new ArrayList<>();
        urls.add(indexUrl);
        String official = "https://f-droid.org/repo/";
        if (indexUrl.startsWith(official)) {
            String tail = indexUrl.substring(official.length());
            urls.add("https://cloudflare.f-droid.org/repo/" + tail);
            urls.add("https://ftp.fau.de/fdroid/repo/" + tail);
            urls.add("https://mirror.cyberbits.eu/fdroid/repo/" + tail);
            urls.add("https://mirrors.tuna.tsinghua.edu.cn/fdroid/repo/" + tail);
        }
        String rawPrefix = "https://raw.githubusercontent.com/";
        if (indexUrl.startsWith(rawPrefix)) {
            String[] parts = indexUrl.substring(rawPrefix.length()).split("/", 4);
            if (parts.length == 4) {
                urls.add("https://cdn.jsdelivr.net/gh/" + parts[0] + "/" + parts[1] + "@" + parts[2] + "/" + parts[3]);
            }
        }
        return urls;
    }

    private void downloadFrom(String fetchUrl, File file, String indexUrl, String cacheKey, boolean forceRefresh, DownloadProgress dlProgress) throws Exception {
        synchronized (file.getAbsolutePath().intern()) {
            if (stopRequested()) throw new java.io.InterruptedIOException("index download stopped");
            if (file.exists() && !isHealthyIndexFile(file)) {
                file.delete();
            }

            HttpURLConnection connection = com.opensourcefinder.app.network.HttpClients.open(new URL(fetchUrl));
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(120000);
            connection.setRequestProperty("User-Agent", "OpenSourceFinder/1.0");
            connection.setUseCaches(false);
            android.content.SharedPreferences prefs = context.getSharedPreferences(
                    "fdroid_index_" + Integer.toHexString(cacheKey.hashCode()), Context.MODE_PRIVATE);
            if (!forceRefresh && file.exists()) {
                String etag = prefs.getString("etag", null);
                if (etag != null) connection.setRequestProperty("If-None-Match", etag);
                String modified = prefs.getString("last_modified", null);
                if (modified != null) connection.setRequestProperty("If-Modified-Since", modified);
            }
            int code = connection.getResponseCode();
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED && file.exists() && isHealthyIndexFile(file)) {
                connection.disconnect();
                if (dlProgress != null) dlProgress.onPercent(100);
                return;
            }
            if (code != HttpURLConnection.HTTP_OK) {
                connection.disconnect();
                if (file.exists() && isHealthyIndexFile(file)) {
                    if (dlProgress != null) dlProgress.onPercent(100);
                    return;
                }
                throw new IllegalStateException("F-Droid index HTTP " + code);
            }
            long total = connection.getContentLengthLong();
            if (total > MAX_INDEX_BYTES) {
                connection.disconnect();
                throw new IllegalStateException("F-Droid index is too large");
            }
            long done = 0;
            File temp = new File(context.getFilesDir(), FILE_PREFIX + "_" + Integer.toHexString(indexUrl.hashCode()) + ".tmp");
            if (temp.exists()) temp.delete();
            String newEtag = connection.getHeaderField("ETag");
            String newModified = connection.getHeaderField("Last-Modified");
            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[65536];
                int read;
                int last = -1;
                while ((read = in.read(buffer)) != -1) {
                    if (stopRequested()) throw new java.io.InterruptedIOException("index download stopped");
                    done += read;
                    if (done > MAX_INDEX_BYTES) {
                        throw new IllegalStateException("F-Droid index is too large");
                    }
                    out.write(buffer, 0, read);
                    if (dlProgress != null && total > 0) {
                        int p = (int) Math.min(100, done * 100 / total);
                        if (p != last) { last = p; dlProgress.onPercent(p); }
                    } else if (dlProgress != null) {
                        int p = (int) Math.min(90, done / (512 * 1024));
                        if (p != last) { last = p; dlProgress.onPercent(p); }
                    }
                }
            } finally {
                connection.disconnect();
            }
            if (temp.length() == 0 || (total > 0 && done < total) || !isHealthyIndexFile(temp)) {
                temp.delete();
                if (file.exists() && isHealthyIndexFile(file)) {
                    if (dlProgress != null) dlProgress.onPercent(100);
                    return;
                }
                throw new IllegalStateException("Incomplete or invalid F-Droid index download");
            }
            if (!temp.renameTo(file)) {
                if (file.exists()) file.delete();
                if (!temp.renameTo(file)) throw new IllegalStateException("Cannot store index");
            }
            android.content.SharedPreferences.Editor ed = prefs.edit();
            if (newEtag != null) ed.putString("etag", newEtag);
            if (newModified != null) ed.putString("last_modified", newModified);
            ed.apply();
            if (dlProgress != null) dlProgress.onPercent(100);
        }
    }

    private boolean isHealthyIndexFile(File file) {
        if (file == null || !file.isFile() || file.length() <= 0 || file.length() > MAX_INDEX_BYTES) {
            return false;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long start = Math.max(0, raf.length() - 4096);
            raf.seek(start);
            byte[] tail = new byte[(int) (raf.length() - start)];
            raf.readFully(tail);
            for (int i = tail.length - 1; i >= 0; i--) {
                if (!Character.isWhitespace((char) tail[i])) {
                    return tail[i] == '}';
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static final class AppMeta {
        String name;
        String summary;
        String description;
        String icon;
        List<String> antiFeatures = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        List<String> screenshots = new ArrayList<>();
        long added;
        long lastUpdated;
        String donate;
        String authorName;
        String license;
        String sourceCode;
        String website;
        String issueTracker;
        String translation;
        String changelog;
    }

    private static final String[] LOCALE_PREFERENCE = {"en-US", "en", "fa", "fa-IR"};

    private static final class LocaleMeta {
        String name;
        String summary;
        String description;
        String icon;
        List<String> screenshots = new ArrayList<>();
    }

    private static Map<String, LocaleMeta> parseLocalized(JsonReader reader) throws Exception {
        Map<String, LocaleMeta> result = new LinkedHashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String locale = reader.nextName();
            LocaleMeta lm = new LocaleMeta();
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                switch (key) {
                    case "name": lm.name = nextString(reader); break;
                    case "summary": lm.summary = nextString(reader); break;
                    case "description": lm.description = nextString(reader); break;
                    case "icon": lm.icon = nextString(reader); break;
                    case "phoneScreenshots":
                        if (reader.peek() == JsonToken.NULL) { reader.nextNull(); break; }
                        reader.beginArray();
                        while (reader.hasNext()) {
                            String s = nextString(reader);
                            if (s != null) lm.screenshots.add(locale + "/phoneScreenshots/" + s);
                        }
                        reader.endArray();
                        break;
                    default: reader.skipValue();
                }
            }
            reader.endObject();
            result.put(locale, lm);
        }
        reader.endObject();
        return result;
    }

    private interface StringExtractor { String get(LocaleMeta lm); }

    private static String pickLocalizedString(Map<String, LocaleMeta> localized, StringExtractor ex) {
        if (localized == null) return null;
        for (String pref : LOCALE_PREFERENCE) {
            LocaleMeta lm = localized.get(pref);
            if (lm != null) {
                String v = ex.get(lm);
                if (!isEmpty(v)) return v;
            }
        }
        for (LocaleMeta lm : localized.values()) {
            String v = ex.get(lm);
            if (!isEmpty(v)) return v;
        }
        return null;
    }

    private static List<String> pickLocalizedScreenshots(Map<String, LocaleMeta> localized) {
        if (localized == null) return new ArrayList<>();
        for (String pref : LOCALE_PREFERENCE) {
            LocaleMeta lm = localized.get(pref);
            if (lm != null && !lm.screenshots.isEmpty()) return lm.screenshots;
        }
        for (LocaleMeta lm : localized.values()) {
            if (!lm.screenshots.isEmpty()) return lm.screenshots;
        }
        return new ArrayList<>();
    }

    private static final class IconRef {
        final String locale;
        final String file;
        IconRef(String locale, String file) { this.locale = locale; this.file = file; }
    }

    private static IconRef pickLocalizedIcon(Map<String, LocaleMeta> localized) {
        if (localized == null) return null;
        for (String pref : LOCALE_PREFERENCE) {
            LocaleMeta lm = localized.get(pref);
            if (lm != null && !isEmpty(lm.icon)) return new IconRef(pref, lm.icon);
        }
        for (Map.Entry<String, LocaleMeta> e : localized.entrySet()) {
            if (!isEmpty(e.getValue().icon)) return new IconRef(e.getKey(), e.getValue().icon);
        }
        return null;
    }

    private static void applyLocalizedFallback(AppMeta meta, Map<String, LocaleMeta> localized) {
        if (localized == null || localized.isEmpty()) return;
        if (isEmpty(meta.name)) meta.name = pickLocalizedString(localized, lm -> lm.name);
        if (isEmpty(meta.summary)) meta.summary = pickLocalizedString(localized, lm -> lm.summary);
        if (isEmpty(meta.description)) meta.description = pickLocalizedString(localized, lm -> lm.description);
    }

    private static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    private static List<String> readPermissionArray(JsonReader reader) throws Exception {
        List<String> list = new ArrayList<>();
        if (reader.peek() == JsonToken.NULL) { reader.nextNull(); return list; }
        if (reader.peek() != JsonToken.BEGIN_ARRAY) { reader.skipValue(); return list; }
        reader.beginArray();
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_ARRAY) { reader.skipValue(); continue; }
            reader.beginArray();
            String permission = null;
            if (reader.hasNext()) permission = nextString(reader);
            while (reader.hasNext()) reader.skipValue();
            reader.endArray();
            if (permission != null && !permission.isEmpty()) list.add(permission);
        }
        reader.endArray();
        return list;
    }

    private static List<String> readStringArray(JsonReader reader) throws Exception {
        List<String> list = new ArrayList<>();
        if (reader.peek() == JsonToken.NULL) { reader.nextNull(); return list; }
        reader.beginArray();
        while (reader.hasNext()) {
            String s = nextString(reader);
            if (s != null) list.add(s);
        }
        reader.endArray();
        return list;
    }

    private static List<String> mergeAntiFeatures(List<String> appLevel, List<String> latestBuild) {
        Map<String, String> unique = new LinkedHashMap<>();
        addAntiFeatures(unique, appLevel);
        addAntiFeatures(unique, latestBuild);
        return new ArrayList<>(unique.values());
    }

    private static void addAntiFeatures(Map<String, String> target, List<String> features) {
        if (features == null) return;
        for (String feature : features) {
            if (feature == null || feature.trim().isEmpty()) continue;
            String value = feature.trim();
            target.putIfAbsent(value.toLowerCase(java.util.Locale.ROOT), value);
        }
    }

    private static final class V1Build {
        String versionName;
        long versionCode = -1;
        String apkName;
        long size;
        List<String> antiFeatures;
        List<String> permissions;
        String nativecode;
        String signer;
        String hash;
    }

    private static V1Build pickV1Build(List<V1Build> candidates) {
        if (candidates.isEmpty()) return null;
        long[] codes = new long[candidates.size()];
        String[] names = new String[candidates.size()];
        String[] archs = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            codes[i] = candidates.get(i).versionCode;
            names[i] = candidates.get(i).versionName;
            archs[i] = candidates.get(i).nativecode;
        }
        int index = com.opensourcefinder.app.model.DeviceAbi.pickBest(codes, names, archs, candidates.size());
        return index < 0 ? null : candidates.get(index);
    }

    private Map<String, MatchedApp> parseInstalled(File file, Map<String, InstalledAppInfo> installed, String indexUrl) throws Exception {
        if (FdroidIndexV2Parser.isV2(indexUrl)) {
            return FdroidIndexV2Parser.parseInstalled(file, installed, indexUrl, repoNameFor(indexUrl));
        }
        Map<String, MatchedApp> result = new HashMap<>();
        Map<String, AppMeta> metas = new HashMap<>();

        Map<String, long[]> pkgCodes = new HashMap<>();
        Map<String, String[]> pkgApks = new HashMap<>();
        String base = repoBase(indexUrl);
        try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("apps".equals(name)) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        reader.beginObject();
                        String pkg = null;
                        AppMeta meta = new AppMeta();
                        while (reader.hasNext()) {
                            String key = reader.nextName();
                            switch (key) {
                                case "packageName": pkg = nextString(reader); break;
                                case "name": meta.name = nextString(reader); break;
                                case "summary": meta.summary = nextString(reader); break;
                                case "description": meta.description = nextString(reader); break;
                                case "icon": meta.icon = nextString(reader); break;
                                case "sourceCode": meta.sourceCode = nextString(reader); break;
                                default: reader.skipValue();
                            }
                        }
                        reader.endObject();
                        if (pkg != null && installed.containsKey(pkg)) metas.put(pkg, meta);
                    }
                    reader.endArray();
                } else if ("packages".equals(name)) {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String packageName = reader.nextName();
                        if (!installed.containsKey(packageName)) { reader.skipValue(); continue; }
                        long maxCode = -1;
                        String maxName = null, maxApk = null;
                        List<V1Build> candidates = new ArrayList<>();
                        reader.beginArray();
                        while (reader.hasNext()) {
                            reader.beginObject();
                            V1Build build = new V1Build();
                            while (reader.hasNext()) {
                                String key = reader.nextName();
                                switch (key) {
                                    case "versionName": build.versionName = nextString(reader); break;
                                    case "versionCode": build.versionCode = nextLong(reader); break;
                                    case "apkName": build.apkName = nextString(reader); break;
                                    case "nativecode":
                                        List<String> abis = readStringArray(reader);
                                        build.nativecode = abis.isEmpty() ? null : android.text.TextUtils.join(", ", abis);
                                        break;
                                    default: reader.skipValue();
                                }
                            }
                            reader.endObject();
                            if (build.versionCode >= 0 && build.apkName != null && !build.apkName.isEmpty()) candidates.add(build);
                        }
                        reader.endArray();
                        V1Build chosen = pickV1Build(candidates);
                        if (chosen != null) {
                            maxCode = chosen.versionCode;
                            maxName = chosen.versionName;
                            maxApk = chosen.apkName;
                        }
                        if (maxCode >= 0 && maxApk != null) {
                            pkgCodes.put(packageName, new long[]{maxCode});
                            pkgApks.put(packageName, new String[]{maxName, maxApk});
                        }
                    }
                    reader.endObject();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }
        for (Map.Entry<String, long[]> e : pkgCodes.entrySet()) {
            String packageName = e.getKey();
            InstalledAppInfo app = installed.get(packageName);
            if (app == null) continue;
            String[] apk = pkgApks.get(packageName);
            long maxCode = e.getValue()[0];
            String maxName = apk[0];
            String maxApk = apk[1];
            MatchedApp matched = new MatchedApp();
            matched.setPackageName(app.getPackageName());
            matched.setAppLabel(app.getAppLabel());
            matched.setInstalledVersionName(app.getVersionName());
            matched.setLatestTag(maxName != null ? maxName : String.valueOf(maxCode));
            matched.setLatestVersionCode(maxCode);
            matched.setUpdateAvailable(maxCode > app.getVersionCode());
            matched.setSourceType("fdroid");
            matched.setOrigin("auto");
            AppMeta meta = metas.get(packageName);
            matched.setSourceUrl(meta != null && !isEmpty(meta.sourceCode)
                    ? meta.sourceCode
                    : base + "/packages/" + app.getPackageName() + "/");
            matched.setDownloadUrl(base + "/repo/" + maxApk);
            if (meta != null) {
                String desc = meta.description != null && !meta.description.isEmpty()
                        ? meta.description
                        : (meta.summary != null ? meta.summary : null);
                matched.setDescription(desc);
                if (meta.name != null && !meta.name.isEmpty()) matched.setAppLabel(meta.name);
            }
            result.put(packageName, matched);
        }
        return result;
    }

private List<ExploreApp> parseExplore(File file, String indexUrl, String repoName, Set<String> installedPackages) throws Exception {
        if (FdroidIndexV2Parser.isV2(indexUrl)) {
            return FdroidIndexV2Parser.parseExplore(file, indexUrl, repoName, installedPackages);
        }
        List<ExploreApp> result = new ArrayList<>();
        Map<String, AppMeta> metas = new HashMap<>();
        Map<String, Map<String, LocaleMeta>> localizedByPkg = new HashMap<>();
        Map<String, long[]> versions = new HashMap<>();
        Map<String, String[]> apkInfo = new HashMap<>();
             Map<String, long[]> apkExtra = new HashMap<>();
        Map<String, List<String>> antiFeaturesByPkg = new HashMap<>();
        Map<String, List<ExploreApp.VersionInfo>> previousVersionsByPkg = new HashMap<>();
             Map<String, String[]> packageExtraByPkg = new HashMap<>();
        Map<String, List<String>> permissionsByPkg = new HashMap<>();
        String base = repoBase(indexUrl);

        try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("apps".equals(name)) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        reader.beginObject();
                        String pkg = null;
                        AppMeta meta = new AppMeta();
                        Map<String, LocaleMeta> localized = null;
                        while (reader.hasNext()) {
                            String key = reader.nextName();
                            switch (key) {
                                case "packageName": pkg = nextString(reader); break;
                                case "name": meta.name = nextString(reader); break;
                                case "summary": meta.summary = nextString(reader); break;
                                case "description": meta.description = nextString(reader); break;
                                case "icon": meta.icon = nextString(reader); break;
                                case "authorName": meta.authorName = nextString(reader); break;
                                case "license": meta.license = nextString(reader); break;
                                case "donate": meta.donate = nextString(reader); break;
                                case "sourceCode": meta.sourceCode = nextString(reader); break;
                                case "webSite": meta.website = nextString(reader); break;
                                case "issueTracker": meta.issueTracker = nextString(reader); break;
                                case "translation": meta.translation = nextString(reader); break;
                                case "changelog": meta.changelog = nextString(reader); break;
                                case "added": meta.added = nextLong(reader); break;
                                case "lastUpdated": meta.lastUpdated = nextLong(reader); break;
                                case "antiFeatures": meta.antiFeatures = readStringArray(reader); break;
                                case "categories": meta.categories = readStringArray(reader); break;
                                case "localized": localized = parseLocalized(reader); break;
                                default: reader.skipValue();
                            }
                        }
                        reader.endObject();
                        if (pkg != null) {
                            metas.put(pkg, meta);
                            if (localized != null) localizedByPkg.put(pkg, localized);
                        }
                    }
                    reader.endArray();
                } else if ("packages".equals(name)) {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String packageName = reader.nextName();
                        long maxCode = -1;
                        String maxName = null, maxApk = null;
                        long maxSize = 0;
                        List<String> maxAntiFeatures = null;
                        List<ExploreApp.VersionInfo> allVersions = new ArrayList<>();
                        List<String> maxPermissions = new ArrayList<>();
                        String maxNativecode = null, maxSigner = null, maxHash = null;
                        List<V1Build> candidates = new ArrayList<>();
                        reader.beginArray();
                        while (reader.hasNext()) {
                            reader.beginObject();
                            String versionName = null, apkName = null;
                            long versionCode = -1;
                            long size = 0;
                            String nativecode = null, signer = null, hash = null;
                            List<String> permissions = new ArrayList<>();
                            List<String> antiFeatures = null;
                            while (reader.hasNext()) {
                                String key = reader.nextName();
                                switch (key) {
                                    case "versionName": versionName = nextString(reader); break;
                                    case "versionCode": versionCode = nextLong(reader); break;
                                    case "apkName": apkName = nextString(reader); break;
                                    case "size": size = nextLong(reader); break;
                                    case "nativecode":
                                        List<String> nc = readStringArray(reader); nativecode = nc.isEmpty() ? null : android.text.TextUtils.join(", ", nc); break;
                                    case "signer": signer = nextString(reader); break;
                                    case "hash": hash = nextString(reader); break;
                                    case "sig": reader.skipValue(); break;
                                    case "uses-permission": permissions = readPermissionArray(reader); break;
                                    case "uses-permission-sdk-23":
                                        permissions.addAll(readPermissionArray(reader)); break;
                                    case "antiFeatures": antiFeatures = readStringArray(reader); break;
                                    default: reader.skipValue();
                                }
                            }
                            reader.endObject();
                            if (versionCode >= 0 && apkName != null && !apkName.isEmpty()) {
                                allVersions.add(new ExploreApp.VersionInfo(versionName, versionCode, size,
                                        base + "/repo/" + apkName, nativecode, signer));
                            }
                            if (versionCode >= 0 && apkName != null && !apkName.isEmpty()) {
                                V1Build build = new V1Build();
                                build.versionName = versionName;
                                build.versionCode = versionCode;
                                build.apkName = apkName;
                                build.size = size;
                                build.antiFeatures = antiFeatures;
                                build.permissions = permissions;
                                build.nativecode = nativecode;
                                build.signer = signer;
                                build.hash = hash;
                                candidates.add(build);
                            }
                        }
                        reader.endArray();
                        V1Build chosen = pickV1Build(candidates);
                        if (chosen != null) {
                            maxCode = chosen.versionCode;
                            maxName = chosen.versionName;
                            maxApk = chosen.apkName;
                            maxSize = chosen.size;
                            maxAntiFeatures = chosen.antiFeatures;
                            maxPermissions = chosen.permissions;
                            maxNativecode = chosen.nativecode;
                            maxSigner = chosen.signer;
                            maxHash = chosen.hash;
                        }
                        if (maxCode >= 0 && maxApk != null) {
                            versions.put(packageName, new long[]{maxCode});
                            apkInfo.put(packageName, new String[]{maxName, maxApk});
                            apkExtra.put(packageName, new long[]{maxSize});
                            if (maxAntiFeatures != null) antiFeaturesByPkg.put(packageName, maxAntiFeatures);
                            previousVersionsByPkg.put(packageName, allVersions);
                            packageExtraByPkg.put(packageName, new String[]{maxNativecode, maxSigner, maxHash});
                            if (maxPermissions != null && !maxPermissions.isEmpty()) permissionsByPkg.put(packageName, maxPermissions);
                        }
                    }
                    reader.endObject();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }

        for (Map.Entry<String, AppMeta> e : metas.entrySet()) {
            String pkg = e.getKey();
            String[] apk = apkInfo.get(pkg);
            long[] ver = versions.get(pkg);
            if (apk == null || ver == null) continue;
            AppMeta meta = e.getValue();
            Map<String, LocaleMeta> localized = localizedByPkg.get(pkg);
            applyLocalizedFallback(meta, localized);

            ExploreApp app = new ExploreApp();
            app.setPackageName(pkg);
            app.setName(!isEmpty(meta.name) ? meta.name : pkg);
            app.setSummary(meta.summary);
            app.setDescription(!isEmpty(meta.description) ? meta.description : meta.summary);
            app.setLatestVersionCode(ver[0]);
            app.setLatestVersionName(apk[0]);
            app.setDownloadUrl(base + "/repo/" + apk[1]);
            app.setSourceUrl(!isEmpty(meta.sourceCode) ? meta.sourceCode : base + "/packages/" + pkg + "/");
            app.setMetadataUrl(base + "/packages/" + pkg + "/");
            app.setWebsiteUrl(meta.website);
            app.setIssueTrackerUrl(meta.issueTracker);
            app.setTranslationUrl(meta.translation);
            app.setChangelogUrl(meta.changelog);

            if (!isEmpty(meta.icon)) {
                app.setIconUrl(base + "/repo/icons-640/" + meta.icon);
            } else {
                IconRef iconRef = pickLocalizedIcon(localized);
                if (iconRef != null) {
                    app.setIconUrl(base + "/repo/" + pkg + "/" + iconRef.locale + "/" + iconRef.file);
                }
            }
            List<String> screenshotRefs = pickLocalizedScreenshots(localized);
            if (!screenshotRefs.isEmpty()) {
                List<String> shots = new ArrayList<>();
                for (String rel : screenshotRefs) shots.add(base + "/repo/" + pkg + "/" + rel);
                app.setScreenshotUrls(shots);
            }

            app.setCategories(meta.categories);
            app.setAdded(meta.added);
            app.setLastUpdated(meta.lastUpdated);
            app.setDonateUrl(meta.donate);
            app.setAuthorName(meta.authorName);
            app.setLicense(meta.license);
            long[] extra = apkExtra.get(pkg);
            if (extra != null) app.setApkSize(extra[0]);
            app.setAntiFeatures(mergeAntiFeatures(meta.antiFeatures, antiFeaturesByPkg.get(pkg)));
            app.setPermissions(permissionsByPkg.get(pkg));
            app.setPreviousVersions(previousVersionsByPkg.get(pkg));
            String[] pe = packageExtraByPkg.get(pkg);
            if (pe != null) {
                app.setArchitecture(pe[0]);
                app.setSigner(pe[1]);
                app.setApkHash(pe[2]);
            }
            app.setRepoName(repoName);
            app.setInstalled(installedPackages != null && installedPackages.contains(pkg));
            result.add(app);
        }
        return result;
    }

    private String repoBase(String indexUrl) {
        if (indexUrl.endsWith("/index-v1.json")) {
            String withoutFile = indexUrl.substring(0, indexUrl.length() - "/index-v1.json".length());
            if (withoutFile.endsWith("/repo")) {
                return withoutFile.substring(0, withoutFile.length() - "/repo".length());
            }
            return withoutFile;
        }
        int marker = indexUrl.indexOf("/repo/");
        if (marker >= 0) return indexUrl.substring(0, marker);
        int slash = indexUrl.lastIndexOf('/');
        return slash > 8 ? indexUrl.substring(0, slash) : indexUrl;
    }

    private MatchedApp parsePackage(JsonReader reader, InstalledAppInfo app, String base) throws Exception {
        long maxCode = -1;
        String maxName = null;
        String maxApk = null;
        reader.beginArray();
        while (reader.hasNext()) {
            reader.beginObject();
            String versionName = null, apkName = null;
            long versionCode = -1;
            while (reader.hasNext()) {
                String key = reader.nextName();
                switch (key) {
                    case "versionName": versionName = nextString(reader); break;
                    case "versionCode": versionCode = nextLong(reader); break;
                    case "apkName": apkName = nextString(reader); break;
                    default: reader.skipValue();
                }
            }
            reader.endObject();
            if (versionCode > maxCode && apkName != null && !apkName.isEmpty()) {
                maxCode = versionCode;
                maxName = versionName;
                maxApk = apkName;
            }
        }
        reader.endArray();
        if (maxCode < 0 || maxApk == null) return null;

        MatchedApp matched = new MatchedApp();
        matched.setPackageName(app.getPackageName());
        matched.setAppLabel(app.getAppLabel());
        matched.setInstalledVersionName(app.getVersionName());
        matched.setLatestTag(maxName != null ? maxName : String.valueOf(maxCode));
        matched.setLatestVersionCode(maxCode);
        matched.setUpdateAvailable(maxCode > app.getVersionCode());
        matched.setSourceType("fdroid");
        matched.setOrigin("auto");
        matched.setSourceUrl(base + "/packages/" + app.getPackageName() + "/");
        matched.setDownloadUrl(base + "/repo/" + maxApk);
        return matched;
    }

    private static boolean isFdropSource(MatchedApp app) {
        return app != null && "fdroid".equalsIgnoreCase(app.getSourceType());
    }

    private static String nextString(JsonReader r) throws Exception {
        if (r.peek() == JsonToken.NULL) { r.nextNull(); return null; }
        return r.nextString();
    }

    private static long nextLong(JsonReader r) throws Exception {
        if (r.peek() == JsonToken.NULL) { r.nextNull(); return -1; }
        return r.nextLong();
    }
}
