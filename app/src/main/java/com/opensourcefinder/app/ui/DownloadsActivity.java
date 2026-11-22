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

package com.opensourcefinder.app.ui;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.widget.PopupMenu;
import android.widget.ImageView;
import android.widget.TextView;
import android.net.Uri;
import java.io.InputStream;
import java.io.OutputStream;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import android.content.Intent;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.download.DownloadTracker;
import com.opensourcefinder.app.install.InstallTracker;
import com.opensourcefinder.app.model.MatchedApp;
import com.opensourcefinder.app.scanner.MatchStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadsActivity extends BaseActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private DownloadsAdapter adapter;
    private View empty;
    private TextView downloadCount;
    private View loading;
    private androidx.activity.result.ActivityResultLauncher<String> exportLauncher;
    private DownloadTracker.Entry exportEntry;
    private androidx.activity.result.ActivityResultLauncher<Uri> exportFolderLauncher;
    private InstallTracker.Listener installListener;
    private volatile boolean loadingData = false;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private boolean refreshQueued = false;
    private java.io.File pendingReinstallApk;
    private String pendingReinstallKey;
    private static final int UNINSTALL_REQUEST = 9421;
    private final DownloadTracker.Listener globalListener = new DownloadTracker.Listener() {
        @Override public void onProgress(DownloadTracker.Entry entry) { scheduleRefresh(); }
        @Override public void onCompleted(DownloadTracker.Entry entry) {
            runOnUiThread(() -> {
                refresh();
                if (!entry.installPrompted && entry.apkFile != null
                        && entry.apkFile.exists() && entry.apkFile.length() > 0) {
                    DownloadTracker.get().markInstallPrompted(entry.key);
                    handleDownloadedApk(entry.key, entry.apkFile);
                }
            });
        }
        @Override public void onFailed(DownloadTracker.Entry entry) { runOnUiThread(() -> refresh()); }

        @Override public void onCancelled(DownloadTracker.Entry entry) { runOnUiThread(() -> refresh()); }    };

    private void scheduleRefresh() {
        if (refreshQueued || isFinishing()) return;
        refreshQueued = true;
        refreshHandler.postDelayed(() -> {
            refreshQueued = false;
            if (!isFinishing()) refresh();
        }, 120L);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        MotionHelper.applyActivityTransition(this, true);
        setContentView(R.layout.activity_downloads);
        MotionHelper.animateActivityContent(this);
        MotionHelper.keepBottomNavStatic(this);
        exportLauncher = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/vnd.android.package-archive"), uri -> {
            if (uri == null || exportEntry == null || exportEntry.apkFile == null) return;
            DownloadTracker.Entry entry = exportEntry; exportEntry = null;
            new Thread(() -> exportApk(uri, entry)).start();
        });
        exportFolderLauncher = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(), uri -> {
            if (uri == null || exportEntry == null || exportEntry.apkFile == null) return;
            DownloadTracker.Entry entry = exportEntry; exportEntry = null;
            new Thread(() -> exportApkToFolder(uri, entry)).start();
        });
        ThemeManager.applyWindow(this);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(getString(R.string.downloads_title));
        BottomNavHelper.bind(this, R.id.nav_downloads);
        empty = findViewById(R.id.emptyView);
        downloadCount = findViewById(R.id.downloadCount);
        loading = findViewById(R.id.downloadsLoading);
        RecyclerView rv = findViewById(R.id.recyclerView);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setHasFixedSize(true);
        rv.setItemViewCacheSize(8);
        MotionHelper.setupListAnimator(rv);
        adapter = new DownloadsAdapter();
        rv.setAdapter(adapter);
    }

    private final Runnable installEventsListener = () -> refresh();

    @Override protected void onResume() {
        super.onResume();
        com.opensourcefinder.app.install.InstallEvents.addListener(installEventsListener);
        if (loadingData) return;
        loadingData = true;

        List<DownloadTracker.Entry> currentEntries = DownloadTracker.get().snapshot();
        for (DownloadTracker.Entry e : currentEntries) {
            DownloadTracker.get().attach(e.key, globalListener);
            if (InstallTracker.get().getEntry(e.key) != null) {
                ensureInstallListener();
                InstallTracker.get().attach(e.key, installListener);
            }
        }
        refresh();
        if (loading != null) loading.setVisibility(View.VISIBLE);
        final android.content.Context context = getApplicationContext();
        executor.execute(() -> {
            try {
                InstallTracker.get().reconcileNow(context);
                DownloadTracker.get().detectInstalledPackages(context);
                syncMatchStore(context);
                List<DownloadTracker.Entry> entries = DownloadTracker.get().snapshot();
                runOnUiThread(() -> {
                    for (DownloadTracker.Entry e : entries) {
                        DownloadTracker.get().attach(e.key, globalListener);
                        if (InstallTracker.get().getEntry(e.key) != null) {
                            ensureInstallListener();
                            InstallTracker.get().attach(e.key, installListener);
                        }
                        if (e.state == DownloadTracker.State.COMPLETED && !e.installPrompted
                                && e.apkFile != null && e.apkFile.exists() && e.apkFile.length() > 0
                                && InstallTracker.get().getEntry(e.key) == null) {
                            DownloadTracker.get().markInstallPrompted(e.key);
                            handleDownloadedApk(e.key, e.apkFile);
                        }
                    }
                    refresh();
                    if (loading != null) loading.setVisibility(View.GONE);
                    loadingData = false;
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (loading != null) loading.setVisibility(View.GONE);
                    loadingData = false;
                    refresh();
                });
            }
        });
    }

    private void syncMatchStore(android.content.Context context) {
        for (DownloadTracker.Entry e : DownloadTracker.get().snapshot()) {
            if (e.state != DownloadTracker.State.INSTALLED) continue;
            String pkg = e.realPackageName != null ? e.realPackageName : e.key;
            try {
                android.content.pm.PackageInfo pi = context.getPackageManager().getPackageInfo(pkg, 0);
                for (MatchedApp m : MatchStore.get(context)) {
                    if (e.key.equals(m.getPackageName()) || pkg.equals(m.getPackageName())) {
                        final String oldKey = m.getPackageName();
                        m.setPackageName(pkg);
                        com.opensourcefinder.app.install.InstallState.refresh(context, m);
                        com.opensourcefinder.app.install.InstallState.markCurrentIfFromLatest(context, m, e);
                        try {
                            m.setAppLabel(context.getPackageManager().getApplicationLabel(pi.applicationInfo).toString());
                        } catch (Exception ignored) {}
                        MatchStore.upsertReplacing(context, m, oldKey, e.key);
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    @Override protected void onPause() {
        super.onPause();
        com.opensourcefinder.app.install.InstallEvents.removeListener(installEventsListener);

        for (DownloadTracker.Entry e : DownloadTracker.get().snapshot()) {
            DownloadTracker.get().detach(e.key, globalListener);
        }
    }

    private void handleDownloadedApk(String key, java.io.File apk) {
        boolean notInstalled = true;
        try { getPackageManager().getPackageInfo(key, 0); notInstalled = false; } catch (Exception ignored) {}
        if (!notInstalled && !UpdateManager.signaturesMatch(this, apk, key)) {
            showIncompatibleVersionDialog(key, apk);
            return;
        }
        ensureInstallListener();
        UpdateManager.installApk(this, key, apk, installListener);
        refresh();
    }

    private void showIncompatibleVersionDialog(String key, java.io.File apk) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.incompatible_version_title))
                .setMessage(getString(R.string.incompatible_version_message))
                .setCancelable(false)
                .setNegativeButton(getString(R.string.cancel), (d, w) -> refresh())
                .setPositiveButton(getString(R.string.reinstall_confirm), (d, w) -> {
                    pendingReinstallApk = apk;
                    pendingReinstallKey = key;
                    DownloadTracker.get().markPendingReinstall(key);
                    UpdateManager.uninstall(this, key, UNINSTALL_REQUEST);
                })
                .show();
    }

    private void ensureInstallListener() {
        if (installListener != null) return;
        installListener = entry -> runOnUiThread(() -> {
            switch (entry.state) {
                case INSTALLED:
                    DownloadTracker.get().markInstalled(entry.key);
                    InstallTracker.get().clear(entry.key);
                    refresh();
                    break;
                case CANCELLED:
                    InstallTracker.get().clear(entry.key);
                    refresh();
                    break;
                case FAILED_INCOMPATIBLE: {
                    java.io.File apk = entry.apkFile;
                    String key = entry.key;
                    InstallTracker.get().clear(entry.key);
                    refresh();
                    if (apk != null) showIncompatibleVersionDialog(key, apk);
                    break;
                }
                case FAILED:
                    InstallTracker.get().clear(entry.key);
                    refresh();
                    android.widget.Toast.makeText(this, R.string.install_failed, android.widget.Toast.LENGTH_LONG).show();
                    break;
                default:
                    refresh();
                    break;
            }
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UNINSTALL_REQUEST && pendingReinstallApk != null) {
            java.io.File f = pendingReinstallApk;
            String key = pendingReinstallKey;
            pendingReinstallApk = null;
            pendingReinstallKey = null;
            new android.os.Handler(getMainLooper()).postDelayed(() -> {
                if (f.exists()) handleDownloadedApk(key, f);
            }, 700);
        }
    }

    private void refresh() {
        List<DownloadTracker.Entry> list = DownloadTracker.get().snapshot();
        adapter.submit(list);
        if (downloadCount != null) downloadCount.setText(getString(R.string.downloads_count, list.size()));
        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void confirmDelete(DownloadTracker.Entry entry) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_download_title)
                .setMessage(R.string.delete_download_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_download, (dialog, which) -> {
                    boolean owned = entry.homePlaceholderOwned;
                    if (DownloadTracker.get().remove(entry.key)) {
                        if (owned) MatchStore.removeIfNeverInstalled(this, entry.key);
                        refresh();
                        android.widget.Toast.makeText(this, R.string.download_deleted, android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private final Map<String, Drawable> iconCache = new HashMap<>();

    private Drawable resolveIcon(DownloadTracker.Entry e) {
        Drawable cached = iconCache.get(e.key);
        if (cached != null) return cached;
        PackageManager pm = getPackageManager();
        String pkg = e.realPackageName != null ? e.realPackageName : e.key;
        try {
            Drawable d = pm.getApplicationIcon(pkg);
            iconCache.put(e.key, d);
            return d;
        } catch (Exception ignored) {}
        if (e.apkFile != null && e.apkFile.exists()) {
            try {
                PackageInfo pi = pm.getPackageArchiveInfo(e.apkFile.getAbsolutePath(), 0);
                if (pi != null && pi.applicationInfo != null) {
                    pi.applicationInfo.sourceDir = e.apkFile.getAbsolutePath();
                    pi.applicationInfo.publicSourceDir = e.apkFile.getAbsolutePath();
                    Drawable d = pi.applicationInfo.loadIcon(pm);
                    iconCache.put(e.key, d);
                    return d;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void bindIcon(DownloadTracker.Entry e, ImageView target) {
        Drawable local = resolveIcon(e);
        if (local != null) {
            com.bumptech.glide.Glide.with(target).clear(target);
            target.setImageDrawable(local);
        } else if (e.iconUrl != null) {
            IconLoader.load(target, e.iconUrl);
        } else {
            com.bumptech.glide.Glide.with(target).clear(target);
            target.setImageResource(TablerIconRes.get(DownloadsActivity.this, "ic_download"));
        }
    }

    private int themeColor(String attrName) {
        int attrId = getResources().getIdentifier(attrName, "attr", getPackageName());
        android.util.TypedValue tv = new android.util.TypedValue();
        if (attrId != 0) getTheme().resolveAttribute(attrId, tv, true);
        return tv.data;
    }

    private class DownloadsAdapter extends RecyclerView.Adapter<DownloadsAdapter.VH> {
        private final List<DownloadTracker.Entry> items = new ArrayList<>();
        void submit(List<DownloadTracker.Entry> data) {
            items.clear(); items.addAll(data); notifyDataSetChanged();
        }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_download, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            DownloadTracker.Entry e = items.get(position);
            h.title.setText(e.fileName != null ? e.fileName : e.key);
            h.cancel.setVisibility(View.GONE);
            h.action.setVisibility(View.GONE);
            h.action.setEnabled(true);
            h.percent.setVisibility(View.GONE);
            h.ring.setVisibility(View.VISIBLE);
            h.ring.setIndicatorColor(themeColor("colorPrimary"));
            h.cancel.setIconTint(android.content.res.ColorStateList.valueOf(
                    themeColor("colorError")));
            setStatusChipTone(h, themeColor("colorSurfaceContainerHighest"), themeColor("colorOnSurfaceVariant"));
            h.itemView.setOnLongClickListener(null);
            h.itemView.setLongClickable(false);

            bindIcon(e, h.icon);

            switch (e.state) {
                case DOWNLOADING:
                    h.status.setText(getString(R.string.download_status));
                    h.ring.setIndeterminate(false);
                    h.ring.setProgressCompat(e.progress, true);
                    h.percent.setVisibility(View.VISIBLE);
                    h.percent.setText(getString(R.string.scan_progress_percent, e.progress));
                    h.action.setVisibility(View.VISIBLE);
                    h.action.setIconResource(TablerIconRes.get(DownloadsActivity.this, "ic_player_pause"));
                    h.action.setContentDescription(getString(R.string.stop_download));
                    h.action.setOnClickListener(v -> DownloadTracker.get().stop(e.key));
                    h.cancel.setVisibility(View.VISIBLE);
                    h.cancel.setIconResource(TablerIconRes.get(DownloadsActivity.this, "ic_x"));
                    h.cancel.setContentDescription(getString(R.string.cancel));
                    h.cancel.setOnClickListener(v -> { boolean owned = e.homePlaceholderOwned; DownloadTracker.get().cancel(e.key); if (owned) MatchStore.removeIfNeverInstalled(DownloadsActivity.this, e.key); refresh(); });
                    break;
                case STOPPED:
                    h.status.setText(getString(R.string.download_stopped));
                    h.ring.setIndeterminate(false);
                    h.ring.setIndicatorColor(themeColor("colorOnSurfaceVariant"));
                    h.ring.setProgressCompat(e.progress, false);
                    h.percent.setVisibility(View.VISIBLE);
                    h.percent.setText(getString(R.string.scan_progress_percent, e.progress));
                    h.action.setVisibility(View.VISIBLE);
                    h.action.setIconResource(TablerIconRes.get(DownloadsActivity.this, "ic_player_play"));
                    h.action.setContentDescription(getString(R.string.resume_download));
                    h.action.setOnClickListener(v -> DownloadTracker.get().start(DownloadsActivity.this, e.key, e.url, e.fileName, null));
                    h.cancel.setVisibility(View.VISIBLE);
                    h.cancel.setIconResource(TablerIconRes.get(DownloadsActivity.this, "ic_x"));
                    h.cancel.setContentDescription(getString(R.string.cancel));
                    h.cancel.setOnClickListener(v -> { boolean owned = e.homePlaceholderOwned; DownloadTracker.get().cancel(e.key); if (owned) MatchStore.removeIfNeverInstalled(DownloadsActivity.this, e.key); refresh(); });
                    break;
                case COMPLETED: {
                    InstallTracker.Entry ie = InstallTracker.get().getEntry(e.key);
                    boolean installing = ie != null && ie.state == InstallTracker.State.INSTALLING;
                    boolean awaitingConfirm = ie != null && ie.state == InstallTracker.State.AWAITING_CONFIRM;
                    if (installing) {
                        h.status.setText(getString(ie.firstInstall ? R.string.installing_progress : R.string.updating_progress));
                    } else if (awaitingConfirm) {
                        h.status.setText(getString(R.string.waiting_install_confirmation));
                    } else {
                        h.status.setText(e.installPrompted ? getString(R.string.waiting_install_confirmation) : getString(R.string.download_complete));
                    }
                    setStatusChipTone(h, themeColor("colorPrimaryContainer"), themeColor("colorOnPrimaryContainer"));
                    h.ring.setIndeterminate(false);
                    h.ring.setProgressCompat(100, false);
                    h.action.setVisibility(View.VISIBLE);
                    h.action.setEnabled(!installing && !awaitingConfirm);
                    h.action.setIconResource(TablerIconRes.get(DownloadsActivity.this, "ic_circle_check"));
                    h.action.setContentDescription(getString(R.string.confirm_install));
                    h.action.setOnClickListener(v -> {
                        if (e.apkFile != null) {
                            DownloadTracker.get().markInstallPrompted(e.key);
                            handleDownloadedApk(e.key, e.apkFile);
                        }
                    });
                    bindItemMenu(h, e, true, true);
                    break;
                }
                case INSTALLED: {
                    h.status.setText(getString(R.string.installed) + " ✓");
                    setStatusChipTone(h, themeColor("colorTertiaryContainer"), themeColor("colorOnTertiaryContainer"));
                    h.ring.setVisibility(View.GONE);

                    boolean keptApk = e.apkFile != null && e.apkFile.exists() && e.apkFile.length() > 0;
                    if (keptApk) {
                        h.action.setVisibility(View.VISIBLE);
                        h.action.setEnabled(true);
                        h.action.setIconResource(TablerIconRes.get(DownloadsActivity.this, "ic_download"));
                        h.action.setContentDescription(getString(R.string.export_apk));
                        h.action.setOnClickListener(v -> {
                            exportEntry = e;
                            exportLauncher.launch(e.fileName == null ? "app.apk" : e.fileName);
                        });
                    }
                    bindItemMenu(h, e, keptApk, true);
                    break;
                }
                case FAILED:
                    h.status.setText(getString(R.string.failed) + (e.error != null ? ": " + e.error : ""));
                    setStatusChipTone(h, themeColor("colorErrorContainer"), themeColor("colorOnErrorContainer"));
                    h.ring.setVisibility(View.GONE);
                    h.action.setVisibility(View.VISIBLE);
                    h.action.setIconResource(TablerIconRes.get(DownloadsActivity.this, "ic_restore"));
                    h.action.setContentDescription(getString(R.string.retry_download));
                    h.action.setOnClickListener(v -> DownloadTracker.get().start(DownloadsActivity.this, e.key, e.url, e.fileName, null));
                    bindItemMenu(h, e, false, true);
                    break;
                default:
                    h.status.setText("");
                    h.ring.setVisibility(View.GONE);
                    break;
            }
        }
        @Override public int getItemCount() { return items.size(); }
        private void setStatusChipTone(VH h, int background, int textColor) {
            h.statusChip.setCardBackgroundColor(background);
            h.status.setTextColor(textColor);
        }
        class VH extends RecyclerView.ViewHolder {
            TextView title, status, percent;
            ImageView icon;
            CircularProgressIndicator ring;
            MaterialButton action, cancel;
            com.google.android.material.card.MaterialCardView statusChip;
            VH(View v) {
                super(v);
                title = v.findViewById(R.id.downloadTitle);
                status = v.findViewById(R.id.downloadStatus);
                percent = v.findViewById(R.id.downloadPercent);
                icon = v.findViewById(R.id.downloadIcon);
                IconShapeHelper.apply(icon);
                ring = v.findViewById(R.id.downloadRing);
                action = v.findViewById(R.id.downloadAction);
                cancel = v.findViewById(R.id.downloadCancel);
                statusChip = v.findViewById(R.id.downloadStatusChip);
            }
        }

        private void bindItemMenu(VH h, DownloadTracker.Entry e, boolean canExport, boolean canDelete) {
            if (!canExport && !canDelete) {
                h.itemView.setOnLongClickListener(null);
                h.itemView.setLongClickable(false);
                return;
            }
            h.itemView.setLongClickable(true);
            h.itemView.setOnLongClickListener(v -> {
                DropdownPopup.Builder menu = DropdownPopup.builder(v).width(220);
                if (canExport) {
                    menu.action(getString(R.string.export_apk), TablerIconRes.get(DownloadsActivity.this, "ic_download"), 0, () -> {
                        if (e.apkFile != null) {
                            exportEntry = e;
                            exportLauncher.launch(e.fileName == null ? "app.apk" : e.fileName);
                        }
                    });
                }
                boolean apkOnly = canExport && e.state == DownloadTracker.State.INSTALLED
                        && e.apkFile != null && e.apkFile.exists();
                if (apkOnly) {
                    menu.action(getString(R.string.export_apk_to_folder), TablerIconRes.get(DownloadsActivity.this, "ic_download"), 0, () -> {
                        if (e.apkFile != null) {
                            exportEntry = e;
                            exportFolderLauncher.launch(null);
                        }
                    });
                    menu.action(getString(R.string.delete_apk_only), TablerIconRes.get(DownloadsActivity.this, "ic_trash"),
                            themeColor("colorError"), () -> confirmDeleteApk(e));
                }
                if (canDelete) {
                    menu.action(getString(R.string.delete_download), TablerIconRes.get(DownloadsActivity.this, "ic_trash"),
                            themeColor("colorError"), () -> confirmDelete(e));
                }
                menu.show();
                return true;
            });
        }
    }
    private void confirmDeleteApk(DownloadTracker.Entry entry) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_apk_only)
                .setMessage(R.string.delete_apk_only_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_apk_only, (dialog, which) -> {
                    if (DownloadTracker.get().deleteApkOnly(entry.key)) {
                        refresh();
                        android.widget.Toast.makeText(this, R.string.apk_deleted, android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void exportApkToFolder(Uri tree, DownloadTracker.Entry entry) {
        final android.content.Context app = getApplicationContext();
        if (app == null || entry.apkFile == null) return;
        String name = entry.fileName == null || entry.fileName.isEmpty() ? "app.apk" : entry.fileName;
        try {
            Uri parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,
                    android.provider.DocumentsContract.getTreeDocumentId(tree));
            Uri target = android.provider.DocumentsContract.createDocument(app.getContentResolver(), parent,
                    "application/vnd.android.package-archive", name);
            if (target == null) throw new IllegalStateException("Cannot create file");
            try (InputStream in = new java.io.FileInputStream(entry.apkFile); OutputStream out = app.getContentResolver().openOutputStream(target)) {
                if (out == null) throw new IllegalStateException("Cannot open file");
                byte[] buffer = new byte[32768]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            }
            runOnUiThread(() -> android.widget.Toast.makeText(app, R.string.apk_exported, android.widget.Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            runOnUiThread(() -> android.widget.Toast.makeText(app, R.string.apk_export_failed, android.widget.Toast.LENGTH_LONG).show());
        }
    }

    private void exportApk(Uri uri, DownloadTracker.Entry entry) {
        try (InputStream in = new java.io.FileInputStream(entry.apkFile); OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("Cannot open output");
            byte[] buffer = new byte[32768]; int n; while ((n=in.read(buffer))!=-1) out.write(buffer,0,n);
            runOnUiThread(() -> android.widget.Toast.makeText(this, R.string.apk_exported, android.widget.Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            runOnUiThread(() -> android.widget.Toast.makeText(this, R.string.apk_export_failed, android.widget.Toast.LENGTH_LONG).show());
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        loadingData = false;
        refreshHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }

}
