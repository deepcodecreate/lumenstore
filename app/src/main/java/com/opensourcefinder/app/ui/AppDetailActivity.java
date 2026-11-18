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

package com.opensourcefinder.app.ui;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.text.Html;
import android.text.TextUtils;
import android.util.TypedValue;
import android.content.res.ColorStateList;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.opensourcefinder.app.model.ExploreApp;
import com.opensourcefinder.app.model.GitHubRelease;
import com.opensourcefinder.app.scanner.FdroidIndexManager;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.net.HttpURLConnection;
import java.net.URL;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.download.DownloadTracker;
import com.opensourcefinder.app.ui.IconShapeHelper;
import com.opensourcefinder.app.install.InstallTracker;
import com.opensourcefinder.app.model.GitHubRepo;
import com.opensourcefinder.app.model.MatchedApp;
import com.opensourcefinder.app.network.RetrofitClient;
import com.opensourcefinder.app.scanner.MatchStore;
import com.opensourcefinder.app.scanner.HostedRepositoryClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Response;

public class AppDetailActivity extends BaseActivity {
    public static final String EXTRA_MATCHED_APP = "extra_matched_app";
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private MatchedApp matchedApp;
    private DownloadActionButton updateButton;
    private TextView downloadCaption;
    private TextView descriptionView;
    private com.google.android.material.loadingindicator.LoadingIndicator readmeProgress;
    private DownloadTracker.Listener downloadListener;
    private InstallTracker.Listener installListener;
    private boolean releasesExpanded;
    private MenuItem runMenuItem;
    private ExploreApp fdroidApp;
    private java.io.File pendingReinstallApk;
    private static final int UNINSTALL_REQUEST = 9137;
    private MaterialToolbar toolbar;

    @Override protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        MotionHelper.applyDistinctActivityTransition(this);
        setContentView(R.layout.activity_app_detail);
        MotionHelper.animateActivityContent(this);
        ThemeManager.applyWindow(this);
        matchedApp = (MatchedApp) getIntent().getSerializableExtra(EXTRA_MATCHED_APP);
        if (matchedApp == null) { finish(); return; }
        if (com.opensourcefinder.app.network.PlayStoreClient.isPlayApp(matchedApp)) {
            Intent play = new Intent(this, PlaySearchActivity.class);
            play.putExtra(PlaySearchActivity.EXTRA_PACKAGE, matchedApp.getPackageName());
            startActivity(play);
            finish();
            return;
        }
        if ("fdroid".equals(matchedApp.getSourceType())) {
            Intent explore = new Intent(this, ExploreDetailActivity.class);
            explore.putExtra(ExploreDetailActivity.EXTRA_MATCHED_APP, matchedApp);
            startActivity(explore);
            finish();
            return;
        }

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        ToolbarCircleHelper.apply(toolbar);

        ImageView icon = findViewById(R.id.detailAppIcon);
        TextView name = findViewById(R.id.detailAppName);
        TextView versions = findViewById(R.id.detailVersions);
        updateButton = findViewById(R.id.updateButton);
        downloadCaption = findViewById(R.id.downloadCaption);
        descriptionView = findViewById(R.id.readmeView);
        readmeProgress = findViewById(R.id.readmeProgress);
        IconShapeHelper.apply(icon);
        DetailHero.setup(this, R.id.detailAppIcon);

        name.setText(matchedApp.getAppLabel());
        refreshVersionText(versions);
        Drawable appIcon = loadIcon(matchedApp.getPackageName());
        if (appIcon != null) icon.setImageDrawable(appIcon);

        boolean canDownload = matchedApp.getDownloadUrl() != null;
        updateButton.setEnabled(canDownload && (matchedApp.isUpdateAvailable() || matchedApp.isNotInstalled()));
        if (matchedApp.isNotInstalled()) updateButton.setText(R.string.install);
        updateButton.setOnClickListener(v -> startUpdate(false));
        updateButton.setOnCancelListener(this::cancelDownload);
        setupInstalledActions();
        updateActionVisibility();

