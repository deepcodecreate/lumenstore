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

import com.opensourcefinder.app.cache.RepoMatchCache;
import com.opensourcefinder.app.model.GitHubCodeSearchResponse;
import com.opensourcefinder.app.model.GitHubRelease;
import com.opensourcefinder.app.model.GitHubRepo;
import com.opensourcefinder.app.model.GitHubSearchResponse;
import com.opensourcefinder.app.model.GitHubTree;
import com.opensourcefinder.app.model.InstalledAppInfo;
import com.opensourcefinder.app.model.MatchedApp;
import com.opensourcefinder.app.network.GitHubApiService;
import com.opensourcefinder.app.network.GitHubTokenProvider;
import com.opensourcefinder.app.network.RetrofitClient;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.regex.Pattern;
import retrofit2.Response;

public class GitHubMatcher {
    private static final int CANDIDATES_PER_QUERY = 4;
    private static final long MIN_GENERAL_SEARCH_INTERVAL_MILLIS = 1800;
    private static final long MIN_CODE_SEARCH_INTERVAL_MILLIS = 7000;
    private static final long MIN_CONTENT_CALL_INTERVAL_MILLIS = 250;
    private static volatile long lastGeneralSearchCallAtMillis;
    private static volatile long lastCodeSearchCallAtMillis;
    private static volatile long lastContentCallAtMillis;
    private final GitHubApiService api = RetrofitClient.getService();

    public static final class MatchResult {
        private final MatchedApp matchedApp;
        private final GitHubRepo verifiedRepo;
        private final boolean transientError;
        private MatchResult(MatchedApp app, GitHubRepo repo, boolean error) { matchedApp = app; verifiedRepo = repo; transientError = error; }
        static MatchResult found(MatchedApp app, GitHubRepo repo) { return new MatchResult(app, repo, false); }
        static MatchResult notFound() { return new MatchResult(null, null, false); }
        static MatchResult error() { return new MatchResult(null, null, true); }
        public MatchedApp getMatchedApp() { return matchedApp; }
        public GitHubRepo getVerifiedRepo() { return verifiedRepo; }
        public boolean isTransientError() { return transientError; }
    }

    public MatchResult tryMatch(InstalledAppInfo app) {
        if (app.isGooglePlay() || app.isFdroidInstalled()) return MatchResult.notFound();
        String pkg = app.getPackageName();
        RepoMatchCache.CachedEntry cached = RepoMatchCache.get(pkg);
        if (cached != null) {
            if (cached.isFound()) {
                try {
                    MatchResult r = buildResultForRepo(cached.getRepo(), app);
                    RepoMatchCache.put(pkg, cached.getRepo(), true);
                    return r;
                } catch (Exception ignored) {}
            } else if (!cached.shouldRetryNegative()) {
                return MatchResult.notFound();
            }
        }
        try {
            GitHubRepo repo = verifyByRepositorySearch(pkg, app.getAppLabel());

            if (repo == null && GitHubTokenProvider.hasToken()) {
                repo = verifyByCodeSearch(pkg);
            }
            if (repo == null) {
                RepoMatchCache.put(pkg, null, false);
                return MatchResult.notFound();
            }
            MatchResult result = buildResultForRepo(repo, app);
            RepoMatchCache.put(pkg, repo, true);
            return result;
        } catch (RateLimitException e) { return MatchResult.error(); }
          catch (Exception e) { return MatchResult.error(); }
    }

    public MatchResult refreshCachedRepo(GitHubRepo repo, InstalledAppInfo app) {
        if (repo == null) return MatchResult.notFound();
        if (app.isGooglePlay() || app.isFdroidInstalled()) return MatchResult.notFound();
        try { return buildResultForRepo(repo, app); }
        catch (Exception e) { return MatchResult.error(); }
    }

    private MatchResult buildResultForRepo(GitHubRepo repo, InstalledAppInfo app) throws Exception {
        GitHubRelease release = fetchLatestRelease(repo);
        MatchedApp matched = new MatchedApp();
        matched.setPackageName(app.getPackageName());
        matched.setAppLabel(app.getAppLabel());
        matched.setInstalledVersionName(app.getVersionName());
        matched.setRepo(repo);
        matched.setSourceType("github");
        matched.setOrigin("auto");
        matched.setSourceUrl(repo.getHtmlUrl());
        if (release == null || release.getTagName() == null) {
            matched.setLatestTag(null);
            matched.setDownloadUrl(null);
            matched.setUpdateAvailable(false);
            return MatchResult.found(matched, repo);
        }
        GitHubRelease.Asset asset = release.findApkAsset(false);
        matched.setLatestTag(release.getTagName());
        matched.setDownloadUrl(asset == null ? null : asset.getBrowserDownloadUrl());
        matched.setUpdateAvailable(asset != null && isNewerVersion(release.getTagName(), app.getVersionName()));
        return MatchResult.found(matched, repo);
    }

