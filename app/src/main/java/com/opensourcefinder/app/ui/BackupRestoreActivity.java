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

import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.scanner.AutoBackupManager;
import com.opensourcefinder.app.scanner.BackupRestore;
import com.opensourcefinder.app.scanner.MatchStore;
import com.opensourcefinder.app.scanner.RepositoryManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackupRestoreActivity extends BaseActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;
    private ActivityResultLauncher<Uri> folderLauncher;

    private TextView currentDataText;
    private TextView maxKeptValue;
    private MaterialSwitch includeSettingsSwitch;
    private MaterialSwitch includeAppsSwitch;
    private MaterialButton undoRestoreButton;
    private TextView lastManualBackupText;
    private TextView frequencyValue;
    private TextView folderValue;
    private TextView lastAutoBackupText;
    private MaterialSwitch autoBackupSwitch;
    private View autoBackupDetails;
    private LinearProgressIndicator manualProgress;

    private boolean folderPickIsForEnabling = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        MotionHelper.applyDistinctActivityTransition(this);
        setContentView(R.layout.activity_backup_restore);
        MotionHelper.animateActivityContent(this);
        ThemeManager.applyWindow(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        ImageView manualIcon = findViewById(R.id.manualIcon);
        manualIcon.setImageResource(TablerIconRes.get(this, "ic_download"));
        ImageView autoIcon = findViewById(R.id.autoIcon);
        autoIcon.setImageResource(TablerIconRes.get(this, "ic_clock"));
        ImageView frequencyChevron = findViewById(R.id.frequencyChevron);
        frequencyChevron.setImageResource(TablerIconRes.get(this, "ic_chevron_right"));
        ImageView folderChevron = findViewById(R.id.folderChevron);
        folderChevron.setImageResource(TablerIconRes.get(this, "ic_chevron_right"));
        ImageView maxKeptChevron = findViewById(R.id.maxKeptChevron);
        maxKeptChevron.setImageResource(TablerIconRes.get(this, "ic_chevron_right"));

        currentDataText = findViewById(R.id.currentDataText);
        maxKeptValue = findViewById(R.id.maxKeptValue);
        includeSettingsSwitch = findViewById(R.id.includeSettingsSwitch);
        includeAppsSwitch = findViewById(R.id.includeAppsSwitch);
        undoRestoreButton = findViewById(R.id.undoRestoreButton);
        lastManualBackupText = findViewById(R.id.lastManualBackupText);
        frequencyValue = findViewById(R.id.frequencyValue);
        folderValue = findViewById(R.id.folderValue);
        lastAutoBackupText = findViewById(R.id.lastAutoBackupText);
        autoBackupSwitch = findViewById(R.id.autoBackupSwitch);
        autoBackupDetails = findViewById(R.id.autoBackupDetails);
        manualProgress = findViewById(R.id.manualProgress);

        MaterialButton backupNowButton = findViewById(R.id.backupNowButton);
        MaterialButton restoreButton = findViewById(R.id.restoreButton);
        View rowFrequency = findViewById(R.id.rowFrequency);
        View rowFolder = findViewById(R.id.rowFolder);
        View rowMaxKept = findViewById(R.id.rowMaxKept);
        MaterialButton backupToFolderButton = findViewById(R.id.backupToFolderButton);
        MaterialButton restoreFromFolderButton = findViewById(R.id.restoreFromFolderButton);

        registerLaunchers();

        backupNowButton.setOnClickListener(v -> exportLauncher.launch(AutoBackupManager.newFileName()));
        restoreButton.setOnClickListener(v -> importLauncher.launch(new String[]{"application/json", "*/*"}));
        undoRestoreButton.setOnClickListener(v -> confirmUndoRestore());
        backupToFolderButton.setOnClickListener(v -> backupToFolderNow());
        restoreFromFolderButton.setOnClickListener(v -> showFolderBackups());
        rowMaxKept.setOnClickListener(v -> showMaxKeptDialog());
        includeSettingsSwitch.setOnCheckedChangeListener((button, checked) -> AutoBackupManager.setIncludeSettings(this, checked));
        includeAppsSwitch.setOnCheckedChangeListener((button, checked) -> AutoBackupManager.setIncludeApps(this, checked));
        rowFrequency.setOnClickListener(v -> showFrequencyDialog());
        rowFolder.setOnClickListener(v -> {
            folderPickIsForEnabling = false;
            folderLauncher.launch(null);
        });
        autoBackupSwitch.setOnCheckedChangeListener((button, checked) -> onAutoBackupToggled(checked));

        refreshState();
    }

    private void registerLaunchers() {
        exportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
            if (uri == null) return;
            setManualBusy(true);
            executor.execute(() -> {
                try {
                    BackupRestore.exportToUri(this, uri);
                    AutoBackupManager.recordManualBackup(this);
                    runOnUiThread(() -> {
                        setManualBusy(false);
                        Toast.makeText(this, R.string.backup_saved, Toast.LENGTH_SHORT).show();
                        refreshManualBackupText();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        setManualBusy(false);
                        Toast.makeText(this, getString(R.string.backup_error, e.getMessage()), Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            previewAndRestore(uri);
        });

        folderLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
            if (uri == null) {
                if (folderPickIsForEnabling) autoBackupSwitch.setChecked(false);
                folderPickIsForEnabling = false;
                return;
            }
            AutoBackupManager.setFolderUri(this, uri);
            if (folderPickIsForEnabling) {
                AutoBackupManager.setEnabled(this, true);
                Toast.makeText(this, R.string.auto_backup_enabled_toast, Toast.LENGTH_SHORT).show();
            }
            folderPickIsForEnabling = false;
            refreshState();
        });
    }

    private void onAutoBackupToggled(boolean checked) {
        if (checked) {
            if (AutoBackupManager.getFolderUri(this) == null) {
                folderPickIsForEnabling = true;
                Toast.makeText(this, R.string.auto_backup_choose_folder_first, Toast.LENGTH_SHORT).show();
                folderLauncher.launch(null);
                return;
            }
            if (!AutoBackupManager.isEnabled(this)) {
                AutoBackupManager.setEnabled(this, true);
                Toast.makeText(this, R.string.auto_backup_enabled_toast, Toast.LENGTH_SHORT).show();
            }
        } else {
            if (AutoBackupManager.isEnabled(this)) {
                AutoBackupManager.setEnabled(this, false);
                Toast.makeText(this, R.string.auto_backup_disabled_toast, Toast.LENGTH_SHORT).show();
            }
        }
        autoBackupDetails.setVisibility(checked ? View.VISIBLE : View.GONE);
    }

    private void showFrequencyDialog() {
        int[] options = AutoBackupManager.INTERVAL_OPTIONS_DAYS;
        String[] labels = new String[options.length];
        int checked = 0;
        int current = AutoBackupManager.getIntervalDays(this);
        for (int i = 0; i < options.length; i++) {
            labels[i] = frequencyLabel(options[i]);
            if (options[i] == current) checked = i;
        }
        int initialChecked = checked;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.auto_backup_frequency)
                .setSingleChoiceItems(labels, initialChecked, (dialog, which) -> {
                    AutoBackupManager.setIntervalDays(this, options[which]);
                    frequencyValue.setText(labels[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void previewAndRestore(Uri uri) {
        setManualBusy(true);
        executor.execute(() -> {
            try {
                BackupRestore.BackupPayload payload = BackupRestore.readPayload(this, uri);
                runOnUiThread(() -> {
                    setManualBusy(false);
                    showRestorePreview(payload);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setManualBusy(false);
                    Toast.makeText(this, R.string.backup_invalid_file, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showRestorePreview(BackupRestore.BackupPayload payload) {
        int apps = BackupRestore.countApps(payload);
        int repos = payload.repositories != null ? payload.repositories.size() : 0;
        String settings = getString(BackupRestore.hasSettings(payload)
                ? R.string.restore_preview_settings_yes : R.string.restore_preview_settings_no);
        String message = getString(R.string.restore_preview_message, formatDate(payload.exportedAt), apps, repos, settings);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.restore)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.restore, (d, w) -> applyRestore(payload))
                .show();
    }

    private void applyRestore(BackupRestore.BackupPayload payload) {
        setManualBusy(true);
        executor.execute(() -> {
            try {
                BackupRestore.applyPayload(this, payload);
                int n = BackupRestore.countApps(payload);
                int missing = 0;
                for (com.opensourcefinder.app.model.MatchedApp m : MatchStore.get(this)) {
                    if (m != null && m.isFromBackup() && com.opensourcefinder.app.install.InstallState.read(this, m.getPackageName()) == null) missing++;
                }
                final int pending = missing;
                runOnUiThread(() -> {
                    setManualBusy(false);
                    Toast.makeText(this, getString(R.string.restore_done, n), Toast.LENGTH_SHORT).show();
                    refreshState();
                    if (pending > 0) showInstallAllPrompt(pending);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setManualBusy(false);
                    Toast.makeText(this, getString(R.string.restore_error, e.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showInstallAllPrompt(int count) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.restore_install_all_title)
                .setMessage(getString(R.string.restore_install_all_message, count))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.restore_install_all_confirm, (d, w) -> {
                    android.content.Intent intent = new android.content.Intent(this, com.opensourcefinder.app.MainActivity.class);
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    intent.putExtra("open_tab", com.opensourcefinder.app.MainActivity.TAG_HOME);
                    startActivity(intent);
                    finish();
                })
                .show();
    }

    private void confirmUndoRestore() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.restore_undo)
                .setMessage(R.string.restore_undo_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.restore, (d, w) -> {
                    setManualBusy(true);
                    executor.execute(() -> {
                        try {
                            BackupRestore.undoLastRestore(this);
                            runOnUiThread(() -> {
                                setManualBusy(false);
                                Toast.makeText(this, R.string.restore_undo_done, Toast.LENGTH_SHORT).show();
                                refreshState();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                setManualBusy(false);
                                Toast.makeText(this, getString(R.string.restore_error, e.getMessage()), Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                })
                .show();
    }

    private void backupToFolderNow() {
        if (AutoBackupManager.getFolderUri(this) == null) {
            Toast.makeText(this, R.string.auto_backup_choose_folder_first, Toast.LENGTH_SHORT).show();
            return;
        }
        setManualBusy(true);
        executor.execute(() -> {
            try {
                AutoBackupManager.performBackupNow(this);
                runOnUiThread(() -> {
                    setManualBusy(false);
                    Toast.makeText(this, R.string.auto_backup_folder_done_toast, Toast.LENGTH_SHORT).show();
                    refreshState();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setManualBusy(false);
                    Toast.makeText(this, getString(R.string.backup_error, e.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showFolderBackups() {
        if (AutoBackupManager.getFolderUri(this) == null) {
            Toast.makeText(this, R.string.auto_backup_choose_folder_first, Toast.LENGTH_SHORT).show();
            return;
        }
        setManualBusy(true);
        executor.execute(() -> {
            List<AutoBackupManager.BackupFile> files = AutoBackupManager.listBackups(this);
            runOnUiThread(() -> {
                setManualBusy(false);
                if (files.isEmpty()) {
                    Toast.makeText(this, R.string.auto_backup_folder_empty, Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] labels = new String[files.size()];
                for (int i = 0; i < files.size(); i++) {
                    AutoBackupManager.BackupFile file = files.get(i);
                    String date = file.modified > 0 ? formatDate(file.modified) : file.name;
                    labels[i] = getString(R.string.backup_file_label, date, Formatter.formatShortFileSize(this, file.size));
                }
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.backup_folder_list_title)
                        .setItems(labels, (dialog, which) -> showBackupFileActions(files.get(which), labels[which]))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            });
        });
    }

    private void showBackupFileActions(AutoBackupManager.BackupFile file, String label) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(label)
                .setItems(new String[]{getString(R.string.restore), getString(R.string.backup_action_delete)}, (dialog, which) -> {
                    if (which == 0) {
                        previewAndRestore(file.uri);
                    } else {
                        confirmDeleteBackup(file);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDeleteBackup(AutoBackupManager.BackupFile file) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.backup_action_delete)
                .setMessage(R.string.backup_delete_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.backup_action_delete, (d, w) -> executor.execute(() -> {
                    boolean deleted = AutoBackupManager.deleteBackup(this, file.uri);
                    runOnUiThread(() -> Toast.makeText(this,
                            deleted ? R.string.backup_deleted : R.string.backup_delete_failed, Toast.LENGTH_SHORT).show());
                }))
                .show();
    }

    private void showMaxKeptDialog() {
        int[] options = AutoBackupManager.MAX_KEPT_OPTIONS;
        String[] labels = new String[options.length];
        int checked = 0;
        int current = AutoBackupManager.getMaxKept(this);
        for (int i = 0; i < options.length; i++) {
            labels[i] = getString(R.string.auto_backup_max_kept_value, options[i]);
            if (options[i] == current) checked = i;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.auto_backup_max_kept)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    AutoBackupManager.setMaxKept(this, options[which]);
                    maxKeptValue.setText(labels[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String formatDate(long millis) {
        return DateUtils.formatDateTime(this, millis,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_YEAR);
    }

    private String frequencyLabel(int days) {
        switch (days) {
            case 1: return getString(R.string.auto_backup_frequency_1);
            case 2: return getString(R.string.auto_backup_frequency_2);
            case 3: return getString(R.string.auto_backup_frequency_3);
            case 7: return getString(R.string.auto_backup_frequency_7);
            case 14: return getString(R.string.auto_backup_frequency_14);
            case 30: return getString(R.string.auto_backup_frequency_30);
            default: return getString(R.string.auto_backup_frequency_7);
        }
    }

    private void setManualBusy(boolean busy) {
        manualProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void refreshState() {
        refreshManualBackupText();
        refreshCurrentData();

        includeSettingsSwitch.setOnCheckedChangeListener(null);
        includeSettingsSwitch.setChecked(AutoBackupManager.isIncludeSettings(this));
        includeSettingsSwitch.setOnCheckedChangeListener((button, checked) -> AutoBackupManager.setIncludeSettings(this, checked));
        includeAppsSwitch.setOnCheckedChangeListener(null);
        includeAppsSwitch.setChecked(AutoBackupManager.isIncludeApps(this));
        includeAppsSwitch.setOnCheckedChangeListener((button, checked) -> AutoBackupManager.setIncludeApps(this, checked));
        undoRestoreButton.setVisibility(BackupRestore.hasUndoSnapshot(this) ? View.VISIBLE : View.GONE);
        maxKeptValue.setText(getString(R.string.auto_backup_max_kept_value, AutoBackupManager.getMaxKept(this)));

        boolean enabled = AutoBackupManager.isEnabled(this);
        autoBackupSwitch.setOnCheckedChangeListener(null);
        autoBackupSwitch.setChecked(enabled);
        autoBackupSwitch.setOnCheckedChangeListener((button, checked) -> onAutoBackupToggled(checked));
        autoBackupDetails.setVisibility(enabled ? View.VISIBLE : View.GONE);

        frequencyValue.setText(frequencyLabel(AutoBackupManager.getIntervalDays(this)));

        Uri folder = AutoBackupManager.getFolderUri(this);
        folderValue.setText(folder != null ? folderDisplayName(folder) : getString(R.string.auto_backup_folder_not_set));

        long lastRun = AutoBackupManager.getLastRunMillis(this);
        lastAutoBackupText.setText(lastRun > 0 ? formatDate(lastRun) : getString(R.string.auto_backup_never));
    }

    private void refreshCurrentData() {
        executor.execute(() -> {
            int apps = MatchStore.get(this).size();
            int repos = RepositoryManager.get(this).size();
            runOnUiThread(() -> currentDataText.setText(getString(R.string.backup_current_data, apps, repos)));
        });
    }

    private void refreshManualBackupText() {
        long last = AutoBackupManager.getLastManualBackupMillis(this);
        lastManualBackupText.setText(last > 0
                ? getString(R.string.backup_last_manual, formatDate(last))
                : getString(R.string.backup_last_manual_never));
    }

    private String folderDisplayName(Uri treeUri) {
        try {
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            int lastColon = docId.lastIndexOf(':');
            String name = lastColon >= 0 && lastColon < docId.length() - 1 ? docId.substring(lastColon + 1) : docId;
            return name.isEmpty() ? treeUri.toString() : name;
        } catch (Exception e) {
            return treeUri.toString();
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