        loadGithubReadmeOnly();
        restoreDownloadState();
        restoreInstallState();
        animateDetailEntrance();
    }

    private void animateDetailEntrance() {
        View root = findViewById(R.id.appDetailContentRoot);
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            int index = 0;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE) continue;
                MotionHelper.animateEntrance(child, index++);
            }
        }
    }

    private void updateActionVisibility() {
        if (matchedApp == null) return;
        DownloadTracker.Entry entry = DownloadTracker.get().getEntry(matchedApp.getPackageName());
        boolean downloading = entry != null && entry.state == DownloadTracker.State.DOWNLOADING;
        boolean awaitingInstall = entry != null && entry.state == DownloadTracker.State.COMPLETED;
        boolean installed = !matchedApp.isNotInstalled();
        boolean updateAvailable = matchedApp.isUpdateAvailable();

        if (downloadCaption != null) downloadCaption.setVisibility(View.GONE);

        View primaryRow = findViewById(R.id.primaryActionRow);
        boolean showPrimary = !installed || updateAvailable || downloading || awaitingInstall;
        if (primaryRow != null) primaryRow.setVisibility(showPrimary ? View.VISIBLE : View.GONE);

        View installedActions = findViewById(R.id.installedActions);
        boolean showInstalledActions = installed && !updateAvailable && !downloading && !awaitingInstall;
        if (installedActions != null) installedActions.setVisibility(showInstalledActions ? View.VISIBLE : View.GONE);
    }

    private void cancelDownload() {
        if (matchedApp == null) return;
        DownloadTracker.get().cancel(matchedApp.getPackageName());
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_app_detail, menu);
        MenuIconTintHelper.tint(menu, toolbar);
        runMenuItem = menu.findItem(R.id.action_run);
        updateRunMenuVisibility();
        MenuItem latest = menu.findItem(R.id.action_latest);
        if (latest != null) latest.setVisible(matchedApp.getDownloadUrl() != null);
        MenuItem repo = menu.findItem(R.id.action_repo);
        if (repo != null) repo.setVisible(matchedApp.getSourceUrl() != null);
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_latest) {
            startUpdate(true);
            return true;
        }
        if (id == R.id.action_run) {
            runInstalledApp();
            return true;
        }
        if (id == R.id.action_share) { Intent share=new Intent(Intent.ACTION_SEND); share.setType("text/plain"); share.putExtra(Intent.EXTRA_TEXT, LumenLinks.shareText(matchedApp.getAppLabel(), matchedApp.getPackageName(), matchedApp.getSourceUrl())); startActivity(Intent.createChooser(share,"Share")); return true; }
        if (id == R.id.action_repo) {
            if (matchedApp.getSourceUrl() != null)
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(matchedApp.getSourceUrl())));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateRunMenuVisibility() {
        if (runMenuItem == null) return;
        try {
            getPackageManager().getPackageInfo(matchedApp.getPackageName(), 0);
            Intent launch = getPackageManager().getLaunchIntentForPackage(matchedApp.getPackageName());
            runMenuItem.setVisible(launch != null);
        } catch (Exception e) {
            runMenuItem.setVisible(false);
        }
    }

    private void refreshVersionText(TextView versions) {
        com.opensourcefinder.app.install.InstallState.refresh(this, matchedApp);
        if (matchedApp.isNotInstalled()) {
            matchedApp.setInstalledVersionName("");
            matchedApp.setUpdateAvailable(false);
        }
        String text = matchedApp.isNotInstalled()
                ? getString(R.string.not_installed)
                : getString(R.string.installed_version, matchedApp.getInstalledVersionName());
        if (matchedApp.getLatestTag() != null) text += "  |  " + getString(R.string.latest_version, matchedApp.getLatestTag());
        if (matchedApp.getSourceType() != null) text += "  |  " + getString(R.string.source_label, sourceName(matchedApp.getSourceType()));
        versions.setText(text);
    }

    private void loadGithubReadmeOnly() {
        View card = findViewById(R.id.readmeCard);
        TextView title = findViewById(R.id.readmeTitle);
        RecyclerView readmeRecycler = findViewById(R.id.readmeRecycler);
        String source = matchedApp.getSourceUrl();
        boolean isGithub = source != null && source.toLowerCase(Locale.ROOT).contains("github.com/");
        boolean isHosted = "gitlab".equalsIgnoreCase(matchedApp.getSourceType())
                || "codeberg".equalsIgnoreCase(matchedApp.getSourceType())
                || "gitea".equalsIgnoreCase(matchedApp.getSourceType());

        if (readmeRecycler != null) readmeRecycler.setVisibility(View.GONE);
        if (readmeProgress != null) readmeProgress.setVisibility(View.GONE);

        if (isHosted) {
            if (card != null) card.setVisibility(View.GONE);
            List<GitHubRelease> cachedReleases = matchedApp.getReleases();
            if (cachedReleases != null && !cachedReleases.isEmpty()) {
                renderGithubReleases(cachedReleases);
            }
            loadHostedRepository(source);
            return;
        }

        if (!isGithub) {
            descriptionView.setVisibility(View.VISIBLE);
            String description = matchedApp.getDescription();
            if ("fdroid".equals(matchedApp.getSourceType()) && description != null && !description.trim().isEmpty()) {
                if (card != null) card.setVisibility(View.VISIBLE);
                if (title != null) title.setText(R.string.about_this_app);
                MarkdownRenderer.render(descriptionView, description);
                ReadMoreHelper.setup(descriptionView, findViewById(R.id.readmeReadMore));
            } else {
                if (card != null) card.setVisibility(View.GONE);
            }
            return;
        }

        descriptionView.setVisibility(View.GONE);
        findViewById(R.id.readmeReadMore).setVisibility(View.GONE);
        if (card != null) card.setVisibility(View.GONE);

        String[] repoParts = githubParts(source);
        String owner = repoParts == null ? null : repoParts[0];
        String repoName = repoParts == null ? null : repoParts[1];
        if (matchedApp.getRepo() != null) {
            if (matchedApp.getRepo().getOwnerLogin() != null) owner = matchedApp.getRepo().getOwnerLogin();
            if (matchedApp.getRepo().getName() != null) repoName = matchedApp.getRepo().getName();
            bindGithubRepoSummary(matchedApp.getRepo());
        }
        if (owner == null || repoName == null || owner.isEmpty() || repoName.isEmpty()) return;
        loadGithubRepoSummary(owner, repoName);
        loadGithubReleases(owner, repoName);
    }

    private void loadHostedRepository(String source) {
        executorService.execute(() -> {
            try {
                HostedRepositoryClient.Result result = HostedRepositoryClient.load(source);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (result == null) return;
                    matchedApp.setRepo(new GitHubRepo(result.owner, result.repo, result.description, result.branch, false, result.webUrl));
                    matchedApp.setSourceUrl(result.webUrl);
                    matchedApp.setDescription(result.description);
                    bindGithubRepoSummary(matchedApp.getRepo());
                    List<GitHubRelease> releases = result.releases == null ? new ArrayList<>() : result.releases;
                    if (releases.isEmpty()) releases = matchedApp.getReleases();
                    else matchedApp.setReleases(releases);
                    GitHubRelease latestInstallable = pickLatestInstallable(releases);
                    if (latestInstallable != null && latestInstallable.getTagName() != null) {
                        matchedApp.setLatestTag(latestInstallable.getTagName());
                        matchedApp.setDownloadUrl(assetOf(latestInstallable).getBrowserDownloadUrl());
                        com.opensourcefinder.app.install.InstallState.refresh(this, matchedApp);
                        TextView versions = findViewById(R.id.detailVersions);
                        if (versions != null) refreshVersionText(versions);
                        refreshPrimaryButton();
                        updateActionVisibility();
                        persistResolvedLatest();
                    }
                    renderGithubReleases(releases);
                });
            } catch (Exception ignored) {
            }
        });
    }

    private boolean isAuthFailure(Response<?> response) {
        return response != null && (response.code() == 401 || response.code() == 403);
    }

    private String[] githubParts(String source) {
        try {
            if (source == null || source.trim().isEmpty()) return null;
            String value = source.trim();
            if (!value.startsWith("http://") && !value.startsWith("https://")) value = "https://" + value;
            Uri uri = Uri.parse(value);
            String host = uri.getHost();
            if (host == null || (!"github.com".equalsIgnoreCase(host) && !"www.github.com".equalsIgnoreCase(host))) return null;
            List<String> segments = uri.getPathSegments();
            if (segments.size() < 2) return null;
            String owner = segments.get(0);
            String repo = segments.get(1).replaceAll("\\.git$", "");
            if (owner.isEmpty() || repo.isEmpty()) return null;
            return new String[]{owner, repo};
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isVersionNewer(String remote, String local) {
        return com.opensourcefinder.app.install.InstallState.isNewer(remote, local);
    }

    private List<Long> versionNumbers(String value) {
        List<Long> values = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(value);
        while (matcher.find()) {
            try { values.add(Long.parseLong(matcher.group())); } catch (Exception ignored) {}
        }
        return values;
    }

    private void bindGithubRepoSummary(GitHubRepo repo) {
        View card = findViewById(R.id.manualRepoCard);
        if (card == null || repo == null) return;
        TextView name = findViewById(R.id.manualRepoName);
        TextView description = findViewById(R.id.manualRepoDescription);
        TextView stars = findViewById(R.id.manualRepoStars);
        TextView latest = findViewById(R.id.manualRepoLatestTag);
        if (name != null) name.setText(TextUtils.isEmpty(repo.getFullName()) ? repo.getName() : repo.getFullName());
        if (description != null) {
            description.setText(TextUtils.isEmpty(repo.getDescription())
                    ? getString(R.string.github_repository)
                    : repo.getDescription());
        }
        if (stars != null) stars.setText(getString(R.string.github_stars, repo.getStargazersCount()));
        if (latest != null && !TextUtils.isEmpty(matchedApp.getLatestTag())) {
            latest.setText(getString(R.string.github_latest_tag, matchedApp.getLatestTag()));
        }
        card.setVisibility(View.VISIBLE);
    }

    private void loadGithubRepoSummary(String owner, String repoName) {
        RetrofitClient.getService().getRepo(owner, repoName).enqueue(new retrofit2.Callback<GitHubRepo>() {
            @Override public void onResponse(retrofit2.Call<GitHubRepo> call, Response<GitHubRepo> response) {
                if (isAuthFailure(response)) {
                    RetrofitClient.getPublicService().getRepo(owner, repoName).enqueue(this);
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    matchedApp.setRepo(response.body());
                    bindGithubRepoSummary(response.body());
                }
            }
            @Override public void onFailure(retrofit2.Call<GitHubRepo> call, Throwable t) {}
        });
    }

    private void loadGithubReleases(String owner, String repoName) {
        View section = findViewById(R.id.manualReleasesSection);
        View progress = findViewById(R.id.manualReleasesProgress);
        if (section != null) section.setVisibility(View.VISIBLE);
        if (progress != null) progress.setVisibility(View.VISIBLE);
        RetrofitClient.getService().listReleases(owner, repoName, 100)
                .enqueue(new retrofit2.Callback<List<GitHubRelease>>() {
                    @Override public void onResponse(retrofit2.Call<List<GitHubRelease>> call, Response<List<GitHubRelease>> response) {
                        if (isAuthFailure(response)) {
                            RetrofitClient.getPublicService().listReleases(owner, repoName, 100).enqueue(this);
                            return;
                        }
                        if (progress != null) progress.setVisibility(View.GONE);
                        if (!response.isSuccessful() || response.body() == null) {
                            if (section != null) section.setVisibility(View.GONE);
                            return;
                        }
                        List<GitHubRelease> releases = new ArrayList<>();
                        for (GitHubRelease release : response.body()) {
                            if (release != null && !release.isDraft() && assetOf(release) != null) releases.add(release);
                        }
                        GitHubRelease latestInstallable = pickLatestInstallable(releases);
                        if (latestInstallable != null && latestInstallable.getTagName() != null) {
                            matchedApp.setLatestTag(latestInstallable.getTagName());
                            matchedApp.setDownloadUrl(assetOf(latestInstallable).getBrowserDownloadUrl());
                            com.opensourcefinder.app.install.InstallState.refresh(AppDetailActivity.this, matchedApp);
                            TextView versions = findViewById(R.id.detailVersions);
                            if (versions != null) refreshVersionText(versions);
                            bindGithubRepoSummary(matchedApp.getRepo());
                            refreshPrimaryButton();
                            updateActionVisibility();
                            persistResolvedLatest();
                        }
                        renderGithubReleases(releases);
                    }
                    @Override public void onFailure(retrofit2.Call<List<GitHubRelease>> call, Throwable t) {
                        if (progress != null) progress.setVisibility(View.GONE);
                        if (section != null) section.setVisibility(View.GONE);
                    }
                });
    }

    private GitHubRelease.Asset assetOf(GitHubRelease release) {
        if (release == null) return null;
        return release.findApkAsset(matchedApp != null && matchedApp.isCloneVariant());
    }

    private GitHubRelease pickLatestInstallable(List<GitHubRelease> releases) {
        if (releases == null) return null;
        boolean clone = matchedApp != null && matchedApp.isCloneVariant();
        String installedName = matchedApp == null || matchedApp.isNotInstalled() ? null : matchedApp.getInstalledVersionName();
        return GitHubRelease.pickForInstalled(releases, clone, installedName);
    }

    private void persistResolvedLatest() {
        try { MatchStore.updateIfPresent(this, matchedApp); } catch (Exception ignored) {}
    }

    private boolean isPrereleaseRepository() {
        if (matchedApp == null || matchedApp.getRepo() == null) return false;
        String name = matchedApp.getRepo().getName();
        if (name == null) return false;
        String normalized = name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
        return java.util.regex.Pattern.compile(
                "(^|[-_.])(?:alpha|beta|snapshot|nightly|preview|dev|rc|pre[-_.]?release)(?=$|[-_.])")
                .matcher(normalized).find();
    }

    private void refreshCloneButton(List<GitHubRelease> releases) {
        View button = findViewById(R.id.cloneVersionButton);
        if (button == null || matchedApp == null) return;
        GitHubRelease target = null;
        boolean hasBase = false;
        if (!matchedApp.isCloneVariant() && releases != null) {
            for (GitHubRelease candidate : releases) {
                if (candidate == null || candidate.isDraft() || candidate.getTagName() == null) continue;
                if (candidate.findApkAsset(false) != null) hasBase = true;
                if (target == null && candidate.findApkAsset(true) != null) target = candidate;
            }
        }
        if (target == null || (!hasBase && !isPrereleaseRepository())) {
            button.setVisibility(View.GONE);
            return;
        }
        final GitHubRelease cloneRelease = target;
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(v -> openCloneVariant(cloneRelease));
    }

    private MatchedApp findSavedClone() {
        GitHubRepo repo = matchedApp.getRepo();
        if (repo == null || repo.getOwnerLogin() == null || repo.getName() == null) return null;
        for (MatchedApp saved : MatchStore.get(this)) {
            if (saved == null || !saved.isCloneVariant() || saved.getRepo() == null) continue;
            GitHubRepo other = saved.getRepo();
            if (repo.getOwnerLogin().equalsIgnoreCase(other.getOwnerLogin())
                    && repo.getName().equalsIgnoreCase(other.getName())) return saved;
        }
        return null;
    }

    private void openCloneVariant(GitHubRelease release) {
        GitHubRelease.Asset asset = release.findApkAsset(true);
        if (asset == null || TextUtils.isEmpty(asset.getBrowserDownloadUrl())) {
            Toast.makeText(this, R.string.download_link_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        MatchedApp clone = findSavedClone();
        if (clone == null) {
            clone = matchedApp.copy();
            clone.setPackageName(matchedApp.getPackageName() + "@clone");
            clone.setAppLabel(getString(R.string.clone_label_format, matchedApp.getAppLabel()));
            clone.setVariant(MatchedApp.VARIANT_CLONE);
            clone.setOrigin("manual");
            clone.setNotInstalled(true);
            clone.setInstalledVersionName("");
            clone.setUpdateAvailable(false);
            clone.setReleases(null);
            clone.setLatestTag(release.getTagName());
            clone.setDownloadUrl(asset.getBrowserDownloadUrl());
        }
        Intent intent = new Intent(this, AppDetailActivity.class);
        intent.putExtra(EXTRA_MATCHED_APP, clone);
        startActivity(intent);
    }

    private void renderGithubReleases(List<GitHubRelease> releases) {
        refreshCloneButton(releases);
        LinearLayout container = findViewById(R.id.manualReleases);
        View section = findViewById(R.id.manualReleasesSection);
        View toggle = findViewById(R.id.manualReleasesToggle);
        ImageView toggleIcon = findViewById(R.id.manualReleasesIcon);
        if (container == null || section == null) return;
        container.removeAllViews();
        List<GitHubRelease> installable = new ArrayList<>();
        if (releases != null) {
            for (GitHubRelease release : releases) {
                if (release != null && !release.isDraft()
                        && (release.findApkAsset(false) != null || release.findApkAsset(true) != null)) {
                    installable.add(release);
                }
            }
        }
        if (installable.isEmpty()) {
            section.setVisibility(View.GONE);
            return;
        }
        section.setVisibility(View.VISIBLE);
        for (GitHubRelease release : installable) container.addView(buildGithubReleaseRow(release));
        TextView countView = findViewById(R.id.manualReleasesCount);
        if (countView != null) countView.setText(String.valueOf(installable.size()));
        ViewGroup animationRoot = findViewById(R.id.appDetailContentRoot);
        AccordionHelper.bind(toggle, animationRoot, container, toggleIcon, new AccordionHelper.ExpandedState() {
            @Override public boolean isExpanded() { return releasesExpanded; }
            @Override public void setExpanded(boolean expanded) { releasesExpanded = expanded; }
        });
    }

    private View buildGithubReleaseRow(GitHubRelease release) {
        boolean latest = release.getTagName() != null && release.getTagName().equals(matchedApp.getLatestTag());
        GitHubRelease.Asset apk = assetOf(release);
        boolean cloneOnlyRelease = matchedApp != null && !matchedApp.isCloneVariant()
                && apk == null && release.findApkAsset(true) != null;

        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(6);
        card.setLayoutParams(cardParams);
        card.setRadius(dp(20));
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        card.setStrokeColor(MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnPrimary, 0));
        card.setCardBackgroundColor(MaterialColors.getColor(card, latest
                ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorSurfaceContainer, 0));
        card.setClickable(apk != null || cloneOnlyRelease);
        card.setFocusable(apk != null || cloneOnlyRelease);
        TypedValue selectableBackground = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectableBackground, true)) {
            card.setForeground(getDrawable(selectableBackground.resourceId));
        }

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(dp(8), dp(8), dp(12), dp(8));

        int iconOnColorAttr = latest
                ? com.google.android.material.R.attr.colorOnPrimaryContainer
                : com.google.android.material.R.attr.colorOnSurfaceVariant;
        FrameLayout iconFrame = new FrameLayout(this);
        iconFrame.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
        android.graphics.drawable.GradientDrawable iconBg = new android.graphics.drawable.GradientDrawable();
        iconBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        iconBg.setColor(MaterialColors.getColor(card, latest
                ? com.google.android.material.R.attr.colorOnPrimaryContainer
                : com.google.android.material.R.attr.colorSurfaceContainerHigh, 0));
        iconFrame.setBackground(iconBg);
        ImageView icon = new ImageView(this);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(18), dp(18));
        iconParams.gravity = Gravity.CENTER;
        icon.setLayoutParams(iconParams);
        icon.setImageResource(TablerIconRes.get(this, latest ? "ic_check" : "ic_lm_history"));
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(MaterialColors.getColor(card,
                latest ? com.google.android.material.R.attr.colorPrimaryContainer : iconOnColorAttr, 0)));
        icon.setContentDescription(getString(latest ? R.string.latest_label : R.string.github_tag));
        iconFrame.addView(icon);
        row.addView(iconFrame);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMarginStart(dp(12));
        copy.setLayoutParams(copyParams);
        TextView tag = new TextView(this);
        tag.setText(TextUtils.isEmpty(release.getTagName()) ? release.getName() : release.getTagName());
        tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tag.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tag.setTextColor(MaterialColors.getColor(card, latest ? com.google.android.material.R.attr.colorOnPrimaryContainer : com.google.android.material.R.attr.colorOnSurface, 0));
        tag.setSingleLine(true);
        tag.setEllipsize(TextUtils.TruncateAt.END);
        TextView meta = new TextView(this);
        StringBuilder metaText = new StringBuilder();
        if (!TextUtils.isEmpty(release.getName()) && !release.getName().equals(release.getTagName())) {
            metaText.append(release.getName());
        }
        if (!TextUtils.isEmpty(release.getPublishedAt())) {
            if (metaText.length() > 0) metaText.append("  •  ");
            metaText.append(release.getPublishedAt().substring(0, Math.min(10, release.getPublishedAt().length())));
        }
        if (release.isPrerelease()) {
            if (metaText.length() > 0) metaText.append("  •  ");
            metaText.append(getString(R.string.github_prerelease));
        }
        if (cloneOnlyRelease) {
            if (metaText.length() > 0) metaText.append("  •  ");
            metaText.append(getString(R.string.github_clone_release));
        } else if (apk == null) {
            if (metaText.length() > 0) metaText.append("  •  ");
            metaText.append(getString(R.string.github_no_apk));
        }
        meta.setText(metaText.toString());
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        meta.setTextColor(MaterialColors.getColor(card, latest ? com.google.android.material.R.attr.colorOnPrimaryContainer : com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
        copy.addView(tag);
        copy.addView(meta);
        row.addView(copy);

        TextView badge = new TextView(this);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.setMarginStart(dp(8));
        badge.setLayoutParams(badgeParams);
        badge.setText(latest ? R.string.latest_label
                : cloneOnlyRelease ? R.string.clone_release_short
                : apk == null ? R.string.github_tag : R.string.install);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setPadding(dp(10), dp(3), dp(10), dp(3));
        android.graphics.drawable.GradientDrawable badgeBg = new android.graphics.drawable.GradientDrawable();
        badgeBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        badgeBg.setCornerRadius(dp(12));
        badgeBg.setColor(MaterialColors.getColor(card, latest
                ? com.google.android.material.R.attr.colorOnPrimaryContainer
                : com.google.android.material.R.attr.colorSurfaceContainerHigh, 0));
        badge.setBackground(badgeBg);
        badge.setTextColor(MaterialColors.getColor(card, latest
                ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
        row.addView(badge);

        card.addView(row);
        card.setContentDescription(tag.getText());
        if (apk != null) {
            card.setOnClickListener(v -> startGithubReleaseDownload(release));
        } else if (cloneOnlyRelease) {
            card.setOnClickListener(v -> openCloneVariant(release));
        }
        return card;
    }

    private void startGithubReleaseDownload(GitHubRelease release) {
        GitHubRelease.Asset apk = assetOf(release);
        if (apk == null || TextUtils.isEmpty(apk.getBrowserDownloadUrl())) {
            Toast.makeText(this, R.string.download_link_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        String tag = TextUtils.isEmpty(release.getTagName()) ? "release" : release.getTagName();
        String key = matchedApp.getPackageName() + "@github@" + tag;
        String fileName = matchedApp.getAppLabel() + "-" + tag.replaceAll("[^a-zA-Z0-9._-]", "_") + ".apk";
        DownloadTracker.get().start(this, key, apk.getBrowserDownloadUrl(), fileName, null, matchedApp.getIconUrl());
        Toast.makeText(this, R.string.older_version_download_started, Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, DownloadsActivity.class));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override protected void onResume() {
        super.onResume();
        if (matchedApp == null) return;
        checkInstallSucceeded();
        updateRunMenuVisibility();
        TextView versions = findViewById(R.id.detailVersions);
        if (versions != null) refreshVersionText(versions);
        restoreDownloadState();
        restoreInstallState();
        updateActionVisibility();
    }

    private void checkInstallSucceeded() {
        DownloadTracker.Entry entry = DownloadTracker.get().getEntry(matchedApp.getPackageName());
        DownloadTracker.get().detectInstalledPackages(this);
        String pkg = matchedApp.getPackageName();
        if (entry != null && entry.realPackageName != null) pkg = entry.realPackageName;
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(pkg, 0);
            long vc = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? pi.getLongVersionCode() : pi.versionCode;
            if (entry != null) DownloadTracker.get().markInstalled(entry.key);
            final String oldKey = matchedApp.getPackageName();
            matchedApp.setPackageName(pkg);
            com.opensourcefinder.app.install.InstallState.recordInstalledTag(this, matchedApp, entry);
            com.opensourcefinder.app.install.InstallState.refresh(this, matchedApp);
            if (entry != null) com.opensourcefinder.app.install.InstallState.markCurrentIfFromLatest(matchedApp, entry);
            try {
                String systemLabel = getPackageManager().getApplicationLabel(pi.applicationInfo).toString();
                if (matchedApp.isCloneVariant() && !systemLabel.toLowerCase(java.util.Locale.ROOT).contains("clone")) {
                    systemLabel = getString(R.string.clone_label_format, systemLabel);
                }
                matchedApp.setAppLabel(systemLabel);
            } catch (Exception ignored) {}
            MatchStore.upsertReplacing(this, matchedApp, oldKey, entry != null ? entry.key : null);
            updateButton.setEnabled(matchedApp.isUpdateAvailable() && matchedApp.getDownloadUrl() != null);
            updateButton.setText(matchedApp.isUpdateAvailable() ? getString(R.string.btn_update) : getString(R.string.installed));
            setupInstalledActions();
            updateActionVisibility();
            updateButton.hideProgress();
            ImageView icon = findViewById(R.id.detailAppIcon);
            if (icon != null) {
                try { icon.setImageDrawable(getPackageManager().getApplicationIcon(pkg)); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            handlePackageGone(pkg);
        }
    }

    private void handlePackageGone(String pkg) {
        if (matchedApp.isNotInstalled()) return;
        DownloadTracker.Entry entry = DownloadTracker.get().getEntry(matchedApp.getPackageName());
        if (entry != null) DownloadTracker.get().clear(matchedApp.getPackageName());
        matchedApp.setNotInstalled(true);
        matchedApp.setInstalledVersionName("");
        MatchStore.upsert(this, matchedApp);
        updateButton.setEnabled(matchedApp.getDownloadUrl() != null);
        updateButton.setText(R.string.install);
        updateButton.setOnClickListener(v -> startUpdate(false));
        setupInstalledActions();
        updateActionVisibility();
        updateButton.hideProgress();
        updateRunMenuVisibility();
    }

    @Override protected void onPause() {
        super.onPause();
        if (matchedApp != null && downloadListener != null) {
            DownloadTracker.get().detach(matchedApp.getPackageName(), downloadListener);
        }

    }

    private void refreshPrimaryButton() {
        if (matchedApp == null || updateButton == null) return;
        String pkg = matchedApp.getPackageName();
        InstallTracker.Entry install = InstallTracker.get().getEntry(pkg);
        if (install != null && (install.state == InstallTracker.State.INSTALLING
                || install.state == InstallTracker.State.AWAITING_CONFIRM)) {
            applyInstallState(install);
            return;
        }
        DownloadTracker.Entry download = DownloadTracker.get().getEntry(pkg);
        if (download != null && (download.state == DownloadTracker.State.DOWNLOADING
                || download.state == DownloadTracker.State.COMPLETED)) {
            applyEntryToButton(download);
            return;
        }
        updateButton.setEnabled(matchedApp.getDownloadUrl() != null
                && (matchedApp.isUpdateAvailable() || matchedApp.isNotInstalled()));
        updateButton.setText(matchedApp.isNotInstalled() ? R.string.install
                : (matchedApp.isUpdateAvailable() ? R.string.btn_update : R.string.installed));
    }

    private void restoreDownloadState() {
        if (matchedApp == null) return;
        DownloadTracker.Entry entry = DownloadTracker.get().getEntry(matchedApp.getPackageName());
        if (entry == null) return;
        ensureListener();
        DownloadTracker.get().attach(matchedApp.getPackageName(), downloadListener);
        applyEntryToButton(entry);
        if (entry.state == DownloadTracker.State.COMPLETED && !entry.installPrompted
                && entry.apkFile != null && entry.apkFile.exists() && entry.apkFile.length() > 0
                && InstallTracker.get().getEntry(matchedApp.getPackageName()) == null) {
            DownloadTracker.get().markInstallPrompted(entry.key);
            handleDownloadedApk(entry.apkFile);
        }
    }

    private void restoreInstallState() {
        if (matchedApp == null) return;
        InstallTracker.Entry entry = InstallTracker.get().getEntry(matchedApp.getPackageName());
        if (entry == null) return;
        ensureInstallListener();
        InstallTracker.get().attach(matchedApp.getPackageName(), installListener);
        applyInstallState(entry);

        if (entry.state == InstallTracker.State.AWAITING_CONFIRM) {
            final String pkg = matchedApp.getPackageName();
            final android.content.Context context = getApplicationContext();
            executorService.execute(() -> {
                InstallTracker.get().reconcileNow(context);
                InstallTracker.Entry reconciled = InstallTracker.get().getEntry(pkg);
                if (reconciled != null) {
                    runOnUiThread(() -> {
                        if (!isFinishing() && matchedApp != null
                                && pkg.equals(matchedApp.getPackageName())) {
                            applyInstallState(reconciled);
                        }
                    });
                }
            });
        }
    }

    private void ensureListener() {
        if (downloadListener != null) return;
        downloadListener = new DownloadTracker.Listener() {
            @Override public void onProgress(DownloadTracker.Entry entry) {
                runOnUiThread(() -> applyEntryToButton(entry));
            }
            @Override public void onCompleted(DownloadTracker.Entry entry) {
                runOnUiThread(() -> {
                    applyEntryToButton(entry);
                    if (!entry.installPrompted && entry.apkFile != null
                            && entry.apkFile.exists() && entry.apkFile.length() > 0) {
                        DownloadTracker.get().markInstallPrompted(entry.key);
                        handleDownloadedApk(entry.apkFile);
                    }
                });
            }
            @Override public void onFailed(DownloadTracker.Entry entry) {
                runOnUiThread(() -> {
                    updateButton.setEnabled(true);
                    updateButton.setText(matchedApp.isNotInstalled() ? getString(R.string.install) : getString(R.string.btn_update));
                    updateButton.hideProgress();
                    updateActionVisibility();
                    Toast.makeText(AppDetailActivity.this, R.string.download_failed, Toast.LENGTH_LONG).show();
                });
            }
            @Override public void onCancelled(DownloadTracker.Entry entry) {
                runOnUiThread(() -> {
                    updateButton.setEnabled(true);
                    updateButton.setText(matchedApp.isNotInstalled() ? getString(R.string.install) : getString(R.string.btn_update));
                    updateButton.hideProgress();
                    updateActionVisibility();
                    Toast.makeText(AppDetailActivity.this, R.string.download_cancelled, Toast.LENGTH_SHORT).show();
                });
            }
        };
    }

    private void applyEntryToButton(DownloadTracker.Entry entry) {
        if (entry == null) return;
        switch (entry.state) {
            case DOWNLOADING:
                updateButton.showProgress(entry.progress, DownloadActionButton.progressLabel(this, entry.progress));
                break;
            case COMPLETED:
                boolean canInstall = entry.apkFile != null && entry.apkFile.exists() && entry.apkFile.length() > 0;
                updateButton.hideProgress();
                updateButton.setEnabled(canInstall);
                updateButton.setText(getString(R.string.install_update));
                updateButton.setOnClickListener(v -> {
                    if (entry.apkFile != null && entry.apkFile.exists() && entry.apkFile.length() > 0) {
                        DownloadTracker.get().markInstallPrompted(entry.key);
                        handleDownloadedApk(entry.apkFile);
                    } else {
                        startUpdate(false);
                    }
                });
                break;
            case INSTALLED:
                updateButton.setEnabled(false);
                updateButton.setText(getString(R.string.installed));
                updateButton.setOnClickListener(v -> startUpdate(false));
                updateButton.hideProgress();
                break;
            case FAILED:
                updateButton.setEnabled(true);
                updateButton.setText(matchedApp.isNotInstalled() ? getString(R.string.install) : getString(R.string.btn_update));
                updateButton.setOnClickListener(v -> startUpdate(false));
                updateButton.hideProgress();
                break;
            default:
                break;
        }
        updateActionVisibility();
    }

    private String sourceName(String source) {
        if ("fdroid".equals(source)) {
            if (matchedApp.isLumenSource()) return "Lumen Store";
            return matchedApp.isLazyIndexSource() ? "IzzyOnDroid" : "F-Droid";
        }
        if ("play".equals(source)) return "Google Play";
        if ("github".equals(source)) return "manual".equals(matchedApp.getOrigin()) ? getString(R.string.github_manual) : "GitHub";
        if ("gitlab".equalsIgnoreCase(source)) return "GitLab";
        if ("codeberg".equalsIgnoreCase(source)) return "Codeberg";
        if ("gitea".equalsIgnoreCase(source)) return "Gitea / Forgejo";
        return getString(R.string.manual_repository);
    }

    private Drawable loadIcon(String packageName) {
        try { return getPackageManager().getApplicationIcon(packageName); }
        catch (Exception e) { return null; }
    }

    private void setupInstalledActions() {
        View actions=findViewById(R.id.installedActions);
        MaterialButton open=findViewById(R.id.openInstalledButton);
        MaterialButton uninstall=findViewById(R.id.uninstallInstalledButton);
        boolean installed=!matchedApp.isNotInstalled();
        actions.setVisibility(installed?View.VISIBLE:View.GONE);
        open.setOnClickListener(v->runInstalledApp());
        uninstall.setOnClickListener(v->UpdateManager.uninstall(this,matchedApp.getPackageName(),UNINSTALL_REQUEST));
    }

    private void handleDownloadedApk(java.io.File apk) {
        if (!matchedApp.isNotInstalled() && !UpdateManager.signaturesMatch(this, apk, matchedApp.getPackageName())) {
            showIncompatibleVersionDialog(apk);
            return;
        }
        startTrackedInstall(apk);
    }

    private void startTrackedInstall(java.io.File apk) {
        ensureInstallListener();
        UpdateManager.installApk(this, matchedApp.getPackageName(), apk, installListener);
    }

    private void showIncompatibleVersionDialog(java.io.File apk) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.incompatible_version_title))
                .setMessage(getString(R.string.incompatible_version_message))
                .setCancelable(false)
                .setNegativeButton(getString(R.string.cancel), (d, w) ->
                        applyEntryToButton(DownloadTracker.get().getEntry(matchedApp.getPackageName())))
                .setPositiveButton(getString(R.string.reinstall_confirm), (d, w) -> {
                    pendingReinstallApk = apk;
                    UpdateManager.uninstall(this, matchedApp.getPackageName(), UNINSTALL_REQUEST);
                })
                .show();
    }

    private void ensureInstallListener() {
        if (installListener != null) return;
        installListener = entry -> runOnUiThread(() -> applyInstallState(entry));
    }

    private void applyInstallState(InstallTracker.Entry entry) {
        if (entry == null || matchedApp == null) return;
        switch (entry.state) {
            case INSTALLING:
                updateButton.showIndeterminate(getString(R.string.installing));
                updateButton.setEnabled(false);
                break;
            case AWAITING_CONFIRM:
                updateButton.showIndeterminate(getString(R.string.waiting_install_confirmation));
                updateButton.setEnabled(false);
                break;
            case INSTALLED:
                updateButton.hideProgress();
                DownloadTracker.get().markInstalled(matchedApp.getPackageName());
                InstallTracker.get().clear(matchedApp.getPackageName());
                checkInstallSucceeded();
                break;
            case CANCELLED:
                updateButton.hideProgress();
                InstallTracker.get().clear(matchedApp.getPackageName());
                updateButton.setEnabled(true);
                updateButton.setText(matchedApp.isNotInstalled() ? getString(R.string.install) : getString(R.string.btn_update));
                updateButton.setOnClickListener(v -> {
                    DownloadTracker.Entry d = DownloadTracker.get().getEntry(matchedApp.getPackageName());
                    if (d != null && d.apkFile != null && d.apkFile.exists() && d.apkFile.length() > 0) {
                        handleDownloadedApk(d.apkFile);
                    } else {
                        startUpdate(false);
                    }
                });
                updateActionVisibility();
                break;
            case FAILED_INCOMPATIBLE: {
                updateButton.hideProgress();
                java.io.File apk = entry.apkFile;
                InstallTracker.get().clear(matchedApp.getPackageName());
                updateButton.setEnabled(true);
                updateActionVisibility();
                if (apk != null) showIncompatibleVersionDialog(apk);
                break;
            }
            case FAILED:
                updateButton.hideProgress();
                InstallTracker.get().clear(matchedApp.getPackageName());
                updateButton.setEnabled(true);
                updateButton.setText(matchedApp.isNotInstalled() ? getString(R.string.install) : getString(R.string.btn_update));
                updateButton.setOnClickListener(v -> startUpdate(false));
                updateActionVisibility();
                Toast.makeText(this, R.string.install_failed, Toast.LENGTH_LONG).show();
                break;
            default:
                break;
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UNINSTALL_REQUEST && pendingReinstallApk != null) {
            java.io.File f = pendingReinstallApk;
            pendingReinstallApk = null;
            new android.os.Handler(getMainLooper()).postDelayed(() -> {
                if (f.exists()) startTrackedInstall(f);
            }, 700);
        }
    }

    private void startUpdate(boolean forceLatest) {
        if (matchedApp.getDownloadUrl() == null) return;
        if (matchedApp.isCloneVariant() && matchedApp.isNotInstalled()) MatchStore.upsert(this, matchedApp);
        if (!forceLatest && !matchedApp.isUpdateAvailable() && !matchedApp.isNotInstalled()) return;
        DownloadTracker.Entry old = DownloadTracker.get().getEntry(matchedApp.getPackageName());

        if (old != null && old.state == DownloadTracker.State.FAILED) {
            DownloadTracker.get().clear(matchedApp.getPackageName());
        }
        ensureListener();
        String fileName = matchedApp.getAppLabel() + "-" + (matchedApp.getLatestTag() == null ? "update" : matchedApp.getLatestTag()) + ".apk";
        DownloadTracker.get().start(this, matchedApp.getPackageName(), matchedApp.getDownloadUrl(), fileName, downloadListener, matchedApp.getIconUrl());
        updateActionVisibility();
    }

    private void runInstalledApp() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(matchedApp.getPackageName());
        if (launch != null) startActivity(launch);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (matchedApp != null && downloadListener != null) {
            DownloadTracker.get().detach(matchedApp.getPackageName(), downloadListener);
        }
        if (matchedApp != null && installListener != null) {
            InstallTracker.get().detach(matchedApp.getPackageName(), installListener);
        }
        executorService.shutdownNow();
    }
}