    private GitHubRepo verifyByRepositorySearch(String packageName, String appLabel) throws Exception {
        GitHubRepo result = searchAndVerifyRepository("\"" + packageName + "\"", packageName);
        if (result != null) return result;
        String label = sanitize(appLabel);
        if (label.isEmpty()) return null;
        return searchAndVerifyRepository("\"" + label + "\" android", packageName);
    }

    private GitHubRepo searchAndVerifyRepository(String query, String packageName) throws Exception {
        throttleGeneralSearchCall();
        Response<GitHubSearchResponse> response = executeWithRetry(api.searchRepositories(query, CANDIDATES_PER_QUERY));
        if (!response.isSuccessful() || response.body() == null || response.body().getItems() == null) return null;
        GitHubRepo fork = null;
        for (GitHubRepo candidate : response.body().getItems()) {
            if (candidate == null || candidate.getOwnerLogin() == null || candidate.getName() == null) continue;
            String path = findManifestOrBuildFilePath(candidate);
            if (path == null) continue;
            String content = fetchFileRaw(candidate.getOwnerLogin(), candidate.getName(), path);
            if (content == null) continue;
            boolean exact = "AndroidManifest.xml".equals(basename(path)) ? manifestDeclaresPackage(content, packageName) : buildDeclaresPackage(content, packageName);
            if (!exact) continue;
            GitHubRepo full = fetchFullRepo(candidate);
            if (!full.isFork()) return full;
            if (fork == null) fork = full;
        }
        return fork;
    }

    private GitHubRepo verifyByCodeSearch(String packageName) throws Exception {
        throttleCodeSearchCall();
        Response<GitHubCodeSearchResponse> response = executeWithRetry(api.searchCode("\"" + packageName + "\"", CANDIDATES_PER_QUERY));
        if (!response.isSuccessful() || response.body() == null || response.body().getItems() == null) return null;
        GitHubRepo fork = null;
        for (GitHubCodeSearchResponse.Item item : response.body().getItems()) {
            GitHubRepo candidate = item.getRepository();
            if (candidate == null || candidate.getOwnerLogin() == null || candidate.getName() == null || item.getPath() == null) continue;
            String file = basename(item.getPath());
            if (!"AndroidManifest.xml".equals(file) && !"build.gradle".equals(file) && !"build.gradle.kts".equals(file)) continue;
            String content = fetchFileRaw(candidate.getOwnerLogin(), candidate.getName(), item.getPath());
            if (content == null) continue;
            boolean exact = "AndroidManifest.xml".equals(file) ? manifestDeclaresPackage(content, packageName) : buildDeclaresPackage(content, packageName);
            if (!exact) continue;
            GitHubRepo full = fetchFullRepo(candidate);
            if (!full.isFork()) return full;
            if (fork == null) fork = full;
        }
        return fork;
    }

    private String findManifestOrBuildFilePath(GitHubRepo repo) {
        String branch = repo.getDefaultBranch() == null ? "main" : repo.getDefaultBranch();
        GitHubTree tree = fetchTree(repo.getOwnerLogin(), repo.getName(), branch);
        if (tree == null || tree.getTree() == null) return null;
        String manifest = null, build = null;
        for (GitHubTree.Entry entry : tree.getTree()) {
            if (entry == null || !"blob".equals(entry.getType()) || entry.getPath() == null) continue;
            String base = basename(entry.getPath());
            if ("AndroidManifest.xml".equals(base)) {
                if (entry.getPath().contains("/src/main/")) return entry.getPath();
                if (manifest == null) manifest = entry.getPath();
            } else if (build == null && ("build.gradle".equals(base) || "build.gradle.kts".equals(base))) build = entry.getPath();
        }
        return manifest != null ? manifest : build;
    }

    private GitHubTree fetchTree(String owner, String repo, String branch) {
        try {
            throttleContentCall();
            Response<GitHubTree> r = executeWithRetry(api.getTree(owner, repo, branch, 1));
            return r.isSuccessful() ? r.body() : null;
        } catch (RateLimitException e) { throw e; }
          catch (Exception e) { return null; }
    }

    private String fetchFileRaw(String owner, String repo, String path) {
        try {
            throttleContentCall();
            Response<String> r = executeWithRetry(api.getFileContentRaw(owner, repo, encodePath(path), "application/vnd.github.raw+json"));
            return r.isSuccessful() ? r.body() : null;
        } catch (RateLimitException e) { throw e; }
          catch (Exception e) { return null; }
    }

