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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
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

public class SearchActivity extends BaseActivity {
    public static final String EXTRA_TRANSITION_FORWARD =
            "search_transition_forward";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<ExploreApp> all = new ArrayList<>();
    private ResultAdapter resultAdapter;
    private RecyclerView resultRecycler;
    private View loadingPanel;
    private TextView empty;
    private TextInputEditText searchInput;
    private Runnable pendingFilter;
    private String lastQuery = "";
    private volatile boolean destroyed = false;
    private long filterGeneration = 0L;
    private boolean catalogLoaded = false;

    private RequestOptions iconOptions;
    private View categoriesScroll;
    private LinearLayout categoriesContainer;
    private String activeCategoryTitle;
    private String[] activeCategoryKeywords;

    static final class SearchCategory {
        final int titleRes;
        final String icon;
        final String[] keywords;
        SearchCategory(int titleRes, String icon, String[] keywords) {
            this.titleRes = titleRes;
            this.icon = icon;
            this.keywords = keywords;
        }
    }

    static final SearchCategory[] SEARCH_CATEGORIES = {
            new SearchCategory(R.string.search_category_action_game, "ic_device_gamepad_2", new String[]{"action game", "arcade"}),
            new SearchCategory(R.string.search_category_ai_chat, "ic_sparkles", new String[]{"artificial intelligence", "ai chat", "ai assistant", "large language model", "llm"}),
            new SearchCategory(R.string.search_category_alarm_clock, "ic_alarm", new String[]{"alarm"}),
            new SearchCategory(R.string.search_category_app_manager, "ic_apps", new String[]{"app manager", "package manager", "application manager"}),
            new SearchCategory(R.string.search_category_app_store, "ic_building_store", new String[]{"app store", "software store", "f-droid client", "package installer"}),
            new SearchCategory(R.string.search_category_battery, "ic_battery", new String[]{"battery"}),
            new SearchCategory(R.string.search_category_board_game, "ic_layout_grid", new String[]{"board game", "chess", "checkers"}),
            new SearchCategory(R.string.search_category_bookmark, "ic_bookmark", new String[]{"bookmark"}),
            new SearchCategory(R.string.search_category_browser, "ic_world", new String[]{"browser", "web browser"}),
            new SearchCategory(R.string.search_category_calculator, "ic_calculator", new String[]{"calculator"}),
            new SearchCategory(R.string.search_category_calendar, "ic_calendar", new String[]{"calendar", "agenda", "scheduler"}),
            new SearchCategory(R.string.search_category_camera, "ic_camera", new String[]{"camera"}),
            new SearchCategory(R.string.search_category_card_game, "ic_cards", new String[]{"card game", "solitaire", "poker"}),
            new SearchCategory(R.string.search_category_cast, "ic_device_tv", new String[]{"cast", "chromecast", "screen mirroring"}),
            new SearchCategory(R.string.search_category_casual_game, "ic_puzzle", new String[]{"casual game", "puzzle game"}),
            new SearchCategory(R.string.search_category_clock, "ic_clock", new String[]{"clock", "world clock"}),
            new SearchCategory(R.string.search_category_cloud_storage, "ic_cloud", new String[]{"cloud storage", "file sync", "backup sync"}),
            new SearchCategory(R.string.search_category_code_forge, "ic_code", new String[]{"git", "code hosting", "forge", "repository manager"}),
            new SearchCategory(R.string.search_category_connectivity, "ic_antenna_bars_5", new String[]{"connectivity", "network", "wifi", "vpn"}),
            new SearchCategory(R.string.search_category_contact, "ic_address_book", new String[]{"contact", "address book"}),
            new SearchCategory(R.string.search_category_development, "ic_terminal_2", new String[]{"development", "developer tools", "ide", "programming"}),
            new SearchCategory(R.string.search_category_dice, "ic_dice", new String[]{"dice"}),
            new SearchCategory(R.string.search_category_diet, "ic_salad", new String[]{"diet", "nutrition", "calorie"}),
            new SearchCategory(R.string.search_category_dns_hosts, "ic_server", new String[]{"dns", "hosts file", "ad blocker"}),
            new SearchCategory(R.string.search_category_download, "ic_download", new String[]{"download manager", "torrent"}),
            new SearchCategory(R.string.search_category_draw, "ic_pencil", new String[]{"drawing", "sketch", "paint"}),
            new SearchCategory(R.string.search_category_ebook_reader, "ic_book", new String[]{"ebook", "e-book", "epub", "reader"}),
            new SearchCategory(R.string.search_category_educational_game, "ic_school", new String[]{"educational game", "learning game"}),
            new SearchCategory(R.string.search_category_email, "ic_mail", new String[]{"email", "mail client"}),
            new SearchCategory(R.string.search_category_emulator, "ic_device_gamepad", new String[]{"emulator", "emulation"}),
            new SearchCategory(R.string.search_category_file_encryption, "ic_lock", new String[]{"encryption", "vault", "password manager"}),
            new SearchCategory(R.string.search_category_file_manager, "ic_folder", new String[]{"file manager", "file explorer"}),
            new SearchCategory(R.string.search_category_file_transfer, "ic_file_upload", new String[]{"file transfer", "ftp", "sftp", "share files"}),
            new SearchCategory(R.string.search_category_finance_manager, "ic_coin", new String[]{"finance", "budget", "expense", "money manager"}),
            new SearchCategory(R.string.search_category_firewall, "ic_shield", new String[]{"firewall"}),
            new SearchCategory(R.string.search_category_flashlight, "ic_flashlight", new String[]{"flashlight", "torch"}),
            new SearchCategory(R.string.search_category_forum, "ic_message_circle", new String[]{"forum", "discussion board"}),
            new SearchCategory(R.string.search_category_gallery, "ic_photo", new String[]{"gallery", "photo viewer"}),
            new SearchCategory(R.string.search_category_game_helper, "ic_tool", new String[]{"game helper", "game utility"}),
            new SearchCategory(R.string.search_category_graphics, "ic_palette", new String[]{"graphics", "image editor", "design tool"}),
            new SearchCategory(R.string.search_category_habit_tracker, "ic_target", new String[]{"habit tracker", "habit"}),
            new SearchCategory(R.string.search_category_health_manager, "ic_first_aid_kit", new String[]{"health", "fitness", "workout"}),
            new SearchCategory(R.string.search_category_icon_pack, "ic_stack_2", new String[]{"icon pack", "icon theme"}),
            new SearchCategory(R.string.search_category_internet, "ic_wifi", new String[]{"internet", "network client"}),
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        boolean transitionForward = getIntent().getBooleanExtra(EXTRA_TRANSITION_FORWARD, true);
        MotionHelper.applyActivityTransition(this, transitionForward);
        setContentView(R.layout.activity_search);
        MotionHelper.animateActivityContent(this);
        MotionHelper.keepBottomNavStatic(this);
        ThemeManager.applyWindow(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("");
        BottomNavHelper.bind(this, R.id.nav_explore);

        int circleIcon = TablerIconRes.get(this, "ic_circle");
        iconOptions = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .centerCrop()
                .dontAnimate()
                .override(112, 112)
                .placeholder(circleIcon)
                .error(circleIcon);

        empty = findViewById(R.id.emptyView);
        loadingPanel = findViewById(R.id.loadingPanel);
        categoriesScroll = findViewById(R.id.searchCategoriesScroll);
        categoriesContainer = findViewById(R.id.searchCategoriesContainer);

        resultRecycler = findViewById(R.id.recyclerView);
        LinearLayoutManager glm = new LinearLayoutManager(this);
        glm.setInitialPrefetchItemCount(8);
        resultRecycler.setLayoutManager(glm);
        resultRecycler.setHasFixedSize(true);
        resultRecycler.setItemViewCacheSize(10);
        MotionHelper.setupListAnimator(resultRecycler);
        resultAdapter = new ResultAdapter();
        resultRecycler.setAdapter(resultAdapter);

        searchInput = findViewById(R.id.searchInput);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingFilter != null) mainHandler.removeCallbacks(pendingFilter);
                final String q = s == null ? "" : s.toString();
                pendingFilter = () -> applyFilter(q);
                mainHandler.postDelayed(pendingFilter, 160);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            applyFilter(searchInput.getText() == null ? "" : searchInput.getText().toString());
            return false;
        });
        searchInput.requestFocus();

        bindSearchCategories();
        showCategories(true);

        loadCatalog();
    }

    private void bindSearchCategories() {
        if (categoriesContainer == null) return;
        categoriesContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (SearchCategory category : SEARCH_CATEGORIES) {
            View row = inflater.inflate(R.layout.item_search_category_row, categoriesContainer, false);
            ImageView icon = row.findViewById(R.id.catIcon);
            if (icon != null) icon.setImageResource(TablerIconRes.get(this, category.icon));
            TextView title = row.findViewById(R.id.catTitle);
            if (title != null) title.setText(getString(category.titleRes));
            row.setOnClickListener(v -> openCategory(getString(category.titleRes), category.keywords));
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

    private final Runnable installEventsListener = () -> { refreshInstalledStatesOnly(); if (resultAdapter != null) resultAdapter.notifyDataSetChanged(); };

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
                if (destroyed || resultAdapter == null) return;
                resultAdapter.notifyDataSetChanged();
            });
        });
    }

    private void loadCatalog() {
        if (loadingPanel != null) loadingPanel.setVisibility(View.VISIBLE);
        final android.content.Context context = getApplicationContext();
        executor.execute(() -> {
            try {
                List<InstalledAppInfo> installed = AppScanner.listUserInstalledApps(context);
                Set<String> pkgs = new HashSet<>();
                Map<String, Long> installedVersions = new HashMap<>();
                for (InstalledAppInfo a : installed) {
                    pkgs.add(a.getPackageName());
                    installedVersions.put(a.getPackageName(), a.getVersionCode());
                }
                List<ExploreApp> catalog = new FdroidIndexManager(context).loadExploreCatalog(pkgs, null);
                for (ExploreApp app : catalog) {
                    Long versionCode = installedVersions.get(app.getPackageName());
                    boolean isInstalled = versionCode != null;
                    app.setInstalled(isInstalled);
                    app.setInstalledVersionCode(isInstalled ? versionCode : 0L);
                }
                java.util.Map<String, com.opensourcefinder.app.install.InstallState.Info> liveAll = com.opensourcefinder.app.install.InstallState.readAll(getApplicationContext());
                for (ExploreApp app : catalog) com.opensourcefinder.app.install.InstallState.refresh(app, liveAll.get(app.getPackageName()));
                ui(() -> {
                    if (destroyed) return;
                    all.clear();
                    all.addAll(catalog == null ? new ArrayList<>() : catalog);
                    catalogLoaded = true;
                    if (loadingPanel != null) loadingPanel.setVisibility(View.GONE);
                    applyFilter(lastQuery);
                });
            } catch (Exception e) {
                ui(() -> {
                    if (destroyed) return;
                    if (loadingPanel != null) loadingPanel.setVisibility(View.GONE);
                    Toast.makeText(SearchActivity.this, getString(R.string.catalog_error), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void applyFilter(String q) {
        if (destroyed) return;
        final long requestGeneration = ++filterGeneration;
        lastQuery = q == null ? "" : q;
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
        final boolean categoryMode = activeCategoryKeywords != null && activeCategoryTitle != null
                && activeCategoryTitle.trim().toLowerCase(Locale.ROOT).equals(query);
        final String[] categoryKeywords = activeCategoryKeywords;
        if (!categoryMode) {
            activeCategoryTitle = null;
            activeCategoryKeywords = null;
        }
        final List<ExploreApp> base = new ArrayList<>(all);
        executor.execute(() -> {
            List<ExploreApp> next = new ArrayList<>();
            for (ExploreApp app : base) {
                boolean matched = categoryMode ? matchesCategory(app, categoryKeywords) : matches(app, query);
                if (matched) next.add(app);
            }
            ui(() -> {
                if (requestGeneration != filterGeneration) return;
                resultAdapter.submitList(next, () -> {
                    if (resultRecycler != null) {
                        resultRecycler.stopScroll();
                        resultRecycler.scrollToPosition(0);
                    }
                });
                if (empty != null) {
                    empty.setVisibility(catalogLoaded && next.isEmpty() ? View.VISIBLE : View.GONE);
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
        StringBuilder sb = new StringBuilder();
        sb.append(' ').append(String.valueOf(app.getName()));
        sb.append(' ').append(String.valueOf(app.getSummary()));
        sb.append(' ').append(String.valueOf(app.getDescription()));
        if (app.getCategories() != null) {
            for (String category : app.getCategories()) sb.append(' ').append(String.valueOf(category));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
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
        match.setNotInstalled(!app.isInstalled());
        match.setUpdateAvailable(com.opensourcefinder.app.install.InstallState.ui(app) != com.opensourcefinder.app.install.InstallState.Ui.UP_TO_DATE);
        MatchStore.upsert(this, match);
        String fileName = (app.getName() != null ? app.getName() : app.getPackageName())
                + "-" + (app.getLatestVersionName() != null ? app.getLatestVersionName() : "latest") + ".apk";
        boolean finalAlreadyTracked = alreadyTracked;
        DownloadTracker.get().start(this, app.getPackageName(), app.getDownloadUrl(), fileName, new DownloadTracker.Listener() {
            @Override public void onProgress(DownloadTracker.Entry entry) {
                runOnUiThread(() -> { if (resultAdapter != null) resultAdapter.notifyDataSetChanged(); });
            }
            @Override public void onCompleted(DownloadTracker.Entry entry) {
                ui(() -> {
                    Toast.makeText(SearchActivity.this, R.string.download_complete, Toast.LENGTH_SHORT).show();
                    if (resultAdapter != null) resultAdapter.notifyDataSetChanged();
                });
            }
            @Override public void onFailed(DownloadTracker.Entry entry) {
                ui(() -> {
                    Toast.makeText(SearchActivity.this, R.string.download_failed, Toast.LENGTH_LONG).show();
                    if (resultAdapter != null) resultAdapter.notifyDataSetChanged();
                });
            }
            @Override public void onCancelled(DownloadTracker.Entry entry) {
                ui(() -> { if (resultAdapter != null) resultAdapter.notifyDataSetChanged(); });
            }
        }, app.getIconUrl(), app.getApkSize());
        if (!finalAlreadyTracked) DownloadTracker.get().markHomePlaceholderOwned(app.getPackageName());
        Toast.makeText(this, getString(R.string.added_to_main_download_started), Toast.LENGTH_SHORT).show();
    }

    private void ui(Runnable r) {
        if (destroyed) return;
        runOnUiThread(() -> { if (!destroyed) r.run(); });
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        destroyed = true;
        if (pendingFilter != null) mainHandler.removeCallbacks(pendingFilter);
        executor.shutdownNow();
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
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_explore, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            ExploreApp app = getItem(position);
            h.name.setText(app.getName());
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
            else h.icon.setImageDrawable(com.opensourcefinder.app.ui.IconLoader.empty(SearchActivity.this));
        }

        @Override public long getItemId(int position) {
            String packageName = getItem(position).getPackageName();
            return packageName == null ? RecyclerView.NO_ID : packageName.hashCode();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView name, summary;
            final MaterialButton install;
            final ImageView icon;
            VH(View v) {
                super(v);
                name = v.findViewById(R.id.exploreName);
                summary = v.findViewById(R.id.exploreSummary);
                install = v.findViewById(R.id.exploreInstall);
                icon = v.findViewById(R.id.exploreIcon);
                IconShapeHelper.apply(icon);
                install.setOnClickListener(view -> {
                    int pos = getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    startDownload(getItem(pos));
                    install.setEnabled(false);
                    install.setAlpha(0.6f);
                    install.setText(getString(R.string.downloading_percent, 0));
                });
                itemView.setOnClickListener(view -> {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) openDetail(getItem(pos));
                });
            }
        }
    }
}
