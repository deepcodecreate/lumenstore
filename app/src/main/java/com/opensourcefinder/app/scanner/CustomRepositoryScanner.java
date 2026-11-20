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
import com.opensourcefinder.app.model.GitHubRelease;
import com.opensourcefinder.app.model.GitHubRepo;
import com.opensourcefinder.app.model.GitHubTree;
import com.opensourcefinder.app.model.InstalledAppInfo;
import com.opensourcefinder.app.install.InstallState;
import com.opensourcefinder.app.model.MatchedApp;
import com.opensourcefinder.app.model.PlayApp;
import com.opensourcefinder.app.network.PlayStoreClient;
import com.opensourcefinder.app.model.SourceRepository;
import com.opensourcefinder.app.network.GitHubApiService;
import com.opensourcefinder.app.network.RetrofitClient;
import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import android.util.Base64;
import retrofit2.Response;

public class CustomRepositoryScanner {
    private static final Pattern GITHUB = Pattern.compile("github\\.com/([^/]+)/([^/#?]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PACKAGE_ATTR = Pattern.compile("package\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']");
    private static final Pattern APPLICATION_ID = Pattern.compile("(?:applicationId|namespace)\\s*(?:=)?\\s*[\\\"']([^\\\"']+)[\\\"']");
    private static final Pattern APK_HREF = Pattern.compile("(?i)(?:href\\s*=\\s*[\\\"'])([^\\\"']+\\.apk(?:\\?[^\\\"']*)?)[\\\"']");
    private static final Pattern APK_PKG_IN_NAME = Pattern.compile("(?i)([a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z0-9_]+)+)");
    private static final Pattern DEFAULT_BRANCH_META = Pattern.compile(
            "(?i)(?:repository_default_branch|default_branch)[^>]*(?:content|value)=[\\\"']([^\\\"']+)[\\\"']");
    private static final Pattern DEFAULT_BRANCH_JSON = Pattern.compile(
            "(?i)[\\\"']defaultBranch[\\\"']\\s*:\\s*[\\\"']([^\\\"']+)[\\\"']");
    private static final Pattern OG_DESCRIPTION = Pattern.compile(
            "(?i)(?:property|name)=[\\\"']og:description[\\\"'][^>]*content=[\\\"']([^\\\"']*)[\\\"']");
    private static final int MAX_ARCHIVE_BYTES = 50 * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 6000;
    private static final int MAX_ARCHIVE_INSPECTED_FILES = 250;
    private static final int MAX_TEXT_BYTES = 1_000_000;
    private final GitHubApiService github = RetrofitClient.getService();
    private final GitHubApiService publicGithub = RetrofitClient.getPublicService();
    private final Context context;

    public CustomRepositoryScanner(Context context) {
        this.context = context.getApplicationContext();
    }

    public CustomRepositoryScanner setForceFull(boolean force) {
        this.forceFull = force;
        return this;
    }

    public List<MatchedApp> scan(SourceRepository source, List<InstalledAppInfo> installedApps) throws Exception {
        if (PlayStoreClient.isPlayUrl(source.getUrl()) || PlayStoreClient.SOURCE_TYPE.equalsIgnoreCase(source.getType())) {
            return scanPlay(source);
        }
        Map<String, InstalledAppInfo> byPkg = new HashMap<>();
        for (InstalledAppInfo a : installedApps) byPkg.put(a.getPackageName(), a);

        if (!forceFull) {
            List<MatchedApp> cached = scanFromCache(source, byPkg);
            if (cached != null) return cached;
        }

        List<MatchedApp> fresh = scanUncached(source, installedApps);
        rememberResult(source, fresh);
        return fresh;
    }

    private List<MatchedApp> scanPlay(SourceRepository source) {
        List<MatchedApp> out = new ArrayList<>();
        String pkg = PlayStoreClient.extractPackage(source.getUrl());
        if (pkg == null) return out;
        MatchedApp result = null;
        try {
            PlayApp app = PlayStoreClient.fetchApp(pkg);
            if (app != null) result = PlayStoreClient.toMatchedApp(context, app);
        } catch (Exception ignored) {}
        if (result == null) {
            for (MatchedApp old : MatchStore.get(context)) {
                if (old != null && pkg.equals(old.getPackageName())) { result = old.copy(); break; }
            }
            if (result == null) {
                PlayApp stub = new PlayApp(pkg);
                stub.name = source.getName();
                result = PlayStoreClient.toMatchedApp(context, stub);
            }
            result.setSourceType(PlayStoreClient.SOURCE_TYPE);
            result.setSourceUrl(PlayStoreClient.appUrl(pkg));
            result.setOrigin("manual");
            InstallState.refresh(context, result);
        }
        out.add(result);
        return out;
    }

    private static final long GITHUB_REANALYSE_MILLIS = 14L * 24L * 60L * 60L * 1000L;
    private static final long PLACEHOLDER_REANALYSE_MILLIS = 24L * 60L * 60L * 1000L;
    private static final long NO_RELEASE_REANALYSE_MILLIS = 3L * 24L * 60L * 60L * 1000L;
    private static final long OTHER_SOURCE_TTL_MILLIS = 30L * 60L * 1000L;
    private boolean forceFull;

    private void rememberResult(SourceRepository source, List<MatchedApp> apps) {
        if (source == null || source.getUrl() == null || apps == null || apps.isEmpty()) return;
        String probeTag = null;
        Matcher gh = GITHUB.matcher(source.getUrl());
        if (gh.find()) probeTag = fetchLatestTag(gh.group(1), cleanRepoName(gh.group(2)));
        ManualScanCache.putAnalysed(context, source.getUrl(), probeTag, apps);
    }

    private List<MatchedApp> scanFromCache(SourceRepository source, Map<String, InstalledAppInfo> byPkg) {
        if (source == null || source.getUrl() == null) return null;
        ManualScanCache.Entry entry = ManualScanCache.get(context, source.getUrl());
        if (entry == null) return null;
        List<MatchedApp> apps = entry.apps();
        if (apps == null || apps.isEmpty()) return null;
        long now = System.currentTimeMillis();

        Matcher gh = GITHUB.matcher(source.getUrl());
        if (!gh.find()) {
            if (now - entry.checkedAt > OTHER_SOURCE_TTL_MILLIS) return null;
            applyInstalledState(apps, byPkg);
            return apps;
        }

        String owner = gh.group(1);
        String repo = cleanRepoName(gh.group(2));
        if (now - entry.analysedAt > GITHUB_REANALYSE_MILLIS) return null;
        for (MatchedApp app : apps) {
            if (app != null && app.getPackageName() != null && app.getPackageName().startsWith("github.")
                    && now - entry.analysedAt > PLACEHOLDER_REANALYSE_MILLIS) {
                return null;
            }
        }

        String latest = fetchLatestTag(owner, repo);

        boolean probeReliable = latest != null && latest.equals(entry.probeTag);
        if (probeReliable) {
            for (MatchedApp app : apps) {
                if (app == null) continue;
                InstalledAppInfo inst = app.getPackageName() == null ? null : byPkg.get(app.getPackageName());
                if (inst != null && InstallState.isPreRelease(inst.getVersionName())) { probeReliable = false; break; }
                if (InstallState.isPreRelease(app.getLatestTag())) { probeReliable = false; break; }
                if (app.getLatestTag() == null || !InstallState.sameVersionName(app.getLatestTag(), latest)) {
                    probeReliable = false;
                    break;
                }
            }
        }
        if (probeReliable) {
            applyInstalledState(apps, byPkg);
            ManualScanCache.touch(context, source.getUrl(), entry);
            return apps;
        }

        List<GitHubRelease> listed = listReleasesChecked(owner, repo);
        if (listed == null) {
            DirectRelease newest = findNewestListedRelease(owner, repo);
            if (newest == null || newest.tag == null || newest.downloadUrl == null) return null;
            for (MatchedApp app : apps) {
                if (app == null) continue;
                app.setLatestTag(newest.tag);
                app.setDownloadUrl(newest.downloadUrl);
            }
            applyInstalledState(apps, byPkg);
            ManualScanCache.putRefreshed(context, source.getUrl(), entry, latest, apps);
            return apps;
        }
        if (listed.isEmpty() && latest == null) {
            if (entry.probeTag == null && now - entry.analysedAt > NO_RELEASE_REANALYSE_MILLIS) return null;
            applyInstalledState(apps, byPkg);
            ManualScanCache.touch(context, source.getUrl(), entry);
            return apps;
        }
        DirectRelease release = null;
        boolean applied = false;
        for (MatchedApp app : apps) {
            if (app == null) continue;
            InstalledAppInfo inst = app.getPackageName() == null ? null : byPkg.get(app.getPackageName());
            GitHubRelease pick = GitHubRelease.pickForInstalled(listed, false, inst == null ? null : inst.getVersionName());
            GitHubRelease.Asset pickAsset = pick == null ? null : pick.findApkAsset(false);
            if (pick != null && pickAsset != null && pickAsset.getBrowserDownloadUrl() != null) {
                app.setLatestTag(pick.getTagName());
                app.setDownloadUrl(pickAsset.getBrowserDownloadUrl());
                applied = true;
                continue;
            }
            if (release == null) release = findLatestApkRelease(owner, repo);
            if (release == null) return null;
            app.setLatestTag(release.tag != null ? release.tag : latest);
            app.setDownloadUrl(release.downloadUrl);
            applied = true;
        }
        if (!applied) return null;
        applyInstalledState(apps, byPkg);
        ManualScanCache.putRefreshed(context, source.getUrl(), entry, latest, apps);
        return apps;
    }

    private void applyInstalledState(List<MatchedApp> apps, Map<String, InstalledAppInfo> byPkg) {
        for (MatchedApp app : apps) {
            if (app == null || app.getPackageName() == null) continue;
            InstalledAppInfo installed = byPkg.get(app.getPackageName());
            boolean hasRelease = app.getLatestTag() != null && !app.getLatestTag().isEmpty();
            if (installed != null) {
                app.setAppLabel(installed.getAppLabel());
                app.setInstalledVersionName(installed.getVersionName());
                app.setNotInstalled(false);
                app.setUpdateAvailable(hasRelease && InstallState.updateFor(app,
                        InstallState.read(context, app.getPackageName())));
            } else {
                app.setInstalledVersionName("");
                app.setNotInstalled(true);
                app.setUpdateAvailable(hasRelease);
            }
        }
    }

    private String pickInstalledName(Map<String, InstalledAppInfo> byPkg, Set<String> packageIds) {
        if (packageIds == null) return null;
        for (String pkg : packageIds) {
            InstalledAppInfo info = byPkg.get(pkg);
            if (info != null) return info.getVersionName();
        }
        return null;
    }

    private String fetchLatestTag(String owner, String repo) {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(new URL("https://github.com/" + owner + "/" + repo + "/releases/latest"));
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("HEAD");
            int code = connection.getResponseCode();
            if (code < 300 || code >= 400) return null;
            String location = connection.getHeaderField("Location");
            if (location == null) return null;
            int marker = location.indexOf("/releases/tag/");
            if (marker < 0) return null;
            String tag = location.substring(marker + "/releases/tag/".length());
            int cut = tag.indexOf('?');
            if (cut >= 0) tag = tag.substring(0, cut);
            tag = java.net.URLDecoder.decode(tag.replace("+", "%2B"), "UTF-8");
            return tag.isEmpty() ? null : tag;
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String cleanRepoName(String raw) {
        return raw.replaceAll("/$", "").replaceAll("(?i)\\.git$", "");
    }

    private List<MatchedApp> scanUncached(SourceRepository source, List<InstalledAppInfo> installedApps) throws Exception {
        Map<String, InstalledAppInfo> byPkg = new HashMap<>();
        for (InstalledAppInfo a : installedApps) byPkg.put(a.getPackageName(), a);

        Matcher gh = GITHUB.matcher(source.getUrl());
        if (gh.find()) {
            return scanGithub(source, byPkg, gh.group(1), gh.group(2).replaceAll("/$", "").replaceAll("(?i)\\.git$", ""));
        }

        HostedRepository hosted = parseHostedRepository(source.getUrl());
        if (hosted != null && hosted.type != null) {
            List<MatchedApp> hostedResult = scanHostedRepository(source, byPkg, hosted);
            if (hostedResult != null && !hostedResult.isEmpty()) return hostedResult;
        }

        String url = source.getUrl();
        if (url.contains("/repo") || url.endsWith("index-v1.json") || url.toLowerCase().contains("fdroid")) {
            String indexUrl = url.endsWith("index-v1.json") ? url
                    : (url.endsWith("/") ? url + "index-v1.json" : url + "/index-v1.json");
            try {
                return scanFdroidIndex(indexUrl, url, byPkg);
            } catch (Exception ignored) {}
        }
        return scanWeb(source, byPkg);
    }

    private HostedRepository parseHostedRepository(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return null;
        try {
            String value = rawUrl.trim();
            if (!value.startsWith("http://") && !value.startsWith("https://")) value = "https://" + value;
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host == null) return null;
            String cleanHost = host.toLowerCase(java.util.Locale.ROOT);
            List<String> parts = new ArrayList<>();
            for (String part : uri.getPath().split("/")) {
                if (part != null && !part.trim().isEmpty()) parts.add(part.trim());
            }
            if (parts.size() < 2) return null;
            if ("gitlab.com".equals(cleanHost) || cleanHost.contains("gitlab")) {
                StringBuilder ownerBuilder = new StringBuilder();
                for (int i = 0; i < parts.size() - 1; i++) {
                    if (i > 0) ownerBuilder.append('/');
                    ownerBuilder.append(parts.get(i));
                }
                String owner = ownerBuilder.toString();
                String repo = parts.get(parts.size() - 1).replaceAll("(?i)\\.git$", "");
                if (owner.isEmpty() || repo.isEmpty()) return null;
                return new HostedRepository("gitlab", value, cleanHost, owner, repo);
            }
            if ("codeberg.org".equals(cleanHost)) {
                String owner = parts.get(0);
                String repo = parts.get(1).replaceAll("(?i)\\.git$", "");
                if (owner.isEmpty() || repo.isEmpty()) return null;
                return new HostedRepository("codeberg", value, cleanHost, owner, repo);
            }
            String owner = parts.get(0);
            String repo = parts.get(1).replaceAll("(?i)\\.git$", "");
            if (!owner.isEmpty() && !repo.isEmpty()) {
                return new HostedRepository("gitea", value, cleanHost, owner, repo);
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<MatchedApp> scanHostedRepository(SourceRepository source,
                                                   Map<String, InstalledAppInfo> byPkg,
                                                   HostedRepository hosted) {
        try {
            JSONObject repoJson;
            String branch;
            String description;
            if ("gitlab".equals(hosted.type)) {
                String projectId = URLEncoder.encode(hosted.owner + "/" + hosted.repo, "UTF-8").replace("+", "%20");
                repoJson = new JSONObject(downloadText(hosted.apiBase() + "/projects/" + projectId));
                branch = repoJson.optString("default_branch", "main");
                description = repoJson.optString("description", null);
            } else {
                repoJson = new JSONObject(downloadText(hosted.apiBase() + "/repos/" + hosted.owner + "/" + hosted.repo));
                branch = repoJson.optString("default_branch", "main");
                description = repoJson.optString("description", null);
            }

            Set<String> packageIds = new HashSet<>();
            collectHostedPackageFiles(hosted, branch, packageIds);

            List<GitHubRelease> hostedReleases = findHostedReleases(hosted);
            HostedRelease latest = findLatestInstallableHostedRelease(hostedReleases);
            if (latest != null && latest.assetName != null) {
                Matcher pm = APK_PKG_IN_NAME.matcher(latest.assetName);
                while (pm.find()) {
                    String candidate = pm.group(1);
                    if (candidate.contains(".") && candidate.length() > 3) packageIds.add(candidate);
                }
            }

            Set<String> realPkgs = new HashSet<>();
            for (String pkg : packageIds) {
                if (pkg != null && pkg.contains(".") && !pkg.startsWith("github.")) realPkgs.add(pkg);
            }
            if (!realPkgs.isEmpty()) packageIds = realPkgs;

            String chosenPkg = choosePackage(packageIds, byPkg, hosted.type, hosted.owner, hosted.repo);
            if (chosenPkg == null) return new ArrayList<>();

            InstalledAppInfo installed = byPkg.get(chosenPkg);
            String canonicalUrl = repoJson.optString("web_url", null);
            if (canonicalUrl == null || canonicalUrl.isEmpty()) canonicalUrl = hosted.webUrl();
            GitHubRepo repo = new GitHubRepo(hosted.owner, hosted.repo, description, branch, false, canonicalUrl);

            MatchedApp matched = new MatchedApp();
            matched.setPackageName(chosenPkg);
            matched.setRepo(repo);
            matched.setSourceType(hosted.type);
            matched.setOrigin("manual");
            matched.setSourceUrl(canonicalUrl);
            matched.setDescription(description);
            matched.setReadme(loadHostedReadme(hosted, branch));
            matched.setReleases(hostedReleases);
            if (installed != null) {
                matched.setAppLabel(installed.getAppLabel());
                matched.setInstalledVersionName(installed.getVersionName());
                matched.setNotInstalled(false);
            } else {
                matched.setAppLabel(repo.getName() != null ? repo.getName() : chosenPkg);
                matched.setInstalledVersionName("");
                matched.setNotInstalled(true);
            }
            if (latest != null) {
                matched.setLatestTag(latest.tag);
                matched.setDownloadUrl(latest.downloadUrl);
                matched.setUpdateAvailable(installed == null || versionIsNewer(latest.tag, installed.getVersionName()));
            } else {
                matched.setUpdateAvailable(false);
            }
            List<MatchedApp> result = new ArrayList<>();
            result.add(matched);
            return result;
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private void collectHostedPackageFiles(HostedRepository hosted, String branch, Set<String> packageIds) {
        String[] paths = {
                "app/src/main/AndroidManifest.xml",
                "src/main/AndroidManifest.xml",
                "AndroidManifest.xml",
                "app/build.gradle",
                "app/build.gradle.kts",
                "build.gradle",
                "build.gradle.kts",
                "app/src/main/java/AndroidManifest.xml"
        };
        for (String path : paths) {
            try {
                String raw = fetchHostedRaw(hosted, branch, path);
                if (raw == null) continue;
                Pattern pattern = path.endsWith("AndroidManifest.xml") ? PACKAGE_ATTR : APPLICATION_ID;
                Matcher m = pattern.matcher(raw);
                while (m.find()) packageIds.add(m.group(1));
            } catch (Exception ignored) {}
        }

        try {
            JSONArray tree = hostedTree(hosted, branch);
            if (tree == null) return;
            int inspected = 0;
            for (int i = 0; i < tree.length() && inspected < 80; i++) {
                JSONObject item = tree.optJSONObject(i);
                if (item == null) continue;
                String path = item.optString("path", item.optString("name", ""));
                if (path.isEmpty()) continue;
                String lower = path.toLowerCase(java.util.Locale.ROOT);
                boolean manifest = lower.endsWith("androidmanifest.xml");
                boolean build = lower.endsWith("build.gradle") || lower.endsWith("build.gradle.kts");
                if (!manifest && !build) continue;
                if (++inspected > 80) break;
                String raw = fetchHostedRaw(hosted, branch, path);
                if (raw == null) continue;
                Matcher m = (manifest ? PACKAGE_ATTR : APPLICATION_ID).matcher(raw);
                while (m.find()) packageIds.add(m.group(1));
            }
        } catch (Exception ignored) {}
    }

    private JSONArray hostedTree(HostedRepository hosted, String branch) throws Exception {
        if ("gitlab".equals(hosted.type)) {
            String projectId = URLEncoder.encode(hosted.owner + "/" + hosted.repo, "UTF-8").replace("+", "%20");
            String url = hosted.apiBase() + "/projects/" + projectId + "/repository/tree?recursive=true&per_page=100&ref="
                    + URLEncoder.encode(branch, "UTF-8");
            return new JSONArray(downloadText(url));
        }
        String encodedBranch = URLEncoder.encode(branch, "UTF-8").replace("+", "%20");
        String refUrl = hosted.apiBase() + "/repos/" + hosted.owner + "/" + hosted.repo
                + "/git/refs/heads/" + encodedBranch;
        JSONObject ref = new JSONObject(downloadText(refUrl));
        JSONObject object = ref.optJSONObject("object");
        String sha = object == null ? null : object.optString("sha", null);
        if (sha == null || sha.isEmpty()) return null;
        String url = hosted.apiBase() + "/repos/" + hosted.owner + "/" + hosted.repo
                + "/git/trees/" + URLEncoder.encode(sha, "UTF-8") + "?recursive=true&per_page=100";
        JSONObject root = new JSONObject(downloadText(url));
        return root.optJSONArray("tree");
    }

    private String fetchHostedRaw(HostedRepository hosted, String branch, String path) {
        try {
            if ("gitlab".equals(hosted.type)) {
                String projectId = URLEncoder.encode(hosted.owner + "/" + hosted.repo, "UTF-8").replace("+", "%20");
                String encodedPath = URLEncoder.encode(path, "UTF-8").replace("+", "%20");
                return downloadText(hosted.apiBase() + "/projects/" + projectId + "/repository/files/"
                        + encodedPath + "/raw?ref=" + URLEncoder.encode(branch, "UTF-8"));
            }
            String encodedPath = URLEncoder.encode(path, "UTF-8").replace("+", "%20");
            JSONObject content = new JSONObject(downloadText(hosted.apiBase() + "/repos/" + hosted.owner + "/"
                    + hosted.repo + "/contents/" + encodedPath + "?ref=" + URLEncoder.encode(branch, "UTF-8")));
            String encoded = content.optString("content", null);
            if (encoded == null) return null;
            return new String(Base64.decode(encoded.replaceAll("\\s", ""), Base64.DEFAULT), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<GitHubRelease> findHostedReleases(HostedRepository hosted) {
        List<GitHubRelease> result = new ArrayList<>();
        try {
            JSONArray releases;
            if ("gitlab".equals(hosted.type)) {
                String projectId = URLEncoder.encode(hosted.owner + "/" + hosted.repo, "UTF-8").replace("+", "%20");
                releases = new JSONArray(downloadText(hosted.apiBase() + "/projects/" + projectId + "/releases?per_page=100&order_by=released_at&sort=desc"));
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject release = releases.optJSONObject(i);
                    if (release == null) continue;
                    String tag = release.optString("tag_name", null);
                    String name = release.optString("name", null);
                    String body = release.optString("description", null);
                    String published = release.optString("released_at", release.optString("created_at", null));
                    List<GitHubRelease.Asset> assets = new ArrayList<>();
                    JSONObject assetRoot = release.optJSONObject("assets");
                    JSONArray links = assetRoot == null ? null : assetRoot.optJSONArray("links");
                    if (links != null) {
                        for (int j = 0; j < links.length(); j++) {
                            JSONObject link = links.optJSONObject(j);
                            if (link == null) continue;
                            String nameValue = link.optString("name", null);
                            String url = link.optString("direct_asset_url", link.optString("url", null));
                            if (!android.text.TextUtils.isEmpty(url)) {
                                assets.add(GitHubRelease.asset(nameValue, url, ""));
                            }
                        }
                    }
                    result.add(new GitHubRelease(tag, name, body, published, false, false, assets));
                }
            } else {
                releases = new JSONArray(downloadText(hosted.apiBase() + "/repos/" + hosted.owner + "/"
                        + hosted.repo + "/releases?limit=50&page=1"));
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject release = releases.optJSONObject(i);
                    if (release == null || release.optBoolean("draft", false)) continue;
                    String tag = release.optString("tag_name", release.optString("tag", null));
                    String name = release.optString("name", null);
                    String body = release.optString("body", release.optString("description", null));
                    String published = release.optString("published_at", release.optString("created_at", null));
                    boolean prerelease = release.optBoolean("prerelease", false);
                    List<GitHubRelease.Asset> assets = new ArrayList<>();
                    JSONArray assetArray = release.optJSONArray("assets");
                    if (assetArray != null) {
                        for (int j = 0; j < assetArray.length(); j++) {
                            JSONObject asset = assetArray.optJSONObject(j);
                            if (asset == null) continue;
                            String nameValue = asset.optString("name", null);
                            String url = asset.optString("browser_download_url", asset.optString("download_url", null));
                            if (!android.text.TextUtils.isEmpty(url)) {
                                assets.add(GitHubRelease.asset(nameValue, url, asset.optString("content_type", "")));
                            }
                        }
                    }
                    result.add(new GitHubRelease(tag, name, body, published, false, prerelease, assets));
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private HostedRelease findLatestInstallableHostedRelease(List<GitHubRelease> releases) {
        if (releases == null) return null;
        for (GitHubRelease release : releases) {
            if (release == null || release.isDraft()) continue;
            GitHubRelease.Asset apk = release.findApkAsset(false);
            if (apk != null && !android.text.TextUtils.isEmpty(apk.getBrowserDownloadUrl())) {
                return new HostedRelease(release.getTagName(), apk.getName(), apk.getBrowserDownloadUrl());
            }
        }
        return null;
    }

    private String loadHostedReadme(HostedRepository hosted, String branch) {
        String[] names = {"README.md", "Readme.md", "readme.md"};
        for (String name : names) {
            String raw = fetchHostedRaw(hosted, branch, name);
            if (raw != null && !raw.trim().isEmpty()) return raw;
        }
        return null;
    }

    private HostedRelease findApkInLinks(String tag, JSONArray links) {
        if (links == null) return null;
        for (int i = 0; i < links.length(); i++) {
            JSONObject link = links.optJSONObject(i);
            if (link == null) continue;
            String name = link.optString("name", null);
            String url = link.optString("direct_asset_url", link.optString("url", null));
            if (GitHubRelease.isDebugName(name != null ? name : url)) continue;
            if ((name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".apk"))
                    || (url != null && url.toLowerCase(java.util.Locale.ROOT).contains(".apk"))) {
                return new HostedRelease(tag, name, url);
            }
        }
        return null;
    }

    private HostedRelease findApkInGiteaAssets(String tag, JSONArray assets) {
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            String name = asset.optString("name", null);
            String url = asset.optString("browser_download_url", asset.optString("download_url", null));
            if (GitHubRelease.isDebugName(name != null ? name : url)) continue;
            if ((name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".apk"))
                    || (url != null && url.toLowerCase(java.util.Locale.ROOT).contains(".apk"))) {
                return new HostedRelease(tag, name, url);
            }
        }
        return null;
    }

    private String choosePackage(Set<String> packageIds, Map<String, InstalledAppInfo> byPkg,
                                 String type, String owner, String repo) {
        for (String pkg : packageIds) if (byPkg.containsKey(pkg)) return pkg;
        if (!packageIds.isEmpty()) return packageIds.iterator().next();
        return type + "." + owner + "." + repo.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private static final class HostedRepository {
        final String type;
        final String originalUrl;
        final String host;
        final String owner;
        final String repo;
        HostedRepository(String type, String originalUrl, String host, String owner, String repo) {
            this.type = type;
            this.originalUrl = originalUrl;
            this.host = host;
            this.owner = owner;
            this.repo = repo;
        }
        String apiBase() {
            return "gitlab".equals(type) ? "https://" + host + "/api/v4" : "https://" + host + "/api/v1";
        }
        String webUrl() { return "https://" + host + "/" + owner + "/" + repo; }
    }

    private static final class HostedRelease {
        final String tag;
        final String assetName;
        final String downloadUrl;
        HostedRelease(String tag, String assetName, String downloadUrl) {
            this.tag = tag;
            this.assetName = assetName;
            this.downloadUrl = downloadUrl;
        }
    }

    private List<MatchedApp> scanFdroidIndex(String indexUrl, String cacheKey, Map<String, InstalledAppInfo> byPkg) throws Exception {

        new FdroidIndexManager(context).refreshAndMatch(new ArrayList<>(byPkg.values()), indexUrl, cacheKey);
        File file = new File(context.getFilesDir(), "fdroid-index-v1.json_" + Integer.toHexString(indexUrl.hashCode()));
        if (!file.exists()) return new ArrayList<>();

        String base = repoBase(indexUrl);
        Map<String, String[]> appNames = new HashMap<>();
        Map<String, Object[]> bestApk = new HashMap<>();

        try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String section = reader.nextName();
                if ("apps".equals(section)) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        reader.beginObject();
                        String pkg = null, name = null, summary = null;
                        while (reader.hasNext()) {
                            String key = reader.nextName();
                            switch (key) {
                                case "packageName": pkg = nextString(reader); break;
                                case "name": name = nextString(reader); break;
                                case "summary": summary = nextString(reader); break;
                                default: reader.skipValue();
                            }
                        }
                        reader.endObject();
                        if (pkg != null) appNames.put(pkg, new String[]{name, summary});
                    }
                    reader.endArray();
                } else if ("packages".equals(section)) {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String pkg = reader.nextName();
                        long maxCode = -1;
                        String maxName = null, maxApk = null;
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
                            if (versionCode > maxCode && apkName != null) {
                                maxCode = versionCode;
                                maxName = versionName;
                                maxApk = apkName;
                            }
                        }
                        reader.endArray();
                        if (maxCode >= 0 && maxApk != null) {
                            bestApk.put(pkg, new Object[]{maxCode, maxName, maxApk});
                        }
                    }
                    reader.endObject();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }

        List<MatchedApp> out = new ArrayList<>();

        Set<String> keys = new HashSet<>();
        keys.addAll(byPkg.keySet());
        keys.addAll(appNames.keySet());

        for (String pkg : keys) {
            Object[] apk = bestApk.get(pkg);
            if (apk == null) continue;
            long code = (Long) apk[0];
            String verName = (String) apk[1];
            String apkName = (String) apk[2];
            InstalledAppInfo installed = byPkg.get(pkg);
            String[] meta = appNames.get(pkg);

            MatchedApp m = new MatchedApp();
            m.setPackageName(pkg);
            m.setLatestTag(verName != null ? verName : String.valueOf(code));
            m.setLatestVersionCode(code);
            m.setDownloadUrl(base + "/repo/" + apkName);
            m.setSourceUrl(base + "/packages/" + pkg + "/");
            m.setSourceType("fdroid");
            m.setOrigin("manual");
            if (meta != null && meta[1] != null) m.setDescription(meta[1]);
            if (installed != null) {
                m.setAppLabel(installed.getAppLabel());
                m.setInstalledVersionName(installed.getVersionName());
                m.setNotInstalled(false);
                m.setUpdateAvailable(code > installed.getVersionCode());
            } else {
                m.setAppLabel(meta != null && meta[0] != null ? meta[0] : pkg);
                m.setInstalledVersionName("");
                m.setNotInstalled(true);
                m.setUpdateAvailable(true);
            }
            out.add(m);
        }
        return out;
    }

    private List<MatchedApp> scanGithub(SourceRepository source, Map<String, InstalledAppInfo> byPkg,
                                        String owner, String repo) throws Exception {
        List<MatchedApp> webResult = scanGithubWithoutApi(source, owner, repo, byPkg);
        if (webResult != null) return webResult;

        return scanGithubViaApi(source, owner, repo, byPkg);
    }

    private List<MatchedApp> scanGithubWithoutApi(SourceRepository source, String owner, String repo,
                                                  Map<String, InstalledAppInfo> byPkg) {
        String repoUrl = "https://github.com/" + owner + "/" + repo;
        String repositoryPage;
        try {
            repositoryPage = downloadText(repoUrl);
        } catch (Exception e) {
            return null;
        }

        String branch = extractDefaultBranch(repositoryPage);
        Set<String> packageIds = new HashSet<>();
        boolean archiveLoaded = false;
        String[] branches = uniqueBranches(branch);
        for (String candidateBranch : branches) {
            if (scanGithubArchive(owner, repo, candidateBranch, packageIds)) {
                branch = candidateBranch;
                archiveLoaded = true;
                break;
            }
        }
        if (!archiveLoaded) {
            collectCommonRawFiles(owner, repo, branches, packageIds);
        }

        DirectRelease release = findLatestApkRelease(owner, repo);
        if (release != null && release.assetName != null) {
            Matcher pm = APK_PKG_IN_NAME.matcher(release.assetName);
            while (pm.find()) {
                String candidate = pm.group(1);
                if (candidate.contains(".") && candidate.length() > 3) packageIds.add(candidate);
            }
        }

        Set<String> realPkgs = new HashSet<>();
        for (String pkg : packageIds) {
            if (pkg != null && !pkg.startsWith("github.") && pkg.contains(".")) realPkgs.add(pkg);
        }
        if (!realPkgs.isEmpty()) packageIds = realPkgs;
        if (packageIds.isEmpty()) {
            packageIds.add("github." + owner + "." + repo.replaceAll("[^a-zA-Z0-9_]", "_"));
        }

        String chosenPkg = null;
        for (String pkg : packageIds) {
            if (byPkg.containsKey(pkg)) {
                chosenPkg = pkg;
                break;
            }
        }
        if (chosenPkg == null) {
            for (String pkg : packageIds) {
                chosenPkg = pkg;
                break;
            }
        }
        if (chosenPkg == null) return new ArrayList<>();

        String description = extractMetaDescription(repositoryPage);
        GitHubRepo gitRepo = new GitHubRepo(owner, repo, description, branch, false);
        InstalledAppInfo installed = byPkg.get(chosenPkg);
        MatchedApp matched = new MatchedApp();
        matched.setPackageName(chosenPkg);
        matched.setRepo(gitRepo);
        matched.setSourceType("github");
        matched.setOrigin("manual");
        matched.setSourceUrl(gitRepo.getHtmlUrl() != null ? gitRepo.getHtmlUrl() : source.getUrl());
        matched.setDescription(description);

        if (installed != null) {
            matched.setAppLabel(installed.getAppLabel());
            matched.setInstalledVersionName(installed.getVersionName());
            matched.setNotInstalled(false);
        } else {
            matched.setAppLabel(repo);
            matched.setInstalledVersionName("");
            matched.setNotInstalled(true);
        }
        if (release != null) {
            matched.setLatestTag(release.tag);
            matched.setDownloadUrl(release.downloadUrl);
            matched.setUpdateAvailable(installed == null
                    || versionIsNewer(release.tag, installed.getVersionName()));
        } else {
            matched.setUpdateAvailable(false);
        }

        List<MatchedApp> result = new ArrayList<>();
        result.add(matched);
        return result;
    }

    private List<MatchedApp> scanGithubViaApi(SourceRepository source, String owner, String repo,
                                              Map<String, InstalledAppInfo> byPkg) throws Exception {
        GitHubRepo gitRepo = null;
        try {
            Response<GitHubRepo> repoResponse = github.getRepo(owner, repo).execute();
            if (isAuthFailure(repoResponse)) repoResponse = publicGithub.getRepo(owner, repo).execute();
            if (repoResponse.isSuccessful()) gitRepo = repoResponse.body();
        } catch (Exception ignored) {}
        if (gitRepo == null) return fallbackGithubMatch(source, owner, repo, byPkg);
        String branch = gitRepo.getDefaultBranch() == null ? "main" : gitRepo.getDefaultBranch();

        Set<String> packageIds = new HashSet<>();
        try {
            Response<GitHubTree> treeResponse = github.getTree(owner, repo, branch, 1).execute();
            if (isAuthFailure(treeResponse)) treeResponse = publicGithub.getTree(owner, repo, branch, 1).execute();
            if (treeResponse.isSuccessful() && treeResponse.body() != null && treeResponse.body().getTree() != null) {
                int checked = 0;
                for (GitHubTree.Entry item : treeResponse.body().getTree()) {
                    if (item == null || item.getPath() == null) continue;
                    String path = item.getPath();
                    String baseName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
                    boolean isManifest = "AndroidManifest.xml".equals(baseName);
                    boolean isBuild = "build.gradle".equals(baseName) || "build.gradle.kts".equals(baseName);
                    if (!isManifest && !isBuild) continue;
                    if (++checked > 50) break;
                    String raw = fetchRaw(owner, repo, path);
                    if (raw == null) continue;
                    Matcher m = isManifest ? PACKAGE_ATTR.matcher(raw) : APPLICATION_ID.matcher(raw);
                    if (m.find()) packageIds.add(m.group(1));
                }
            }
        } catch (Exception ignored) {}
        collectPackageFromPath(owner, repo, "app/build.gradle", false, packageIds);
        collectPackageFromPath(owner, repo, "app/build.gradle.kts", false, packageIds);
        collectPackageFromPath(owner, repo, "app/src/main/AndroidManifest.xml", true, packageIds);
        collectPackageFromPath(owner, repo, "AndroidManifest.xml", true, packageIds);

        GitHubRelease selected = null;
        GitHubRelease.Asset asset = null;
        try {
            Response<List<GitHubRelease>> releasesResp = github.listReleases(owner, repo, 100).execute();
            if (isAuthFailure(releasesResp)) releasesResp = publicGithub.listReleases(owner, repo, 100).execute();
            if (releasesResp.isSuccessful() && releasesResp.body() != null) {
                GitHubRelease chosenRelease = GitHubRelease.pickForInstalled(releasesResp.body(), false,
                        pickInstalledName(byPkg, packageIds));
                GitHubRelease.Asset candidate = chosenRelease == null ? null : chosenRelease.findApkAsset(false);
                if (candidate != null) {
                    selected = chosenRelease;
                    asset = candidate;
                    if (candidate.getName() != null) {
                        Matcher pm = APK_PKG_IN_NAME.matcher(candidate.getName());
                        while (pm.find()) {
                            String cand = pm.group(1);
                            if (cand.contains(".") && cand.length() > 3) packageIds.add(cand);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        Set<String> realPkgs = new HashSet<>();
        for (String pkg : packageIds) {
            if (pkg != null && !pkg.startsWith("github.") && pkg.contains(".")) realPkgs.add(pkg);
        }
        if (!realPkgs.isEmpty()) packageIds = realPkgs;

        Set<String> installedHits = new HashSet<>();
        for (String pkg : packageIds) {
            if (byPkg.containsKey(pkg)) installedHits.add(pkg);
        }
        if (!installedHits.isEmpty()) packageIds = installedHits;

        if (packageIds.isEmpty()) {

            packageIds.add("github." + owner + "." + repo.replaceAll("[^a-zA-Z0-9_]", "_"));
        }

        String chosenPkg = null;
        for (String pkg : packageIds) {
            if (byPkg.containsKey(pkg)) { chosenPkg = pkg; break; }
        }
        if (chosenPkg == null) {
            for (String pkg : packageIds) { chosenPkg = pkg; break; }
        }

        List<MatchedApp> result = new ArrayList<>();
        if (chosenPkg != null) {
            InstalledAppInfo installed = byPkg.get(chosenPkg);
            MatchedApp matched = new MatchedApp();
            matched.setPackageName(chosenPkg);
            matched.setRepo(gitRepo);
            matched.setSourceType("github");
            matched.setOrigin("manual");
            matched.setSourceUrl(gitRepo.getHtmlUrl() != null ? gitRepo.getHtmlUrl() : source.getUrl());
            matched.setDescription(gitRepo.getDescription());

            if (installed != null) {
                matched.setAppLabel(installed.getAppLabel());
                matched.setInstalledVersionName(installed.getVersionName());
                matched.setNotInstalled(false);
            } else {
                matched.setAppLabel(gitRepo.getName() != null ? gitRepo.getName() : chosenPkg);
                matched.setInstalledVersionName("");
                matched.setNotInstalled(true);
            }
            if (selected != null && asset != null) {
                matched.setLatestTag(selected.getTagName());
                matched.setDownloadUrl(asset.getBrowserDownloadUrl());
                matched.setUpdateAvailable(installed == null || versionIsNewer(selected.getTagName(), installed.getVersionName()));
            } else {
                matched.setUpdateAvailable(false);
            }
            result.add(matched);
        }
        return result;
    }

    private boolean scanGithubArchive(String owner, String repo, String branch, Set<String> packageIds) {
        HttpURLConnection connection = null;
        try {
            String encodedBranch = java.net.URLEncoder.encode(branch, "UTF-8").replace("+", "%20");
            URL url = new URL("https://codeload.github.com/" + owner + "/" + repo
                    + "/zip/refs/heads/" + encodedBranch);
            connection = openConnection(url);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return false;
            long length = connection.getContentLengthLong();
            if (length > MAX_ARCHIVE_BYTES) return false;

            int entriesSeen = 0;
            int inspected = 0;
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 ZipInputStream zip = new ZipInputStream(input)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null && entriesSeen++ < MAX_ARCHIVE_ENTRIES) {
                    if (entry.isDirectory()) continue;
                    String path = entry.getName();
                    int root = path.indexOf('/');
                    if (root >= 0) path = path.substring(root + 1);
                    String baseName = basename(path);
                    boolean manifest = "AndroidManifest.xml".equals(baseName);
                    boolean build = "build.gradle".equals(baseName) || "build.gradle.kts".equals(baseName);
                    if (!manifest && !build) continue;
                    if (++inspected > MAX_ARCHIVE_INSPECTED_FILES) break;
                    String content = readZipText(zip, MAX_TEXT_BYTES);
                    Matcher matcher = (manifest ? PACKAGE_ATTR : APPLICATION_ID).matcher(content);
                    while (matcher.find()) packageIds.add(matcher.group(1));
                }
            }
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void collectCommonRawFiles(String owner, String repo, String[] branches,
                                       Set<String> packageIds) {
        String[] paths = {
                "app/src/main/AndroidManifest.xml",
                "src/main/AndroidManifest.xml",
                "AndroidManifest.xml",
                "app/build.gradle",
                "app/build.gradle.kts",
                "build.gradle",
                "build.gradle.kts"
        };
        for (String branch : branches) {
            for (String path : paths) {
                String raw = fetchGithubRaw(owner, repo, branch, path);
                if (raw == null) continue;
                boolean manifest = path.endsWith("AndroidManifest.xml");
                Matcher matcher = (manifest ? PACKAGE_ATTR : APPLICATION_ID).matcher(raw);
                while (matcher.find()) packageIds.add(matcher.group(1));
            }
        }
    }

    private String fetchGithubRaw(String owner, String repo, String branch, String path) {
        String encodedBranch;
        try {
            encodedBranch = java.net.URLEncoder.encode(branch, "UTF-8").replace("+", "%20");
        } catch (Exception ignored) {
            return null;
        }
        String[] candidates = {
                "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + encodedBranch + "/" + path,
                "https://cdn.jsdelivr.net/gh/" + owner + "/" + repo + "@" + encodedBranch + "/" + path
        };
        for (String candidate : candidates) {
            try {
                String text = downloadText(candidate);
                if (text != null) return text;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String extractDefaultBranch(String html) {
        Matcher meta = DEFAULT_BRANCH_META.matcher(html);
        if (meta.find()) return htmlDecode(meta.group(1));
        Matcher json = DEFAULT_BRANCH_JSON.matcher(html);
        if (json.find()) return htmlDecode(json.group(1));
        return "main";
    }

    private String[] uniqueBranches(String preferred) {
        LinkedHashMap<String, Boolean> values = new LinkedHashMap<>();
        if (preferred != null && !preferred.trim().isEmpty()) values.put(preferred.trim(), true);
        values.put("main", true);
        values.put("master", true);
        return values.keySet().toArray(new String[0]);
    }

    private String extractMetaDescription(String html) {
        Matcher m = OG_DESCRIPTION.matcher(html);
        return m.find() ? htmlDecode(m.group(1)) : null;
    }

    public static final class ResolvedRelease {
        public final String tag;
        public final String assetName;
        public final String downloadUrl;
        public ResolvedRelease(String tag, String assetName, String downloadUrl) {
            this.tag = tag;
            this.assetName = assetName;
            this.downloadUrl = downloadUrl;
        }
    }

    public List<GitHubRelease> listReleases(String owner, String repo) {
        List<GitHubRelease> checked = listReleasesChecked(owner, repo);
        return checked == null ? new ArrayList<>() : checked;
    }

    private DirectRelease findNewestListedRelease(String owner, String repo) {
        String base = "https://github.com/" + owner + "/" + repo;
        try {
            String html = downloadText(base + "/releases");
            Matcher link = APK_HREF.matcher(html);
            while (link.find()) {
                String href = htmlDecode(link.group(1));
                if (!href.contains("/releases/download/")) continue;
                Matcher tagMatcher = Pattern.compile(
                        "/releases/download/([^/]+)/", Pattern.CASE_INSENSITIVE).matcher(href);
                String tag = tagMatcher.find() ? htmlDecode(tagMatcher.group(1)) : null;
                String assetName = href.substring(href.lastIndexOf('/') + 1);
                int query = assetName.indexOf('?');
                if (query >= 0) assetName = assetName.substring(0, query);
                if (GitHubRelease.isCloneName(assetName) || GitHubRelease.isDebugName(assetName)) continue;
                return new DirectRelease(tag, assetName, resolve(base + "/", href));
            }
        } catch (Exception ignored) {}
        return null;
    }

    private List<GitHubRelease> listReleasesChecked(String owner, String repo) {
        try {
            Response<List<GitHubRelease>> response = github.listReleases(owner, repo, 100).execute();
            if (isAuthFailure(response)) response = publicGithub.listReleases(owner, repo, 100).execute();
            if (response.isSuccessful() && response.body() != null) {
                List<GitHubRelease> out = new ArrayList<>();
                for (GitHubRelease release : response.body()) {
                    if (release != null && !release.isDraft() && release.getTagName() != null) out.add(release);
                }
                return out;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public ResolvedRelease resolveLatestRelease(String owner, String repo) {
        GitHubRelease pick = GitHubRelease.firstStableInstallable(listReleases(owner, repo));
        if (pick != null) {
            GitHubRelease.Asset asset = pick.findApkAsset(false);
            if (asset != null && asset.getBrowserDownloadUrl() != null) {
                return new ResolvedRelease(pick.getTagName(), asset.getName(), asset.getBrowserDownloadUrl());
            }
        }
        DirectRelease direct = findLatestApkRelease(owner, repo);
        if (direct != null && direct.tag != null && direct.downloadUrl != null) {
            return new ResolvedRelease(direct.tag, direct.assetName, direct.downloadUrl);
        }
        return null;
    }

    private DirectRelease findLatestApkRelease(String owner, String repo) {
        String base = "https://github.com/" + owner + "/" + repo;
        String[] pages = {base + "/releases/latest", base + "/releases"};
        for (String page : pages) {
            try {
                String html = downloadText(page);
                Matcher link = APK_HREF.matcher(html);
                while (link.find()) {
                    String href = htmlDecode(link.group(1));
                    if (!href.contains("/releases/download/")) continue;
                    String downloadUrl = resolve(base + "/", href);
                    Matcher tagMatcher = Pattern.compile(
                            "/releases/download/([^/]+)/", Pattern.CASE_INSENSITIVE).matcher(href);
                    String tag = tagMatcher.find() ? htmlDecode(tagMatcher.group(1)) : null;
                    String assetName = href.substring(href.lastIndexOf('/') + 1);
                    int query = assetName.indexOf('?');
                    if (query >= 0) assetName = assetName.substring(0, query);
                    if (GitHubRelease.isCloneName(assetName) || GitHubRelease.isDebugName(assetName)) continue;
                    return new DirectRelease(tag, assetName, downloadUrl);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private HttpURLConnection openConnection(URL url) throws IOException {
        HttpURLConnection connection = com.opensourcefinder.app.network.HttpClients.open(url);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "OpenSourceFinder/1.0");
        connection.setRequestProperty("Accept-Encoding", "gzip");
        return connection;
    }

    private String readZipText(ZipInputStream zip, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = zip.read(buffer)) != -1 && total < maxBytes) {
            int keep = Math.min(read, maxBytes - total);
            out.write(buffer, 0, keep);
            total += keep;
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private String htmlDecode(String value) {
        if (value == null) return null;
        return value.replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private static String basename(String path) {
        int slash = path == null ? -1 : path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static final class DirectRelease {
        final String tag;
        final String assetName;
        final String downloadUrl;
        DirectRelease(String tag, String assetName, String downloadUrl) {
            this.tag = tag;
            this.assetName = assetName;
            this.downloadUrl = downloadUrl;
        }
    }

    private List<MatchedApp> fallbackGithubMatch(SourceRepository source, String owner, String repo,
                                                  Map<String, InstalledAppInfo> byPkg) {
        String packageName = "github." + owner + "." + repo.replaceAll("[^a-zA-Z0-9_]", "_");
        InstalledAppInfo installed = byPkg.get(packageName);
        MatchedApp matched = new MatchedApp();
        matched.setPackageName(packageName);
        matched.setAppLabel(repo);
        matched.setSourceType("github");
        matched.setOrigin("manual");
        matched.setSourceUrl(source.getUrl());
        matched.setInstalledVersionName(installed == null ? "" : installed.getVersionName());
        matched.setNotInstalled(installed == null);
        matched.setUpdateAvailable(false);
        List<MatchedApp> result = new ArrayList<>();
        result.add(matched);
        return result;
    }

    private void collectPackageFromPath(String owner, String repo, String path, boolean manifest,
                                        Set<String> packageIds) {
        String raw = fetchRaw(owner, repo, path);
        if (raw == null) return;
        Matcher matcher = manifest ? PACKAGE_ATTR.matcher(raw) : APPLICATION_ID.matcher(raw);
        if (matcher.find()) packageIds.add(matcher.group(1));
    }

    private List<MatchedApp> scanWeb(SourceRepository source, Map<String, InstalledAppInfo> byPkg) throws Exception {
        String html = downloadText(source.getUrl());
        List<MatchedApp> result = new ArrayList<>();
        Matcher apkMatcher = APK_HREF.matcher(html);
        List<String> apkUrls = new ArrayList<>();
        while (apkMatcher.find() && apkUrls.size() < 100) {
            apkUrls.add(resolve(source.getUrl(), apkMatcher.group(1)));
        }
        Map<String, String> apkByPackage = new LinkedHashMap<>();
        for (String apk : apkUrls) {
            String file = apk.substring(apk.lastIndexOf('/') + 1);
            Matcher name = APK_PKG_IN_NAME.matcher(file);
            if (name.find()) apkByPackage.put(name.group(1), apk);
        }

        String chosenPackage = null;
        for (String pkg : byPkg.keySet()) {
            if (apkByPackage.containsKey(pkg) || html.contains(pkg)) {
                chosenPackage = pkg;
                break;
            }
        }
        if (chosenPackage == null && !apkByPackage.isEmpty()) chosenPackage = apkByPackage.keySet().iterator().next();

        String apk = chosenPackage == null ? (apkUrls.isEmpty() ? null : apkUrls.get(0)) : apkByPackage.get(chosenPackage);
        if (apk == null && chosenPackage != null && !apkUrls.isEmpty()) apk = chooseApk(apkUrls, chosenPackage);
        if (apk == null) return result;

        if (chosenPackage == null) {
            Matcher name = APK_PKG_IN_NAME.matcher(apk.substring(apk.lastIndexOf('/') + 1));
            chosenPackage = name.find() ? name.group(1) : "web." + Integer.toHexString(source.getUrl().hashCode());
        }

        InstalledAppInfo installed = byPkg.get(chosenPackage);
        MatchedApp matched = new MatchedApp();
        matched.setPackageName(chosenPackage);
        matched.setLatestTag(extractVersion(apk));
        matched.setSourceType("custom");
        matched.setOrigin("manual");
        matched.setSourceUrl(source.getUrl());
        matched.setDownloadUrl(apk);
        if (installed != null) {
            matched.setAppLabel(installed.getAppLabel());
            matched.setInstalledVersionName(installed.getVersionName());
            matched.setNotInstalled(false);
            matched.setUpdateAvailable(versionIsNewer(extractVersion(apk), installed.getVersionName()));
        } else {
            matched.setAppLabel(source.getName() != null ? source.getName() : chosenPackage);
            matched.setInstalledVersionName("");
            matched.setNotInstalled(true);
            matched.setUpdateAvailable(true);
        }
        result.add(matched);
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

    private String chooseApk(List<String> urls, String packageName) {
        for (String url : urls) if (url.contains(packageName)) return url;
        return urls.get(0);
    }

    private String downloadText(String url) throws Exception {
        HttpURLConnection c = openConnection(new URL(url));
        if (c.getResponseCode() < 200 || c.getResponseCode() >= 300) {
            c.disconnect();
            throw new IOException("HTTP " + c.getResponseCode());
        }
        InputStream stream = c.getInputStream();
        if ("gzip".equalsIgnoreCase(c.getContentEncoding())) {
            stream = new GZIPInputStream(stream);
        }
        try (InputStream in = stream;
             BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            int size = 0;
            while ((line = br.readLine()) != null && size < 2_000_000) {
                out.append(line).append('\n');
                size += line.length();
            }
            return out.toString();
        } finally {
            c.disconnect();
        }
    }

    private String fetchRaw(String owner, String repo, String path) {
        try {
            Response<String> response = github.getFileContentRaw(owner, repo, path, "application/vnd.github.raw+json").execute();
            if (isAuthFailure(response)) {
                response = publicGithub.getFileContentRaw(owner, repo, path, "application/vnd.github.raw+json").execute();
            }
            return response.isSuccessful() ? response.body() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isAuthFailure(Response<?> response) {
        return response != null && (response.code() == 401 || response.code() == 403);
    }

    private String resolve(String base, String href) {
        try { return URI.create(base).resolve(href).toString(); }
        catch (Exception e) { return href; }
    }

    private String extractVersion(String value) {
        if (value == null) return null;
        Matcher m = Pattern.compile("(?i)(?:v|version[-_ ]?)(\\d+(?:\\.\\d+){1,3})").matcher(value);
        return m.find() ? m.group(1) : null;
    }

    private boolean versionIsNewer(String remote, String local) {
        return com.opensourcefinder.app.install.InstallState.isNewer(remote, local);
    }

    private List<Long> numbers(String s) {
        List<Long> out = new ArrayList<>();
        Matcher m = Pattern.compile("\\d+").matcher(s);
        while (m.find()) {
            try { out.add(Long.parseLong(m.group())); } catch (Exception ignored) {}
        }
        return out;
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
