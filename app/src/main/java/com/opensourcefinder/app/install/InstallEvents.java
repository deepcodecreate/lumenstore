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

package com.opensourcefinder.app.install;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import com.opensourcefinder.app.download.DownloadTracker;
import com.opensourcefinder.app.model.MatchedApp;
import com.opensourcefinder.app.scanner.AppScanner;
import com.opensourcefinder.app.scanner.MatchStore;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class InstallEvents {
    private InstallEvents() {}

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static boolean registered;

    public static void addListener(Runnable r) { if (r != null) LISTENERS.addIfAbsent(r); }
    public static void removeListener(Runnable r) { if (r != null) LISTENERS.remove(r); }

    public static synchronized void register(Context context) {
        if (registered) return;
        registered = true;
        final Context app = context.getApplicationContext();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                String action = intent.getAction();
                if (intent.getData() == null) return;
                String pkg = intent.getData().getSchemeSpecificPart();

                if (Intent.ACTION_PACKAGE_REMOVED.equals(action)
                        && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
                if (Intent.ACTION_PACKAGE_ADDED.equals(action)
                        && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
                packageChanged(app, pkg);
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else app.registerReceiver(receiver, filter);
    }

    public static void packageChanged(Context context, String packageName) {
        final Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            try {
                AppScanner.invalidateCache();
                InstallTracker.get().reconcileNow(app);
                DownloadTracker.get().detectInstalledPackages(app);
                if (packageName != null) {
                    for (MatchedApp m : MatchStore.get(app)) {
                        if (packageName.equals(m.getPackageName())) {
                            InstallState.refresh(app, m);
                            MatchStore.upsert(app, m);
                        }
                    }
                }
            } catch (Exception ignored) {}
            MAIN.post(() -> { for (Runnable r : LISTENERS) { try { r.run(); } catch (Exception ignored) {} } });
        });
    }
}
