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

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.loadingindicator.LoadingIndicator;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.install.InstallState;
import com.opensourcefinder.app.model.PlayApp;
import com.opensourcefinder.app.model.SourceRepository;
import com.opensourcefinder.app.network.PlayStoreClient;
import com.opensourcefinder.app.scanner.MatchStore;
import com.opensourcefinder.app.scanner.RepositoryManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlaySearchActivity extends BaseActivity {

    public static final String EXTRA_PACKAGE = "package";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService detailExecutor = Executors.newFixedThreadPool(4);
    private final Map<String, PlayApp> cache = new ConcurrentHashMap<>();
    private final Set<String> requested = ConcurrentHashMap.newKeySet();
    private final Set<String> failedIds = ConcurrentHashMap.newKeySet();
    private final List<String> resultIds = new ArrayList<>();

    private final Map<Integer, List<String>> exploreCache = new ConcurrentHashMap<>();

    private static final int[] CATEGORY_LABELS = {
            R.string.play_cat_open_source, R.string.play_cat_privacy, R.string.play_cat_productivity,
            R.string.play_cat_media, R.string.play_cat_tools, R.string.play_cat_social};
    private static final String[] CATEGORY_QUERIES = {
            "open source", "privacy", "productivity", "media player", "tools", "social"};
    private int category = 0;

    private MaterialToolbar toolbar;
    private HeaderSearch header;
    private ChipGroup categoryChips;
    private View chipScroll;
    private LoadingIndicator searchProgress;
    private TextView resultSummary;
    private TextView emptyState;
    private RecyclerView resultsList;
    private View searchContent;
    private View detailContent;
    private ImageView detailIcon;
    private TextView detailName;
    private TextView detailDeveloper;
    private TextView detailStats;
    private TextView detailVersion;
    private TextView detailInstalled;
    private TextView detailPackage;
    private TextView detailCategory;
    private TextView detailDescription;
    private TextView detailError;
    private LoadingIndicator detailProgress;
    private MaterialButton addToListButton;
    private MaterialButton openInPlayButton;

    private String selectedPackage;
    private long searchGeneration;

    private boolean directLaunch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        MotionHelper.applyDistinctActivityTransition(this);
        setContentView(R.layout.activity_play_search);
        MotionHelper.animateActivityContent(this);
        ThemeManager.applyWindow(this);
        bindViews();
        setupToolbar();
        setupChips();
        setupSearch();
        showSearch();
        showExplore();

        String pkg = PlayStoreClient.extractPackage(getIntent().getStringExtra(EXTRA_PACKAGE));
        if (pkg != null) {
            directLaunch = true;
            showDetail(pkg);
        }
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        categoryChips = findViewById(R.id.categoryChips);
        chipScroll = findViewById(R.id.chipScroll);
        searchProgress = findViewById(R.id.searchProgress);
        resultSummary = findViewById(R.id.resultSummary);
        emptyState = findViewById(R.id.emptyState);
        resultsList = findViewById(R.id.resultsList);
        searchContent = findViewById(R.id.searchContent);
        detailContent = findViewById(R.id.detailContent);
        detailIcon = findViewById(R.id.detailIcon);
        detailName = findViewById(R.id.detailFullName);
        detailDeveloper = findViewById(R.id.detailOwner);
        detailStats = findViewById(R.id.detailStats);
        detailVersion = findViewById(R.id.detailVersion);
        detailInstalled = findViewById(R.id.detailInstalled);
        detailPackage = findViewById(R.id.detailPackage);
        detailCategory = findViewById(R.id.detailCategory);
        detailDescription = findViewById(R.id.detailDescription);
        detailError = findViewById(R.id.detailError);
        detailProgress = findViewById(R.id.detailProgress);
        addToListButton = findViewById(R.id.addToListButton);
        openInPlayButton = findViewById(R.id.openInPlayButton);
        IconShapeHelper.apply(detailIcon);
        resultsList.setLayoutManager(new LinearLayoutManager(this));
        resultsList.setAdapter(new PlayAdapter());
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationIcon(TablerIconRes.get(this, "ic_arrow_back"));
        toolbar.setNavigationIconTint(MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface));
        ToolbarCircleHelper.apply(toolbar);
        toolbar.setNavigationOnClickListener(v -> goBack());
    }

    private void goBack() {
        if (PanelTransition.isShown(detailContent) && !directLaunch) showSearch();
        else if (header != null && header.hasFocusOrText()) header.clearAndNotify();
        else finish();
    }

    private void setupSearch() {

        header = HeaderSearch.bind(getWindow().getDecorView(), R.drawable.ic_brand_google_play, getString(R.string.play_header_title),
                R.string.play_header_hint, 600L, (text, immediate) -> {
                    if (text.length() < 2) showExplore();
                    else runSearch(text);
                });
        addToListButton.setOnClickListener(v -> toggleSelectedInList());
        openInPlayButton.setOnClickListener(v -> {
            if (selectedPackage != null) PlayStoreClient.openInPlayStore(this, selectedPackage);
        });
    }

    private void setupChips() {
        for (int i = 0; i < CATEGORY_LABELS.length; i++) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_play_category_chip, categoryChips, false);
            chip.setId(View.generateViewId());
            chip.setText(CATEGORY_LABELS[i]);
            final int index = i;
            chip.setChecked(i == category);
            chip.setOnClickListener(v -> {
                category = index;
                showExplore();
            });
            categoryChips.addView(chip);
        }
    }

    private void showExplore() {
        final long generation = ++searchGeneration;
        chipScroll.setVisibility(View.VISIBLE);
        resultSummary.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        final int selected = category;
        List<String> cached = exploreCache.get(selected);
        if (cached != null) {
            searchProgress.setVisibility(View.GONE);
            showResults(cached, false, false);
            return;
        }
        resultIds.clear();
        resultsList.getAdapter().notifyDataSetChanged();
        searchProgress.setVisibility(View.VISIBLE);
        searchExecutor.execute(() -> {
            List<String> ids = new ArrayList<>();
            boolean failed = false;
            try {
                ids.addAll(PlayStoreClient.search(CATEGORY_QUERIES[selected]));
            } catch (Exception e) {
                failed = true;
            }
            final boolean error = failed && ids.isEmpty();
            runOnUiThread(() -> {
                if (generation != searchGeneration || isFinishing() || isDestroyed()) return;
                searchProgress.setVisibility(View.GONE);
                if (!error) exploreCache.put(selected, ids);
                showResults(ids, error, false);
            });
        });
    }

    private void showResults(List<String> ids, boolean error, boolean showCount) {
        resultIds.clear();
        resultIds.addAll(ids);
        resultsList.getAdapter().notifyDataSetChanged();
        resultsList.scrollToPosition(0);
        if (resultIds.isEmpty()) {
            emptyState.setText(error ? R.string.play_search_error
                    : showCount ? R.string.play_search_no_results : R.string.play_search_empty);
            emptyState.setVisibility(View.VISIBLE);
            resultSummary.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            if (showCount) {
                resultSummary.setText(getString(R.string.play_search_result_count, resultIds.size()));
                resultSummary.setVisibility(View.VISIBLE);
            } else {
                resultSummary.setVisibility(View.GONE);
            }
        }
    }

    private void runSearch(final String query) {
        final long generation = ++searchGeneration;
        chipScroll.setVisibility(View.GONE);
        searchProgress.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        resultSummary.setVisibility(View.GONE);
        searchExecutor.execute(() -> {
            List<String> ids = new ArrayList<>();
            String direct = PlayStoreClient.extractPackage(query);
            boolean isLink = PlayStoreClient.looksLikeLink(query);
            if (direct != null) ids.add(direct);
            boolean failed = false;
            if (!isLink) {
                try {
                    for (String id : PlayStoreClient.search(query)) if (!ids.contains(id)) ids.add(id);
                } catch (Exception e) {
                    failed = true;
                }
            }
            final boolean error = failed && ids.isEmpty();
            runOnUiThread(() -> {
                if (generation != searchGeneration || isFinishing() || isDestroyed()) return;
                searchProgress.setVisibility(View.GONE);
                showResults(ids, error, true);
            });
        });
    }

    private void showSearch() {
        PanelTransition.swap(detailContent, searchContent, false);
        selectedPackage = null;
        toolbar.setTitle("");
        if (header != null) header.setRowVisible(true);
    }

    private void requestApp(final String pkg, final boolean force) {
        if (pkg == null) return;
        if (!force && (cache.containsKey(pkg) || !requested.add(pkg))) return;
        if (force) requested.add(pkg);
        detailExecutor.execute(() -> {
            PlayApp app = null;
            boolean failed = false;
            try {
                app = PlayStoreClient.fetchApp(pkg);
            } catch (Exception e) {
                failed = true;
            }
            final PlayApp result = app;
            final boolean error = failed;
            if (result != null) { cache.put(pkg, result); failedIds.remove(pkg); }
            else failedIds.add(pkg);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                int index = resultIds.indexOf(pkg);
                if (index >= 0 && resultsList.getAdapter() != null) resultsList.getAdapter().notifyItemChanged(index);
                if (pkg.equals(selectedPackage)) {
                    detailProgress.setVisibility(View.GONE);
                    if (result != null) {
                        detailError.setVisibility(View.GONE);
                        bindDetail(result);
                    } else {
                        detailError.setText(error ? R.string.play_detail_error : R.string.play_detail_not_found);
                        detailError.setVisibility(View.VISIBLE);
                    }
                }
            });
        });
    }

    private void showDetail(String pkg) {
        selectedPackage = pkg;
        PanelTransition.swap(searchContent, detailContent, true);
        if (header != null) header.setRowVisible(false);
        detailError.setVisibility(View.GONE);
        PlayApp cached = cache.get(pkg);
        bindDetail(cached != null ? cached : new PlayApp(pkg));
        if (cached == null) {
            detailProgress.setVisibility(View.VISIBLE);
            requestApp(pkg, true);
        } else {
            detailProgress.setVisibility(View.GONE);
        }
    }

    private void bindDetail(PlayApp app) {
        toolbar.setTitle(R.string.play_app_title);
        detailName.setText(app.displayName());
        detailDeveloper.setText(app.developer == null || app.developer.isEmpty()
                ? getString(R.string.play_developer_unknown) : app.developer);
        detailPackage.setText(getString(R.string.play_package_name, app.packageName));

        List<String> stats = new ArrayList<>();
        if (app.rating != null && !app.rating.isEmpty()) {
            stats.add("\u2605 " + app.rating + (app.ratingCount != null && !app.ratingCount.isEmpty()
                    ? " (" + app.ratingCount + ")" : ""));
        }
        if (app.installs != null && !app.installs.isEmpty()) stats.add(getString(R.string.play_installs, app.installs));
        detailStats.setText(stats.isEmpty() ? getString(R.string.play_stats_unknown) : join(stats, "  \u00B7  "));

        detailVersion.setText(app.version == null || app.version.isEmpty()
                ? getString(R.string.play_version_unknown)
                : getString(R.string.play_version_latest, app.version));

        if (app.category != null && !app.category.isEmpty()) {
            detailCategory.setText(getString(R.string.play_category, app.category));
            detailCategory.setVisibility(View.VISIBLE);
        } else {
            detailCategory.setVisibility(View.GONE);
        }

        detailDescription.setText(app.description == null || app.description.isEmpty()
                ? getString(R.string.play_no_description) : app.description);

        bindIcon(app);
        bindActions(app);
    }

    private void bindIcon(PlayApp app) {
        if (app.iconUrl != null) {
            IconLoader.load(detailIcon, app.iconUrl);
            return;
        }
        try {
            Drawable installedIcon = getPackageManager().getApplicationIcon(app.packageName);
            detailIcon.setImageDrawable(installedIcon);
        } catch (Exception e) {
            detailIcon.setImageDrawable(com.opensourcefinder.app.ui.IconLoader.empty(this));
        }
    }

    private void bindActions(PlayApp app) {
        InstallState.Info installed = InstallState.read(this, app.packageName);
        boolean updateAvailable = InstallState.updateAvailable(installed, 0L, app.version);
        if (installed == null) {
            detailInstalled.setText(R.string.play_status_not_installed);
        } else if (updateAvailable) {
            detailInstalled.setText(getString(R.string.play_status_update, installed.versionName));
        } else {
            detailInstalled.setText(getString(R.string.play_status_installed, installed.versionName));
        }

        boolean inList = isInList(app.packageName);
        addToListButton.setText(inList ? R.string.play_remove_from_list : R.string.play_add_to_list);
        addToListButton.setEnabled(true);
        openInPlayButton.setText(updateAvailable ? R.string.play_update_in_store
                : installed != null ? R.string.play_open_in_store : R.string.play_install_from_store);
    }

    private void toggleSelectedInList() {
        if (selectedPackage == null) return;
        PlayApp app = cache.get(selectedPackage);
        if (app == null) app = new PlayApp(selectedPackage);
        String url = PlayStoreClient.appUrl(selectedPackage);
        if (isInList(selectedPackage)) {
            RepositoryManager.remove(this, url);
            MatchStore.remove(this, selectedPackage);
            Toast.makeText(this, R.string.play_removed_from_list, Toast.LENGTH_SHORT).show();
        } else {
            RepositoryManager.add(this, new SourceRepository(url, app.displayName(), PlayStoreClient.SOURCE_TYPE));
            MatchStore.upsert(this, PlayStoreClient.toMatchedApp(this, app));
            Toast.makeText(this, R.string.play_added_to_list, Toast.LENGTH_SHORT).show();
        }
        bindActions(app);
    }

    private boolean isInList(String pkg) {
        String url = PlayStoreClient.appUrl(pkg);
        for (SourceRepository source : RepositoryManager.get(this)) {
            if (source.getUrl() != null && source.getUrl().equalsIgnoreCase(url)) return true;
        }
        return false;
    }

    private static String join(List<String> parts, String separator) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(separator);
            sb.append(part);
        }
        return sb.toString();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (selectedPackage != null && detailContent.getVisibility() == View.VISIBLE) {
            PlayApp app = cache.get(selectedPackage);
            if (app != null) bindActions(app);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (header != null) header.destroy();
        searchExecutor.shutdownNow();
        detailExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (PanelTransition.isShown(detailContent) && !directLaunch) {
            showSearch();
            return;
        }
        if (header != null && header.hasFocusOrText()) {
            header.clearAndNotify();
            return;
        }
        super.onBackPressed();
    }

    private final class PlayAdapter extends RecyclerView.Adapter<PlayAdapter.PlayViewHolder> {
        @NonNull
        @Override public PlayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_play_app, parent, false);
            return new PlayViewHolder(view);
        }

        @Override public void onBindViewHolder(@NonNull PlayViewHolder holder, int position) {
            holder.bind(resultIds.get(position));
        }

        @Override public int getItemCount() {
            return resultIds.size();
        }

        final class PlayViewHolder extends RecyclerView.ViewHolder {
            private final ImageView icon;
            private final TextView name;
            private final TextView developer;
            private final TextView summary;

            PlayViewHolder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.playItemIcon);
                name = itemView.findViewById(R.id.playItemName);
                developer = itemView.findViewById(R.id.playItemDeveloper);
                summary = itemView.findViewById(R.id.playItemSummary);
                IconShapeHelper.apply(icon);
            }

            void bind(final String pkg) {
                PlayApp app = cache.get(pkg);
                if (app == null) {
                    name.setText(pkg);
                    developer.setText("");
                    summary.setText(failedIds.contains(pkg) ? R.string.play_item_unavailable : R.string.loading);
                    icon.setImageDrawable(com.opensourcefinder.app.ui.IconLoader.empty(itemView.getContext()));
                    requestApp(pkg, false);
                } else {
                    name.setText(app.displayName());
                    developer.setText(app.developer == null ? "" : app.developer);
                    summary.setText(app.description == null ? "" : app.description);
                    if (app.iconUrl != null) {
                        IconLoader.load(icon, app.iconUrl);
                    } else {
                        icon.setImageDrawable(com.opensourcefinder.app.ui.IconLoader.empty(itemView.getContext()));
                    }
                }
                itemView.setOnClickListener(v -> showDetail(pkg));
            }
        }
    }
}
