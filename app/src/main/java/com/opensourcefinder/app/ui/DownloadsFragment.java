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
import android.widget.Toast;
import android.net.Uri;
import android.content.Intent;
import java.io.InputStream;
import java.io.OutputStream;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
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

public class DownloadsFragment extends Fragment {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private DownloadsAdapter adapter;
    private View empty;
    private TextView downloadCount;
    private HeaderSearch downloadsHeader;
    private String downloadsQuery = "";
    private View loading;
    private ActivityResultLauncher<String> exportLauncher;
    private DownloadTracker.Entry exportEntry;
    private ActivityResultLauncher<Intent> uninstallLauncher;
    private InstallTracker.Listener installListener;
    private volatile boolean loadingData = false;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private boolean refreshQueued = false;
    private java.io.File pendingReinstallApk;
    private String pendingReinstallKey;
    private static final int MENU_SORT = 0x5101;
    private static final int MENU_CLEAR_FINISHED = 0x5102;

    private final DownloadTracker.Listener globalListener = new DownloadTracker.Listener() {
        @Override public void onProgress(DownloadTracker.Entry entry) { scheduleRefresh(); }
        @Override public void onCompleted(DownloadTracker.Entry entry) {
            ui(() -> {
                refresh();
                if (!entry.installPrompted && entry.apkFile != null
                        && entry.apkFile.exists() && entry.apkFile.length() > 0) {
                    DownloadTracker.get().markInstallPrompted(entry.key);
                    handleDownloadedApk(entry.key, entry.apkFile);
                }
            });
        }
        @Override public void onFailed(DownloadTracker.Entry entry) { ui(() -> refresh()); }
        @Override public void onCancelled(DownloadTracker.Entry entry) { ui(() -> refresh()); }
    };

    private void ui(Runnable action) {
        refreshHandler.post(() -> {
            if (isAdded() && getView() != null && adapter != null) action.run();
        });
    }

    private void scheduleRefresh() {
        if (refreshQueued) return;
        refreshQueued = true;
        refreshHandler.postDelayed(() -> {
            refreshQueued = false;
            if (isAdded() && adapter != null) refresh();
        }, 150L);
    }

