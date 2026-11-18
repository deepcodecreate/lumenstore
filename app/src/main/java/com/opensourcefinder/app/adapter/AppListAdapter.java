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

package com.opensourcefinder.app.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.download.DownloadTracker;
import com.opensourcefinder.app.model.MatchedApp;
import com.opensourcefinder.app.ui.IconShapeHelper;
import com.opensourcefinder.app.ui.ShapedIconView;
import com.opensourcefinder.app.ui.TablerIconRes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.AppViewHolder> {

    public interface OnAppClickListener {
        void onAppClick(MatchedApp matchedApp);
    }

    public interface OnAppActionListener {
        void onPrimaryAction(MatchedApp matchedApp);
        default void onLongPress(MatchedApp matchedApp, View itemView) {}
    }

    public static final Object PAYLOAD_DOWNLOAD = new Object();

    private static final ExecutorService ICON_EXECUTOR = Executors.newFixedThreadPool(2);
    private static final DiffUtil.ItemCallback<MatchedApp> DIFF_CALLBACK = new DiffUtil.ItemCallback<MatchedApp>() {
        @Override public boolean areItemsTheSame(@NonNull MatchedApp oldItem, @NonNull MatchedApp newItem) {
            return Objects.equals(oldItem.getStoreKey(), newItem.getStoreKey());
        }

        @Override public boolean areContentsTheSame(@NonNull MatchedApp oldItem, @NonNull MatchedApp newItem) {
            return Objects.equals(oldItem.getAppLabel(), newItem.getAppLabel())
                    && Objects.equals(oldItem.getInstalledVersionName(), newItem.getInstalledVersionName())
                    && Objects.equals(oldItem.getLatestTag(), newItem.getLatestTag())
                    && Objects.equals(oldItem.getSourceType(), newItem.getSourceType())
                    && Objects.equals(oldItem.getSourceUrl(), newItem.getSourceUrl())
                    && Objects.equals(oldItem.getDownloadUrl(), newItem.getDownloadUrl())
                    && oldItem.getLatestVersionCode() == newItem.getLatestVersionCode()
                    && oldItem.isUpdateAvailable() == newItem.isUpdateAvailable()
                    && oldItem.isNotInstalled() == newItem.isNotInstalled()
                    && Objects.equals(oldItem.getDescription(), newItem.getDescription());
        }
    };
    private final AsyncListDiffer<MatchedApp> differ = new AsyncListDiffer<>(this, DIFF_CALLBACK);
    private final Map<String, Drawable> iconCache;
    private final OnAppClickListener listener;
    private final OnAppActionListener actionListener;

    private boolean colorsReady;
    private int cPrimary, cOnPrimary, cPrimaryContainer, cOnPrimaryContainer, cSurfaceHigh, cSurface, cOnSurfaceVariant;
    private float strokeWidthPx;

    private void ensureColors(Context context) {
        if (colorsReady) return;
        cPrimary = resolveAttr(context, androidx.appcompat.R.attr.colorPrimary);
        cOnPrimary = resolveAttr(context, com.google.android.material.R.attr.colorOnPrimary);
        cPrimaryContainer = resolveAttr(context, com.google.android.material.R.attr.colorPrimaryContainer);
        cOnPrimaryContainer = resolveAttr(context, com.google.android.material.R.attr.colorOnPrimaryContainer);
        cSurfaceHigh = resolveAttr(context, com.google.android.material.R.attr.colorSurfaceContainerHigh);
        cSurface = resolveAttr(context, com.google.android.material.R.attr.colorSurfaceContainer);
        cOnSurfaceVariant = resolveAttr(context, com.google.android.material.R.attr.colorOnSurfaceVariant);
        strokeWidthPx = 1.5f * context.getResources().getDisplayMetrics().density;
        colorsReady = true;
    }

    public AppListAdapter(Map<String, Drawable> iconCache, OnAppClickListener listener) {
        this(iconCache, listener, null);
    }

    public AppListAdapter(Map<String, Drawable> iconCache, OnAppClickListener listener, OnAppActionListener actionListener) {
        this.iconCache = iconCache;
        this.listener = listener;
        this.actionListener = actionListener;
    }

    public void submitList(List<MatchedApp> next) {
        differ.submitList(new ArrayList<>(next == null ? java.util.Collections.emptyList() : next));
    }

    public void submitList(List<MatchedApp> next, Runnable committed) {
        differ.submitList(new ArrayList<>(next == null ? java.util.Collections.emptyList() : next), committed);
    }

    public List<MatchedApp> getCurrentList() {
        return differ.getCurrentList();
    }

    public void removeByPackage(String packageName) {
        if (packageName == null) return;
        List<MatchedApp> next = new ArrayList<>(differ.getCurrentList());
        for (int i = 0; i < next.size(); i++) {
            if (packageName.equals(next.get(i).getPackageName())) {
                next.remove(i);
                submitList(next);
                return;
            }
        }
    }

    public void removeByStoreKey(String storeKey) {
        if (storeKey == null) return;
        List<MatchedApp> next = new ArrayList<>(differ.getCurrentList());
        for (int i = 0; i < next.size(); i++) {
            if (storeKey.equals(next.get(i).getStoreKey())) {
                next.remove(i);
                submitList(next);
                return;
            }
        }
    }

    public void notifyDownloadChanged(String packageName) {
        if (packageName == null) return;
        List<MatchedApp> list = differ.getCurrentList();
        for (int i = 0; i < list.size(); i++) {
            if (packageName.equals(list.get(i).getPackageName())) {
                notifyItemChanged(i, PAYLOAD_DOWNLOAD);
            }
        }
    }

    public void addItem(MatchedApp matchedApp) {
        List<MatchedApp> next = new ArrayList<>(differ.getCurrentList());
        next.add(matchedApp);
        submitList(next);
    }

    public void addOrReplace(MatchedApp matchedApp) {
        List<MatchedApp> next = new ArrayList<>(differ.getCurrentList());
        for (int i = 0; i < next.size(); i++) {
            if (Objects.equals(next.get(i).getStoreKey(), matchedApp.getStoreKey())) {
                next.set(i, matchedApp);
                submitList(next);
                return;
            }
        }
        next.add(matchedApp);
        submitList(next);
    }

    public void clear() {
        submitList(java.util.Collections.emptyList());
    }

    public boolean isEmpty() {
        return differ.getCurrentList().isEmpty();
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        AppViewHolder holder = new AppViewHolder(view);
        view.setOnClickListener(v -> {
            MatchedApp app = getItemAt(holder.getBindingAdapterPosition());
            if (app != null && listener != null) listener.onAppClick(app);
        });
        view.setOnLongClickListener(v -> {
            MatchedApp app = getItemAt(holder.getBindingAdapterPosition());
            if (app == null || actionListener == null) return false;
            actionListener.onLongPress(app, v);
            return true;
        });
        holder.statusAction.setOnClickListener(v -> {
            MatchedApp app = getItemAt(holder.getBindingAdapterPosition());
            if (app != null && actionListener != null) actionListener.onPrimaryAction(app);
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_DOWNLOAD)) {
            MatchedApp app = getItemAt(position);
            if (app != null) bindAction(holder, app);
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        MatchedApp matchedApp = differ.getCurrentList().get(position);
        Context context = holder.itemView.getContext();
        holder.appName.setText(matchedApp.getAppLabel());
        CharSequence subtitle;
        if (matchedApp.getRepo() != null) {
            subtitle = matchedApp.getRepo().getFullName();
        } else if (matchedApp.isLumenSource()) {
            subtitle = context.getString(R.string.source_lumen_store);
        } else if (matchedApp.isLazyIndexSource()) {
            subtitle = context.getString(R.string.source_izzyondroid);
        } else if ("fdroid".equals(matchedApp.getSourceType())) {
            subtitle = context.getString(R.string.source_fdroid);
        } else if ("play".equals(matchedApp.getSourceType())) {
            subtitle = context.getString(R.string.source_play);
        } else {
            subtitle = context.getString(R.string.source_manual);
        }
        holder.repoFullName.setText(subtitle);
        bindVersion(holder, matchedApp, context);

        bindIcon(holder, matchedApp, context);
        bindAction(holder, matchedApp);
    }

    private void bindVersion(AppViewHolder holder, MatchedApp app, Context context) {
        String installed = app.getInstalledVersionName();
        String latest = app.getLatestTag();
        boolean hasInstalled = installed != null && !installed.isEmpty();
        boolean hasLatest = latest != null && !latest.isEmpty();
        boolean pending = !app.isNotInstalled() && app.isUpdateAvailable();

        CharSequence base = holder.repoFullName.getText();
        String extra = null;
        if (app.isNotInstalled() && hasLatest) {
            extra = context.getString(R.string.last_label, latest);
        } else if (pending && hasLatest && hasInstalled) {
            extra = installed + " \u2192 " + latest;
        } else if (hasInstalled) {
            extra = installed;
        }
        if (extra != null) holder.repoFullName.setText(base + " \u00B7 " + extra);

        MaterialCardView card = holder.card;
        ensureColors(context);
        if (pending) {
            card.setCardBackgroundColor(cSurfaceHigh);
            card.setStrokeColor(ColorStateList.valueOf((cPrimary & 0x00FFFFFF) | 0x66000000));
            card.setStrokeWidth(Math.round(strokeWidthPx));
        } else {
            card.setCardBackgroundColor(cSurface);
            card.setStrokeWidth(0);
        }
    }

    private void bindIcon(AppViewHolder holder, MatchedApp matchedApp, Context context) {
        String packageName = matchedApp.getPackageName();
        holder.appIcon.setTag(packageName);
        holder.appIcon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        IconShapeHelper.apply(holder.appIcon);
        Drawable icon;
        synchronized (iconCache) {
            icon = iconCache.get(packageName);
        }
        if (icon != null) {
            holder.appIcon.setImageDrawable(icon);
        } else if (!matchedApp.isNotInstalled()) {
            holder.appIcon.setImageDrawable(com.opensourcefinder.app.ui.IconLoader.loading(context));
            Context appContext = context.getApplicationContext();
            ICON_EXECUTOR.execute(() -> {
                try {
                    Drawable loaded = appContext.getPackageManager().getApplicationIcon(packageName);
                    ShapedIconView.prewarm(loaded);
                    synchronized (iconCache) {
                        iconCache.put(packageName, loaded);
                    }
                    holder.appIcon.post(() -> {
                        if (Objects.equals(packageName, holder.appIcon.getTag())) {
                            holder.appIcon.setImageDrawable(loaded);
                        }
                    });
                } catch (Exception ignored) {}
            });
        } else if (matchedApp.getIconUrl() != null) {
            com.opensourcefinder.app.ui.IconLoader.load(holder.appIcon, matchedApp.getIconUrl());
        } else {
            holder.appIcon.setImageDrawable(com.opensourcefinder.app.ui.IconLoader.empty(context));
        }
    }

    private void bindAction(AppViewHolder holder, MatchedApp app) {
        Context context = holder.itemView.getContext();
        MaterialButton button = holder.statusAction;
        ensureColors(context);
        int primary = cPrimary, onPrimary = cOnPrimary;
        int primaryContainer = cPrimaryContainer, onPrimaryContainer = cOnPrimaryContainer;
        int surfaceHigh = cSurfaceHigh, onSurfaceVariant = cOnSurfaceVariant;

        String text;
        int bg;
        int fg;
        String iconName = null;
        boolean clickable;

        DownloadTracker.Entry dl = DownloadTracker.get().getEntry(app.getPackageName());
        if (dl != null && dl.state == DownloadTracker.State.DOWNLOADING) {
            text = context.getString(R.string.scan_progress_percent, dl.progress);
            bg = primaryContainer; fg = onPrimaryContainer; iconName = "ic_player_pause";
            clickable = true;
        } else if (dl != null && dl.state == DownloadTracker.State.STOPPED) {
            text = context.getString(R.string.resume_short);
            bg = primaryContainer; fg = onPrimaryContainer; iconName = "ic_player_play";
            clickable = true;
        } else if (dl != null && dl.state == DownloadTracker.State.COMPLETED
                && (app.isNotInstalled() || app.isUpdateAvailable())) {
            text = context.getString(R.string.install);
            bg = primary; fg = onPrimary; iconName = "ic_circle_check";
            clickable = true;
        } else if (app.isNotInstalled()) {
            text = context.getString(R.string.install);
            bg = primary; fg = onPrimary; iconName = "ic_download";
            clickable = true;
        } else if (app.isUpdateAvailable() && !installedFromLatest(dl, app)) {
            text = context.getString(R.string.update);
            bg = primary; fg = onPrimary; iconName = "ic_download";
            clickable = true;
        } else {
            text = context.getString(R.string.up_to_date);
            bg = surfaceHigh; fg = onSurfaceVariant;
            clickable = false;
        }

        button.setText(text);
        button.setContentDescription(text + " " + (app.getAppLabel() == null ? "" : app.getAppLabel()));
        button.setBackgroundTintList(ColorStateList.valueOf(bg));
        button.setTextColor(fg);
        if (iconName != null) {
            button.setIconResource(TablerIconRes.get(context, iconName));
            button.setIconTint(ColorStateList.valueOf(fg));
        } else {
            button.setIcon(null);
        }
        button.setClickable(clickable);
        button.setFocusable(clickable);
        button.setEnabled(true);
    }

    private static boolean installedFromLatest(DownloadTracker.Entry dl, MatchedApp app) {
        if (dl == null || dl.state != DownloadTracker.State.INSTALLED) return false;
        String tag = app.getLatestTag();
        if (tag == null || tag.isEmpty() || dl.fileName == null) return false;
        return dl.fileName.contains(tag);
    }

    public MatchedApp getItemAt(int position) {
        if (position < 0 || position >= differ.getCurrentList().size()) return null;
        return differ.getCurrentList().get(position);
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }

    private static int resolveAttr(Context context, int attrRes) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attrRes, typedValue, true);
        return typedValue.data;
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        final ImageView appIcon;
        final TextView appName;
        final TextView repoFullName;
        final MaterialCardView card;
        final MaterialButton statusAction;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.appIcon);
            if (appIcon instanceof ShapedIconView) {

                ((ShapedIconView) appIcon).setSpinEnabled(false);
            }
            appName = itemView.findViewById(R.id.appName);
            repoFullName = itemView.findViewById(R.id.repoFullName);
            card = (MaterialCardView) itemView;
            statusAction = itemView.findViewById(R.id.statusAction);
        }
    }
}
