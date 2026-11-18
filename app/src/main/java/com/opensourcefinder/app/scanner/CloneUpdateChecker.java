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
import android.content.pm.PackageInfo;
import com.opensourcefinder.app.install.InstallState;
import com.opensourcefinder.app.model.GitHubRelease;
import com.opensourcefinder.app.model.GitHubRepo;
import com.opensourcefinder.app.model.MatchedApp;
import com.opensourcefinder.app.network.GitHubApiService;
import com.opensourcefinder.app.network.RetrofitClient;
import java.util.List;
import retrofit2.Response;

public final class CloneUpdateChecker {

    private CloneUpdateChecker() {}

    public static void refresh(Context context, List<MatchedApp> apps) {
        if (apps == null) return;
        GitHubApiService api = RetrofitClient.getService();
        GitHubApiService publicApi = RetrofitClient.getPublicService();
        for (MatchedApp app : apps) {
            if (app == null || !app.isCloneVariant() || app.isNotInstalled()) continue;
            GitHubRepo repo = app.getRepo();
            if (repo == null || repo.getOwnerLogin() == null || repo.getName() == null) continue;
            String installedVersion = installedVersionName(context, app.getPackageName());
            if (installedVersion == null) continue;
            GitHubRelease release = latestCloneRelease(api, publicApi, repo);
            if (release == null) continue;
            GitHubRelease.Asset asset = release.findApkAsset(true);
            if (asset == null) continue;
            app.setInstalledVersionName(installedVersion);
            app.setLatestTag(release.getTagName());
            app.setDownloadUrl(asset.getBrowserDownloadUrl());
            app.setUpdateAvailable(InstallState.updateFor(app, InstallState.read(context, app.getPackageName())));
        }
    }

    private static String installedVersionName(Context context, String packageName) {
        if (packageName == null) return null;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (Exception notInstalled) {
            return null;
        }
    }

    private static GitHubRelease latestCloneRelease(GitHubApiService api, GitHubApiService publicApi, GitHubRepo repo) {
        try {
            Response<List<GitHubRelease>> response = api.listReleases(repo.getOwnerLogin(), repo.getName(), 100).execute();
            if (response.code() == 401 || response.code() == 403) {
                response = publicApi.listReleases(repo.getOwnerLogin(), repo.getName(), 100).execute();
            }
            if (!response.isSuccessful() || response.body() == null) return null;
            for (GitHubRelease release : response.body()) {
                if (release == null || release.isDraft() || release.getTagName() == null) continue;
                if (release.findApkAsset(true) != null) return release;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
