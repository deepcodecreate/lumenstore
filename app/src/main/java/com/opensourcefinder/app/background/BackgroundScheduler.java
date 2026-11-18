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

package com.opensourcefinder.app.background;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.opensourcefinder.app.ui.AppPreferences;

import java.util.concurrent.TimeUnit;

public final class BackgroundScheduler {
    public static final String CHANNEL_ID = "repository_scans";
    private static final String WORK_NAME = "opensourcefinder_periodic_scan";
    private static final String MANUAL_WORK_NAME = "opensourcefinder_manual_scan";

    private BackgroundScheduler() {}

    public static void initialize(Context context) {
        createChannel(context);
        scheduleInternal(context);
    }

    public static void refreshConstraints(Context context) {
        scheduleInternal(context);
    }

    private static void scheduleInternal(Context context) {
        Context app = context.getApplicationContext();
        WorkManager workManager = WorkManager.getInstance(app);
        if (!AppPreferences.isBackgroundSyncEnabled(app)) {

            workManager.cancelUniqueWork(WORK_NAME);
            workManager.cancelUniqueWork(MANUAL_WORK_NAME);
            cancelLeftoverNotification(app);
            return;
        }

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                BackgroundScanWorker.class, 6, TimeUnit.HOURS)
                .setConstraints(buildConstraints(app))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build();

        workManager.enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    private static Constraints buildConstraints(Context context) {
        boolean wifiOnly = AppPreferences.isWifiOnlyBackgroundSync(context);
        return new Constraints.Builder()
                .setRequiredNetworkType(wifiOnly ? NetworkType.UNMETERED : NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build();
    }

    public static void runNow(Context context) {
        Context app = context.getApplicationContext();
        if (!AppPreferences.isBackgroundSyncEnabled(app)) return;
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BackgroundScanWorker.class)
                .setConstraints(buildConstraints(app))
                .build();
        WorkManager.getInstance(app).enqueueUniqueWork(
                MANUAL_WORK_NAME, ExistingWorkPolicy.REPLACE, request);
    }

    public static boolean isNetworkAllowed(Context context) {
        Context app = context.getApplicationContext();
        if (!AppPreferences.isBackgroundSyncEnabled(app)) return false;
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false;
        if (!AppPreferences.isWifiOnlyBackgroundSync(app)) return true;

        boolean metered = cm.isActiveNetworkMetered()
                || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        boolean cellularOnly = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                && !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                && !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
        return !metered && !cellularOnly;
    }

    private static void cancelLeftoverNotification(Context context) {
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.cancel(BackgroundScanWorker.NOTIFICATION_ID);
        } catch (Exception ignored) {}
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Repository scans",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Background open-source repository checks");
            channel.setShowBadge(false);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
