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

import android.util.Base64;

import com.opensourcefinder.app.model.GitHubRelease;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class HostedRepositoryClient {
    private HostedRepositoryClient() {}

    public static final class Result {
        public final String type;
        public final String webUrl;
        public final String owner;
        public final String repo;
        public final String branch;
        public final String description;
        public final String readme;
        public final List<GitHubRelease> releases;

        Result(String type, String webUrl, String owner, String repo, String branch,
               String description, String readme, List<GitHubRelease> releases) {
            this.type = type;
            this.webUrl = webUrl;
            this.owner = owner;
            this.repo = repo;
            this.branch = branch;
            this.description = description;
            this.readme = readme;
            this.releases = releases;
        }
    }

    public static Result load(String sourceUrl) throws Exception {
        Ref ref = Ref.parse(sourceUrl);
        if (ref == null) return null;
        JSONObject repository = new JSONObject(get(ref.repositoryApiUrl()));
        String branch = repository.optString("default_branch", "main");
        String description = repository.optString("description", null);
        String webUrl = repository.optString("web_url", null);
        if (webUrl == null || webUrl.isEmpty()) webUrl = ref.webUrl();

        String readme = loadReadme(ref, branch);
        List<GitHubRelease> releases = loadReleases(ref);
        return new Result(ref.type, webUrl, ref.owner, ref.repo, branch, description, readme, releases);
    }

    private static String loadReadme(Ref ref, String branch) {
        String[] names = {"README.md", "Readme.md", "readme.md"};
        for (String name : names) {
            try {
                String raw = ref.type.equals("gitlab")
                        ? getGitLabRaw(ref, branch, name)
                        : getGiteaRaw(ref, branch, name);
                if (raw != null && !raw.trim().isEmpty()) return raw;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static List<GitHubRelease> loadReleases(Ref ref) {
        List<GitHubRelease> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(get(ref.releasesApiUrl()));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null || item.optBoolean("draft", false)) continue;
                String tag = item.optString("tag_name", item.optString("tag", null));
                String name = item.optString("name", null);
                String body = item.optString("description", item.optString("body", null));
                String published = item.optString("published_at", item.optString("released_at", item.optString("published", null)));
                boolean prerelease = item.optBoolean("prerelease", false);
                List<GitHubRelease.Asset> assets = new ArrayList<>();
                if (ref.type.equals("gitlab")) {
                    JSONObject assetRoot = item.optJSONObject("assets");
                    JSONArray links = assetRoot == null ? null : assetRoot.optJSONArray("links");
                    if (links != null) {
                        for (int j = 0; j < links.length(); j++) {
                            JSONObject link = links.optJSONObject(j);
                            if (link == null) continue;
                            String assetName = link.optString("name", null);
                            String url = link.optString("direct_asset_url", link.optString("url", null));
                            if (url != null) assets.add(GitHubRelease.asset(assetName, url, ""));
                        }
                    }
                } else {
                    JSONArray assetArray = item.optJSONArray("assets");
                    if (assetArray != null) {
                        for (int j = 0; j < assetArray.length(); j++) {
                            JSONObject asset = assetArray.optJSONObject(j);
                            if (asset == null) continue;
                            String assetName = asset.optString("name", null);
                            String url = asset.optString("browser_download_url",
                                    asset.optString("download_url", null));
                            if (url != null) assets.add(GitHubRelease.asset(assetName, url, asset.optString("content_type", "")));
                        }
                    }
                }
                result.add(new GitHubRelease(tag, name, body, published, false, prerelease, assets));
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static String getGitLabRaw(Ref ref, String branch, String path) throws Exception {
        String project = encode(ref.owner + "/" + ref.repo);
        String encodedPath = encode(path);
        return get(ref.apiBase() + "/projects/" + project + "/repository/files/" + encodedPath
                + "/raw?ref=" + encode(branch));
    }

    private static String getGiteaRaw(Ref ref, String branch, String path) throws Exception {
        JSONObject file = new JSONObject(get(ref.apiBase() + "/repos/" + ref.owner + "/" + ref.repo
                + "/contents/" + encode(path) + "?ref=" + encode(branch)));
        String content = file.optString("content", null);
        if (content != null && !content.isEmpty()) {
            return new String(Base64.decode(content.replaceAll("\\s", ""), Base64.DEFAULT), StandardCharsets.UTF_8);
        }
        String downloadUrl = file.optString("download_url", null);
        return downloadUrl == null || downloadUrl.isEmpty() ? null : get(downloadUrl);
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = com.opensourcefinder.app.network.HttpClients.open(new URI(url).toURL());
        c.setConnectTimeout(12000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "OpenSourceFinder/1.0");
        c.setRequestProperty("Accept", "application/json, text/plain, */*");
        try {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            InputStream stream = c.getInputStream();
            try (InputStream in = stream;
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder out = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) out.append(line).append('\n');
                return out.toString();
            }
        } finally {
            c.disconnect();
        }
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }

    private static final class Ref {
        final String type;
        final String host;
        final String owner;
        final String repo;

        Ref(String type, String host, String owner, String repo) {
            this.type = type;
            this.host = host;
            this.owner = owner;
            this.repo = repo;
        }

        static Ref parse(String raw) {
            try {
                String value = raw == null ? "" : raw.trim();
                if (!value.startsWith("http://") && !value.startsWith("https://")) value = "https://" + value;
                URI uri = URI.create(value);
                String host = uri.getHost();
                if (host == null) return null;
                host = host.toLowerCase(java.util.Locale.ROOT);
                List<String> parts = new ArrayList<>();
                for (String p : uri.getPath().split("/")) if (!p.isEmpty()) parts.add(p);
                if (parts.size() < 2) return null;
                if ("codeberg.org".equals(host)) {
                    return new Ref("codeberg", host, parts.get(0), parts.get(1).replaceAll("(?i)\\.git$", ""));
                }
                if ("gitlab.com".equals(host) || host.contains("gitlab")) {
                    StringBuilder owner = new StringBuilder();
                    for (int i = 0; i < parts.size() - 1; i++) {
                        if (i > 0) owner.append('/');
                        owner.append(parts.get(i));
                    }
                    return new Ref("gitlab", host, owner.toString(), parts.get(parts.size() - 1).replaceAll("(?i)\\.git$", ""));
                }
                return new Ref("gitea", host, parts.get(0), parts.get(1).replaceAll("(?i)\\.git$", ""));
            } catch (Exception ignored) {}
            return null;
        }

        String apiBase() { return "gitlab".equals(type) ? "https://" + host + "/api/v4" : "https://" + host + "/api/v1"; }
        String repositoryApiUrl() {
            try {
                if ("gitlab".equals(type)) return apiBase() + "/projects/" + encode(owner + "/" + repo);
            } catch (Exception ignored) {}
            return apiBase() + "/repos/" + owner + "/" + repo;
        }
        String releasesApiUrl() {
            try {
                if ("gitlab".equals(type)) return apiBase() + "/projects/" + encode(owner + "/" + repo) + "/releases?per_page=100";
            } catch (Exception ignored) {}
            return apiBase() + "/repos/" + owner + "/" + repo + "/releases?limit=50";
        }
        String webUrl() { return "https://" + host + "/" + owner + "/" + repo; }
    }
}
