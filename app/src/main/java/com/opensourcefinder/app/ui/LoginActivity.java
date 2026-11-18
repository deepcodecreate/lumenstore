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
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.opensourcefinder.app.MainActivity;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.model.GitHubUser;
import com.opensourcefinder.app.network.GitHubApiService;
import com.opensourcefinder.app.network.GitHubTokenProvider;
import com.opensourcefinder.app.network.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends BaseActivity {

    private View checkingGroup;
    private View formGroup;
    private TextInputLayout tokenInputLayout;
    private TextInputEditText tokenInput;
    private android.widget.TextView errorText;
    private MaterialButton loginButton;
    private com.google.android.material.loadingindicator.LoadingIndicator formProgress;
    private boolean returnToGithubSearch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        MotionHelper.applyDistinctActivityTransition(this);
        setContentView(R.layout.activity_login);
        MotionHelper.animateActivityContent(this);
        ThemeManager.applyWindow(this);

        checkingGroup = findViewById(R.id.checkingGroup);
        formGroup = findViewById(R.id.formGroup);
        tokenInputLayout = findViewById(R.id.tokenInputLayout);
        tokenInput = findViewById(R.id.tokenInput);
        errorText = findViewById(R.id.errorText);
        loginButton = findViewById(R.id.loginButton);
        formProgress = findViewById(R.id.formProgress);
        returnToGithubSearch = getIntent().getBooleanExtra("return_to_github_search", false);

        findViewById(R.id.createTokenLink).setOnClickListener(view -> openTokenCreationPage());
        loginButton.setOnClickListener(view -> attemptLogin());

        View skip = findViewById(R.id.skipButton);
        if (skip != null) {
            skip.setVisibility(View.VISIBLE);
            skip.setOnClickListener(v -> goToMain());
        }

        if (GitHubTokenProvider.hasToken()) {
            showChecking();
            silentlyRevalidateStoredToken();
        } else {
            showForm(null);
        }
    }

    private void silentlyRevalidateStoredToken() {
        String storedToken = GitHubTokenProvider.getToken();
        validateTokenWithGitHub(storedToken, new ValidationCallback() {
            @Override
            public void onValid(GitHubUser user) {
                goToMain();
            }

            @Override
            public void onInvalid(@Nullable String message) {
                GitHubTokenProvider.clearToken(LoginActivity.this);
                showForm(getString(R.string.session_expired_message));
            }
        });
    }

    private void attemptLogin() {
        String token = tokenInput.getText() != null ? tokenInput.getText().toString().trim() : "";

        if (token.isEmpty()) {
            tokenInputLayout.setError(getString(R.string.error_empty_token));
            return;
        }
        tokenInputLayout.setError(null);
        hideError();
        setLoading(true);

        validateTokenWithGitHub(token, new ValidationCallback() {
            @Override
            public void onValid(GitHubUser user) {
                GitHubTokenProvider.saveToken(LoginActivity.this, token);
                setLoading(false);
                goToMain();
            }

            @Override
            public void onInvalid(@Nullable String message) {
                setLoading(false);
                showError(message != null ? message : getString(R.string.error_invalid_token));
            }
        });
    }

    private interface ValidationCallback {
        void onValid(GitHubUser user);
        void onInvalid(@Nullable String message);
    }

    private void validateTokenWithGitHub(String token, ValidationCallback callback) {
        GitHubTokenProvider.setToken(token);
        GitHubApiService api = RetrofitClient.getService();

        api.getAuthenticatedUser().enqueue(new Callback<GitHubUser>() {
            @Override
            public void onResponse(Call<GitHubUser> call, Response<GitHubUser> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onValid(response.body());
                } else {
                    GitHubTokenProvider.setToken("");
                    if (response.code() == 401 || response.code() == 403) {
                        callback.onInvalid(getString(R.string.error_invalid_token));
                    } else {
                        callback.onInvalid(getString(R.string.error_generic, response.code()));
                    }
                }
            }

            @Override
            public void onFailure(Call<GitHubUser> call, Throwable throwable) {
                GitHubTokenProvider.setToken("");
                callback.onInvalid(getString(R.string.error_network));
            }
        });
    }

    private void showChecking() {
        checkingGroup.setVisibility(View.VISIBLE);
        formGroup.setVisibility(View.GONE);
    }

    private void showForm(@Nullable String prefilledError) {
        checkingGroup.setVisibility(View.GONE);
        formGroup.setVisibility(View.VISIBLE);
        if (prefilledError != null) {
            showError(prefilledError);
        }
    }

    private void setLoading(boolean loading) {
        loginButton.setEnabled(!loading);
        tokenInput.setEnabled(!loading);
        formProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        errorText.setVisibility(View.GONE);
    }

    private void openTokenCreationPage() {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/settings/tokens/new?scopes=public_repo&description=OpenSourceFinder"));
        startActivity(browserIntent);
    }

    private void goToMain() {
        if (returnToGithubSearch) {
            Intent search = new Intent(this, MainActivity.class);
            search.putExtra("open_tab", MainActivity.TAG_GITHUB);
            search.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(search);
            finish();
            return;
        }
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }
}
