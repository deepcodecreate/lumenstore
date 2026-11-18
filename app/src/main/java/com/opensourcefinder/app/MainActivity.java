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

package com.opensourcefinder.app;

import android.os.Build;
import android.view.View;
import android.os.Bundle;
import android.content.Intent;
import android.view.HapticFeedbackConstants;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.opensourcefinder.app.background.BackgroundProtection;
import com.opensourcefinder.app.ui.AppPreferences;
import com.opensourcefinder.app.ui.BaseActivity;
import com.opensourcefinder.app.ui.DownloadsFragment;
import com.opensourcefinder.app.ui.ExploreFragment;
import com.opensourcefinder.app.ui.GitHubExploreFragment;
import com.opensourcefinder.app.ui.HomeFragment;
import com.opensourcefinder.app.ui.SettingsFragment;
import com.opensourcefinder.app.ui.MotionHelper;
import com.opensourcefinder.app.ui.NavCapsule;
import com.opensourcefinder.app.ui.ThemeManager;

public class MainActivity extends BaseActivity {
    public static final String TAG_HOME = "tab_home";
    public static final String TAG_EXPLORE = "tab_explore";
    public static final String TAG_SEARCH = "tab_search";
    public static final String TAG_DOWNLOADS = "tab_downloads";
    public static final String TAG_SETTINGS = "tab_settings";
    public static final String TAG_GITHUB = "tab_github";

    private String currentTag = TAG_HOME;

    private String tabBeforeSettings = TAG_HOME;
    private androidx.activity.OnBackPressedCallback settingsBackCallback;
    private com.google.android.material.navigation.NavigationBarView.OnItemSelectedListener navListener;

    @Override protected boolean handlesNavigationBarInset() { return true; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state == null && !AppPreferences.isWelcomeDone(this)) {
            if (com.opensourcefinder.app.network.GitHubTokenProvider.hasToken()) {
                AppPreferences.setWelcomeDone(this);
            } else {
                Intent welcome = new Intent(this, com.opensourcefinder.app.ui.WelcomeActivity.class);
                if (getIntent() != null && getIntent().getExtras() != null) welcome.putExtras(getIntent().getExtras());
                startActivity(welcome);
                finish();
                return;
            }
        }
        ThemeManager.apply(this);
        MotionHelper.applyActivityTransition(this, true);
        setContentView(R.layout.activity_main);
        MotionHelper.animateActivityContent(this);
        MotionHelper.keepBottomNavStatic(this);
        ThemeManager.applyWindow(this);

