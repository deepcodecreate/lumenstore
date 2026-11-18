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

package com.opensourcefinder.app;

import android.app.Application;

import com.opensourcefinder.app.cache.RepoMatchCache;
import com.opensourcefinder.app.network.GitHubTokenProvider;
import com.opensourcefinder.app.background.BackgroundScheduler;
import com.opensourcefinder.app.download.DownloadTracker;
import com.opensourcefinder.app.install.InstallTracker;
import com.opensourcefinder.app.scanner.AutoBackupManager;
import com.opensourcefinder.app.ui.LanguageManager;

public class OpenSourceFinderApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        LanguageManager.init(this);
        GitHubTokenProvider.init(this);
        RepoMatchCache.init(this);
        BackgroundScheduler.initialize(this);
        AutoBackupManager.rescheduleIfEnabled(this);
        InstallTracker.get().watchForDismissedConfirmations(this);
        DownloadTracker.get().restore(this);
        com.opensourcefinder.app.install.InstallEvents.register(this);
    }
}
