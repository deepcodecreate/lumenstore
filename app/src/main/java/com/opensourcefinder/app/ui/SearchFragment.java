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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchFragment extends Fragment {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<ExploreApp> all = new ArrayList<>();

    private ResultAdapter resultAdapter;
    private RecyclerView resultRecycler;
    private View loadingPanel;
    private TextView empty;
    private TextInputEditText searchInput;
    private View categoriesScroll;
    private LinearLayout categoriesContainer;
    private Runnable pendingFilter;
    private String lastQuery = "";
    private long filterGeneration = 0L;
    private boolean catalogLoaded = false;
    private volatile boolean destroyed = false;
    private RequestOptions iconOptions;
    private String activeCategoryTitle;
    private String[] activeCategoryKeywords;
    private OnBackPressedCallback backCallback;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View embeddedNav = view.findViewById(R.id.bottomNav);
        if (embeddedNav != null) embeddedNav.setVisibility(View.GONE);

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle("");

        int circleIcon = TablerIconRes.get(requireContext(), "ic_circle");
        iconOptions = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .centerCrop()
                .dontAnimate()
                .override(112, 112)
                .placeholder(circleIcon)
                .error(circleIcon);

        empty = view.findViewById(R.id.emptyView);
        loadingPanel = view.findViewById(R.id.loadingPanel);
        categoriesScroll = view.findViewById(R.id.searchCategoriesScroll);
        categoriesContainer = view.findViewById(R.id.searchCategoriesContainer);
        resultRecycler = view.findViewById(R.id.recyclerView);

        LinearLayoutManager manager = new LinearLayoutManager(requireContext());
        manager.setInitialPrefetchItemCount(8);
        resultRecycler.setLayoutManager(manager);
        resultRecycler.setHasFixedSize(true);
        resultRecycler.setItemViewCacheSize(10);
        MotionHelper.setupListAnimator(resultRecycler);
        resultAdapter = new ResultAdapter();
        resultRecycler.setAdapter(resultAdapter);

        backCallback = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() {
                clearSearch();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);

        searchInput = view.findViewById(R.id.searchInput);
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingFilter != null) mainHandler.removeCallbacks(pendingFilter);
                final String query = s == null ? "" : s.toString();
                pendingFilter = () -> applyFilter(query);
                mainHandler.postDelayed(pendingFilter, 160L);
            }
            @Override public void afterTextChanged(android.text.Editable s) {
                updateBackCallbackState();
            }
        });
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            applyFilter(searchInput.getText() == null ? "" : searchInput.getText().toString());
            return false;
        });
        searchInput.setOnFocusChangeListener((v, hasFocus) -> updateBackCallbackState());

        bindSearchCategories();
        showCategories(true);
        loadCatalog();
    }

    private void bindSearchCategories() {
        if (categoriesContainer == null) return;
        categoriesContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (SearchActivity.SearchCategory category : SearchActivity.SEARCH_CATEGORIES) {
            View row = inflater.inflate(R.layout.item_search_category_row, categoriesContainer, false);
            ImageView icon = row.findViewById(R.id.catIcon);
            if (icon != null) icon.setImageResource(
                    TablerIconRes.get(requireContext(), category.icon));
            TextView title = row.findViewById(R.id.catTitle);
            if (title != null) title.setText(getString(category.titleRes));
            row.setOnClickListener(v -> openCategory(
                    getString(category.titleRes), category.keywords));
            categoriesContainer.addView(row);
        }
    }

    private void openCategory(String title, String[] keywords) {
        activeCategoryTitle = title;
        activeCategoryKeywords = keywords;
        if (searchInput != null) {
            searchInput.setText(title);
            searchInput.setSelection(title.length());
        }
    }

    private void showCategories(boolean show) {
        if (categoriesScroll != null) categoriesScroll.setVisibility(show ? View.VISIBLE : View.GONE);
        if (resultRecycler != null) resultRecycler.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void updateBackCallbackState() {
        if (backCallback == null || searchInput == null) return;
        boolean hasFocus = searchInput.hasFocus();
        boolean hasText = searchInput.getText() != null && searchInput.getText().length() > 0;
        backCallback.setEnabled(hasFocus || hasText);
    }

    public void resetToInitialState() {
        if (!isAdded() || getView() == null) return;
        clearSearch();
        if (categoriesScroll instanceof androidx.core.widget.NestedScrollView) ((androidx.core.widget.NestedScrollView) categoriesScroll).smoothScrollTo(0, 0);
        if (resultRecycler != null) resultRecycler.scrollToPosition(0);
    }

    private void clearSearch() {
        activeCategoryTitle = null;
        activeCategoryKeywords = null;
        if (searchInput != null) {
            searchInput.setText("");
            searchInput.clearFocus();
            Object service = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (service instanceof android.view.inputmethod.InputMethodManager) {
                ((android.view.inputmethod.InputMethodManager) service)
                        .hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
            }
        }
        applyFilter("");
        updateBackCallbackState();
    }

    private final Runnable installEventsListener = () -> { refreshInstalledStatesOnly(); if (resultAdapter != null) resultAdapter.notifyDataSetChanged(); };

    @Override public void onPause() {
        com.opensourcefinder.app.install.InstallEvents.removeListener(installEventsListener);
        super.onPause();
    }

    @Override public void onResume() {
        super.onResume();
        com.opensourcefinder.app.install.InstallEvents.addListener(installEventsListener);
        refreshInstalledStatesOnly();
    }

    @Override public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) refreshInstalledStatesOnly();
    }

    private void refreshInstalledStatesOnly() {
        if (!isAdded() || all.isEmpty()) return;
        final android.content.Context context = requireContext().getApplicationContext();
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
            ui(() -> {
                if (destroyed || resultAdapter == null) return;
                resultAdapter.notifyDataSetChanged();
            });
        });
    }

    private void loadCatalog() {
        if (loadingPanel != null) loadingPanel.setVisibility(View.VISIBLE);
        final android.content.Context context = requireContext().getApplicationContext();
        executor.execute(() -> {
            try {
                List<InstalledAppInfo> installed = AppScanner.listUserInstalledApps(context);
                Set<String> packages = new HashSet<>();
                Map<String, Long> installedVersions = new HashMap<>();
                for (InstalledAppInfo app : installed) {
                    packages.add(app.getPackageName());
                    installedVersions.put(app.getPackageName(), app.getVersionCode());
                }

                List<ExploreApp> catalog =
                        new FdroidIndexManager(context).loadExploreCatalog(packages, null);
                for (ExploreApp app : catalog) {
                    Long version = installedVersions.get(app.getPackageName());
                    app.setInstalled(version != null);
                    app.setInstalledVersionCode(version == null ? 0L : version);
                }
                java.util.Map<String, com.opensourcefinder.app.install.InstallState.Info> liveAll = com.opensourcefinder.app.install.InstallState.readAll(context);
                for (ExploreApp app : catalog) com.opensourcefinder.app.install.InstallState.refresh(app, liveAll.get(app.getPackageName()));

                ui(() -> {
                    all.clear();
                    all.addAll(catalog == null ? new ArrayList<>() : catalog);
                    catalogLoaded = true;
                    if (loadingPanel != null) loadingPanel.setVisibility(View.GONE);
                    applyFilter(lastQuery);
                });
            } catch (Exception error) {
                ui(() -> {
                    if (loadingPanel != null) loadingPanel.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), R.string.catalog_error,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void applyFilter(String value) {
        if (destroyed) return;
        final long generation = ++filterGeneration;
        lastQuery = value == null ? "" : value;
        final String query = lastQuery.trim().toLowerCase(Locale.ROOT);

        if (query.isEmpty()) {
            activeCategoryTitle = null;
            activeCategoryKeywords = null;
            resultAdapter.submitList(new ArrayList<>());
            if (empty != null) empty.setVisibility(View.GONE);
            showCategories(true);
            return;
        }

        showCategories(false);
        final boolean categoryMode = activeCategoryTitle != null
                && activeCategoryKeywords != null
                && activeCategoryTitle.trim().toLowerCase(Locale.ROOT).equals(query);
        final String[] keywords = activeCategoryKeywords;
        if (!categoryMode) {
            activeCategoryTitle = null;
            activeCategoryKeywords = null;
        }

        final List<ExploreApp> base = new ArrayList<>(all);
        executor.execute(() -> {
            List<ExploreApp> filtered = new ArrayList<>();
            for (ExploreApp app : base) {
                if (categoryMode ? matchesCategory(app, keywords) : matches(app, query)) {
                    filtered.add(app);
                }
            }
            ui(() -> {
                if (generation != filterGeneration) return;
                resultAdapter.submitList(filtered, () -> {
                    if (resultRecycler != null) {
                        resultRecycler.stopScroll();
                        resultRecycler.scrollToPosition(0);
                    }
                });
                if (empty != null) {
                    empty.setVisibility(catalogLoaded && filtered.isEmpty()
                            ? View.VISIBLE : View.GONE);
                }
            });
        });
    }

    private static boolean matchesCategory(ExploreApp app, String[] keywords) {
        String text = searchableText(app);
        for (String keyword : keywords) if (text.contains(keyword)) return true;
        return false;
    }

    private static String searchableText(ExploreApp app) {
        if (app == null) return "";
        StringBuilder text = new StringBuilder();
        text.append(' ').append(String.valueOf(app.getName()));
        text.append(' ').append(String.valueOf(app.getSummary()));
        text.append(' ').append(String.valueOf(app.getDescription()));
        if (app.getCategories() != null) {
            for (String category : app.getCategories()) text.append(' ').append(String.valueOf(category));
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean matches(ExploreApp app, String query) {
        return (app.getName() != null
                && app.getName().toLowerCase(Locale.ROOT).contains(query))
                || (app.getPackageName() != null
                && app.getPackageName().toLowerCase(Locale.ROOT).contains(query))
                || (app.getSummary() != null
                && app.getSummary().toLowerCase(Locale.ROOT).contains(query));
    }

    private void openDetail(ExploreApp app) {
        Intent intent = new Intent(requireContext(), ExploreDetailActivity.class);
        intent.putExtra(ExploreDetailActivity.EXTRA_EXPLORE_APP, app);
        startActivity(intent);
    }

    private void startDownload(ExploreApp app) {
        if (app.isGithubRelease() && app.getDownloadUrl() == null) {
            openDetail(app);
            return;
        }
        if (app.getDownloadUrl() == null) {
            Toast.makeText(requireContext(), R.string.download_link_missing,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        boolean alreadyTracked = false;
        for (MatchedApp existing : MatchStore.get(requireContext())) {
            if (app.getPackageName().equals(existing.getPackageName())) {
                alreadyTracked = true;
                break;
            }
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
        match.setDescription(app.getDescription() != null
                ? app.getDescription() : app.getSummary());
        match.setNotInstalled(!app.isInstalled());
        match.setUpdateAvailable(com.opensourcefinder.app.install.InstallState.ui(app) != com.opensourcefinder.app.install.InstallState.Ui.UP_TO_DATE);
        MatchStore.upsert(requireContext(), match);

        String fileName = (app.getName() != null ? app.getName() : app.getPackageName())
                + "-" + (app.getLatestVersionName() != null
                ? app.getLatestVersionName() : "latest") + ".apk";
        final boolean wasTracked = alreadyTracked;
        DownloadTracker.get().start(requireContext(), app.getPackageName(),
                app.getDownloadUrl(), fileName, new DownloadTracker.Listener() {
                    @Override public void onProgress(DownloadTracker.Entry entry) {
                        ui(() -> { if (resultAdapter != null) resultAdapter.notifyDataSetChanged(); });
                    }
                    @Override public void onCompleted(DownloadTracker.Entry entry) {
                        ui(() -> {
                            Toast.makeText(requireContext(), R.string.download_complete,
                                    Toast.LENGTH_SHORT).show();
                            if (resultAdapter != null) resultAdapter.notifyDataSetChanged();
                        });
                    }
                    @Override public void onFailed(DownloadTracker.Entry entry) {
                        ui(() -> {
                            Toast.makeText(requireContext(), R.string.download_failed,
                                    Toast.LENGTH_LONG).show();
                            if (resultAdapter != null) resultAdapter.notifyDataSetChanged();
                        });
                    }
                    @Override public void onCancelled(DownloadTracker.Entry entry) {
                        ui(() -> { if (resultAdapter != null) resultAdapter.notifyDataSetChanged(); });
                    }
                }, app.getIconUrl(), app.getApkSize());
        if (!wasTracked) {
            DownloadTracker.get().markHomePlaceholderOwned(app.getPackageName());
        }
        Toast.makeText(requireContext(), R.string.added_to_main_download_started,
                Toast.LENGTH_SHORT).show();
    }

    private void ui(Runnable action) {
        if (!isAdded() || destroyed) return;
        requireActivity().runOnUiThread(() -> {
            if (isAdded() && !destroyed) action.run();
        });
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        if (pendingFilter != null) mainHandler.removeCallbacks(pendingFilter);
        executor.shutdownNow();
        super.onDestroy();
    }

    private class ResultAdapter extends ListAdapter<ExploreApp, ResultAdapter.VH> {
        ResultAdapter() {
            super(new DiffUtil.ItemCallback<ExploreApp>() {
                @Override public boolean areItemsTheSame(@NonNull ExploreApp a, @NonNull ExploreApp b) {
                    return Objects.equals(a.getPackageName(), b.getPackageName());
                }

                @Override public boolean areContentsTheSame(@NonNull ExploreApp a, @NonNull ExploreApp b) {
                    return Objects.equals(a.getName(), b.getName())
                            && Objects.equals(a.getLatestVersionName(), b.getLatestVersionName())
                            && Objects.equals(a.getIconUrl(), b.getIconUrl())
                            && a.isInstalled() == b.isInstalled()
                            && a.getInstalledVersionCode() == b.getInstalledVersionCode();
                }
            });
            setHasStableIds(true);
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_explore, parent, false);
            return new VH(view);
        }

        @Override public void onBindViewHolder(@NonNull VH holder, int position) {
            ExploreApp app = getItem(position);
            holder.name.setText(app.getName());
            String subtitle = app.getLatestVersionName() == null
                    ? "" : "v" + app.getLatestVersionName();
            if (app.getRepoName() != null) {
                subtitle += (subtitle.isEmpty() ? "" : " · ") + app.getRepoName();
            }
            holder.summary.setText(subtitle);
            boolean upToDate = com.opensourcefinder.app.install.InstallState.ui(app) == com.opensourcefinder.app.install.InstallState.Ui.UP_TO_DATE;
            holder.install.setEnabled(!upToDate);
            holder.install.setText(!app.isInstalled() ? getString(R.string.install)
                    : (upToDate ? getString(R.string.up_to_date)
                    : getString(R.string.update)));
            holder.install.setAlpha(1f);
            DownloadTracker.Entry dl = DownloadTracker.get().getEntry(app.getPackageName());
            if (dl != null && dl.state == DownloadTracker.State.DOWNLOADING) {
                holder.install.setEnabled(false);
                holder.install.setAlpha(0.6f);
                holder.install.setText(getString(R.string.downloading_percent, dl.progress));
            } else if (dl != null && dl.state == DownloadTracker.State.COMPLETED) {
                holder.install.setEnabled(false);
                holder.install.setAlpha(0.6f);
                holder.install.setText(getString(R.string.waiting_for_install));
            } else if (dl != null && dl.state == DownloadTracker.State.INSTALLED) {
                holder.install.setEnabled(false);
                holder.install.setAlpha(1f);
                holder.install.setText(getString(R.string.up_to_date));
            }
            if (app.getIconUrl() != null) {
                IconLoader.load(holder.icon, app.getIconUrl(), iconOptions);
            } else {
                holder.icon.setImageDrawable(com.opensourcefinder.app.ui.IconLoader.empty(requireContext()));
            }
        }

        @Override public long getItemId(int position) {
            String packageName = getItem(position).getPackageName();
            return packageName == null ? RecyclerView.NO_ID : packageName.hashCode();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView summary;
            final MaterialButton install;
            final ImageView icon;

            VH(View view) {
                super(view);
                name = view.findViewById(R.id.exploreName);
                summary = view.findViewById(R.id.exploreSummary);
                install = view.findViewById(R.id.exploreInstall);
                icon = view.findViewById(R.id.exploreIcon);
                IconShapeHelper.apply(icon);
                install.setOnClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (position == RecyclerView.NO_POSITION) return;
                    startDownload(getItem(position));
                    install.setEnabled(false);
                    install.setAlpha(0.6f);
                    install.setText(getString(R.string.downloading_percent, 0));
                });
                itemView.setOnClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) openDetail(getItem(position));
                });
            }
        }
    }
}
