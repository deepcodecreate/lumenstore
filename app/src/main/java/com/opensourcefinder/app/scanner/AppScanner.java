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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.opensourcefinder.app.model.InstalledAppInfo;

import java.util.ArrayList;
import java.util.List;

public class AppScanner {

    private static final long CACHE_TTL_MILLIS = 5L * 60L * 1000L;
    private static final Object CACHE_LOCK = new Object();
    private static volatile List<InstalledAppInfo> cachedApps;
    private static volatile long cachedAt;
    private static volatile boolean receiverRegistered;

    public static List<InstalledAppInfo> listUserInstalledApps(Context context) {
        Context appContext = context.getApplicationContext();
        registerPackageChangeReceiver(appContext);
        synchronized (CACHE_LOCK) {
            long now = System.currentTimeMillis();
            if (cachedApps != null && now - cachedAt < CACHE_TTL_MILLIS) {
                return new ArrayList<>(cachedApps);
            }

            PackageManager packageManager = appContext.getPackageManager();
            List<PackageInfo> packages = packageManager.getInstalledPackages(0);
            List<InstalledAppInfo> result = new ArrayList<>();

            for (PackageInfo packageInfo : packages) {
                ApplicationInfo applicationInfo = packageInfo.applicationInfo;
                if (applicationInfo == null) continue;

                boolean isSystemApp = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean wasUpdated = (applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;

                if (isSystemApp && !wasUpdated) continue;

                String label = applicationInfo.loadLabel(packageManager).toString();
                String versionName = packageInfo.versionName != null ? packageInfo.versionName : "";
                long versionCode = getVersionCode(packageInfo);

                String installer = null;
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        installer = packageManager.getInstallSourceInfo(packageInfo.packageName).getInstallingPackageName();
                    } else {
                        installer = packageManager.getInstallerPackageName(packageInfo.packageName);
                    }
                } catch (Exception ignored) {}

                result.add(new InstalledAppInfo(
                        packageInfo.packageName,
                        label,
                        versionName,
                        versionCode,
                        applicationInfo.loadIcon(packageManager),
                        installer
                ));
            }
            cachedApps = result;
            cachedAt = System.currentTimeMillis();
            return new ArrayList<>(result);
        }
    }

    public static void invalidateCache() {
        synchronized (CACHE_LOCK) {
            cachedApps = null;
            cachedAt = 0L;
        }
    }

    private static void registerPackageChangeReceiver(Context context) {
        if (receiverRegistered) return;
        synchronized (CACHE_LOCK) {
            if (receiverRegistered) return;
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context receiverContext, Intent intent) {
                    synchronized (CACHE_LOCK) {
                        cachedApps = null;
                        cachedAt = 0L;
                    }
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
            filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
            filter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
            filter.addDataScheme("package");
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            receiverRegistered = true;
        }
    }

    private static long getVersionCode(PackageInfo packageInfo) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            return packageInfo.getLongVersionCode();
        }
        return packageInfo.versionCode;
    }
}
