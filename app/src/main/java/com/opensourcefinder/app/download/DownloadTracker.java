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

package com.opensourcefinder.app.download;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.scanner.MatchStore;
import com.opensourcefinder.app.MainActivity;
import com.opensourcefinder.app.ui.AppPreferences;
import com.opensourcefinder.app.ui.TablerIconRes;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Objects;

public final class DownloadTracker {

    public enum State { IDLE, DOWNLOADING, STOPPED, COMPLETED, FAILED, INSTALLED, CANCELLED }

    public static final class Entry {
        public final String key;
        public final String url;
        public final String fileName;
        public volatile String iconUrl;
        public volatile State state = State.IDLE;
        public volatile int progress;
        public volatile File apkFile;
        public volatile File partialFile;
        public volatile String error;
        public volatile boolean installPrompted;
        public volatile boolean cancelled;
        public volatile boolean stopped;
        public volatile long expectedBytes;
        public volatile boolean homePlaceholderOwned;
        volatile HttpURLConnection connection;
        volatile boolean terminalNotified;

        public volatile String realPackageName;
        public volatile long createdAt = System.currentTimeMillis();
        private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

        Entry(String key, String url, String fileName) {
            this.key = key;
            this.url = url;
            this.fileName = fileName;
        }

        public String displayName() {
            String name = fileName != null ? fileName : key;
            if (name == null) return "";
            return name.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")
                    ? name.substring(0, name.length() - 4) : name;
        }

        void notifyProgress() {
            for (Listener l : listeners) l.onProgress(this);
            DownloadTracker.get().dispatchGlobalProgress(this);
        }

        void notifyTerminal() {
            for (Listener l : listeners) {
                if (state == State.COMPLETED) l.onCompleted(this);
                else if (state == State.FAILED) l.onFailed(this);
                else if (state == State.CANCELLED) l.onCancelled(this);
            }
            DownloadTracker.get().dispatchGlobalTerminal(this);
        }

        void notifyStarted() {
            for (Listener l : listeners) l.onStarted(this);
            DownloadTracker.get().dispatchGlobalStarted(this);
        }
    }

    public interface Listener {
        void onProgress(Entry entry);
        void onCompleted(Entry entry);
        void onFailed(Entry entry);
        default void onCancelled(Entry entry) {}
        default void onStarted(Entry entry) {}
    }

    private static final DownloadTracker INSTANCE = new DownloadTracker();
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Listener> globalListeners = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final String NOTIFICATION_CHANNEL_ID = "downloads";
    private final AtomicBoolean channelCreated = new AtomicBoolean(false);

    private void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        if (!channelCreated.compareAndSet(false, true)) return;
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private void notifyProgressNotification(Context context, Entry entry) {
        ensureChannel(context);
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("open_tab", MainActivity.TAG_DOWNLOADS);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, entry.key.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(TablerIconRes.get(context, "ic_download"))
                .setContentTitle(entry.fileName)
                .setContentText(context.getString(R.string.download_progress, entry.progress))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(contentIntent)
                .setProgress(100, entry.progress, entry.progress <= 0);
        try {
            NotificationManagerCompat.from(context).notify(entry.key.hashCode(), builder.build());
        } catch (SecurityException ignored) {}
    }

    private void cancelNotification(Context context, Entry entry) {
        try {
            NotificationManagerCompat.from(context).cancel(entry.key.hashCode());
        } catch (Exception ignored) {}
    }

    public static DownloadTracker get() { return INSTANCE; }
    private DownloadTracker() {}

    public void addGlobalListener(Listener listener) {
        if (listener != null) globalListeners.addIfAbsent(listener);
    }

    public void removeGlobalListener(Listener listener) {
        if (listener != null) globalListeners.remove(listener);
    }

    private void dispatchGlobalProgress(Entry entry) {
        for (Listener listener : globalListeners) {
            try { listener.onProgress(entry); } catch (Exception ignored) {}
        }
    }

    private void dispatchGlobalStarted(Entry entry) {
        for (Listener listener : globalListeners) {
            try { listener.onStarted(entry); } catch (Exception ignored) {}
        }
    }

    private void dispatchGlobalTerminal(Entry entry) {
        for (Listener listener : globalListeners) {
            try {
                if (entry.state == State.COMPLETED) listener.onCompleted(entry);
                else if (entry.state == State.FAILED) listener.onFailed(entry);
                else if (entry.state == State.CANCELLED) listener.onCancelled(entry);
            } catch (Exception ignored) {}
        }
    }

