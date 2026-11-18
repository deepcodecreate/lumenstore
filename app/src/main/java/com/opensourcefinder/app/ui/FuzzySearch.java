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

import com.opensourcefinder.app.model.ExploreApp;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FuzzySearch {
    private static final String FA_KEYS = "ضصثقفغعهخحجچشسیبلاتنمکگظطزرذدپو";
    private static final String EN_KEYS = "qwertyuiop[]asdfghjkl;'zxcvbnm,";
    private static final int MAX_RESULTS = 250;

    private final Map<ExploreApp, Doc> cache = new IdentityHashMap<>();

    private static final class Doc {
        String name = "";
        String pkg = "";
        String summary = "";
        String categories = "";
        String[] nameTokens = new String[0];
        String[] pkgTokens = new String[0];
        String[] summaryTokens = new String[0];
    }

    private static final class Scored {
        final ExploreApp app;
        final int score;
        Scored(ExploreApp app, int score) {
            this.app = app;
            this.score = score;
        }
    }

    public synchronized List<ExploreApp> search(List<ExploreApp> base, String rawQuery) {
        List<String> variants = variants(rawQuery);
        List<ExploreApp> out = new ArrayList<>();
        if (variants.isEmpty() || base == null) return out;
        String[] qTokens = tokens(rawQuery);
        List<Scored> strict = rank(base, variants, qTokens, 0);
        if (strict.isEmpty()) strict = rank(base, variants, qTokens, 1);
        for (Scored s : strict) out.add(s.app);
        return out;
    }

    private List<Scored> rank(List<ExploreApp> base, List<String> variants, String[] qTokens, int slack) {
        List<Scored> scored = new ArrayList<>();
        for (ExploreApp app : base) {
            if (app == null) continue;
            Doc doc = cache.get(app);
            if (doc == null) {
                doc = buildDoc(app);
                cache.put(app, doc);
            }
            int best = 0;
            for (String variant : variants) {
                int s = scoreDoc(doc, variant, slack);
                if (s > best) best = s;
            }
            if (qTokens.length >= 2) best = Math.max(best, multiTokenScore(doc, qTokens, slack));
            if (best > 0) scored.add(new Scored(app, best));
        }
        Collections.sort(scored, (a, b) -> {
            if (a.score != b.score) return b.score - a.score;
            String an = a.app.getName() == null ? "" : a.app.getName();
            String bn = b.app.getName() == null ? "" : b.app.getName();
            return an.compareToIgnoreCase(bn);
        });
        if (scored.size() > MAX_RESULTS) return new ArrayList<>(scored.subList(0, MAX_RESULTS));
        return scored;
    }

    private static List<String> variants(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        String primary = normalize(raw);
        if (primary.isEmpty()) return out;
        out.add(primary);
        String mapped = mapPersianLayout(raw);
        if (mapped != null) {
            String m = normalize(mapped);
            if (!m.isEmpty() && !out.contains(m)) out.add(m);
        }
        return out;
    }

    private static String mapPersianLayout(String raw) {
        boolean any = false;
        StringBuilder sb = new StringBuilder();
        String lower = raw.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = unifyPersian(lower.charAt(i));
            int idx = FA_KEYS.indexOf(c);
            if (idx >= 0) {
                sb.append(EN_KEYS.charAt(idx));
                any = true;
            } else if (Character.isWhitespace(c)) {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return any ? sb.toString() : null;
    }

    private static char unifyPersian(char c) {
        switch (c) {
            case 'ي': case 'ى': return 'ی';
            case 'ك': return 'ک';
            case 'ة': case 'ۀ': return 'ه';
            case 'ؤ': return 'و';
            case 'أ': case 'إ': case 'آ': return 'ا';
            default: return c;
        }
    }

    private static String normalizeChars(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            int type = Character.getType(c);
            if (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) continue;
            c = unifyPersian(c);
            if (c >= '۰' && c <= '۹') c = (char) ('0' + (c - '۰'));
            else if (c >= '٠' && c <= '٩') c = (char) ('0' + (c - '٠'));
            sb.append(c);
        }
        return sb.toString();
    }

    private static String normalize(String raw) {
        String chars = normalizeChars(raw);
        StringBuilder sb = new StringBuilder(chars.length());
        for (int i = 0; i < chars.length(); i++) {
            char c = chars.charAt(i);
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    private static String[] tokens(String raw) {
        if (raw == null || raw.isEmpty()) return new String[0];
        String spaced = raw.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
        String chars = normalizeChars(spaced);
        List<String> list = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < chars.length(); i++) {
            char c = chars.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                cur.append(c);
            } else if (cur.length() > 0) {
                list.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) list.add(cur.toString());
        return list.toArray(new String[0]);
    }

    private static Doc buildDoc(ExploreApp app) {
        Doc d = new Doc();
        String name = app.getName() == null ? "" : app.getName();
        String pkg = app.getPackageName() == null ? "" : app.getPackageName();
        String summary = app.getSummary() == null ? "" : app.getSummary();
        if (summary.length() > 240) summary = summary.substring(0, 240);
        d.name = normalize(name);
        d.pkg = normalize(pkg);
        d.summary = normalize(summary);
        d.nameTokens = tokens(name);
        d.pkgTokens = tokens(pkg);
        d.summaryTokens = tokens(summary);
        StringBuilder cats = new StringBuilder();
        if (app.getCategories() != null) {
            for (String c : app.getCategories()) cats.append(normalize(String.valueOf(c))).append(' ');
        }
        d.categories = cats.toString();
        return d;
    }

    private static int allowedErrors(int len, int slack) {
        int base;
        if (len <= 3) base = 0;
        else if (len <= 5) base = 1;
        else if (len <= 8) base = 2;
        else base = 3;
        int allowed = base + slack;
        int cap = Math.max(1, len / 2);
        if (len <= 2) return 0;
        return Math.min(allowed, cap);
    }

    private static int scoreDoc(Doc d, String q, int slack) {
        int best = 0;
        if (d.name.equals(q)) return 1000;
        if (d.name.startsWith(q)) best = Math.max(best, 920 - Math.min(40, d.name.length() - q.length()));
        int idx = d.name.indexOf(q);
        if (idx >= 0) best = Math.max(best, 840 - Math.min(60, idx * 3));
        if (q.length() >= 3 && d.pkg.contains(q)) best = Math.max(best, 560);
        if (best >= 840) return best;

        int tokenScore = tokenScore(d, q, slack);
        best = Math.max(best, tokenScore);

        int allowed = allowedErrors(q.length(), slack);
        if (allowed > 0 && d.name.length() > 0) {
            int dist = approxSubstring(q, d.name, allowed);
            if (dist <= allowed) best = Math.max(best, 700 - 90 * dist);
        }
        if (slack > 0 && q.length() >= 3 && d.name.length() > 0) {
            int gaps = subsequenceGaps(q, d.name);
            if (gaps >= 0) best = Math.max(best, 300 - Math.min(150, gaps * 6));
        }
        if (q.length() >= 3 && d.summary.contains(q)) best = Math.max(best, 260);
        if (q.length() >= 3 && d.categories.contains(q)) best = Math.max(best, 240);
        return best;
    }

    private static int tokenScore(Doc d, String compactQuery, int slack) {
        int best = 0;
        for (String t : d.nameTokens) {
            if (t.equals(compactQuery)) best = Math.max(best, 880);
            else if (t.startsWith(compactQuery)) best = Math.max(best, 800);
        }
        for (String t : d.pkgTokens) {
            if (t.equals(compactQuery)) best = Math.max(best, 520);
        }
        int allowed = allowedErrors(compactQuery.length(), slack);
        if (allowed > 0) {
            for (String t : d.nameTokens) {
                if (t.length() < 3) continue;
                int dist = approxSubstring(compactQuery, t, allowed);
                if (dist <= allowed) best = Math.max(best, 660 - 80 * dist);
            }
            if (compactQuery.length() >= 4) {
                for (String t : d.summaryTokens) {
                    if (t.length() < 4) continue;
                    if (t.equals(compactQuery)) best = Math.max(best, 280);
                    else if (approxSubstring(compactQuery, t, allowed) <= Math.min(1, allowed)) best = Math.max(best, 200);
                }
            }
        }
        return best;
    }

    private static int multiTokenScore(Doc d, String[] qTokens, int slack) {
        int total = 0;
        for (String t : qTokens) {
            int s = tokenMatch(d, t, slack);
            if (s == 0) return 0;
            total += s;
        }
        return 380 + total / qTokens.length;
    }

    private static int tokenMatch(Doc d, String t, int slack) {
        int best = 0;
        String[][] pools = {d.nameTokens, d.pkgTokens, d.summaryTokens};
        for (int pi = 0; pi < pools.length; pi++) {
            int weight = pi == 0 ? 0 : -10;
            for (String w : pools[pi]) {
                if (w.equals(t)) best = Math.max(best, 100 + weight);
                else if (w.startsWith(t)) best = Math.max(best, 85 + weight);
                else if (t.length() >= 3 && w.contains(t)) best = Math.max(best, 70 + weight);
                else {
                    int allowed = allowedErrors(t.length(), slack);
                    if (allowed > 0 && t.length() >= 4 && w.length() >= 3
                            && approxSubstring(t, w, allowed) <= allowed) best = Math.max(best, 55 + weight);
                }
            }
        }
        return best;
    }

    private static int subsequenceGaps(String pattern, String text) {
        int ti = 0;
        int first = -1;
        int last = -1;
        for (int pi = 0; pi < pattern.length(); pi++) {
            char c = pattern.charAt(pi);
            while (ti < text.length() && text.charAt(ti) != c) ti++;
            if (ti >= text.length()) return -1;
            if (first < 0) first = ti;
            last = ti;
            ti++;
        }
        return (last - first + 1) - pattern.length();
    }

    private static int approxSubstring(String p, String t, int maxErr) {
        int m = p.length();
        int n = t.length();
        if (m == 0) return 0;
        if (n == 0) return m;
        int[] prev2 = new int[n + 1];
        int[] prev = new int[n + 1];
        int[] cur = new int[n + 1];
        for (int j = 0; j <= n; j++) prev[j] = 0;
        int best = m;
        for (int i = 1; i <= m; i++) {
            cur[0] = i;
            char pc = p.charAt(i - 1);
            int rowMin = cur[0];
            for (int j = 1; j <= n; j++) {
                int cost = pc == t.charAt(j - 1) ? 0 : 1;
                int v = Math.min(Math.min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + cost);
                if (i > 1 && j > 1 && pc == t.charAt(j - 2) && p.charAt(i - 2) == t.charAt(j - 1)) {
                    v = Math.min(v, prev2[j - 2] + 1);
                }
                cur[j] = v;
                if (v < rowMin) rowMin = v;
            }
            if (rowMin > maxErr + (m - i)) return maxErr + 1;
            int[] tmp = prev2;
            prev2 = prev;
            prev = cur;
            cur = tmp;
        }
        for (int j = 0; j <= n; j++) if (prev[j] < best) best = prev[j];
        return best;
    }
}
