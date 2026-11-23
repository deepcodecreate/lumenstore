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

import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.opensourcefinder.app.download.DownloadTracker;
import com.opensourcefinder.app.scanner.MatchStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InstallTracker {

    public enum State { IDLE, INSTALLING, AWAITING_CONFIRM, INSTALLED, CANCELLED, FAILED, FAILED_INCOMPATIBLE }

    public static final class Entry {
        public final String key;
        public final boolean firstInstall;
        public volatile State state = State.IDLE;
        public volatile String message;
        public volatile File apkFile;
        public volatile Context appContext;
        volatile int sessionId = -1;
        volatile long startedAt;
        volatile long awaitingSince;
        volatile Intent confirmIntent;
        final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

        Entry(String key, boolean firstInstall) {
            this.key = key;
            this.firstInstall = firstInstall;
        }

        void notifyState() {
            for (Listener l : listeners) l.onStateChanged(this);
        }
    }

    public interface Listener {
        void onStateChanged(Entry entry);
    }

    private static final InstallTracker INSTANCE = new InstallTracker();
    public static InstallTracker get() { return INSTANCE; }
    private InstallTracker() {}

    private static final long AWAIT_LIMIT_MS = 90_000L;
    private static final long SESSION_LIMIT_MS = 120_000L;
    private static final int ICON_SIZE = 192;

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final ArrayDeque<Entry> queue = new ArrayDeque<>();
    private final Object queueLock = new Object();
    private Entry active;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean watchdogRegistered = false;
    private int startedActivityCount = 0;
    private boolean wasInBackground = false;
    private volatile Activity resumedActivity;

    public Entry getEntry(String key) { return entries.get(key); }

    public void watchForDismissedConfirmations(Application app) {
        if (watchdogRegistered) return;
        watchdogRegistered = true;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity activity) {
                if (startedActivityCount == 0) wasInBackground = true;
                startedActivityCount++;
            }
            @Override public void onActivityStopped(Activity activity) {
                startedActivityCount = Math.max(0, startedActivityCount - 1);
            }
            @Override public void onActivityResumed(Activity activity) {
                resumedActivity = activity;
                if (wasInBackground) {
                    wasInBackground = false;
                    Context appContext = activity.getApplicationContext();
                    mainHandler.postDelayed(() -> reconcileAwaitingConfirm(appContext), 400L);
                }
                mainHandler.post(() -> pump(false));
            }
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
            @Override public void onActivityPaused(Activity activity) {
                if (resumedActivity == activity) resumedActivity = null;
            }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {
                if (resumedActivity == activity) resumedActivity = null;
            }
        });
    }

    private static boolean isTerminal(State state) {
        return state == State.INSTALLED || state == State.CANCELLED
                || state == State.FAILED || state == State.FAILED_INCOMPATIBLE;
    }

    private boolean sessionPending(Context context, Entry e) {
        if (e.sessionId < 0) return false;
        try {
            PackageInstaller installer = context.getPackageManager().getPackageInstaller();
            return installer.getSessionInfo(e.sessionId) != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private void reconcileAwaitingConfirm(Context context) {
        long now = SystemClock.elapsedRealtime();
        for (Entry e : entries.values()) {
            boolean awaiting = e.state == State.AWAITING_CONFIRM;
            boolean stuck = e.state == State.INSTALLING && e.sessionId >= 0
                    && e.startedAt > 0 && now - e.startedAt > SESSION_LIMIT_MS;
            if (!awaiting && !stuck) continue;

            boolean installed = InstallState.isApkInstalled(context, e.apkFile, e.key);
            if (!installed && awaiting && now - e.awaitingSince <= AWAIT_LIMIT_MS && sessionPending(context, e)) continue;

            e.state = installed ? State.INSTALLED : (stuck ? State.FAILED : State.CANCELLED);
            if (!installed) cleanupAbandonedPlaceholder(e);
            e.notifyState();
            finish(e);
        }
    }

    public void reconcileNow(Context context) {
        reconcileAwaitingConfirm(context);
    }

    private void cleanupAbandonedPlaceholder(Entry entry) {
        Context context = entry.appContext;
        if (context == null) return;
        DownloadTracker.Entry de = DownloadTracker.get().getEntry(entry.key);
        if (de != null && de.homePlaceholderOwned) {
            MatchStore.removeIfNeverInstalled(context, entry.key);
        }
    }

    public void attach(String key, Listener listener) {
        Entry e = entries.get(key);
        if (e == null || listener == null) return;
        e.listeners.add(listener);
    }

    public void detach(String key, Listener listener) {
        Entry e = entries.get(key);
        if (e != null && listener != null) e.listeners.remove(listener);
    }

    public void clear(String key) {
        Entry removed = entries.remove(key);
        if (removed != null) finish(removed);
    }

    public void startInstall(Context context, String key, File apkFile, Listener listener) {
        Entry existing = entries.get(key);
        if (existing != null && (existing.state == State.INSTALLING || existing.state == State.AWAITING_CONFIRM)) {
            if (listener != null) existing.listeners.addIfAbsent(listener);
            Intent confirm = existing.confirmIntent;
            if (existing.state == State.AWAITING_CONFIRM && confirm != null) {
                try {
                    launchConfirmation(context, confirm);
                } catch (Exception ignored) {}
            }
            existing.notifyState();
            return;
        }
        boolean firstInstall = true;
        try {
            context.getPackageManager().getApplicationInfo(key, 0);
            firstInstall = false;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
        Entry entry = new Entry(key, firstInstall);
        entry.apkFile = apkFile;
        entry.state = State.INSTALLING;
        entry.appContext = context.getApplicationContext();
        if (listener != null) entry.listeners.add(listener);
        entries.put(key, entry);
        entry.notifyState();
        synchronized (queueLock) {
            queue.add(entry);
        }
        pump(false);
    }

    private void pump(boolean fromCompletion) {
        Entry next;
        synchronized (queueLock) {
            if (active != null) return;
            while (true) {
                next = queue.peek();
                if (next == null) return;
                if (entries.get(next.key) != next || isTerminal(next.state)) {
                    queue.poll();
                    continue;
                }
                break;
            }
            if (fromCompletion && resumedActivity == null) return;
            queue.poll();
            active = next;
            next.startedAt = SystemClock.elapsedRealtime();
        }
        final Entry toRun = next;
        final Context context = toRun.appContext;
        new Thread(() -> runSession(context, toRun), "install-session-" + toRun.key).start();
    }

    private void finish(Entry entry) {
        synchronized (queueLock) {
            if (active == entry) active = null;
            queue.remove(entry);
        }
        mainHandler.post(() -> pump(true));
    }

    public void launchConfirmation(Context context, Intent confirmIntent) {
        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity front = resumedActivity;
        (front != null ? front : context).startActivity(confirmIntent);
    }

    private void runSession(Context context, Entry entry) {
        try {
            PackageInstaller installer = context.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setInstallReason(PackageManager.INSTALL_REASON_USER);
            applyApkDetails(context, params, entry.apkFile);
            int sessionId = installer.createSession(params);
            entry.sessionId = sessionId;
            try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                try (InputStream in = new FileInputStream(entry.apkFile);
                     OutputStream out = session.openWrite("apk", 0, entry.apkFile.length())) {
                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                    session.fsync(out);
                }

                Intent callback = new Intent(context, InstallResultReceiver.class);
                callback.setAction(InstallResultReceiver.ACTION_INSTALL_STATUS);
                callback.putExtra(InstallResultReceiver.EXTRA_KEY, entry.key);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, sessionId, callback, flags);
                session.commit(pendingIntent.getIntentSender());
            }
        } catch (Exception e) {
            reportStatus(entry.key, PackageInstaller.STATUS_FAILURE, e.getMessage());
        }
    }

    private static void applyApkDetails(Context context, PackageInstaller.SessionParams params, File apkFile) {
        try {
            PackageManager pm = context.getPackageManager();
            String path = apkFile.getAbsolutePath();
            PackageInfo info = pm.getPackageArchiveInfo(path, 0);
            if (info == null || info.applicationInfo == null || info.packageName == null
                    || info.packageName.isEmpty()) return;
            params.setAppPackageName(info.packageName);
            ApplicationInfo appInfo = info.applicationInfo;
            appInfo.sourceDir = path;
            appInfo.publicSourceDir = path;
            try {
                CharSequence label = appInfo.loadLabel(pm);
                if (label != null && label.length() > 0) params.setAppLabel(label);
            } catch (Exception ignored) {}
            try {
                Drawable icon = appInfo.loadIcon(pm);
                if (icon != null) {
                    Bitmap bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    icon.setBounds(0, 0, ICON_SIZE, ICON_SIZE);
                    icon.draw(canvas);
                    params.setAppIcon(bitmap);
                }
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    public void reportAwaitingConfirm(String key, Intent confirmIntent) {
        Entry e = entries.get(key);
        if (e == null) return;
        e.confirmIntent = confirmIntent;
        e.awaitingSince = SystemClock.elapsedRealtime();
        e.state = State.AWAITING_CONFIRM;
        e.notifyState();
    }

    public void reportStatus(String key, int status, String message) {
        Entry e = entries.get(key);
        if (e == null) return;
        State state;
        if (status == PackageInstaller.STATUS_SUCCESS) {
            state = State.INSTALLED;
        } else if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
            state = State.CANCELLED;
        } else if (status == PackageInstaller.STATUS_FAILURE_CONFLICT
                || status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE) {
            state = State.FAILED_INCOMPATIBLE;
        } else {
            state = State.FAILED;
        }
        e.state = state;
        e.message = message;

        if (state == State.CANCELLED || state == State.FAILED) cleanupAbandonedPlaceholder(e);
        e.notifyState();
        finish(e);
        if (state == State.INSTALLED && e.appContext != null) {
            InstallEvents.packageChanged(e.appContext, null);
        }
    }
}