    public Entry getEntry(String key) { return entries.get(key); }

    public void markHomePlaceholderOwned(String key) {
        Entry e = entries.get(key);
        if (e != null) e.homePlaceholderOwned = true;
    }

    public boolean isDownloading(String key) {
        Entry e = entries.get(key);
        return e != null && e.state == State.DOWNLOADING;
    }

    public List<Entry> snapshot() { return new ArrayList<>(entries.values()); }

    public void start(Context context, String key, String url, String fileName, Listener listener) {
        start(context, key, url, fileName, listener, null, -1L);
    }

    public void start(Context context, String key, String url, String fileName, Listener listener, String iconUrl) {
        start(context, key, url, fileName, listener, iconUrl, -1L);
    }

    public void start(Context context, String key, String url, String fileName, Listener listener, String iconUrl, long expectedBytes) {
        Entry existing = entries.get(key);
        if (existing != null && (existing.state == State.DOWNLOADING || existing.state == State.STOPPED)) {
            if (iconUrl != null) existing.iconUrl = iconUrl;
            if (expectedBytes > 0) existing.expectedBytes = expectedBytes;
            if (existing.state == State.STOPPED) {
                existing.cancelled = false;
                existing.stopped = false;
                existing.state = State.DOWNLOADING;
                if (listener != null) existing.listeners.add(listener);
                existing.notifyProgress();
                final Context app = context.getApplicationContext();
                executor.execute(() -> doDownload(app, existing));
            } else if (listener != null) {
                existing.listeners.add(listener);
                listener.onProgress(existing);
            }
            return;
        }
        if (existing != null && (existing.state == State.COMPLETED || existing.state == State.INSTALLED)) {
            if (Objects.equals(existing.url, url) && Objects.equals(existing.fileName, fileName)) {

                entries.remove(key, existing);
            } else {

                remove(key);
            }
        }

        Entry entry = new Entry(key, url, fileName);
        entry.iconUrl = iconUrl;
        entry.expectedBytes = expectedBytes;
        entry.state = State.DOWNLOADING;
        entry.progress = 0;
        entry.installPrompted = false;
        if (listener != null) entry.listeners.add(listener);
        entries.put(key, entry);
        lastContext = context.getApplicationContext();
        entry.notifyProgress();
        entry.notifyStarted();
        persist();
        final Context app = context.getApplicationContext();
        executor.execute(() -> prepareAndRun(app, entry));
    }

