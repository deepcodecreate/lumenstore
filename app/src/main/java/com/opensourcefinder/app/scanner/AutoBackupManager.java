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

package com.opensourcefinder.app.scanner;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.opensourcefinder.app.background.AutoBackupWorker;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class AutoBackupManager {
    private static final String PREFS = "auto_backup";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_INTERVAL_DAYS = "interval_days";
    private static final String KEY_FOLDER_URI = "folder_uri";
    private static final String KEY_LAST_RUN = "last_run";
    private static final String KEY_LAST_MANUAL_BACKUP = "last_manual_backup";
    private static final String KEY_MAX_KEPT = "max_kept";
    private static final String KEY_INCLUDE_SETTINGS = "include_settings";
    private static final String KEY_INCLUDE_APPS = "include_apps";
    private static final String WORK_NAME = "lumenstore_auto_backup";
    private static final String LEGACY_WORK_NAME = "opensourcefinder_auto_backup";
    private static final String FILE_PREFIX = "lumenstore-backup-";
    private static final String LEGACY_FILE_PREFIX = "opensourcefinder-backup-";
    private static final int DEFAULT_INTERVAL_DAYS = 7;
    private static final int DEFAULT_MAX_KEPT = 10;

    public static final int[] INTERVAL_OPTIONS_DAYS = {1, 2, 3, 7, 14, 30};
    public static final int[] MAX_KEPT_OPTIONS = {3, 5, 10, 20, 50};

    public static final class BackupFile {
        public final Uri uri;
        public final String name;
        public final long modified;
        public final long size;

        BackupFile(Uri uri, String name, long modified, long size) {
            this.uri = uri;
            this.name = name;
            this.modified = modified;
            this.size = size;
        }
    }

    private AutoBackupManager() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static int getIntervalDays(Context context) {
        return prefs(context).getInt(KEY_INTERVAL_DAYS, DEFAULT_INTERVAL_DAYS);
    }

    public static void setIntervalDays(Context context, int days) {
        prefs(context).edit().putInt(KEY_INTERVAL_DAYS, days).apply();
        if (isEnabled(context)) schedule(context);
    }

    public static int getMaxKept(Context context) {
        return prefs(context).getInt(KEY_MAX_KEPT, DEFAULT_MAX_KEPT);
    }

    public static void setMaxKept(Context context, int count) {
        prefs(context).edit().putInt(KEY_MAX_KEPT, count).apply();
    }

    public static boolean isIncludeApps(Context context) {
        return prefs(context).getBoolean(KEY_INCLUDE_APPS, true);
    }

    public static void setIncludeApps(Context context, boolean include) {
        prefs(context).edit().putBoolean(KEY_INCLUDE_APPS, include).apply();
    }

    public static boolean isIncludeSettings(Context context) {
        return prefs(context).getBoolean(KEY_INCLUDE_SETTINGS, true);
    }

    public static void setIncludeSettings(Context context, boolean include) {
        prefs(context).edit().putBoolean(KEY_INCLUDE_SETTINGS, include).apply();
    }

    public static String newFileName() {
        return FILE_PREFIX + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new java.util.Date()) + ".json";
    }

    public static Uri getFolderUri(Context context) {
        String raw = prefs(context).getString(KEY_FOLDER_URI, null);
        return raw == null ? null : Uri.parse(raw);
    }

    public static void setFolderUri(Context context, Uri treeUri) {
        Context app = context.getApplicationContext();
        try {
            app.getContentResolver().takePersistableUriPermission(treeUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {}
        prefs(app).edit().putString(KEY_FOLDER_URI, treeUri.toString()).apply();
    }

    public static long getLastRunMillis(Context context) {
        return prefs(context).getLong(KEY_LAST_RUN, 0L);
    }

    private static void setLastRunMillis(Context context, long millis) {
        prefs(context).edit().putLong(KEY_LAST_RUN, millis).apply();
    }

    public static long getLastManualBackupMillis(Context context) {
        return prefs(context).getLong(KEY_LAST_MANUAL_BACKUP, 0L);
    }

    public static void recordManualBackup(Context context) {
        prefs(context).edit().putLong(KEY_LAST_MANUAL_BACKUP, System.currentTimeMillis()).apply();
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        if (enabled) schedule(context); else cancel(context);
    }

    public static void rescheduleIfEnabled(Context context) {
        if (isEnabled(context) && getFolderUri(context) != null) schedule(context);
    }

    private static void schedule(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_WORK_NAME);
        int days = getIntervalDays(context);
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                AutoBackupWorker.class, days, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setBackoffCriteria(androidx.work.BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    private static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_WORK_NAME);
    }

    public static void performBackupNow(Context context) throws Exception {
        Uri treeUri = getFolderUri(context);
        if (treeUri == null) throw new IllegalStateException("No backup folder configured");

        Uri folderDocUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri));

        Uri newFileUri = DocumentsContract.createDocument(
                context.getContentResolver(), folderDocUri, "application/json", newFileName());
        if (newFileUri == null) throw new IllegalStateException("Could not create backup file");

        BackupRestore.exportToUri(context, newFileUri, isIncludeSettings(context));
        setLastRunMillis(context, System.currentTimeMillis());
        pruneOldBackups(context);
    }

    private static boolean isBackupName(String name) {
        return name != null && (name.startsWith(FILE_PREFIX) || name.startsWith(LEGACY_FILE_PREFIX));
    }

    public static List<BackupFile> listBackups(Context context) {
        List<BackupFile> result = new ArrayList<>();
        Uri treeUri = getFolderUri(context);
        if (treeUri == null) return result;
        try {
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri, DocumentsContract.getTreeDocumentId(treeUri));
            try (android.database.Cursor cursor = context.getContentResolver().query(
                    childrenUri,
                    new String[]{
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                            DocumentsContract.Document.COLUMN_SIZE
                    }, null, null, null)) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String name = cursor.getString(1);
                        if (!isBackupName(name)) continue;
                        Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0));
                        result.add(new BackupFile(docUri, name, cursor.getLong(2), cursor.getLong(3)));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        result.sort((a, b) -> {
            int byDate = Long.compare(b.modified, a.modified);
            return byDate != 0 ? byDate : b.name.compareTo(a.name);
        });
        return result;
    }

    public static boolean deleteBackup(Context context, Uri uri) {
        try {
            return DocumentsContract.deleteDocument(context.getContentResolver(), uri);
        } catch (Exception e) {
            return false;
        }
    }

    private static void pruneOldBackups(Context context) {
        List<BackupFile> backups = listBackups(context);
        int keep = getMaxKept(context);
        for (int i = keep; i < backups.size(); i++) {
            deleteBackup(context, backups.get(i).uri);
        }
    }
}
