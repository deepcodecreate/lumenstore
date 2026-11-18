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
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.loadingindicator.LoadingIndicator;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.model.GitHubRepo;
import com.opensourcefinder.app.model.GitHubSearchResponse;
import com.opensourcefinder.app.model.GitHubTree;
import com.opensourcefinder.app.model.SourceRepository;
import com.opensourcefinder.app.network.GitHubTokenProvider;
import com.opensourcefinder.app.network.RetrofitClient;
import com.opensourcefinder.app.scanner.RepositoryManager;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GitHubExploreFragment extends Fragment {

    private static final int PAGE_SIZE = 30;
    private static final int MAX_RESULTS = 1000;
    private static final int PREFETCH_DISTANCE = 6;
    private static final long SEARCH_DEBOUNCE_MS = 500L;

    private static final int MODE_TRENDING = 0;
    private static final int MODE_NEW = 1;
    private static final int MODE_STARS = 2;
    private static final int MODE_UPDATED = 3;

    private static final int TYPE_REPO = 0;
    private static final int TYPE_FOOTER = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Call<?>> activeCalls = new ArrayList<>();
    private final List<GitHubRepo> repositories = new ArrayList<>();
    private final Set<String> addedUrls = new HashSet<>();

    private MaterialToolbar toolbar;
    private HeaderSearch header;
    private ChipGroup modeChips;
    private ChipGroup langChips;
    private Chip modeTrendingChip;
    private LoadingIndicator searchProgress;
    private TextView resultSummary;
    private View emptyBlock;
    private TextView emptyState;
    private MaterialButton retryButton;
    private RecyclerView resultsList;
    private MaterialRefreshLayout resultsRefresh;
    private FloatingActionButton scrollTopFab;
    private RepositoryAdapter adapter;
    private View searchContent;
    private View detailContent;
    private TextView detailFullName;
    private TextView detailOwner;
    private TextView detailDescription;
    private ChipGroup detailTopics;
    private TextView detailStats;
    private TextView detailLanguage;
    private TextView detailPackage;
    private TextView detailLicense;
    private MaterialCardView readmeCard;
    private LoadingIndicator readmeProgress;
    private RecyclerView readmeContent;
    private MaterialButton addToListButton;
    private MaterialButton openInBrowserButton;
    private GitHubRepo selectedRepository;
    private Call<GitHubSearchResponse> searchCall;
    private long searchGeneration;
    private boolean redirectingToLogin;

    private int mode = MODE_TRENDING;
    private String language = null;
    private String query = "";

    private String loadedQuery = "";
    private int nextPage = 1;
    private int totalCount = 0;
    private boolean loadingPage;
    private boolean endReached;
    private boolean footerVisible;
    private boolean fabShown;

    private View root;
    private View loginPrompt;
    private OnBackPressedCallback detailBackCallback;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_github_explore, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        root = view;
        bindViews();
        setupHeader();
        setupFilters();
        setupList();
        setupDetailButtons();
        detailBackCallback = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() {
                if (inDetail()) showSearch();
                else if (header != null && header.hasFocusOrText()) header.clearAndNotify();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), detailBackCallback);
        refreshAdded();
        showSearch();
        updateLoginState();
    }

    @Override
    public void onResume() {
        super.onResume();
        consumeRepositoryIntent();
        redirectingToLogin = false;
        refreshAdded();
        notifyCardsChanged();
        updateLoginState();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        syncBackCallback();
        if (!hidden) {
            refreshAdded();
            notifyCardsChanged();
            updateLoginState();
        }
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        if (header != null) header.destroy();
        header = null;
        clearActiveCalls();
        root = null;
        super.onDestroyView();
    }

    public void resetToInitialState() {
        if (!isAdded() || root == null) return;
        if (inDetail()) showSearch();

        if (header != null && header.hasFocusOrText()) header.clearAndNotify();

        if (modeChips != null && mode != MODE_TRENDING && modeTrendingChip != null) modeTrendingChip.setChecked(true);
        if (langChips != null && language != null) {
            View all = langChips.getChildCount() > 0 ? langChips.getChildAt(0) : null;
            if (all instanceof Chip) ((Chip) all).setChecked(true);
        }
        scrollToTop();
    }

    private boolean inDetail() {
        return PanelTransition.isShown(detailContent);
    }

    private void syncBackCallback() {
        if (detailBackCallback == null) return;
        boolean searching = header != null && header.hasFocusOrText();
        detailBackCallback.setEnabled(isAdded() && !isHidden() && (inDetail() || searching));
    }

    private void updateLoginState() {
        if (root == null) return;
        boolean signedIn = GitHubTokenProvider.hasToken();
        loginPrompt.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        if (header != null) header.setSearchVisible(signedIn && !inDetail());
        if (!signedIn) {
            searchContent.setVisibility(View.GONE);
            detailContent.setVisibility(View.GONE);
            if (header != null) header.setRowVisible(true);
            toolbar.setNavigationIcon(null);
            syncBackCallback();
        } else if (!inDetail() && searchContent.getVisibility() != View.VISIBLE) {
            showSearch();
        }
    }

    private void bindViews() {
        toolbar = root.findViewById(R.id.toolbar);
        modeChips = root.findViewById(R.id.modeChips);
        langChips = root.findViewById(R.id.langChips);
        modeTrendingChip = root.findViewById(R.id.modeTrending);
        searchProgress = root.findViewById(R.id.searchProgress);
        resultSummary = root.findViewById(R.id.resultSummary);
        emptyBlock = root.findViewById(R.id.emptyBlock);
        emptyState = root.findViewById(R.id.emptyState);
        retryButton = root.findViewById(R.id.retryButton);
        resultsList = root.findViewById(R.id.resultsList);
        resultsRefresh = root.findViewById(R.id.resultsRefresh);
        scrollTopFab = root.findViewById(R.id.scrollTopFab);
        searchContent = root.findViewById(R.id.searchContent);
        detailContent = root.findViewById(R.id.detailContent);
        detailFullName = root.findViewById(R.id.detailFullName);
        detailOwner = root.findViewById(R.id.detailOwner);
        detailDescription = root.findViewById(R.id.detailDescription);
        detailTopics = root.findViewById(R.id.detailTopics);
        detailStats = root.findViewById(R.id.detailStats);
        detailLanguage = root.findViewById(R.id.detailLanguage);
        detailPackage = root.findViewById(R.id.detailPackage);
        detailLicense = root.findViewById(R.id.detailLicense);
        readmeCard = root.findViewById(R.id.readmeCard);
        readmeProgress = root.findViewById(R.id.readmeProgress);
        readmeContent = root.findViewById(R.id.readmeContent);
        addToListButton = root.findViewById(R.id.addToListButton);
        openInBrowserButton = root.findViewById(R.id.openInBrowserButton);
        loginPrompt = root.findViewById(R.id.loginPrompt);
        root.findViewById(R.id.loginButton).setOnClickListener(v -> openLogin());
    }

    private void setupHeader() {
        toolbar.setTitle("");
        toolbar.setNavigationIcon(null);
        toolbar.setNavigationOnClickListener(v -> {
            if (inDetail()) showSearch();
        });
        header = HeaderSearch.bind(root, R.drawable.ic_nav_github, getString(R.string.github_search_title),
                R.string.github_header_hint, SEARCH_DEBOUNCE_MS, (text, immediate) -> {
                    syncBackCallback();
                    onQueryChanged(text);
                });
    }

    private void onQueryChanged(String text) {
        String next = text == null ? "" : text.trim();

        if (next.length() == 1) return;
        if (next.equals(query)) return;
        query = next;
        updateTrendingLabel();
        if (!GitHubTokenProvider.hasToken()) return;
        loadPage(true);
    }

    private void updateTrendingLabel() {
        if (modeTrendingChip == null) return;
        modeTrendingChip.setText(query.isEmpty() ? R.string.github_mode_trending : R.string.github_sort_best);
    }

    private void setupFilters() {
        modeChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.modeNew) mode = MODE_NEW;
            else if (id == R.id.modeStars) mode = MODE_STARS;
            else if (id == R.id.modeUpdated) mode = MODE_UPDATED;
            else mode = MODE_TRENDING;
            applyChipColors(group);
            loadPage(true);
        });
        langChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.langKotlin) language = "Kotlin";
            else if (id == R.id.langJava) language = "Java";
            else if (id == R.id.langDart) language = "Dart";
            else if (id == R.id.langCpp) language = "C++";
            else language = null;
            applyChipColors(group);
            loadPage(true);
        });
        applyChipColors(modeChips);
        applyChipColors(langChips);
        updateTrendingLabel();
    }

    private void applyChipColors(ChipGroup group) {
        if (group == null) return;
        int primaryContainer = MaterialColors.getColor(group, com.google.android.material.R.attr.colorPrimaryContainer);
        int onPrimaryContainer = MaterialColors.getColor(group, com.google.android.material.R.attr.colorOnPrimaryContainer);
        int surfaceContainerHigh = MaterialColors.getColor(group, com.google.android.material.R.attr.colorSurfaceContainerHigh);
        int onSurface = MaterialColors.getColor(group, com.google.android.material.R.attr.colorOnSurface);
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                boolean checked = chip.isChecked();
                chip.setChipBackgroundColor(ColorStateList.valueOf(checked ? primaryContainer : surfaceContainerHigh));
                chip.setTextColor(checked ? onPrimaryContainer : onSurface);
                chip.setCheckedIconTint(ColorStateList.valueOf(checked ? onPrimaryContainer : onSurface));
                chip.setChipStrokeWidth(0f);
            }
        }
    }

    private void setupList() {
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        lm.setInitialPrefetchItemCount(6);
        resultsList.setLayoutManager(lm);
        resultsList.setItemViewCacheSize(10);
        adapter = new RepositoryAdapter();
        resultsList.setAdapter(adapter);
        MotionHelper.setupListAnimator(resultsList);
        resultsList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                int total = lm.getItemCount();
                int last = lm.findLastVisibleItemPosition();
                if (dy > 0 && total > 0 && last >= total - PREFETCH_DISTANCE) resultsList.post(() -> loadPage(false));
                boolean show = lm.findFirstVisibleItemPosition() > 6;
                if (show != fabShown) {
                    fabShown = show;
                    if (show) MotionHelper.showFab(scrollTopFab);
                    else MotionHelper.hideFab(scrollTopFab);
                }
            }
        });
        scrollTopFab.setImageResource(TablerIconRes.get(requireContext(), "ic_chevron_up"));
        scrollTopFab.setOnClickListener(v -> {
            AppPreferences.performHaptic(v);
            resultsList.smoothScrollToPosition(0);
        });
        resultsRefresh.setColorSchemeColors(MaterialColors.getColor(resultsRefresh, android.R.attr.colorAccent));
        resultsRefresh.setProgressBackgroundColorSchemeColor(MaterialColors.getColor(
                resultsRefresh, com.google.android.material.R.attr.colorSurfaceContainerHigh));
        resultsRefresh.setOnChildScrollUpCallback((parent, child) -> resultsList.canScrollVertically(-1));
        resultsRefresh.setOnRefreshListener(() -> loadPage(true));
        retryButton.setOnClickListener(v -> loadPage(true));
    }

    private void setupDetailButtons() {
        addToListButton.setOnClickListener(v -> addSelectedRepository());
        openInBrowserButton.setOnClickListener(v -> {
            if (selectedRepository != null && selectedRepository.getHtmlUrl() != null) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(selectedRepository.getHtmlUrl())));
            }
        });
    }

    private boolean listDimmed;

    private void dimList() {
        if (resultsList == null) return;
        listDimmed = true;
        resultsList.animate().cancel();
        resultsList.animate().alpha(0f).setDuration(140L)
                .setInterpolator(new AccelerateInterpolator()).start();
    }

    private void revealList(boolean slide) {
        if (resultsList == null) return;
        if (!listDimmed && resultsList.getAlpha() >= 1f) return;
        listDimmed = false;
        resultsList.animate().cancel();
        if (slide) resultsList.setTranslationY(resultsList.getResources().getDisplayMetrics().density * 14f);
        resultsList.animate().alpha(1f).translationY(0f).setDuration(260L)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    private void setProgressVisible(boolean visible) {
        if (searchProgress == null) return;
        searchProgress.animate().cancel();
        if (visible) {
            if (searchProgress.getVisibility() != View.VISIBLE) {
                searchProgress.setAlpha(0f);
                searchProgress.setVisibility(View.VISIBLE);
            }
            searchProgress.animate().alpha(1f).setDuration(180L).start();
        } else if (searchProgress.getVisibility() == View.VISIBLE) {
            searchProgress.animate().alpha(0f).setDuration(140L)
                    .withEndAction(() -> { if (searchProgress != null) searchProgress.setVisibility(View.GONE); })
                    .start();
        }
    }

    private void scrollToTop() {
        if (resultsList != null) resultsList.scrollToPosition(0);
    }

    private static String daysAgo(int days) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date(System.currentTimeMillis() - days * 86_400_000L));
    }

    private String buildQuery() {
        StringBuilder q = new StringBuilder();
        if (!query.isEmpty()) {
            q.append(query);
            if (language != null) q.append(" language:").append(language);
            return q.toString();
        }
        q.append("topic:android archived:false");
        if (language != null) q.append(" language:").append(language);
        switch (mode) {
            case MODE_NEW:
                q.append(" created:>").append(daysAgo(7));
                break;
            case MODE_STARS:
                if (query.isEmpty()) q.append(" stars:>100");
                break;
            case MODE_UPDATED:
                q.append(" stars:>20 pushed:>").append(daysAgo(14));
                break;
            default:
                if (query.isEmpty()) q.append(" stars:>5 created:>").append(daysAgo(30));
                break;
        }
        return q.toString();
    }

    @Nullable
    private String sortParam() {
        switch (mode) {
            case MODE_STARS:
            case MODE_NEW:
                return "stars";
            case MODE_UPDATED:
                return "updated";
            default:
                return query.isEmpty() ? "stars" : null;
        }
    }

    private void loadPage(boolean reset) {
        if (root == null || !GitHubTokenProvider.hasToken()) {
            if (resultsRefresh != null) resultsRefresh.setRefreshing(false);
            return;
        }
        if (reset) {
            boolean pulling = resultsRefresh.isRefreshing();
            if (searchCall != null) searchCall.cancel();
            clearActiveCalls();
            if (pulling) resultsRefresh.setRefreshing(true);
            searchGeneration++;
            loadedQuery = query;
            nextPage = 1;
            endReached = false;
            emptyBlock.setVisibility(View.GONE);
            if (!pulling) {

                if (!repositories.isEmpty()) dimList();
                setProgressVisible(true);
            }
        } else if (loadingPage || endReached || repositories.isEmpty()) {
            return;
        }
        loadingPage = true;
        final long generation = searchGeneration;
        final int page = nextPage;
        if (page > 1) setFooter(true);
        searchCall = RetrofitClient.getService().searchRepositoriesPage(buildQuery(), PAGE_SIZE, page, sortParam(), "desc");
        activeCalls.add(searchCall);
        searchCall.enqueue(new Callback<GitHubSearchResponse>() {
            @Override public void onResponse(@NonNull Call<GitHubSearchResponse> call, @NonNull Response<GitHubSearchResponse> response) {
                if (generation != searchGeneration || root == null) return;
                finishLoading();
                if (response.code() == 401) {
                    revealList(false);
                    openLogin();
                    return;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    onLoadFailed(page);
                    return;
                }
                List<GitHubRepo> items = response.body().getItems() == null
                        ? new ArrayList<>() : response.body().getItems();
                totalCount = response.body().getTotalCount();
                if (page == 1) {
                    repositories.clear();
                    repositories.addAll(items);
                    adapter.notifyDataSetChanged();
                    scrollToTop();
                    revealList(true);
                } else {
                    int start = repositories.size();
                    repositories.addAll(items);
                    adapter.notifyItemRangeInserted(start, items.size());
                }
                nextPage = page + 1;
                endReached = items.size() < PAGE_SIZE || repositories.size() >= Math.min(totalCount, MAX_RESULTS);
                renderSummary();
            }

            @Override public void onFailure(@NonNull Call<GitHubSearchResponse> call, @NonNull Throwable throwable) {
                if (generation != searchGeneration || call.isCanceled() || root == null) return;
                finishLoading();
                onLoadFailed(page);
            }
        });
    }

    private void finishLoading() {
        loadingPage = false;
        setProgressVisible(false);
        resultsRefresh.setRefreshing(false);
        setFooter(false);
    }

    private void onLoadFailed(int page) {
        revealList(false);
        if (page > 1 || !repositories.isEmpty()) {
            Toast.makeText(requireContext(), R.string.github_search_error, Toast.LENGTH_SHORT).show();
            return;
        }
        showEmpty(getString(R.string.github_search_error), true);
    }

    private void renderSummary() {
        if (repositories.isEmpty()) {
            resultSummary.setVisibility(View.GONE);
            showEmpty(getString(query.isEmpty() ? R.string.github_search_empty : R.string.github_search_no_results), false);
            return;
        }
        emptyBlock.setVisibility(View.GONE);
        String count = NumberFormat.getIntegerInstance().format(totalCount);
        resultSummary.setText(query.isEmpty() && mode == MODE_TRENDING
                ? getString(R.string.github_trending_summary)
                : getString(R.string.github_result_count_android, count));
        resultSummary.setVisibility(View.VISIBLE);
    }

    private void showEmpty(String message, boolean canRetry) {
        emptyState.setText(message);
        retryButton.setVisibility(canRetry ? View.VISIBLE : View.GONE);
        emptyBlock.setVisibility(View.VISIBLE);
        resultSummary.setVisibility(View.GONE);
    }

    private void setFooter(boolean visible) {
        if (adapter == null || footerVisible == visible) return;
        footerVisible = visible;
        if (visible) adapter.notifyItemInserted(repositories.size());
        else adapter.notifyItemRemoved(repositories.size());
    }

    public void consumeRepositoryIntent() {
        android.app.Activity activity = getActivity();
        if (activity == null || getView() == null) return;
        String slug = activity.getIntent().getStringExtra(LinkHandlerActivity.EXTRA_OPEN_REPO);
        if (slug == null) return;
        activity.getIntent().removeExtra(LinkHandlerActivity.EXTRA_OPEN_REPO);
        int slash = slug.indexOf('/');
        if (slash <= 0 || slash == slug.length() - 1) return;
        final String owner = slug.substring(0, slash);
        final String name = slug.substring(slash + 1);
        Call<GitHubRepo> call = RetrofitClient.getPublicService().getRepo(owner, name);
        activeCalls.add(call);
        call.enqueue(new Callback<GitHubRepo>() {
            @Override public void onResponse(@NonNull Call<GitHubRepo> call, @NonNull Response<GitHubRepo> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    showDetail(response.body());
                } else {
                    openRepositoryInBrowser(owner, name);
                }
            }

            @Override public void onFailure(@NonNull Call<GitHubRepo> call, @NonNull Throwable throwable) {
                if (!isAdded() || call.isCanceled()) return;
                openRepositoryInBrowser(owner, name);
            }
        });
    }

    private void openRepositoryInBrowser(String owner, String name) {
        LinkHandlerActivity.openInBrowser(requireContext(), Uri.parse("https://github.com/" + owner + "/" + name));
    }

    private void showSearch() {
        clearActiveCalls();
        boolean signedIn = GitHubTokenProvider.hasToken();
        if (signedIn) PanelTransition.swap(detailContent, searchContent, false);
        else detailContent.setVisibility(View.GONE);
        toolbar.setTitle("");
        toolbar.setNavigationIcon(null);
        if (header != null) {
            header.setRowVisible(true);
            header.setSearchVisible(signedIn);
        }
        syncBackCallback();

        if (signedIn && (repositories.isEmpty() || !loadedQuery.equals(query))) loadPage(true);
    }

    private void showDetail(GitHubRepo repository) {
        selectedRepository = repository;
        PanelTransition.swap(searchContent, detailContent, true);
        if (header != null) {
            header.reset();
            header.setRowVisible(false);
        }
        query = "";
        updateTrendingLabel();
        toolbar.setTitle(R.string.github_repository);
        toolbar.setNavigationIcon(TablerIconRes.get(requireContext(), "ic_arrow_back"));
        toolbar.setNavigationIconTint(MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface));
        syncBackCallback();
        bindRepositoryDetail(repository);
        loadRepositoryDetail(repository);
    }

    private void refreshAdded() {
        addedUrls.clear();
        if (!isAdded()) return;
        for (SourceRepository source : RepositoryManager.get(requireContext())) {
            if (source.getUrl() != null) addedUrls.add(source.getUrl().toLowerCase(Locale.ROOT));
        }
    }

    private boolean isRepositoryAdded(GitHubRepo repository) {
        return repository != null && repository.getHtmlUrl() != null
                && addedUrls.contains(repository.getHtmlUrl().toLowerCase(Locale.ROOT));
    }

    private void notifyCardsChanged() {
        if (adapter != null && !repositories.isEmpty()) adapter.notifyItemRangeChanged(0, repositories.size());
    }

    private void quickAdd(GitHubRepo repository) {
        if (repository == null || repository.getHtmlUrl() == null || isRepositoryAdded(repository)) return;
        RepositoryManager.add(requireContext(), new SourceRepository(
                repository.getHtmlUrl(),
                valueOrFallback(repository.getFullName(), repository.getName()),
                "github"));
        refreshAdded();
        notifyCardsChanged();
        Toast.makeText(requireContext(), R.string.github_repository_added, Toast.LENGTH_SHORT).show();
    }

    private static String formatCount(int value) {
        if (value < 1000) return String.valueOf(value);
        if (value < 10_000) return String.format(Locale.getDefault(), "%.1fk", value / 1000f);
        if (value < 1_000_000) return (value / 1000) + "k";
        return String.format(Locale.getDefault(), "%.1fM", value / 1_000_000f);
    }

    @Nullable
    private static CharSequence relativeTime(@Nullable String iso) {
        if (iso == null || iso.length() < 19) return null;
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = fmt.parse(iso.substring(0, 19));
            if (date == null) return null;
            return DateUtils.getRelativeTimeSpanString(date.getTime(), System.currentTimeMillis(),
                    DateUtils.DAY_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
        } catch (Exception e) {
            return null;
        }
    }

    private static int languageColor(@Nullable String language) {
        if (language == null) return 0xFF9E9E9E;
        switch (language) {
            case "Kotlin": return 0xFFA97BFF;
            case "Java": return 0xFFB07219;
            case "Dart": return 0xFF00B4AB;
            case "C++": return 0xFFF34B7D;
            case "C": return 0xFF555555;
            case "Python": return 0xFF3572A5;
            case "JavaScript": return 0xFFF1E05A;
            case "TypeScript": return 0xFF3178C6;
            case "Rust": return 0xFFDEA584;
            case "Go": return 0xFF00ADD8;
            case "Shell": return 0xFF89E051;
            case "Smali": return 0xFF757575;
            default: return 0xFF7E57C2;
        }
    }

    private void bindRepositoryDetail(GitHubRepo repository) {
        bindRepositoryHeader(repository);
        detailPackage.setVisibility(View.GONE);
        detailLicense.setVisibility(View.GONE);
        boolean alreadyAdded = isRepositoryAdded(repository);
        addToListButton.setText(alreadyAdded ? R.string.github_added_to_list : R.string.github_add_to_list);
        addToListButton.setEnabled(!alreadyAdded);
        readmeProgress.setVisibility(View.VISIBLE);
        readmeContent.setAdapter(null);
    }

    private void bindRepositoryHeader(GitHubRepo repository) {
        detailFullName.setText(valueOrFallback(repository.getFullName(), repository.getName()));
        detailOwner.setText(getString(R.string.github_repository_owner, valueOrFallback(repository.getOwnerLogin(), getString(R.string.unknown_value))));
        detailDescription.setText(valueOrFallback(repository.getDescription(), getString(R.string.github_no_description)));
        detailStats.setText(getString(R.string.github_stats,
                repository.getStargazersCount(), repository.getForksCount(), repository.getOpenIssuesCount()));
        detailLanguage.setText(getString(R.string.github_language,
                valueOrFallback(repository.getLanguage(), getString(R.string.unknown_value))));
        bindTopics(detailTopics, repository.getTopics());
        bindOptionalText(detailLicense, repository.getLicenseName(), R.string.github_license);
    }

    private void loadRepositoryDetail(GitHubRepo repository) {
        String owner = repository.getOwnerLogin();
        String name = repository.getName();
        if (owner == null || name == null) return;
        clearActiveCalls();
        Call<GitHubRepo> repoCall = RetrofitClient.getService().getRepo(owner, name);
        activeCalls.add(repoCall);
        repoCall.enqueue(new Callback<GitHubRepo>() {
            @Override public void onResponse(@NonNull Call<GitHubRepo> call, @NonNull Response<GitHubRepo> response) {
                if (response.code() == 401) {
                    openLogin();
                    return;
                }
                if (response.isSuccessful() && response.body() != null && selectedRepository == repository) {
                    selectedRepository = response.body();
                    bindRepositoryHeader(selectedRepository);
                }
            }

            @Override public void onFailure(@NonNull Call<GitHubRepo> call, @NonNull Throwable throwable) {}
        });
        loadReadme(owner, name);
        loadPackageName(owner, name, repository.getDefaultBranch());
    }

    private void loadReadme(String owner, String name) {
        String expectedRepository = owner + "/" + name;
        Call<String> readmeCall = RetrofitClient.getService().getReadmeRaw(owner, name, "application/vnd.github.raw+json");
        activeCalls.add(readmeCall);
        readmeCall.enqueue(new Callback<String>() {
            @Override public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (!isSelectedRepository(expectedRepository)) return;
                if (response.code() == 401) {
                    openLogin();
                    return;
                }
                readmeProgress.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && !response.body().trim().isEmpty()) {
                    MarkdownRenderer.renderSegmented(readmeContent, response.body(), owner, name);
                } else {
                    MarkdownRenderer.renderSegmented(readmeContent, getString(R.string.readme_unavailable));
                }
            }

            @Override public void onFailure(@NonNull Call<String> call, @NonNull Throwable throwable) {
                if (!isSelectedRepository(expectedRepository)) return;
                readmeProgress.setVisibility(View.GONE);
                MarkdownRenderer.renderSegmented(readmeContent, getString(R.string.readme_unavailable));
            }
        });
    }

    private void loadPackageName(String owner, String name, String branch) {
        String safeBranch = branch == null || branch.trim().isEmpty() ? "HEAD" : branch;
        Call<GitHubTree> treeCall = RetrofitClient.getService().getTree(owner, name, safeBranch, 1);
        activeCalls.add(treeCall);
        treeCall.enqueue(new Callback<GitHubTree>() {
            @Override public void onResponse(@NonNull Call<GitHubTree> call, @NonNull Response<GitHubTree> response) {
                if (response.code() == 401) {
                    openLogin();
                    return;
                }
                if (!response.isSuccessful() || response.body() == null || response.body().getTree() == null) return;
                String path = packagePath(response.body().getTree());
                if (path != null) fetchPackageFile(owner, name, path);
            }

            @Override public void onFailure(@NonNull Call<GitHubTree> call, @NonNull Throwable throwable) {}
        });
    }

    private String packagePath(List<GitHubTree.Entry> entries) {
        List<String> preferred = new ArrayList<>();
        for (GitHubTree.Entry entry : entries) {
            if (entry == null || !"blob".equals(entry.getType()) || entry.getPath() == null) continue;
            String path = entry.getPath();
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.endsWith("/package.json") || lower.equals("package.json")) preferred.add(path);
            else if (lower.endsWith("/pyproject.toml") || lower.equals("pyproject.toml")) preferred.add(path);
            else if (lower.endsWith("/cargo.toml") || lower.equals("cargo.toml")) preferred.add(path);
            else if (lower.endsWith("/pubspec.yaml") || lower.equals("pubspec.yaml")) preferred.add(path);
            else if (lower.endsWith("/composer.json") || lower.equals("composer.json")) preferred.add(path);
            else if (lower.endsWith("/pom.xml") || lower.equals("pom.xml")) preferred.add(path);
            else if (lower.endsWith("/androidmanifest.xml") || lower.equals("androidmanifest.xml")) preferred.add(path);
            else if (lower.endsWith("/build.gradle") || lower.endsWith("/build.gradle.kts") || lower.equals("build.gradle") || lower.equals("build.gradle.kts")) preferred.add(path);
        }
        if (preferred.isEmpty()) return null;
        Collections.sort(preferred, (left, right) -> Integer.compare(packagePriority(left), packagePriority(right)));
        return preferred.get(0);
    }

    private int packagePriority(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith("package.json")) return 0;
        if (lower.endsWith("pyproject.toml")) return 1;
        if (lower.endsWith("cargo.toml")) return 2;
        if (lower.endsWith("pubspec.yaml")) return 3;
        if (lower.endsWith("composer.json")) return 4;
        if (lower.endsWith("pom.xml")) return 5;
        if (lower.endsWith("androidmanifest.xml")) return 6;
        return 7;
    }

    private void fetchPackageFile(String owner, String name, String path) {
        String expectedRepository = owner + "/" + name;
        Call<String> fileCall = RetrofitClient.getService().getFileContentRaw(owner, name, path, "application/vnd.github.raw+json");
        activeCalls.add(fileCall);
        fileCall.enqueue(new Callback<String>() {
            @Override public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (!isSelectedRepository(expectedRepository)) return;
                if (response.code() == 401) {
                    openLogin();
                    return;
                }
                if (!response.isSuccessful() || response.body() == null) return;
                String packageName = parsePackageName(path, response.body());
                if (packageName != null && selectedRepository != null) {
                    detailPackage.setText(getString(R.string.github_package_name, packageName));
                    detailPackage.setVisibility(View.VISIBLE);
                }
            }

            @Override public void onFailure(@NonNull Call<String> call, @NonNull Throwable throwable) {}
        });
    }

    private boolean isSelectedRepository(String fullName) {
        return selectedRepository != null
                && selectedRepository.getFullName() != null
                && selectedRepository.getFullName().equalsIgnoreCase(fullName);
    }

    private String parsePackageName(String path, String content) {
        String lower = path.toLowerCase(Locale.ROOT);
        Pattern pattern;
        if (lower.endsWith("package.json") || lower.endsWith("composer.json")) {
            pattern = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
        } else if (lower.endsWith("pyproject.toml") || lower.endsWith("cargo.toml") || lower.endsWith("pubspec.yaml")) {
            pattern = Pattern.compile("(?m)^\\s*name\\s*[:=]\\s*[\"']?([^\"'\\r\\n#]+)");
        } else if (lower.endsWith("pom.xml")) {
            pattern = Pattern.compile("(?s)<artifactId>\\s*([^<]+?)\\s*</artifactId>");
        } else if (lower.endsWith("androidmanifest.xml")) {
            pattern = Pattern.compile("package\\s*=\\s*[\"']([^\"']+)[\"']");
        } else {
            pattern = Pattern.compile("(?m)(?:namespace|applicationId)\\s*[\"'=:\\( ]+\\s*[\"']?([A-Za-z0-9_.-]+)");
        }
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) return null;
        String value = matcher.group(1);
        return value == null ? null : value.trim();
    }

    private void bindTopics(ChipGroup group, List<String> topics) {
        group.removeAllViews();
        for (String topic : topics) {
            if (topic == null || topic.trim().isEmpty()) continue;
            Chip chip = new Chip(requireContext());
            chip.setText(topic);
            chip.setClickable(false);
            chip.setCheckable(false);
            group.addView(chip);
        }
    }

    private void bindOptionalText(TextView view, String value, int labelRes) {
        if (value == null || value.trim().isEmpty()) {
            view.setVisibility(View.GONE);
        } else {
            view.setText(getString(labelRes, value));
            view.setVisibility(View.VISIBLE);
        }
    }

    private void addSelectedRepository() {
        if (selectedRepository == null || selectedRepository.getHtmlUrl() == null) return;
        RepositoryManager.add(requireContext(), new SourceRepository(
                selectedRepository.getHtmlUrl(),
                valueOrFallback(selectedRepository.getFullName(), selectedRepository.getName()),
                "github"));
        addToListButton.setText(R.string.github_added_to_list);
        addToListButton.setEnabled(false);
        refreshAdded();
        notifyCardsChanged();
        Toast.makeText(requireContext(), R.string.github_repository_added, Toast.LENGTH_SHORT).show();
    }

    private String valueOrFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private void openLogin() {
        if (redirectingToLogin || !isAdded()) return;
        redirectingToLogin = true;
        clearActiveCalls();
        Intent login = new Intent(requireContext(), LoginActivity.class);
        login.putExtra("return_to_github_search", true);
        startActivity(login);
    }

    private void clearActiveCalls() {
        for (Call<?> call : activeCalls) {
            if (call != null && !call.isCanceled()) call.cancel();
        }
        activeCalls.clear();
        loadingPage = false;
        if (searchProgress != null) {
            searchProgress.animate().cancel();
            searchProgress.setVisibility(View.GONE);
        }
        revealList(false);
        if (resultsRefresh != null) resultsRefresh.setRefreshing(false);
        setFooter(false);
    }

    private final class RepositoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemViewType(int position) {
            return position < repositories.size() ? TYPE_REPO : TYPE_FOOTER;
        }

        @NonNull
        @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_FOOTER) {
                return new RecyclerView.ViewHolder(inflater.inflate(R.layout.item_loading_footer, parent, false)) {};
            }
            return new RepositoryViewHolder(inflater.inflate(R.layout.item_github_repo_card, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof RepositoryViewHolder && position < repositories.size()) {
                ((RepositoryViewHolder) holder).bind(repositories.get(position));
            }
        }

        @Override public int getItemCount() {
            return repositories.size() + (footerVisible ? 1 : 0);
        }

        final class RepositoryViewHolder extends RecyclerView.ViewHolder {
            private final ShapeableImageView avatar;
            private final TextView name;
            private final TextView owner;
            private final TextView stars;
            private final TextView description;
            private final TextView topics;
            private final View langDot;
            private final TextView languageText;
            private final TextView forks;
            private final TextView updated;
            private final MaterialButton addButton;

            RepositoryViewHolder(@NonNull View itemView) {
                super(itemView);
                avatar = itemView.findViewById(R.id.repoAvatar);
                name = itemView.findViewById(R.id.repoName);
                owner = itemView.findViewById(R.id.repoOwner);
                stars = itemView.findViewById(R.id.repoStars);
                description = itemView.findViewById(R.id.repoDescription);
                topics = itemView.findViewById(R.id.repoTopics);
                langDot = itemView.findViewById(R.id.repoLangDot);
                languageText = itemView.findViewById(R.id.repoLanguage);
                forks = itemView.findViewById(R.id.repoForks);
                updated = itemView.findViewById(R.id.repoUpdated);
                addButton = itemView.findViewById(R.id.repoAddButton);
            }

            void bind(GitHubRepo repository) {
                name.setText(valueOrFallback(repository.getName(), getString(R.string.unknown_value)));
                owner.setText(valueOrFallback(repository.getOwnerLogin(), getString(R.string.unknown_value)));
                stars.setText("\u2605 " + formatCount(repository.getStargazersCount()));
                description.setText(valueOrFallback(repository.getDescription(), getString(R.string.github_no_description)));

                StringBuilder tags = new StringBuilder();
                int shown = 0;
                if (repository.getTopics() != null) {
                    for (String topic : repository.getTopics()) {
                        if (topic == null || topic.trim().isEmpty() || "android".equalsIgnoreCase(topic)) continue;
                        if (shown > 0) tags.append("  ");
                        tags.append('#').append(topic.trim());
                        if (++shown == 4) break;
                    }
                }
                topics.setText(tags);
                topics.setVisibility(shown == 0 ? View.GONE : View.VISIBLE);

                String language = repository.getLanguage();
                boolean hasLanguage = language != null && !language.trim().isEmpty();
                langDot.setVisibility(hasLanguage ? View.VISIBLE : View.GONE);
                languageText.setVisibility(hasLanguage ? View.VISIBLE : View.GONE);
                if (hasLanguage) {
                    languageText.setText(language);
                    GradientDrawable dot = new GradientDrawable();
                    dot.setShape(GradientDrawable.OVAL);
                    dot.setColor(languageColor(language));
                    langDot.setBackground(dot);
                }
                forks.setText(getString(R.string.github_forks_short, formatCount(repository.getForksCount())));
                CharSequence when = relativeTime(repository.getPushedAt());
                updated.setText(when == null ? "" : getString(R.string.github_updated_ago, when));

                String avatarUrl = repository.getOwnerAvatarUrl();
                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                    IconLoader.loadAvatar(avatar, avatarUrl);
                } else {
                    Glide.with(avatar).clear(avatar);
                    avatar.setImageDrawable(null);
                }

                boolean added = isRepositoryAdded(repository);
                addButton.setIconResource(TablerIconRes.get(itemView.getContext(), added ? "ic_circle_check" : "ic_plus"));
                addButton.setEnabled(!added);
                addButton.setAlpha(added ? 0.6f : 1f);
                addButton.setOnClickListener(v -> {
                    AppPreferences.performHaptic(v);
                    quickAdd(repository);
                });
                itemView.setOnClickListener(v -> showDetail(repository));
            }
        }
    }
}
