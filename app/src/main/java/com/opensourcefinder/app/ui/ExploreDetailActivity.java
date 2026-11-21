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
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.text.TextUtils;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.LayoutInflater;
import androidx.annotation.NonNull;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.download.DownloadTracker;
import com.opensourcefinder.app.install.InstallTracker;
import com.opensourcefinder.app.model.DeviceAbi;
import com.opensourcefinder.app.model.ExploreApp;
import com.opensourcefinder.app.model.GitHubRelease;
import com.opensourcefinder.app.model.MatchedApp;
import com.opensourcefinder.app.scanner.CustomRepositoryScanner;
import com.opensourcefinder.app.scanner.FdroidIndexManager;
import com.opensourcefinder.app.scanner.MatchStore;
import com.opensourcefinder.app.network.RetrofitClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExploreDetailActivity extends BaseActivity {
    public static final String EXTRA_EXPLORE_APP = "extra_explore_app";
    public static final String EXTRA_MATCHED_APP = "extra_matched_app";
    private static final int UNINSTALL_REQUEST = 9321;
    private ExploreApp app;
    private MatchedApp matchedSource;
    private DownloadActionButton installButton;
    private TextView progressCaption;
    private MaterialButton openButton;
    private MaterialButton uninstallButton;
    private DownloadTracker.Listener listener;
    private InstallTracker.Listener installListener;
    private java.io.File pendingReinstallApk;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private MaterialToolbar toolbar;
    private boolean versionsExpanded;
    private boolean linksExpanded;
    private boolean antiFeaturesExpanded = true;
    private boolean statsAnimated;
    private boolean actionsAnimEnabled;
    private boolean actionsTransitionPending;
    private boolean antiFeaturesAnimated;

    @Override protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        MotionHelper.applyDistinctActivityTransition(this);
        setContentView(R.layout.activity_explore_detail);
        MotionHelper.animateActivityContent(this);
        ThemeManager.applyWindow(this);
        app = (ExploreApp) getIntent().getSerializableExtra(EXTRA_EXPLORE_APP);
        matchedSource = (MatchedApp) getIntent().getSerializableExtra(EXTRA_MATCHED_APP);
        if (app == null && matchedSource != null && "fdroid".equals(matchedSource.getSourceType())) {
            app = createExploreAppFromMatch(matchedSource);
        }
        if (app == null) { finish(); return; }

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle("");
        toolbar.setNavigationOnClickListener(v -> finish());
        ToolbarCircleHelper.apply(toolbar);

        int circleIcon = com.opensourcefinder.app.ui.TablerIconRes.get(this, "ic_circle");
        relatedIconOptions = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .centerCrop()
                .override(160, 160);

        ImageView icon = findViewById(R.id.exploreDetailIcon);
        TextView name = findViewById(R.id.exploreDetailName);
        TextView desc = findViewById(R.id.exploreDetailDesc);
        ChipGroup categories = findViewById(R.id.exploreDetailCategories);
        installButton = findViewById(R.id.exploreDetailInstall);
        progressCaption = findViewById(R.id.exploreDetailProgressCaption);
        openButton = findViewById(R.id.exploreDetailOpen);
        uninstallButton = findViewById(R.id.exploreDetailUninstall);
        findViewById(R.id.exploreDetailContentRoot).post(() -> actionsAnimEnabled = true);
        IconShapeHelper.apply(icon);
        DetailHero.setup(this, R.id.exploreDetailIcon);

        name.setText(app.getName());
        bindHeaderMeta();
        bindInfoBoxes();

        if (app.getIconUrl() != null) {
            IconLoader.load(icon, app.getIconUrl());
        }

        bindCategories(categories);
        bindScreenshots();
        bindDescription();
        bindAntiFeatures();
        bindDonate();
        bindLinks();
        bindVersions();
        bindGithubReadme();

        installButton.setText(app.isInstalled() ? getString(R.string.reinstall_update) : getString(R.string.download_install));
        installButton.setOnClickListener(v -> startDownload());
        installButton.setOnCancelListener(this::cancelDownload);
        openButton.setOnClickListener(v -> runInstalledApp());
        uninstallButton.setOnClickListener(v -> UpdateManager.uninstall(this, app.getPackageName(), UNINSTALL_REQUEST));
        restoreState();
        updateActionVisibility();
        if (matchedSource != null && "fdroid".equals(matchedSource.getSourceType())) {
            loadFullFdroidDetails();
        }
        loadGithubReleases();
        animateDetailEntrance();
    }

    private void animateDetailEntrance() {
        View root = findViewById(R.id.exploreDetailContentRoot);
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

    private ExploreApp createExploreAppFromMatch(MatchedApp match) {
        ExploreApp converted = new ExploreApp();
        converted.setPackageName(match.getPackageName());
        converted.setName(match.getAppLabel());
        converted.setDescription(match.getDescription());
        converted.setSummary(match.getDescription());
        converted.setLatestVersionName(match.getLatestTag());
        converted.setLatestVersionCode(match.getLatestVersionCode());
        converted.setDownloadUrl(match.getDownloadUrl());
        converted.setSourceUrl(match.getSourceUrl());
        converted.setRepoName(match.isLumenSource() ? "Lumen Store" : "F-Droid");
        converted.setGithubSlug(match.getGithubSlug());
        converted.setIconUrl(match.getIconUrl());
        com.opensourcefinder.app.install.InstallState.refresh(converted, com.opensourcefinder.app.install.InstallState.read(this, match.getPackageName()));
        return converted;
    }

    private long readInstalledVersionCode(String packageName) {
        if (TextUtils.isEmpty(packageName)) return 0;
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(packageName, 0);
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : info.versionCode;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void loadFullFdroidDetails() {
        final String packageName = app.getPackageName();
        executorService.execute(() -> {
            try {
                List<ExploreApp> catalog = new FdroidIndexManager(this)
                        .loadExploreCatalog(Collections.singleton(packageName), null);
                ExploreApp full = null;
                for (ExploreApp candidate : catalog) {
                    if (candidate != null && packageName.equals(candidate.getPackageName())) {
                        full = candidate;
                        break;
                    }
                }
                if (full == null) return;
                final ExploreApp loaded = full;
                com.opensourcefinder.app.install.InstallState.refresh(loaded, com.opensourcefinder.app.install.InstallState.read(this, packageName));
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (loaded.isGithubRelease() && app != null && !TextUtils.isEmpty(app.getDownloadUrl())
                            && TextUtils.isEmpty(loaded.getDownloadUrl())) {
                        loaded.setLatestVersionName(app.getLatestVersionName());
                        loaded.setDownloadUrl(app.getDownloadUrl());
                        loaded.setApkSize(app.getApkSize());
                        loaded.setPreviousVersions(app.getPreviousVersions());
                    }
                    app = loaded;
                    if (app.getIconUrl() != null) {
                        ImageView iconView = findViewById(R.id.exploreDetailIcon);
                        if (iconView != null) {
                            IconLoader.load(iconView, app.getIconUrl());
                        }
                    }
                    bindHeaderMeta();
                    bindInfoBoxes();
                    bindCategories(findViewById(R.id.exploreDetailCategories));
                    bindScreenshots();
                    bindDescription();
                    bindAntiFeatures();
                    bindDonate();
                    bindLinks();
                    bindVersions();
                    bindGithubReadme();
                    installButton.setText(app.isInstalled()
                            ? getString(R.string.reinstall_update)
                            : getString(R.string.download_install));
                    updateActionVisibility();
                    loadGithubReleases();
                });
            } catch (Exception ignored) {
            }
        });
    }

    private String githubLoadedSlug;

    private void loadGithubReleases() {
        if (app == null || !app.isGithubRelease()) return;
        final String slug = app.getGithubSlug();
        if (slug.equals(githubLoadedSlug)) return;
        final int cut = slug.indexOf('/');
        if (cut <= 0 || cut >= slug.length() - 1) return;
        githubLoadedSlug = slug;
        final String owner = slug.substring(0, cut);
        final String repo = slug.substring(cut + 1);
        final String packageName = app.getPackageName();
        executorService.execute(() -> {
            CustomRepositoryScanner scanner = new CustomRepositoryScanner(this);
            List<GitHubRelease> releases = scanner.listReleases(owner, repo);
            List<ExploreApp.VersionInfo> versions = new ArrayList<>();
            for (GitHubRelease release : releases) {
                GitHubRelease.Asset asset = release.findApkAsset(false);
                if (asset == null || asset.getBrowserDownloadUrl() == null) continue;
                versions.add(new ExploreApp.VersionInfo(release.getTagName(), 0L, asset.getSize(),
                        asset.getBrowserDownloadUrl(), DeviceAbi.fromFileName(asset.getName()), null));
            }
            GitHubRelease latest = GitHubRelease.firstStableInstallable(releases);
            GitHubRelease.Asset latestAsset = latest == null ? null : latest.findApkAsset(false);
            String latestTag = null, latestUrl = null, latestArch = null;
            long latestSize = 0L, publishedAt = 0L;
            if (latest != null && latestAsset != null) {
                latestTag = latest.getTagName();
                latestUrl = latestAsset.getBrowserDownloadUrl();
                latestSize = latestAsset.getSize();
                latestArch = DeviceAbi.fromFileName(latestAsset.getName());
                publishedAt = parseIsoTime(latest.getPublishedAt());
            } else {
                CustomRepositoryScanner.ResolvedRelease direct = scanner.resolveLatestRelease(owner, repo);
                if (direct != null) {
                    latestTag = direct.tag;
                    latestUrl = direct.downloadUrl;
                    latestArch = DeviceAbi.fromFileName(direct.assetName);
                    if (versions.isEmpty()) {
                        versions.add(new ExploreApp.VersionInfo(direct.tag, 0L, 0L, direct.downloadUrl, latestArch, null));
                    }
                }
            }
            if (latestTag == null || latestUrl == null) {
                runOnUiThread(() -> { if (slug.equals(githubLoadedSlug)) githubLoadedSlug = null; });
                return;
            }
            final String fTag = latestTag, fUrl = latestUrl, fArch = latestArch;
            final long fSize = latestSize, fPublished = publishedAt;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || app == null
                        || !packageName.equals(app.getPackageName())) return;
                app.setLatestVersionName(fTag);
                app.setDownloadUrl(fUrl);
                if (fSize > 0) app.setApkSize(fSize);
                if (!TextUtils.isEmpty(fArch)) app.setArchitecture(fArch);
                if (fPublished > 0) app.setLastUpdated(fPublished);
                app.setPreviousVersions(versions);
                com.opensourcefinder.app.install.InstallState.refresh(app,
                        com.opensourcefinder.app.install.InstallState.read(this, packageName));
                bindHeaderMeta();
                bindInfoBoxes();
                bindVersions();
                syncInstalledButton();
                updateActionVisibility();
                persistResolvedLatestToHome(packageName, fTag, fUrl);
            });
        });
    }

    private void persistResolvedLatestToHome(final String pkg, final String tag, final String url) {
        if (pkg == null || tag == null || url == null) return;
        final android.content.Context context = getApplicationContext();
        new Thread(() -> {
            try {
                for (MatchedApp saved : MatchStore.get(context)) {
                    if (saved == null || !pkg.equals(saved.getPackageName()) || !saved.isGithubRelease()) continue;
                    MatchedApp copy = saved.copy();
                    copy.setLatestTag(tag);
                    copy.setDownloadUrl(url);
                    com.opensourcefinder.app.install.InstallState.refresh(context, copy);
                    MatchStore.updateIfPresent(context, copy);
                    break;
                }
            } catch (Exception ignored) {}
        }, "home-latest-sync").start();
    }

    private static long parseIsoTime(String value) {
        if (value == null || value.isEmpty()) return 0L;
        try {
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date date = format.parse(value);
            return date == null ? 0L : date.getTime();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void bindGithubReadme() {
        View card = findViewById(R.id.exploreDetailReadmeCard);
        com.google.android.material.loadingindicator.LoadingIndicator bar = findViewById(R.id.exploreDetailReadmeProgress);
        RecyclerView readme = findViewById(R.id.exploreDetailReadme);
        String url = app.getSourceUrl();
        boolean hasAbout = !TextUtils.isEmpty(app.getDescription()) || !TextUtils.isEmpty(app.getSummary());
        if (hasAbout || url == null || !url.toLowerCase(Locale.ROOT).contains("github.com/")) {
            if (card != null) card.setVisibility(View.GONE);
            return;
        }
        String[] parts = parseGithub(url);
        if (parts == null) { if (card != null) card.setVisibility(View.GONE); return; }
        if (card != null) card.setVisibility(View.VISIBLE);
        if (bar != null) bar.setVisibility(View.VISIBLE);
        RetrofitClient.getService().getReadmeRaw(parts[0], parts[1], "application/vnd.github.raw+json")
                .enqueue(new retrofit2.Callback<String>() {
                    @Override public void onResponse(retrofit2.Call<String> call, retrofit2.Response<String> response) {
                        if (bar != null) bar.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null && !response.body().trim().isEmpty()) {
                            MarkdownRenderer.renderSegmented(readme, response.body(), parts[0], parts[1]);
                        } else {
                            MarkdownRenderer.renderSegmented(readme, getString(R.string.readme_unavailable));
                        }
                    }
                    @Override public void onFailure(retrofit2.Call<String> call, Throwable t) {
                        if (bar != null) bar.setVisibility(View.GONE);
                        MarkdownRenderer.renderSegmented(readme, getString(R.string.readme_unavailable));
                    }
                });
    }

    private String[] parseGithub(String url) {
        try {
            String clean = url.trim().replaceFirst("^https?://", "");
            if (!clean.toLowerCase(Locale.ROOT).startsWith("github.com/")) return null;
            String[] p = clean.substring("github.com/".length()).split("/");
            if (p.length < 2) return null;
            String owner = p[0]; String repo = p[1].replaceAll("[?#].*$", "").replaceAll("\\.git$", "");
            if (owner.isEmpty() || repo.isEmpty()) return null;
            return new String[]{owner, repo};
        } catch (Exception e) { return null; }
    }

    private void runInstalledApp() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        if (launch != null) startActivity(launch);
    }

    private void cancelDownload() {
        if (app == null) return;
        DownloadTracker.Entry e = DownloadTracker.get().getEntry(app.getPackageName());
        boolean owned = e != null && e.homePlaceholderOwned;
        DownloadTracker.get().cancel(app.getPackageName());
        if (owned) MatchStore.removeIfNeverInstalled(this, app.getPackageName());
    }

    private void removeGhostEntryIfNeverInstalled() {
        if (app == null) return;
        DownloadTracker.Entry e = DownloadTracker.get().getEntry(app.getPackageName());
        if (e != null && e.homePlaceholderOwned) {
            MatchStore.removeIfNeverInstalled(this, app.getPackageName());
        }
    }

    private void setVis(View v, int visibility) {
        if (v == null || v.getVisibility() == visibility) return;
        beginActionsTransition();
        v.setVisibility(visibility);
    }

    private void beginActionsTransition() {
        if (!actionsAnimEnabled || actionsTransitionPending) return;
        long d = MotionHelper.getDuration(this, 340, 520);
        if (d <= 0) return;
        ViewGroup root = findViewById(R.id.exploreDetailContentRoot);
        if (root == null || !root.isLaidOut()) return;
        actionsTransitionPending = true;
        root.post(() -> actionsTransitionPending = false);

        android.transition.TransitionSet set = new android.transition.TransitionSet();
        set.setOrdering(android.transition.TransitionSet.ORDERING_TOGETHER);

        android.transition.ChangeBounds bounds = new android.transition.ChangeBounds();
        bounds.setDuration(d);
        bounds.setInterpolator(new android.view.animation.DecelerateInterpolator(1.7f));
        set.addTransition(bounds);

        android.transition.Fade captionFade = new android.transition.Fade();
        captionFade.addTarget(progressCaption);
        captionFade.setDuration(d);
        captionFade.setStartDelay(120);
        set.addTransition(captionFade);

        android.transition.Fade fade = new android.transition.Fade();
        fade.addTarget(openButton);
        fade.addTarget(uninstallButton);
        fade.addTarget(installButton);
        fade.setDuration(Math.round(d * 0.8f));
        fade.setStartDelay(60);
        set.addTransition(fade);

        android.transition.TransitionManager.beginDelayedTransition(root, set);
    }

    private void popInstallButton() {
        long d = MotionHelper.getDuration(this, 320, 460);
        if (d <= 0 || installButton == null) return;
        installButton.animate().cancel();
        android.animation.ObjectAnimator pop = android.animation.ObjectAnimator.ofPropertyValuesHolder(
                installButton,
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.95f, 1f),
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.9f, 1f));
        pop.setDuration(d);
        pop.setInterpolator(MotionHelper.getInterpolator(this));
        pop.start();
    }

    private void updateActionVisibility() {
        if (app == null) return;
        DownloadTracker.Entry e = DownloadTracker.get().getEntry(app.getPackageName());
        boolean downloading = e != null && e.state == DownloadTracker.State.DOWNLOADING;
        boolean awaitingInstall = e != null && e.state == DownloadTracker.State.COMPLETED;
        boolean installed = app.isInstalled();
        boolean updateAvailable = installed && com.opensourcefinder.app.install.InstallState.ui(app) == com.opensourcefinder.app.install.InstallState.Ui.UPDATE;

        setVis(progressCaption, View.GONE);

        boolean showPrimary = !installed || updateAvailable || downloading || awaitingInstall;
        setVis(installButton, showPrimary ? View.VISIBLE : View.GONE);

        boolean showInstalledActions = installed && !updateAvailable && !downloading && !awaitingInstall;
        setVis(openButton, showInstalledActions ? View.VISIBLE : View.GONE);
        setVis(uninstallButton, showInstalledActions ? View.VISIBLE : View.GONE);
    }

    private void bindInfoBoxes() {
        View row = findViewById(R.id.exploreDetailInfoRow);
        View updatedBox = findViewById(R.id.exploreDetailUpdatedBox);
        TextView updated = findViewById(R.id.exploreDetailUpdated);
        TextView updatedCaption = findViewById(R.id.exploreDetailUpdatedCaption);
        String updatedValue = null;
        int updatedCaptionRes = R.string.stat_updated;
        if (app.getLastUpdated() > 0) {
            updatedValue = formatRelativeTime(app.getLastUpdated());
        } else if (app.getLatestVersionName() != null) {
            updatedValue = app.getLatestVersionName();
            updatedCaptionRes = R.string.stat_version;
        }
        if (updatedValue != null) {
            updated.setText(updatedValue);
            updatedCaption.setText(updatedCaptionRes);
            updatedBox.setVisibility(View.VISIBLE);
        } else {
            updatedBox.setVisibility(View.GONE);
        }

        View sizeBox = findViewById(R.id.exploreDetailSizeBox);
        TextView size = findViewById(R.id.exploreDetailSize);
        TextView sizeCaption = findViewById(R.id.exploreDetailSizeCaption);
        if (app.getApkSize() > 0) {
            size.setText(formatSize(app.getApkSize()));
            sizeCaption.setText(R.string.stat_size);
            sizeBox.setVisibility(View.VISIBLE);
        } else {
            sizeBox.setVisibility(View.GONE);
        }

        View repoBox = findViewById(R.id.exploreDetailRepoBox);
        TextView repo = findViewById(R.id.exploreDetailRepo);
        TextView repoCaption = findViewById(R.id.exploreDetailRepoCaption);
        String repoText = app.getRepoName();
        String repoCaptionText = getString(R.string.stat_source);
        if (app.isInstalled()) {
            if (repoText == null) {
                repoText = getString(R.string.installed_label);
            } else {
                repoCaptionText = repoCaptionText + " · " + getString(R.string.installed_label);
            }
        }
        if (repoText != null) {
            repo.setText(repoText);
            repoCaption.setText(repoCaptionText);
            repoBox.setVisibility(View.VISIBLE);
        } else {
            repoBox.setVisibility(View.GONE);
        }

        boolean u = updatedBox.getVisibility() == View.VISIBLE;
        boolean z = sizeBox.getVisibility() == View.VISIBLE;
        boolean r = repoBox.getVisibility() == View.VISIBLE;
        findViewById(R.id.exploreDetailDivider1).setVisibility(u && (z || r) ? View.VISIBLE : View.GONE);
        findViewById(R.id.exploreDetailDivider2).setVisibility(z && r ? View.VISIBLE : View.GONE);
        boolean any = u || z || r;
        row.setVisibility(any ? View.VISIBLE : View.GONE);
        if (any && !statsAnimated) {
            statsAnimated = true;
            animateStatsIn(new View[]{updatedBox, sizeBox, repoBox});
        }
    }

    private void animateStatsIn(View[] boxes) {
        long duration = MotionHelper.getDuration(this, 320, 480);
        if (duration <= 0) return;
        float shift = 14f * getResources().getDisplayMetrics().density;
        int i = 0;
        for (View box : boxes) {
            if (box.getVisibility() != View.VISIBLE) continue;
            box.setAlpha(0f);
            box.setTranslationY(shift);
            box.animate().alpha(1f).translationY(0f)
                    .setStartDelay(90L * i++)
                    .setDuration(duration)
                    .setInterpolator(MotionHelper.getInterpolator(this))
                    .start();
        }
    }

    private void bindHeaderMeta() {
        TextView meta = findViewById(R.id.exploreDetailMeta);
        if (meta == null) return;

        String author = app != null ? app.getAuthorName() : null;
        if (TextUtils.isEmpty(author)) {
            meta.setText(null);
            meta.setVisibility(View.GONE);
            return;
        }

        meta.setText(author.trim());
        meta.setTextColor(MaterialColors.getColor(meta, com.google.android.material.R.attr.colorOnSurfaceVariant));
        meta.setVisibility(View.VISIBLE);
    }

    private void bindDescription() {
        View section = findViewById(R.id.exploreDetailDescriptionSection);
        TextView description = findViewById(R.id.exploreDetailDesc);
        TextView readMore = findViewById(R.id.exploreDetailDescReadMore);
        if (section == null || description == null) return;
        String value = !TextUtils.isEmpty(app.getDescription()) ? app.getDescription() : app.getSummary();
        if (TextUtils.isEmpty(value)) {
            section.setVisibility(View.GONE);
            return;
        }
        section.setVisibility(View.VISIBLE);
        MarkdownRenderer.render(description, value.trim());
        ReadMoreHelper.setup(description, readMore);
    }

    private String formatRelativeTime(long millis) {
        long diff = System.currentTimeMillis() - millis;
        if (diff < 0) diff = 0;
        long hours = diff / (1000L * 60 * 60);
        if (hours < 1) return getString(R.string.minutes_ago);
        if (hours < 24) return getString(R.string.hours_ago, hours);
        long days = hours / 24;
        if (days < 30) return getString(R.string.days_ago, days);
        long months = days / 30;
        if (months < 12) return getString(R.string.months_ago, months);
        return getString(R.string.years_ago, months / 12);
    }

    private String formatSize(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        return getString(R.string.megabytes, mb);
    }

    private void bindCategories(ChipGroup group) {
        group.removeAllViews();
        View holder = (View) group.getParent();
        List<String> cats = app.getCategories();
        if (cats == null || cats.isEmpty()) {
            group.setVisibility(View.GONE);
            if (holder != null) holder.setVisibility(View.GONE);
            return;
        }
        group.setVisibility(View.VISIBLE);
        if (holder != null) holder.setVisibility(View.VISIBLE);
        for (String c : cats) {
            Chip chip = new Chip(this);
            chip.setText(c);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setClickable(false);
            chip.setCheckable(false);
            group.addView(chip);
        }
    }

    private void bindScreenshots() {
        View scroll = findViewById(R.id.exploreDetailScreenshotsScroll);
        LinearLayout container = findViewById(R.id.exploreDetailScreenshots);
        if (scroll == null || container == null) return;
        container.removeAllViews();
        List<String> shots = app.getScreenshotUrls();
        if (shots == null || shots.isEmpty()) { scroll.setVisibility(View.GONE); return; }
        scroll.setVisibility(View.VISIBLE);
        float density = getResources().getDisplayMetrics().density;
        int heightPx = (int) (210 * density);
        int widthPx = (int) (124 * density);
        int marginPx = (int) (8 * density);
        int radiusPx = (int) (12 * density);
        RequestOptions options = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .override(widthPx, heightPx)
                .transform(new RoundedCorners(radiusPx));
        int max = Math.min(shots.size(), 8);
        ArrayList<String> previewUrls = new ArrayList<>(shots.subList(0, max));
        int tonal = MaterialColors.getColor(container,
                com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFF444444);
        int onTonal = MaterialColors.getColor(container,
                com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFBBBBBB);
        for (int i = 0; i < max; i++) {
            final int index = i;

            FrameLayout frame = new FrameLayout(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(widthPx, heightPx);
            lp.setMarginEnd(marginPx);
            frame.setLayoutParams(lp);
            frame.setClickable(true);
            frame.setFocusable(true);
            frame.setContentDescription(getString(R.string.screenshot_preview));
            frame.setOnClickListener(v -> openScreenshotPreview(previewUrls, index));

            ShimmerView shimmer = new ShimmerView(this);
            shimmer.setCornerRadius(radiusPx);
            frame.addView(shimmer, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            ImageView iv = new ImageView(this);
            iv.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setContentDescription(null);
            frame.addView(iv);

            Glide.with(this).load(shots.get(i)).apply(options)
                    .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(220))
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override public boolean onLoadFailed(
                                com.bumptech.glide.load.engine.GlideException e, Object model,
                                com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                boolean isFirstResource) {

                            frame.removeView(shimmer);
                            android.graphics.drawable.GradientDrawable box = new android.graphics.drawable.GradientDrawable();
                            box.setColor(tonal);
                            box.setCornerRadius(radiusPx);
                            frame.setBackground(box);
                            iv.setScaleType(ImageView.ScaleType.CENTER);
                            iv.setImageResource(R.drawable.ic_alert_triangle);
                            ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(onTonal));
                            return true;
                        }
                        @Override public boolean onResourceReady(
                                android.graphics.drawable.Drawable resource, Object model,
                                com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            shimmer.animate().alpha(0f).setDuration(240)
                                    .withEndAction(() -> frame.removeView(shimmer)).start();
                            return false;
                        }
                    })
                    .into(iv);
            container.addView(frame);
        }
    }

    private void openScreenshotPreview(ArrayList<String> urls, int startIndex) {
        Intent intent = new Intent(this, ScreenshotPreviewActivity.class);
        intent.putStringArrayListExtra(ScreenshotPreviewActivity.EXTRA_URLS, urls);
        intent.putExtra(ScreenshotPreviewActivity.EXTRA_START_INDEX, startIndex);
        startActivity(intent);
    }

    private void downloadOlderVersion(ExploreApp.VersionInfo version) {
        if (version.getDownloadUrl() == null) {
            Toast.makeText(this, R.string.download_link_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        String key = app.getPackageName() + "@"
                + (version.getVersionCode() > 0 ? String.valueOf(version.getVersionCode()) : version.getVersionName());
        String fileName = (app.getName() != null ? app.getName() : app.getPackageName())
                + "-" + (version.getVersionName() != null ? version.getVersionName() : version.getVersionCode()) + ".apk";
        DownloadTracker.get().start(this, key, version.getDownloadUrl(), fileName, null, app.getIconUrl());
        Toast.makeText(this, R.string.older_version_download_started, Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, DownloadsActivity.class));
    }

    private void bindAntiFeatures() {
        View card = findViewById(R.id.exploreDetailAntiFeaturesCard);
        LinearLayout list = findViewById(R.id.exploreDetailAntiFeaturesList);
        View header = findViewById(R.id.exploreDetailAntiFeaturesHeader);
        View body = findViewById(R.id.exploreDetailAntiFeaturesBody);
        ImageView arrow = findViewById(R.id.exploreDetailAntiFeaturesArrow);
        ImageView icon = findViewById(R.id.exploreDetailAntiFeaturesIcon);
        TextView count = findViewById(R.id.exploreDetailAntiFeaturesCount);
        if (card == null || list == null || body == null) return;
        List<String> features = app.getAntiFeatures();
        if (features == null || features.isEmpty()) { card.setVisibility(View.GONE); return; }
        list.removeAllViews();
        int n = 0;
        for (String feature : features) {
            if (TextUtils.isEmpty(feature)) continue;
            addAntiFeatureRow(list, feature);
            n++;
        }
        if (n == 0) { card.setVisibility(View.GONE); return; }
        if (count != null) count.setText(String.valueOf(n));
        card.setVisibility(View.VISIBLE);

        if (header != null) header.setBackground(null);
        card.setClickable(false);
        card.setFocusable(false);
        ViewGroup animationRoot = findViewById(R.id.exploreDetailContentRoot);
        AccordionHelper.bind(header, animationRoot, body, arrow, new AccordionHelper.ExpandedState() {
            @Override public boolean isExpanded() { return antiFeaturesExpanded; }
            @Override public void setExpanded(boolean expanded) { antiFeaturesExpanded = expanded; }
        });
        if (!antiFeaturesAnimated) {
            antiFeaturesAnimated = true;
            animateAntiFeaturesIn(card, icon);
        }
    }

    private String antiFeatureIcon(String feature) {
        String key = feature.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", "");
        if (key.contains("nonfreenet")) return "ic_cloud";
        if (key.contains("nonfreeadd")) return "ic_af_puzzle";
        if (key.contains("nonfreedep")) return "ic_lm_link";
        if (key.contains("nonfreeasset")) return "ic_af_photo";
        if (key.contains("upstreamnonfree")) return "ic_lm_license";
        if (key.contains("nosource")) return "ic_lm_code";
        if (key.contains("knownvuln")) return "ic_af_shield";
        if (key.contains("disabledalgorithm")) return "ic_af_lock";
        if (key.contains("debuggable")) return "ic_lm_bug";
        if (key.contains("tracking")) return "ic_af_eye";
        if (key.contains("nsfw")) return "ic_af_eye_off";
        if (key.contains("ads")) return "ic_af_megaphone";
        return "ic_alert_triangle";
    }

    private int[] antiFeatureDescription(String feature) {
        String key = feature.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", "");
        if (key.contains("nonfreenet")) return new int[]{R.string.af_nonfreenet_en, R.string.af_nonfreenet_fa};
        if (key.contains("nonfreeadd")) return new int[]{R.string.af_nonfreeadd_en, R.string.af_nonfreeadd_fa};
        if (key.contains("nonfreedep")) return new int[]{R.string.af_nonfreedep_en, R.string.af_nonfreedep_fa};
        if (key.contains("nonfreeasset")) return new int[]{R.string.af_nonfreeasset_en, R.string.af_nonfreeasset_fa};
        if (key.contains("upstreamnonfree")) return new int[]{R.string.af_upstreamnonfree_en, R.string.af_upstreamnonfree_fa};
        if (key.contains("nosource")) return new int[]{R.string.af_nosource_en, R.string.af_nosource_fa};
        if (key.contains("knownvuln")) return new int[]{R.string.af_knownvuln_en, R.string.af_knownvuln_fa};
        if (key.contains("disabledalgorithm")) return new int[]{R.string.af_disabledalgorithm_en, R.string.af_disabledalgorithm_fa};
        if (key.contains("debuggable")) return new int[]{R.string.af_debuggable_en, R.string.af_debuggable_fa};
        if (key.contains("tracking")) return new int[]{R.string.af_tracking_en, R.string.af_tracking_fa};
        if (key.contains("nsfw")) return new int[]{R.string.af_nsfw_en, R.string.af_nsfw_fa};
        if (key.contains("ads")) return new int[]{R.string.af_ads_en, R.string.af_ads_fa};
        return new int[]{R.string.af_generic_en, R.string.af_generic_fa};
    }

    private void addAntiFeatureRow(LinearLayout container, String feature) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(0, dp(6), 0, dp(6));

        FrameLayout iconFrame = new FrameLayout(this);
        iconFrame.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(34)));
        android.graphics.drawable.GradientDrawable iconBg = new android.graphics.drawable.GradientDrawable();
        iconBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        iconBg.setColor(MaterialColors.getColor(row, com.google.android.material.R.attr.colorErrorContainer, 0));
        iconFrame.setBackground(iconBg);
        ImageView icon = new ImageView(this);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(18), dp(18));
        iconParams.gravity = Gravity.CENTER;
        icon.setLayoutParams(iconParams);
        icon.setImageResource(TablerIconRes.get(this, antiFeatureIcon(feature)));
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(
                MaterialColors.getColor(row, com.google.android.material.R.attr.colorOnErrorContainer, 0)));
        iconFrame.addView(icon);
        row.addView(iconFrame);

        TextView label = new TextView(this);
        label.setText(feature);
        label.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        label.setTextColor(MaterialColors.getColor(row, com.google.android.material.R.attr.colorOnSurface, 0));
        int[] descriptions = antiFeatureDescription(feature);
        int muted = MaterialColors.getColor(row, com.google.android.material.R.attr.colorOnSurfaceVariant, 0);
        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        columnParams.setMarginStart(dp(12));
        textColumn.setLayoutParams(columnParams);
        textColumn.addView(label);
        for (int res : descriptions) {
            TextView description = new TextView(this);
            description.setText(res);
            description.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            description.setTextColor(muted);
            LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            descriptionParams.topMargin = dp(2);
            description.setLayoutParams(descriptionParams);
            textColumn.addView(description);
        }
        row.addView(textColumn);
        container.addView(row);
    }

    private void animateAntiFeaturesIn(View card, View icon) {
        long duration = MotionHelper.getDuration(this, 380, 560);
        if (duration <= 0) return;
        card.setAlpha(0f);
        card.setTranslationY(18f * getResources().getDisplayMetrics().density);
        card.animate().alpha(1f).translationY(0f)
                .setStartDelay(260)
                .setDuration(duration)
                .setInterpolator(MotionHelper.getInterpolator(this))
                .withEndAction(() -> {
                    if (icon == null) return;
                    android.animation.ObjectAnimator wiggle = android.animation.ObjectAnimator.ofFloat(
                            icon, View.ROTATION, 0f, -14f, 12f, -8f, 5f, 0f);
                    wiggle.setDuration(duration + 200);
                    wiggle.start();
                })
                .start();
    }

    private void bindDonate() {
        View section = findViewById(R.id.exploreDetailDonateSection);
        TextView link = findViewById(R.id.exploreDetailDonateLink);
        if (section == null || link == null) return;
        if (app.getDonateUrl() == null || app.getDonateUrl().trim().isEmpty()) {
            section.setVisibility(View.GONE);
            return;
        }
        section.setVisibility(View.VISIBLE);
        link.setText(app.getDonateUrl());
        section.setOnClickListener(v -> openUrl(app.getDonateUrl()));
        section.setContentDescription(getString(R.string.donate_section_title) + ": " + app.getDonateUrl());
    }

    private void bindLinks() {
        LinearLayout container = findViewById(R.id.exploreDetailLinks);
        View section = findViewById(R.id.exploreDetailLinksSection);
        View toggle = findViewById(R.id.exploreDetailLinksToggle);
        ImageView toggleIcon = findViewById(R.id.exploreDetailLinksIcon);
        if (container == null || section == null) return;
        container.removeAllViews();
        int count = 0;
        count += addLinkRow(container, R.string.link_website, app.getWebsiteUrl(), "ic_lm_globe");
        count += addLinkRow(container, R.string.link_issues, app.getIssueTrackerUrl(), "ic_lm_bug");
        count += addLinkRow(container, R.string.link_changelog, app.getChangelogUrl(), "ic_lm_history");
        count += addLinkRow(container, R.string.link_license, app.getLicense(), "ic_lm_license");
        count += addLinkRow(container, R.string.link_translation, app.getTranslationUrl(), "ic_language");
        count += addLinkRow(container, R.string.link_source, app.getSourceUrl(), "ic_lm_code");
        count += addLinkRow(container, R.string.link_metadata, app.getMetadataUrl(), "ic_lm_file");
        section.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        ImageView headerIcon = findViewById(R.id.exploreDetailLinksHeaderIcon);
        if (headerIcon != null) headerIcon.setImageResource(TablerIconRes.get(this, "ic_lm_link"));
        TextView countView = findViewById(R.id.exploreDetailLinksCount);
        if (countView != null) countView.setText(String.valueOf(count));
        if (toggleIcon != null) toggleIcon.setImageResource(R.drawable.ic_chevron_down);
        ViewGroup animationRoot = findViewById(R.id.exploreDetailContentRoot);
        AccordionHelper.bind(toggle, animationRoot, container, toggleIcon, new AccordionHelper.ExpandedState() {
            @Override public boolean isExpanded() { return linksExpanded; }
            @Override public void setExpanded(boolean expanded) { linksExpanded = expanded; }
        });
    }

    private int addLinkRow(LinearLayout container, int labelRes, String value, String iconName) {
        if (TextUtils.isEmpty(value)) return 0;
        boolean clickable = labelRes != R.string.link_license;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        row.setPaddingRelative(dp(6), dp(6), dp(10), dp(6));
        row.setClickable(clickable);
        row.setFocusable(clickable);
        if (clickable) {
            TypedValue selectableBackground = new TypedValue();
            if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectableBackground, true)) {
                row.setBackgroundResource(selectableBackground.resourceId);
            }
        }

        FrameLayout iconFrame = new FrameLayout(this);
        iconFrame.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
        android.graphics.drawable.GradientDrawable iconBg = new android.graphics.drawable.GradientDrawable();
        iconBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        iconBg.setColor(MaterialColors.getColor(row, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0));
        iconFrame.setBackground(iconBg);
        ImageView icon = new ImageView(this);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(18), dp(18));
        iconParams.gravity = Gravity.CENTER;
        icon.setLayoutParams(iconParams);
        icon.setImageResource(TablerIconRes.get(this, iconName));
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(
                MaterialColors.getColor(row, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)));
        iconFrame.addView(icon);
        row.addView(iconFrame);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMarginStart(dp(12));
        copy.setLayoutParams(copyParams);
        TextView label = new TextView(this);
        label.setText(getString(labelRes));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setTextColor(MaterialColors.getColor(row, com.google.android.material.R.attr.colorOnSurface, 0));
        TextView url = new TextView(this);
        url.setText(value);
        url.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        url.setSingleLine(true);
        url.setEllipsize(TextUtils.TruncateAt.END);
        url.setTextColor(MaterialColors.getColor(row, com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
        copy.addView(label);
        copy.addView(url);
        row.addView(copy);

        if (clickable) {
            ImageView trailing = new ImageView(this);
            LinearLayout.LayoutParams trailingParams = new LinearLayout.LayoutParams(dp(18), dp(18));
            trailingParams.setMarginStart(dp(8));
            trailing.setLayoutParams(trailingParams);
            trailing.setImageResource(R.drawable.ic_external_link);
            trailing.setContentDescription(null);
            ImageViewCompat.setImageTintList(trailing, ColorStateList.valueOf(
                    MaterialColors.getColor(row, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)));
            row.addView(trailing);
        }

        row.setContentDescription(getString(labelRes) + ": " + value);
        if (clickable) row.setOnClickListener(v -> openUrl(value));
        container.addView(row);
        return 1;
    }

    private void bindVersions() {
        LinearLayout container = findViewById(R.id.exploreDetailVersions);
        View section = findViewById(R.id.exploreDetailVersionsSection);
        View toggle = findViewById(R.id.exploreDetailVersionsToggle);
        ImageView toggleIcon = findViewById(R.id.exploreDetailVersionsIcon);
        if (container == null || section == null) return;
        container.removeAllViews();
        List<ExploreApp.VersionInfo> versions = new ArrayList<>();
        if (app.getPreviousVersions() != null) versions.addAll(app.getPreviousVersions());
        if (app.isGithubRelease()) {
            if (versions.isEmpty() && !TextUtils.isEmpty(app.getLatestVersionName())
                    && !TextUtils.isEmpty(app.getDownloadUrl())) {
                versions.add(new ExploreApp.VersionInfo(app.getLatestVersionName(), 0L,
                        app.getApkSize(), app.getDownloadUrl(), app.getArchitecture(), app.getSigner()));
            }
        } else {
            boolean hasLatest = false;
            for (ExploreApp.VersionInfo version : versions) {
                if (version.getVersionCode() == app.getLatestVersionCode()) {
                    hasLatest = true;
                    break;
                }
            }
            if (!hasLatest && app.getLatestVersionCode() > 0) {
                versions.add(new ExploreApp.VersionInfo(app.getLatestVersionName(), app.getLatestVersionCode(),
                        app.getApkSize(), app.getDownloadUrl(), app.getArchitecture(), app.getSigner()));
            }
            Collections.sort(versions, (left, right) -> Long.compare(right.getVersionCode(), left.getVersionCode()));
        }
        for (ExploreApp.VersionInfo version : versions) {
            container.addView(buildVersionRow(version));
        }
        section.setVisibility(versions.isEmpty() ? View.GONE : View.VISIBLE);
        ImageView headerIcon = findViewById(R.id.exploreDetailVersionsHeaderIcon);
        if (headerIcon != null) headerIcon.setImageResource(TablerIconRes.get(this, "ic_lm_history"));
        TextView countView = findViewById(R.id.exploreDetailVersionsCount);
        if (countView != null) countView.setText(String.valueOf(versions.size()));
        ViewGroup animationRoot = findViewById(R.id.exploreDetailContentRoot);
        AccordionHelper.bind(toggle, animationRoot, container, toggleIcon, new AccordionHelper.ExpandedState() {
            @Override public boolean isExpanded() { return versionsExpanded; }
            @Override public void setExpanded(boolean expanded) { versionsExpanded = expanded; }
        });
    }

    private boolean isLatestVersion(ExploreApp.VersionInfo version) {
        if (app.isGithubRelease()) {
            return !TextUtils.isEmpty(version.getVersionName())
                    && version.getVersionName().equals(app.getLatestVersionName());
        }
        return version.getVersionCode() == app.getLatestVersionCode();
    }

    private View buildVersionRow(ExploreApp.VersionInfo version) {
        boolean latest = isLatestVersion(version);

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
        card.setClickable(true);
        card.setFocusable(true);
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
        ImageView versionIcon = new ImageView(this);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(18), dp(18));
        iconParams.gravity = Gravity.CENTER;
        versionIcon.setLayoutParams(iconParams);
        versionIcon.setImageResource(TablerIconRes.get(this, latest ? "ic_check" : "ic_lm_history"));
        ImageViewCompat.setImageTintList(versionIcon, ColorStateList.valueOf(MaterialColors.getColor(card,
                latest ? com.google.android.material.R.attr.colorPrimaryContainer : iconOnColorAttr, 0)));
        versionIcon.setContentDescription(getString(latest ? R.string.latest_label : R.string.install));
        iconFrame.addView(versionIcon);
        row.addView(iconFrame);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMarginStart(dp(12));
        copy.setLayoutParams(copyParams);
        TextView title = new TextView(this);
        title.setText(versionLabel(version));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(MaterialColors.getColor(card, latest ? com.google.android.material.R.attr.colorOnPrimaryContainer : com.google.android.material.R.attr.colorOnSurface, 0));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView details = new TextView(this);
        StringBuilder detail = new StringBuilder();
        if (version.getVersionCode() > 0) detail.append(getString(R.string.version_code, version.getVersionCode()));
        if (version.getSize() > 0) detail.append(detail.length() > 0 ? "  •  " : "").append(formatSize(version.getSize()));
        if (!TextUtils.isEmpty(version.getArchitecture())) detail.append(detail.length() > 0 ? "  •  " : "").append(version.getArchitecture());
        details.setText(detail);
        details.setVisibility(detail.length() == 0 ? View.GONE : View.VISIBLE);
        details.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        details.setSingleLine(true);
        details.setEllipsize(TextUtils.TruncateAt.END);
        details.setTextColor(MaterialColors.getColor(card, latest ? com.google.android.material.R.attr.colorOnPrimaryContainer : com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
        copy.addView(title);
        copy.addView(details);
        row.addView(copy);

        TextView badge = new TextView(this);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.setMarginStart(dp(8));
        badge.setLayoutParams(badgeParams);
        badge.setText(latest ? R.string.latest_label : R.string.install);
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
        card.setContentDescription(versionLabel(version));
        card.setOnClickListener(v -> {
            if (latest) startDownload();
            else downloadOlderVersion(version);
        });
        return card;
    }

    private String versionLabel(ExploreApp.VersionInfo version) {
        return !TextUtils.isEmpty(version.getVersionName())
                ? version.getVersionName()
                : getString(R.string.version_code, version.getVersionCode());
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void openUrl(String value) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(value)));
        } catch (Exception ignored) {
            Toast.makeText(this, R.string.link_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_explore_detail, menu);
        MenuIconTintHelper.tint(menu, toolbar);
        MenuItem viewRepo = menu.findItem(R.id.action_view_repo);
        if (viewRepo != null) viewRepo.setVisible(app.getSourceUrl() != null);
        MenuItem website = menu.findItem(R.id.action_view_website);
        if (website != null) website.setVisible(!TextUtils.isEmpty(app.getWebsiteUrl()));
        MenuItem issues = menu.findItem(R.id.action_view_issues);
        if (issues != null) issues.setVisible(!TextUtils.isEmpty(app.getIssueTrackerUrl()));
        MenuItem changelog = menu.findItem(R.id.action_view_changelog);
        if (changelog != null) changelog.setVisible(!TextUtils.isEmpty(app.getChangelogUrl()));
        MenuItem translation = menu.findItem(R.id.action_view_translation);
        if (translation != null) translation.setVisible(!TextUtils.isEmpty(app.getTranslationUrl()));
        MenuItem donate = menu.findItem(R.id.action_donate);
        if (donate != null) donate.setVisible(!TextUtils.isEmpty(app.getDonateUrl()));
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_share) {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, LumenLinks.shareText(app.getName(), app.getPackageName(), app.getSourceUrl()));
            startActivity(Intent.createChooser(share, app.getName()));
            return true;
        }
        if (id == R.id.action_view_repo) {
            if (app.getSourceUrl() != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(app.getSourceUrl())));
            return true;
        }
        if (id == R.id.action_view_website) {
            openUrl(app.getWebsiteUrl());
            return true;
        }
        if (id == R.id.action_view_issues) {
            openUrl(app.getIssueTrackerUrl());
            return true;
        }
        if (id == R.id.action_view_changelog) {
            openUrl(app.getChangelogUrl());
            return true;
        }
        if (id == R.id.action_view_translation) {
            openUrl(app.getTranslationUrl());
            return true;
        }
        if (id == R.id.action_donate) {
            openUrl(app.getDonateUrl());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean shareCategory(ExploreApp a, ExploreApp b) {
        for (String c : a.getCategories()) if (b.getCategories().contains(c)) return true;
        return false;
    }

    private RequestOptions relatedIconOptions;

    private class RelatedAdapter extends RecyclerView.Adapter<RelatedAdapter.VH> {
        private final List<ExploreApp> items = new ArrayList<>();
        void submit(List<ExploreApp> next) {
            items.clear();
            if (next != null) items.addAll(next);
            notifyDataSetChanged();
        }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_related_app, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            ExploreApp related = items.get(position);
            h.name.setText(related.getName());
            if (related.getIconUrl() != null) IconLoader.load(h.icon, related.getIconUrl(), relatedIconOptions);
            else h.icon.setImageDrawable(com.opensourcefinder.app.ui.IconLoader.empty(ExploreDetailActivity.this));
        }
        @Override public int getItemCount() { return items.size(); }
        class VH extends RecyclerView.ViewHolder {
            final ImageView icon; final TextView name;
            VH(View v) {
                super(v);
                icon = v.findViewById(R.id.tileIcon);
                IconShapeHelper.apply(icon);
                name = v.findViewById(R.id.tileName);
                itemView.setOnClickListener(view -> {
                    int pos = getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    Intent i = new Intent(ExploreDetailActivity.this, ExploreDetailActivity.class);
                    i.putExtra(ExploreDetailActivity.EXTRA_EXPLORE_APP, items.get(pos));
                    startActivity(i);
                });
            }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        restoreState();
        restoreInstallState();
        syncInstalledButton();
    }

    private void syncInstalledButton() {
        if (app == null) return;
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(app.getPackageName(), 0);
            long installedCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? pi.getLongVersionCode() : pi.versionCode;
            app.setInstalled(true);
            app.setInstalledVersionCode(installedCode);
            app.setInstalledVersionName(pi.versionName);
            if (com.opensourcefinder.app.install.InstallState.ui(app) != com.opensourcefinder.app.install.InstallState.Ui.UPDATE) {
                DownloadTracker.get().markInstalled(app.getPackageName());
                installButton.hideProgress();
                installButton.setText(getString(R.string.installed));
                installButton.setEnabled(false);
            } else if (!installButton.isProgressMode()) {
                installButton.setText(getString(R.string.reinstall_update));
                installButton.setEnabled(true);
            }
        } catch (Exception ignored) {
            boolean wasInstalled = app.isInstalled();
            app.setInstalled(false);
            if (wasInstalled) {
                DownloadTracker.get().clear(app.getPackageName());
                installButton.hideProgress();
                installButton.setText(getString(R.string.download_install));
                installButton.setEnabled(true);
                installButton.setOnClickListener(v -> startDownload());
            } else {
                DownloadTracker.Entry e = DownloadTracker.get().getEntry(app.getPackageName());
                InstallTracker.Entry ie = InstallTracker.get().getEntry(app.getPackageName());
                boolean installActive = ie != null && (ie.state == InstallTracker.State.INSTALLING
                        || ie.state == InstallTracker.State.AWAITING_CONFIRM);
                boolean abandoned = e != null && (e.state == DownloadTracker.State.CANCELLED
                        || e.state == DownloadTracker.State.FAILED);
                if (abandoned && !installActive) {
                    removeGhostEntryIfNeverInstalled();
                }
            }
        }
        updateActionVisibility();
    }

    private void restoreState() {
        DownloadTracker.Entry e = DownloadTracker.get().getEntry(app.getPackageName());
        if (e == null) return;
        ensureListener();
        DownloadTracker.get().attach(app.getPackageName(), listener);
        apply(e);
        if (e.state == DownloadTracker.State.COMPLETED && !e.installPrompted
                && e.apkFile != null && e.apkFile.exists() && e.apkFile.length() > 0
                && InstallTracker.get().getEntry(app.getPackageName()) == null) {
            DownloadTracker.get().markInstallPrompted(e.key);
            handleDownloadedApk(e.apkFile);
        }
    }

    private void restoreInstallState() {
        InstallTracker.Entry ie = InstallTracker.get().getEntry(app.getPackageName());
        if (ie == null) return;
        ensureInstallListener();
        InstallTracker.get().attach(app.getPackageName(), installListener);
        applyInstallState(ie);

        if (ie.state == InstallTracker.State.AWAITING_CONFIRM) {
            final String pkg = app.getPackageName();
            final android.content.Context context = getApplicationContext();
            executorService.execute(() -> {
                InstallTracker.get().reconcileNow(context);
                InstallTracker.Entry reconciled = InstallTracker.get().getEntry(pkg);
                if (reconciled != null) {
                    runOnUiThread(() -> {
                        if (!isFinishing() && app != null && pkg.equals(app.getPackageName())) {
                            applyInstallState(reconciled);
                        }
                    });
                }
            });
        }
    }

    private void handleDownloadedApk(java.io.File apk) {
        if (app.isInstalled() && !UpdateManager.signaturesMatch(this, apk, app.getPackageName())) {
            showIncompatibleVersionDialog(apk);
            return;
        }
        startTrackedInstall(apk);
    }

    private void startTrackedInstall(java.io.File apk) {
        ensureInstallListener();
        UpdateManager.installApk(this, app.getPackageName(), apk, installListener);
    }

    private void showIncompatibleVersionDialog(java.io.File apk) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.incompatible_version_title))
                .setMessage(getString(R.string.incompatible_version_message))
                .setCancelable(false)
                .setNegativeButton(getString(R.string.cancel), (d, w) -> {
                    DownloadTracker.Entry e = DownloadTracker.get().getEntry(app.getPackageName());
                    if (e != null) apply(e);
                })
                .setPositiveButton(getString(R.string.reinstall_confirm), (d, w) -> {
                    pendingReinstallApk = apk;
                    DownloadTracker.get().markPendingReinstall(app.getPackageName());
                    UpdateManager.uninstall(this, app.getPackageName(), UNINSTALL_REQUEST);
                })
                .show();
    }

    private void ensureInstallListener() {
        if (installListener != null) return;
        installListener = entry -> runOnUiThread(() -> applyInstallState(entry));
    }

    private void applyInstallState(InstallTracker.Entry entry) {
        if (entry == null) return;
        switch (entry.state) {
            case INSTALLING:
                installButton.showIndeterminate(getString(R.string.installing));
                installButton.setEnabled(false);
                break;
            case AWAITING_CONFIRM:
                installButton.showIndeterminate(getString(R.string.waiting_install_confirmation));
                installButton.setEnabled(false);
                break;
            case INSTALLED:
                DownloadTracker.get().markInstalled(app.getPackageName());
                InstallTracker.get().clear(app.getPackageName());
                installButton.hideProgress();
                installButton.setEnabled(false);
                installButton.setText(getString(R.string.installed));
                syncHomeAfterInstall();
                break;
            case CANCELLED:
                InstallTracker.get().clear(app.getPackageName());
                installButton.hideProgress();
                installButton.setEnabled(true);
                installButton.setText(getString(R.string.install));
                break;
            case FAILED_INCOMPATIBLE: {
                java.io.File apk = entry.apkFile;
                InstallTracker.get().clear(app.getPackageName());
                installButton.hideProgress();
                installButton.setEnabled(true);
                if (apk != null) showIncompatibleVersionDialog(apk);
                break;
            }
            case FAILED:
                InstallTracker.get().clear(app.getPackageName());
                installButton.hideProgress();
                installButton.setEnabled(true);
                installButton.setText(getString(R.string.install));
                Toast.makeText(this, R.string.install_failed, Toast.LENGTH_LONG).show();
                break;
            default:
                break;
        }
        updateActionVisibility();
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

    private void ensureListener() {
        if (listener != null) return;
        listener = new DownloadTracker.Listener() {
            @Override public void onProgress(DownloadTracker.Entry entry) { runOnUiThread(() -> apply(entry)); }
            @Override public void onCompleted(DownloadTracker.Entry entry) {
                runOnUiThread(() -> {
                    apply(entry);
                    if (!entry.installPrompted && entry.apkFile != null
                            && entry.apkFile.exists() && entry.apkFile.length() > 0) {
                        DownloadTracker.get().markInstallPrompted(entry.key);
                        handleDownloadedApk(entry.apkFile);
                    }
                });
            }
            @Override public void onFailed(DownloadTracker.Entry entry) {
                runOnUiThread(() -> {
                    installButton.hideProgress();
                    installButton.setEnabled(true);
                    installButton.setText(getString(R.string.download_install));
                    updateActionVisibility();
                    Toast.makeText(ExploreDetailActivity.this, R.string.download_failed, Toast.LENGTH_LONG).show();
                });
            }
            @Override public void onCancelled(DownloadTracker.Entry entry) {
                runOnUiThread(() -> {
                    installButton.hideProgress();
                    installButton.setEnabled(true);
                    installButton.setText(app.isInstalled() ? getString(R.string.reinstall_update) : getString(R.string.download_install));
                    installButton.setOnClickListener(v -> startDownload());
                    updateActionVisibility();
                    Toast.makeText(ExploreDetailActivity.this, R.string.download_cancelled, Toast.LENGTH_SHORT).show();
                });
            }
        };
    }

    private void apply(DownloadTracker.Entry e) {
        switch (e.state) {
            case DOWNLOADING:
                installButton.showProgress(e.progress, DownloadActionButton.progressLabel(this, e.progress));
                break;
            case COMPLETED: {
                boolean canInstall = e.apkFile != null && e.apkFile.exists() && e.apkFile.length() > 0;
                InstallTracker.Entry ie = InstallTracker.get().getEntry(app.getPackageName());
                if (ie != null && ie.state == InstallTracker.State.INSTALLING) {
                    installButton.showIndeterminate(getString(R.string.installing));
                    installButton.setEnabled(false);
                } else if (ie != null && ie.state == InstallTracker.State.AWAITING_CONFIRM) {
                    installButton.showIndeterminate(getString(R.string.waiting_install_confirmation));
                    installButton.setEnabled(false);
                } else {
                    installButton.hideProgress();
                    installButton.setEnabled(canInstall);
                    installButton.setText(getString(R.string.install));
                    installButton.setOnClickListener(v -> {
                        if (e.apkFile != null && e.apkFile.exists() && e.apkFile.length() > 0) {
                            DownloadTracker.get().markInstallPrompted(e.key);
                            handleDownloadedApk(e.apkFile);
                        } else {
                            startDownload();
                        }
                    });
                }
                break;
            }
            case INSTALLED:
                installButton.hideProgress();
                installButton.setEnabled(false);
                installButton.setText(getString(R.string.installed));
                break;
            default:
                break;
        }
        updateActionVisibility();
    }

    private MatchedApp buildMatch() {
        MatchedApp match = new MatchedApp();
        match.setPackageName(app.getPackageName());
        match.setAppLabel(app.getName());
        match.setLatestTag(app.getLatestVersionName());
        match.setLatestVersionCode(app.getLatestVersionCode());
        match.setDownloadUrl(app.getDownloadUrl());
        match.setSourceUrl(app.getSourceUrl());
        match.setSourceType("fdroid");
        match.setOrigin("manual");
        match.setIndexRepo(app.getRepoName());
        match.setGithubSlug(app.getGithubSlug());
        match.setIconUrl(app.getIconUrl());
        match.setDescription(app.getDescription() != null ? app.getDescription() : app.getSummary());
        match.setNotInstalled(!app.isInstalled());
        match.setUpdateAvailable(com.opensourcefinder.app.install.InstallState.ui(app) != com.opensourcefinder.app.install.InstallState.Ui.UP_TO_DATE);
        return match;
    }

    private void syncHomeAfterInstall() {
        if (app == null) return;
        final android.content.Context context = getApplicationContext();
        final String pkg = app.getPackageName();
        final MatchedApp match = buildMatch();
        new Thread(() -> {
            match.setNotInstalled(false);
            com.opensourcefinder.app.install.InstallState.refresh(context, match);
            MatchStore.upsert(context, match);
            com.opensourcefinder.app.install.InstallEvents.packageChanged(context, pkg);
        }, "home-sync").start();
    }

    private void startDownload() {
        if (TextUtils.isEmpty(app.getDownloadUrl())) {
            Toast.makeText(this, R.string.download_link_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean alreadyTracked = false;
        for (MatchedApp m : MatchStore.get(this)) {
            if (app.getPackageName().equals(m.getPackageName())) { alreadyTracked = true; break; }
        }
        MatchedApp match = buildMatch();
        MatchStore.upsert(this, match);

        String fileName = (app.getName() != null ? app.getName() : app.getPackageName())
                + "-" + (app.getLatestVersionName() != null ? app.getLatestVersionName() : "latest") + ".apk";
        popInstallButton();
        ensureListener();
        DownloadTracker.get().start(this, app.getPackageName(), app.getDownloadUrl(), fileName, listener, app.getIconUrl());
        if (!alreadyTracked) DownloadTracker.get().markHomePlaceholderOwned(app.getPackageName());
        updateActionVisibility();
    }

    @Override protected void onPause() {
        super.onPause();
        if (listener != null) DownloadTracker.get().detach(app.getPackageName(), listener);
        if (installListener != null) InstallTracker.get().detach(app.getPackageName(), installListener);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        executorService.shutdownNow();
    }
}
