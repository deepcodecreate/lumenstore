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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.loadingindicator.LoadingIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.opensourcefinder.app.MainActivity;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.adapter.AppListAdapter;
import com.opensourcefinder.app.download.DownloadTracker;
import com.opensourcefinder.app.model.GitHubUser;
import com.opensourcefinder.app.model.InstalledAppInfo;
import com.opensourcefinder.app.model.MatchedApp;
import com.opensourcefinder.app.model.ExploreApp;
import com.opensourcefinder.app.model.SourceRepository;
import com.opensourcefinder.app.network.GitHubTokenProvider;
import com.opensourcefinder.app.network.RetrofitClient;
import com.opensourcefinder.app.scanner.AppScanner;
import com.opensourcefinder.app.scanner.CustomRepositoryScanner;
import com.opensourcefinder.app.scanner.FdroidIndexManager;
import com.opensourcefinder.app.scanner.MatchStore;
import com.opensourcefinder.app.scanner.RepositoryManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService presentationExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, Drawable> iconCache = new HashMap<>();
    private final DownloadTracker.Listener homeDownloadListener = new DownloadTracker.Listener() {
        @Override public void onStarted(DownloadTracker.Entry entry) {
            scheduleHomeReload();
        }
        @Override public void onProgress(DownloadTracker.Entry entry) {
            ui(() -> { if (adapter != null) adapter.notifyDownloadChanged(entry.key); });
        }
        @Override public void onCompleted(DownloadTracker.Entry entry) {
            ui(() -> { if (adapter != null) adapter.notifyDownloadChanged(entry.key); });
        }
        @Override public void onFailed(DownloadTracker.Entry entry) {
            ui(() -> { if (adapter != null) adapter.notifyDownloadChanged(entry.key); });
        }
        @Override public void onCancelled(DownloadTracker.Entry entry) {
            ui(() -> { if (adapter != null) adapter.notifyDownloadChanged(entry.key); });
        }
    };
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private volatile Boolean lastScanIndexEnabled;
    private String lastFavoritesKey;
    private volatile boolean scanRunning = false;
    private AppListAdapter adapter;
    private MaterialRefreshLayout swipe;
    private TextView empty;
    private TextView appCount;
    private MaterialToolbar toolbar;
    private ImageView avatar;
    private HeaderSearch homeHeader;
    private String homeQuery = "";
    private List<MatchedApp> lastSaved = new ArrayList<>();
    private long homePresentationGeneration;

    private View homeLoading;
    private boolean homeLoaded;
    private GitHubUser currentUser;
    private RecyclerView favoritesRecycler;
    private View favoritesSection;
    private com.google.android.material.button.MaterialButton sortDropdown;
    private String updatesFirstLabel;
    private boolean updatesFirst = false;
    private boolean notInstalledLast = false;
    private com.google.android.material.button.MaterialButton sourceDropdown;
    private String selectedSource = SOURCE_ALL;
    private SortMode selectedSort = SortMode.LAST_UPDATED;

    private com.google.android.material.appbar.AppBarLayout homeAppBar;
    private int appBarOffset = 0;
    private View heroCard;
    private View filterRow;
    private TextView heroTitle;
    private TextView heroSubtitle;
    private View updateAllButtonView;
    private boolean heroInstallMode;
    private int menuInstalled;
    private int menuUpdates;
    private int menuAvailable;
    private int pendingRestoreCount;
    private long seenStoreRevision;
    private ShapeView heroShape;
    private ShapeView heroShapeSmall;

    private static final String SOURCE_ALL = "all";
    private static final String SOURCE_FDROID = "fdroid";
    private static final String SOURCE_MANUAL = "manual";
    private static final String SOURCE_LUMEN = "lumen";

    private enum SortMode {
        LAST_UPDATED, NAME, SOURCE
    }

    private enum StageState { PENDING, ACTIVE, DONE }
    private static final int STAGE1_END = 70;
    private static final int STAGE2_END = 100;

    private MaterialCardView scanProgressCard;
    private View scanPanel;
    private View scanPanelArrow;
    private TextView scanTitleText;
    private View scanScrim;
    private View scanStatusButton;
    private ImageView scanStatusIcon;
    private ObjectAnimator scanIconSpin;
    private boolean scanPanelOpen = false;
    private boolean homeMenuOpen = false;
    private View homeMenuPanel;
    private MaterialCardView homeMenuCard;
    private View homeMenuArrow;
    private android.widget.LinearLayout homeMenuContent;
    private View moreButtonView;
    private LinearProgressIndicator overallProgress;
    private TextView scanPercentText;
    private TextView scanCurrentItemText;
    private View stageFdroidRow;
    private View stageManualRow;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle("");
        homeHeader = HeaderSearch.bind(view, R.drawable.ic_home, getString(R.string.app_name),
                R.string.search_hint, 140L, (text, immediate) -> {
                    homeQuery = text == null ? "" : text.trim();
                    if (adapter != null) applyHomePresentation(lastSaved);
                });
        homeHeader.setSearchVisible(false);
        appCount = view.findViewById(R.id.appCount);
        View updateAllButton = view.findViewById(R.id.updateAllButton);
        if (updateAllButton != null) updateAllButton.setOnClickListener(v -> { if (heroInstallMode) installAllMissing(); else updateAll(); });
        updateAllButtonView = updateAllButton;
        setupHero(view);
        swipe = view.findViewById(R.id.swipeRefresh);
        empty = view.findViewById(R.id.emptyView);
        homeLoading = view.findViewById(R.id.homeLoading);
        homeLoaded = false;
        avatar = homeHeader.logoIcon();
        View profileContainer = homeHeader.logo();
        profileContainer.setOnClickListener(v -> {
            if (GitHubTokenProvider.hasToken()) openProfile();
        });
        selectedSort = parseSort(AppPreferences.getHomeSort(requireContext()));
        updatesFirst = AppPreferences.isHomeUpdatesFirst(requireContext());
        notInstalledLast = AppPreferences.isHomeNotInstalledLast(requireContext());
        setupSortControls(view);
        sourceDropdown = view.findViewById(R.id.sourceDropdown);
        if (sourceDropdown != null) {
            final String[] sourceKeys = {SOURCE_ALL, SOURCE_FDROID, SOURCE_LUMEN, SOURCE_MANUAL};
            final String[] sourceLabels = {
                    getString(R.string.source_all),
                    getString(R.string.source_fdroid),
                    getString(R.string.source_lumen_store),
                    getString(R.string.source_manual)
            };
            int current = 0;
            for (int i = 0; i < sourceKeys.length; i++) if (sourceKeys[i].equals(selectedSource)) current = i;
            sourceDropdown.setText(sourceLabels[current]);
            sourceDropdown.setOnClickListener(anchor -> {
                AppPreferences.performHaptic(sourceDropdown);
                int selectedIndex = 0;
                for (int i = 0; i < sourceKeys.length; i++) if (sourceKeys[i].equals(selectedSource)) selectedIndex = i;
                DropdownPopup.show(sourceDropdown, sourceLabels, selectedIndex, position -> {
                    if (position < 0 || position >= sourceKeys.length) return;
                    selectedSource = sourceKeys[position];
                    sourceDropdown.setText(sourceLabels[position]);
                    loadSaved();
                }, null, false, null);
            });
        }
        View fab = view.findViewById(R.id.addRepoFab);
        if (fab != null) {
            fab.setOnClickListener(v -> { AppPreferences.performHaptic(v); toggleFabMenu(!fabMenuOpen); });
            keepFabAboveNav(fab);
            setupFabMenu(view, fab);
        }
        favoritesSection = view.findViewById(R.id.favoritesSection);
        favoritesRecycler = view.findViewById(R.id.favoritesRecycler);
        favoritesRecycler.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        favoritesRecycler.setAdapter(new FavoritesAdapter());
        RecyclerView rv = view.findViewById(R.id.recyclerView);
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        lm.setInitialPrefetchItemCount(6);
        rv.setLayoutManager(lm);
        rv.setHasFixedSize(true);
        rv.setItemViewCacheSize(12);
        adapter = new AppListAdapter(iconCache, this::openDetail, new AppListAdapter.OnAppActionListener() {
            @Override public void onPrimaryAction(MatchedApp app) { startUpdateFromList(app); }
            @Override public void onLongPress(MatchedApp app, View itemView) {
                if (isRemovable(app)) {
                    AppPreferences.performHaptic(itemView);
                    confirmRemoveFromHome(app, itemView, null);
                }
            }
        });
        rv.setAdapter(adapter);
        DownloadTracker.get().addGlobalListener(homeDownloadListener);
        final SwipeToDeleteCallback swipeCallback = new SwipeToDeleteCallback(requireContext()) {
            @Override protected boolean canSwipe(@NonNull RecyclerView.ViewHolder holder) {
                if (holder.getBindingAdapter() != adapter) return false;
                return isRemovable(adapter.getItemAt(holder.getBindingAdapterPosition()));
            }

            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                MatchedApp item = adapter.getItemAt(position);
                restore(viewHolder);
                resetTouchHelper();
                if (!isRemovable(item)) return;
                AppPreferences.performHaptic(viewHolder.itemView);
                confirmRemoveFromHome(item, null, null);
            }
        };
        swipeHelper = new ItemTouchHelper(swipeCallback);
        swipeHelperRecycler = rv;
        swipeHelper.attachToRecyclerView(rv);
        if (fab != null) {
            final boolean[] fabVisible = {true};
            rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    if (dy > 12 && fabVisible[0]) {
                        fabVisible[0] = false;
                        if (fabMenuOpen) toggleFabMenu(false);
                        MotionHelper.hideFab(fab);
                    } else if (dy < -12 && !fabVisible[0]) {
                        fabVisible[0] = true;
                        MotionHelper.showFab(fab);
                    }
                }
            });
        }

        rv.setItemAnimator(null);

        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                LiveBackgroundView.setScrollPaused(newState != RecyclerView.SCROLL_STATE_IDLE);
            }
        });

        swipe.setOnChildScrollUpCallback((parent, child) -> rv != null && (rv.canScrollVertically(-1) || appBarOffset != 0));
        swipe.setOnRefreshListener(() -> startScan(false, true));

        scanProgressCard = view.findViewById(R.id.scanProgressCard);
        scanPanel = view.findViewById(R.id.scanPanel);
        scanPanelArrow = view.findViewById(R.id.scanPanelArrow);
        overallProgress = view.findViewById(R.id.overallProgress);
        scanTitleText = view.findViewById(R.id.scanTitle);
        scanPercentText = view.findViewById(R.id.scanPercentText);
        scanCurrentItemText = view.findViewById(R.id.scanCurrentItemText);
        stageFdroidRow = view.findViewById(R.id.stageFdroid);
        View stageGithubRow = view.findViewById(R.id.stageGithub);
        stageManualRow = view.findViewById(R.id.stageManual);
        ((TextView) stageFdroidRow.findViewById(R.id.stageLabel)).setText(R.string.scan_stage_fdroid);
        stageGithubRow.setVisibility(View.GONE);
        ((TextView) stageManualRow.findViewById(R.id.stageLabel)).setText(R.string.scan_stage_manual);

        scanScrim = view.findViewById(R.id.scanScrim);
        scanStatusButton = view.findViewById(R.id.scanStatusButton);
        scanStatusIcon = view.findViewById(R.id.scanStatusIcon);
        scanStatusIcon.setImageResource(TablerIconRes.get(requireContext(), "ic_radar"));
        scanStatusButton.setOnClickListener(v -> {
            AppPreferences.performHaptic(v);
            if (scanPanelOpen) hideScanPanel(); else showScanPanel();
        });
        ImageView settingsIcon = view.findViewById(R.id.settingsButtonIcon);
        View settingsButton = view.findViewById(R.id.settingsButton);
        if (settingsIcon != null) settingsIcon.setImageResource(TablerIconRes.get(requireContext(), "ic_settings"));
        if (settingsButton != null) {
            MotionHelper.attachPressScale(settingsButton);
            settingsButton.setOnClickListener(v -> {
                AppPreferences.performHaptic(v);
                if (scanPanelOpen) hideScanPanel();
                if (homeMenuOpen) hideHomeMenu();
                if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).openSettings();
            });
        }
        ImageView moreIcon = view.findViewById(R.id.moreButtonIcon);
        View moreButton = view.findViewById(R.id.moreButton);
        if (moreIcon != null) moreIcon.setImageResource(TablerIconRes.get(requireContext(), "ic_dots_vertical"));
        if (moreButton != null) {
            MotionHelper.attachPressScale(moreButton);
            moreButton.setOnClickListener(v -> {
                AppPreferences.performHaptic(v);
                if (homeMenuOpen) hideHomeMenu(); else showHomeMenu();
            });
        }
        android.widget.LinearLayout headerActions = homeHeader.actions();
        if (headerActions != null) {
            reparentToHeaderActions(headerActions, scanStatusButton);
            reparentToHeaderActions(headerActions, settingsButton);
            reparentToHeaderActions(headerActions, moreButton);
        }
        homeMenuPanel = view.findViewById(R.id.homeMenuPanel);
        homeMenuCard = view.findViewById(R.id.homeMenuCard);
        homeMenuArrow = view.findViewById(R.id.homeMenuArrow);
        homeMenuContent = view.findViewById(R.id.homeMenuContent);
        moreButtonView = moreButton;
        scanScrim.setOnClickListener(v -> { hideScanPanel(); hideHomeMenu(); });
        toolbar.setOnClickListener(v -> { hideScanPanel(); hideHomeMenu(); });
        applyIdleScanPanel();

        ImageView favoritesIcon = view.findViewById(R.id.favoritesIcon);
        if (favoritesIcon != null) favoritesIcon.setImageResource(TablerIconRes.get(requireContext(), "ic_star_filled"));
        if (heroCard != null) MotionHelper.animateEntrance(heroCard, 0);
        if (filterRow != null) MotionHelper.animateEntrance(filterRow, 1);

        loadProfileAvatar();
        loadSaved();
        loadFavorites();
        maybeAutoScanOnLaunch();
    }

    private void setupHero(View view) {
        Context ctx = requireContext();
        homeAppBar = view.findViewById(R.id.homeAppBar);
        if (homeAppBar != null) homeAppBar.addOnOffsetChangedListener((ab, offset) -> appBarOffset = offset);
        heroCard = view.findViewById(R.id.heroCard);
        filterRow = view.findViewById(R.id.filterRow);
        heroTitle = view.findViewById(R.id.heroTitle);
        heroSubtitle = view.findViewById(R.id.heroSubtitle);
        heroShape = view.findViewById(R.id.heroShape);
        heroShapeSmall = view.findViewById(R.id.heroShapeSmall);

        int primary = com.google.android.material.color.MaterialColors.getColor(view,
                androidx.appcompat.R.attr.colorPrimary, 0xFF6750A4);
        String shapeId = AppPreferences.getIconShape(ctx);
        boolean spin = !AppPreferences.isReduceMotion(ctx);
        if (heroShape != null) {
            heroShape.setFillColor((primary & 0x00FFFFFF) | 0x26000000);
            heroShape.setShape(shapeId, false);
            heroShape.setIdleSpin(spin);
        }
        if (heroShapeSmall != null) {
            heroShapeSmall.setFillColor((primary & 0x00FFFFFF) | 0x33000000);
            heroShapeSmall.setShape(shapeId, false);
            heroShapeSmall.setRotation(24f);
            heroShapeSmall.setIdleSpin(spin);
        }
        if (updateAllButtonView != null) MotionHelper.attachPressScale(updateAllButtonView);
        applyHeroState(0, 0, 0);
    }

    private void applyHeroState(int installed, int updates, int available) {
        if (!isAdded() || heroTitle == null) return;
        boolean hasData = installed + available > 0;
        if (!hasData) {
            heroTitle.setText(R.string.home_hero_empty);
            heroSubtitle.setText(R.string.home_hero_sub_empty);
        } else {
            if (updates > 0) {
                heroTitle.setText(getResources().getQuantityString(R.plurals.home_hero_updates, updates, updates));
            } else {
                heroTitle.setText(R.string.home_hero_all_good);
            }
            java.text.NumberFormat nf = java.text.NumberFormat.getInstance();
            heroSubtitle.setText(nf.format(installed) + " " + getString(R.string.home_stat_installed)
                    + " \u00B7 " + nf.format(updates) + " " + getString(R.string.home_stat_updates)
                    + " \u00B7 " + nf.format(available) + " " + getString(R.string.home_stat_available));
        }
        menuInstalled = installed;
        menuUpdates = updates;
        menuAvailable = available;
        heroInstallMode = updates == 0 && pendingRestoreCount > 0;
        if (heroInstallMode) {
            heroTitle.setText(getString(R.string.home_hero_restore, pendingRestoreCount));
        }
        if (updateAllButtonView != null) {
            if (updateAllButtonView instanceof com.google.android.material.button.MaterialButton) {
                ((com.google.android.material.button.MaterialButton) updateAllButtonView)
                        .setText(heroInstallMode ? R.string.install_all : R.string.update_all);
            }
            updateAllButtonView.setVisibility(updates > 0 || heroInstallMode ? View.VISIBLE : View.GONE);
        }
    }

    private boolean fabMenuOpen = false;
    private View fabScrimView;
    private View fabMenuView;
    private View fabMainView;
    private View fabContainerView;

    private void setupFabMenu(View root, View fab) {
        Context ctx = requireContext();
        fabMainView = fab;
        fabContainerView = root.findViewById(R.id.fabContainer);
        fabScrimView = root.findViewById(R.id.fabScrim);
        fabMenuView = root.findViewById(R.id.fabMenu);
        com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton gh = root.findViewById(R.id.fabActionGithub);
        com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton play = root.findViewById(R.id.fabActionPlay);
        if (gh != null) {
            gh.setIconResource(TablerIconRes.get(ctx, "ic_brand_github"));
            gh.setOnClickListener(v -> { toggleFabMenu(false); showAddRepositoryDialog(); });
        }
        if (play != null) {
            int id = ctx.getResources().getIdentifier("ic_brand_google_play", "drawable", ctx.getPackageName());
            play.setIconResource(id != 0 ? id : TablerIconRes.get(ctx, "ic_building_store"));
            play.setOnClickListener(v -> { toggleFabMenu(false); openPlayExplorer(); });
        }
        if (fabScrimView != null) fabScrimView.setOnClickListener(v -> toggleFabMenu(false));
    }

    private android.animation.ValueAnimator fabIconAnimator;

    private void turnFabIcon(boolean open, long duration, boolean animate, android.animation.TimeInterpolator interpolator) {
        if (!(fabMainView instanceof android.widget.ImageView)) return;
        final android.widget.ImageView icon = (android.widget.ImageView) fabMainView;
        if (fabIconAnimator != null) fabIconAnimator.cancel();
        int from = icon.getDrawable() == null ? 0 : icon.getDrawable().getLevel();
        int to = open ? 10000 : 0;
        if (!animate || duration <= 0L) { icon.setImageLevel(to); return; }
        fabIconAnimator = android.animation.ValueAnimator.ofInt(from, to);
        fabIconAnimator.setDuration(duration);
        fabIconAnimator.setInterpolator(interpolator);
        fabIconAnimator.addUpdateListener(a -> icon.setImageLevel((Integer) a.getAnimatedValue()));
        fabIconAnimator.start();
    }

    private void toggleFabMenu(boolean open) {
        if (fabMenuView == null || fabScrimView == null || fabMainView == null || open == fabMenuOpen) return;
        fabMenuOpen = open;
        final Context ctx = requireContext();
        final boolean reduced = AppPreferences.isReduceMotion(ctx);
        final float d = getResources().getDisplayMetrics().density;
        final ViewGroup menu = (ViewGroup) fabMenuView;
        final int n = menu.getChildCount();
        long dur = MotionHelper.getDuration(ctx, 200, 380);
        fabScrimView.animate().cancel();
        for (int i = 0; i < n; i++) menu.getChildAt(i).animate().cancel();

        if (open) {
            fabScrimView.setVisibility(View.VISIBLE);
            menu.setVisibility(View.VISIBLE);
            if (fabContainerView != null) {
                fabContainerView.bringToFront();
            }
            if (reduced) {
                fabScrimView.setAlpha(1f);
                turnFabIcon(true, 0L, false, null);
                for (int i = 0; i < n; i++) {
                    View c = menu.getChildAt(i);
                    c.setAlpha(1f); c.setTranslationY(0f); c.setScaleX(1f); c.setScaleY(1f);
                }
                return;
            }
            fabScrimView.animate().alpha(1f).setDuration(dur).start();
            turnFabIcon(true, dur, true, new android.view.animation.AccelerateDecelerateInterpolator());
            for (int i = 0; i < n; i++) {
                View c = menu.getChildAt(i);
                c.setAlpha(0f);
                c.setTranslationY(24f * d);
                c.setScaleX(0.7f);
                c.setScaleY(0.7f);
                c.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                        .setStartDelay((n - 1 - i) * 60L)
                        .setDuration(dur)
                        .setInterpolator(new OvershootInterpolator(1.6f))
                        .start();
            }
        } else {
            if (reduced) {
                fabScrimView.setAlpha(0f);
                fabScrimView.setVisibility(View.GONE);
                menu.setVisibility(View.GONE);
                turnFabIcon(false, 0L, false, null);
                return;
            }
            long out = (long) (dur * 0.7f);
            fabScrimView.animate().alpha(0f).setDuration(out)
                    .withEndAction(() -> { if (!fabMenuOpen && fabScrimView != null) fabScrimView.setVisibility(View.GONE); })
                    .start();
            turnFabIcon(false, out, true, new DecelerateInterpolator(1.6f));
            for (int i = 0; i < n; i++) {
                View c = menu.getChildAt(i);
                final boolean last = i == 0;
                c.animate().alpha(0f).translationY(16f * d).scaleX(0.8f).scaleY(0.8f)
                        .setStartDelay(i * 40L)
                        .setDuration(out)
                        .setInterpolator(new AccelerateInterpolator(1.4f))
                        .withEndAction(() -> { if (last && !fabMenuOpen) menu.setVisibility(View.GONE); })
                        .start();
            }
        }
    }

    private void openPlayExplorer() {
        if (!isAdded()) return;
        AppPreferences.performHaptic(getView());
        startActivity(new Intent(requireContext(), PlaySearchActivity.class));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void reparentToHeaderActions(android.widget.LinearLayout target, View child) {
        if (target == null || child == null) return;
        android.view.ViewGroup parent = (android.view.ViewGroup) child.getParent();
        if (parent != null) parent.removeView(child);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                (int) dp(36), (int) dp(36));
        lp.setMarginStart((int) dp(6));
        child.setLayoutParams(lp);
        target.addView(child);
    }

    private void applyIdleScanPanel() {
        setScanPanelBusy(false);
        setStageState(stageFdroidRow, StageState.DONE);
        setStageState(stageManualRow, StageState.DONE);
    }

    private void setScanPanelBusy(boolean busy) {
        if (overallProgress != null) overallProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (scanPercentText != null) scanPercentText.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (scanCurrentItemText != null) scanCurrentItemText.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (scanTitleText != null) {
            CharSequence done = appCount != null ? appCount.getText() : "";
            if (busy || done == null || done.length() == 0) scanTitleText.setText(R.string.scan_progress_title);
            else scanTitleText.setText(done);
        }
    }

    private void setScanIconSpinning(boolean spinning) {
        if (scanStatusIcon == null) return;
        if (spinning) {
            if (scanIconSpin != null && scanIconSpin.isRunning()) return;
            scanIconSpin = ObjectAnimator.ofFloat(scanStatusIcon, View.ROTATION, 0f, 360f);
            scanIconSpin.setDuration(1400);
            scanIconSpin.setRepeatCount(ObjectAnimator.INFINITE);
            scanIconSpin.setInterpolator(new LinearInterpolator());
            scanIconSpin.start();
        } else {
            if (scanIconSpin != null) scanIconSpin.cancel();
            scanIconSpin = null;
            scanStatusIcon.animate().rotation(0f).setDuration(220).start();
        }
    }

    private float positionPanel(View panel, ViewGroup card, View arrow, View anchor, float widthDp, float cornerDp) {
        View parent = (View) panel.getParent();
        int[] p = new int[2];
        int[] b = new int[2];
        parent.getLocationInWindow(p);
        anchor.getLocationInWindow(b);
        float margin = dp(8);
        float width = Math.min(dp(widthDp), parent.getWidth() - 2 * margin);
        float centerX = b[0] - p[0] + anchor.getWidth() / 2f;
        float left = Math.max(margin, Math.min(centerX - width / 2f, parent.getWidth() - width - margin));
        float leftEdge = parent.getPaddingLeft();

        float top = b[1] - p[1] - parent.getPaddingTop() + anchor.getHeight() + dp(2);
        ViewGroup.LayoutParams raw = panel.getLayoutParams();
        if (raw instanceof androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) {
            androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams c =
                    (androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) raw;
            c.width = Math.round(width);
            c.setMargins(Math.round(left - leftEdge), Math.round(top), 0, 0);
            panel.setLayoutParams(c);
        }
        float arrowW = dp(22);
        float edge = arrowW / 2f + dp(2f);
        float tail = Math.max(edge, Math.min(centerX - left, width - edge));
        if (card instanceof MaterialCardView) {
            float full = dp(cornerDp) * AppPreferences.getCornerScale(requireContext());
            float limit = Math.max(dp(4f), Math.min(tail, width - tail) - arrowW / 2f);
            MaterialCardView mc = (MaterialCardView) card;
            mc.setShapeAppearanceModel(mc.getShapeAppearanceModel().toBuilder()
                    .setAllCornerSizes(Math.min(full, limit))
                    .build());
        }
        arrow.setTranslationX(tail - arrowW / 2f);
        return tail;
    }

    private void revealPanel(View panel, ViewGroup card, View arrow, View anchor, float widthDp, float cornerDp) {
        Context ctx = requireContext();
        long duration = MotionHelper.getDuration(ctx, 260, 420);
        panel.animate().cancel();
        scanScrim.animate().cancel();
        scanScrim.setVisibility(View.VISIBLE);
        scanScrim.animate().alpha(1f).setDuration(duration).start();
        final float pivotX = positionPanel(panel, card, arrow, anchor, widthDp, cornerDp);
        panel.setVisibility(View.VISIBLE);
        panel.setAlpha(0f);
        panel.setScaleX(0.55f);
        panel.setScaleY(0.55f);
        panel.setTranslationY(-dp(6));

        panel.setPivotX(pivotX);
        panel.setPivotY(0f);
        panel.animate()
                .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(duration)
                .setInterpolator(new OvershootInterpolator(1.1f))
                .setListener(null)
                .start();
        if (duration > 0 && card.getChildCount() > 0 && card.getChildAt(0) instanceof ViewGroup) {
            ViewGroup content = (ViewGroup) card.getChildAt(0);
            for (int i = 0; i < content.getChildCount(); i++) {
                View child = content.getChildAt(i);
                child.animate().cancel();
                child.setAlpha(0f);
                child.setTranslationY(dp(10));
                child.animate().alpha(1f).translationY(0f)
                        .setStartDelay(70L + i * 45L)
                        .setDuration(duration)
                        .setInterpolator(new DecelerateInterpolator(1.8f))
                        .start();
            }
        }
        anchor.animate().cancel();
        anchor.setScaleX(0.8f);
        anchor.setScaleY(0.8f);
        anchor.animate().scaleX(1f).scaleY(1f).setDuration(duration)
                .setInterpolator(new OvershootInterpolator(2.2f)).start();
    }

    private void concealPanel(final View panel) {
        long duration = MotionHelper.getDuration(requireContext(), 170, 240);
        if (!scanPanelOpen && !homeMenuOpen) {
            scanScrim.animate().cancel();
            scanScrim.animate().alpha(0f).setDuration(duration)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override public void onAnimationEnd(Animator animation) {
                            if (!scanPanelOpen && !homeMenuOpen && scanScrim != null) scanScrim.setVisibility(View.GONE);
                        }
                    }).start();
        }
        panel.animate().cancel();
        panel.animate()
                .alpha(0f).scaleX(0.88f).scaleY(0.88f).translationY(-dp(6))
                .setDuration(duration)
                .setInterpolator(new AccelerateInterpolator(1.4f))
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        boolean open = panel == scanPanel ? scanPanelOpen : homeMenuOpen;
                        if (!open) panel.setVisibility(View.GONE);
                    }
                }).start();
    }

    private void showScanPanel() {
        if (scanPanelOpen || scanProgressCard == null || scanPanel == null) return;
        if (homeMenuOpen) hideHomeMenu();
        scanPanelOpen = true;
        if (!scanRunning) applyIdleScanPanel();
        revealPanel(scanPanel, scanProgressCard, scanPanelArrow, scanStatusButton, 300, 32f);
    }

    private void hideScanPanel() {
        if (!scanPanelOpen || scanProgressCard == null || scanPanel == null) return;
        scanPanelOpen = false;
        concealPanel(scanPanel);
    }

    private void showHomeMenu() {
        if (homeMenuOpen || homeMenuCard == null || homeMenuPanel == null || moreButtonView == null) return;
        if (scanPanelOpen) hideScanPanel();
        homeMenuOpen = true;
        HomeMenuPanel.populate(homeMenuContent, buildMenuConfig());
        revealPanel(homeMenuPanel, homeMenuCard, homeMenuArrow, moreButtonView, 280, 14f);
    }

    private void hideHomeMenu() {
        if (!homeMenuOpen || homeMenuCard == null || homeMenuPanel == null) return;
        homeMenuOpen = false;
        concealPanel(homeMenuPanel);
    }

    private HomeMenuPanel.Config buildMenuConfig() {
        final SortMode[] modes = {SortMode.LAST_UPDATED, SortMode.NAME, SortMode.SOURCE};
        final String[] sourceKeys = {SOURCE_ALL, SOURCE_FDROID, SOURCE_LUMEN, SOURCE_MANUAL};
        int sourceIndex = 0;
        for (int i = 0; i < sourceKeys.length; i++) if (sourceKeys[i].equals(selectedSource)) sourceIndex = i;
        HomeMenuPanel.Config c = new HomeMenuPanel.Config();
        c.installedCount = menuInstalled;
        c.updatesCount = menuUpdates;
        c.availableCount = menuAvailable;
        c.installedLabel = getString(R.string.home_stat_installed);
        c.updatesLabel = getString(R.string.home_stat_updates);
        c.availableLabel = getString(R.string.home_stat_available);
        c.actions.add(new HomeMenuPanel.Action("ic_refresh", getString(R.string.home_menu_check_updates), null, () -> {
            hideHomeMenu();
            startScan();
        }));
        if (menuUpdates > 0) {
            c.actions.add(new HomeMenuPanel.Action("ic_download", getString(R.string.update_all),
                    String.valueOf(menuUpdates), () -> {
                hideHomeMenu();
                updateAll();
            }));
        } else if (pendingRestoreCount > 0) {
            c.actions.add(new HomeMenuPanel.Action("ic_download", getString(R.string.install_all),
                    String.valueOf(pendingRestoreCount), () -> {
                hideHomeMenu();
                installAllMissing();
            }));
        }
        c.actions.add(new HomeMenuPanel.Action("ic_package", getString(R.string.manage_repos), null, () -> {
            hideHomeMenu();
            startActivity(new Intent(requireContext(), ManageRepositoriesActivity.class));
        }));
        c.actions.add(new HomeMenuPanel.Action("ic_database", getString(R.string.settings_backup_restore), null, () -> {
            hideHomeMenu();
            startActivity(new Intent(requireContext(), BackupRestoreActivity.class));
        }));
        c.sortTitle = getString(R.string.home_menu_sort);
        c.sortLabels = new String[]{getString(R.string.sort_latest), getString(R.string.sort_name), getString(R.string.sort_source)};
        c.sortSelected = sortIndex(selectedSort);
        c.onSort = position -> {
            if (position < 0 || position >= modes.length) return;
            selectedSort = modes[position];
            AppPreferences.setHomeSort(requireContext(), selectedSort.name());
            loadSaved();
            hideHomeMenu();
        };
        c.sourceTitle = getString(R.string.sort_source);
        c.sourceLabels = new String[]{getString(R.string.source_all), getString(R.string.source_fdroid),
                getString(R.string.source_lumen_store), getString(R.string.source_manual)};
        c.sourceSelected = sourceIndex;
        c.onSource = position -> {
            if (position < 0 || position >= sourceKeys.length) return;
            selectedSource = sourceKeys[position];
            loadSaved();
            hideHomeMenu();
        };
        c.listTitle = getString(R.string.home_menu_list);
        c.updatesFirstLabel = updatesFirstLabel == null ? getString(R.string.sort_updates_first) : updatesFirstLabel;
        c.updatesFirst = updatesFirst;
        c.onUpdatesFirst = checked -> {
            updatesFirst = checked;
            AppPreferences.setHomeUpdatesFirst(requireContext(), updatesFirst);
            loadSaved();
        };
        c.notInstalledLastLabel = getString(R.string.sort_not_installed_last);
        c.notInstalledLast = notInstalledLast;
        c.onNotInstalledLast = checked -> {
            notInstalledLast = checked;
            AppPreferences.setHomeNotInstalledLast(requireContext(), notInstalledLast);
            loadSaved();
        };
        return c;
    }

    private void finishScanPanel() {
        setScanIconSpinning(false);
        if (!isAdded()) return;
        applyIdleScanPanel();
    }

    private void stopScanUi() {
        scanRunning = false;
        setScanIconSpinning(false);
        if (swipe != null) swipe.setRefreshing(false);
        if (isAdded() && getView() != null) finishScanPanel();
    }

    private ItemTouchHelper swipeHelper;
    private RecyclerView swipeHelperRecycler;

    private void resetTouchHelper() {
        final ItemTouchHelper helper = swipeHelper;
        final RecyclerView rv = swipeHelperRecycler;
        if (helper == null || rv == null) return;
        rv.post(() -> {
            if (swipeHelper != helper || !isAdded()) return;
            helper.attachToRecyclerView(null);
            helper.attachToRecyclerView(rv);
            rv.invalidate();
        });
    }

    private void maybeAutoScanOnLaunch() {
        if (!isAdded()) return;
        final Context context = requireContext().getApplicationContext();
        executor.execute(() -> {
            boolean hasSavedData = !MatchStore.get(context).isEmpty();
            boolean autoCheckEnabled = AppPreferences.isAutoCheckUpdatesOnLaunch(context);
            boolean hasSources = FdroidIndexManager.isEnabled(context) || !RepositoryManager.get(context).isEmpty();
            if ((!hasSavedData && hasSources) || autoCheckEnabled) ui(this::startScan);
            else lastScanIndexEnabled = FdroidIndexManager.isEnabled(context);
        });
    }

    private static boolean isRemovable(MatchedApp app) {
        return app != null && ("manual".equalsIgnoreCase(app.getOrigin()) || app.isNotInstalled());
    }

    private static android.graphics.drawable.Drawable tintedDialogIcon(Context context, String name) {
        android.graphics.drawable.Drawable d = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, TablerIconRes.get(context, name));
        if (d == null) return null;
        d = androidx.core.graphics.drawable.DrawableCompat.wrap(d.mutate());
        int color = com.google.android.material.color.MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0xFF000000);
        androidx.core.graphics.drawable.DrawableCompat.setTint(d, color);
        return d;
    }

    private void confirmRemoveFromHome(MatchedApp item, @Nullable View animatedView, @Nullable Runnable onCancel) {
        if (!isAdded() || item == null) return;
        final boolean[] confirmed = {false};
        String label = item.getAppLabel() == null ? item.getPackageName() : item.getAppLabel();
        new MaterialAlertDialogBuilder(requireContext())
                .setIcon(tintedDialogIcon(requireContext(), "ic_trash"))
                .setTitle(R.string.home_remove_title)
                .setMessage(getString(R.string.home_remove_message, label))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.home_remove_confirm, (d, w) -> {
                    confirmed[0] = true;
                    Runnable remove = () -> {
                        if (!isAdded()) return;
                        MatchStore.remove(requireContext().getApplicationContext(), item.getStoreKey());
                        if (com.opensourcefinder.app.network.PlayStoreClient.isPlayApp(item) && item.getSourceUrl() != null) {
                            RepositoryManager.remove(requireContext().getApplicationContext(), item.getSourceUrl());
                        }
                        adapter.removeByStoreKey(item.getStoreKey());
                        updateCountLabel(adapter.getItemCount());
                        Toast.makeText(requireContext(), R.string.repository_app_removed, Toast.LENGTH_SHORT).show();
                    };
                    if (animatedView != null && !AppPreferences.isReduceMotion(requireContext())) {
                        animatedView.animate()
                                .alpha(0f).scaleX(0.92f).scaleY(0.92f)
                                .setDuration(MotionHelper.getDuration(requireContext(), 160, 230))
                                .setInterpolator(MotionHelper.getInterpolator(requireContext()))
                                .withEndAction(() -> {
                                    remove.run();
                                    animatedView.setAlpha(1f);
                                    animatedView.setScaleX(1f);
                                    animatedView.setScaleY(1f);
                                })
                                .start();
                    } else {
                        remove.run();
                    }
                })
                .setOnDismissListener(d -> {
                    if (!confirmed[0] && onCancel != null) onCancel.run();
                })
                .show();
    }

    private void updateCountLabel(int count) {
        if (appCount == null || !isAdded()) return;
        int safe = Math.max(0, count);
        appCount.setText(safe == 0 ? getString(R.string.no_apps_found) : getString(R.string.apps_found, safe));
        if (empty != null) empty.setVisibility(safe == 0 ? View.VISIBLE : View.GONE);
    }

    private DownloadTracker.Listener autoInstallListener(final Context app) {
        return new DownloadTracker.Listener() {
            @Override public void onProgress(DownloadTracker.Entry entry) {}
            @Override public void onCompleted(DownloadTracker.Entry entry) {
                mainHandler.post(() -> installDownloaded(app, entry));
            }
            @Override public void onFailed(DownloadTracker.Entry entry) {
                mainHandler.post(() -> Toast.makeText(app, R.string.download_failed, Toast.LENGTH_SHORT).show());
            }
        };
    }

    private static void installDownloaded(Context app, DownloadTracker.Entry entry) {
        if (entry == null || entry.installPrompted || entry.apkFile == null || !entry.apkFile.exists()) return;
        DownloadTracker.get().markInstallPrompted(entry.key);
        String pkg = entry.realPackageName != null ? entry.realPackageName : entry.key;
        boolean installed = true;
        try { app.getPackageManager().getPackageInfo(pkg, 0); } catch (Exception e) { installed = false; }
        if (installed && !UpdateManager.signaturesMatch(app, entry.apkFile, pkg)) {
            Toast.makeText(app, R.string.incompatible_version_title, Toast.LENGTH_LONG).show();
            return;
        }
        UpdateManager.installApk(app, entry.key, entry.apkFile, null);
    }

    private void startUpdateFromList(MatchedApp app) {
        if (!isAdded() || app == null || app.getPackageName() == null) return;
        final Context context = requireContext().getApplicationContext();
        String pkg = app.getPackageName();
        if (com.opensourcefinder.app.network.PlayStoreClient.isPlayApp(app)) {

            com.opensourcefinder.app.network.PlayStoreClient.openInPlayStore(requireContext(), pkg);
            return;
        }
        DownloadTracker.Entry existing = DownloadTracker.get().getEntry(pkg);
        if (existing != null && existing.state == DownloadTracker.State.DOWNLOADING) {
            DownloadTracker.get().stop(pkg);
            return;
        }
        if (existing != null && existing.state == DownloadTracker.State.COMPLETED
                && existing.apkFile != null && existing.apkFile.exists()) {
            existing.installPrompted = false;
            installDownloaded(context, existing);
            return;
        }
        if (existing != null && existing.state == DownloadTracker.State.STOPPED) {
            DownloadTracker.get().start(context, pkg, existing.url, existing.fileName, autoInstallListener(context));
            adapter.notifyDownloadChanged(pkg);
            return;
        }
        if (app.getDownloadUrl() == null) {
            openDetail(app);
            return;
        }
        String tag = app.getLatestTag() == null ? "update" : app.getLatestTag();
        String label = app.getAppLabel() == null ? pkg : app.getAppLabel();
        DownloadTracker.get().start(context, pkg, app.getDownloadUrl(),
                label + "-" + tag + ".apk", autoInstallListener(context), app.getIconUrl());
        adapter.notifyDownloadChanged(pkg);
        Toast.makeText(requireContext(), getString(R.string.update_started, label), Toast.LENGTH_SHORT).show();
    }

    private static SortMode parseSort(String value) {
        try { return SortMode.valueOf(value); } catch (Exception e) { return SortMode.LAST_UPDATED; }
    }

    private void setupSortControls(View root) {
        sortDropdown = root.findViewById(R.id.sortDropdown);
        updatesFirstLabel = getString(R.string.sort_updates_first);
        if (sortDropdown == null) return;
        final String[] labels = {
                getString(R.string.sort_latest),
                getString(R.string.sort_name),
                getString(R.string.sort_source)
        };
        final SortMode[] modes = {SortMode.LAST_UPDATED, SortMode.NAME, SortMode.SOURCE};
        sortDropdown.setText(labels[sortIndex(selectedSort)]);
        sortDropdown.setOnClickListener(anchor -> {
            AppPreferences.performHaptic(sortDropdown);
            DropdownPopup.show(sortDropdown, labels, sortIndex(selectedSort), position -> {
                if (position < 0 || position >= modes.length) return;
                selectedSort = modes[position];
                sortDropdown.setText(labels[position]);
                AppPreferences.setHomeSort(requireContext(), selectedSort.name());
                loadSaved();
            }, updatesFirstLabel, updatesFirst, checked -> {
                updatesFirst = checked;
                AppPreferences.setHomeUpdatesFirst(requireContext(), checked);
                loadSaved();
            });
        });
    }

    private static int sortIndex(SortMode mode) {
        return mode == SortMode.NAME ? 1 : mode == SortMode.SOURCE ? 2 : 0;
    }

    private static boolean hasPendingUpdate(MatchedApp app) {
        return app != null && !app.isNotInstalled() && app.isUpdateAvailable();
    }

    private final Runnable installEventsListener = () -> { if (isAdded()) scheduleRefreshVisible(); };

    @Override public void onResume() {
        super.onResume();
        com.opensourcefinder.app.install.InstallEvents.addListener(installEventsListener);
        refreshVisibleState();
        if (homeLoaded && seenStoreRevision != MatchStore.revision) scheduleHomeReload();
    }

    @Override public void onPause() {
        LiveBackgroundView.setScrollPaused(false);
        com.opensourcefinder.app.install.InstallEvents.removeListener(installEventsListener);
        super.onPause();
    }

    private volatile boolean refreshQueued = false;
    private final Runnable refreshRunner = () -> {
        refreshQueued = false;
        if (isAdded()) refreshVisibleState();
    };

    private void scheduleRefreshVisible() {
        if (refreshQueued) return;
        refreshQueued = true;
        mainHandler.postDelayed(refreshRunner, 150L);
    }

    private volatile boolean reloadQueued = false;
    private final Runnable reloadRunner = () -> {
        reloadQueued = false;
        if (isAdded()) loadSaved();
    };

    private void scheduleHomeReload() {
        if (reloadQueued) return;
        reloadQueued = true;
        mainHandler.postDelayed(reloadRunner, 120L);
    }

    private void refreshVisibleState() {
        if (!isAdded()) return;
        final Context context = requireContext().getApplicationContext();
        String currentShape = AppPreferences.getIconShape(context);
        if (heroShape != null) heroShape.setShape(currentShape, true);
        if (heroShapeSmall != null) heroShapeSmall.setShape(currentShape, true);
        boolean indexEnabled = FdroidIndexManager.isEnabled(context);
        if (lastScanIndexEnabled != null && lastScanIndexEnabled != indexEnabled && !scanRunning) {
            startScan();
            return;
        }
        executor.execute(() -> {
            DownloadTracker.get().detectInstalledPackages(context);
            syncInstalledFromDownloads(context);
            cleanupDuplicateNotInstalled(context);
            ui(() -> {
                loadSaved();
                loadFavorites();
                loadProfileAvatar();
            });
        });
    }

    @Override public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) LiveBackgroundView.setScrollPaused(false);
        if (!hidden) refreshVisibleState();
    }

    private void loadFavorites() {
        if (!isAdded() || favoritesRecycler == null) return;
        final Set<String> favoritePkgs = FavoriteManager.all(requireContext());
        final Context context = requireContext().getApplicationContext();
        if (favoritePkgs.isEmpty() || !FdroidIndexManager.isEnabled(context)) {
            favoritesSection.setVisibility(View.GONE);
            lastFavoritesKey = null;
            return;
        }
        final String key = new java.util.TreeSet<>(favoritePkgs).toString();
        if (key.equals(lastFavoritesKey)) return;
        lastFavoritesKey = key;
        executor.execute(() -> {
            try {
                List<ExploreApp> catalog = new FdroidIndexManager(context).loadExploreCatalog(new HashSet<>(), null);
                List<ExploreApp> favorites = new ArrayList<>();
                for (ExploreApp a : catalog) if (favoritePkgs.contains(a.getPackageName())) favorites.add(a);
                ui(() -> {
                    FavoritesAdapter fa = (FavoritesAdapter) favoritesRecycler.getAdapter();
                    fa.submit(favorites);
                    favoritesSection.setVisibility(favorites.isEmpty() ? View.GONE : View.VISIBLE);
                });
            } catch (Exception ignored) {}
        });
    }

    public void requestRescan() {
        if (isAdded()) startScan(false);
    }

    public void requestFullRescan() {
        if (isAdded()) startScan(true);
    }

    private static boolean isRestorable(MatchedApp app) {
        return app != null && app.isFromBackup() && app.isNotInstalled()
                && !com.opensourcefinder.app.network.PlayStoreClient.isPlayApp(app);
    }

    private static void refreshRestoreTargets(Context context, List<MatchedApp> targets) {
        if (targets.isEmpty() || !FdroidIndexManager.isEnabled(context)) return;
        List<InstalledAppInfo> pseudo = new ArrayList<>();
        for (MatchedApp app : targets) {
            if ("fdroid".equalsIgnoreCase(app.getSourceType())) {
                pseudo.add(new InstalledAppInfo(app.getPackageName(), app.getAppLabel(), "", 0L, null, null));
            }
        }
        if (pseudo.isEmpty()) return;
        Map<String, MatchedApp> fresh;
        try {
            fresh = new FdroidIndexManager(context).refreshAndMatchAllDefaults(pseudo, null, false);
        } catch (Exception e) {
            return;
        }
        for (MatchedApp app : targets) {
            MatchedApp latest = fresh.get(app.getPackageName());
            if (latest == null || latest.getDownloadUrl() == null) continue;
            app.setLatestTag(latest.getLatestTag());
            app.setLatestVersionCode(latest.getLatestVersionCode());
            app.setDownloadUrl(latest.getDownloadUrl());
            MatchStore.updateIfPresent(context, app);
        }
    }

    private void installAllMissing() {
        if (!isAdded() || adapter == null) return;
        final Context context = requireContext().getApplicationContext();
        Toast.makeText(requireContext(), R.string.install_all_preparing, Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            List<MatchedApp> targets = new ArrayList<>();
            for (MatchedApp app : MatchStore.get(context)) {
                if (isRestorable(app)) targets.add(app);
            }
            refreshRestoreTargets(context, targets);
            int started = 0;
            for (MatchedApp app : targets) {
                if (app.getDownloadUrl() == null) continue;
                DownloadTracker.Entry e = DownloadTracker.get().getEntry(app.getPackageName());
                if (e != null && e.state == DownloadTracker.State.DOWNLOADING) continue;
                String tag = app.getLatestTag() == null ? "latest" : app.getLatestTag();
                String label = app.getAppLabel() == null ? app.getPackageName() : app.getAppLabel();
                DownloadTracker.get().start(context, app.getPackageName(), app.getDownloadUrl(),
                        label + "-" + tag + ".apk", autoInstallListener(context), app.getIconUrl());
                started++;
            }
            final int count = started;
            ui(() -> {
                if (!isAdded()) return;
                if (count > 0) {
                    Toast.makeText(requireContext(), getString(R.string.install_all_started, count), Toast.LENGTH_SHORT).show();
                    adapter.notifyDataSetChanged();
                } else {
                    Toast.makeText(requireContext(), R.string.install_all_none, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void updateAll() {
        if (!isAdded() || adapter == null) return;
        final Context context = requireContext().getApplicationContext();
        executor.execute(() -> {
            List<MatchedApp> list = MatchStore.get(context);
            int started = 0;
            int playPending = 0;
            for (MatchedApp app : list) {
                if (app != null && !app.isNotInstalled() && app.isUpdateAvailable()
                        && com.opensourcefinder.app.network.PlayStoreClient.isPlayApp(app)) playPending++;
                if (app == null || app.isNotInstalled() || !app.isUpdateAvailable() || app.getDownloadUrl() == null) continue;
                DownloadTracker.Entry e = DownloadTracker.get().getEntry(app.getPackageName());
                if (e != null && e.state == DownloadTracker.State.DOWNLOADING) continue;
                String tag = app.getLatestTag() == null ? "update" : app.getLatestTag();
                final String packageName = app.getPackageName();
                DownloadTracker.get().start(context, packageName, app.getDownloadUrl(),
                        app.getAppLabel() + "-" + tag + ".apk", autoInstallListener(context), app.getIconUrl());
                started++;
            }
            final int count = started;
            final int playCount = playPending;
            ui(() -> {
                if (count > 0) {
                    Toast.makeText(requireContext(), getString(R.string.update_all_started, count), Toast.LENGTH_SHORT).show();
                    adapter.notifyDataSetChanged();
                } else if (playCount > 0) {
                    Toast.makeText(requireContext(), R.string.update_all_play_hint, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(requireContext(), R.string.no_updates_available, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void cleanupDuplicateNotInstalled(Context context) {
        List<MatchedApp> list = MatchStore.get(context);
        java.util.Set<String> installedSources = new java.util.HashSet<>();
        java.util.Set<String> installedPkgs = new java.util.HashSet<>();
        for (MatchedApp m : list) {
            if (m != null && !m.isNotInstalled()) {
                if (m.getSourceUrl() != null) installedSources.add(normalizeSource(m.getSourceUrl()));
                if (m.getPackageName() != null) installedPkgs.add(m.getPackageName());
            }
        }
        java.util.LinkedHashMap<String, MatchedApp> uniqueManual = new java.util.LinkedHashMap<>();
        List<MatchedApp> out = new ArrayList<>();
        boolean changed = false;
        for (MatchedApp m : list) {
            if (m == null) { changed = true; continue; }
            if (m.isNotInstalled()) {
                String src = normalizeSource(m.getSourceUrl());
                if ((src != null && installedSources.contains(src))
                        || (m.getPackageName() != null && installedPkgs.contains(m.getPackageName()))) {
                    changed = true;
                    continue;
                }
                if ("manual".equalsIgnoreCase(m.getOrigin()) && src != null) {
                    MatchedApp previous = uniqueManual.get(src);
                    if (previous == null || isBetterManualMatch(m, previous)) {
                        if (previous != null) changed = true;
                        uniqueManual.put(src, m);
                    } else {
                        changed = true;
                    }
                    continue;
                }
            }
            out.add(m);
        }
        if (!uniqueManual.isEmpty()) out.addAll(uniqueManual.values());
        if (changed) MatchStore.replaceAll(context, out);
    }

    private boolean isBetterManualMatch(MatchedApp candidate, MatchedApp current) {
        if (candidate == null) return false;
        if (current == null) return true;
        boolean candidateRealPackage = candidate.getPackageName() != null
                && !candidate.getPackageName().startsWith("gitlab.")
                && !candidate.getPackageName().startsWith("codeberg.")
                && !candidate.getPackageName().startsWith("web.");
        boolean currentRealPackage = current.getPackageName() != null
                && !current.getPackageName().startsWith("gitlab.")
                && !current.getPackageName().startsWith("codeberg.")
                && !current.getPackageName().startsWith("web.");
        if (candidateRealPackage != currentRealPackage) return candidateRealPackage;
        if ((candidate.getDownloadUrl() != null) != (current.getDownloadUrl() != null)) return candidate.getDownloadUrl() != null;
        return candidate.getLatestTag() != null && current.getLatestTag() == null;
    }

    private void syncInstalledFromDownloads(Context context) {
        for (DownloadTracker.Entry e : DownloadTracker.get().snapshot()) {
            if (e.state != DownloadTracker.State.INSTALLED) continue;
            String pkg = e.realPackageName != null ? e.realPackageName : e.key;
            try {
                android.content.pm.PackageInfo pi = context.getPackageManager().getPackageInfo(pkg, 0);
                MatchedApp found = null;
                for (MatchedApp m : MatchStore.get(context)) {
                    if (pkg.equals(m.getPackageName()) || e.key.equals(m.getPackageName())) {
                        found = m;
                        break;
                    }
                }
                if (found == null) continue;
                final String oldKey = found.getPackageName();
                if (!pkg.equals(oldKey)) found.setPackageName(pkg);
                com.opensourcefinder.app.install.InstallState.recordInstalledTag(context, found, e);
                com.opensourcefinder.app.install.InstallState.refresh(context, found);
                com.opensourcefinder.app.install.InstallState.markCurrentIfFromLatest(context, found, e);
                try {
                     synchronized (iconCache) {
                         iconCache.put(pkg, context.getPackageManager().getApplicationIcon(pkg));
                     }
                     found.setAppLabel(context.getPackageManager().getApplicationLabel(pi.applicationInfo).toString());
                } catch (Exception ignored) {}
                 MatchStore.upsertReplacing(context, found, oldKey, e.key);
            } catch (Exception ignored) {}
        }
    }

    private void loadSaved() {
        if (adapter == null || !isAdded()) return;
        final Context context = requireContext().getApplicationContext();
        executor.execute(() -> {
            List<MatchedApp> saved = MatchStore.get(context);
            java.util.Map<String, com.opensourcefinder.app.install.InstallState.Info> installed =
                    com.opensourcefinder.app.install.InstallState.readAll(context);
            List<MatchedApp> toKeep = new ArrayList<>();
            boolean changedAny = false;
            for (MatchedApp app : saved) {
                boolean before = app.isNotInstalled();
                refreshInstalledFields(context, app, installed.get(app.getPackageName()));
                if (before != app.isNotInstalled()) changedAny = true;
                toKeep.add(app);
            }
            if (changedAny) MatchStore.replaceAll(context, toKeep);
            ui(() -> applyHomePresentation(toKeep));
        });
    }

    private void applyHomePresentation(List<MatchedApp> saved) {
        if (!isAdded() || adapter == null || getView() == null) return;
        lastSaved = saved == null ? new ArrayList<>() : new ArrayList<>(saved);
        final List<MatchedApp> source = new ArrayList<>(lastSaved);
        final String query = homeQuery == null ? "" : homeQuery;
        final String normalizedQuery = query.toLowerCase(java.util.Locale.ROOT);
        final SortMode sortMode = selectedSort;
        final String sourceFilter = selectedSource;
        final boolean prioritizeUpdates = updatesFirst;
        final boolean pushNotInstalledDown = notInstalledLast;
        final boolean lazyEnabled = AppPreferences.isLazyIndexEnabled(requireContext());
        final String fdroidLabel = getString(R.string.source_fdroid);
        final String izzyLabel = getString(R.string.source_izzyondroid);
        final String lumenLabel = getString(R.string.source_lumen_store);
        final String manualLabel = getString(R.string.source_manual);
        final long generation = ++homePresentationGeneration;

        presentationExecutor.execute(() -> {
            List<MatchedApp> displayed = new ArrayList<>();
            int libInstalled = 0, libUpdates = 0, libAvailable = 0, restorable = 0;
            for (MatchedApp app : source) {
                if (app == null) continue;
                if (!lazyEnabled && "fdroid".equalsIgnoreCase(app.getSourceType())
                        && !"manual".equalsIgnoreCase(app.getOrigin())
                        && app.getDownloadUrl() != null
                        && app.getDownloadUrl().contains("izzysoft")) continue;
                if (app.isNotInstalled()) libAvailable++; else libInstalled++;
                if (isRestorable(app)) restorable++;
                if (hasPendingUpdate(app)) libUpdates++;
                if (SOURCE_FDROID.equals(sourceFilter) && (!"fdroid".equalsIgnoreCase(app.getSourceType()) || app.isLumenSource())) continue;
                if (SOURCE_LUMEN.equals(sourceFilter) && !app.isLumenSource()) continue;
                if (SOURCE_MANUAL.equals(sourceFilter) && "fdroid".equalsIgnoreCase(app.getSourceType())) continue;
                if (!matchesHomeQuery(app, normalizedQuery)) continue;
                displayed.add(app);
            }
            Comparator<MatchedApp> byName = Comparator.comparing(
                    (MatchedApp app) -> app.getAppLabel() == null
                            ? "" : app.getAppLabel().toLowerCase(java.util.Locale.ROOT));
            if (sortMode == SortMode.NAME) {
                Collections.sort(displayed, byName);
            } else if (sortMode == SortMode.SOURCE) {
                Comparator<MatchedApp> bySource = Comparator.comparing((MatchedApp app) ->
                        app.isLumenSource() ? lumenLabel
                                : app.isLazyIndexSource() ? izzyLabel
                                : "fdroid".equalsIgnoreCase(app.getSourceType()) ? fdroidLabel : manualLabel);
                Collections.sort(displayed, bySource.thenComparing(byName));
            } else {
                Collections.sort(displayed, (a, b) -> {
                    int version = Long.compare(b.getLatestVersionCode(), a.getLatestVersionCode());
                    return version != 0 ? version : byName.compare(a, b);
                });
            }
            int pendingUpdates = 0;
            for (MatchedApp app : displayed) if (hasPendingUpdate(app)) pendingUpdates++;
            if (prioritizeUpdates) {
                displayed.sort((a, b) -> Boolean.compare(hasPendingUpdate(b), hasPendingUpdate(a)));
            }
            if (pushNotInstalledDown) {
                displayed.sort((a, b) -> Boolean.compare(a.isNotInstalled(), b.isNotInstalled()));
            }
            List<MatchedApp> snapshot = new ArrayList<>(displayed.size());
            for (MatchedApp shown : displayed) snapshot.add(shown.copy());
            final int installedCount = libInstalled;
            final int updatesCount = libUpdates;
            final int availableCount = libAvailable;
            final int restorableCount = restorable;
            final int visibleUpdates = pendingUpdates;
            final int visibleCount = displayed.size();

            ui(() -> {
                if (generation != homePresentationGeneration || adapter == null) return;
                if (homeLoaded) adapter.submitList(snapshot);
                else adapter.submitList(snapshot, () -> hideHomeLoading());
                pendingRestoreCount = restorableCount;
                seenStoreRevision = MatchStore.revision;
                applyHeroState(installedCount, updatesCount, availableCount);
                if (empty instanceof TextView) {
                    ((TextView) empty).setText(query.isEmpty()
                            ? R.string.no_results : R.string.home_search_no_results);
                }
                empty.setVisibility(visibleCount == 0 ? View.VISIBLE : View.GONE);
                if (favoritesSection != null && !query.isEmpty()) favoritesSection.setVisibility(View.GONE);
                if (appCount != null) {
                    appCount.setText(visibleCount == 0 ? getString(R.string.no_apps_found)
                            : getString(R.string.apps_found, visibleCount));
                    appCount.setVisibility(View.GONE);
                }
                updatesFirstLabel = visibleUpdates > 0
                        ? getString(R.string.sort_updates_first_count, visibleUpdates)
                        : getString(R.string.sort_updates_first);
            });
        });
    }

    private void hideHomeLoading() {
        if (homeLoaded) return;
        homeLoaded = true;
        View root = getView();
        final RecyclerView list = root == null ? null : root.findViewById(R.id.recyclerView);
        if (list != null) list.post(() -> MotionHelper.setupListAnimator(list));
        final View loading = homeLoading;
        if (loading == null) return;

        loading.postOnAnimation(() -> loading.animate().alpha(0f).setDuration(160L)
                .withEndAction(() -> loading.setVisibility(View.GONE)).start());
    }

    private static boolean matchesHomeQuery(MatchedApp app, String query) {
        if (query == null || query.isEmpty()) return true;
        String label = app.getAppLabel() == null ? "" : app.getAppLabel().toLowerCase(java.util.Locale.ROOT);
        String pkg = app.getPackageName() == null ? "" : app.getPackageName().toLowerCase(java.util.Locale.ROOT);
        String desc = app.getDescription() == null ? "" : app.getDescription().toLowerCase(java.util.Locale.ROOT);
        return label.contains(query) || pkg.contains(query) || desc.contains(query);
    }

    private void refreshInstalledFields(Context context, MatchedApp match, com.opensourcefinder.app.install.InstallState.Info info) {
        com.opensourcefinder.app.install.InstallState.refresh(match, info);
        if (!match.isNotInstalled()) {
            boolean known;
            synchronized (iconCache) { known = iconCache.containsKey(match.getPackageName()); }
            if (!known) {
                try {

                    Drawable appIcon = context.getPackageManager().getApplicationIcon(match.getPackageName());
                    ShapedIconView.prewarm(appIcon);
                    synchronized (iconCache) { iconCache.put(match.getPackageName(), appIcon); }
                } catch (Exception ignored) {}
            }
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

    private String normalizeSource(String url) {
        if (url == null) return null;
        String u = url.trim().toLowerCase(java.util.Locale.ROOT);
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        int gh = u.indexOf("github.com/");
        if (gh >= 0) {
            String rest = u.substring(gh + "github.com/".length());
            String[] parts = rest.split("/");
            if (parts.length >= 2) return "github.com/" + parts[0] + "/" + parts[1];
        }
        int gl = u.indexOf("gitlab.com/");
        if (gl >= 0) {
            String rest = u.substring(gl + "gitlab.com/".length());
            while (rest.endsWith("/")) rest = rest.substring(0, rest.length() - 1);
            return "gitlab.com/" + rest.replaceAll("\\.git$", "");
        }
        int cb = u.indexOf("codeberg.org/");
        if (cb >= 0) {
            String rest = u.substring(cb + "codeberg.org/".length());
            String[] parts = rest.split("/");
            if (parts.length >= 2) return "codeberg.org/" + parts[0] + "/" + parts[1].replaceAll("\\.git$", "");
        }
        return u;
    }

    private void showAddRepositoryDialog() {
        com.google.android.material.textfield.TextInputLayout til = new com.google.android.material.textfield.TextInputLayout(requireContext(), null, com.google.android.material.R.attr.textInputOutlinedStyle);
        til.setHint(getString(R.string.repository_link));
        til.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * density);
        til.setPadding(pad, pad, pad, 0);
        com.google.android.material.textfield.TextInputEditText input = new com.google.android.material.textfield.TextInputEditText(til.getContext());
        input.setSingleLine(true);
        til.addView(input);
        til.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_CUSTOM);
        til.setEndIconDrawable(R.drawable.ic_clipboard);
        til.setEndIconContentDescription(getString(R.string.repos_paste));
        til.setEndIconOnClickListener(v -> {
            String link = RepositoryLinkExtractor.fromClipboard(requireContext());
            if (link == null) {
                Toast.makeText(requireContext(), R.string.repository_clipboard_no_link, Toast.LENGTH_SHORT).show();
                return;
            }
            til.setError(null);
            input.setText(link);
            input.setSelection(link.length());
        });
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(til);
        com.google.android.material.button.MaterialButton githubSearch =
                new com.google.android.material.button.MaterialButton(requireContext(), null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
        githubSearch.setText(R.string.github_explore_title);
        githubSearch.setAllCaps(false);
        githubSearch.setSingleLine(true);
        githubSearch.setEllipsize(android.text.TextUtils.TruncateAt.END);
        githubSearch.setIconResource(TablerIconRes.get(requireContext(), "ic_brand_github"));
        githubSearch.setIconGravity(com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gp.setMargins(pad, (int) (12 * density), pad, 0);
        githubSearch.setLayoutParams(gp);
        container.addView(githubSearch);
        com.google.android.material.loadingindicator.LoadingIndicator spinner = new com.google.android.material.loadingindicator.LoadingIndicator(requireContext());
        spinner.setIndicatorSize((int) (24 * density));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        sp.topMargin = (int) (16 * density);
        spinner.setLayoutParams(sp);
        spinner.setVisibility(View.GONE);
        container.addView(spinner);
        TextView status = new TextView(requireContext());
        status.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        status.setPadding(pad, (int)(8*density), pad, 0);
        status.setTextColor(requireContext().getColor(R.color.md_on_surface));
        status.setVisibility(View.GONE);
        container.addView(status);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.add_repository))
                .setMessage(getString(R.string.repository_add_message))
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(getString(R.string.check_and_add), null)
                .create();
        githubSearch.setOnClickListener(v -> {
            dialog.dismiss();
            openGitHubExplorer();
        });
        dialog.setOnShowListener(d -> {
            final View decor = dialog.getWindow() == null ? null : dialog.getWindow().getDecorView();
            if (decor != null) {
                final Runnable autoPaste = () -> {
                    if (!isAdded()) return;
                    if (input.getText() != null && input.getText().length() > 0) return;
                    String link = RepositoryLinkExtractor.fromClipboard(requireContext());
                    if (link == null) return;
                    input.setText(link);
                    input.setSelection(link.length());
                };
                if (decor.hasWindowFocus()) {
                    autoPaste.run();
                } else {
                    decor.getViewTreeObserver().addOnWindowFocusChangeListener(
                            new android.view.ViewTreeObserver.OnWindowFocusChangeListener() {
                        @Override public void onWindowFocusChanged(boolean hasFocus) {
                            if (!hasFocus) return;
                            android.view.ViewTreeObserver observer = decor.getViewTreeObserver();
                            if (observer.isAlive()) observer.removeOnWindowFocusChangeListener(this);
                            autoPaste.run();
                        }
                    });
                }
            }
            android.widget.Button pos = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            pos.setOnClickListener(v -> {
                String url = input.getText() == null ? "" : input.getText().toString().trim();
                if (url.isEmpty()) { til.setError(getString(R.string.enter_link)); return; }
                if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
                String[] githubParts = githubRepositoryParts(url);
                if (githubParts != null) url = githubParts[2];
                til.setError(null);
                input.setEnabled(false);
                pos.setEnabled(false);
                spinner.setVisibility(View.VISIBLE);
                status.setVisibility(View.VISIBLE);
                status.setText(getString(R.string.checking_repository));
                final String finalUrl = url;
                executor.execute(() -> {
                    String error = null;
                    try { error = validateRepository(finalUrl); }
                    catch (Exception e) { error = e.getMessage() == null ? getString(R.string.repository_check_error) : e.getMessage(); }
                    final String err = error;
                    ui(() -> {
                        spinner.setVisibility(View.GONE);
                        input.setEnabled(true);
                        pos.setEnabled(true);
                        if (err != null) {
                            status.setText(err);
                            til.setError(err);
                        } else {
                            SourceRepository src = new SourceRepository(finalUrl, finalUrl.replaceFirst("https?://", ""), "auto");
                            RepositoryManager.add(requireContext(), src);
                            dialog.dismiss();
                            Toast.makeText(requireContext(), getString(R.string.repository_valid_added), Toast.LENGTH_SHORT).show();
                            startScan();
                        }
                    });
                });
            });
        });
        dialog.show();
    }

    private String validateRepository(String url) {
        try {
            String[] githubParts = githubRepositoryParts(url);
            if (githubParts != null) {
                retrofit2.Response<com.opensourcefinder.app.model.GitHubRepo> resp =
                        RetrofitClient.getService().getRepo(githubParts[0], githubParts[1]).execute();
                if (resp.code() == 401 || resp.code() == 403) {
                    if (resp.code() == 401) {
                        Context currentContext = getContext();
                        if (currentContext != null) GitHubTokenProvider.clearToken(currentContext);
                    }
                    resp = RetrofitClient.getPublicService().getRepo(githubParts[0], githubParts[1]).execute();
                }
                if (resp.isSuccessful() && resp.body() != null) return null;
                if (resp.code() == 404) return getString(R.string.github_repo_not_found, resp.code());
                if (isReachable(githubParts[2])) return null;
                return getString(R.string.github_repo_not_found, resp.code());
            }
            if (url.toLowerCase(java.util.Locale.ROOT).contains("github.com")) {
                return getString(R.string.invalid_github_link);
            }
            if (!isHttpUrl(url)) return getString(R.string.repository_check_error);
            String indexUrl = url.endsWith("index-v1.json") ? url
                    : (url.endsWith("/") ? url + "index-v1.json" : url + "/index-v1.json");
            java.net.HttpURLConnection c = com.opensourcefinder.app.network.HttpClients.open(new java.net.URL(indexUrl));
            c.setConnectTimeout(12000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "OpenSourceFinder/1.0");
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            if (code >= 200 && code < 300) { c.disconnect(); return null; }
            c.disconnect();
            c = com.opensourcefinder.app.network.HttpClients.open(new java.net.URL(url));
            c.setConnectTimeout(12000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "OpenSourceFinder/1.0");
            code = c.getResponseCode();
            c.disconnect();
            if (code >= 200 && code < 400) return null;
            return getString(R.string.repository_unavailable, code);
        } catch (Exception e) {
            String[] githubParts = githubRepositoryParts(url);
            if (githubParts != null && isReachable(githubParts[2])) return null;
            return getString(R.string.error_prefix, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private String[] githubRepositoryParts(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return null;
        String value = rawUrl.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "https://" + value;
        try {
            Uri uri = Uri.parse(value);
            String host = uri.getHost();
            if (host == null || (!"github.com".equalsIgnoreCase(host) && !"www.github.com".equalsIgnoreCase(host))) return null;
            List<String> segments = uri.getPathSegments();
            if (segments.size() < 2) return null;
            String owner = segments.get(0).trim();
            String repo = segments.get(1).trim();
            if (repo.endsWith(".git")) repo = repo.substring(0, repo.length() - 4);
            if (owner.isEmpty() || repo.isEmpty()) return null;
            return new String[]{owner, repo, "https://github.com/" + owner + "/" + repo};
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isHttpUrl(String value) {
        try {
            Uri uri = Uri.parse(value);
            String scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isReachable(String value) {
        java.net.HttpURLConnection connection = null;
        try {
            connection = com.opensourcefinder.app.network.HttpClients.open(new java.net.URL(value));
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "OpenSourceFinder/1.0");
            int code = connection.getResponseCode();
            return code >= 200 && code < 400;
        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void applyLogoMode(boolean showAvatar) {
        if (homeHeader == null || avatar == null) return;
        View container = homeHeader.logo();
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round((showAvatar ? 40f : 22f) * density);
        android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) avatar.getLayoutParams();
        lp.width = size;
        lp.height = size;
        avatar.setLayoutParams(lp);
        if (showAvatar) {
            container.setBackground(null);
            androidx.core.widget.ImageViewCompat.setImageTintList(avatar, null);
        } else {
            container.setBackgroundResource(R.drawable.bg_explore_logo);
            androidx.core.widget.ImageViewCompat.setImageTintList(avatar,
                    android.content.res.ColorStateList.valueOf(com.google.android.material.color.MaterialColors.getColor(
                            avatar, com.google.android.material.R.attr.colorOnPrimaryContainer)));
            Glide.with(HomeFragment.this).clear(avatar);
            avatar.setImageResource(R.drawable.ic_home);
        }
    }

    private void loadProfileAvatar() {
        if (avatar == null || !isAdded()) return;
        if (!GitHubTokenProvider.hasToken()) {
            currentUser = null;
            applyLogoMode(false);
            return;
        }
        if (currentUser != null) {
            if (currentUser.getAvatarUrl() != null) {
                applyLogoMode(true);
                IconLoader.loadAvatar(avatar, currentUser.getAvatarUrl());
            }
            return;
        }
        RetrofitClient.getService().getAuthenticatedUser().enqueue(new Callback<GitHubUser>() {
            @Override public void onResponse(Call<GitHubUser> c, Response<GitHubUser> r) {
                if (!isAdded()) return;
                if (r.isSuccessful() && r.body() != null) {
                    currentUser = r.body();
                    if (currentUser.getAvatarUrl() != null) {
                        applyLogoMode(true);
                        IconLoader.loadAvatar(avatar, currentUser.getAvatarUrl());
                    }
                }
            }
            @Override public void onFailure(Call<GitHubUser> c, Throwable t) {}
        });
    }

    private void openProfile() {
        Intent i = new Intent(requireContext(), GitHubProfileActivity.class);
        if (currentUser != null) i.putExtra(GitHubProfileActivity.EXTRA_CACHED_USER, currentUser);
        startActivity(i);
    }

    private void openDetail(MatchedApp app) {
        Intent i = new Intent(requireContext(), AppDetailActivity.class);
        i.putExtra(AppDetailActivity.EXTRA_MATCHED_APP, app);
        startActivity(i);
    }

    private void setStageState(View row, StageState state) {
        ImageView icon = row.findViewById(R.id.stageStateIcon);
        LoadingIndicator spinner = row.findViewById(R.id.stageSpinner);
        switch (state) {
            case ACTIVE:
                icon.setVisibility(View.GONE);
                spinner.setVisibility(View.VISIBLE);
                break;
            case DONE:
                spinner.setVisibility(View.GONE);
                icon.setVisibility(View.VISIBLE);
                icon.setImageResource(com.opensourcefinder.app.ui.TablerIconRes.get(requireContext(), "ic_circle_check"));
                break;
            default:
                spinner.setVisibility(View.GONE);
                icon.setVisibility(View.VISIBLE);
                icon.setImageResource(com.opensourcefinder.app.ui.TablerIconRes.get(requireContext(), "ic_circle"));
                break;
        }
    }

    private void setStageCount(View row, String text) {
        ((TextView) row.findViewById(R.id.stageCount)).setText(text);
    }

    private void updateOverallProgress(int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        overallProgress.setProgressCompat(clamped, true);
        scanPercentText.setText(getString(R.string.scan_progress_percent, clamped));
    }

    private void setCurrentItem(String text) { scanCurrentItemText.setText(text); }

    private void resetScanCard() {
        setScanIconSpinning(true);
        setScanPanelBusy(true);
        updateOverallProgress(0);
        setStageState(stageFdroidRow, StageState.ACTIVE);
        setStageCount(stageFdroidRow, "");
        setStageState(stageManualRow, StageState.PENDING);
        setStageCount(stageManualRow, "");
        setCurrentItem(getString(R.string.scan_updating_fdroid_index));
    }

    public void startScan() {
        startScan(false);
    }

    private void startScan(boolean forceIndexRefresh) {
        startScan(forceIndexRefresh, forceIndexRefresh);
    }

    private void startScan(boolean forceIndexRefresh, boolean forceReleases) {
        if (!isAdded() || executor.isShutdown() || scanRunning) {
            if (swipe != null) swipe.setRefreshing(false);
            return;
        }
        scanRunning = true;
        resetScanCard();
        if (swipe != null) swipe.setRefreshing(true);
        final Context context = requireContext().getApplicationContext();
        executor.execute(() -> {
            try {
            List<InstalledAppInfo> installed = AppScanner.listUserInstalledApps(context);
            Map<String, InstalledAppInfo> byPkg = new HashMap<>();
            for (InstalledAppInfo app : installed) {
                byPkg.put(app.getPackageName(), app);
                synchronized (iconCache) {
                    iconCache.put(app.getPackageName(), app.getIcon());
                }
            }
            List<MatchedApp> saved = MatchStore.get(context);
            Map<String, MatchedApp> finalMap = new HashMap<>();
            try {
                Map<String, MatchedApp> fdroid = new FdroidIndexManager(context).setForceReleases(forceReleases).refreshAndMatchAllDefaults(installed, (percent, message) -> {
                    ui(() -> {
                        int mapped = (int) Math.round(STAGE1_END * (percent / 100.0));
                        updateOverallProgress(mapped);
                        setCurrentItem(message);
                        setStageCount(stageFdroidRow, getString(R.string.scan_progress_percent, percent));
                    });
                }, forceIndexRefresh);
                for (Map.Entry<String, MatchedApp> e : fdroid.entrySet()) {
                    finalMap.put(e.getKey(), e.getValue());
                }
                if (fdroid.isEmpty() && FdroidIndexManager.isEnabled(context)) {
                    for (MatchedApp old : saved) {
                        if (old != null && "fdroid".equalsIgnoreCase(old.getSourceType())
                                && old.getPackageName() != null) {
                            finalMap.put(old.getPackageName(), old);
                        }
                    }
                }
            } catch (Exception ignored) {}
            final int fdroidFound = finalMap.size();
            final boolean indexOn = FdroidIndexManager.isEnabled(context);
            lastScanIndexEnabled = indexOn;
            ui(() -> {
                setStageState(stageFdroidRow, StageState.DONE);
                setStageCount(stageFdroidRow, !indexOn ? getString(R.string.fdroid_index_disabled_short)
                        : fdroidFound == 0 ? getString(R.string.scan_stage_count_zero) : getString(R.string.apps_found_simple, fdroidFound));
                updateOverallProgress(STAGE1_END);
            });
            List<SourceRepository> sources = RepositoryManager.get(context);
            final int totalSources = sources.size();
            ui(() -> {
                if (totalSources == 0) {
                    setStageState(stageManualRow, StageState.DONE);
                    setStageCount(stageManualRow, getString(R.string.scan_stage_count_zero));
                    updateOverallProgress(STAGE2_END);
                } else {
                    setStageState(stageManualRow, StageState.ACTIVE);
                    setStageCount(stageManualRow, getString(R.string.scan_stage_count, 0, totalSources));
                }
            });
            int si = 0;
            for (SourceRepository source : sources) {
                si++;
                int current = si;
                final String srcName = source.getName() != null && !source.getName().isEmpty() ? source.getName() : source.getUrl();
                ui(() -> {
                    setStageCount(stageManualRow, getString(R.string.scan_stage_count, current, totalSources));
                    updateOverallProgress(STAGE1_END + (int) Math.round((STAGE2_END - STAGE1_END) * (current / (double) totalSources)));
                    setCurrentItem(getString(R.string.scan_checking_item, srcName));
                });
                try {
                    List<MatchedApp> found = new CustomRepositoryScanner(context).setForceFull(forceIndexRefresh).scan(source, installed);
                    for (MatchedApp m : found) {
                        MatchStore.putManual(finalMap, m);
                    }
                } catch (Exception ignored) {}
            }
            if (totalSources > 0) ui(() -> {
                setStageState(stageManualRow, StageState.DONE);
                updateOverallProgress(STAGE2_END);
            });
            MatchStore.restoreInstalledTags(context, finalMap.values());
            Set<String> installedPkgs = byPkg.keySet();
            List<MatchedApp> persisted = new ArrayList<>();
            for (MatchedApp m : finalMap.values()) {
                if (m == null || m.getPackageName() == null) continue;
                if (installedPkgs.contains(m.getPackageName()) || m.isNotInstalled() || "manual".equals(m.getOrigin())) {
                    if (installedPkgs.contains(m.getPackageName())) {
                        InstalledAppInfo info = byPkg.get(m.getPackageName());
                        if (info != null) {
                            com.opensourcefinder.app.install.InstallState.refresh(context, m);
                        }
                    }
                    persisted.add(m);
                }
            }
            for (MatchedApp oldm : saved) {
                if (oldm == null || oldm.getPackageName() == null) continue;
                if (finalMap.containsKey(oldm.getStoreKey()) || MatchStore.coveredBySameRepo(finalMap, oldm)) continue;
                if (!installedPkgs.contains(oldm.getPackageName())) {
                    com.opensourcefinder.app.install.InstallState.refresh(context, oldm);
                }
                if (!indexOn && "fdroid".equalsIgnoreCase(oldm.getSourceType()) && !"manual".equals(oldm.getOrigin())) continue;
                if ("manual".equals(oldm.getOrigin()) || oldm.isNotInstalled()) {
                    if (installedPkgs.contains(oldm.getPackageName())) {
                        InstalledAppInfo info = byPkg.get(oldm.getPackageName());
                        if (info != null) {
                            com.opensourcefinder.app.install.InstallState.refresh(context, oldm);
                        }
                    }
                    persisted.add(oldm);
                }
            }
            com.opensourcefinder.app.scanner.CloneUpdateChecker.refresh(context, persisted);
            java.util.Set<String> installedSourceUrls = new java.util.HashSet<>();
            java.util.Set<String> installedPkgs2 = new java.util.HashSet<>();
            for (MatchedApp m : persisted) {
                if (m != null && !m.isNotInstalled()) {
                    if (m.getSourceUrl() != null) installedSourceUrls.add(normalizeSource(m.getSourceUrl()));
                    if (m.getPackageName() != null) installedPkgs2.add(m.getPackageName());
                }
            }
            List<MatchedApp> cleaned = new ArrayList<>();
            for (MatchedApp m : persisted) {
                if (m == null) continue;
                if (m.isNotInstalled() && !m.isCloneVariant()) {
                    String src = normalizeSource(m.getSourceUrl());
                    if (src != null && installedSourceUrls.contains(src)) continue;
                    if (m.getPackageName() != null && installedPkgs2.contains(m.getPackageName())) continue;
                }
                cleaned.add(m);
            }
            final List<MatchedApp> finalCleaned = deduplicateManualSources(cleaned);
            MatchStore.replaceAll(context, finalCleaned);
            ui(() -> {
                try {
                    applyHomePresentation(finalCleaned);
                } finally {
                    if (swipe != null) swipe.setRefreshing(false);
                    finishScanPanel();
                }
            });
            } catch (Exception error) {
                ui(() -> {
                    if (swipe != null) swipe.setRefreshing(false);
                    finishScanPanel();
                    if (isAdded()) Toast.makeText(requireContext(), R.string.scan_failed, Toast.LENGTH_LONG).show();
                });
            } finally {
                scanRunning = false;
                mainHandler.post(this::stopScanUi);
            }
        });
    }

    private List<MatchedApp> deduplicateManualSources(List<MatchedApp> input) {
        if (input == null || input.isEmpty()) return new ArrayList<>();
        LinkedHashMap<String, MatchedApp> bySource = new LinkedHashMap<>();
        List<MatchedApp> output = new ArrayList<>();
        for (MatchedApp app : input) {
            if (app == null) continue;
            if (app.isCloneVariant() || !"manual".equalsIgnoreCase(app.getOrigin()) || app.getSourceUrl() == null) {
                output.add(app);
                continue;
            }
            String key = normalizeSource(app.getSourceUrl());
            if (key == null || key.isEmpty()) {
                output.add(app);
                continue;
            }
            MatchedApp existing = bySource.get(key);
            if (existing == null) {
                bySource.put(key, app);
                output.add(app);
            } else if (isBetterManualMatch(app, existing)) {
                int index = output.indexOf(existing);
                if (index >= 0) output.set(index, app);
                bySource.put(key, app);
            }
        }
        return output;
    }

    @Override public void onDestroyView() {
        LiveBackgroundView.setScrollPaused(false);
        homePresentationGeneration++;
        homeLoading = null;
        if (scanIconSpin != null) scanIconSpin.cancel();
        scanIconSpin = null;
        scanPanelOpen = false;
        homeMenuOpen = false;
        DownloadTracker.get().removeGlobalListener(homeDownloadListener);
        mainHandler.removeCallbacksAndMessages(null);
        refreshQueued = false;
        reloadQueued = false;
        lastFavoritesKey = null;
        if (homeHeader != null) homeHeader.destroy();
        homeHeader = null;
        fabMenuOpen = false;
        if (fabIconAnimator != null) { fabIconAnimator.cancel(); fabIconAnimator = null; }
        fabScrimView = null;
        fabMenuView = null;
        fabMainView = null;
        fabContainerView = null;
        if (heroShape != null) heroShape.setIdleSpin(false);
        if (heroShapeSmall != null) heroShapeSmall.setIdleSpin(false);
        heroShape = null;
        heroShapeSmall = null;
        homeAppBar = null;
        heroCard = null;
        filterRow = null;
        heroTitle = null;
        heroSubtitle = null;
        updateAllButtonView = null;
        super.onDestroyView();
    }

    private void openGitHubExplorer() {
        if (!isAdded()) return;
        AppPreferences.performHaptic(getView());
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showTab(MainActivity.TAG_GITHUB);
            return;
        }
        Intent intent = new Intent(requireContext(), GitHubSearchActivity.class);
        intent.putExtra("open_github_search", true);
        startActivity(intent);
    }

    private void ui(Runnable r) {
        android.app.Activity activity = getActivity();
        if (!isAdded() || activity == null || getView() == null) return;
        activity.runOnUiThread(() -> {
            if (isAdded() && getActivity() != null && getView() != null) r.run();
        });
    }

    private class FavoritesAdapter extends RecyclerView.Adapter<FavoritesAdapter.VH> {
        private final List<ExploreApp> items = new ArrayList<>();
        void submit(List<ExploreApp> next) { items.clear(); if (next != null) items.addAll(next); notifyDataSetChanged(); }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            Context ctx = parent.getContext();
            float d = parent.getResources().getDisplayMetrics().density;
            LinearLayout box = new LinearLayout(ctx);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            box.setPadding((int) (6 * d), (int) (4 * d), (int) (6 * d), (int) (4 * d));
            box.setLayoutParams(new RecyclerView.LayoutParams((int) (84 * d), ViewGroup.LayoutParams.WRAP_CONTENT));
            android.util.TypedValue ripple = new android.util.TypedValue();
            if (ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
                box.setBackgroundResource(ripple.resourceId);
            }
            box.setClickable(true);
            box.setFocusable(true);
            ImageView icon = new ShapedIconView(ctx);
            icon.setId(7001);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int iconPx = (int) (58 * d);
            box.addView(icon, new LinearLayout.LayoutParams(iconPx, iconPx));
            TextView name = new TextView(ctx);
            name.setId(7002);
            name.setGravity(android.view.Gravity.CENTER);
            name.setTextAlignment(android.view.View.TEXT_ALIGNMENT_CENTER);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setTextSize(12f);
            name.setTextColor(com.google.android.material.color.MaterialColors.getColor(parent,
                    com.google.android.material.R.attr.colorOnSurface, 0xFF000000));
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            np.topMargin = (int) (6 * d);
            box.addView(name, np);
            MotionHelper.attachPressScale(box);
            return new VH(box);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int p) {
            ExploreApp a = items.get(p);
            h.name.setText(a.getName());
            if (a.getIconUrl() != null) IconLoader.load(h.icon, a.getIconUrl());
            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(requireContext(), ExploreDetailActivity.class);
                i.putExtra(ExploreDetailActivity.EXTRA_EXPLORE_APP, a);
                startActivity(i);
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name;
            VH(View v) {
                super(v);
                icon = v.findViewById(7001);
                name = v.findViewById(7002);
                IconShapeHelper.apply(icon);
            }
        }
    }

    @Override public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        presentationExecutor.shutdownNow();
    }

    private void keepFabAboveNav(@NonNull View fab) {
        final View nav = requireActivity().findViewById(R.id.bottomNav);
        if (nav == null || !(nav.getParent() instanceof View)) return;
        final View navParent = (View) nav.getParent();
        final Runnable update = () -> {
            if (nav.getHeight() == 0) return;
            int target = navParent.getHeight() - nav.getTop()
                    + Math.round(14f * getResources().getDisplayMetrics().density);
            android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) fab.getLayoutParams();
            if (lp.bottomMargin != target) {
                lp.bottomMargin = target;
                fab.setLayoutParams(lp);
            }
        };
        nav.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> update.run());
        navParent.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> update.run());
        nav.post(update);
    }
}
