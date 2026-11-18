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
import java.util.ArrayList;
import java.util.List;

public class ExploreApp implements Serializable {
    private String packageName;
    private String name;
    private String summary;
    private String description;
    private String latestVersionName;
    private long latestVersionCode;
    private String downloadUrl;
    private String sourceUrl;
    private String websiteUrl;
    private String issueTrackerUrl;
    private String translationUrl;
    private String metadataUrl;
    private String architecture;
    private String signer;
    private String apkHash;
    private String changelogUrl;
    private List<String> permissions = new ArrayList<>();
    private List<VersionInfo> previousVersions = new ArrayList<>();
    private boolean favorite;    private String iconUrl;
    private String repoName;
    private boolean installed;
    private long installedVersionCode;
    private List<String> categories = new ArrayList<>();
    private List<String> antiFeatures = new ArrayList<>();
    private List<String> screenshotUrls = new ArrayList<>();
    private long added;
    private long lastUpdated;
    private long apkSize;
    private String donateUrl;
    private String authorName;
    private String license;
    private int popularRank = Integer.MAX_VALUE;
    private String githubSlug;
    private String installedVersionName;

    public String getGithubSlug() { return githubSlug; }
    public void setGithubSlug(String githubSlug) { this.githubSlug = githubSlug; }
    public boolean isGithubRelease() { return githubSlug != null && !githubSlug.isEmpty(); }

    public String getInstalledVersionName() { return installedVersionName; }
    public void setInstalledVersionName(String installedVersionName) { this.installedVersionName = installedVersionName; }

    public List<String> getCategories() { return categories; }
    public void setCategories(List<String> categories) { this.categories = categories == null ? new ArrayList<>() : categories; }

    public List<String> getAntiFeatures() { return antiFeatures; }
    public void setAntiFeatures(List<String> antiFeatures) { this.antiFeatures = antiFeatures == null ? new ArrayList<>() : antiFeatures; }

    public List<String> getScreenshotUrls() { return screenshotUrls; }
    public void setScreenshotUrls(List<String> screenshotUrls) { this.screenshotUrls = screenshotUrls == null ? new ArrayList<>() : screenshotUrls; }

    public long getAdded() { return added; }
    public void setAdded(long added) { this.added = added; }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }

    public long getApkSize() { return apkSize; }
    public void setApkSize(long apkSize) { this.apkSize = apkSize; }

    public String getDonateUrl() { return donateUrl; }
    public void setDonateUrl(String donateUrl) { this.donateUrl = donateUrl; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public String getLicense() { return license; }
    public void setLicense(String license) { this.license = license; }

    public int getPopularRank() { return popularRank; }
    public void setPopularRank(int popularRank) { this.popularRank = popularRank; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLatestVersionName() { return latestVersionName; }
    public void setLatestVersionName(String latestVersionName) { this.latestVersionName = latestVersionName; }

    public long getLatestVersionCode() { return latestVersionCode; }
    public void setLatestVersionCode(long latestVersionCode) { this.latestVersionCode = latestVersionCode; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public String getWebsiteUrl() { return websiteUrl; }
    public void setWebsiteUrl(String v) { websiteUrl = v; }
    public String getIssueTrackerUrl() { return issueTrackerUrl; }
    public void setIssueTrackerUrl(String v) { issueTrackerUrl = v; }
    public String getTranslationUrl() { return translationUrl; }
    public void setTranslationUrl(String v) { translationUrl = v; }
    public String getMetadataUrl() { return metadataUrl; }
    public void setMetadataUrl(String v) { metadataUrl = v; }
    public String getArchitecture() { return architecture; }
    public void setArchitecture(String v) { architecture = v; }
    public String getSigner() { return signer; }
    public void setSigner(String v) { signer = v; }
    public String getApkHash() { return apkHash; }
    public void setApkHash(String v) { apkHash = v; }
    public String getChangelogUrl() { return changelogUrl; }
    public void setChangelogUrl(String v) { changelogUrl = v; }
    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> v) { permissions = v == null ? new ArrayList<>() : v; }
    public List<VersionInfo> getPreviousVersions() { return previousVersions; }
    public void setPreviousVersions(List<VersionInfo> v) { previousVersions = v == null ? new ArrayList<>() : v; }
    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean v) { favorite = v; }

    public static class VersionInfo implements Serializable {
        private String versionName;
        private long versionCode;
        private long size;
        private String downloadUrl;
        private String architecture;
        private String signer;
        public VersionInfo() {}
        public VersionInfo(String name, long code, long size, String url, String arch, String signer) {
            this.versionName=name; this.versionCode=code; this.size=size; this.downloadUrl=url;
            this.architecture=arch; this.signer=signer;
        }
        public String getVersionName(){return versionName;}
        public long getVersionCode(){return versionCode;}
        public long getSize(){return size;}
        public String getDownloadUrl(){return downloadUrl;}
        public String getArchitecture(){return architecture;}
        public String getSigner(){return signer;}
    }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

    public String getRepoName() { return repoName; }
    public void setRepoName(String repoName) { this.repoName = repoName; }

    public boolean isInstalled() { return installed; }
    public void setInstalled(boolean installed) { this.installed = installed; }
    public long getInstalledVersionCode() { return installedVersionCode; }
    public void setInstalledVersionCode(long installedVersionCode) { this.installedVersionCode = installedVersionCode; }

    public ExploreApp copy() {
        ExploreApp copy = new ExploreApp();
        copy.setPackageName(packageName);
        copy.setName(name);
        copy.setSummary(summary);
        copy.setDescription(description);
        copy.setLatestVersionName(latestVersionName);
        copy.setLatestVersionCode(latestVersionCode);
        copy.setDownloadUrl(downloadUrl);
        copy.setSourceUrl(sourceUrl);
        copy.setWebsiteUrl(websiteUrl);
        copy.setIssueTrackerUrl(issueTrackerUrl);
        copy.setTranslationUrl(translationUrl);
        copy.setMetadataUrl(metadataUrl);
        copy.setArchitecture(architecture);
        copy.setSigner(signer);
        copy.setApkHash(apkHash);
        copy.setChangelogUrl(changelogUrl);
        copy.setPermissions(new ArrayList<>(permissions));
        List<VersionInfo> versions = new ArrayList<>();
        for (VersionInfo version : previousVersions) {
            versions.add(new VersionInfo(version.getVersionName(), version.getVersionCode(),
                    version.getSize(), version.getDownloadUrl(), version.getArchitecture(),
                    version.getSigner()));
        }
        copy.setPreviousVersions(versions);
        copy.setFavorite(favorite);
        copy.setIconUrl(iconUrl);
        copy.setRepoName(repoName);
        copy.setInstalled(installed);
        copy.setInstalledVersionCode(installedVersionCode);
        copy.setCategories(new ArrayList<>(categories));
        copy.setAntiFeatures(new ArrayList<>(antiFeatures));
        copy.setScreenshotUrls(new ArrayList<>(screenshotUrls));
        copy.setAdded(added);
        copy.setLastUpdated(lastUpdated);
        copy.setApkSize(apkSize);
        copy.setDonateUrl(donateUrl);
        copy.setAuthorName(authorName);
        copy.setLicense(license);
        copy.setPopularRank(popularRank);
        copy.setGithubSlug(githubSlug);
        copy.setInstalledVersionName(installedVersionName);
        return copy;
    }
}
