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
import android.content.pm.PackageInstaller;

public class InstallResultReceiver extends BroadcastReceiver {

    public static final String ACTION_INSTALL_STATUS = "com.opensourcefinder.app.action.INSTALL_STATUS";
    public static final String EXTRA_KEY = "com.opensourcefinder.app.extra.INSTALL_KEY";

    @Override
    public void onReceive(Context context, Intent intent) {
        String key = intent.getStringExtra(EXTRA_KEY);
        if (key == null) return;

        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            InstallTracker.get().reportAwaitingConfirm(key, confirmIntent);
            if (confirmIntent != null) {
                try {
                    InstallTracker.get().launchConfirmation(context, confirmIntent);
                } catch (Exception e) {
                    InstallTracker.get().reportStatus(key, PackageInstaller.STATUS_FAILURE, e.getMessage());
                }
            }
            return;
        }

        InstallTracker.get().reportStatus(key, status, message);
    }
}
