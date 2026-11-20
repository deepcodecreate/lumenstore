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

package com.opensourcefinder.app.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class GitHubRelease implements Serializable {

    @SerializedName("tag_name")
    private String tagName;

    private String name;

    private String body;

    @SerializedName("published_at")
    private String publishedAt;

    private boolean draft;

    private boolean prerelease;

    private List<Asset> assets;

    public static class Asset implements Serializable {
        private String name;

        @SerializedName("browser_download_url")
        private String browserDownloadUrl;

        @SerializedName("content_type")
        private String contentType;

        private long size;

        public Asset() {}

        public Asset(String name, String browserDownloadUrl, String contentType) {
            this.name = name;
            this.browserDownloadUrl = browserDownloadUrl;
            this.contentType = contentType;
        }

        public String getName() {
            return name;
        }

        public String getBrowserDownloadUrl() {
            return browserDownloadUrl;
        }

        public String getContentType() {
            return contentType;
        }

        public long getSize() {
            return size;
        }
    }

    public GitHubRelease() {}

    public GitHubRelease(String tagName, String name, String body, String publishedAt,
                         boolean draft, boolean prerelease, List<Asset> assets) {
        this.tagName = tagName;
        this.name = name;
        this.body = body;
        this.publishedAt = publishedAt;
        this.draft = draft;
        this.prerelease = prerelease;
        this.assets = assets;
    }

    public static Asset asset(String name, String browserDownloadUrl, String contentType) {
        return new Asset(name, browserDownloadUrl, contentType);
    }

    public void setTagName(String tagName) { this.tagName = tagName; }
    public void setName(String name) { this.name = name; }
    public void setBody(String body) { this.body = body; }
    public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }
    public void setDraft(boolean draft) { this.draft = draft; }
    public void setPrerelease(boolean prerelease) { this.prerelease = prerelease; }
    public void setAssets(List<Asset> assets) { this.assets = assets; }

    public String getTagName() {
        return tagName;
    }

    public String getName() {
        return name;
    }

    public String getBody() {
        return body;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public boolean isDraft() {
        return draft;
    }

    public boolean isPrerelease() {
        return prerelease;
    }

    public List<Asset> getAssets() {
        return assets;
    }

    public Asset findApkAsset() {
        Asset base = findApkAsset(false);
        return base != null ? base : findApkAsset(true);
    }

    public Asset findApkAsset(boolean clone) {
        Asset best = findApkAsset(clone, false);
        return best != null ? best : findApkAsset(clone, true);
    }

    private Asset findApkAsset(boolean clone, boolean debug) {
        if (assets == null) return null;
        Asset best = null;
        int bestRank = Integer.MAX_VALUE;
        for (Asset asset : assets) {
            String name = asset == null ? null : asset.getName();
            if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
            if (isCloneName(name) != clone) continue;
            if (isDebugName(name) != debug) continue;
            int rank = DeviceAbi.rank(DeviceAbi.fromFileName(name));
            if (rank < bestRank) {
                best = asset;
                bestRank = rank;
            }
        }
        return best;
    }

    public boolean hasCloneAsset() {
        return findApkAsset(true) != null;
    }

    public static GitHubRelease firstStableInstallable(List<GitHubRelease> releases) {
        if (releases == null) return null;
        GitHubRelease firstAny = null;
        for (GitHubRelease release : releases) {
            if (release == null || release.isDraft() || release.getTagName() == null) continue;
            if (release.findApkAsset(false) == null) continue;
            if (!release.isPrerelease()) return release;
            if (firstAny == null) firstAny = release;
        }
        return firstAny;
    }

    public static GitHubRelease pickForInstalled(List<GitHubRelease> releases, boolean clone, String installedVersionName) {
        if (releases == null) return null;
        boolean trackPre = com.opensourcefinder.app.install.InstallState.isPreRelease(installedVersionName);
        GitHubRelease firstAny = null;
        for (GitHubRelease release : releases) {
            if (release == null || release.isDraft() || release.getTagName() == null) continue;
            if (release.findApkAsset(clone) == null) continue;
            if (trackPre || !release.isPrerelease()) return release;
            if (firstAny == null) firstAny = release;
        }
        return firstAny;
    }

    public static GitHubRelease firstBaseInstallable(List<GitHubRelease> releases) {
        if (releases == null) return null;
        for (GitHubRelease release : releases) {
            if (release == null || release.isDraft() || release.getTagName() == null) continue;
            if (release.findApkAsset(false) != null) return release;
        }
        return null;
    }

    private static final Pattern CLONE_TOKEN = Pattern.compile(
            "(?<![a-z0-9])(clone|clones|cloned|clon|dual|dualapp|dualspace|dual-space|parallel|parallelspace|twin|twins|2nd|second|secondary|cl)(?![a-z0-9])");
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");

    private static final Pattern DEBUG_TOKEN = Pattern.compile("(?<![a-z0-9])(debug|dbg)(?![a-z0-9])");

    public static boolean isDebugName(String name) {
        if (name == null) return false;
        String spaced = CAMEL_BOUNDARY.matcher(name).replaceAll("$1-$2");
        return DEBUG_TOKEN.matcher(spaced.toLowerCase(Locale.ROOT)).find();
    }

    public static boolean isCloneName(String name) {
        if (name == null) return false;
        String spaced = CAMEL_BOUNDARY.matcher(name).replaceAll("$1-$2");
        String lower = spaced.toLowerCase(Locale.ROOT);
        if (lower.contains("cyclone")) return false;
        if (lower.contains("clone")) return true;
        return CLONE_TOKEN.matcher(lower).find();
    }
}
