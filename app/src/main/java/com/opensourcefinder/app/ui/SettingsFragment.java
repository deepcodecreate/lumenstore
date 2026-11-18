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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;
import android.content.res.ColorStateList;
import androidx.fragment.app.Fragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.opensourcefinder.app.MainActivity;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.background.BackgroundScheduler;
import com.opensourcefinder.app.model.SourceRepository;
import com.opensourcefinder.app.model.GitHubUser;
import com.opensourcefinder.app.network.GitHubTokenProvider;
import com.opensourcefinder.app.network.RetrofitClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.opensourcefinder.app.scanner.RepositoryManager;
import java.util.List;

public class SettingsFragment extends Fragment {

    private static GitHubUser cachedUser;
    private boolean profileLoading;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.settings_title);
        toolbar.setNavigationIcon(TablerIconRes.get(requireContext(), "ic_arrow_back"));
        toolbar.setNavigationIconTint(com.google.android.material.color.MaterialColors.getColor(
                toolbar, com.google.android.material.R.attr.colorOnSurface));
        toolbar.setNavigationOnClickListener(v -> {
            if (getActivity() instanceof com.opensourcefinder.app.MainActivity) {
                ((com.opensourcefinder.app.MainActivity) getActivity()).closeSettings();
            }
        });
        setupSeedColorRow(view);
        setupLanguageRow(view);
        setupFontSizeRow(view);
        setupNavLabelsRow(view);

        setupDefaultTabRow(view);
        setupMotionStyleRow(view);
        setupHapticsRow(view);

        setupAutoCheckUpdatesRow(view);
        setupBackgroundSyncRow(view);
        setupWifiOnlySyncRow(view);
        setupFdroidIndexRow(view);

        setupSaveDownloadsRow(view);
        setupKeepApkRow(view);

        bindRow(R.id.rowRescan, TablerIconRes.get(requireContext(), "ic_refresh"), getString(R.string.settings_rescan), getString(R.string.settings_rescan_subtitle), v -> {
            MainActivity act = (MainActivity) requireActivity();
            act.showTab(MainActivity.TAG_HOME);
            HomeFragment home = act.homeFragment();
            if (home != null) home.requestFullRescan();
        });
        bindRow(R.id.rowRepos, TablerIconRes.get(requireContext(), "ic_database"), getString(R.string.settings_repositories), getString(R.string.settings_repositories_subtitle), v ->
                startActivity(new Intent(requireContext(), ManageRepositoriesActivity.class)));
        bindRow(R.id.rowBackup, TablerIconRes.get(requireContext(), "ic_download"), getString(R.string.settings_backup_restore), getString(R.string.settings_backup_restore_subtitle), v ->
                startActivity(new Intent(requireContext(), BackupRestoreActivity.class)));
        setupVersionRow(view);

        setupSections(view);
    }

    private static final boolean[] sectionExpanded = {true, true, true, true, true};

    private void setupSections(View view) {
        Context ctx = requireContext();
        int[] cards = {R.id.cardAppearance, R.id.cardBehavior, R.id.cardSync, R.id.cardDownloads, R.id.cardData};
        int[] headers = {R.id.headerAppearance, R.id.headerBehavior, R.id.headerSync, R.id.headerDownloads, R.id.headerData};
        int[] contents = {R.id.contentAppearance, R.id.contentBehavior, R.id.contentSync, R.id.contentDownloads, R.id.contentData};
        int[] icons = {
                R.drawable.ic_settings_appearance,
                R.drawable.ic_settings_behavior,
                TablerIconRes.get(ctx, "ic_refresh"),
                TablerIconRes.get(ctx, "ic_download"),
                TablerIconRes.get(ctx, "ic_database")};
        int[] titles = {
                R.string.settings_section_appearance,
                R.string.settings_section_behavior,
                R.string.settings_group_sync,
                R.string.settings_group_downloads,
                R.string.settings_section_data};
        int[] subtitles = {
                R.string.settings_section_appearance_subtitle,
                R.string.settings_section_behavior_subtitle,
                R.string.settings_group_sync_subtitle,
                R.string.settings_group_downloads_subtitle,
                R.string.settings_section_data_subtitle};
        ViewGroup root = (ViewGroup) view.findViewById(R.id.settingsSectionsContainer);
        for (int i = 0; i < cards.length; i++) {
            final int index = i;
            View header = view.findViewById(headers[i]);
            View content = view.findViewById(contents[i]);
            ((ImageView) header.findViewById(R.id.sectionIcon)).setImageResource(icons[i]);
            ((TextView) header.findViewById(R.id.sectionTitle)).setText(titles[i]);
            ((TextView) header.findViewById(R.id.sectionSubtitle)).setText(subtitles[i]);
            ImageView chevron = header.findViewById(R.id.sectionChevron);
            AccordionHelper.bind(header, root, content, chevron, new AccordionHelper.ExpandedState() {
                @Override public boolean isExpanded() { return sectionExpanded[index]; }
                @Override public void setExpanded(boolean expanded) {
                    sectionExpanded[index] = expanded;
                    AppPreferences.performHaptic(header);
                }
            });
            MotionHelper.animateEntrance(view.findViewById(cards[i]), i);
        }
    }

    private void setupVersionRow(View view) {
        View row = view.findViewById(R.id.rowVersion);
        if (row == null) return;
        ((ImageView) row.findViewById(R.id.rowIcon)).setImageResource(TablerIconRes.get(requireContext(), "ic_info_circle"));
        ((TextView) row.findViewById(R.id.rowTitle)).setText(R.string.settings_version);
        String version = "";
        try {
            version = requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        ((TextView) row.findViewById(R.id.rowValue)).setText(version == null ? "" : version);
    }

    private void bindGithubRows() {
        View root = getView();
        if (root == null || !isAdded()) return;
        View account = root.findViewById(R.id.rowGithub);
        if (account == null) return;
        Context ctx = requireContext();
        ImageView icon = account.findViewById(R.id.rowIcon);
        TextView title = account.findViewById(R.id.rowTitle);
        TextView subtitle = account.findViewById(R.id.rowSubtitle);
        icon.setImageResource(TablerIconRes.get(ctx, "ic_brand_github"));
        title.setText(R.string.settings_github_account);
        subtitle.setVisibility(View.VISIBLE);

        if (!GitHubTokenProvider.hasToken()) {
            cachedUser = null;
            subtitle.setText(R.string.settings_profile_signed_out);
            account.setOnClickListener(v -> {
                AppPreferences.performHaptic(v);
                startActivity(new Intent(ctx, LoginActivity.class));
            });
            return;
        }

        account.setOnClickListener(v -> { AppPreferences.performHaptic(v); openProfile(); });

        if (cachedUser == null) {
            subtitle.setText(R.string.settings_profile_loading);
            loadProfile();
            return;
        }
        String login = cachedUser.getLogin();
        subtitle.setText(login == null ? getString(R.string.settings_profile_signed_in_as) : "@" + login);
    }

    private void loadProfile() {
        if (profileLoading) return;
        profileLoading = true;
        RetrofitClient.getService().getAuthenticatedUser().enqueue(new Callback<GitHubUser>() {
            @Override public void onResponse(@NonNull Call<GitHubUser> call, @NonNull Response<GitHubUser> response) {
                profileLoading = false;
                if (response.isSuccessful() && response.body() != null) {
                    cachedUser = response.body();
                    bindGithubRows();
                } else if (isAdded() && getView() != null) {
                    TextView sub = getView().findViewById(R.id.rowGithub).findViewById(R.id.rowSubtitle);
                    sub.setText(R.string.settings_profile_signed_in_as);
                }
            }
            @Override public void onFailure(@NonNull Call<GitHubUser> call, @NonNull Throwable t) {
                profileLoading = false;
                if (isAdded() && getView() != null) {
                    TextView sub = getView().findViewById(R.id.rowGithub).findViewById(R.id.rowSubtitle);
                    sub.setText(R.string.settings_profile_signed_in_as);
                }
            }
        });
    }

    private void openProfile() {
        Intent i = new Intent(requireContext(), GitHubProfileActivity.class);
        if (cachedUser != null) i.putExtra(GitHubProfileActivity.EXTRA_CACHED_USER, cachedUser);
        startActivity(i);
    }

    @Override public void onResume() {
        super.onResume();
        bindGithubRows();
    }

    private void setupFontSizeRow(View root) {
        View row = root.findViewById(R.id.rowFontSize);
        ImageView icon = row.findViewById(R.id.rowIcon);
        TextView title = row.findViewById(R.id.rowTitle);
        TextView value = row.findViewById(R.id.rowValue);
        Slider slider = row.findViewById(R.id.rowSlider);

        icon.setImageResource(R.drawable.ic_settings_font_size);
        title.setText(R.string.settings_font_size);

        int level = AppPreferences.getFontScaleLevel(requireContext());
        slider.setValue(level);
        value.setText(fontLevelLabel(level));

        slider.addOnChangeListener((s, v, fromUser) -> {
            if (fromUser) value.setText(fontLevelLabel((int) v));
        });
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull Slider s) {}
            @Override public void onStopTrackingTouch(@NonNull Slider s) {
                int newLevel = (int) s.getValue();
                if (newLevel != AppPreferences.getFontScaleLevel(requireContext())) {
                    AppPreferences.setFontScaleLevel(requireContext(), newLevel);
                    AppPreferences.performHaptic(s);
                    requireActivity().recreate();
                }
            }
        });
    }

    private void setupSeedColorRow(View root) {
        int selectedLabel = R.string.theme_default_teal;
        String selectedId = ThemeManager.getThemeId(requireContext());
        for (ThemeManager.ThemeDef theme : ThemeManager.THEMES) {
            if (theme.id.equals(selectedId)) {
                selectedLabel = theme.titleRes;
                break;
            }
        }
        View row = root.findViewById(R.id.rowTheme);
        updateValueRow(row, R.string.settings_seed_color, getString(selectedLabel));
        ImageView icon = row.findViewById(R.id.rowIcon);
        icon.setImageResource(TablerIconRes.get(requireContext(), "ic_palette"));
        row.setOnClickListener(v -> {
            AppPreferences.performHaptic(v);
            startActivity(new Intent(requireContext(), ThemeActivity.class));
        });
    }

    private interface SliderChanged { void onChanged(int value); }

    private void setupAppearanceSlider(View root, int rowId, int iconRes, int titleRes,
                                       int initial, String[] labels, SliderChanged onChanged) {
        View row = root.findViewById(rowId);
        ImageView icon = row.findViewById(R.id.rowIcon);
        TextView title = row.findViewById(R.id.rowTitle);
        TextView value = row.findViewById(R.id.rowValue);
        Slider slider = row.findViewById(R.id.rowSlider);
        icon.setImageResource(iconRes);
        title.setText(titleRes);
        slider.setValueFrom(0f);
        slider.setValueTo(labels.length - 1f);
        slider.setStepSize(1f);
        slider.setValue(initial);
        value.setText(labels[initial]);
        slider.addOnChangeListener((s, v, fromUser) -> {
            if (fromUser) value.setText(labels[Math.round(v)]);
        });
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull Slider s) {}
            @Override public void onStopTrackingTouch(@NonNull Slider s) {
                int selected = Math.round(s.getValue());
                if (selected != initial) {
                    onChanged.onChanged(selected);
                    AppPreferences.performHaptic(s);
                }
            }
        });
    }

    private interface ChoiceSelected { void onSelected(String value); }

    private void showChoiceDialog(int titleRes, String[] labels, String[] values,
                                  String selectedValue, ChoiceSelected onSelected) {
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(selectedValue)) {
                checked = i;
                break;
            }
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleRes);
        if (titleRes == R.string.settings_palette_style) {
            builder.setMessage(R.string.settings_palette_style_info);
        }
        builder.setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    onSelected.onSelected(values[which]);
                    AppPreferences.performHaptic(requireView());
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateValueRow(View row, int titleRes, String value) {
        TextView title = row.findViewById(R.id.rowTitle);
        TextView subtitle = row.findViewById(R.id.rowSubtitle);
        title.setText(titleRes);
        if (subtitle != null) {
            subtitle.setText(value);
            subtitle.setVisibility(View.VISIBLE);
        }
        row.setOnClickListener(v -> AppPreferences.performHaptic(v));
    }

    private String fontLevelLabel(int level) {
        switch (level) {
            case 0: return getString(R.string.settings_font_size_small);
            case 2: return getString(R.string.settings_font_size_large);
            case 3: return getString(R.string.settings_font_size_extra_large);
            default: return getString(R.string.settings_font_size_default);
        }
    }

    private void setupLanguageRow(View root) {
        View row = root.findViewById(R.id.rowLanguage);
        ((ImageView) row.findViewById(R.id.rowIcon)).setImageResource(TablerIconRes.get(requireContext(), "ic_language"));
        ((TextView) row.findViewById(R.id.rowTitle)).setText(R.string.settings_language);
        final String[] values = {LanguageManager.ENGLISH, LanguageManager.PERSIAN};
        String current = LanguageManager.get(requireContext());
        int selected = LanguageManager.PERSIAN.equals(current) ? 1 : 0;
        SegmentedOptions.bind(row.findViewById(R.id.segmentContainer),
                new String[]{getString(R.string.language_english), getString(R.string.language_persian)},
                selected, index -> {
                    if (values[index].equals(LanguageManager.get(requireContext()))) return;
                    AppPreferences.performHaptic(row);
                    LanguageManager.set(requireContext(), values[index]);
                });
    }

    private void setupDefaultTabRow(View root) {
        View row = root.findViewById(R.id.rowDefaultTab);
        ((ImageView) row.findViewById(R.id.rowIcon)).setImageResource(R.drawable.ic_settings_start_tab);
        ((TextView) row.findViewById(R.id.rowTitle)).setText(R.string.settings_default_tab);
        final String[] tags = {AppPreferences.TAB_HOME, AppPreferences.TAB_EXPLORE, AppPreferences.TAB_GITHUB, AppPreferences.TAB_DOWNLOADS};
        String current = AppPreferences.getDefaultTab(requireContext());
        int selected = 0;
        if (AppPreferences.TAB_EXPLORE.equals(current) || AppPreferences.TAB_SEARCH.equals(current)) selected = 1;
        else if (AppPreferences.TAB_GITHUB.equals(current)) selected = 2;
        else if (AppPreferences.TAB_DOWNLOADS.equals(current)) selected = 3;
        SegmentedOptions.bind(row.findViewById(R.id.segmentContainer),
                new String[]{getString(R.string.nav_home), getString(R.string.explore_title), getString(R.string.nav_github), getString(R.string.downloads_title)},
                selected, index -> {
                    AppPreferences.setDefaultTab(requireContext(), tags[index]);
                    AppPreferences.performHaptic(row);
                });
    }

    private void setupAutoCheckUpdatesRow(View root) {
        View row = root.findViewById(R.id.rowAutoCheckUpdates);
        ImageView icon = row.findViewById(R.id.rowIcon);
        TextView title = row.findViewById(R.id.rowTitle);
        TextView subtitle = row.findViewById(R.id.rowSubtitle);
        MaterialSwitch toggle = row.findViewById(R.id.rowSwitch);

        icon.setImageResource(com.opensourcefinder.app.ui.TablerIconRes.get(requireContext(), "ic_refresh"));
        tintSwitchRowIcon(icon);
        title.setText(R.string.settings_auto_check_updates);
        subtitle.setText(R.string.settings_auto_check_updates_subtitle);
        toggle.setSaveEnabled(false);
        toggle.setOnCheckedChangeListener(null);
        toggle.setChecked(AppPreferences.isAutoCheckUpdatesOnLaunch(requireContext()));
        toggle.setOnCheckedChangeListener((button, checked) -> AppPreferences.setAutoCheckUpdatesOnLaunch(requireContext(), checked));

        row.setOnClickListener(v -> {
            boolean newValue = !toggle.isChecked();
            toggle.setChecked(newValue);
            AppPreferences.performHaptic(toggle);
        });
    }

    private void setupWifiOnlySyncRow(View root) {
        View row = root.findViewById(R.id.rowWifiOnlySync);
        ImageView icon = row.findViewById(R.id.rowIcon);
        TextView title = row.findViewById(R.id.rowTitle);
        TextView subtitle = row.findViewById(R.id.rowSubtitle);
        MaterialSwitch toggle = row.findViewById(R.id.rowSwitch);

        icon.setImageResource(com.opensourcefinder.app.ui.TablerIconRes.get(requireContext(), "ic_wifi"));
        tintSwitchRowIcon(icon);
        title.setText(R.string.settings_wifi_only_sync);
        subtitle.setText(R.string.settings_wifi_only_sync_subtitle);
        toggle.setSaveEnabled(false);
        toggle.setOnCheckedChangeListener(null);
        toggle.setChecked(AppPreferences.isWifiOnlyBackgroundSync(requireContext()));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.setWifiOnlyBackgroundSync(requireContext(), checked);
            BackgroundScheduler.refreshConstraints(requireContext());
        });

        row.setOnClickListener(v -> {
            if (!AppPreferences.isBackgroundSyncEnabled(requireContext())) return;
            toggle.setChecked(!toggle.isChecked());
            AppPreferences.performHaptic(toggle);
        });
        updateWifiRowEnabled(root);
    }

    private void updateWifiRowEnabled(View root) {
        View row = root.findViewById(R.id.rowWifiOnlySync);
        if (row == null) return;
        boolean enabled = AppPreferences.isBackgroundSyncEnabled(requireContext());
        row.setAlpha(enabled ? 1f : 0.45f);
        row.setEnabled(enabled);
        View toggle = row.findViewById(R.id.rowSwitch);
        if (toggle != null) toggle.setEnabled(enabled);
    }

    private interface SwitchChanged { void onChanged(boolean checked); }

    private void setupSwitchRow(View root, int rowId, int iconRes, int titleRes, int subtitleRes,
                                boolean initial, SwitchChanged onChanged) {
        View row = root.findViewById(rowId);
        if (row == null) return;
        ImageView icon = row.findViewById(R.id.rowIcon);
        TextView title = row.findViewById(R.id.rowTitle);
        TextView subtitle = row.findViewById(R.id.rowSubtitle);
        MaterialSwitch toggle = row.findViewById(R.id.rowSwitch);
        icon.setImageResource(iconRes);
        tintSwitchRowIcon(icon);
        title.setText(titleRes);
        subtitle.setText(subtitleRes);
        toggle.setSaveEnabled(false);
        toggle.setOnCheckedChangeListener(null);
        toggle.setChecked(initial);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (isAdded()) onChanged.onChanged(checked);
        });
        row.setOnClickListener(v -> {
            toggle.setChecked(!toggle.isChecked());
            AppPreferences.performHaptic(toggle);
        });
    }

    private void setupBackgroundSyncRow(View root) {
        setupSwitchRow(root, R.id.rowBackgroundSync,
                TablerIconRes.get(requireContext(), "ic_cloud"),
                R.string.settings_background_sync, R.string.settings_background_sync_subtitle,
                AppPreferences.isBackgroundSyncEnabled(requireContext()),
                checked -> {
                    AppPreferences.setBackgroundSyncEnabled(requireContext(), checked);
                    BackgroundScheduler.refreshConstraints(requireContext());
                    updateWifiRowEnabled(requireView());
                    Toast.makeText(requireContext(), checked ? R.string.background_sync_on
                            : R.string.background_sync_off, Toast.LENGTH_SHORT).show();
                });
    }

    private void setupFdroidIndexRow(View root) {
        setupSwitchRow(root, R.id.rowFdroidIndex,
                TablerIconRes.get(requireContext(), "ic_building_store"),
                R.string.settings_fdroid_index, R.string.settings_fdroid_index_subtitle,
                AppPreferences.isLazyIndexEnabled(requireContext()),
                checked -> {
                    final android.content.Context app = requireContext().getApplicationContext();
                    AppPreferences.setLazyIndexEnabled(app, checked);
                    com.opensourcefinder.app.scanner.FdroidIndexManager.clearExploreCatalogCache(app);
                    if (!checked) {
                        new Thread(() -> com.opensourcefinder.app.scanner.FdroidIndexManager.deleteLocalIndexes(app),
                                "fdroid-index-cleanup").start();
                    }
                    Toast.makeText(requireContext(), checked ? R.string.fdroid_index_enabled_toast
                            : R.string.fdroid_index_disabled_toast, Toast.LENGTH_SHORT).show();
                });
    }

    private void setupSaveDownloadsRow(View root) {
        setupSwitchRow(root, R.id.rowSaveDownloads,
                TablerIconRes.get(requireContext(), "ic_history"),
                R.string.settings_save_downloads, R.string.settings_save_downloads_subtitle,
                AppPreferences.isSaveDownloads(requireContext()),
                checked -> {
                    AppPreferences.setSaveDownloads(requireContext(), checked);
                    com.opensourcefinder.app.download.DownloadTracker.get().onSavePreferenceChanged(requireContext());
                });
    }

    private void setupKeepApkRow(View root) {
        setupSwitchRow(root, R.id.rowKeepApk,
                TablerIconRes.get(requireContext(), "ic_package"),
                R.string.settings_keep_apk, R.string.settings_keep_apk_subtitle,
                AppPreferences.isKeepApk(requireContext()),
                checked -> AppPreferences.setKeepApk(requireContext(), checked));
    }

    private void setupMotionStyleRow(View root) {
        View row = root.findViewById(R.id.rowMotionStyle);
        ((ImageView) row.findViewById(R.id.rowIcon)).setImageResource(R.drawable.ic_settings_motion);
        ((TextView) row.findViewById(R.id.rowTitle)).setText(R.string.settings_motion_style);
        final String[] values = {AppPreferences.MOTION_EXPRESSIVE, AppPreferences.MOTION_STANDARD, AppPreferences.MOTION_REDUCED};
        String current = AppPreferences.getMotionStyle(requireContext());
        int selected = AppPreferences.MOTION_STANDARD.equals(current) ? 1 : AppPreferences.MOTION_REDUCED.equals(current) ? 2 : 0;
        SegmentedOptions.bind(row.findViewById(R.id.segmentContainer),
                new String[]{getString(R.string.settings_motion_expressive), getString(R.string.settings_motion_standard), getString(R.string.settings_motion_reduced)},
                selected, index -> {
                    if (values[index].equals(AppPreferences.getMotionStyle(requireContext()))) return;
                    AppPreferences.performHaptic(row);
                    AppPreferences.setMotionStyle(requireContext(), values[index]);
                    requireActivity().recreate();
                });
    }

    private void setupHapticsRow(View root) {
        View row = root.findViewById(R.id.rowHaptics);
        ImageView icon = row.findViewById(R.id.rowIcon);
        TextView title = row.findViewById(R.id.rowTitle);
        TextView subtitle = row.findViewById(R.id.rowSubtitle);
        MaterialSwitch toggle = row.findViewById(R.id.rowSwitch);

        icon.setImageResource(R.drawable.ic_settings_haptics);
        tintSwitchRowIcon(icon);
        title.setText(R.string.settings_haptic_feedback);
        subtitle.setText(R.string.settings_haptic_feedback_subtitle);
        toggle.setSaveEnabled(false);
        toggle.setOnCheckedChangeListener(null);
        toggle.setChecked(AppPreferences.isHapticFeedback(requireContext()));
        toggle.setOnCheckedChangeListener((button, checked) -> AppPreferences.setHapticFeedback(requireContext(), checked));

        row.setOnClickListener(v -> {
            boolean newValue = !toggle.isChecked();
            toggle.setChecked(newValue);
            if (newValue) AppPreferences.performHaptic(toggle);
        });
    }

    private void setupNavLabelsRow(View root) {
        View row = root.findViewById(R.id.rowNavLabels);
        if (row == null) return;
        ImageView icon = row.findViewById(R.id.rowIcon);
        TextView title = row.findViewById(R.id.rowTitle);
        TextView subtitle = row.findViewById(R.id.rowSubtitle);
        MaterialSwitch toggle = row.findViewById(R.id.rowSwitch);

        icon.setImageResource(R.drawable.ic_nav_bar);
        tintSwitchRowIcon(icon);
        title.setText(R.string.settings_nav_hide_labels);
        subtitle.setText(R.string.settings_nav_hide_labels_subtitle);
        toggle.setSaveEnabled(false);
        toggle.setOnCheckedChangeListener(null);
        toggle.setChecked(AppPreferences.isNavLabelsHidden(requireContext()));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.setNavLabelsHidden(requireContext(), checked);
            requireActivity().recreate();
        });

        row.setOnClickListener(v -> {
            boolean newValue = !toggle.isChecked();
            toggle.setChecked(newValue);
            if (newValue) AppPreferences.performHaptic(toggle);
        });
    }

    private void tintSwitchRowIcon(ImageView icon) {
        if (icon == null) return;
        int color = com.google.android.material.color.MaterialColors.getColor(icon, com.google.android.material.R.attr.colorOnPrimaryContainer, android.graphics.Color.TRANSPARENT);
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color));
    }

    private void bindRow(int includeId, int icon, String title, View.OnClickListener click) {
        bindRow(includeId, icon, title, null, click);
    }

    private void bindRow(int includeId, int icon, String title, @Nullable String subtitle, View.OnClickListener click) {
        View row = requireView().findViewById(includeId);
        if (row == null) return;
        ImageView iv = row.findViewById(R.id.rowIcon);
        TextView tv = row.findViewById(R.id.rowTitle);
        TextView sv = row.findViewById(R.id.rowSubtitle);
        ImageView chevron = row.findViewById(R.id.rowChevron);
        if (iv != null) iv.setImageResource(icon);
        if (tv != null) tv.setText(title);
        if (sv != null) {
            sv.setText(subtitle == null ? "" : subtitle);
            sv.setVisibility(subtitle == null || subtitle.isEmpty() ? View.GONE : View.VISIBLE);
        }
        row.setOnClickListener(v -> {
            AppPreferences.performHaptic(v);
            click.onClick(v);
        });
    }
}
