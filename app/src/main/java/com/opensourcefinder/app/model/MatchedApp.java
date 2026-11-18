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

import java.io.Serializable;

public class MatchedApp implements Serializable {

    private String packageName;
    private String appLabel;
    private String installedVersionName;
    private GitHubRepo repo;
    private String latestTag;
    private boolean updateAvailable;
    private String sourceType = "github";
    private String sourceUrl;
    private String downloadUrl;
    private long latestVersionCode;
    private String origin = "auto";
    private String description;
    private String iconUrl;
    private String readme;
    private java.util.List<GitHubRelease> releases;

    private boolean notInstalled;

    private String variant;

    private boolean secondary;

    private String installedTag;

    private long installedTagVersionCode;

    private String indexRepo;

    private String githubSlug;

    private boolean fromBackup;

    public static final String LUMEN_REPO_NAME = "Lumen Store";

    public static final String VARIANT_CLONE = "clone";

    public MatchedApp copy() {
        MatchedApp c = new MatchedApp();
        c.packageName = packageName; c.appLabel = appLabel; c.installedVersionName = installedVersionName;
        c.repo = repo; c.latestTag = latestTag; c.updateAvailable = updateAvailable;
        c.sourceType = sourceType; c.sourceUrl = sourceUrl; c.downloadUrl = downloadUrl;
        c.latestVersionCode = latestVersionCode; c.origin = origin; c.description = description;
        c.iconUrl = iconUrl; c.readme = readme; c.releases = releases; c.notInstalled = notInstalled; c.variant = variant; c.secondary = secondary; c.installedTag = installedTag; c.installedTagVersionCode = installedTagVersionCode;
        c.indexRepo = indexRepo; c.githubSlug = githubSlug; c.fromBackup = fromBackup;
        return c;
    }

    public boolean isFromBackup() { return fromBackup; }
    public void setFromBackup(boolean fromBackup) { this.fromBackup = fromBackup; }

    public String getIndexRepo() { return indexRepo; }
    public void setIndexRepo(String indexRepo) { this.indexRepo = indexRepo; }

    public String getGithubSlug() { return githubSlug; }
    public void setGithubSlug(String githubSlug) { this.githubSlug = githubSlug; }
    public boolean isGithubRelease() { return githubSlug != null && !githubSlug.isEmpty(); }

    public String getVariant() { return variant; }
    public void setVariant(String variant) { this.variant = variant; }
    public boolean isSecondary() { return secondary; }
    public void setSecondary(boolean secondary) { this.secondary = secondary; }

    public String getInstalledTag() { return installedTag; }
    public void setInstalledTag(String installedTag) { this.installedTag = installedTag; }

    public long getInstalledTagVersionCode() { return installedTagVersionCode; }
    public void setInstalledTagVersionCode(long installedTagVersionCode) { this.installedTagVersionCode = installedTagVersionCode; }

    public String getStoreKey() {
        if (secondary && repo != null && repo.getOwnerLogin() != null && repo.getName() != null) {
            return packageName + "|" + repo.getOwnerLogin() + "/" + repo.getName();
        }
        return packageName;
    }

    public boolean isCloneVariant() { return VARIANT_CLONE.equals(variant); }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getAppLabel() { return appLabel; }
    public void setAppLabel(String appLabel) { this.appLabel = appLabel; }

    public String getInstalledVersionName() { return installedVersionName; }
    public void setInstalledVersionName(String installedVersionName) { this.installedVersionName = installedVersionName; }

    public GitHubRepo getRepo() { return repo; }
    public void setRepo(GitHubRepo repo) { this.repo = repo; }

    public String getLatestTag() { return latestTag; }
    public void setLatestTag(String latestTag) { this.latestTag = latestTag; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public long getLatestVersionCode() { return latestVersionCode; }
    public void setLatestVersionCode(long latestVersionCode) { this.latestVersionCode = latestVersionCode; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public boolean isUpdateAvailable() { return updateAvailable; }
    public void setUpdateAvailable(boolean updateAvailable) { this.updateAvailable = updateAvailable; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getReadme() { return readme; }
    public void setReadme(String readme) { this.readme = readme; }

    public java.util.List<GitHubRelease> getReleases() {
        return releases == null ? new java.util.ArrayList<GitHubRelease>() : new java.util.ArrayList<>(releases);
    }
    public void setReleases(java.util.List<GitHubRelease> releases) {
        this.releases = releases == null ? null : new java.util.ArrayList<>(releases);
    }

    public boolean isLazyIndexSource() {
        return "fdroid".equalsIgnoreCase(sourceType)
                && !LUMEN_REPO_NAME.equalsIgnoreCase(indexRepo)
                && downloadUrl != null
                && downloadUrl.contains("apt.izzysoft.de");
    }

    public boolean isLumenSource() {
        if (!"fdroid".equalsIgnoreCase(sourceType)) return false;
        if (LUMEN_REPO_NAME.equalsIgnoreCase(indexRepo)) return true;
        return downloadUrl != null && downloadUrl.contains("deepcodecreate/lumenrepo");
    }

    public boolean isNotInstalled() { return notInstalled; }
    public void setNotInstalled(boolean notInstalled) { this.notInstalled = notInstalled; }

    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
    public String getIconUrl() {
        if (iconUrl != null) return iconUrl;
        if (repo != null && repo.getOwnerLogin() != null) {
            return "https://github.com/" + repo.getOwnerLogin() + ".png";
        }
        return null;
    }
}
