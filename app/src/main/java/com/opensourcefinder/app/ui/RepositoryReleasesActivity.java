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
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.opensourcefinder.app.MainActivity;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.model.GitHubRelease;
import com.opensourcefinder.app.model.InstalledAppInfo;
import com.opensourcefinder.app.model.SourceRepository;
import com.opensourcefinder.app.scanner.AppScanner;
import com.opensourcefinder.app.scanner.CustomRepositoryScanner;
import com.opensourcefinder.app.scanner.RepoReleasePolicy;
import com.opensourcefinder.app.scanner.RepositoryManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RepositoryReleasesActivity extends BaseActivity {
    public static final String EXTRA_URL = "url";

    private static final Pattern GITHUB = Pattern.compile("github\\.com/([^/]+)/([^/#?]+)", Pattern.CASE_INSENSITIVE);
    private static final int PAGE = 15;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<GitHubRelease> releases = new ArrayList<>();
    private final List<Chip> channelChips = new ArrayList<>();

    private SourceRepository source;
    private String owner;
    private String repo;
    private LinearLayout content;
    private LinearLayout releasesBox;
    private ChipGroup channelGroup;
    private TextInputEditText filterInput;
    private TextInputEditText minInput;
    private TextInputEditText assetInput;
    private TextInputEditText baselineInput;
    private MaterialButton linkButton;
    private String linkedPackage;
    private TextView releasesStatus;
    private int shown;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        MotionHelper.applyDistinctActivityTransition(this);
        setContentView(R.layout.activity_repo_releases);
        MotionHelper.animateActivityContent(this);
        ThemeManager.applyWindow(this);

        String url = getIntent().getStringExtra(EXTRA_URL);
        for (SourceRepository item : RepositoryManager.get(this)) {
            if (item.getUrl() != null && item.getUrl().equalsIgnoreCase(url)) { source = item; break; }
        }
        if (source == null) { finish(); return; }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(TablerIconRes.get(this, "ic_arrow_back"));
        toolbar.setNavigationIconTint(MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface));
        ToolbarCircleHelper.apply(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setTitle(displayName());

        content = findViewById(R.id.content);
        Matcher gh = GITHUB.matcher(source.getUrl());
        if (!gh.find()) {
            content.addView(infoCard(getString(R.string.repo_rel_github_only)));
            return;
        }
        owner = gh.group(1);
        repo = gh.group(2).replaceAll("/$", "").replaceAll("(?i)\\.git$", "");
        linkedPackage = source.getPackageName();

        content.addView(buildRulesCard());
        content.addView(sectionTitle(getString(R.string.repo_rel_releases)));
        releasesStatus = bodyText(getString(R.string.repo_rel_loading));
        content.addView(releasesStatus);
        releasesBox = new LinearLayout(this);
        releasesBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(releasesBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        loadReleases();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private String displayName() {
        if (source.getName() != null && !source.getName().trim().isEmpty()) return source.getName().trim();
        return source.getUrl();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int appearance(int attr) {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(attr, value, true);
        return value.resourceId;
    }

    private int color(View anchor, int attr) {
        return MaterialColors.getColor(anchor, attr);
    }

    private TextView bodyText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextAppearance(appearance(com.google.android.material.R.attr.textAppearanceBodyMedium));
        view.setTextColor(color(view, com.google.android.material.R.attr.colorOnSurfaceVariant));
        view.setPadding(dp(4), dp(4), dp(4), dp(4));
        return view;
    }

    private TextView sectionTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextAppearance(appearance(com.google.android.material.R.attr.textAppearanceTitleMedium));
        view.setTextColor(color(view, com.google.android.material.R.attr.colorOnSurface));
        view.setPadding(dp(8), dp(16), dp(8), dp(6));
        return view;
    }

    private MaterialCardView card(int bgAttr, int radiusDp) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(MaterialColors.getColor(card, bgAttr));
        card.setCardElevation(0f);
        card.setStrokeWidth(0);
        card.setRadius(dp(radiusDp));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);
        return card;
    }

    private View infoCard(String message) {
        MaterialCardView card = card(com.google.android.material.R.attr.colorSurfaceContainerHigh, 24);
        TextView text = bodyText(message);
        text.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.addView(text);
        return card;
    }

    private TextInputEditText[] field(LinearLayout parent, String hint, String value) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setHint(hint);
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setSingleLine(true);
        input.setText(value == null ? "" : value);
        layout.addView(input);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        parent.addView(layout, lp);
        return new TextInputEditText[]{input};
    }

    private View buildRulesCard() {
        MaterialCardView card = card(com.google.android.material.R.attr.colorSurfaceContainerHigh, 24);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.addView(column);

        TextView title = new TextView(this);
        title.setText(R.string.repo_rel_title);
        title.setTextAppearance(appearance(com.google.android.material.R.attr.textAppearanceTitleMedium));
        title.setTextColor(color(title, com.google.android.material.R.attr.colorOnSurface));
        column.addView(title);

        TextView channelLabel = bodyText(getString(R.string.repo_rel_channel));
        channelLabel.setPadding(0, dp(10), 0, dp(4));
        column.addView(channelLabel);

        channelGroup = new ChipGroup(this);
        channelGroup.setSingleSelection(true);
        channelGroup.setSelectionRequired(true);
        String[][] channels = {
                {RepoReleasePolicy.CHANNEL_AUTO, getString(R.string.repo_rel_channel_auto)},
                {RepoReleasePolicy.CHANNEL_STABLE, getString(R.string.repo_rel_channel_stable)},
                {RepoReleasePolicy.CHANNEL_PRE, getString(R.string.repo_rel_channel_pre)},
                {RepoReleasePolicy.CHANNEL_ANY, getString(R.string.repo_rel_channel_any)}};
        String current = source.getChannel() == null || source.getChannel().isEmpty()
                ? RepoReleasePolicy.CHANNEL_AUTO : source.getChannel();
        for (String[] item : channels) {
            Chip chip = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
            chip.setId(View.generateViewId());
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChipStrokeWidth(0f);
            chip.setText(item[1]);
            chip.setTag(item[0]);
            channelGroup.addView(chip);
            channelChips.add(chip);
            if (item[0].equals(current)) chip.setChecked(true);
        }
        if (channelGroup.getCheckedChipId() == View.NO_ID && !channelChips.isEmpty()) channelChips.get(0).setChecked(true);
        channelGroup.setOnCheckedStateChangeListener((group, ids) -> colorChannelChips());
        column.addView(channelGroup);
        colorChannelChips();

        filterInput = field(column, getString(R.string.repo_rel_filter_hint), source.getTagFilter())[0];
        minInput = field(column, getString(R.string.repo_rel_min_hint), source.getMinVersion())[0];
        assetInput = field(column, getString(R.string.repo_rel_asset_hint), source.getAssetPattern())[0];
        baselineInput = field(column, getString(R.string.repo_rel_baseline_hint), source.getBaselineTag())[0];

        linkButton = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        linkButton.setAllCaps(false);
        LinearLayout.LayoutParams linkLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        linkLp.topMargin = dp(12);
        column.addView(linkButton, linkLp);
        linkButton.setOnClickListener(v -> { AppPreferences.performHaptic(v); pickLinkedApp(); });
        updateLinkButton();

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        MaterialButton reset = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        reset.setAllCaps(false);
        reset.setText(R.string.repo_rel_reset);
        reset.setOnClickListener(v -> { AppPreferences.performHaptic(v); resetFields(); });
        MaterialButton save = new MaterialButton(this);
        save.setAllCaps(false);
        save.setText(R.string.repo_rel_save);
        save.setOnClickListener(v -> { AppPreferences.performHaptic(v); save(); });
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resetLp.setMarginEnd(dp(8));
        actions.addView(reset, resetLp);
        actions.addView(save);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(14);
        column.addView(actions, actionsLp);
        return card;
    }

    private void colorChannelChips() {
        int on = MaterialColors.getColor(channelGroup, com.google.android.material.R.attr.colorPrimaryContainer);
        int onText = MaterialColors.getColor(channelGroup, com.google.android.material.R.attr.colorOnPrimaryContainer);
        int off = MaterialColors.getColor(channelGroup, com.google.android.material.R.attr.colorSurfaceContainerHighest);
        int offText = MaterialColors.getColor(channelGroup, com.google.android.material.R.attr.colorOnSurface);
        for (Chip chip : channelChips) {
            boolean checked = chip.isChecked();
            chip.setChipBackgroundColor(ColorStateList.valueOf(checked ? on : off));
            chip.setTextColor(checked ? onText : offText);
        }
    }

    private String selectedChannel() {
        for (Chip chip : channelChips) {
            if (chip.isChecked()) return (String) chip.getTag();
        }
        return RepoReleasePolicy.CHANNEL_AUTO;
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private static String nullIfBlank(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private void updateLinkButton() {
        linkButton.setText(linkedPackage == null || linkedPackage.isEmpty()
                ? getString(R.string.repo_rel_link_auto)
                : getString(R.string.repo_rel_link_pkg, linkedPackage));
    }

    private void resetFields() {
        for (Chip chip : channelChips) {
            if (RepoReleasePolicy.CHANNEL_AUTO.equals(chip.getTag())) chip.setChecked(true);
        }
        colorChannelChips();
        filterInput.setText("");
        minInput.setText("");
        assetInput.setText("");
        baselineInput.setText("");
        linkedPackage = null;
        updateLinkButton();
    }

    private void pickLinkedApp() {
        final Context app = getApplicationContext();
        executor.execute(() -> {
            List<InstalledAppInfo> installed = new ArrayList<>(AppScanner.listUserInstalledApps(app));
            Collections.sort(installed, (a, b) -> a.getAppLabel().compareToIgnoreCase(b.getAppLabel()));
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                final List<String> packages = new ArrayList<>();
                final List<CharSequence> labels = new ArrayList<>();
                packages.add(null);
                labels.add(getString(R.string.repo_rel_link_clear));
                for (InstalledAppInfo info : installed) {
                    packages.add(info.getPackageName());
                    labels.add(info.getAppLabel() + "\n" + info.getPackageName());
                }
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.repo_rel_link_title)
                        .setItems(labels.toArray(new CharSequence[0]), (d, which) -> {
                            linkedPackage = packages.get(which);
                            updateLinkButton();
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            });
        });
    }

    private void save() {
        SourceRepository updated = new SourceRepository(source.getUrl(), source.getName(), source.getType());
        String channel = selectedChannel();
        updated.setChannel(RepoReleasePolicy.CHANNEL_AUTO.equals(channel) ? null : channel);
        updated.setTagFilter(nullIfBlank(textOf(filterInput)));
        updated.setMinVersion(nullIfBlank(textOf(minInput)));
        updated.setAssetPattern(nullIfBlank(textOf(assetInput)));
        updated.setBaselineTag(nullIfBlank(textOf(baselineInput)));
        updated.setPackageName(nullIfBlank(linkedPackage));
        RepositoryManager.update(getApplicationContext(), updated);
        source = updated;
        Snackbar.make(content, R.string.repo_rel_saved, Snackbar.LENGTH_LONG)
                .setAction(R.string.repo_rel_scan_now, v -> {
                    Intent home = new Intent(this, MainActivity.class);
                    home.putExtra("rescan", true);
                    home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(home);
                }).show();
    }

    private void loadReleases() {
        final Context app = getApplicationContext();
        executor.execute(() -> {
            List<GitHubRelease> list = new ArrayList<>();
            try {
                list.addAll(new CustomRepositoryScanner(app).listReleases(owner, repo));
            } catch (Exception ignored) {}
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                releases.clear();
                releases.addAll(list);
                shown = 0;
                releasesBox.removeAllViews();
                if (releases.isEmpty()) {
                    releasesStatus.setText(R.string.repo_rel_none);
                    return;
                }
                releasesStatus.setVisibility(View.GONE);
                showMore();
            });
        });
    }

    private void showMore() {
        View old = releasesBox.findViewWithTag("more");
        if (old != null) releasesBox.removeView(old);
        int end = Math.min(releases.size(), shown + PAGE);
        for (int i = shown; i < end; i++) releasesBox.addView(releaseCard(releases.get(i)));
        shown = end;
        if (shown < releases.size()) {
            MaterialButton more = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            more.setAllCaps(false);
            more.setText(R.string.repo_rel_show_more);
            more.setTag("more");
            more.setOnClickListener(v -> { AppPreferences.performHaptic(v); showMore(); });
            releasesBox.addView(more);
        }
    }

    private View releaseCard(final GitHubRelease release) {
        MaterialCardView card = card(com.google.android.material.R.attr.colorSurfaceContainer, 20);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16), dp(14), dp(16), dp(8));
        card.addView(column);

        final String tag = release.getTagName();
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView tagView = new TextView(this);
        tagView.setText(tag);
        tagView.setTextAppearance(appearance(com.google.android.material.R.attr.textAppearanceTitleSmall));
        tagView.setTextColor(color(tagView, com.google.android.material.R.attr.colorOnSurface));
        header.addView(tagView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (RepoReleasePolicy.isPre(release)) {
            TextView badge = new TextView(this);
            badge.setText(R.string.repo_rel_prerelease);
            badge.setTextAppearance(appearance(com.google.android.material.R.attr.textAppearanceLabelSmall));
            badge.setTextColor(color(badge, com.google.android.material.R.attr.colorOnTertiaryContainer));
            badge.setBackgroundColor(color(badge, com.google.android.material.R.attr.colorTertiaryContainer));
            badge.setPadding(dp(8), dp(2), dp(8), dp(2));
            header.addView(badge);
        }
        column.addView(header);

        String date = release.getPublishedAt();
        if (date != null && date.length() >= 10) {
            TextView meta = bodyText(date.substring(0, 10));
            meta.setPadding(0, dp(2), 0, dp(4));
            column.addView(meta);
        }

        int apkCount = 0;
        if (release.getAssets() != null) {
            for (final GitHubRelease.Asset asset : release.getAssets()) {
                final String name = asset.getName();
                if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
                apkCount++;
                TextView row = new TextView(this);
                row.setText(String.format(Locale.US, "%s  ·  %.1f MB", name, asset.getSize() / (1024f * 1024f)));
                row.setTextAppearance(appearance(com.google.android.material.R.attr.textAppearanceBodySmall));
                row.setTextColor(color(row, com.google.android.material.R.attr.colorPrimary));
                row.setPadding(dp(4), dp(8), dp(4), dp(8));
                TypedValue ripple = new TypedValue();
                getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
                row.setBackgroundResource(ripple.resourceId);
                row.setOnClickListener(v -> {
                    AppPreferences.performHaptic(v);
                    String pattern = RepoReleasePolicy.derivePattern(name, tag);
                    assetInput.setText(pattern);
                    Toast.makeText(this, getString(R.string.repo_rel_asset_set, pattern), Toast.LENGTH_SHORT).show();
                });
                column.addView(row);
            }
        }
        if (apkCount == 0) column.addView(bodyText(getString(R.string.repo_rel_no_apk)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        MaterialButton from = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        from.setAllCaps(false);
        from.setText(R.string.repo_rel_start_here);
        from.setOnClickListener(v -> {
            AppPreferences.performHaptic(v);
            minInput.setText(tag);
            Toast.makeText(this, getString(R.string.repo_rel_min_set, tag), Toast.LENGTH_SHORT).show();
        });
        MaterialButton mine = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        mine.setAllCaps(false);
        mine.setText(R.string.repo_rel_mine);
        mine.setOnClickListener(v -> {
            AppPreferences.performHaptic(v);
            baselineInput.setText(tag);
            Toast.makeText(this, getString(R.string.repo_rel_baseline_set, tag), Toast.LENGTH_SHORT).show();
        });
        actions.addView(from);
        actions.addView(mine);
        column.addView(actions);
        return card;
    }
}
