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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import com.opensourcefinder.app.model.ExploreApp;
import com.opensourcefinder.app.model.MatchedApp;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class InstallState {
    private InstallState() {}

    public static final class Info {
        public final String packageName;
        public final long versionCode;
        public final String versionName;
        Info(String packageName, long versionCode, String versionName) {
            this.packageName = packageName;
            this.versionCode = versionCode;
            this.versionName = versionName == null ? "" : versionName;
        }
    }

    public enum Ui { NOT_INSTALLED, UPDATE, UP_TO_DATE }

    public static long codeOf(PackageInfo pi) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? pi.getLongVersionCode() : pi.versionCode;
    }

    public static Info read(Context context, String packageName) {
        if (context == null || packageName == null || packageName.isEmpty()) return null;
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(packageName, 0);
            return new Info(packageName, codeOf(pi), pi.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static Map<String, Info> readAll(Context context) {
        Map<String, Info> out = new HashMap<>();
        try {
            for (PackageInfo pi : context.getPackageManager().getInstalledPackages(0)) {
                if (pi.packageName != null) out.put(pi.packageName, new Info(pi.packageName, codeOf(pi), pi.versionName));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static boolean isNewer(String remote, String local) {
        if (remote == null || local == null) return false;
        return VersionCompare.isNewer(remote, local);
    }

    public static boolean isPreRelease(String s) {
        return VersionCompare.isPreRelease(s);
    }

    public static boolean tagIsNewer(String remote, String local) {
        if (remote == null || local == null || remote.isEmpty() || local.isEmpty()) return false;
        if (normalizeName(remote).equals(normalizeName(local))) return false;
        return isNewer(remote, local);
    }

    public static boolean sameVersionName(String a, String b) {
        if (a == null || b == null) return false;
        String x = normalizeName(a), y = normalizeName(b);
        return !x.isEmpty() && x.equals(y);
    }

    private static String normalizeName(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("v") && t.length() > 1 && Character.isDigit(t.charAt(1))) t = t.substring(1);
        return t;
    }

    public static boolean updateAvailable(Info installed, long latestCode, String latestTag) {
        if (installed == null) return false;
        if (sameVersionName(latestTag, installed.versionName)) return false;
        if (latestCode > 0 && installed.versionCode > 0) return latestCode > installed.versionCode;
        if (latestTag != null && !latestTag.isEmpty() && !installed.versionName.isEmpty()) {
            return isNewer(latestTag, installed.versionName);
        }
        return false;
    }

    public static boolean updateFor(MatchedApp m, Info info) {
        if (info == null) return false;
        String installedTag = m.getInstalledTag();
        String latestTag = m.getLatestTag();
        if (installedTag != null && !installedTag.isEmpty()
                && m.getInstalledTagVersionCode() == info.versionCode
                && m.getLatestVersionCode() <= 0
                && latestTag != null && !latestTag.isEmpty()) {
            if (installedTag.equals(latestTag)) return false;
            if (sameVersionName(latestTag, info.versionName)) return false;
            return tagIsNewer(latestTag, installedTag);
        }
        return updateAvailable(info, m.getLatestVersionCode(), latestTag);
    }

    public static boolean verifiedInstalled(Context context, com.opensourcefinder.app.download.DownloadTracker.Entry e) {
        if (context == null || e == null || e.state != com.opensourcefinder.app.download.DownloadTracker.State.INSTALLED) return false;
        String pkg = e.realPackageName != null ? e.realPackageName : e.key;
        if (read(context, pkg) == null && (pkg.equals(e.key) || read(context, e.key) == null)) return false;
        if (e.apkFile == null || !e.apkFile.exists() || e.apkFile.length() == 0) return true;
        return isApkInstalled(context, e.apkFile, pkg) || isApkInstalled(context, e.apkFile, e.key);
    }

    private static final String DOWNLOAD_MARKER = "/releases/download/";

    static String tagFromUrl(String url) {
        if (url == null) return null;
        int at = url.indexOf(DOWNLOAD_MARKER);
        if (at < 0) return null;
        String rest = url.substring(at + DOWNLOAD_MARKER.length());
        int cut = rest.indexOf('/');
        if (cut <= 0) return null;
        String raw = rest.substring(0, cut);
        try {
            return java.net.URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8");
        } catch (Exception e) {
            return raw;
        }
    }

    public static void recordInstalledTag(Context context, MatchedApp m, com.opensourcefinder.app.download.DownloadTracker.Entry e) {
        if (m == null || e == null || !verifiedInstalled(context, e)) return;
        String tag = tagFromUrl(e.url);
        if (tag == null || tag.isEmpty()) {
            String latest = m.getLatestTag();
            if (latest == null || latest.isEmpty() || e.fileName == null || !e.fileName.contains(latest)) return;
            tag = latest;
        }
        Info info = read(context, m.getPackageName());
        if (info == null) return;
        m.setInstalledTag(tag);
        m.setInstalledTagVersionCode(info.versionCode);
    }

    public static void refresh(Context context, MatchedApp m) {
        if (m == null || m.getPackageName() == null) return;
        Info info = read(context, m.getPackageName());
        if (info == null) {
            m.setNotInstalled(true);
            m.setUpdateAvailable(m.getDownloadUrl() != null);
        } else {
            m.setNotInstalled(false);
            m.setInstalledVersionName(info.versionName);
            m.setUpdateAvailable(updateFor(m, info));
        }
    }

    public static void refresh(MatchedApp m, Info info) {
        if (m == null) return;
        if (info == null) {
            m.setNotInstalled(true);
            m.setUpdateAvailable(m.getDownloadUrl() != null);
        } else {
            m.setNotInstalled(false);
            m.setInstalledVersionName(info.versionName);
            m.setUpdateAvailable(updateFor(m, info));
        }
    }

    public static void refresh(ExploreApp app, Info info) {
        if (app == null) return;
        app.setInstalled(info != null);
        app.setInstalledVersionCode(info == null ? 0L : info.versionCode);
        app.setInstalledVersionName(info == null ? null : info.versionName);
    }

    public static Ui ui(ExploreApp app) {
        if (app == null || !app.isInstalled()) return Ui.NOT_INSTALLED;
        if (app.isGithubRelease()) {
            String latestName = app.getLatestVersionName();
            String have = app.getInstalledVersionName();
            if (latestName == null || latestName.isEmpty() || have == null || have.isEmpty()) return Ui.UP_TO_DATE;
            return isNewer(latestName, have) ? Ui.UPDATE : Ui.UP_TO_DATE;
        }
        if (sameVersionName(app.getLatestVersionName(), app.getInstalledVersionName())) return Ui.UP_TO_DATE;
        long latest = app.getLatestVersionCode(), have = app.getInstalledVersionCode();
        if (latest > 0 && have > 0 && have < latest) return Ui.UPDATE;
        return Ui.UP_TO_DATE;
    }

    public static void markCurrentIfFromLatest(Context context, MatchedApp m, com.opensourcefinder.app.download.DownloadTracker.Entry e) {
        if (m == null || e == null || m.isNotInstalled() || !m.isUpdateAvailable()) return;
        if (!verifiedInstalled(context, e)) return;
        if (m.getLatestVersionCode() > 0) return;
        String tag = m.getLatestTag();
        if (tag == null || tag.isEmpty()) return;
        String downloaded = tagFromUrl(e.url);
        boolean same = downloaded != null ? downloaded.equals(tag) : e.fileName != null && e.fileName.contains(tag);
        if (same) m.setUpdateAvailable(false);
    }

    public static boolean isApkInstalled(Context context, java.io.File apk, String fallbackPackage) {
        String pkg = fallbackPackage;
        long apkCode = -1;
        try {
            if (apk != null && apk.exists() && apk.length() > 0) {
                PackageInfo ai = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
                if (ai != null) { pkg = ai.packageName; apkCode = codeOf(ai); }
            }
        } catch (Exception ignored) {}
        Info installed = read(context, pkg);
        if (installed == null) return false;
        return apkCode <= 0 || installed.versionCode >= apkCode;
    }
}
