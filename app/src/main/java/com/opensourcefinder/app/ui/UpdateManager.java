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
import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.install.InstallTracker;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UpdateManager {
    public interface OnDownloadCompleteListener {
        void onDownloadComplete(File apkFile);
        void onDownloadFailed();
        default void onProgress(int percent) {}
    }

    private UpdateManager() {}

    public static void downloadAndInstall(Context context, String url, String fileName, OnDownloadCompleteListener listener) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            File target = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), safeName(fileName));
            try {
                download(url, target, percent -> {
                    if (listener != null) context.getMainExecutor().execute(() -> listener.onProgress(percent));
                });
                if (listener != null) context.getMainExecutor().execute(() -> listener.onDownloadComplete(target));
            } catch (Exception e) {
                if (listener != null) context.getMainExecutor().execute(listener::onDownloadFailed);
            } finally {
                executor.shutdown();
            }
        });
    }

    private interface Progress { void update(int percent); }

    private static void download(String url, File target, Progress progress) throws Exception {
        HttpURLConnection c = com.opensourcefinder.app.network.HttpClients.open(new URL(url));
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "OpenSourceFinder/1.0");
        if (c.getResponseCode() < 200 || c.getResponseCode() >= 300) throw new IllegalStateException("HTTP " + c.getResponseCode());
        long total = c.getContentLengthLong();
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[32768];
            long done = 0;
            int read;
            int last = -1;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                done += read;
                if (total > 0) {
                    int p = (int) Math.min(100, done * 100 / total);
                    if (p != last) { last = p; progress.update(p); }
                }
            }
        } finally { c.disconnect(); }
        if (!target.exists() || target.length() == 0) throw new IllegalStateException("empty apk");
        progress.update(100);
    }

    private static String safeName(String name) {
        if (name == null || name.trim().isEmpty()) return "update.apk";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public static boolean signaturesMatch(Context context, File apkFile, String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo installed = pm.getPackageInfo(packageName,
                    Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES);
            PackageInfo archive = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(),
                    Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES);
            if (archive == null) return false;
            if (Build.VERSION.SDK_INT >= 28) {
                return installed.signingInfo != null && archive.signingInfo != null
                        && installed.signingInfo.getApkContentsSigners().length > 0
                        && archive.signingInfo.getApkContentsSigners().length > 0
                        && installed.signingInfo.getApkContentsSigners()[0].equals(archive.signingInfo.getApkContentsSigners()[0]);
            }
            Signature[] a = installed.signatures, b = archive.signatures;
            return a != null && b != null && a.length > 0 && b.length > 0 && a[0].equals(b[0]);
        } catch (Exception e) { return false; }
    }

    private static boolean canInstallPackages(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || context.getPackageManager().canRequestPackageInstalls();
    }

    private static void requestInstallPermission(Context context) {
        Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.getPackageName()));
        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(settingsIntent);
        Toast.makeText(context, R.string.enable_unknown_sources, Toast.LENGTH_LONG).show();
    }

    public static void installApk(Context context, String key, File apkFile, InstallTracker.Listener listener) {
        if (!canInstallPackages(context)) {
            requestInstallPermission(context);
            return;
        }
        InstallTracker.get().startInstall(context, key, apkFile, listener);
    }

    public static void installApk(Context context, File apkFile) {
        installApk(context, deriveKey(context, apkFile), apkFile, null);
    }

    private static String deriveKey(Context context, File apkFile) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
            if (pi != null && pi.packageName != null) return pi.packageName;
        } catch (Exception ignored) {}
        return apkFile.getAbsolutePath();
    }

    public static Intent uninstallIntent(String packageName) {
        Intent i = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.fromParts("package", packageName, null));
        i.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        return i;
    }

    public static void uninstall(Context context, String packageName, int requestCode) {
        if (packageName == null || packageName.trim().isEmpty()) {
            Toast.makeText(context, R.string.uninstall_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = uninstallIntent(packageName);
        try {
            if (context instanceof Activity) {
                ((Activity) context).startActivityForResult(i, requestCode);
            } else {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            }
        } catch (Exception e) {
            android.util.Log.e("UpdateManager", "ACTION_UNINSTALL_PACKAGE failed for " + packageName, e);
            try {
                Intent appInfo = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null));
                appInfo.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(appInfo);
                Toast.makeText(context, context.getString(R.string.uninstall_manual_hint)
                        + " (" + e.getClass().getSimpleName() + ")", Toast.LENGTH_LONG).show();
            } catch (Exception e2) {
                android.util.Log.e("UpdateManager", "App Info fallback also failed for " + packageName, e2);
                Toast.makeText(context, R.string.uninstall_failed
                        + " (" + e2.getClass().getSimpleName() + ")", Toast.LENGTH_LONG).show();
            }
        }
    }
}
