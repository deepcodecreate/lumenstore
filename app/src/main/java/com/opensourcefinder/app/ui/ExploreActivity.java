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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.download.DownloadTracker;
import com.opensourcefinder.app.model.ExploreApp;
import com.opensourcefinder.app.model.InstalledAppInfo;
import com.opensourcefinder.app.model.MatchedApp;
import com.opensourcefinder.app.scanner.AppScanner;
import com.opensourcefinder.app.scanner.FdroidIndexManager;
import com.opensourcefinder.app.scanner.MatchStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExploreActivity extends BaseActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<ExploreApp> all = new ArrayList<>();
    private volatile boolean catalogLoading = false;
    private ExploreAdapter searchAdapter;
    private View exploreScroll;
    private View loadingPanel;
    private RecyclerView searchRecycler;
    private SwipeRefreshLayout exploreRefresh;
    private LinearLayout categoriesContainer;
    private TextView empty;
    private MaterialToolbar toolbar;
    private com.google.android.material.textfield.TextInputLayout searchLayout;
    private androidx.activity.OnBackPressedCallback categoryBackCallback;
    private Runnable pendingFilter;
    private String lastQuery = "";
    private volatile boolean destroyed = false;
    private List<ExploreApp> activeCategoryApps;
    private long filterGeneration = 0L;

    private RequestOptions iconOptions;

    @Override protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        MotionHelper.applyActivityTransition(this, true);
        setContentView(R.layout.activity_explore);
        MotionHelper.animateActivityContent(this);
        MotionHelper.keepBottomNavStatic(this);
        ThemeManager.applyWindow(this);
        toolbar = findViewById(R.id.toolbar);
        int circleIcon = com.opensourcefinder.app.ui.TablerIconRes.get(this, "ic_circle");
        iconOptions = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .centerCrop()
                .dontAnimate()
                .override(112, 112)
                .placeholder(circleIcon)
                .error(circleIcon);
        searchLayout = findViewById(R.id.searchLayout);
        setSearchStartMargin(4);
        toolbar.setTitle(getString(R.string.explore_title));
        BottomNavHelper.bind(this, R.id.nav_explore);

        empty = findViewById(R.id.emptyView);
        exploreScroll = findViewById(R.id.exploreScroll);
        loadingPanel = findViewById(R.id.loadingPanel);
        exploreRefresh = findViewById(R.id.exploreRefresh);
        if (exploreRefresh != null) {
            exploreRefresh.setColorSchemeColors(
                    com.google.android.material.color.MaterialColors.getColor(
                            exploreRefresh, android.R.attr.colorAccent));
            exploreRefresh.setProgressBackgroundColorSchemeColor(
                    com.google.android.material.color.MaterialColors.getColor(
                            exploreRefresh, com.google.android.material.R.attr.colorSurfaceContainerHigh));
            exploreRefresh.setOnChildScrollUpCallback((parent, child) -> {
                if (searchRecycler != null && searchRecycler.getVisibility() == View.VISIBLE) {
                    return searchRecycler.canScrollVertically(-1);
                }
                return exploreScroll != null && exploreScroll.canScrollVertically(-1);
            });
            exploreRefresh.setOnRefreshListener(() -> {
                loadCatalog(true);
            });
        }
        categoriesContainer = findViewById(R.id.categoriesContainer);

        ((TextView) findViewById(R.id.sectionNew).findViewById(R.id.sectionNewTitle)).setText(getString(R.string.new_apps));
        ((TextView) findViewById(R.id.sectionUpdated).findViewById(R.id.sectionUpdatedTitle)).setText(getString(R.string.recently_updated));
        ((TextView) findViewById(R.id.sectionPopular).findViewById(R.id.sectionPopularTitle)).setText(getString(R.string.popular));

        setupHorizontal(R.id.sectionNew);
        setupHorizontal(R.id.sectionUpdated);
        setupHorizontal(R.id.sectionPopular);

        searchRecycler = findViewById(R.id.recyclerView);
        LinearLayoutManager glm = new LinearLayoutManager(this);
        glm.setInitialPrefetchItemCount(8);
        searchRecycler.setLayoutManager(glm);
        searchRecycler.setHasFixedSize(true);
        searchRecycler.setItemViewCacheSize(10);
        MotionHelper.setupListAnimator(searchRecycler);
        searchAdapter = new ExploreAdapter();
        searchRecycler.setAdapter(searchAdapter);

        TextInputEditText search = findViewById(R.id.searchInput);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (pendingFilter != null) mainHandler.removeCallbacks(pendingFilter);
                final String q = s == null ? "" : s.toString();
                pendingFilter = () -> applyFilter(q);
                mainHandler.postDelayed(pendingFilter, 180);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadCatalog(false);

        categoryBackCallback = new androidx.activity.OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() {
                exitCategory();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, categoryBackCallback);
    }

    private void setupHorizontal(int sectionId) {
        RecyclerView rv = findViewById(sectionId).findViewById(sectionRecyclerId(sectionId));
        rv.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rv.setNestedScrollingEnabled(false);
        rv.setHasFixedSize(true);
        rv.setItemViewCacheSize(8);
        MotionHelper.setupListAnimator(rv);
        rv.setAdapter(new TileAdapter());
    }

    private final Runnable installEventsListener = () -> { refreshInstalledStatesOnly(); if (searchAdapter != null) searchAdapter.notifyDataSetChanged(); };

    @Override public void onPause() {
        com.opensourcefinder.app.install.InstallEvents.removeListener(installEventsListener);
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        com.opensourcefinder.app.install.InstallEvents.addListener(installEventsListener);
        refreshInstalledStatesOnly();
    }

    private void refreshInstalledStatesOnly() {
        if (all.isEmpty()) return;
        final android.content.Context context = getApplicationContext();
        executor.execute(() -> {
            java.util.Map<String, com.opensourcefinder.app.install.InstallState.Info> live = com.opensourcefinder.app.install.InstallState.readAll(context);
            boolean changed = false;
            for (ExploreApp app : all) {
                com.opensourcefinder.app.install.InstallState.Info info = live.get(app.getPackageName());
                boolean isInstalled = info != null;
                long newVersionCode = isInstalled ? info.versionCode : 0L;
                if (isInstalled != app.isInstalled() || newVersionCode != app.getInstalledVersionCode()) changed = true;
                com.opensourcefinder.app.install.InstallState.refresh(app, info);
            }
            if (!changed) return;
            runOnUiThread(() -> {
                if (destroyed) return;
                if (searchAdapter != null) searchAdapter.notifyDataSetChanged();
            });
        });
    }

    private void loadCatalog(boolean refresh) {
        if (catalogLoading) {
            if (exploreRefresh != null) exploreRefresh.setRefreshing(false);
            return;
        }
        catalogLoading = true;
        if (exploreRefresh != null) exploreRefresh.setRefreshing(refresh);
        boolean firstLoad = all.isEmpty();
        if (loadingPanel != null) loadingPanel.setVisibility(firstLoad ? View.VISIBLE : View.GONE);
        if (firstLoad && exploreScroll != null) exploreScroll.setVisibility(View.INVISIBLE);
        executor.execute(() -> {
            try {
                List<InstalledAppInfo> installed = AppScanner.listUserInstalledApps(this);
                Set<String> pkgs = new HashSet<>();
                Map<String, Long> installedVersions = new HashMap<>();
                for (InstalledAppInfo a : installed) {
                    pkgs.add(a.getPackageName());
                    installedVersions.put(a.getPackageName(), a.getVersionCode());
                }
                List<ExploreApp> catalog = new FdroidIndexManager(this).loadExploreCatalog(pkgs, null);
                for (ExploreApp app : catalog) {
                    Long versionCode = installedVersions.get(app.getPackageName());
                    boolean isInstalled = versionCode != null;
                    app.setInstalled(isInstalled);
                    app.setInstalledVersionCode(isInstalled ? versionCode : 0L);
                }
                java.util.Map<String, com.opensourcefinder.app.install.InstallState.Info> liveAll = com.opensourcefinder.app.install.InstallState.readAll(getApplicationContext());
                for (ExploreApp app : catalog) com.opensourcefinder.app.install.InstallState.refresh(app, liveAll.get(app.getPackageName()));
                runOnUiThread(() -> {
                    if (destroyed) return;
                    catalogLoading = false;
                    all.clear();
                    all.addAll(stableCatalog(catalog));
                    bindHomeSections();
                    if (loadingPanel != null) loadingPanel.setVisibility(View.GONE);
                    if (exploreRefresh != null) exploreRefresh.setRefreshing(false);
                    applyFilter(lastQuery);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    catalogLoading = false;
                    if (loadingPanel != null) loadingPanel.setVisibility(View.GONE);
                    if (exploreRefresh != null) exploreRefresh.setRefreshing(false);
                    if (exploreScroll != null) exploreScroll.setVisibility(View.VISIBLE);
                    Toast.makeText(this, getString(R.string.catalog_error), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private List<ExploreApp> stableCatalog(List<ExploreApp> catalog) {
        List<ExploreApp> stable = new ArrayList<>(catalog == null
                ? java.util.Collections.emptyList() : catalog);
        stable.sort((a, b) -> {
            String an = a.getName() == null ? "" : a.getName();
            String bn = b.getName() == null ? "" : b.getName();
            int byName = an.compareToIgnoreCase(bn);
            if (byName != 0) return byName;
            String ap = a.getPackageName() == null ? "" : a.getPackageName();
            String bp = b.getPackageName() == null ? "" : b.getPackageName();
            return ap.compareToIgnoreCase(bp);
        });
        return stable;
    }

    private void bindHomeSections() {
        bindSection(R.id.sectionNew, 0);
        bindSection(R.id.sectionUpdated, 16);
        bindSection(R.id.sectionPopular, 32);
        bindCategories();
    }

    private void bindSection(int sectionId, int start) {
        bindTiles(sectionId, slice(all, start, 16));
        View header = findViewById(sectionId);
        View arrow = header.findViewById(sectionArrowId(sectionId));
        if (arrow != null) {
            CharSequence title = ((TextView) header.findViewById(sectionTitleId(sectionId))).getText();
            List<ExploreApp> full = slice(all, start, all.size());
            arrow.setOnClickListener(v -> showCategory(title.toString(), full));
        }
    }

    private List<ExploreApp> slice(List<ExploreApp> src, int start, int count) {
        List<ExploreApp> out = new ArrayList<>();
        if (src.isEmpty()) return out;
        int from = Math.min(start, src.size());
        int to = Math.min(from + count, src.size());
        if (from >= to) {
            from = 0;
            to = Math.min(count, src.size());
        }
        out.addAll(src.subList(from, to));
        return out;
    }

    @SuppressWarnings("unchecked")
    private void bindTiles(int sectionId, List<ExploreApp> apps) {
        RecyclerView rv = findViewById(sectionId).findViewById(sectionRecyclerId(sectionId));
        TileAdapter adapter = (TileAdapter) rv.getAdapter();
        if (adapter != null) adapter.submit(apps);
    }

    private void bindCategories() {
        if (categoriesContainer == null) return;
        categoriesContainer.removeAllViews();
        Map<String, Integer> counts = new HashMap<>();
        Map<String, List<ExploreApp>> groups = new HashMap<>();
        for (ExploreApp app : all) {
            String key = app.getRepoName() == null || app.getRepoName().isEmpty() ? getString(R.string.other_repositories) : app.getRepoName();
            Integer n = counts.get(key);
            counts.put(key, n == null ? 1 : n + 1);
            List<ExploreApp> list = groups.get(key);
            if (list == null) {
                list = new ArrayList<>();
                groups.put(key, list);
            }
            list.add(app);
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            View row = inflater.inflate(R.layout.item_category_row, categoriesContainer, false);
            ((TextView) row.findViewById(R.id.catTitle)).setText(e.getKey());
            ((TextView) row.findViewById(R.id.catSubtitle)).setText(getString(R.string.apps_found, e.getValue()));
            final List<ExploreApp> catApps = groups.get(e.getKey());
            row.setOnClickListener(v -> showCategory(e.getKey(), catApps));
            categoriesContainer.addView(row);
        }
    }

    private void setSearchStartMargin(int dp) {
        if (searchLayout == null) return;
        float d = getResources().getDisplayMetrics().density;
        android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) searchLayout.getLayoutParams();
        lp.setMarginStart((int) (dp * d));
        lp.setMarginEnd((int) (4 * d));
        searchLayout.setLayoutParams(lp);
    }

    private void showCategory(String title, List<ExploreApp> apps) {
        filterGeneration++;
        activeCategoryApps = apps == null ? new ArrayList<>() : new ArrayList<>(apps);
        toolbar.setTitle(title);
        toolbar.setNavigationIcon(com.opensourcefinder.app.ui.TablerIconRes.get(this, "ic_arrow_back"));
        toolbar.setNavigationIconTint(com.google.android.material.color.MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface));
        setSearchStartMargin(52);
        toolbar.setNavigationOnClickListener(v -> exitCategory());
        categoryBackCallback.setEnabled(true);
        TextInputEditText search = findViewById(R.id.searchInput);
        if (search != null) { search.setText(""); search.setHint(title); }
        searchRecycler.stopScroll();
        searchRecycler.setVisibility(View.GONE);
        searchRecycler.scrollToPosition(0);
        searchAdapter.submit(activeCategoryApps);
        searchRecycler.scrollToPosition(0);
        crossfade(exploreScroll, searchRecycler);
        empty.setVisibility(activeCategoryApps.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void exitCategory() {
        filterGeneration++;
        activeCategoryApps = null;
        toolbar.setNavigationIcon(null);
        toolbar.setNavigationOnClickListener(null);
        setSearchStartMargin(4);
        categoryBackCallback.setEnabled(false);
        toolbar.setTitle(getString(R.string.explore_title));
        lastQuery = "";
        TextInputEditText search = findViewById(R.id.searchInput);
        if (search != null) { search.setText(""); search.setHint(getString(R.string.search_hint)); }
        crossfade(searchRecycler, exploreScroll);
        empty.setVisibility(View.GONE);
    }

    private void applyFilter(String q) {
        if (destroyed) return;
        final long requestGeneration = ++filterGeneration;
        lastQuery = q == null ? "" : q;
        final String query = lastQuery.trim().toLowerCase(Locale.ROOT);
        final List<ExploreApp> base = activeCategoryApps != null ? new ArrayList<>(activeCategoryApps) : new ArrayList<>(all);
        if (query.isEmpty()) {
            if (activeCategoryApps != null) {
                searchRecycler.stopScroll();
                searchAdapter.submit(base);
                searchRecycler.scrollToPosition(0);
                crossfade(exploreScroll, searchRecycler);
                empty.setVisibility(base.isEmpty() ? View.VISIBLE : View.GONE);
            } else {
                toolbar.setNavigationIcon(null);
                toolbar.setNavigationOnClickListener(null);
                categoryBackCallback.setEnabled(false);
                toolbar.post(() -> toolbar.setTitle(getString(R.string.explore_title)));
                crossfade(searchRecycler, exploreScroll);
                empty.setVisibility(View.GONE);
            }
            return;
        }
        executor.execute(() -> {
            List<ExploreApp> next = new ArrayList<>();
            for (ExploreApp app : base) if (matches(app, query)) next.add(app);
            runOnUiThread(() -> {
                if (destroyed || requestGeneration != filterGeneration
                        || !query.equals(lastQuery.trim().toLowerCase(Locale.ROOT))) return;
                searchRecycler.stopScroll();
                searchAdapter.submit(next);
                empty.setVisibility(next.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void crossfade(View outView, View inView) {
        if (inView.getVisibility() == View.VISIBLE && outView.getVisibility() != View.VISIBLE) {
            return;
        }

        ViewGroup parent = outView.getParent() instanceof ViewGroup
                ? (ViewGroup) outView.getParent() : null;
        if (parent != null) {
            android.transition.TransitionManager.endTransitions(parent);
            com.google.android.material.transition.platform.MaterialFadeThrough transition =
                    new com.google.android.material.transition.platform.MaterialFadeThrough();
            transition.setDuration(MotionHelper.getDuration(this, 160, 220));
            transition.setInterpolator(MotionHelper.getInterpolator(this));
            android.transition.TransitionManager.beginDelayedTransition(parent, transition);
        }
        outView.setVisibility(View.GONE);
        inView.setVisibility(View.VISIBLE);
        inView.bringToFront();
    }

    private static boolean matches(ExploreApp app, String query) {
        return (app.getName() != null && app.getName().toLowerCase(Locale.ROOT).contains(query))
                || (app.getPackageName() != null && app.getPackageName().toLowerCase(Locale.ROOT).contains(query))
                || (app.getSummary() != null && app.getSummary().toLowerCase(Locale.ROOT).contains(query));
    }

    private void openDetail(ExploreApp app) {
        Intent i = new Intent(this, ExploreDetailActivity.class);
        i.putExtra(ExploreDetailActivity.EXTRA_EXPLORE_APP, app);
        startActivity(i);
    }

    private void startDownload(ExploreApp app) {
        if (app.isGithubRelease() && app.getDownloadUrl() == null) {
            openDetail(app);
            return;
        }
        if (app.getDownloadUrl() == null) {
            Toast.makeText(this, getString(R.string.download_link_missing), Toast.LENGTH_SHORT).show();
            return;
        }
        boolean alreadyTracked = false;
        for (MatchedApp existing : MatchStore.get(this)) {
            if (app.getPackageName().equals(existing.getPackageName())) { alreadyTracked = true; break; }
        }
        MatchedApp match = new MatchedApp();
        match.setPackageName(app.getPackageName());
        match.setAppLabel(app.getName() != null ? app.getName() : app.getPackageName());
        match.setLatestTag(app.getLatestVersionName());
        match.setLatestVersionCode(app.getLatestVersionCode());
        match.setDownloadUrl(app.getDownloadUrl());
        match.setIconUrl(app.getIconUrl());
        match.setSourceUrl(app.getSourceUrl());
        match.setSourceType("fdroid");
        match.setOrigin("manual");
        match.setDescription(app.getDescription() != null ? app.getDescription() : app.getSummary());
        if (app.isInstalled() && app.getLatestVersionCode() > 0 && app.getInstalledVersionCode() >= app.getLatestVersionCode()) return;
        match.setNotInstalled(!app.isInstalled());
        match.setUpdateAvailable(com.opensourcefinder.app.install.InstallState.ui(app) != com.opensourcefinder.app.install.InstallState.Ui.UP_TO_DATE);
        MatchStore.upsert(this, match);
        String fileName = (app.getName() != null ? app.getName() : app.getPackageName())
                + "-" + (app.getLatestVersionName() != null ? app.getLatestVersionName() : "latest") + ".apk";
        DownloadTracker.get().start(this, app.getPackageName(), app.getDownloadUrl(), fileName, new DownloadTracker.Listener() {
            @Override public void onProgress(DownloadTracker.Entry entry) {
                runOnUiThread(() -> { if (searchAdapter != null) searchAdapter.notifyDataSetChanged(); });
            }
            @Override public void onCompleted(DownloadTracker.Entry entry) {
                runOnUiThread(() -> {
                    Toast.makeText(ExploreActivity.this, R.string.download_complete, Toast.LENGTH_SHORT).show();
                    if (searchAdapter != null) searchAdapter.notifyDataSetChanged();
                });
            }
            @Override public void onFailed(DownloadTracker.Entry entry) {
                runOnUiThread(() -> {
                    Toast.makeText(ExploreActivity.this, R.string.download_failed, Toast.LENGTH_LONG).show();
                    if (searchAdapter != null) searchAdapter.notifyDataSetChanged();
                });
            }
            @Override public void onCancelled(DownloadTracker.Entry entry) {
                runOnUiThread(() -> { if (searchAdapter != null) searchAdapter.notifyDataSetChanged(); });
            }
        }, app.getIconUrl(), app.getApkSize());
        if (!alreadyTracked) DownloadTracker.get().markHomePlaceholderOwned(app.getPackageName());
        Toast.makeText(this, getString(R.string.added_to_main_download_started), Toast.LENGTH_SHORT).show();
    }

    private class TileAdapter extends RecyclerView.Adapter<TileAdapter.VH> {
        private final List<ExploreApp> items = new ArrayList<>();
        void submit(List<ExploreApp> next) {
            items.clear();
            if (next != null) items.addAll(next);
            notifyDataSetChanged();
        }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_explore_tile, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            ExploreApp app = items.get(position);
            h.name.setText(app.getName());
            if (app.getIconUrl() != null) IconLoader.load(h.icon, app.getIconUrl(), iconOptions);
            else h.icon.setImageDrawable(com.opensourcefinder.app.ui.IconLoader.empty(ExploreActivity.this));
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
                    if (pos != RecyclerView.NO_POSITION) openDetail(items.get(pos));
                });
            }
        }
    }

    private class ExploreAdapter extends RecyclerView.Adapter<ExploreAdapter.VH> {
        private final AsyncListDiffer<ExploreApp> differ = new AsyncListDiffer<>(this,
                new DiffUtil.ItemCallback<ExploreApp>() {
                    @Override public boolean areItemsTheSame(@NonNull ExploreApp oldItem, @NonNull ExploreApp newItem) {
                        return java.util.Objects.equals(oldItem.getPackageName(), newItem.getPackageName());
                    }

                    @Override public boolean areContentsTheSame(@NonNull ExploreApp oldItem, @NonNull ExploreApp newItem) {
                        return java.util.Objects.equals(oldItem.getName(), newItem.getName())
                                && java.util.Objects.equals(oldItem.getLatestVersionName(), newItem.getLatestVersionName())
                                && java.util.Objects.equals(oldItem.getIconUrl(), newItem.getIconUrl())
                                && oldItem.isInstalled() == newItem.isInstalled()
                                && oldItem.getInstalledVersionCode() == newItem.getInstalledVersionCode();
                    }
                });

        ExploreAdapter() {
            setHasStableIds(true);
        }

        void submit(List<ExploreApp> next) {
            differ.submitList(new ArrayList<>(next == null ? java.util.Collections.emptyList() : next), () -> {
                if (searchRecycler != null) {
                    searchRecycler.stopScroll();
                    searchRecycler.scrollToPosition(0);
                }
            });
        }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_explore, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            ExploreApp app = differ.getCurrentList().get(position);
            h.name.setText(app.getName());
            boolean favorite = FavoriteManager.isFavorite(ExploreActivity.this, app.getPackageName());
            h.favorite.setImageResource(favorite ? com.opensourcefinder.app.ui.TablerIconRes.get(ExploreActivity.this, "ic_star_filled") : com.opensourcefinder.app.ui.TablerIconRes.get(ExploreActivity.this, "ic_star"));
            String sub = app.getLatestVersionName() != null ? ("v" + app.getLatestVersionName()) : "";
            if (app.getRepoName() != null) sub += (sub.isEmpty() ? "" : " · ") + app.getRepoName();
            h.summary.setText(sub);
            boolean upToDate = com.opensourcefinder.app.install.InstallState.ui(app) == com.opensourcefinder.app.install.InstallState.Ui.UP_TO_DATE;
            h.install.setEnabled(!upToDate);
            h.install.setText(!app.isInstalled() ? getString(R.string.install) : (upToDate ? getString(R.string.up_to_date) : getString(R.string.update)));
            h.install.setAlpha(1f);
            DownloadTracker.Entry dl = DownloadTracker.get().getEntry(app.getPackageName());
            if (dl != null && dl.state == DownloadTracker.State.DOWNLOADING) {
                h.install.setEnabled(false);
                h.install.setAlpha(0.6f);
                h.install.setText(getString(R.string.downloading_percent, dl.progress));
            } else if (dl != null && dl.state == DownloadTracker.State.COMPLETED) {
                h.install.setEnabled(false);
                h.install.setAlpha(0.6f);
                h.install.setText(getString(R.string.waiting_for_install));
            } else if (dl != null && dl.state == DownloadTracker.State.INSTALLED) {
                h.install.setEnabled(false);
                h.install.setAlpha(1f);
                h.install.setText(getString(R.string.up_to_date));
            }
            if (app.getIconUrl() != null) IconLoader.load(h.icon, app.getIconUrl(), iconOptions);
            else h.icon.setImageDrawable(com.opensourcefinder.app.ui.IconLoader.empty(ExploreActivity.this));
        }
        @Override public int getItemCount() { return differ.getCurrentList().size(); }
        @Override public long getItemId(int position) {
            String packageName = differ.getCurrentList().get(position).getPackageName();
            return packageName == null ? RecyclerView.NO_ID : packageName.hashCode();
        }
        class VH extends RecyclerView.ViewHolder {
            final TextView name, summary;
            final MaterialButton install;
            final ImageView icon;
            final ImageButton favorite;
            VH(View v) {
                super(v);
                name = v.findViewById(R.id.exploreName);
                summary = v.findViewById(R.id.exploreSummary);
                install = v.findViewById(R.id.exploreInstall);
                icon = v.findViewById(R.id.exploreIcon);
                IconShapeHelper.apply(icon);
                favorite = v.findViewById(R.id.exploreFavorite);
                favorite.setOnClickListener(view -> {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        ExploreApp a = differ.getCurrentList().get(pos);
                        boolean now = FavoriteManager.toggle(ExploreActivity.this, a.getPackageName());
                        favorite.setImageResource(now ? com.opensourcefinder.app.ui.TablerIconRes.get(ExploreActivity.this, "ic_star_filled") : com.opensourcefinder.app.ui.TablerIconRes.get(ExploreActivity.this, "ic_star"));
                    }
                });
                install.setOnClickListener(view -> {
                    int pos = getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    startDownload(differ.getCurrentList().get(pos));
                    install.setEnabled(false);
                    install.setAlpha(0.6f);
                    install.setText(getString(R.string.downloading_percent, 0));
                });
                itemView.setOnClickListener(view -> {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) openDetail(differ.getCurrentList().get(pos));
                });
            }
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        destroyed = true;
        catalogLoading = false;
        if (pendingFilter != null) mainHandler.removeCallbacks(pendingFilter);
        executor.shutdownNow();
    }
    private int sectionTitleId(int sectionId) {
        if (sectionId == R.id.sectionNew) return R.id.sectionNewTitle;
        if (sectionId == R.id.sectionUpdated) return R.id.sectionUpdatedTitle;
        return R.id.sectionPopularTitle;
    }

    private int sectionArrowId(int sectionId) {
        if (sectionId == R.id.sectionNew) return R.id.sectionNewArrow;
        if (sectionId == R.id.sectionUpdated) return R.id.sectionUpdatedArrow;
        return R.id.sectionPopularArrow;
    }

    private int sectionRecyclerId(int sectionId) {
        if (sectionId == R.id.sectionNew) return R.id.sectionNewRecycler;
        if (sectionId == R.id.sectionUpdated) return R.id.sectionUpdatedRecycler;
        return R.id.sectionPopularRecycler;
    }

}
