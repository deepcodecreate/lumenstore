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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.download.DownloadTracker;
import com.opensourcefinder.app.model.ExploreApp;
import com.opensourcefinder.app.model.InstalledAppInfo;
import com.opensourcefinder.app.model.MatchedApp;
import com.opensourcefinder.app.scanner.AppScanner;
import com.opensourcefinder.app.scanner.FdroidIndexManager;
import com.opensourcefinder.app.scanner.MatchStore;
import android.animation.ObjectAnimator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExploreFragment extends Fragment {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<ExploreApp> all = new ArrayList<>();
    private final List<ExploreApp> newest = new ArrayList<>();
    private final List<ExploreApp> updated = new ArrayList<>();
    private final List<ExploreApp> popular = new ArrayList<>();
    private final List<ExploreApp> lumenApps = new ArrayList<>();
    private ExploreAdapter searchAdapter;
    private View exploreScroll;
    private View loadingPanel;
    private RecyclerView searchRecycler;
    private LinearLayout categoriesContainer;
    private TextView empty;
    private MaterialToolbar toolbar;
    private TextView storeTitle;
    private androidx.activity.OnBackPressedCallback categoryBackCallback;
    private String lastQuery = "";
    private MaterialRefreshLayout exploreRefresh;
    private List<ExploreApp> activeCategoryApps;
    private volatile boolean destroyed = false;
    private volatile boolean catalogLoading = false;
    private long filterGeneration = 0L;
    private int catalogGeneration = 0;
    private RecyclerView.ItemAnimator searchItemAnimator;

    private RequestOptions iconOptions;
    private final List<android.animation.Animator> heroAnimators = new ArrayList<>();
    private final FuzzySearch fuzzy = new FuzzySearch();
    private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingFilter;
    private android.widget.EditText searchInput;
    private ImageView searchClear;
    private ImageView searchIcon;
    private com.google.android.material.card.MaterialCardView searchCard;
    private android.animation.ValueAnimator searchStrokeAnimator;
    private boolean entrancePlayed = false;

    private View root;

    private final DownloadTracker.Listener downloadSyncListener = new DownloadTracker.Listener() {
        @Override public void onProgress(DownloadTracker.Entry entry) { ui(ExploreFragment.this::refreshDownloadButtons); }
        @Override public void onCompleted(DownloadTracker.Entry entry) { ui(ExploreFragment.this::refreshDownloadButtons); }
        @Override public void onFailed(DownloadTracker.Entry entry) { ui(ExploreFragment.this::refreshDownloadButtons); }
        @Override public void onCancelled(DownloadTracker.Entry entry) { ui(ExploreFragment.this::refreshDownloadButtons); }
    };

    private void refreshDownloadButtons() {
        if (searchAdapter != null) searchAdapter.notifyDataSetChanged();
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_explore, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        root = view;
        int circleIcon = com.opensourcefinder.app.ui.TablerIconRes.get(requireContext(), "ic_circle");
        iconOptions = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .centerCrop()
                .dontAnimate()
                .override(112, 112)
                .placeholder(circleIcon)
                .error(circleIcon);
        toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle("");
        toolbar.setNavigationIcon(null);

        storeTitle = view.findViewById(R.id.storeTitle);
        if (storeTitle != null) storeTitle.setText(getString(R.string.open_store_title));

        empty = view.findViewById(R.id.emptyView);
        exploreScroll = view.findViewById(R.id.exploreScroll);
        loadingPanel = view.findViewById(R.id.loadingPanel);
        exploreRefresh = view.findViewById(R.id.exploreRefresh);
        if (exploreRefresh != null) {
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
        categoriesContainer = view.findViewById(R.id.categoriesContainer);
        setupHeroShapes(view);

        ((TextView) view.findViewById(R.id.sectionNew).findViewById(R.id.sectionNewTitle)).setText(getString(R.string.new_apps));
        ((TextView) view.findViewById(R.id.sectionUpdated).findViewById(R.id.sectionUpdatedTitle)).setText(getString(R.string.recently_updated));
        ((TextView) view.findViewById(R.id.sectionPopular).findViewById(R.id.sectionPopularTitle)).setText(getString(R.string.popular));
        ((TextView) view.findViewById(R.id.sectionGames).findViewById(R.id.sectionGamesTitle)).setText(getString(R.string.lumen_apps));

        setupHorizontal(R.id.sectionNew);
        setupHorizontal(R.id.sectionUpdated);
        setupHorizontal(R.id.sectionPopular);
        setupHorizontal(R.id.sectionGames);

        searchRecycler = view.findViewById(R.id.recyclerView);
        LinearLayoutManager glm = new LinearLayoutManager(requireContext());
        glm.setInitialPrefetchItemCount(8);
        searchRecycler.setLayoutManager(glm);
        searchRecycler.setHasFixedSize(true);
        searchRecycler.setItemViewCacheSize(10);
        MotionHelper.setupListAnimator(searchRecycler);
        searchItemAnimator = searchRecycler.getItemAnimator();
        searchAdapter = new ExploreAdapter();
        searchRecycler.setAdapter(searchAdapter);

        searchRecycler.setVisibility(View.INVISIBLE);
        searchRecycler.post(() -> {
            if (isAdded() && searchRecycler != null) searchRecycler.setVisibility(View.GONE);
        });

         loadCatalog(false);

        categoryBackCallback = new androidx.activity.OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() {
                if (activeCategoryApps == null && searchInput != null) clearSearch();
                else exitCategory();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), categoryBackCallback);
        setupSearch(view);

        DownloadTracker.get().addGlobalListener(downloadSyncListener);
    }

    @Override public void onDestroyView() {
        if (pendingFilter != null) searchHandler.removeCallbacks(pendingFilter);
        if (searchStrokeAnimator != null) searchStrokeAnimator.cancel();
        DownloadTracker.get().removeGlobalListener(downloadSyncListener);
        for (android.animation.Animator animator : heroAnimators) animator.cancel();
        heroAnimators.clear();
        entrancePlayed = false;
        super.onDestroyView();
    }

    private void setupSearch(View view) {
        searchCard = view.findViewById(R.id.exploreSearchCard);
        searchInput = view.findViewById(R.id.exploreSearchInput);
        searchClear = view.findViewById(R.id.exploreSearchClear);
        searchIcon = view.findViewById(R.id.exploreSearchIcon);
        if (searchInput == null) return;
        KeyboardFocusHelper.attach(searchInput);
        searchClear.setImageResource(TablerIconRes.get(requireContext(), "ic_x"));
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                final String value = s == null ? "" : s.toString();
                setSearchClearVisible(!value.isEmpty());
                if (pendingFilter != null) searchHandler.removeCallbacks(pendingFilter);
                pendingFilter = () -> applyFilter(value);
                searchHandler.postDelayed(pendingFilter, value.isEmpty() ? 0L : 140L);
            }
            @Override public void afterTextChanged(android.text.Editable s) {
                updateBackState();
            }
        });
        searchInput.setOnFocusChangeListener((v, hasFocus) -> {
            animateSearchFocus(hasFocus);
            updateBackState();
        });
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                if (pendingFilter != null) searchHandler.removeCallbacks(pendingFilter);
                applyFilter(searchInput.getText() == null ? "" : searchInput.getText().toString());
                hideKeyboard();
                return true;
            }
            return false;
        });
        searchClear.setOnClickListener(v -> {
            AppPreferences.performHaptic(v);
            searchInput.setText("");
            searchInput.requestFocus();
        });
        if (searchRecycler != null) {
            searchRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard();
                }
            });
        }
    }

    private void updateBackState() {
        if (categoryBackCallback == null) return;
        boolean hasText = searchInput != null && searchInput.getText() != null && searchInput.getText().length() > 0;
        boolean focused = searchInput != null && searchInput.hasFocus();
        categoryBackCallback.setEnabled(activeCategoryApps != null || hasText || focused);
    }

    private void clearSearch() {
        if (searchInput == null) return;
        if (searchInput.getText() != null && searchInput.getText().length() > 0) searchInput.setText("");
        searchInput.clearFocus();
        hideKeyboard();
        updateBackState();
    }

    private void hideKeyboard() {
        if (searchInput == null || !isAdded()) return;
        Object service = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (service instanceof android.view.inputmethod.InputMethodManager) {
            ((android.view.inputmethod.InputMethodManager) service)
                    .hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
    }

    private void setSearchClearVisible(boolean visible) {
        if (searchClear == null) return;
        if (visible && searchClear.getVisibility() != View.VISIBLE) {
            searchClear.animate().cancel();
            searchClear.setVisibility(View.VISIBLE);
            searchClear.setAlpha(0f);
            searchClear.setScaleX(0.5f);
            searchClear.setScaleY(0.5f);
            searchClear.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(MotionHelper.getDuration(requireContext(), 200, 320))
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
        } else if (!visible && searchClear.getVisibility() == View.VISIBLE) {
            searchClear.animate().cancel();
            searchClear.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f)
                    .setDuration(MotionHelper.getDuration(requireContext(), 120, 180))
                    .withEndAction(() -> {
                        if (searchClear != null) searchClear.setVisibility(View.GONE);
                    }).start();
        }
    }

    private void animateSearchFocus(boolean focused) {
        if (searchCard == null) return;
        float density = getResources().getDisplayMetrics().density;
        int from = searchCard.getStrokeWidth();
        int to = focused ? Math.round(2f * density) : 0;
        if (searchStrokeAnimator != null) searchStrokeAnimator.cancel();
        long duration = MotionHelper.getDuration(requireContext(), 200, 320);
        if (duration == 0L) {
            searchCard.setStrokeWidth(to);
        } else {
            searchStrokeAnimator = android.animation.ValueAnimator.ofInt(from, to);
            searchStrokeAnimator.setDuration(duration);
            searchStrokeAnimator.addUpdateListener(a -> {
                if (searchCard != null) searchCard.setStrokeWidth((int) a.getAnimatedValue());
            });
            searchStrokeAnimator.start();
        }
        int tint = com.google.android.material.color.MaterialColors.getColor(searchCard,
                focused ? androidx.appcompat.R.attr.colorPrimary
                        : com.google.android.material.R.attr.colorOnSurfaceVariant);
        if (searchIcon != null) {
            androidx.core.widget.ImageViewCompat.setImageTintList(searchIcon, android.content.res.ColorStateList.valueOf(tint));
            searchIcon.animate().cancel();
            searchIcon.setScaleX(0.8f);
            searchIcon.setScaleY(0.8f);
            searchIcon.animate().scaleX(1f).scaleY(1f).setDuration(duration)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
        }
    }

    private void setupHeroShapes(View view) {
        heroAnimators.clear();
        if (AppPreferences.isReduceMotion(requireContext())) {
            applyHeroShapeColors(view, null);
            return;
        }
        applyHeroShapeColors(view, heroAnimators);
    }

    private void applyHeroShapeColors(View view, List<android.animation.Animator> sink) {
        int[] ids = {R.id.heroShapeA, R.id.heroShapeB, R.id.heroShapeC};
        int[] attrs = {
                androidx.appcompat.R.attr.colorPrimary,
                com.google.android.material.R.attr.colorTertiary,
                com.google.android.material.R.attr.colorOnPrimaryContainer
        };
        int[] alphas = {70, 90, 60};
        float[] sizes = {124f, 76f, 44f};
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < ids.length; i++) {
            View shape = view.findViewById(ids[i]);
            if (shape == null) continue;
            int base = com.google.android.material.color.MaterialColors.getColor(shape, attrs[i], android.graphics.Color.GRAY);
            int color = androidx.core.graphics.ColorUtils.setAlphaComponent(base, alphas[i]);
            final android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
            drawable.setColor(color);
            final float size = sizes[i] * density;
            drawable.setCornerRadius(size * 0.5f);
            shape.setBackground(drawable);
            if (sink == null) continue;
            android.animation.ValueAnimator morph = android.animation.ValueAnimator.ofFloat(0.5f, 0.24f);
            morph.setDuration(2600L + i * 700L);
            morph.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            morph.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            morph.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            morph.addUpdateListener(a -> drawable.setCornerRadius(size * (float) a.getAnimatedValue()));
            ObjectAnimator spin = ObjectAnimator.ofFloat(shape, View.ROTATION, 0f, i % 2 == 0 ? 360f : -360f);
            spin.setDuration(16000L + i * 5000L);
            spin.setRepeatCount(ObjectAnimator.INFINITE);
            spin.setInterpolator(new android.view.animation.LinearInterpolator());
            morph.start();
            spin.start();
            sink.add(morph);
            sink.add(spin);
        }
    }

    private void setHeroAnimating(boolean running) {
        for (android.animation.Animator animator : heroAnimators) {
            if (running) {
                if (animator.isPaused()) animator.resume();
            } else if (animator.isRunning()) {
                animator.pause();
            }
        }
    }

    private void playExploreEntrance() {
        if (entrancePlayed || !isAdded() || getView() == null) return;
        entrancePlayed = true;
        int index = 0;
        int[] ids = {R.id.exploreHero, R.id.sectionNew, R.id.sectionUpdated, R.id.sectionGames, R.id.sectionPopular};
        for (int id : ids) {
            View v = getView().findViewById(id);
            if (v != null && v.getVisibility() == View.VISIBLE) MotionHelper.animateEntrance(v, index++);
        }
        if (categoriesContainer != null) {
            for (int i = 0; i < categoriesContainer.getChildCount(); i++) {
                MotionHelper.animateEntrance(categoriesContainer.getChildAt(i), index++);
            }
        }
    }

    private void setupHorizontal(int sectionId) {
        RecyclerView rv = requireView().findViewById(sectionId).findViewById(sectionRecyclerId(sectionId));
        rv.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rv.setNestedScrollingEnabled(false);
        rv.setHasFixedSize(true);
        rv.setItemViewCacheSize(8);
        MotionHelper.setupListAnimator(rv);
        rv.setAdapter(new TileAdapter());
    }

    private void loadCatalog() {
        loadCatalog(false);
    }

    private final Runnable installEventsListener = () -> { refreshInstalledStatesOnly(); refreshDownloadButtons(); };

    @Override public void onPause() {
        com.opensourcefinder.app.install.InstallEvents.removeListener(installEventsListener);
        super.onPause();
    }

    @Override public void onResume() {
        super.onResume();
        com.opensourcefinder.app.install.InstallEvents.addListener(installEventsListener);
        refreshInstalledStatesOnly();
        refreshDownloadButtons();
    }

    @Override public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        setHeroAnimating(!hidden);
        if (!hidden) {
            refreshInstalledStatesOnly();
            refreshDownloadButtons();
        }
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
        final int request = ++catalogGeneration;
        if (exploreRefresh != null) exploreRefresh.setRefreshing(refresh);
        boolean firstLoad = all.isEmpty();
        if (loadingPanel != null) loadingPanel.setVisibility(firstLoad ? View.VISIBLE : View.GONE);
        if (firstLoad && exploreScroll != null) exploreScroll.setVisibility(View.INVISIBLE);
        final android.content.Context context = getContext();
        if (context == null) {
            if (exploreRefresh != null) exploreRefresh.setRefreshing(false);
            catalogLoading = false;
            return;
        }
        if (refresh) FdroidIndexManager.clearExploreCatalogCache(context);
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
                java.util.Map<String, com.opensourcefinder.app.install.InstallState.Info> liveAll = com.opensourcefinder.app.install.InstallState.readAll(context);
                for (ExploreApp app : catalog) com.opensourcefinder.app.install.InstallState.refresh(app, liveAll.get(app.getPackageName()));
                ui(() -> {
                    catalogLoading = false;
                    if (destroyed || request != catalogGeneration) return;
                    all.clear();
                    all.addAll(stableCatalog(catalog));
                    buildExploreSections();
                    bindHomeSections();
                    if (exploreScroll != null) exploreScroll.setVisibility(View.VISIBLE);
                    if (loadingPanel != null) loadingPanel.setVisibility(View.GONE);
                    if (exploreRefresh != null) exploreRefresh.setRefreshing(false);
                    if (searchRecycler != null) searchRecycler.setVisibility(View.GONE);
                    if (catalog.isEmpty() && empty != null) {
                        empty.setText(getString(R.string.catalog_error));
                        empty.setVisibility(View.VISIBLE);
                    } else if (empty != null) {
                        empty.setVisibility(View.GONE);
                    }
                    applyFilter(lastQuery);
                });
            } catch (Exception e) {
                ui(() -> {
                    catalogLoading = false;
                    if (destroyed || request != catalogGeneration) return;
                    if (loadingPanel != null) loadingPanel.setVisibility(View.GONE);
                    if (exploreRefresh != null) exploreRefresh.setRefreshing(false);
                    if (exploreScroll != null) exploreScroll.setVisibility(View.VISIBLE);
                    Toast.makeText(requireContext(), getString(R.string.catalog_error), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private List<ExploreApp> stableCatalog(List<ExploreApp> catalog) {
        List<ExploreApp> stable = new ArrayList<>(catalog == null
                ? Collections.emptyList() : catalog);
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

    private void buildExploreSections() {
        newest.clear();
        updated.clear();
        popular.clear();
        lumenApps.clear();
        long now = System.currentTimeMillis();
        long newestCutoff = now - 180L * 24 * 60 * 60 * 1000;
        long updatedCutoff = now - 365L * 24 * 60 * 60 * 1000;
        java.util.Calendar recentWindow = java.util.Calendar.getInstance();
        recentWindow.setTimeInMillis(now);
        recentWindow.add(java.util.Calendar.MONTH, -12);
        long recentCutoff = recentWindow.getTimeInMillis();
        for (ExploreApp app : all) {
            if (app.getAdded() >= newestCutoff) newest.add(app);
            if (app.getLastUpdated() >= updatedCutoff) updated.add(app);
            if (app.getPopularRank() != Integer.MAX_VALUE) popular.add(app);
            if (isLumenApp(app)) lumenApps.add(app);
        }
        Collections.sort(newest, (a, b) -> Long.compare(b.getAdded(), a.getAdded()));
        Collections.sort(updated, (a, b) -> Long.compare(b.getLastUpdated(), a.getLastUpdated()));
        Collections.sort(popular, (a, b) -> Integer.compare(a.getPopularRank(), b.getPopularRank()));
        Collections.sort(lumenApps, (a, b) -> {
            boolean recentA = a.getLastUpdated() > 0 && a.getLastUpdated() >= recentCutoff;
            boolean recentB = b.getLastUpdated() > 0 && b.getLastUpdated() >= recentCutoff;
            if (recentA != recentB) return recentA ? -1 : 1;
            if (recentA) {
                int byUpdateDate = Long.compare(b.getLastUpdated(), a.getLastUpdated());
                if (byUpdateDate != 0) return byUpdateDate;
            }
            int byName = String.valueOf(a.getName()).compareToIgnoreCase(String.valueOf(b.getName()));
            return byName != 0 ? byName : String.valueOf(a.getPackageName()).compareToIgnoreCase(String.valueOf(b.getPackageName()));
        });
    }

    private void bindHomeSections() {
        bindSection(R.id.sectionNew, newest);
        bindSection(R.id.sectionUpdated, updated);
        bindSection(R.id.sectionPopular, popular);
        bindSection(R.id.sectionGames, lumenApps);
        View lumenSection = requireView().findViewById(R.id.sectionGames);
        if (lumenSection != null) lumenSection.setVisibility(lumenApps.isEmpty() ? View.GONE : View.VISIBLE);
        bindCategories();
        TextView heroCount = requireView().findViewById(R.id.exploreHeroCount);
        if (heroCount != null) heroCount.setText(getString(R.string.apps_found, all.size()));
        playExploreEntrance();
    }

    private boolean isLumenApp(ExploreApp app) {
        if (app == null || app.getCategories() == null) return false;
        for (String category : app.getCategories()) {
            if (category != null && category.trim().equalsIgnoreCase("lumen")) return true;
        }
        return false;
    }

    private void bindSection(int sectionId, List<ExploreApp> apps) {
        bindTiles(sectionId, slice(apps, 0, 16));
        View header = requireView().findViewById(sectionId);
        View arrow = header.findViewById(sectionArrowId(sectionId));
        if (arrow != null) {
            CharSequence title = ((TextView) header.findViewById(sectionTitleId(sectionId))).getText();
            List<ExploreApp> full = new ArrayList<>(apps);
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
        RecyclerView rv = requireView().findViewById(sectionId).findViewById(sectionRecyclerId(sectionId));
        TileAdapter adapter = (TileAdapter) rv.getAdapter();
        if (adapter != null) adapter.submit(apps);
    }

    private int expandedGroup = -1;
    private final List<View> groupCards = new ArrayList<>();

    private static final class CategoryEntry {
        final int key;
        final String title;
        final int icon;
        final List<ExploreApp> apps = new ArrayList<>();
        CategoryEntry(int key, String title, int icon) {
            this.key = key;
            this.title = title;
            this.icon = icon;
        }
    }

    private static final class CategoryGroup {
        final int titleRes;
        final String icon;
        final int[] keys;
        final List<CategoryEntry> entries = new ArrayList<>();
        CategoryGroup(int titleRes, String icon, int... keys) {
            this.titleRes = titleRes;
            this.icon = icon;
            this.keys = keys;
        }
        boolean owns(int key) {
            for (int k : keys) if (k == key) return true;
            return false;
        }
    }

    private List<CategoryGroup> createGroups() {
        List<CategoryGroup> groups = new ArrayList<>();
        groups.add(new CategoryGroup(R.string.category_group_games, "ic_device_gamepad_2",
                R.string.search_category_action_game, R.string.search_category_board_game,
                R.string.search_category_card_game, R.string.search_category_casual_game,
                R.string.search_category_dice, R.string.search_category_educational_game,
                R.string.search_category_emulator, R.string.search_category_game_helper));
        groups.add(new CategoryGroup(R.string.category_group_social, "ic_message_circle",
                R.string.category_chats, R.string.search_category_email,
                R.string.search_category_contact, R.string.search_category_forum,
                R.string.search_category_browser, R.string.search_category_internet,
                R.string.search_category_connectivity, R.string.search_category_cast));
        groups.add(new CategoryGroup(R.string.category_group_tools, "ic_tool",
                R.string.category_stores, R.string.search_category_app_manager,
                R.string.search_category_battery, R.string.search_category_calculator,
                R.string.search_category_alarm_clock, R.string.search_category_clock,
                R.string.search_category_flashlight, R.string.search_category_file_manager,
                R.string.search_category_download));
        groups.add(new CategoryGroup(R.string.category_group_media, "ic_palette",
                R.string.search_category_camera, R.string.search_category_gallery,
                R.string.search_category_graphics, R.string.search_category_draw,
                R.string.search_category_icon_pack, R.string.search_category_ebook_reader));
        groups.add(new CategoryGroup(R.string.category_group_life, "ic_target",
                R.string.search_category_bookmark, R.string.search_category_calendar,
                R.string.search_category_diet, R.string.search_category_habit_tracker,
                R.string.search_category_health_manager, R.string.search_category_finance_manager));
        groups.add(new CategoryGroup(R.string.category_group_security_dev, "ic_shield",
                R.string.category_ai, R.string.search_category_development,
                R.string.search_category_code_forge, R.string.search_category_firewall,
                R.string.search_category_dns_hosts, R.string.search_category_file_encryption,
                R.string.search_category_file_transfer, R.string.search_category_cloud_storage));
        return groups;
    }

    private void bindCategories() {
        if (categoriesContainer == null) return;
        categoriesContainer.removeAllViews();
        groupCards.clear();

        Context ctx = requireContext();
        List<CategoryEntry> primary = new ArrayList<>();
        CategoryEntry ai = new CategoryEntry(R.string.category_ai, getString(R.string.category_ai), TablerIconRes.get(ctx, "ic_sparkles"));
        CategoryEntry chats = new CategoryEntry(R.string.category_chats, getString(R.string.category_chats), TablerIconRes.get(ctx, "ic_message"));
        CategoryEntry stores = new CategoryEntry(R.string.category_stores, getString(R.string.category_stores), TablerIconRes.get(ctx, "ic_building_store"));
        primary.add(ai);
        primary.add(chats);
        primary.add(stores);

        List<CategoryEntry> extra = new ArrayList<>();
        List<String[]> extraKeywords = new ArrayList<>();
        for (SearchActivity.SearchCategory category : SearchActivity.SEARCH_CATEGORIES) {
            if (category.titleRes == R.string.search_category_ai_chat
                    || category.titleRes == R.string.search_category_app_store) continue;
            extra.add(new CategoryEntry(category.titleRes, getString(category.titleRes), TablerIconRes.get(ctx, category.icon)));
            extraKeywords.add(category.keywords);
        }

        for (ExploreApp app : all) {
            String text = appSearchText(app);
            if (isAiApp(app)) ai.apps.add(app);
            if (isChatApp(app)) chats.apps.add(app);
            if (isStoreApp(app)) stores.apps.add(app);
            for (int i = 0; i < extra.size(); i++) {
                if (matchesAny(text, extraKeywords.get(i))) extra.get(i).apps.add(app);
            }
        }

        List<CategoryEntry> entries = new ArrayList<>();
        for (CategoryEntry entry : primary) if (!entry.apps.isEmpty()) entries.add(entry);
        for (CategoryEntry entry : extra) if (!entry.apps.isEmpty()) entries.add(entry);
        Collections.sort(entries, (x, y) -> Integer.compare(y.apps.size(), x.apps.size()));

        List<CategoryGroup> groups = createGroups();
        CategoryGroup fallbackGroup = groups.get(groups.size() - 1);
        for (CategoryEntry entry : entries) {
            CategoryGroup target = fallbackGroup;
            for (CategoryGroup group : groups) {
                if (group.owns(entry.key)) {
                    target = group;
                    break;
                }
            }
            target.entries.add(entry);
        }

        LayoutInflater inflater = LayoutInflater.from(ctx);
        for (CategoryGroup group : groups) {
            if (group.entries.isEmpty()) continue;
            final int index = groupCards.size();
            View card = inflater.inflate(R.layout.item_category_group, categoriesContainer, false);
            ((ImageView) card.findViewById(R.id.groupIcon)).setImageResource(TablerIconRes.get(ctx, group.icon));
            ((TextView) card.findViewById(R.id.groupTitle)).setText(getString(group.titleRes));
            Set<ExploreApp> unique = new HashSet<>();
            for (CategoryEntry entry : group.entries) unique.addAll(entry.apps);
            ((TextView) card.findViewById(R.id.groupMeta)).setText(
                    getString(R.string.category_group_meta, group.entries.size(), unique.size()));

            ViewGroup content = card.findViewById(R.id.groupContent);
            ImageView chevron = card.findViewById(R.id.groupChevron);
            for (CategoryEntry entry : group.entries) {
                View row = inflater.inflate(R.layout.item_category_sub, content, false);
                if (entry.icon != 0) ((ImageView) row.findViewById(R.id.subIcon)).setImageResource(entry.icon);
                ((TextView) row.findViewById(R.id.subTitle)).setText(entry.title);
                ((TextView) row.findViewById(R.id.subCount)).setText(String.valueOf(entry.apps.size()));
                MotionHelper.attachPressScale(row);
                row.setOnClickListener(v -> showCategory(entry.title, entry.apps));
                content.addView(row);
            }

            boolean open = index == expandedGroup;
            content.setVisibility(open ? View.VISIBLE : View.GONE);
            chevron.setRotation(open ? 180f : 0f);
            View header = card.findViewById(R.id.groupHeader);
            MotionHelper.attachPressScale(header);
            header.setOnClickListener(v -> toggleGroup(index));
            groupCards.add(card);
            categoriesContainer.addView(card);
        }
        if (expandedGroup >= groupCards.size()) expandedGroup = -1;
    }

    private void toggleGroup(int index) {
        if (index < 0 || index >= groupCards.size()) return;
        int previous = expandedGroup;
        boolean expand = previous != index;
        expandedGroup = expand ? index : -1;
        if (previous >= 0 && previous != index) setGroupExpanded(previous, false);
        setGroupExpanded(index, expand);
        if (expand) {
            final View card = groupCards.get(index);
            long delay = MotionHelper.getDuration(requireContext(), 220, 320) + 40L;
            card.postDelayed(() -> {
                if (!isAdded() || expandedGroup != index) return;
                card.requestRectangleOnScreen(new android.graphics.Rect(0, 0, card.getWidth(), card.getHeight()), false);
            }, delay);
        }
    }

    private void setGroupExpanded(int index, boolean expand) {
        if (index < 0 || index >= groupCards.size()) return;
        View card = groupCards.get(index);
        AccordionHelper.setExpanded(categoriesContainer, card.findViewById(R.id.groupContent),
                (ImageView) card.findViewById(R.id.groupChevron), expand, true);
    }

    private boolean matchesAny(String value, String... keywords) {
        if (value == null || value.isEmpty()) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (normalized.contains(keyword)) return true;
        }
        return false;
    }

    private String appSearchText(ExploreApp app) {
        if (app == null) return "";
        StringBuilder text = new StringBuilder();
        text.append(' ').append(String.valueOf(app.getName()));
        text.append(' ').append(String.valueOf(app.getPackageName()));
        text.append(' ').append(String.valueOf(app.getSummary()));
        if (app.getCategories() != null) {
            for (String category : app.getCategories()) text.append(' ').append(String.valueOf(category));
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private boolean isAiApp(ExploreApp app) {
        String text = appSearchText(app);
        return matchesAny(text,
                "artificial intelligence", "machine learning", "ai assistant", "ai chat",
                "large language model", "language model", "llm", "chatgpt", "openai",
                "ollama", "stable diffusion", "generative ai", "generative artificial intelligence");
    }

    private boolean isChatApp(ExploreApp app) {
        String text = appSearchText(app);
        return matchesAny(text,
                "chat", "messaging", "instant messaging", "matrix", "xmpp", "irc",
                "signal", "telegram", "element", "conversations", "messenger", "jabber");
    }

    private boolean isStoreApp(ExploreApp app) {
        String text = appSearchText(app);
        return matchesAny(text,
                "app store", "apps store", "software store", "package manager",
                "application manager", "f-droid", "fdroid", "aurora store", "droid-ify",
                "droidify", "obtainium", "app manager", "repository manager");
    }

    private void showCategory(String title, List<ExploreApp> apps) {
        filterGeneration++;
        activeCategoryApps = apps == null ? new ArrayList<>() : new ArrayList<>(apps);
        lastQuery = "";
        setCategoryHeader(title);
        toolbar.setTitle("");
        toolbar.setNavigationIcon(com.opensourcefinder.app.ui.TablerIconRes.get(requireContext(), "ic_arrow_back"));
        toolbar.setNavigationIconTint(com.google.android.material.color.MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface));
        toolbar.setNavigationOnClickListener(v -> exitCategory());
        categoryBackCallback.setEnabled(true);
        showSearchResults(activeCategoryApps, true);
    }

    private void setCategoryHeader(@Nullable String categoryTitle) {
        if (toolbar == null) return;
        View logo = toolbar.findViewById(R.id.exploreLogo);
        View row = toolbar.findViewById(R.id.exploreHeaderRow);
        float density = getResources().getDisplayMetrics().density;
        boolean category = categoryTitle != null;
        if (logo != null) logo.setVisibility(category ? View.GONE : View.VISIBLE);
        if (storeTitle != null) {
            storeTitle.setText(category ? categoryTitle : getString(R.string.open_store_title));
            storeTitle.setVisibility(View.VISIBLE);
            ViewGroup.MarginLayoutParams titleParams = (ViewGroup.MarginLayoutParams) storeTitle.getLayoutParams();
            titleParams.setMarginStart(Math.round((category ? 0f : 10f) * density));
            storeTitle.setLayoutParams(titleParams);
            storeTitle.setMaxWidth(Math.round((category ? 176f : 150f) * density));
        }
        if (row != null && row.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) row.getLayoutParams();
            params.setMarginStart(Math.round((category ? 0f : 12f) * density));
            row.setLayoutParams(params);
        }
    }

    public void resetToInitialState() {
        if (!isAdded() || root == null) return;
        if (searchRecycler != null && searchRecycler.getVisibility() == View.VISIBLE
                || activeCategoryApps != null) {
            exitCategory();
        }
        if (exploreScroll instanceof androidx.core.widget.NestedScrollView) ((androidx.core.widget.NestedScrollView) exploreScroll).smoothScrollTo(0, 0);
        if (categoriesContainer != null) categoriesContainer.requestFocus();
    }

    private void exitCategory() {
        filterGeneration++;
        hideCategoryLoading();
        activeCategoryApps = null;
        toolbar.setTitle("");
        toolbar.setNavigationIcon(null);
        toolbar.setNavigationOnClickListener(null);
        setCategoryHeader(null);
        if (searchInput != null && searchInput.getText() != null && searchInput.getText().length() > 0) searchInput.setText("");
        updateBackState();
        clearSearchList();
        showHomeContent();
    }

    private void showHomeContent() {
        crossfade(searchRecycler, exploreScroll);
        empty.setVisibility(View.GONE);
    }

    private void showSearchResults(List<ExploreApp> results, boolean resetPosition) {
        if (resetPosition) {
            clearSearchList();
        }
        if (resetPosition && searchRecycler != null) {
            searchRecycler.scrollToPosition(0);
        }
        List<ExploreApp> snapshot = results == null ? new ArrayList<>() : new ArrayList<>(results);
        final long submitGeneration = filterGeneration;
        searchAdapter.submitList(snapshot, () -> {
            if (destroyed || submitGeneration != filterGeneration) return;
            if (!resetPosition && searchRecycler != null) {
                searchRecycler.stopScroll();
                searchRecycler.scrollToPosition(0);
            }
            if (resetPosition && searchRecycler != null) {
                searchRecycler.stopScroll();
                searchRecycler.scrollToPosition(0);
                searchRecycler.post(() -> {
                    if (destroyed || submitGeneration != filterGeneration) return;
                    searchRecycler.scrollToPosition(0);
                    searchRecycler.setAlpha(1f);
                    searchRecycler.setItemAnimator(searchItemAnimator);
                    hideCategoryLoading();
                });
            }
        });
        crossfade(exploreScroll, searchRecycler);
        empty.bringToFront();
        empty.setVisibility(snapshot.isEmpty() ? View.VISIBLE : View.GONE);
        if (resetPosition) showCategoryLoading();
    }

    private void clearSearchList() {
        if (searchRecycler == null) return;
        searchRecycler.stopScroll();
        searchRecycler.setItemAnimator(null);
        searchAdapter = new ExploreAdapter();
        searchRecycler.setAdapter(searchAdapter);
        searchRecycler.scrollToPosition(0);
        searchRecycler.setAlpha(1f);
        searchRecycler.setItemAnimator(searchItemAnimator);
        searchRecycler.setVisibility(View.GONE);
    }

    private void showCategoryLoading() {
        if (loadingPanel == null) return;
        loadingPanel.setVisibility(View.VISIBLE);
        loadingPanel.bringToFront();
        View indicator = loadingPanel.findViewById(R.id.exploreLoadingIndicator);
        if (indicator instanceof com.google.android.material.loadingindicator.LoadingIndicator) {
            ((com.google.android.material.loadingindicator.LoadingIndicator) indicator).show();
        }
    }

    private void hideCategoryLoading() {
        if (loadingPanel == null) return;
        View indicator = loadingPanel.findViewById(R.id.exploreLoadingIndicator);
        if (indicator instanceof com.google.android.material.loadingindicator.LoadingIndicator) {
            ((com.google.android.material.loadingindicator.LoadingIndicator) indicator).hide();
        }
        loadingPanel.setVisibility(View.GONE);
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
            transition.setDuration(MotionHelper.getDuration(requireContext(), 160, 220));
            transition.setInterpolator(MotionHelper.getInterpolator(requireContext()));
            android.transition.TransitionManager.beginDelayedTransition(parent, transition);
        }
        outView.setVisibility(View.GONE);
        inView.setVisibility(View.VISIBLE);
        inView.bringToFront();
    }

    private void applyFilter(String q) {
        if (destroyed) return;
        if (activeCategoryApps != null) {
            searchRecycler.setAlpha(1f);
            searchRecycler.setItemAnimator(searchItemAnimator);
        }
        hideCategoryLoading();
        final long requestGeneration = ++filterGeneration;
        lastQuery = q == null ? "" : q;
        final String query = lastQuery.trim().toLowerCase(Locale.ROOT);
        final List<ExploreApp> base = activeCategoryApps != null ? new ArrayList<>(activeCategoryApps) : new ArrayList<>(all);
        if (query.isEmpty()) {
            if (activeCategoryApps != null) {
                showSearchResults(base, false);
            } else {
                showHomeContent();
            }
            return;
        }
        executor.execute(() -> {
            List<ExploreApp> next = fuzzy.search(base, lastQuery);
            ui(() -> {
                if (requestGeneration != filterGeneration || !query.equals(lastQuery.trim().toLowerCase(Locale.ROOT))) return;
                showSearchResults(next, false);
            });
        });
    }

    private static boolean matches(ExploreApp app, String query) {
        return (app.getName() != null && app.getName().toLowerCase(Locale.ROOT).contains(query))
                || (app.getPackageName() != null && app.getPackageName().toLowerCase(Locale.ROOT).contains(query))
                || (app.getSummary() != null && app.getSummary().toLowerCase(Locale.ROOT).contains(query));
    }

    private void openDetail(ExploreApp app) {
        Intent i = new Intent(requireContext(), ExploreDetailActivity.class);
        i.putExtra(ExploreDetailActivity.EXTRA_EXPLORE_APP, app);
        startActivity(i);
    }

    private void startDownload(ExploreApp app) {
        if (app.isGithubRelease() && app.getDownloadUrl() == null) {
            openDetail(app);
            return;
        }
        if (app.getDownloadUrl() == null) {
            Toast.makeText(requireContext(), getString(R.string.download_link_missing), Toast.LENGTH_SHORT).show();
            return;
        }
        boolean alreadyTracked = false;
        for (MatchedApp existing : MatchStore.get(requireContext())) {
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
        MatchStore.upsert(requireContext(), match);
        String fileName = (app.getName() != null ? app.getName() : app.getPackageName())
                + "-" + (app.getLatestVersionName() != null ? app.getLatestVersionName() : "latest") + ".apk";
        DownloadTracker.get().start(requireContext(), app.getPackageName(), app.getDownloadUrl(), fileName, new DownloadTracker.Listener() {
            @Override public void onProgress(DownloadTracker.Entry entry) {
                        ui(() -> { if (searchAdapter != null) searchAdapter.notifyDataSetChanged(); });
                    }
            @Override public void onCompleted(DownloadTracker.Entry entry) {
                ui(() -> {
                    Toast.makeText(requireContext(), R.string.download_complete, Toast.LENGTH_SHORT).show();
                    if (searchAdapter != null) searchAdapter.notifyDataSetChanged();
                });
            }
            @Override public void onFailed(DownloadTracker.Entry entry) {
                ui(() -> {
                    Toast.makeText(requireContext(), R.string.download_failed, Toast.LENGTH_LONG).show();
                    if (searchAdapter != null) searchAdapter.notifyDataSetChanged();
                });
            }
            @Override public void onCancelled(DownloadTracker.Entry entry) {
                ui(() -> { if (searchAdapter != null) searchAdapter.notifyDataSetChanged(); });
            }
        }, app.getIconUrl(), app.getApkSize());
        if (!alreadyTracked) DownloadTracker.get().markHomePlaceholderOwned(app.getPackageName());
        Toast.makeText(requireContext(), getString(R.string.added_to_main_download_started), Toast.LENGTH_SHORT).show();
    }

    private void applyTileSize(ViewGroup parent, View tile) {
        android.content.res.Resources res = parent.getResources();
        float density = res.getDisplayMetrics().density;
        int width = parent.getMeasuredWidth() > 0 ? parent.getMeasuredWidth()
                : res.getDisplayMetrics().widthPixels - Math.round(24f * density);
        int available = width - parent.getPaddingLeft();
        int minWidth = res.getDimensionPixelSize(R.dimen.explore_tile_min_width);
        int count = Math.max(2, (int) Math.floor(available / (float) minWidth - 0.5f));
        int tileWidth = Math.round(available / (count + 0.5f));
        int iconMin = res.getDimensionPixelSize(R.dimen.explore_tile_icon_min);
        int iconMax = res.getDimensionPixelSize(R.dimen.explore_tile_icon_max);
        int icon = Math.max(iconMin, Math.min(iconMax, tileWidth - Math.round(20f * density)));
        ViewGroup.LayoutParams params = tile.getLayoutParams();
        params.width = tileWidth;
        tile.setLayoutParams(params);
        View iconView = tile.findViewById(R.id.tileIcon);
        View frame = (View) iconView.getParent();
        ViewGroup.LayoutParams frameParams = frame.getLayoutParams();
        frameParams.width = icon;
        frameParams.height = icon;
        frame.setLayoutParams(frameParams);
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
            applyTileSize(parent, v);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            ExploreApp app = items.get(position);
            h.name.setText(app.getName());
            IconShapeHelper.apply(h.icon);
            if (app.getIconUrl() != null) IconLoader.load(h.icon, app.getIconUrl(), iconOptions);
            else h.icon.setImageDrawable(com.opensourcefinder.app.ui.IconLoader.empty(requireContext()));
            if (!h.entered) {
                h.entered = true;
                MotionHelper.animateEntrance(h.itemView, position);
            }
        }
        @Override public int getItemCount() { return items.size(); }
        class VH extends RecyclerView.ViewHolder {
            final ImageView icon; final TextView name;
            boolean entered = false;
            VH(View v) {
                super(v);
                icon = v.findViewById(R.id.tileIcon);
                IconShapeHelper.apply(icon);
                name = v.findViewById(R.id.tileName);
                IconShapeHelper.apply(icon);
                MotionHelper.attachPressScale(v);
                itemView.setOnClickListener(view -> {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) openDetail(items.get(pos));
                });
            }
        }
    }

    private class ExploreAdapter extends ListAdapter<ExploreApp, ExploreAdapter.VH> {
        ExploreAdapter() {
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
            IconShapeHelper.apply(h.icon);
            if (app.getIconUrl() != null) IconLoader.load(h.icon, app.getIconUrl(), iconOptions);
            else h.icon.setImageDrawable(com.opensourcefinder.app.ui.IconLoader.empty(requireContext()));
            h.itemView.animate().cancel();
            h.itemView.setAlpha(1f);
            h.itemView.setTranslationY(0f);
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

    private void ui(Runnable r) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> { if (isAdded()) r.run(); });
    }

    @Override public void onDestroy() {
        super.onDestroy();
        destroyed = true;
        catalogLoading = false;
        executor.shutdownNow();
    }
    private int sectionTitleId(int sectionId) {
        if (sectionId == R.id.sectionNew) return R.id.sectionNewTitle;
        if (sectionId == R.id.sectionUpdated) return R.id.sectionUpdatedTitle;
        if (sectionId == R.id.sectionPopular) return R.id.sectionPopularTitle;
        return R.id.sectionGamesTitle;
    }

    private int sectionArrowId(int sectionId) {
        if (sectionId == R.id.sectionNew) return R.id.sectionNewArrow;
        if (sectionId == R.id.sectionUpdated) return R.id.sectionUpdatedArrow;
        if (sectionId == R.id.sectionPopular) return R.id.sectionPopularArrow;
        return R.id.sectionGamesArrow;
    }

    private int sectionRecyclerId(int sectionId) {
        if (sectionId == R.id.sectionNew) return R.id.sectionNewRecycler;
        if (sectionId == R.id.sectionUpdated) return R.id.sectionUpdatedRecycler;
        if (sectionId == R.id.sectionPopular) return R.id.sectionPopularRecycler;
        return R.id.sectionGamesRecycler;
    }

}
