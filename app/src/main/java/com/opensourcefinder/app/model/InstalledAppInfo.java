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

package com.opensourcefinder.app.model;

import android.graphics.drawable.Drawable;

public class InstalledAppInfo {
    private final String packageName;
    private final String appLabel;
    private final String versionName;
    private final long versionCode;
    private final Drawable icon;
    private final String installerPackage;

    public InstalledAppInfo(String packageName, String appLabel, String versionName, long versionCode, Drawable icon, String installerPackage) {
        this.packageName = packageName;
        this.appLabel = appLabel;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.icon = icon;
        this.installerPackage = installerPackage;
    }

    public String getPackageName() { return packageName; }
    public String getAppLabel() { return appLabel; }
    public String getVersionName() { return versionName; }
    public long getVersionCode() { return versionCode; }
    public Drawable getIcon() { return icon; }
    public String getInstallerPackage() { return installerPackage; }

    public boolean isGooglePlay() {
        return "com.android.vending".equals(installerPackage);
    }

    public boolean isFdroidInstalled() {
        if (installerPackage == null) return false;
        return "org.fdroid.fdroid".equals(installerPackage)
                || "org.fdroid.basic".equals(installerPackage)
                || "nya.kitsunyan.foxydroid".equals(installerPackage)
                || "com.machiav3lli.fdroid".equals(installerPackage)
                || "dev.imranr.obtainium".equals(installerPackage)
                || "dev.imranr.obtainium.nightly".equals(installerPackage);
    }
}
