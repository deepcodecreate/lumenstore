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

import android.content.res.ColorStateList;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.model.GitHubEvent;
import com.opensourcefinder.app.model.GitHubOrg;
import com.opensourcefinder.app.model.GitHubRepo;
import com.opensourcefinder.app.model.GitHubUser;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

final class GitHubProfileAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int T_HEADER = 0;
    private static final int T_STATS = 1;
    private static final int T_ACTIONS = 2;
    private static final int T_SECTION = 3;
    private static final int T_REPO = 4;
    private static final int T_LANGS = 5;
    private static final int T_PEOPLE = 6;
    private static final int T_EVENT = 7;
    private static final int T_README = 8;

    interface ActionListener {
        void onLogout();
        void onShare();
        void onOpenUrl(@NonNull String url);
    }

    static final class Person {
        final String name;
        final String avatarUrl;
        final String url;

        Person(String name, String avatarUrl, String url) {
            this.name = name;
            this.avatarUrl = avatarUrl;
            this.url = url;
        }
    }

    private static final class Section {
        final int titleRes;
        @Nullable final String url;

        Section(int titleRes, @Nullable String url) {
            this.titleRes = titleRes;
            this.url = url;
        }

        @Override public boolean equals(Object o) {
            return o instanceof Section && ((Section) o).titleRes == titleRes && Objects.equals(((Section) o).url, url);
        }

        @Override public int hashCode() { return titleRes; }
    }

    private static final class Row {
        final int type;
        final String key;
        final Object data;

        Row(int type, String key, Object data) {
            this.type = type;
            this.key = key;
            this.data = data;
        }
    }

    private final ActionListener listener;
    private final List<Row> rows = new ArrayList<>();

    @Nullable private GitHubUser user;
    @Nullable private String readme;
    @Nullable private List<GitHubRepo> repos;
    @Nullable private List<GitHubRepo> starred;
    @Nullable private List<Person> orgs;
    @Nullable private List<Person> followers;
    @Nullable private List<GitHubEvent> events;
    private int starredCount = -1;
    private List<Map.Entry<String, Integer>> languages = Collections.emptyList();

    GitHubProfileAdapter(@NonNull ActionListener listener) {
        this.listener = listener;
    }

    void setUser(@Nullable GitHubUser user) { this.user = user; rebuild(); }
    void setReadme(@Nullable String markdown) { this.readme = markdown; rebuild(); }
    void setStarredCount(int count) { this.starredCount = count; rebuild(); }
    void setStarred(@Nullable List<GitHubRepo> list) { this.starred = list; rebuild(); }
    void setEvents(@Nullable List<GitHubEvent> list) { this.events = list; rebuild(); }

    void setRepos(@Nullable List<GitHubRepo> list) {
        this.repos = list;
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (list != null) {
            for (GitHubRepo r : list) {
                String lang = r.getLanguage();
                if (r.isFork() || lang == null || lang.trim().isEmpty()) continue;
                Integer c = counts.get(lang);
                counts.put(lang, c == null ? 1 : c + 1);
            }
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        Collections.sort(sorted, (a, b) -> b.getValue() - a.getValue());
        languages = sorted.size() > 6 ? new ArrayList<>(sorted.subList(0, 6)) : sorted;
        rebuild();
    }

    void setOrgs(@Nullable List<GitHubOrg> list) {
        if (list == null || list.isEmpty()) { orgs = null; rebuild(); return; }
        List<Person> out = new ArrayList<>();
        for (GitHubOrg o : list) out.add(new Person(o.getLogin(), o.getAvatarUrl(), "https://github.com/" + o.getLogin()));
        orgs = out;
        rebuild();
    }

    void setFollowers(@Nullable List<GitHubUser> list) {
        if (list == null || list.isEmpty()) { followers = null; rebuild(); return; }
        List<Person> out = new ArrayList<>();
        for (GitHubUser u : list) out.add(new Person(u.getLogin(), u.getAvatarUrl(), "https://github.com/" + u.getLogin()));
        followers = out;
        rebuild();
    }

    private void rebuild() {
        List<Row> next = new ArrayList<>();
        if (user != null) {
            String login = user.getLogin();
            next.add(new Row(T_HEADER, "header", user));
            next.add(new Row(T_STATS, "stats", Arrays.asList(user, starredCount)));
            next.add(new Row(T_ACTIONS, "actions", user));
            if (readme != null && !readme.trim().isEmpty()) {
                next.add(new Row(T_README, "readme", readme));
            }

            if (repos != null && !repos.isEmpty()) {
                next.add(new Row(T_SECTION, "s_repos", new Section(R.string.profile_section_repos,
                        "https://github.com/" + login + "?tab=repositories")));
                for (int i = 0; i < Math.min(5, repos.size()); i++) {
                    next.add(new Row(T_REPO, "r_" + repos.get(i).getFullName(), repos.get(i)));
                }
            }
            if (!languages.isEmpty()) {
                next.add(new Row(T_SECTION, "s_langs", new Section(R.string.profile_section_languages, null)));
                next.add(new Row(T_LANGS, "langs", languages));
            }
            if (starred != null && !starred.isEmpty()) {
                next.add(new Row(T_SECTION, "s_starred", new Section(R.string.profile_section_starred,
                        "https://github.com/" + login + "?tab=stars")));
                for (int i = 0; i < Math.min(5, starred.size()); i++) {
                    next.add(new Row(T_REPO, "st_" + starred.get(i).getFullName(), starred.get(i)));
                }
            }
            if (orgs != null && !orgs.isEmpty()) {
                next.add(new Row(T_SECTION, "s_orgs", new Section(R.string.profile_section_orgs, null)));
                next.add(new Row(T_PEOPLE, "orgs", orgs));
            }
            if (followers != null && !followers.isEmpty()) {
                next.add(new Row(T_SECTION, "s_followers", new Section(R.string.profile_section_followers,
                        "https://github.com/" + login + "?tab=followers")));
                next.add(new Row(T_PEOPLE, "followers", followers));
            }
            if (events != null && !events.isEmpty()) {
                next.add(new Row(T_SECTION, "s_events", new Section(R.string.profile_section_activity, null)));
                for (int i = 0; i < Math.min(8, events.size()); i++) {
                    next.add(new Row(T_EVENT, "e_" + i, events.get(i)));
                }
            }
        }

        if (user == null && readme != null && !readme.trim().isEmpty()) {
            next.add(new Row(T_README, "readme", readme));
        }

        final List<Row> old = new ArrayList<>(rows);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return old.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override public boolean areItemsTheSame(int o, int n) {
                return old.get(o).type == next.get(n).type && old.get(o).key.equals(next.get(n).key);
            }
            @Override public boolean areContentsTheSame(int o, int n) {
                return Objects.equals(old.get(o).data, next.get(n).data);
            }
        });
        rows.clear();
        rows.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    @Override public int getItemCount() { return rows.size(); }
    @Override public int getItemViewType(int position) { return rows.get(position).type; }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater in = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case T_HEADER: return new HeaderHolder(in.inflate(R.layout.item_profile_header, parent, false), listener);
            case T_STATS: return new StatsHolder(in.inflate(R.layout.item_profile_stats, parent, false));
            case T_ACTIONS: return new ActionsHolder(in.inflate(R.layout.item_profile_actions, parent, false), listener);
            case T_SECTION: return new SectionHolder(in.inflate(R.layout.item_profile_section, parent, false), listener);
            case T_REPO: return new RepoHolder(in.inflate(R.layout.item_profile_repo, parent, false), listener);
            case T_LANGS: return new LangsHolder(in.inflate(R.layout.item_profile_languages, parent, false));
            case T_PEOPLE: return new PeopleHolder(in.inflate(R.layout.item_profile_people, parent, false), listener);
            case T_EVENT: return new EventHolder(in.inflate(R.layout.item_profile_event, parent, false));
            case T_README:
            default: return new ReadmeHolder(in.inflate(R.layout.item_profile_readme, parent, false));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object data = rows.get(position).data;
        switch (rows.get(position).type) {
            case T_HEADER: ((HeaderHolder) holder).bind((GitHubUser) data); break;
            case T_STATS: {
                List<Object> pair = (List<Object>) data;
                ((StatsHolder) holder).bind((GitHubUser) pair.get(0), (Integer) pair.get(1));
                break;
            }
            case T_ACTIONS: ((ActionsHolder) holder).bind((GitHubUser) data); break;
            case T_SECTION: ((SectionHolder) holder).bind((Section) data); break;
            case T_REPO: ((RepoHolder) holder).bind((GitHubRepo) data); break;
            case T_LANGS: ((LangsHolder) holder).bind((List<Map.Entry<String, Integer>>) data); break;
            case T_PEOPLE: ((PeopleHolder) holder).bind((List<Person>) data); break;
            case T_EVENT: ((EventHolder) holder).bind((GitHubEvent) data); break;
            case T_README: ((ReadmeHolder) holder).bind((String) data, user != null ? user.getLogin() : null); break;
            default: break;
        }
    }

    static long parseIso(@Nullable String iso) {
        if (iso == null) return 0L;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = f.parse(iso);
            return d != null ? d.getTime() : 0L;
        } catch (ParseException e) {
            return 0L;
        }
    }

    static String relative(long millis) {
        if (millis <= 0) return "";
        return DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString();
    }

    private static final int[] PALETTE_ATTRS = {
            androidx.appcompat.R.attr.colorPrimary,
            com.google.android.material.R.attr.colorTertiary,
            com.google.android.material.R.attr.colorSecondary,
            com.google.android.material.R.attr.colorPrimaryContainer,
            com.google.android.material.R.attr.colorTertiaryContainer,
            com.google.android.material.R.attr.colorSecondaryContainer,
    };

    static int colorFor(View view, String language) {
        int idx = Math.abs(language.hashCode()) % PALETTE_ATTRS.length;
        return MaterialColors.getColor(view, PALETTE_ATTRS[idx], 0xFF888888);
    }

    static final class HeaderHolder extends RecyclerView.ViewHolder {
        private final ImageView avatar;
        private final TextView name;
        private final TextView username;
        private final TextView bio;
        private final ChipGroup chips;
        private final ActionListener listener;

        HeaderHolder(@NonNull View v, ActionListener listener) {
            super(v);
            this.listener = listener;
            avatar = v.findViewById(R.id.profileAvatar);
            name = v.findViewById(R.id.profileName);
            username = v.findViewById(R.id.profileUsername);
            bio = v.findViewById(R.id.profileBio);
            chips = v.findViewById(R.id.profileMetaChips);
        }

        void bind(@Nullable GitHubUser user) {
            if (user == null) return;
            name.setText(user.getDisplayName());
            username.setText("@" + user.getLogin());
            if (user.getBio() != null && !user.getBio().trim().isEmpty()) {
                bio.setText(user.getBio().trim());
                bio.setVisibility(View.VISIBLE);
            } else {
                bio.setVisibility(View.GONE);
            }
            if (user.getAvatarUrl() != null) {
                IconLoader.loadAvatar(avatar, user.getAvatarUrl());
            }
            chips.removeAllViews();
            addChip(user.getLocation(), null);
            addChip(user.getCompany(), null);
            String blog = user.getBlog();
            if (blog != null && !blog.trim().isEmpty()) {
                String url = blog.trim().startsWith("http") ? blog.trim() : "https://" + blog.trim();
                addChip(blog.trim().replaceFirst("^https?://", ""), url);
            }
            if (user.getTwitterUsername() != null && !user.getTwitterUsername().trim().isEmpty()) {
                addChip("@" + user.getTwitterUsername().trim(), "https://x.com/" + user.getTwitterUsername().trim());
            }
            long joined = parseIso(user.getCreatedAt());
            if (joined > 0) {
                String when = new SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(new Date(joined));
                addChip(itemView.getContext().getString(R.string.profile_hero_joined, when), null);
            }
            chips.setVisibility(chips.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        }

        private void addChip(@Nullable String text, @Nullable String url) {
            if (text == null || text.trim().isEmpty()) return;
            Chip chip = new Chip(chips.getContext());
            chip.setText(text.trim());
            chip.setCheckable(false);
            chip.setChipBackgroundColor(ColorStateList.valueOf(MaterialColors.getColor(chip,
                    com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFF222222)));
            chip.setChipStrokeWidth(0f);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setChipMinHeight(28f * chip.getResources().getDisplayMetrics().density);
            chip.setTextSize(12f);
            chip.setTextColor(MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFFFFFFF));
            if (url != null) chip.setOnClickListener(v -> listener.onOpenUrl(url));
            else chip.setClickable(false);
            chips.addView(chip);
        }
    }

    static final class StatsHolder extends RecyclerView.ViewHolder {
        private final TextView repos;
        private final TextView followers;
        private final TextView following;
        private final TextView stars;

        StatsHolder(@NonNull View v) {
            super(v);
            repos = v.findViewById(R.id.statRepos);
            followers = v.findViewById(R.id.statFollowers);
            following = v.findViewById(R.id.statFollowing);
            stars = v.findViewById(R.id.statStars);
        }

        void bind(@Nullable GitHubUser user, int starCount) {
            if (user == null) return;
            repos.setText(String.valueOf(user.getPublicRepos()));
            followers.setText(String.valueOf(user.getFollowers()));
            following.setText(String.valueOf(user.getFollowing()));
            stars.setText(starCount >= 0 ? String.valueOf(starCount) : "–");
        }
    }

    static final class ActionsHolder extends RecyclerView.ViewHolder {
        private final MaterialButton open;
        private final MaterialButton share;

        ActionsHolder(@NonNull View v, @NonNull ActionListener listener) {
            super(v);
            open = v.findViewById(R.id.openInBrowserButton);
            share = v.findViewById(R.id.shareProfileButton);
            MaterialButton logout = v.findViewById(R.id.logoutButton);
            logout.setOnClickListener(view -> listener.onLogout());
            share.setOnClickListener(view -> listener.onShare());
            open.setOnClickListener(view -> {
                Object url = open.getTag();
                if (url instanceof String) listener.onOpenUrl((String) url);
            });
        }

        void bind(@Nullable GitHubUser user) {
            String url = user != null ? user.getHtmlUrl() : null;
            open.setTag(url);
            open.setVisibility(url != null ? View.VISIBLE : View.GONE);
            share.setVisibility(url != null ? View.VISIBLE : View.GONE);
        }
    }

    static final class SectionHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView action;
        private final ActionListener listener;

        SectionHolder(@NonNull View v, ActionListener listener) {
            super(v);
            this.listener = listener;
            title = v.findViewById(R.id.sectionTitle);
            action = v.findViewById(R.id.sectionAction);
        }

        void bind(Section section) {
            title.setText(section.titleRes);
            if (section.url != null) {
                action.setVisibility(View.VISIBLE);
                action.setOnClickListener(v -> listener.onOpenUrl(section.url));
            } else {
                action.setVisibility(View.GONE);
                action.setOnClickListener(null);
            }
        }
    }

    static final class RepoHolder extends RecyclerView.ViewHolder {
        private final TextView name;
        private final TextView badge;
        private final TextView description;
        private final View dot;
        private final TextView meta;
        private final ActionListener listener;

        RepoHolder(@NonNull View v, ActionListener listener) {
            super(v);
            this.listener = listener;
            name = v.findViewById(R.id.repoName);
            badge = v.findViewById(R.id.repoBadge);
            description = v.findViewById(R.id.repoDescription);
            dot = v.findViewById(R.id.repoLangDot);
            meta = v.findViewById(R.id.repoMeta);
        }

        void bind(GitHubRepo repo) {
            String full = repo.getFullName();
            name.setText(full != null && !full.isEmpty() ? full : repo.getName());
            badge.setVisibility(repo.isFork() ? View.VISIBLE : View.GONE);
            String d = repo.getDescription();
            description.setText(d != null && !d.trim().isEmpty() ? d.trim()
                    : itemView.getContext().getString(R.string.profile_no_description));

            StringBuilder sb = new StringBuilder();
            String lang = repo.getLanguage();
            if (lang != null && !lang.trim().isEmpty()) {
                dot.setVisibility(View.VISIBLE);
                dot.setBackgroundTintList(ColorStateList.valueOf(colorFor(dot, lang)));
                sb.append(lang).append("  •  ");
            } else {
                dot.setVisibility(View.GONE);
            }
            sb.append("★ ").append(repo.getStargazersCount()).append("  •  ⑂ ").append(repo.getForksCount());
            long pushed = parseIso(repo.getPushedAt());
            if (pushed > 0) {
                sb.append("  •  ").append(itemView.getContext().getString(R.string.profile_repo_updated, relative(pushed)));
            }
            meta.setText(sb.toString());

            itemView.setOnClickListener(v -> {
                if (repo.getHtmlUrl() != null) listener.onOpenUrl(repo.getHtmlUrl());
            });
        }
    }

    static final class LangsHolder extends RecyclerView.ViewHolder {
        private final LinearLayout bar;
        private final ChipGroup legend;

        LangsHolder(@NonNull View v) {
            super(v);
            bar = v.findViewById(R.id.languageBar);
            legend = v.findViewById(R.id.languageLegend);
        }

        void bind(List<Map.Entry<String, Integer>> langs) {
            bar.removeAllViews();
            legend.removeAllViews();
            float gap = 2f * itemView.getResources().getDisplayMetrics().density;
            int total = 0;
            for (Map.Entry<String, Integer> e : langs) total += e.getValue();
            if (total == 0) return;
            for (int i = 0; i < langs.size(); i++) {
                Map.Entry<String, Integer> e = langs.get(i);
                int color = colorFor(bar, e.getKey());

                View seg = new View(bar.getContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.MATCH_PARENT, e.getValue());
                if (i > 0) lp.setMarginStart(Math.round(gap));
                seg.setLayoutParams(lp);
                android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
                shape.setColor(color);
                shape.setCornerRadius(100f * itemView.getResources().getDisplayMetrics().density);
                seg.setBackground(shape);
                bar.addView(seg);

                Chip chip = new Chip(legend.getContext());
                chip.setText(e.getKey() + "  " + Math.round(100f * e.getValue() / total) + "%");
                chip.setCheckable(false);
                chip.setClickable(false);
                chip.setChipBackgroundColor(ColorStateList.valueOf(MaterialColors.getColor(chip,
                        com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFF222222)));
                chip.setChipStrokeWidth(0f);
                chip.setEnsureMinTouchTargetSize(false);
                chip.setChipMinHeight(26f * itemView.getResources().getDisplayMetrics().density);
                chip.setTextSize(12f);
                chip.setChipIconVisible(true);
                android.graphics.drawable.GradientDrawable dotShape = new android.graphics.drawable.GradientDrawable();
                dotShape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                dotShape.setColor(color);
                int s = Math.round(10f * itemView.getResources().getDisplayMetrics().density);
                dotShape.setSize(s, s);
                chip.setChipIcon(dotShape);
                chip.setChipIconSize(s);
                legend.addView(chip);
            }
        }
    }

    static final class PeopleHolder extends RecyclerView.ViewHolder {
        private final LinearLayout row;
        private final ActionListener listener;

        PeopleHolder(@NonNull View v, ActionListener listener) {
            super(v);
            this.listener = listener;
            row = v.findViewById(R.id.peopleRow);
        }

        void bind(List<Person> people) {
            row.removeAllViews();
            LayoutInflater in = LayoutInflater.from(row.getContext());
            for (Person p : people) {
                View item = in.inflate(R.layout.item_profile_person, row, false);
                ((TextView) item.findViewById(R.id.personName)).setText(p.name);
                ImageView img = item.findViewById(R.id.personAvatar);
                if (p.avatarUrl != null) IconLoader.loadAvatar(img, p.avatarUrl);
                item.setOnClickListener(v -> listener.onOpenUrl(p.url));
                row.addView(item);
            }
        }
    }

    static final class EventHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView time;

        EventHolder(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.eventTitle);
            time = v.findViewById(R.id.eventTime);
        }

        void bind(GitHubEvent e) {
            android.content.Context c = itemView.getContext();
            String repo = e.getRepoName() != null ? e.getRepoName() : "";
            int res;
            String type = e.getType() != null ? e.getType() : "";
            switch (type) {
                case "PushEvent": res = R.string.profile_event_push; break;
                case "WatchEvent": res = R.string.profile_event_star; break;
                case "CreateEvent": res = R.string.profile_event_create; break;
                case "ForkEvent": res = R.string.profile_event_fork; break;
                case "PullRequestEvent": res = R.string.profile_event_pr; break;
                case "IssuesEvent":
                case "IssueCommentEvent": res = R.string.profile_event_issue; break;
                case "ReleaseEvent": res = R.string.profile_event_release; break;
                default: res = R.string.profile_event_other; break;
            }
            title.setText(c.getString(res, repo));
            time.setText(relative(parseIso(e.getCreatedAt())));
        }
    }

    static final class ReadmeHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView card;
        private final RecyclerView segments;

        ReadmeHolder(@NonNull View v) {
            super(v);
            card = (MaterialCardView) v;
            segments = v.findViewById(R.id.profileReadmeContainer);
        }

        void bind(@Nullable String markdown, @Nullable String login) {
            if (markdown == null || markdown.trim().isEmpty()) { card.setVisibility(View.GONE); return; }
            try {
                MarkdownRenderer.renderSegmented(segments, markdown, login, login);
            } catch (RuntimeException malformed) {
                card.setVisibility(View.GONE);
                return;
            }
            card.setVisibility(View.VISIBLE);
        }
    }
}
