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
import android.net.Uri;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.model.GitHubEvent;
import com.opensourcefinder.app.model.GitHubOrg;
import com.opensourcefinder.app.model.GitHubRepo;
import com.opensourcefinder.app.model.GitHubUser;
import com.opensourcefinder.app.network.GitHubApiService;
import com.opensourcefinder.app.network.GitHubTokenProvider;
import com.opensourcefinder.app.network.RetrofitClient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GitHubProfileActivity extends BaseActivity {

    public static final String EXTRA_CACHED_USER = "extra_cached_user";

    private RecyclerView profileRecyclerView;
    private GitHubProfileAdapter adapter;
    private boolean readmeRequested = false;
    private boolean extrasRequested = false;
    private final List<Call<?>> extraCalls = new ArrayList<>();
    private com.google.android.material.loadingindicator.LoadingIndicator progress;
    private Call<GitHubUser> profileCall;
    private Call<String> readmeCall;
    private boolean destroyed;

    private GitHubUser currentUser;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        MotionHelper.applyDistinctActivityTransition(this);
        setContentView(R.layout.activity_github_profile);
        MotionHelper.animateActivityContent(this);
        ThemeManager.applyWindow(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());

        progress = findViewById(R.id.profileProgress);

        profileRecyclerView = findViewById(R.id.profileRecyclerView);
        profileRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        profileRecyclerView.setItemAnimator(null);
        adapter = new GitHubProfileAdapter(new GitHubProfileAdapter.ActionListener() {
            @Override public void onLogout() { confirmLogout(); }
            @Override public void onShare() { shareProfile(); }
            @Override public void onOpenUrl(@androidx.annotation.NonNull String url) { openUrl(url); }
        });
        profileRecyclerView.setAdapter(adapter);

        GitHubUser cachedUser = (GitHubUser) getIntent().getSerializableExtra(EXTRA_CACHED_USER);
        if (cachedUser != null) {
            bindUser(cachedUser);
        } else {
            progress.setVisibility(View.VISIBLE);
        }

        refreshProfile();
    }

    private void refreshProfile() {
        if (!GitHubTokenProvider.hasToken()) {
            finish();
            return;
        }
        GitHubApiService api = RetrofitClient.getService();
        profileCall = api.getAuthenticatedUser();
        profileCall.enqueue(new Callback<GitHubUser>() {
            @Override
            public void onResponse(Call<GitHubUser> call, Response<GitHubUser> response) {
                if (!isScreenActive()) return;
                progress.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    bindUser(response.body());
                } else if (currentUser == null) {
                    finish();
                }
            }

            @Override
            public void onFailure(Call<GitHubUser> call, Throwable throwable) {
                if (!isScreenActive()) return;
                progress.setVisibility(View.GONE);
            }
        });
    }

    private void bindUser(GitHubUser user) {
        if (!isScreenActive() || user == null) return;
        currentUser = user;
        adapter.setUser(user);

        loadExtras(user.getLogin());
        loadProfileReadme(user.getLogin());
    }

    private void loadProfileReadme(String login) {
        if (readmeRequested || login == null || login.trim().isEmpty()) return;
        readmeRequested = true;
        readmeCall = RetrofitClient.getService().getReadmeRaw(login, login, "application/vnd.github.raw+json");
        readmeCall.enqueue(new Callback<String>() {
                    @Override public void onResponse(Call<String> call, Response<String> response) {
                        if (!isScreenActive() || currentUser == null || !login.equalsIgnoreCase(currentUser.getLogin())) return;
                        if (response.isSuccessful() && response.body() != null && !response.body().trim().isEmpty()) {
                            String readme = response.body();
                            if (readme.length() > 120000) readme = readme.substring(0, 120000);
                            adapter.setReadme(readme);
                        } else {
                            adapter.setReadme(null);
                        }
                    }

                    @Override public void onFailure(Call<String> call, Throwable throwable) {
                        if (!isScreenActive()) return;
                        adapter.setReadme(null);
                    }
                });
    }

    private boolean isScreenActive() {
        return !destroyed && !isFinishing() && (android.os.Build.VERSION.SDK_INT < 17 || !isDestroyed());
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException ignored) {
        }
    }

    private void shareProfile() {
        if (currentUser == null || currentUser.getHtmlUrl() == null) return;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, currentUser.getHtmlUrl());
        startActivity(Intent.createChooser(send, getString(R.string.profile_share)));
    }

    private void loadExtras(String login) {
        if (extrasRequested || login == null || login.trim().isEmpty()) return;
        extrasRequested = true;
        GitHubApiService api = RetrofitClient.getService();

        Call<List<GitHubRepo>> repos = api.listMyRepos(30, "pushed", "owner");
        extraCalls.add(repos);
        repos.enqueue(new Callback<List<GitHubRepo>>() {
            @Override public void onResponse(Call<List<GitHubRepo>> call, Response<List<GitHubRepo>> r) {
                if (isScreenActive() && r.isSuccessful()) adapter.setRepos(r.body());
            }
            @Override public void onFailure(Call<List<GitHubRepo>> call, Throwable t) { }
        });

        Call<List<GitHubRepo>> starred = api.listMyStarred(6, 1);
        extraCalls.add(starred);
        starred.enqueue(new Callback<List<GitHubRepo>>() {
            @Override public void onResponse(Call<List<GitHubRepo>> call, Response<List<GitHubRepo>> r) {
                if (isScreenActive() && r.isSuccessful()) adapter.setStarred(r.body());
            }
            @Override public void onFailure(Call<List<GitHubRepo>> call, Throwable t) { }
        });

        Call<List<GitHubRepo>> starCount = api.listMyStarred(1, 1);
        extraCalls.add(starCount);
        starCount.enqueue(new Callback<List<GitHubRepo>>() {
            @Override public void onResponse(Call<List<GitHubRepo>> call, Response<List<GitHubRepo>> r) {
                if (!isScreenActive() || !r.isSuccessful()) return;
                String link = r.headers().get("Link");
                int count = r.body() != null ? r.body().size() : 0;
                if (link != null) {
                    Matcher m = Pattern.compile("[?&]page=(\\d+)>; rel=\"last\"").matcher(link);
                    if (m.find()) count = Integer.parseInt(m.group(1));
                }
                adapter.setStarredCount(count);
            }
            @Override public void onFailure(Call<List<GitHubRepo>> call, Throwable t) { }
        });

        Call<List<GitHubOrg>> orgs = api.listMyOrgs(12);
        extraCalls.add(orgs);
        orgs.enqueue(new Callback<List<GitHubOrg>>() {
            @Override public void onResponse(Call<List<GitHubOrg>> call, Response<List<GitHubOrg>> r) {
                if (isScreenActive() && r.isSuccessful()) adapter.setOrgs(r.body());
            }
            @Override public void onFailure(Call<List<GitHubOrg>> call, Throwable t) { }
        });

        Call<List<GitHubUser>> followers = api.listMyFollowers(12);
        extraCalls.add(followers);
        followers.enqueue(new Callback<List<GitHubUser>>() {
            @Override public void onResponse(Call<List<GitHubUser>> call, Response<List<GitHubUser>> r) {
                if (isScreenActive() && r.isSuccessful()) adapter.setFollowers(r.body());
            }
            @Override public void onFailure(Call<List<GitHubUser>> call, Throwable t) { }
        });

        Call<List<GitHubEvent>> events = api.listPublicEvents(login, 10);
        extraCalls.add(events);
        events.enqueue(new Callback<List<GitHubEvent>>() {
            @Override public void onResponse(Call<List<GitHubEvent>> call, Response<List<GitHubEvent>> r) {
                if (isScreenActive() && r.isSuccessful()) adapter.setEvents(r.body());
            }
            @Override public void onFailure(Call<List<GitHubEvent>> call, Throwable t) { }
        });
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.logout, (dialog, which) -> {
                    GitHubTokenProvider.clearToken(this);
                    Intent goToLogin = new Intent(this, LoginActivity.class);
                    goToLogin.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(goToLogin);
                    finish();
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (profileCall != null) profileCall.cancel();
        if (readmeCall != null) readmeCall.cancel();
        for (Call<?> c : extraCalls) c.cancel();
        super.onDestroy();
    }
}
