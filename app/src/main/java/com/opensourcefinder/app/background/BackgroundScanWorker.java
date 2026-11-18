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

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.opensourcefinder.app.model.InstalledAppInfo;
import com.opensourcefinder.app.model.MatchedApp;
import com.opensourcefinder.app.model.SourceRepository;
import com.opensourcefinder.app.scanner.AppScanner;
import com.opensourcefinder.app.scanner.CustomRepositoryScanner;
import com.opensourcefinder.app.scanner.FdroidIndexManager;
import com.opensourcefinder.app.scanner.MatchStore;
import com.opensourcefinder.app.scanner.RepositoryManager;
import com.opensourcefinder.app.ui.AppPreferences;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BackgroundScanWorker extends Worker {
    public static final int NOTIFICATION_ID = 4107;
    private static final int MAX_ATTEMPTS = 3;

    public BackgroundScanWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    private boolean mustStop() {
        return isStopped() || !BackgroundScheduler.isNetworkAllowed(getApplicationContext());
    }

    @NonNull @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (!AppPreferences.isBackgroundSyncEnabled(context)) return Result.success();
        if (mustStop()) return Result.success();
        try {
            List<InstalledAppInfo> installed = AppScanner.listUserInstalledApps(context);
            Map<String, MatchedApp> finalMap = new HashMap<>();
            for (MatchedApp m : MatchStore.get(context)) {
                if (m != null && m.getPackageName() != null) finalMap.put(m.getStoreKey(), m);
            }

            if (FdroidIndexManager.isEnabled(context)) {
                try {
                    Map<String, MatchedApp> fdroid = new FdroidIndexManager(context)
                            .withStopCondition(this::mustStop)
                            .refreshAndMatchAllDefaults(installed, null);
                    for (Map.Entry<String, MatchedApp> e : fdroid.entrySet()) {
                        finalMap.put(e.getKey(), e.getValue());
                    }
                } catch (Exception ignored) {}
            }

            for (SourceRepository source : RepositoryManager.get(context)) {
                if (mustStop()) return Result.success();
                try {
                    List<MatchedApp> found = new CustomRepositoryScanner(context).scan(source, installed);
                    for (MatchedApp m : found) {
                        MatchStore.putManual(finalMap, m);
                    }
                } catch (Exception ignored) {}
            }
            if (isStopped()) return Result.success();

            Map<String, InstalledAppInfo> byPkg = new HashMap<>();
            for (InstalledAppInfo a : installed) byPkg.put(a.getPackageName(), a);
            List<MatchedApp> persisted = new ArrayList<>();
            for (MatchedApp m : finalMap.values()) {
                if (m == null || m.getPackageName() == null) continue;

                if (byPkg.containsKey(m.getPackageName()) || "manual".equals(m.getOrigin()) || m.isNotInstalled()) {
                    persisted.add(m);
                }
            }
            MatchStore.restoreInstalledTags(context, persisted);
            for (MatchedApp m : persisted) {
                if (m != null && m.getPackageName() != null && byPkg.containsKey(m.getPackageName())) {
                    com.opensourcefinder.app.install.InstallState.refresh(context, m);
                }
            }
            com.opensourcefinder.app.scanner.CloneUpdateChecker.refresh(context, persisted);
            MatchStore.replaceAll(context, persisted);
            return Result.success();
        } catch (Exception e) {
            return getRunAttemptCount() + 1 < MAX_ATTEMPTS ? Result.retry() : Result.failure();
        }
    }
}
