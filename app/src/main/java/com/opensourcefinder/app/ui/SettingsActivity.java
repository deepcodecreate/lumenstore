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
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.opensourcefinder.app.MainActivity;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.model.SourceRepository;
import com.opensourcefinder.app.network.GitHubTokenProvider;
import com.opensourcefinder.app.scanner.RepositoryManager;
import java.util.List;

public class SettingsActivity extends BaseActivity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        MotionHelper.applyActivityTransition(this, true);
        setContentView(R.layout.activity_settings);
        MotionHelper.animateActivityContent(this);
        MotionHelper.keepBottomNavStatic(this);
        ThemeManager.applyWindow(this);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.settings_title);
        BottomNavHelper.bind(this, R.id.nav_settings);

        bindRow(R.id.rowRescan, com.opensourcefinder.app.ui.TablerIconRes.get(this, "ic_player_play"), getString(R.string.settings_rescan), v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra("open_tab", MainActivity.TAG_HOME);
            i.putExtra("force_rescan", true);
            startActivity(i);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });
        bindRow(R.id.rowRepos, com.opensourcefinder.app.ui.TablerIconRes.get(this, "ic_plus"), getString(R.string.settings_repositories), v -> startActivity(new android.content.Intent(this, ManageRepositoriesActivity.class)));
        bindRow(R.id.rowGithubSearch, com.opensourcefinder.app.ui.TablerIconRes.get(this, "ic_search"), getString(R.string.settings_github_search), v -> {
            Intent intent = new Intent(this, GitHubSearchActivity.class);
            intent.putExtra("open_github_search", true);
            startActivity(intent);
        });
        bindRow(R.id.rowTheme, com.opensourcefinder.app.ui.TablerIconRes.get(this, "ic_palette"), getString(R.string.settings_theme), v ->
                startActivity(new Intent(this, ThemeActivity.class)));
        setupLanguageRow();
        bindRow(R.id.rowBackup, com.opensourcefinder.app.ui.TablerIconRes.get(this, "ic_download"), getString(R.string.settings_backup_restore), v ->
                startActivity(new Intent(this, BackupRestoreActivity.class)));
    }

    @Override protected void onResume() {
        super.onResume();
        boolean hasToken = GitHubTokenProvider.hasToken();
        bindRow(R.id.rowGithub,
                com.opensourcefinder.app.ui.TablerIconRes.get(this, "ic_external_link"),
                hasToken ? getString(R.string.settings_github_sign_out) : getString(R.string.settings_github_sign_in),
                v -> {
                    if (GitHubTokenProvider.hasToken()) confirmLogout();
                    else startActivity(new Intent(this, LoginActivity.class));
                });
    }

    private void setupLanguageRow() {
        View row = findViewById(R.id.rowLanguage);
        ImageView icon = row.findViewById(R.id.rowIcon);
        TextView title = row.findViewById(R.id.rowTitle);
        ChipGroup group = row.findViewById(R.id.languageGroup);
        Chip chipEnglish = row.findViewById(R.id.chipLanguageEnglish);
        Chip chipPersian = row.findViewById(R.id.chipLanguagePersian);

        icon.setImageResource(com.opensourcefinder.app.ui.TablerIconRes.get(this, "ic_language"));
        title.setText(R.string.settings_language);

        String current = LanguageManager.get(this);
        if (LanguageManager.PERSIAN.equals(current)) chipPersian.setChecked(true);
        else chipEnglish.setChecked(true);

        group.setOnCheckedStateChangeListener((g, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            String selected = id == R.id.chipLanguagePersian ? LanguageManager.PERSIAN : LanguageManager.ENGLISH;
            if (selected.equals(LanguageManager.get(this))) return;
            AppPreferences.performHaptic(g);
            LanguageManager.set(this, selected);
        });
    }

    private void bindRow(int includeId, int icon, String title, View.OnClickListener click) {
        View row = findViewById(includeId);
        if (row == null) return;
        ImageView iv = row.findViewById(R.id.rowIcon);
        TextView tv = row.findViewById(R.id.rowTitle);
        if (iv != null) iv.setImageResource(icon);
        if (tv != null) tv.setText(title);
        row.setOnClickListener(click);
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.logout, (d, w) -> {
                    GitHubTokenProvider.clearToken(this);
                    Toast.makeText(this, R.string.github_signed_out, Toast.LENGTH_SHORT).show();
                    onResume();
                })
                .show();
    }
}
