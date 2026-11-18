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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.loadingindicator.LoadingIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.opensourcefinder.app.MainActivity;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.model.GitHubUser;
import com.opensourcefinder.app.network.GitHubTokenProvider;
import com.opensourcefinder.app.network.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WelcomeActivity extends BaseActivity {

    private TextInputLayout tokenInputLayout;
    private TextInputEditText tokenInput;
    private TextView errorText;
    private MaterialButton loginButton;
    private MaterialButton skipButton;
    private LoadingIndicator progress;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        MotionHelper.applyDistinctActivityTransition(this);
        setContentView(R.layout.activity_welcome);
        ThemeManager.applyWindow(this);

        tokenInputLayout = findViewById(R.id.tokenInputLayout);
        tokenInput = findViewById(R.id.tokenInput);
        errorText = findViewById(R.id.errorText);
        loginButton = findViewById(R.id.loginButton);
        skipButton = findViewById(R.id.skipButton);
        progress = findViewById(R.id.formProgress);

        ShapeView halo = findViewById(R.id.welcomeHalo);
        halo.setFillColor(MaterialColors.getColor(halo,
                com.google.android.material.R.attr.colorPrimaryContainer, 0xFF444444));
        halo.setShape(AppPreferences.getIconShape(this), false);
        halo.setIdleSpin(true);

        LinearLayout features = findViewById(R.id.welcomeFeatures);
        addFeature(features, "ic_refresh", R.string.onb_feature_scan_title, R.string.onb_feature_scan_text);
        addFeature(features, "ic_download", R.string.onb_feature_install_title, R.string.onb_feature_install_text);
        addFeature(features, "ic_compass", R.string.onb_feature_explore_title, R.string.onb_feature_explore_text);

        findViewById(R.id.createTokenLink).setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/settings/tokens/new?scopes=public_repo&description=OpenSourceFinder"))));
        loginButton.setOnClickListener(v -> attemptLogin());
        skipButton.setOnClickListener(v -> finishWelcome());
        tokenInput.setOnEditorActionListener((v, actionId, event) -> { attemptLogin(); return true; });

        if (savedInstanceState == null && !AppPreferences.isReduceMotion(this)) animateIn();
    }

    private void addFeature(LinearLayout parent, String iconName, int titleRes, int textRes) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_onboarding_feature, parent, false);
        ShapedIconView badge = row.findViewById(R.id.featureBadge);
        badge.setImageResource(TablerIconRes.get(this, iconName));
        badge.setColorFilter(MaterialColors.getColor(badge,
                androidx.appcompat.R.attr.colorPrimary, 0xFFFFFFFF));
        ((TextView) row.findViewById(R.id.featureTitle)).setText(titleRes);
        ((TextView) row.findViewById(R.id.featureText)).setText(textRes);
        parent.addView(row);
    }

    private void animateIn() {
        View[] steps = {findViewById(R.id.welcomeHero), findViewById(R.id.welcomeTitle),
                findViewById(R.id.welcomeSubtitle), findViewById(R.id.welcomeFeatures),
                findViewById(R.id.welcomeLoginCard), skipButton};
        float shift = 18f * getResources().getDisplayMetrics().density;
        long unit = MotionHelper.getDuration(this, 260, 420);
        for (int i = 0; i < steps.length; i++) {
            View v = steps[i];
            v.setAlpha(0f);
            v.setTranslationY(shift);
            v.animate().alpha(1f).translationY(0f).setStartDelay(60L + i * 70L).setDuration(unit)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
    }

    private void attemptLogin() {
        String token = tokenInput.getText() != null ? tokenInput.getText().toString().trim() : "";
        if (token.isEmpty()) {
            tokenInputLayout.setError(getString(R.string.error_empty_token));
            return;
        }
        tokenInputLayout.setError(null);
        errorText.setVisibility(View.GONE);
        setLoading(true);
        final String previous = GitHubTokenProvider.getToken();
        GitHubTokenProvider.setToken(token);
        RetrofitClient.getService().getAuthenticatedUser().enqueue(new Callback<GitHubUser>() {
            @Override public void onResponse(Call<GitHubUser> call, Response<GitHubUser> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null) {
                    GitHubTokenProvider.saveToken(WelcomeActivity.this, token);
                    setLoading(false);
                    finishWelcome();
                    return;
                }
                GitHubTokenProvider.setToken(previous == null ? "" : previous);
                setLoading(false);
                showError(response.code() == 401 || response.code() == 403
                        ? getString(R.string.error_invalid_token)
                        : getString(R.string.error_generic, response.code()));
            }

            @Override public void onFailure(Call<GitHubUser> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                GitHubTokenProvider.setToken(previous == null ? "" : previous);
                setLoading(false);
                showError(getString(R.string.error_network));
            }
        });
    }

    private void setLoading(boolean loading) {
        loginButton.setEnabled(!loading);
        skipButton.setEnabled(!loading);
        tokenInput.setEnabled(!loading);
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    private void finishWelcome() {
        AppPreferences.setWelcomeDone(this);
        Intent main = new Intent(this, MainActivity.class);
        if (getIntent() != null && getIntent().getExtras() != null) main.putExtras(getIntent().getExtras());
        main.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(main);
        finish();
    }
}