        if (state == null) {
            currentTag = AppPreferences.getDefaultTab(this);
            if (TAG_SEARCH.equals(currentTag)) currentTag = TAG_EXPLORE;
            if (TAG_SETTINGS.equals(currentTag)) currentTag = TAG_HOME;
            Fragment initial = createFragment(currentTag);
            if (initial == null) { initial = new HomeFragment(); currentTag = TAG_HOME; }
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragmentContainer, initial, currentTag)
                    .commitNow();
        } else {
            currentTag = state.getString("currentTag", TAG_HOME);
            tabBeforeSettings = state.getString("tabBeforeSettings", TAG_HOME);
        }
        settingsBackCallback = new androidx.activity.OnBackPressedCallback(TAG_SETTINGS.equals(currentTag)) {
            @Override public void handleOnBackPressed() { closeSettings(); }
        };
        getOnBackPressedDispatcher().addCallback(this, settingsBackCallback);

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(idForTag(TAG_SETTINGS.equals(currentTag) ? tabBeforeSettings : currentTag));
        if (TAG_SETTINGS.equals(currentTag)) nav.setVisibility(View.GONE);
        navListener = item -> {
            AppPreferences.performHaptic(nav);
            int id = item.getItemId();
            MotionHelper.bounceNavItem(nav, id);
            if (id == R.id.nav_home) showTab(TAG_HOME);
            else if (id == R.id.nav_explore) showTab(TAG_EXPLORE);
            else if (id == R.id.nav_github) showTab(TAG_GITHUB);
            else if (id == R.id.nav_downloads) showTab(TAG_DOWNLOADS);
            else return false;
            return true;
        };
        nav.setOnItemSelectedListener(navListener);
        nav.setOnItemReselectedListener(item -> resetTab(item.getItemId()));
        NavCapsule.install(nav);

        String openTab = getIntent().getStringExtra("open_tab");
        if (openTab != null) {
            showTab(openTab);
            getIntent().removeExtra("open_tab");
            deliverRepositoryLink();
        }
        if (getIntent().getBooleanExtra("force_rescan", false)) {
            showTab(TAG_HOME);
            HomeFragment home = homeFragment();
            if (home != null) home.requestFullRescan();
            getIntent().removeExtra("force_rescan");
        }
        if (getIntent().getBooleanExtra("rescan", false)) {
            showTab(TAG_HOME);
            HomeFragment home = homeFragment();
            if (home != null) home.requestRescan();
            getIntent().removeExtra("rescan");
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 4108);
        BackgroundProtection.request(this);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String openTab = intent.getStringExtra("open_tab");
        if (openTab != null) { showTab(openTab); intent.removeExtra("open_tab"); deliverRepositoryLink(); }
        if (intent.getBooleanExtra("force_rescan", false)) {
            showTab(TAG_HOME);
            HomeFragment home = homeFragment();
            if (home != null) home.requestFullRescan();
            intent.removeExtra("force_rescan");
        }
        if (intent.getBooleanExtra("rescan", false)) {
            showTab(TAG_HOME);
            HomeFragment home = homeFragment();
            if (home != null) home.requestRescan();
            intent.removeExtra("rescan");
        }
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("currentTag", currentTag);
        outState.putString("tabBeforeSettings", tabBeforeSettings);
    }

    public void showTab(String tag) {
        if (tag == null) return;
        if (TAG_SEARCH.equals(tag)) tag = TAG_EXPLORE;

        boolean toSettings = TAG_SETTINGS.equals(tag);
        if (toSettings && !TAG_SETTINGS.equals(currentTag)) tabBeforeSettings = currentTag;
        if (!toSettings) syncNavSelection(tag);
        setNavVisible(!toSettings);
        if (settingsBackCallback != null) settingsBackCallback.setEnabled(toSettings);

        if (tag.equals(currentTag) && findFrag(tag) != null) return;
        Fragment next = findFrag(tag);
        Fragment prev = findFrag(currentTag);
        if (next == null) next = createFragment(tag);
        if (next == null) return;

        int from = tabOrder(currentTag);
        int to = tabOrder(tag);
        boolean fromRight = to > from;
        if (getResources().getConfiguration().getLayoutDirection() == android.view.View.LAYOUT_DIRECTION_RTL) fromRight = !fromRight;

        androidx.fragment.app.FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.setReorderingAllowed(true);
        if (!AppPreferences.isReduceMotion(this)) {
            if (fromRight) {
                tx.setCustomAnimations(R.anim.tab_enter_from_right, R.anim.tab_exit_to_left);
            } else {
                tx.setCustomAnimations(R.anim.tab_enter_from_left, R.anim.tab_exit_to_right);
            }
        }
        if (prev != null) tx.hide(prev);
        if (next.isAdded()) tx.show(next);
        else tx.add(R.id.fragmentContainer, next, tag);
        tx.commit();
        currentTag = tag;

        if (!toSettings) syncNavSelection(tag);
    }

    public void openSettings() {
        showTab(TAG_SETTINGS);
    }

    public void closeSettings() {
        if (!TAG_SETTINGS.equals(currentTag)) return;
        String back = tabBeforeSettings == null || TAG_SETTINGS.equals(tabBeforeSettings) ? TAG_HOME : tabBeforeSettings;
        showTab(back);
    }

    private void setNavVisible(boolean visible) {
        View nav = findViewById(R.id.bottomNav);
        if (nav == null) return;
        if (visible && nav.getVisibility() == View.VISIBLE && nav.getTranslationY() == 0f) return;
        nav.animate().cancel();
        long duration = MotionHelper.getDuration(this, 320, 460);
        if (duration == 0L) {
            nav.setVisibility(visible ? View.VISIBLE : View.GONE);
            nav.setTranslationY(0f);
            nav.setAlpha(1f);
            return;
        }
        float distance = nav.getHeight() + 80f * getResources().getDisplayMetrics().density;
        if (visible) {
            nav.setVisibility(View.VISIBLE);
            if (nav.getAlpha() == 1f && nav.getTranslationY() == 0f) nav.setTranslationY(distance);
            nav.animate().translationY(0f).alpha(1f).setDuration(duration)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();
        } else {
            nav.animate().translationY(distance).alpha(0f).setDuration(duration / 2 + 60)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .withEndAction(() -> { if (TAG_SETTINGS.equals(currentTag)) nav.setVisibility(View.GONE); })
                    .start();
        }
    }

    private void deliverRepositoryLink() {
        Fragment f = findFrag(TAG_GITHUB);
        if (f instanceof GitHubExploreFragment) ((GitHubExploreFragment) f).consumeRepositoryIntent();
    }

    private void resetTab(int itemId) {
        AppPreferences.performHaptic(findViewById(R.id.bottomNav));
        if (itemId == R.id.nav_explore) {
            Fragment f = findFrag(TAG_EXPLORE);
            if (f instanceof ExploreFragment) ((ExploreFragment) f).resetToInitialState();
        } else if (itemId == R.id.nav_github) {
            Fragment f = findFrag(TAG_GITHUB);
            if (f instanceof GitHubExploreFragment) ((GitHubExploreFragment) f).resetToInitialState();
        }
    }

    private void syncNavSelection(String tag) {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        if (nav == null) return;
        int want = idForTag(tag);
        if (nav.getSelectedItemId() == want) return;
        nav.setOnItemSelectedListener(null);
        nav.setSelectedItemId(want);
        nav.setOnItemSelectedListener(navListener);
        MotionHelper.bounceNavItem(nav, want);
    }

    public HomeFragment homeFragment() {
        Fragment f = findFrag(TAG_HOME);
        return f instanceof HomeFragment ? (HomeFragment) f : null;
    }

    private Fragment findFrag(String tag) {
        return getSupportFragmentManager().findFragmentByTag(tag);
    }

    private Fragment createFragment(String tag) {
        if (TAG_HOME.equals(tag)) return new HomeFragment();
        if (TAG_EXPLORE.equals(tag)) return new ExploreFragment();
        if (TAG_GITHUB.equals(tag)) return new GitHubExploreFragment();
        if (TAG_DOWNLOADS.equals(tag)) return new DownloadsFragment();
        if (TAG_SETTINGS.equals(tag)) return new SettingsFragment();
        return null;
    }

    private int tabOrder(String tag) {
        if (TAG_HOME.equals(tag)) return 0;
        if (TAG_EXPLORE.equals(tag)) return 1;
        if (TAG_GITHUB.equals(tag)) return 2;
        if (TAG_DOWNLOADS.equals(tag)) return 3;
        if (TAG_SETTINGS.equals(tag)) return 4;
        return 0;
    }

    private int idForTag(String tag) {
        if (TAG_EXPLORE.equals(tag) || TAG_SEARCH.equals(tag)) return R.id.nav_explore;
        if (TAG_GITHUB.equals(tag)) return R.id.nav_github;
        if (TAG_DOWNLOADS.equals(tag)) return R.id.nav_downloads;
        return R.id.nav_home;
    }
}