    @Override public void onCreate(@Nullable android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        exportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/vnd.android.package-archive"), uri -> {
            if (uri == null || exportEntry == null || exportEntry.apkFile == null) return;
            final DownloadTracker.Entry entry = exportEntry;
            exportEntry = null;
            new Thread(() -> exportApk(uri, entry)).start();
        });
        uninstallLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (pendingReinstallApk == null) return;
            final java.io.File f = pendingReinstallApk;
            final String key = pendingReinstallKey;
            pendingReinstallApk = null;
            pendingReinstallKey = null;
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (isAdded() && f.exists()) handleDownloadedApk(key, f);
            }, 700);
        });
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_downloads, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle("");
        downloadsHeader = HeaderSearch.bind(view, R.drawable.ic_download, getString(R.string.downloads_title),
                R.string.downloads_search_hint, 140L, (text, immediate) -> {
                    downloadsQuery = text == null ? "" : text.trim();
                    refresh();
                });
        downloadsHeader.setSearchVisible(false);
        setupActionButtons(view);
        empty = view.findViewById(R.id.emptyView);
        downloadCount = view.findViewById(R.id.downloadCount);
        loading = view.findViewById(R.id.downloadsLoading);
        RecyclerView rv = view.findViewById(R.id.recyclerView);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setHasFixedSize(true);
        rv.setItemViewCacheSize(8);
        MotionHelper.setupListAnimator(rv);
        adapter = new DownloadsAdapter();
        rv.setAdapter(adapter);
        final androidx.recyclerview.widget.ItemTouchHelper[] helperRef = new androidx.recyclerview.widget.ItemTouchHelper[1];
        final SwipeToDeleteCallback swipeCallback = new SwipeToDeleteCallback(requireContext()) {
            @Override protected boolean canSwipe(@NonNull RecyclerView.ViewHolder holder) {
                DownloadTracker.Entry e = adapter.itemAt(holder.getBindingAdapterPosition());
                return e != null && e.state != DownloadTracker.State.DOWNLOADING;
            }

            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                int position = holder.getBindingAdapterPosition();
                DownloadTracker.Entry e = adapter.itemAt(position);
                if (e == null) { refresh(); return; }
                AppPreferences.performHaptic(holder.itemView);
                confirmDelete(e, () -> {

                    restore(holder);
                    if (position != RecyclerView.NO_POSITION) adapter.notifyItemChanged(position);
                    rv.post(() -> {
                        if (helperRef[0] == null || !isAdded()) return;
                        helperRef[0].attachToRecyclerView(null);
                        helperRef[0].attachToRecyclerView(rv);
                        rv.invalidate();
                    });
                });
            }
        };
        helperRef[0] = new androidx.recyclerview.widget.ItemTouchHelper(swipeCallback);
        helperRef[0].attachToRecyclerView(rv);
        DownloadTracker.get().addGlobalListener(globalListener);
    }

    @Override public void onDestroyView() {
        DownloadTracker.get().removeGlobalListener(globalListener);
        if (downloadsHeader != null) downloadsHeader.destroy();
        downloadsHeader = null;
        refreshHandler.removeCallbacksAndMessages(null);
        refreshQueued = false;
        super.onDestroyView();
    }

    @Override public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && adapter != null) refresh();
    }

    private void setupActionButtons(View view) {
        android.widget.ImageView sort = view.findViewById(R.id.downloadSortButton);
        android.widget.ImageView clearBtn = view.findViewById(R.id.downloadClearButton);
        TextView count = view.findViewById(R.id.downloadCount);
        if (sort != null) {
            sort.setImageResource(TablerIconRes.get(requireContext(), "ic_arrows_sort"));
            sort.setOnClickListener(v -> { AppPreferences.performHaptic(v); showSortDialog(v); });
        }
        if (clearBtn != null) {
            clearBtn.setImageResource(TablerIconRes.get(requireContext(), "ic_trash"));
            clearBtn.setOnClickListener(v -> { AppPreferences.performHaptic(v); confirmClearFinished(); });
        }
        android.widget.LinearLayout headerActions = downloadsHeader != null ? downloadsHeader.actions() : null;
        if (headerActions != null) {
            float d = getResources().getDisplayMetrics().density;
            if (count != null) {
                android.view.ViewGroup parent = (android.view.ViewGroup) count.getParent();
                if (parent != null) parent.removeView(count);
                count.setBackgroundResource(R.drawable.download_count_bg);
                count.setPadding((int) (12 * d), (int) (7 * d), (int) (12 * d), (int) (7 * d));
                android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMarginStart((int) (6 * d));
                count.setLayoutParams(lp);
                headerActions.addView(count);
            }
            moveCircleAction(headerActions, sort, d);
            moveCircleAction(headerActions, clearBtn, d);
        }
    }

    private void moveCircleAction(android.widget.LinearLayout target, View child, float density) {
        if (target == null || child == null) return;
        android.view.ViewGroup parent = (android.view.ViewGroup) child.getParent();
        if (parent != null) parent.removeView(child);
        child.setBackgroundResource(R.drawable.circle_avatar_bg);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                (int) (40 * density), (int) (40 * density));
        lp.setMarginStart((int) (6 * density));
        child.setLayoutParams(lp);
        target.addView(child);
    }

    private void setupToolbarMenu(MaterialToolbar toolbar) {
        android.view.Menu menu = toolbar.getMenu();
        menu.clear();
        android.view.MenuItem sort = menu.add(android.view.Menu.NONE, MENU_SORT, 0, R.string.sort_by);
        sort.setIcon(TablerIconRes.get(requireContext(), "ic_arrows_sort"));
        sort.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM);
        android.view.MenuItem clear = menu.add(android.view.Menu.NONE, MENU_CLEAR_FINISHED, 1, R.string.downloads_clear_finished);
        clear.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        MenuIconTintHelper.tint(menu, toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_SORT) { showSortDialog(toolbar); return true; }
            if (item.getItemId() == MENU_CLEAR_FINISHED) { confirmClearFinished(); return true; }
            return false;
        });
    }

    private void showSortDialog(View anchor) {
        final String[] keys = {"NEWEST", "OLDEST", "NAME", "STATUS"};
        String[] labels = {
                getString(R.string.downloads_sort_newest),
                getString(R.string.downloads_sort_oldest),
                getString(R.string.sort_name),
                getString(R.string.downloads_sort_status)
        };
        String current = AppPreferences.getDownloadsSort(requireContext());
        int checked = 0;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(current)) checked = i;
        DropdownPopup.show(anchor, labels, checked, which -> {
            AppPreferences.setDownloadsSort(requireContext(), keys[which]);
            refresh();
        }, null, false, null);
    }

    private void confirmClearFinished() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.downloads_clear_finished)
                .setMessage(R.string.downloads_clear_finished_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.downloads_clear_finished_confirm, (d, w) -> {
                    int removed = DownloadTracker.get().clearFinished();
                    refresh();
                    Toast.makeText(requireContext(), getString(R.string.downloads_cleared, removed), Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private List<DownloadTracker.Entry> sorted(List<DownloadTracker.Entry> input) {
        List<DownloadTracker.Entry> list = new ArrayList<>(input);
        String mode = AppPreferences.getDownloadsSort(requireContext());
        java.util.Comparator<DownloadTracker.Entry> byNewest =
                (a, b) -> Long.compare(b.createdAt, a.createdAt);
        java.util.Comparator<DownloadTracker.Entry> byName = (a, b) ->
                a.displayName().compareToIgnoreCase(b.displayName());
        switch (mode) {
            case "OLDEST":
                list.sort((a, b) -> Long.compare(a.createdAt, b.createdAt));
                break;
            case "NAME":
                list.sort(byName);
                break;
            case "STATUS":
                list.sort((a, b) -> {
                    int byState = Integer.compare(statusRank(a), statusRank(b));
                    return byState != 0 ? byState : byNewest.compare(a, b);
                });
                break;
            default:
                list.sort(byNewest);
                break;
        }
        return list;
    }

    private static int statusRank(DownloadTracker.Entry e) {
        switch (e.state) {
            case DOWNLOADING: return 0;
            case STOPPED: return 1;
            case COMPLETED: return 2;
            case FAILED: return 3;
            case INSTALLED: return 4;
            default: return 5;
        }
    }

    private final Runnable installEventsListener = () -> { if (isAdded()) refresh(); };

    @Override public void onPause() {
        com.opensourcefinder.app.install.InstallEvents.removeListener(installEventsListener);
        super.onPause();
    }

    @Override public void onResume() {
        super.onResume();
        com.opensourcefinder.app.install.InstallEvents.addListener(installEventsListener);
        if (!isAdded() || loadingData) return;
        loadingData = true;

        List<DownloadTracker.Entry> currentEntries = DownloadTracker.get().snapshot();
        for (DownloadTracker.Entry e : currentEntries) {
            if (InstallTracker.get().getEntry(e.key) != null) {
                ensureInstallListener();
                InstallTracker.get().attach(e.key, installListener);
            }
        }
        refresh();
        if (loading != null) loading.setVisibility(View.VISIBLE);
        final android.content.Context context = requireContext().getApplicationContext();
        executor.execute(() -> {
            try {
                InstallTracker.get().reconcileNow(context);
                DownloadTracker.get().detectInstalledPackages(context);
                syncMatchStore(context);
                List<DownloadTracker.Entry> entries = DownloadTracker.get().snapshot();
                ui(() -> {
                    for (DownloadTracker.Entry e : entries) {
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
            } catch (Exception ignored) {
                ui(() -> {
                    if (loading != null) loading.setVisibility(View.GONE);
                    refresh();
                });
            } finally {
                loadingData = false;
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
                        com.opensourcefinder.app.install.InstallState.markCurrentIfFromLatest(m, e);
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

    private void handleDownloadedApk(String key, java.io.File apk) {
        if (!isAdded()) return;
        boolean notInstalled = true;
        try { requireContext().getPackageManager().getPackageInfo(key, 0); notInstalled = false; } catch (Exception ignored) {}
        if (!notInstalled && !UpdateManager.signaturesMatch(requireContext(), apk, key)) {
            showIncompatibleVersionDialog(key, apk);
            return;
        }
        ensureInstallListener();
        UpdateManager.installApk(requireContext(), key, apk, installListener);
        refresh();
    }

    private void showIncompatibleVersionDialog(String key, java.io.File apk) {
        if (!isAdded()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.incompatible_version_title))
                .setMessage(getString(R.string.incompatible_version_message))
                .setCancelable(false)
                .setNegativeButton(getString(R.string.cancel), (d, w) -> refresh())
                .setPositiveButton(getString(R.string.reinstall_confirm), (d, w) -> {
                    pendingReinstallApk = apk;
                    pendingReinstallKey = key;
                    DownloadTracker.get().markPendingReinstall(key);
                    try {
                        uninstallLauncher.launch(UpdateManager.uninstallIntent(key));
                    } catch (Exception ex) {
                        UpdateManager.uninstall(requireContext(), key, 0);
                    }
                })
                .show();
    }

    private void ensureInstallListener() {
        if (installListener != null) return;
        installListener = entry -> {
            ui(() -> {
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
                        Toast.makeText(requireContext(), R.string.install_failed, Toast.LENGTH_LONG).show();
                        break;
                    default:
                        refresh();
                        break;
                }
            });
        };
    }

    private void refresh() {
        if (adapter == null || !isAdded()) return;
        List<DownloadTracker.Entry> list = sorted(DownloadTracker.get().snapshot());
        if (!downloadsQuery.isEmpty()) {
            String q = downloadsQuery.toLowerCase(java.util.Locale.ROOT);
            List<DownloadTracker.Entry> filtered = new ArrayList<>();
            for (DownloadTracker.Entry e : list) {
                String name = e.displayName() == null ? "" : e.displayName().toLowerCase(java.util.Locale.ROOT);
                String key = e.key == null ? "" : e.key.toLowerCase(java.util.Locale.ROOT);
                if (name.contains(q) || key.contains(q)) filtered.add(e);
            }
            list = filtered;
        }
        adapter.submit(list);
        if (downloadCount != null) downloadCount.setText(getString(R.string.downloads_count, list.size()));
        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void confirmDelete(DownloadTracker.Entry entry) {
        confirmDelete(entry, null);
    }

    private void confirmDelete(DownloadTracker.Entry entry, @Nullable Runnable onCancel) {
        final boolean[] confirmed = {false};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_download_title)
                .setMessage(R.string.delete_download_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_download, (dialog, which) -> {
                    confirmed[0] = true;
                    boolean owned = entry.homePlaceholderOwned;
                    if (entry.state == DownloadTracker.State.STOPPED) DownloadTracker.get().cancel(entry.key);
                    if (DownloadTracker.get().remove(entry.key) || DownloadTracker.get().getEntry(entry.key) == null) {
                        if (owned) MatchStore.removeIfNeverInstalled(requireContext(), entry.key);
                        refresh();
                        Toast.makeText(requireContext(), R.string.download_deleted, Toast.LENGTH_SHORT).show();
                    }
                })
                .setOnDismissListener(d -> {
                    if (!confirmed[0] && onCancel != null) onCancel.run();
                })
                .show();
    }

    private final Map<String, Drawable> iconCache = new HashMap<>();

    private Drawable resolveIcon(DownloadTracker.Entry e) {
        Drawable cached = iconCache.get(e.key);
        if (cached != null) return cached;
        PackageManager pm = requireContext().getPackageManager();
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
            target.setImageResource(TablerIconRes.get(requireContext(), "ic_download"));
        }
    }

    private int themeColor(String attrName) {
        android.content.Context ctx = requireContext();
        int attrId = ctx.getResources().getIdentifier(attrName, "attr", ctx.getPackageName());
        android.util.TypedValue tv = new android.util.TypedValue();
        if (attrId != 0) ctx.getTheme().resolveAttribute(attrId, tv, true);
        return tv.data;
    }

    private class DownloadsAdapter extends RecyclerView.Adapter<DownloadsAdapter.VH> {
        private final List<DownloadTracker.Entry> items = new ArrayList<>();

        private final List<String> itemSignatures = new ArrayList<>();

        @Nullable DownloadTracker.Entry itemAt(int position) {
            return position >= 0 && position < items.size() ? items.get(position) : null;
        }

        void submit(List<DownloadTracker.Entry> data) {
            final List<DownloadTracker.Entry> old = new ArrayList<>(items);
            final List<String> oldSignatures = new ArrayList<>(itemSignatures);
            final List<DownloadTracker.Entry> next = new ArrayList<>(data);
            final List<String> nextSignatures = new ArrayList<>(next.size());
            for (DownloadTracker.Entry e : next) nextSignatures.add(contentSignature(e));
            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return old.size(); }
                @Override public int getNewListSize() { return next.size(); }
                @Override public boolean areItemsTheSame(int oldPos, int newPos) {
                    return old.get(oldPos).key.equals(next.get(newPos).key);
                }
                @Override public boolean areContentsTheSame(int oldPos, int newPos) {
                    return oldSignatures.get(oldPos).equals(nextSignatures.get(newPos));
                }
            });
            items.clear();
            items.addAll(next);
            itemSignatures.clear();
            itemSignatures.addAll(nextSignatures);
            diff.dispatchUpdatesTo(this);
        }

        private String contentSignature(DownloadTracker.Entry e) {
            InstallTracker.Entry ie = InstallTracker.get().getEntry(e.key);
            return e.state + "|" + e.progress + "|" + e.installPrompted + "|" + (ie != null ? ie.state : "NONE");
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
                    setRingProgress(h.ring, e.key, e.progress, true);
                    h.percent.setVisibility(View.VISIBLE);
                    h.percent.setText(getString(R.string.scan_progress_percent, e.progress));
                    h.action.setVisibility(View.VISIBLE);
                    h.action.setIconResource(TablerIconRes.get(requireContext(), "ic_player_pause"));
                    h.action.setContentDescription(getString(R.string.stop_download));
                    h.action.setOnClickListener(v -> DownloadTracker.get().stop(e.key));
                    h.cancel.setVisibility(View.VISIBLE);
                    h.cancel.setIconResource(TablerIconRes.get(requireContext(), "ic_x"));
                    h.cancel.setContentDescription(getString(R.string.cancel));
                    h.cancel.setOnClickListener(v -> { boolean owned = e.homePlaceholderOwned; DownloadTracker.get().cancel(e.key); if (owned) MatchStore.removeIfNeverInstalled(requireContext(), e.key); refresh(); });
                    break;
                case STOPPED:
                    h.status.setText(getString(R.string.download_stopped));
                    h.ring.setIndeterminate(false);
                    h.ring.setIndicatorColor(themeColor("colorOnSurfaceVariant"));
                    setRingProgress(h.ring, e.key, e.progress, false);
                    h.percent.setVisibility(View.VISIBLE);
                    h.percent.setText(getString(R.string.scan_progress_percent, e.progress));
                    h.action.setVisibility(View.VISIBLE);
                    h.action.setIconResource(TablerIconRes.get(requireContext(), "ic_player_play"));
                    h.action.setContentDescription(getString(R.string.resume_download));
                    h.action.setOnClickListener(v -> DownloadTracker.get().start(requireContext(), e.key, e.url, e.fileName, null));
                    h.cancel.setVisibility(View.VISIBLE);
                    h.cancel.setIconResource(TablerIconRes.get(requireContext(), "ic_x"));
                    h.cancel.setContentDescription(getString(R.string.cancel));
                    h.cancel.setOnClickListener(v -> { boolean owned = e.homePlaceholderOwned; DownloadTracker.get().cancel(e.key); if (owned) MatchStore.removeIfNeverInstalled(requireContext(), e.key); refresh(); });
                    break;
                case COMPLETED: {
                    InstallTracker.Entry ie = InstallTracker.get().getEntry(e.key);
                    boolean installing = ie != null && ie.state == InstallTracker.State.INSTALLING;
                    boolean awaitingConfirm = ie != null && ie.state == InstallTracker.State.AWAITING_CONFIRM;
                    if (installing) {
                        h.status.setText(getString(R.string.installing));
                    } else if (awaitingConfirm) {
                        h.status.setText(getString(R.string.waiting_install_confirmation));
                    } else {
                        h.status.setText(e.installPrompted ? getString(R.string.waiting_install_confirmation) : getString(R.string.download_complete));
                    }
                    setStatusChipTone(h, themeColor("colorPrimaryContainer"), themeColor("colorOnPrimaryContainer"));
                    h.ring.setIndeterminate(false);
                    h.ring.setProgressCompat(100, false);
                    h.action.setVisibility(View.VISIBLE);

                    h.action.setEnabled(true);
                    h.action.setIconResource(TablerIconRes.get(requireContext(), "ic_circle_check"));
                    h.action.setContentDescription(getString((installing || awaitingConfirm)
                            ? R.string.resume_download : R.string.confirm_install));
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
                    setStatusChipTone(h, themeColor("colorPrimaryContainer"), themeColor("colorOnPrimaryContainer"));
                    h.ring.setVisibility(View.GONE);

                    boolean keptApk = e.apkFile != null && e.apkFile.exists() && e.apkFile.length() > 0;
                    if (keptApk) {
                        h.action.setVisibility(View.VISIBLE);
                        h.action.setEnabled(true);
                        h.action.setIconResource(TablerIconRes.get(requireContext(), "ic_download"));
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
                    h.action.setIconResource(TablerIconRes.get(requireContext(), "ic_restore"));
                    h.action.setContentDescription(getString(R.string.retry_download));
                    h.action.setOnClickListener(v -> DownloadTracker.get().start(requireContext(), e.key, e.url, e.fileName, null));
                    bindItemMenu(h, e, false, true);
                    break;
                default:
                    h.status.setText("");
                    h.ring.setVisibility(View.GONE);
                    break;
            }
        }
        @Override public int getItemCount() { return items.size(); }
        private void setRingProgress(CircularProgressIndicator ring, String key, int progress, boolean animated) {
            String tag = key + ":" + progress + ":" + animated;
            if (tag.equals(ring.getTag())) return;
            ring.setProgressCompat(progress, animated);
            ring.setTag(tag);
        }
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
                    menu.action(getString(R.string.export_apk), TablerIconRes.get(requireContext(), "ic_download"), 0, () -> {
                        if (e.apkFile != null) {
                            exportEntry = e;
                            exportLauncher.launch(e.fileName == null ? "app.apk" : e.fileName);
                        }
                    });
                }
                if (canDelete) {
                    menu.action(getString(R.string.delete_download), TablerIconRes.get(requireContext(), "ic_trash"),
                            themeColor("colorError"), () -> confirmDelete(e));
                }
                menu.show();
                return true;
            });
        }
    }
    private void exportApk(Uri uri, DownloadTracker.Entry entry) {
        final android.content.Context app = getContext() == null ? null : getContext().getApplicationContext();
        if (app == null || entry.apkFile == null) return;
        try (InputStream in = new java.io.FileInputStream(entry.apkFile); OutputStream out = app.getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("Cannot open file");
            byte[] buffer = new byte[32768]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            refreshHandler.post(() -> Toast.makeText(app, R.string.apk_exported, Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            refreshHandler.post(() -> Toast.makeText(app, R.string.apk_export_failed, Toast.LENGTH_LONG).show());
        }
    }

    @Override public void onDestroy() {
        super.onDestroy();
        loadingData = false;
        refreshHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }

}