    private void prepareAndRun(Context context, Entry entry) {
        try {
            File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) dir = context.getFilesDir();
            File target = new File(dir, safeName(entry.fileName));
            if (target.exists()) {
                String pkg = reusablePackage(context, target, entry.expectedBytes);
                if (pkg != null && !entry.cancelled && !entry.stopped) {
                    entry.partialFile = target;
                    entry.apkFile = target;
                    entry.realPackageName = pkg;
                    entry.progress = 100;
                    entry.state = State.COMPLETED;
                    entry.notifyProgress();
                    entry.notifyTerminal();
                    persist();
                    return;
                }
                try { target.delete(); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        if (entry.cancelled) return;
        doDownload(context, entry);
    }

    private static String reusablePackage(Context context, File apk, long expectedBytes) {
        try {
            if (apk == null || !apk.exists() || apk.length() <= 0) return null;
            if (expectedBytes > 0 && apk.length() != expectedBytes) return null;
            PackageInfo ai = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            if (ai == null || ai.packageName == null) return null;
            com.opensourcefinder.app.install.InstallState.Info installed =
                    com.opensourcefinder.app.install.InstallState.read(context, ai.packageName);
            if (installed != null && installed.versionCode >= com.opensourcefinder.app.install.InstallState.codeOf(ai)) {
                return null;
            }
            return ai.packageName;
        } catch (Exception e) {
            return null;
        }
    }

    public void attach(String key, Listener listener) {
        Entry e = entries.get(key);
        if (e == null || listener == null) return;
        e.listeners.add(listener);
        listener.onProgress(e);
        if (e.state == State.FAILED) listener.onFailed(e);
    }

    public void detach(String key, Listener listener) {
        Entry e = entries.get(key);
        if (e != null && listener != null) e.listeners.remove(listener);
    }

    public void markInstallPrompted(String key) {
        Entry e = entries.get(key);
        if (e != null) {
            e.installPrompted = true;
            if (lastContext != null) cancelNotification(lastContext, e);
        }
    }

    public void markInstalled(String key) {
        Entry e = entries.get(key);
        if (e != null) {
            e.state = State.INSTALLED;
            applyKeepApkPolicy(e);
            if (lastContext != null) cancelNotification(lastContext, e);
            e.notifyProgress();
            persist();
        }
    }

    private void applyKeepApkPolicy(Entry e) {
        if (e == null || e.apkFile == null) return;
        Context app = lastContext;
        if (app != null && AppPreferences.isKeepApk(app)) return;
        File f = e.apkFile;
        e.apkFile = null;
        try { if (f.exists()) f.delete(); } catch (Exception ignored) {}
    }

    public void clear(String key) { entries.remove(key); }

    public boolean remove(String key) {
        Entry e = entries.remove(key);
        if (e == null) return false;
        HttpURLConnection c = e.connection;
        if (c != null) {
            try { c.disconnect(); } catch (Exception ignored) {}
        }
        if (e.apkFile != null && e.apkFile.exists()) {
            try { e.apkFile.delete(); } catch (Exception ignored) {}
        }
        if (e.partialFile != null && e.partialFile.exists()) {
            try { e.partialFile.delete(); } catch (Exception ignored) {}
        }
        e.connection = null;
        e.state = State.CANCELLED;
        persist();
        return true;
    }

    public int clearFinished() {
        int removed = 0;
        for (Entry e : snapshot()) {
            if (e.state == State.INSTALLED || e.state == State.FAILED || e.state == State.CANCELLED) {
                if (remove(e.key)) removed++;
            }
        }
        return removed;
    }

    public void stop(String key) {
        Entry e = entries.get(key);
        if (e == null || e.state != State.DOWNLOADING) return;
        e.stopped = true;
        HttpURLConnection c = e.connection;
        if (c != null) c.disconnect();
    }

    public void cancel(String key) {
        Entry e = entries.get(key);
        if (e == null || (e.state != State.DOWNLOADING && e.state != State.STOPPED)) return;
        e.cancelled = true;
        e.stopped = false;
        HttpURLConnection c = e.connection;
        if (c != null) {
            try { c.disconnect(); } catch (Exception ignored) {}
        }
        File partial = e.partialFile;
        if (partial != null && partial.exists()) {
            try { partial.delete(); } catch (Exception ignored) {}
        }
        e.state = State.CANCELLED;
        e.connection = null;
        if (!e.terminalNotified) {
            e.terminalNotified = true;
            e.notifyTerminal();
        }
        cleanupAbandonedPlaceholder(lastContext, e);
        entries.remove(key, e);
        persist();
    }

    public void detectInstalledPackages(Context context) {
        lastContext = context.getApplicationContext();
        boolean changed = false;
        for (Entry e : entries.values()) {
            if (e.state != State.COMPLETED && e.state != State.INSTALLED) continue;

            String pkg = e.realPackageName != null ? e.realPackageName : e.key;
            boolean installedNow = com.opensourcefinder.app.install.InstallState.isApkInstalled(lastContext, e.apkFile, pkg);
            if (!installedNow && !pkg.equals(e.key)) {
                installedNow = com.opensourcefinder.app.install.InstallState.isApkInstalled(lastContext, e.apkFile, e.key);
            }
            boolean apkAvailable = e.apkFile != null && e.apkFile.exists() && e.apkFile.length() > 0;
            if (e.state == State.COMPLETED && installedNow && apkAvailable) {
                e.state = State.INSTALLED;
                applyKeepApkPolicy(e);
                cancelNotification(lastContext, e);
                e.notifyProgress();
                changed = true;
            } else if (e.state == State.INSTALLED && !installedNow) {
                if (isPendingReinstall(pkg) || isPendingReinstall(e.key)) {
                    if (apkAvailable) {
                        e.state = State.COMPLETED;
                        e.installPrompted = true;
                        e.notifyProgress();
                    } else {
                        entries.remove(e.key, e);
                    }
                    clearPendingReinstall(pkg);
                    clearPendingReinstall(e.key);
                } else {
                    remove(e.key);
                }
                changed = true;
            }
        }
        if (changed) persist();
    }

    private volatile Context lastContext;

    private void doDownload(Context context, Entry entry) {
        lastContext = context;
        File downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir == null) downloadDir = context.getFilesDir();
        File target = new File(downloadDir, safeName(entry.fileName));
        entry.partialFile = target;
        notifyProgressNotification(context, entry);
        try {
            File parent = target.getParentFile();
            if (parent != null) parent.mkdirs();
            long existingBytes = target.exists() ? target.length() : 0L;
            HttpURLConnection c = com.opensourcefinder.app.network.HttpClients.open(new URL(entry.url));
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "OpenSourceFinder/1.0");
            if (existingBytes > 0) c.setRequestProperty("Range", "bytes=" + existingBytes + "-");
            entry.connection = c;
            int code = c.getResponseCode();
            if (code == 416) {
                existingBytes = 0L;
                c.disconnect();
                c = com.opensourcefinder.app.network.HttpClients.open(new URL(entry.url));
                c.setConnectTimeout(15000); c.setReadTimeout(60000); c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "OpenSourceFinder/1.0");
                code = c.getResponseCode();
            }
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            boolean append = existingBytes > 0 && code == HttpURLConnection.HTTP_PARTIAL;
            if (!append) existingBytes = 0L;
            long contentLength = c.getContentLengthLong();
            long total;
            if (entry.expectedBytes > 0) {
                total = entry.expectedBytes;
            } else {
                total = contentLength > 0 ? existingBytes + contentLength : -1;
                String contentRange = c.getHeaderField("Content-Range");
                if (contentRange != null) {
                    java.util.regex.Matcher range = java.util.regex.Pattern
                            .compile("bytes\\s+\\d+-\\d+\\/(\\d+)")
                            .matcher(contentRange);
                    if (range.find()) {
                        try { total = Long.parseLong(range.group(1)); } catch (NumberFormatException ignored) {}
                    }
                }
            }
            entry.connection = c;
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(target, append)) {
                byte[] buffer = new byte[32768];
                long done = existingBytes;
                int read;
                int last = -1;
                while ((read = in.read(buffer)) != -1) {
                    if (entry.cancelled) throw new java.io.InterruptedIOException("cancelled");
                    if (entry.stopped) throw new java.io.InterruptedIOException("stopped");
                    out.write(buffer, 0, read);
                    done += read;
                    if (total > 0) {
                        int p = (int) Math.min(100, done * 100 / total);
                        if (p != last) {
                            last = p;
                            entry.progress = p;
                            entry.notifyProgress();
                            notifyProgressNotification(context, entry);
                        }
                    }
                }
            } finally {
                c.disconnect();
            }
            if (entry.cancelled) throw new java.io.InterruptedIOException("cancelled");
            if (entry.stopped) {
                entry.state = State.STOPPED;
                entry.connection = null;
                entry.notifyProgress();
                cancelNotification(context, entry);
                persist();
                return;
            }
            if (!target.exists() || target.length() == 0) throw new IllegalStateException("empty apk");
            entry.progress = 100;
            entry.apkFile = target;

