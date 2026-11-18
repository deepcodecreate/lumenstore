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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.loadingindicator.LoadingIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.model.GitHubRepo;
import com.opensourcefinder.app.model.GitHubSearchResponse;
import com.opensourcefinder.app.model.GitHubTree;
import com.opensourcefinder.app.model.SourceRepository;
import com.opensourcefinder.app.network.GitHubApiService;
import com.opensourcefinder.app.network.GitHubTokenProvider;
import com.opensourcefinder.app.network.RetrofitClient;
import com.opensourcefinder.app.scanner.RepositoryManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GitHubSearchActivity extends BaseActivity {

    private static final int SEARCH_PAGE_SIZE = 30;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Call<?>> activeCalls = new ArrayList<>();
    private final List<GitHubRepo> repositories = new ArrayList<>();

    private MaterialToolbar toolbar;
    private TextInputLayout searchInputLayout;
    private TextInputEditText searchInput;
    private ChipGroup sortChips;
    private LoadingIndicator searchProgress;
    private TextView resultSummary;
    private TextView emptyState;
    private RecyclerView resultsList;
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
    private String sortMode = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        if (!GitHubTokenProvider.hasToken()) {
            openLogin();
            return;
        }
        MotionHelper.applyDistinctActivityTransition(this);
        setContentView(R.layout.activity_github_search);
        MotionHelper.animateActivityContent(this);
        ThemeManager.applyWindow(this);
        bindViews();
        setupToolbar();
        setupSearch();
        applySortChipColors();
        showSearch();
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        searchInputLayout = findViewById(R.id.searchInputLayout);
        searchInput = findViewById(R.id.searchInput);
        KeyboardFocusHelper.attach(searchInput);
        sortChips = findViewById(R.id.sortChips);
        searchProgress = findViewById(R.id.searchProgress);
        resultSummary = findViewById(R.id.resultSummary);
        emptyState = findViewById(R.id.emptyState);
        resultsList = findViewById(R.id.resultsList);
        searchContent = findViewById(R.id.searchContent);
        detailContent = findViewById(R.id.detailContent);
        detailFullName = findViewById(R.id.detailFullName);
        detailOwner = findViewById(R.id.detailOwner);
        detailDescription = findViewById(R.id.detailDescription);
        detailTopics = findViewById(R.id.detailTopics);
        detailStats = findViewById(R.id.detailStats);
        detailLanguage = findViewById(R.id.detailLanguage);
        detailPackage = findViewById(R.id.detailPackage);
        detailLicense = findViewById(R.id.detailLicense);
        readmeCard = findViewById(R.id.readmeCard);
        readmeProgress = findViewById(R.id.readmeProgress);
        readmeContent = findViewById(R.id.readmeContent);
        addToListButton = findViewById(R.id.addToListButton);
        openInBrowserButton = findViewById(R.id.openInBrowserButton);
        resultsList.setLayoutManager(new LinearLayoutManager(this));
        resultsList.setAdapter(new RepositoryAdapter());
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> {
            if (detailContent.getVisibility() == View.VISIBLE) showSearch();
            else finish();
        });
    }

    private void setupSearch() {
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch();
                return true;
            }
            return false;
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                handler.removeCallbacksAndMessages("github_search");
                String query = s == null ? "" : s.toString().trim();
                if (query.length() < 2) {
                    repositories.clear();
                    resultsList.getAdapter().notifyDataSetChanged();
                    resultSummary.setVisibility(View.GONE);
                    searchProgress.setVisibility(View.GONE);
                    emptyState.setText(R.string.github_search_empty);
                    emptyState.setVisibility(View.VISIBLE);
                    return;
                }
                handler.postAtTime(() -> runSearch(), "github_search", android.os.SystemClock.uptimeMillis() + 450);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        sortChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.sortStars) sortMode = "stars";
            else if (id == R.id.sortUpdated) sortMode = "updated";
            else sortMode = "";
            applySortChipColors();
            if (searchInput.getText() != null && searchInput.getText().toString().trim().length() >= 2) runSearch();
        });
        addToListButton.setOnClickListener(v -> addSelectedRepository());
        openInBrowserButton.setOnClickListener(v -> {
            if (selectedRepository != null && selectedRepository.getHtmlUrl() != null) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(selectedRepository.getHtmlUrl())));
            }
        });
    }

    private void applySortChipColors() {
        if (sortChips == null) return;
        int primaryContainer = com.google.android.material.color.MaterialColors.getColor(
                sortChips, com.google.android.material.R.attr.colorPrimaryContainer);
        int onPrimaryContainer = com.google.android.material.color.MaterialColors.getColor(
                sortChips, com.google.android.material.R.attr.colorOnPrimaryContainer);
        int surfaceContainerHigh = com.google.android.material.color.MaterialColors.getColor(
                sortChips, com.google.android.material.R.attr.colorSurfaceContainerHigh);
        int onSurface = com.google.android.material.color.MaterialColors.getColor(
                sortChips, com.google.android.material.R.attr.colorOnSurface);
        for (int i = 0; i < sortChips.getChildCount(); i++) {
            View child = sortChips.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                boolean isChecked = chip.isChecked();
                chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                        isChecked ? primaryContainer : surfaceContainerHigh));
                chip.setTextColor(isChecked ? onPrimaryContainer : onSurface);
            }
        }
    }

    private void runSearch() {
        if (searchInput == null || !GitHubTokenProvider.hasToken()) return;
        String query = searchInput.getText() == null ? "" : searchInput.getText().toString().trim();
        if (query.length() < 2) return;
        if (searchCall != null) searchCall.cancel();
        clearActiveCalls();
        long generation = ++searchGeneration;
        searchProgress.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        resultSummary.setVisibility(View.GONE);
        String sort = sortMode.isEmpty() ? null : sortMode;
        searchCall = RetrofitClient.getService().searchRepositories(query, SEARCH_PAGE_SIZE, sort, "desc");
        activeCalls.add(searchCall);
        searchCall.enqueue(new Callback<GitHubSearchResponse>() {
            @Override public void onResponse(@NonNull Call<GitHubSearchResponse> call, @NonNull Response<GitHubSearchResponse> response) {
                if (generation != searchGeneration) return;
                searchProgress.setVisibility(View.GONE);
                if (response.code() == 401) {
                    openLogin();
                    return;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    showSearchError();
                    return;
                }
                repositories.clear();
                if (response.body().getItems() != null) repositories.addAll(response.body().getItems());
                resultsList.getAdapter().notifyDataSetChanged();
                if (repositories.isEmpty()) {
                    emptyState.setText(R.string.github_search_no_results);
                    emptyState.setVisibility(View.VISIBLE);
                } else {
                    resultSummary.setText(getString(R.string.github_search_result_count, response.body().getTotalCount()));
                    resultSummary.setVisibility(View.VISIBLE);
                }
            }

            @Override public void onFailure(@NonNull Call<GitHubSearchResponse> call, @NonNull Throwable throwable) {
                if (generation != searchGeneration || call.isCanceled()) return;
                searchProgress.setVisibility(View.GONE);
                showSearchError();
            }
        });
    }

    private void showSearchError() {
        repositories.clear();
        resultsList.getAdapter().notifyDataSetChanged();
        emptyState.setText(R.string.github_search_error);
        emptyState.setVisibility(View.VISIBLE);
    }

    private void showSearch() {
        clearActiveCalls();
        searchContent.setVisibility(View.VISIBLE);
        detailContent.setVisibility(View.GONE);
        toolbar.setTitle(R.string.github_search_title);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private void showDetail(GitHubRepo repository) {
        selectedRepository = repository;
        searchContent.setVisibility(View.GONE);
        detailContent.setVisibility(View.VISIBLE);
        toolbar.setTitle(R.string.github_repository);
        bindRepositoryDetail(repository);
        loadRepositoryDetail(repository);
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
            Chip chip = new Chip(this);
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
        RepositoryManager.add(this, new SourceRepository(
                selectedRepository.getHtmlUrl(),
                valueOrFallback(selectedRepository.getFullName(), selectedRepository.getName()),
                "github"));
        addToListButton.setText(R.string.github_added_to_list);
        addToListButton.setEnabled(false);
        Toast.makeText(this, R.string.github_repository_added, Toast.LENGTH_SHORT).show();
    }

    private boolean isRepositoryAdded(GitHubRepo repository) {
        if (repository == null || repository.getHtmlUrl() == null) return false;
        for (SourceRepository source : RepositoryManager.get(this)) {
            if (source.getUrl() != null && source.getUrl().equalsIgnoreCase(repository.getHtmlUrl())) return true;
        }
        return false;
    }

    private String valueOrFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private void clearActiveCalls() {
        for (Call<?> call : activeCalls) {
            if (call != null && !call.isCanceled()) call.cancel();
        }
        activeCalls.clear();
    }

    private void openLogin() {
        if (redirectingToLogin || isFinishing()) return;
        redirectingToLogin = true;
        clearActiveCalls();
        Intent login = new Intent(this, LoginActivity.class);
        login.putExtra("return_to_github_search", true);
        startActivity(login);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        clearActiveCalls();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (detailContent != null && detailContent.getVisibility() == View.VISIBLE) {
            showSearch();
            return;
        }
        super.onBackPressed();
    }

    private final class RepositoryAdapter extends RecyclerView.Adapter<RepositoryAdapter.RepositoryViewHolder> {
        @NonNull
        @Override public RepositoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_github_repository, parent, false);
            return new RepositoryViewHolder(view);
        }

        @Override public void onBindViewHolder(@NonNull RepositoryViewHolder holder, int position) {
            holder.bind(repositories.get(position));
        }

        @Override public int getItemCount() {
            return repositories.size();
        }

        final class RepositoryViewHolder extends RecyclerView.ViewHolder {
            private final TextView name;
            private final TextView owner;
            private final TextView description;
            private final ChipGroup topics;
            private final TextView meta;

            RepositoryViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.repositoryName);
                owner = itemView.findViewById(R.id.repositoryOwner);
                description = itemView.findViewById(R.id.repositoryDescription);
                topics = itemView.findViewById(R.id.repositoryTopics);
                meta = itemView.findViewById(R.id.repositoryMeta);
            }

            void bind(GitHubRepo repository) {
                name.setText(valueOrFallback(repository.getName(), getString(R.string.unknown_value)));
                owner.setText(getString(R.string.github_repository_owner, valueOrFallback(repository.getOwnerLogin(), getString(R.string.unknown_value))));
                description.setText(valueOrFallback(repository.getDescription(), getString(R.string.github_no_description)));
                bindTopics(topics, repository.getTopics());
                String language = valueOrFallback(repository.getLanguage(), getString(R.string.unknown_value));
                meta.setText(getString(R.string.github_repository_meta, repository.getStargazersCount(), language));
                itemView.setOnClickListener(v -> showDetail(repository));
            }
        }
    }
}
