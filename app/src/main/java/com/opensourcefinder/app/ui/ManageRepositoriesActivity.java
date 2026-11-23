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

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.opensourcefinder.app.MainActivity;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.model.MatchedApp;
import com.opensourcefinder.app.model.SourceRepository;
import com.opensourcefinder.app.scanner.MatchStore;
import com.opensourcefinder.app.scanner.RepositoryManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class ManageRepositoriesActivity extends BaseActivity {

    private static final String ALL = "all";
    private static final String GITHUB = "github";
    private static final String PLAY = "play";
    private static final String FDROID = "fdroid";
    private static final String OTHER = "other";
    private static final Pattern OWNER_REPO = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");

    private final List<SourceRepository> all = new ArrayList<>();
    private final List<SourceRepository> shown = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private final Map<String, Chip> chips = new HashMap<>();

    private MaterialToolbar toolbar;
    private RecyclerView recycler;
    private RepoAdapter adapter;
    private TextView countPill;
    private ChipGroup filterChips;
    private View emptyState;
    private TextView emptyTitle;
    private TextView emptyMessage;
    private ExtendedFloatingActionButton addFab;
    private MaterialCardView selectionBar;
    private TextView selectionCount;
    private String filter = ALL;
    private boolean selecting;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        MotionHelper.applyDistinctActivityTransition(this);
        setContentView(R.layout.activity_manage_repositories);
        MotionHelper.animateActivityContent(this);
        ThemeManager.applyWindow(this);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(TablerIconRes.get(this, "ic_arrow_back"));
        toolbar.setNavigationIconTint(MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface));
        ToolbarCircleHelper.apply(toolbar);
        toolbar.setNavigationOnClickListener(v -> {
            if (selecting) exitSelection(); else finish();
        });

        countPill = findViewById(R.id.repoCount);
        filterChips = findViewById(R.id.filterChips);
        emptyState = findViewById(R.id.emptyState);
        emptyTitle = findViewById(R.id.emptyTitle);
        emptyMessage = findViewById(R.id.emptyMessage);
        addFab = findViewById(R.id.addFab);
        selectionBar = findViewById(R.id.selectionBar);
        selectionCount = findViewById(R.id.selectionCount);
        recycler = findViewById(R.id.recyclerView);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RepoAdapter();
        recycler.setAdapter(adapter);
        MotionHelper.setupListAnimator(recycler);

        addFab.setOnClickListener(v -> { AppPreferences.performHaptic(v); showAddDialog(); });
        findViewById(R.id.selectionClose).setOnClickListener(v -> exitSelection());
        findViewById(R.id.selectionAll).setOnClickListener(v -> selectAllShown());
        findViewById(R.id.selectionDelete).setOnClickListener(v -> removeSelected());

        buildChips();
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_manage_repos, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_scan_now) {
            Intent home = new Intent(this, MainActivity.class);
            home.putExtra("rescan", true);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(home);
            return true;
        }
        if (id == R.id.action_remove_all) {
            if (all.isEmpty()) return true;
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.repos_remove_all_title)
                    .setMessage(R.string.repos_remove_all_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.repos_action_remove, (d, w) -> removeSources(new ArrayList<>(all)))
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (selecting) { exitSelection(); return; }
        super.onBackPressed();
    }

    private void load() {
        all.clear();
        all.addAll(RepositoryManager.get(this));

        Set<String> alive = new LinkedHashSet<>();
        for (SourceRepository s : all) alive.add(key(s));
        selected.retainAll(alive);
        if (selecting && selected.isEmpty()) setSelecting(false);
        updateChipCounts();
        applyFilter();
    }

    private void applyFilter() {
        shown.clear();
        for (SourceRepository s : all) {
            if (ALL.equals(filter) || filter.equals(typeOf(s))) shown.add(s);
        }
        countPill.setText(String.valueOf(all.size()));
        adapter.notifyDataSetChanged();
        boolean empty = shown.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        if (empty) {
            boolean none = all.isEmpty();
            emptyTitle.setText(none ? R.string.repos_empty_title : R.string.repos_empty_filtered_title);
            emptyMessage.setText(none ? R.string.no_manual_repositories : R.string.repos_empty_filtered);
        }
        updateSelectionUi();
    }

    private static String key(SourceRepository s) {
        return s.getUrl() == null ? "" : s.getUrl().toLowerCase(Locale.ROOT);
    }

    private static String typeOf(SourceRepository s) {
        String url = s.getUrl() == null ? "" : s.getUrl().toLowerCase(Locale.ROOT);
        if (url.contains("play.google.com")) return PLAY;
        if (url.contains("f-droid.org") || "fdroid".equalsIgnoreCase(s.getType())) return FDROID;
        if (url.contains("github.com") || "github".equalsIgnoreCase(s.getType())) return GITHUB;
        return OTHER;
    }

    private String typeLabel(String type) {
        switch (type) {
            case GITHUB: return getString(R.string.repos_filter_github);
            case PLAY: return getString(R.string.repos_filter_play);
            case FDROID: return getString(R.string.repos_filter_fdroid);
            default: return getString(R.string.repos_filter_other);
        }
    }

    private int typeIcon(String type) {
        switch (type) {
            case GITHUB: return TablerIconRes.get(this, "ic_nav_github");
            case PLAY: return TablerIconRes.get(this, "ic_player_play");
            case FDROID: return TablerIconRes.get(this, "ic_download");
            default: return TablerIconRes.get(this, "ic_lm_link");
        }
    }

    private String displayName(SourceRepository s) {
        if (s.getName() != null && !s.getName().trim().isEmpty()) return s.getName().trim();
        String url = s.getUrl() == null ? "" : s.getUrl();
        Uri uri = Uri.parse(url);
        String id = uri.getQueryParameter("id");
        if (id != null) return id;
        List<String> segs = uri.getPathSegments();
        if (typeOf(s).equals(GITHUB) && segs.size() >= 2) return segs.get(0) + "/" + segs.get(1);
        if (!segs.isEmpty()) return segs.get(segs.size() - 1);
        return url;
    }

    private void buildChips() {
        String[] types = {ALL, GITHUB, PLAY, FDROID, OTHER};
        for (final String type : types) {
            Chip chip = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
            chip.setId(View.generateViewId());
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChipStrokeWidth(0f);
            chip.setTag(type);
            chip.setOnClickListener(v -> AppPreferences.performHaptic(v));
            filterChips.addView(chip);
            chips.put(type, chip);
        }
        chips.get(ALL).setChecked(true);
        filterChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Chip c = group.findViewById(checkedIds.get(0));
            if (c == null) return;
            filter = (String) c.getTag();
            colorChips();
            applyFilter();
        });
        colorChips();
    }

    private void updateChipCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (SourceRepository s : all) {
            String t = typeOf(s);
            counts.put(t, (counts.containsKey(t) ? counts.get(t) : 0) + 1);
        }
        for (Map.Entry<String, Chip> e : chips.entrySet()) {
            String type = e.getKey();
            int n = ALL.equals(type) ? all.size() : (counts.containsKey(type) ? counts.get(type) : 0);
            String label = ALL.equals(type) ? getString(R.string.repos_filter_all) : typeLabel(type);
            e.getValue().setText(label + "  " + n);
            e.getValue().setVisibility(ALL.equals(type) || n > 0 || type.equals(filter) ? View.VISIBLE : View.GONE);
        }

        if (!ALL.equals(filter) && !counts.containsKey(filter)) {
            filter = ALL;
            chips.get(ALL).setChecked(true);
        }
        colorChips();
    }

    private void colorChips() {
        int primaryContainer = MaterialColors.getColor(filterChips, com.google.android.material.R.attr.colorPrimaryContainer);
        int onPrimaryContainer = MaterialColors.getColor(filterChips, com.google.android.material.R.attr.colorOnPrimaryContainer);
        int surface = MaterialColors.getColor(filterChips, com.google.android.material.R.attr.colorSurfaceContainerHigh);
        int onSurface = MaterialColors.getColor(filterChips, com.google.android.material.R.attr.colorOnSurface);
        for (Chip chip : chips.values()) {
            boolean on = chip.isChecked();
            chip.setChipBackgroundColor(ColorStateList.valueOf(on ? primaryContainer : surface));
            chip.setTextColor(on ? onPrimaryContainer : onSurface);
        }
    }

    private void setSelecting(boolean on) {
        if (selecting == on) return;
        selecting = on;
        if (!on) selected.clear();
        updateSelectionUi();
        adapter.notifyDataSetChanged();
    }

    private void exitSelection() {
        setSelecting(false);
    }

    private void toggleSelected(SourceRepository s) {
        String k = key(s);
        if (!selected.remove(k)) selected.add(k);
        if (selected.isEmpty()) { setSelecting(false); return; }
        updateSelectionUi();
        int index = shown.indexOf(s);
        if (index >= 0) adapter.notifyItemChanged(index);
    }

    private void selectAllShown() {
        AppPreferences.performHaptic(selectionBar);
        boolean everything = true;
        for (SourceRepository s : shown) if (!selected.contains(key(s))) everything = false;
        if (everything) selected.clear();
        else for (SourceRepository s : shown) selected.add(key(s));
        if (selected.isEmpty()) { setSelecting(false); return; }
        updateSelectionUi();
        adapter.notifyDataSetChanged();
    }

    private void updateSelectionUi() {
        boolean show = selecting && !selected.isEmpty();
        toolbar.setNavigationIcon(TablerIconRes.get(this, show ? "ic_x" : "ic_arrow_back"));
        toolbar.setTitle(show ? getString(R.string.repos_selected_count, selected.size()) : getString(R.string.manual_repositories));
        selectionCount.setText(getString(R.string.repos_selected_count, selected.size()));
        animateBar(show);
    }

    private void animateBar(boolean show) {
        boolean visible = selectionBar.getVisibility() == View.VISIBLE;
        if (show == visible) return;
        long d = MotionHelper.getDuration(this, 200, 320);
        float shift = 72f * getResources().getDisplayMetrics().density;
        selectionBar.animate().cancel();
        if (show) {
            selectionBar.setVisibility(View.VISIBLE);
            addFab.hide();
            if (d > 0) {
                selectionBar.setAlpha(0f);
                selectionBar.setTranslationY(shift);
                selectionBar.animate().alpha(1f).translationY(0f).setDuration(d)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f)).start();
            } else {
                selectionBar.setAlpha(1f);
                selectionBar.setTranslationY(0f);
            }
        } else {
            addFab.show();
            if (d > 0) {
                selectionBar.animate().alpha(0f).translationY(shift).setDuration((long) (d * 0.7f))
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> selectionBar.setVisibility(View.GONE)).start();
            } else {
                selectionBar.setVisibility(View.GONE);
            }
        }
    }

    private void showRowMenu(final SourceRepository s, View anchor) {
        final Context c = this;
        DropdownPopup.builder(anchor).width(220)
                .action(getString(R.string.repos_action_open), TablerIconRes.get(c, "ic_external_link"), 0, () -> {
                    if (!LinkHandlerActivity.openInBrowser(c, Uri.parse(s.getUrl()))) {
                        Toast.makeText(c, R.string.repos_no_browser, Toast.LENGTH_SHORT).show();
                    }
                })
                .action(getString(R.string.repos_action_copy), TablerIconRes.get(c, "ic_copy"), 0, () -> copyLink(s))
                .action(getString(R.string.repos_action_share), TablerIconRes.get(c, "ic_share"), 0, () -> {
                    Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s.getUrl());
                    startActivity(Intent.createChooser(send, null));
                })
                .action(getString(R.string.repos_action_releases), TablerIconRes.get(c, "ic_download"), 0, () ->
                        startActivity(new Intent(c, RepositoryReleasesActivity.class)
                                .putExtra(RepositoryReleasesActivity.EXTRA_URL, s.getUrl())))
                .action(getString(R.string.repos_action_rename), TablerIconRes.get(c, "ic_pencil"), 0, () -> showRenameDialog(s))
                .action(getString(R.string.repos_action_select), TablerIconRes.get(c, "ic_circle_check"), 0, () -> {
                    selected.add(key(s));
                    setSelecting(true);
                    adapter.notifyDataSetChanged();
                    updateSelectionUi();
                })
                .action(getString(R.string.repos_action_remove), TablerIconRes.get(c, "ic_trash"),
                        MaterialColors.getColor(anchor, androidx.appcompat.R.attr.colorError), () -> {
                            List<SourceRepository> one = new ArrayList<>();
                            one.add(s);
                            removeSources(one);
                        })
                .show();
    }

    private void copyLink(SourceRepository s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("link", s.getUrl()));
        Toast.makeText(this, R.string.repos_link_copied, Toast.LENGTH_SHORT).show();
    }

    private void showRenameDialog(final SourceRepository s) {
        final TextInputLayout layout = new TextInputLayout(this);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setHint(getString(R.string.repos_rename_hint));
        final TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setSingleLine(true);
        input.setText(displayName(s));
        input.setSelection(input.getText() == null ? 0 : input.getText().length());
        layout.addView(input);
        FrameLayout box = new FrameLayout(this);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(layout);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.repos_rename_title)
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    List<SourceRepository> list = RepositoryManager.get(this);
                    for (SourceRepository item : list) {
                        if (item.getUrl() != null && item.getUrl().equalsIgnoreCase(s.getUrl())) item.setName(name);
                    }
                    RepositoryManager.replaceAll(this, list);
                    load();
                })
                .show();
    }

    private void removeSelected() {
        if (selected.isEmpty()) return;
        List<SourceRepository> picked = new ArrayList<>();
        for (SourceRepository s : all) if (selected.contains(key(s))) picked.add(s);
        final int n = picked.size();
        if (n == 1) { removeSources(picked); return; }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.repos_remove_many_title, n))
                .setMessage(R.string.repos_remove_many_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.repos_action_remove, (d, w) -> removeSources(picked))
                .show();
    }

    private void removeSources(final List<SourceRepository> sources) {
        if (sources.isEmpty()) return;
        final Context app = getApplicationContext();
        final List<SourceRepository> removedSources = new ArrayList<>(sources);
        final List<MatchedApp> removedApps = new ArrayList<>();
        Set<String> urls = new LinkedHashSet<>();
        for (SourceRepository s : removedSources) urls.add(key(s));
        for (MatchedApp m : MatchStore.get(app)) {
            if (m != null && m.getSourceUrl() != null && urls.contains(m.getSourceUrl().toLowerCase(Locale.ROOT))) {
                removedApps.add(m);
            }
        }
        for (SourceRepository s : removedSources) RepositoryManager.remove(app, s.getUrl());
        for (MatchedApp m : removedApps) MatchStore.remove(app, m.getStoreKey());
        selected.clear();
        setSelecting(false);
        load();

        String message = removedSources.size() == 1
                ? getString(R.string.repos_removed_one, displayName(removedSources.get(0)))
                : getString(R.string.repos_removed_many, removedSources.size());
        Snackbar.make(findViewById(R.id.recyclerView), message, Snackbar.LENGTH_LONG)
                .setAnchorView(addFab)
                .setAction(R.string.repos_undo, v -> {
                    for (SourceRepository s : removedSources) RepositoryManager.add(app, s);
                    for (MatchedApp m : removedApps) MatchStore.upsert(app, m);
                    load();
                }).show();
    }

    private void showAddDialog() {
        final TextInputLayout layout = new TextInputLayout(this);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setHint(getString(R.string.repos_add_hint));
        layout.setHelperTextEnabled(true);
        final TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(input);

        final com.google.android.material.button.MaterialButton paste = new com.google.android.material.button.MaterialButton(
                this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        paste.setText(R.string.repos_paste);
        paste.setAllCaps(false);
        paste.setIconResource(R.drawable.ic_clipboard);
        LinearLayout.LayoutParams pasteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pasteLp.topMargin = (int) (4 * getResources().getDisplayMetrics().density);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(layout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(paste, pasteLp);

        final androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.repos_add_title)
                .setMessage(R.string.repos_add_message)
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.repos_add, null)
                .create();
        dialog.setOnShowListener(d -> {
            final android.widget.Button add = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            add.setEnabled(false);
            add.setOnClickListener(v -> {
                Uri link = parseLink(textOf(input));
                if (link == null) {
                    layout.setError(getString(R.string.repos_add_invalid));
                    return;
                }
                dialog.dismiss();

                startActivity(new Intent(this, LinkHandlerActivity.class).setData(link));
            });
            input.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
                @Override public void afterTextChanged(android.text.Editable e) {
                    String text = textOf(input);
                    if (text.isEmpty()) {
                        layout.setError(null);
                        layout.setHelperText(null);
                        add.setEnabled(false);
                        return;
                    }
                    Uri link = parseLink(text);
                    if (link == null) {
                        layout.setHelperText(null);
                        layout.setError(getString(R.string.repos_add_invalid));
                        add.setEnabled(false);
                    } else {
                        layout.setError(null);
                        layout.setHelperText(describeLink(link));
                        add.setEnabled(true);
                    }
                }
            });
            paste.setOnClickListener(v -> {
                String clip = readClipboard();
                if (clip.isEmpty()) {
                    Toast.makeText(this, R.string.repos_clipboard_empty, Toast.LENGTH_SHORT).show();
                    return;
                }
                input.setText(clip);
                input.setSelection(input.length());
            });
        });
        dialog.show();
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private String readClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null
                    || cm.getPrimaryClip().getItemCount() == 0) return "";
            CharSequence text = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            return text == null ? "" : text.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String describeLink(Uri link) {
        String host = link.getHost() == null ? "" : link.getHost().toLowerCase(Locale.ROOT);
        if (host.endsWith("github.com")) {
            List<String> seg = link.getPathSegments();
            return getString(R.string.repos_valid_github, seg.get(0) + "/" + stripGit(seg.get(1)));
        }
        if (host.endsWith("f-droid.org")) {
            return getString(R.string.repos_valid_fdroid, fdroidPackage(link));
        }
        return getString(R.string.repos_valid_play, link.getQueryParameter("id"));
    }

    private static final Set<String> RESERVED_OWNERS = new java.util.HashSet<>(java.util.Arrays.asList(
            "features", "settings", "topics", "orgs", "marketplace", "sponsors", "explore", "about",
            "login", "join", "pricing", "collections", "trending", "notifications", "search", "apps",
            "enterprise", "security", "pulls", "issues", "new", "organizations", "users", "events"));
    private static final Pattern GITHUB_OWNER = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?");
    private static final Pattern GITHUB_REPO = Pattern.compile("[A-Za-z0-9_.-]+");

    private static String stripGit(String repo) {
        return repo.toLowerCase(Locale.ROOT).endsWith(".git") ? repo.substring(0, repo.length() - 4) : repo;
    }

    @Nullable
    private static String fdroidPackage(Uri u) {
        List<String> seg = u.getPathSegments();
        for (int i = 0; i < seg.size() - 1; i++) {
            if ("packages".equals(seg.get(i))) {
                String pkg = seg.get(i + 1);
                return PACKAGE.matcher(pkg).matches() ? pkg : null;
            }
        }
        return null;
    }

    @Nullable
    private static Uri parseLink(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty() || text.matches(".*\\s.*")) return null;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("market://")) {
            String id = Uri.parse(text).getQueryParameter("id");
            return id != null && PACKAGE.matcher(id).matches()
                    ? Uri.parse("https://play.google.com/store/apps/details?id=" + id) : null;
        }
        boolean hasScheme = lower.startsWith("http://") || lower.startsWith("https://");
        boolean hostFirst = lower.startsWith("github.com/") || lower.startsWith("www.github.com/")
                || lower.startsWith("play.google.com/") || lower.startsWith("f-droid.org/")
                || lower.startsWith("www.f-droid.org/");
        if (hasScheme || hostFirst) {
            Uri u = Uri.parse(hasScheme ? text : "https://" + text);
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);
            List<String> seg = u.getPathSegments();
            switch (host) {
                case "github.com": {
                    if (seg.size() < 2) return null;
                    String owner = seg.get(0), repo = stripGit(seg.get(1));
                    if (!GITHUB_OWNER.matcher(owner).matches() || RESERVED_OWNERS.contains(owner.toLowerCase(Locale.ROOT))) return null;
                    if (repo.isEmpty() || repo.equals(".") || repo.equals("..") || !GITHUB_REPO.matcher(repo).matches()) return null;
                    return u;
                }
                case "play.google.com": {
                    if (seg.size() < 3 || !"store".equals(seg.get(0)) || !"apps".equals(seg.get(1))
                            || !"details".equals(seg.get(2))) return null;
                    String id = u.getQueryParameter("id");
                    return id != null && PACKAGE.matcher(id).matches() ? u : null;
                }
                case "f-droid.org":
                    return fdroidPackage(u) != null ? u : null;
                default:
                    return null;
            }
        }

        String[] parts = text.split("/");
        if (parts.length == 2 && GITHUB_OWNER.matcher(parts[0]).matches() && !RESERVED_OWNERS.contains(parts[0].toLowerCase(Locale.ROOT))
                && GITHUB_REPO.matcher(stripGit(parts[1])).matches() && !stripGit(parts[1]).isEmpty()) {
            return Uri.parse("https://github.com/" + parts[0] + "/" + stripGit(parts[1]));
        }
        if (PACKAGE.matcher(text).matches()) {
            return Uri.parse("https://play.google.com/store/apps/details?id=" + text);
        }
        return null;
    }

    private final class RepoAdapter extends RecyclerView.Adapter<RepoAdapter.Holder> {
        @NonNull
        @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_manage_repo, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull Holder h, int position) {
            h.bind(shown.get(position));
        }

        @Override public int getItemCount() { return shown.size(); }

        final class Holder extends RecyclerView.ViewHolder {
            private final MaterialCardView card;
            private final View iconBox;
            private final ImageView icon;
            private final TextView name;
            private final TextView url;
            private final TextView type;
            private final ImageButton more;

            Holder(@NonNull View v) {
                super(v);
                card = (MaterialCardView) v;
                iconBox = v.findViewById(R.id.repoIconBox);
                icon = v.findViewById(R.id.repoIcon);
                name = v.findViewById(R.id.repoName);
                url = v.findViewById(R.id.repoUrl);
                type = v.findViewById(R.id.repoType);
                more = v.findViewById(R.id.repoMore);
            }

            void bind(final SourceRepository s) {
                String t = typeOf(s);
                boolean isSelected = selected.contains(key(s));
                name.setText(displayName(s));
                url.setText(s.getUrl());
                type.setText(typeLabel(t));
                icon.setImageResource(isSelected ? TablerIconRes.get(itemView.getContext(), "ic_check") : typeIcon(t));
                iconBox.setBackgroundResource(isSelected ? R.drawable.bg_squircle_primary : R.drawable.bg_squircle_tonal);
                androidx.core.widget.ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(MaterialColors.getColor(card,
                        isSelected ? com.google.android.material.R.attr.colorOnPrimary
                                : com.google.android.material.R.attr.colorOnPrimaryContainer)));
                card.setCardBackgroundColor(MaterialColors.getColor(card, isSelected
                        ? com.google.android.material.R.attr.colorSecondaryContainer
                        : com.google.android.material.R.attr.colorSurfaceContainer));
                more.setVisibility(selecting ? View.INVISIBLE : View.VISIBLE);
                more.setOnClickListener(v -> { AppPreferences.performHaptic(v); showRowMenu(s, v); });
                card.setOnClickListener(v -> {
                    AppPreferences.performHaptic(v);
                    if (selecting) toggleSelected(s); else showRowMenu(s, more);
                });
                card.setOnLongClickListener(v -> {
                    AppPreferences.performHaptic(v);
                    if (!selecting) {
                        selected.add(key(s));
                        setSelecting(true);
                        updateSelectionUi();
                    } else {
                        toggleSelected(s);
                    }
                    return true;
                });
            }
        }
    }
}