            PackageInfo pi = context.getPackageManager().getPackageArchiveInfo(target.getAbsolutePath(), 0);
            if (pi == null || pi.packageName == null) {
                throw new IllegalStateException("invalid APK");
            }
            entry.realPackageName = pi.packageName;
            entry.state = State.COMPLETED;
            entry.notifyProgress();
            entry.notifyTerminal();
            cancelNotification(context, entry);
            persist();
        } catch (Exception e) {
            if (entry.stopped && !entry.cancelled) {
                entry.state = State.STOPPED;
                entry.connection = null;
                entry.notifyProgress();
                cancelNotification(context, entry);
                persist();
                return;
            }
            if (target.exists()) target.delete();
            if (entry.cancelled) {
                entry.state = State.CANCELLED;
                if (!entry.terminalNotified) {
                    entry.terminalNotified = true;
                    entry.notifyTerminal();
                }
                cancelNotification(context, entry);
                cleanupAbandonedPlaceholder(context, entry);
                entries.remove(entry.key, entry);
                persist();
                return;
            }
            entry.error = e.getMessage();
            entry.state = State.FAILED;
            entry.notifyTerminal();
            cancelNotification(context, entry);
            cleanupAbandonedPlaceholder(context, entry);
            persist();
        }
    }

    private static final String HISTORY_PREFS = "download_history";
    private static final String HISTORY_KEY = "entries_v1";
    private final ExecutorService persistExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean restored;
    private final java.util.Set<String> pendingReinstalls =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    public void markPendingReinstall(String packageName) {
        if (packageName != null) pendingReinstalls.add(packageName);
    }

    public boolean isPendingReinstall(String packageName) {
        return packageName != null && pendingReinstalls.contains(packageName);
    }

    public void clearPendingReinstall(String packageName) {
        if (packageName != null) pendingReinstalls.remove(packageName);
    }

    private static final class SavedEntry {
        String key;
        String url;
        String fileName;
        String iconUrl;
        String state;
        int progress;
        String apkPath;
        String partialPath;
        String realPackageName;
        long expectedBytes;
        long createdAt;
        boolean homePlaceholderOwned;
        String error;
    }

    public synchronized void restore(Context context) {
        if (context == null) return;
        lastContext = context.getApplicationContext();
        if (restored) return;
        restored = true;
        if (!AppPreferences.isSaveDownloads(lastContext)) return;
        String raw = lastContext.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE).getString(HISTORY_KEY, null);
        if (raw == null || raw.isEmpty()) return;
        try {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<SavedEntry>>(){}.getType();
            List<SavedEntry> saved = new com.google.gson.Gson().fromJson(raw, type);
            if (saved == null) return;
            for (SavedEntry s : saved) {
                if (s == null || s.key == null || s.url == null || entries.containsKey(s.key)) continue;
                State state;
                try { state = State.valueOf(s.state); } catch (Exception bad) { continue; }
                Entry e = new Entry(s.key, s.url, s.fileName);
                e.iconUrl = s.iconUrl;
                e.progress = Math.max(0, Math.min(100, s.progress));
                e.realPackageName = s.realPackageName;
                e.expectedBytes = s.expectedBytes;
                e.createdAt = s.createdAt > 0 ? s.createdAt : System.currentTimeMillis();
                e.homePlaceholderOwned = s.homePlaceholderOwned;
                e.error = s.error;
                e.installPrompted = true;
                File apk = s.apkPath == null ? null : new File(s.apkPath);
                File partial = s.partialPath == null ? null : new File(s.partialPath);
                switch (state) {
                    case DOWNLOADING:
                    case STOPPED:

                        e.state = State.STOPPED;
                        e.partialFile = partial;
                        if (partial == null || !partial.exists()) e.progress = 0;
                        break;
                    case COMPLETED:
                        if (apk == null || !apk.exists() || apk.length() == 0) continue;
                        e.state = State.COMPLETED;
                        e.apkFile = apk;
                        break;
                    case INSTALLED:
                        e.state = State.INSTALLED;
                        e.apkFile = apk != null && apk.exists() ? apk : null;
                        break;
                    case FAILED:
                        e.state = State.FAILED;
                        break;
                    default:
                        continue;
                }
                e.terminalNotified = e.state != State.STOPPED;
                entries.put(e.key, e);
            }
        } catch (Exception ignored) {}
    }

    public void onSavePreferenceChanged(Context context) {
        if (context == null) return;
        lastContext = context.getApplicationContext();
        restored = true;
        if (AppPreferences.isSaveDownloads(lastContext)) {
            persist();
        } else {
            final Context app = lastContext;
            persistExecutor.execute(() -> app.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE)
                    .edit().remove(HISTORY_KEY).apply());
        }
    }

    private void persist() {
        final Context app = lastContext;
        if (app == null || !AppPreferences.isSaveDownloads(app)) return;
        final List<SavedEntry> out = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (e.state == State.CANCELLED || e.state == State.IDLE) continue;
            SavedEntry s = new SavedEntry();
            s.key = e.key;
            s.url = e.url;
            s.fileName = e.fileName;
            s.iconUrl = e.iconUrl;
            s.state = e.state.name();
            s.progress = e.progress;
            s.apkPath = e.apkFile != null ? e.apkFile.getAbsolutePath() : null;
            s.partialPath = e.partialFile != null ? e.partialFile.getAbsolutePath() : null;
            s.realPackageName = e.realPackageName;
            s.expectedBytes = e.expectedBytes;
            s.createdAt = e.createdAt;
            s.homePlaceholderOwned = e.homePlaceholderOwned;
            s.error = e.error;
            out.add(s);
        }
        persistExecutor.execute(() -> {
            try {
                String json = new com.google.gson.Gson().toJson(out);
                app.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE)
                        .edit().putString(HISTORY_KEY, json).apply();
            } catch (Exception ignored) {}
        });
    }

    private static String safeName(String name) {
        if (name == null || name.trim().isEmpty()) return "update.apk";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void cleanupAbandonedPlaceholder(Context context, Entry entry) {
        if (!entry.homePlaceholderOwned || context == null) return;
        MatchStore.removeIfNeverInstalled(context, entry.key);
    }
}