    private GitHubRepo fetchFullRepo(GitHubRepo repo) {
        try {
            throttleContentCall();
            Response<GitHubRepo> r = executeWithRetry(api.getRepo(repo.getOwnerLogin(), repo.getName()));
            return r.isSuccessful() && r.body() != null ? r.body() : repo;
        } catch (RateLimitException e) { throw e; }
          catch (Exception e) { return repo; }
    }

    private GitHubRelease fetchLatestRelease(GitHubRepo repo) throws Exception {
        throttleContentCall();
        Response<List<GitHubRelease>> r = executeWithRetry(api.listReleases(repo.getOwnerLogin(), repo.getName(), 100));
        if (!r.isSuccessful() || r.body() == null) return null;
        return GitHubRelease.firstBaseInstallable(r.body());
    }

    private <T> Response<T> executeWithRetry(retrofit2.Call<T> call) throws Exception {
        Response<T> response = call.execute();
        if (isRateLimited(response.code())) {
            sleepSilently(retryAfterMillis(response));
            response = call.clone().execute();
            if (isRateLimited(response.code())) throw new RateLimitException();
        }
        return response;
    }

    private static long retryAfterMillis(Response<?> response) {
        String header = response.headers().get("Retry-After");
        if (header != null) {
            try { return Math.min(30_000L, Math.max(1_000L, Long.parseLong(header.trim()) * 1000L)); } catch (NumberFormatException ignored) {}
        }
        String reset = response.headers().get("X-RateLimit-Reset");
        if (reset != null) {
            try {
                long resetAtSeconds = Long.parseLong(reset.trim());
                long waitMs = resetAtSeconds * 1000L - System.currentTimeMillis();
                if (waitMs > 0) return Math.min(30_000L, waitMs);
            } catch (NumberFormatException ignored) {}
        }
        return 5_000L;
    }

    private static void sleepSilently(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static boolean isRateLimited(int code) { return code == 403 || code == 429; }
    private static synchronized void throttleGeneralSearchCall() { throttle(lastGeneralSearchCallAtMillis, MIN_GENERAL_SEARCH_INTERVAL_MILLIS); lastGeneralSearchCallAtMillis = System.currentTimeMillis(); }
    private static synchronized void throttleCodeSearchCall() { throttle(lastCodeSearchCallAtMillis, MIN_CODE_SEARCH_INTERVAL_MILLIS); lastCodeSearchCallAtMillis = System.currentTimeMillis(); }
    private static synchronized void throttleContentCall() { throttle(lastContentCallAtMillis, MIN_CONTENT_CALL_INTERVAL_MILLIS); lastContentCallAtMillis = System.currentTimeMillis(); }
    private static void throttle(long last, long min) { long wait = min - (System.currentTimeMillis() - last); if (last != 0 && wait > 0) try { Thread.sleep(wait); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
    private static String basename(String path) { int i = path.lastIndexOf('/'); return i < 0 ? path : path.substring(i + 1); }
    private static boolean manifestDeclaresPackage(String content, String pkg) { return Pattern.compile("(?m)\\bpackage\\s*=\\s*[\\\"']" + Pattern.quote(pkg) + "[\\\"']").matcher(content).find(); }
    private static boolean buildDeclaresPackage(String content, String pkg) { return Pattern.compile("(?m)(?:applicationId|namespace)\\s*(?:=)?\\s*[\\\"']" + Pattern.quote(pkg) + "[\\\"']").matcher(content).find() || content.contains("\"" + pkg + "\""); }
    private static String sanitize(String label) { return label == null ? "" : label.replaceAll("[^\\p{L}\\p{N} ._-]", " ").replaceAll("\\s+", " ").trim(); }
    private static String encodePath(String path) { String[] parts = path.split("/"); StringBuilder b = new StringBuilder(); for (int i=0;i<parts.length;i++) { if(i>0)b.append('/'); try { b.append(URLEncoder.encode(parts[i], "UTF-8").replace("+", "%20")); } catch (UnsupportedEncodingException e) { b.append(parts[i]); } } return b.toString(); }
    private static boolean isNewerVersion(String remote, String local) { return com.opensourcefinder.app.install.InstallState.isNewer(remote, local); }
    private static List<Long> nums(String s) { java.util.ArrayList<Long> out=new java.util.ArrayList<>(); java.util.regex.Matcher m=Pattern.compile("\\d+").matcher(s); while(m.find())try{out.add(Long.parseLong(m.group()));}catch(Exception ignored){} return out; }
    private static final class RateLimitException extends RuntimeException {}
}
