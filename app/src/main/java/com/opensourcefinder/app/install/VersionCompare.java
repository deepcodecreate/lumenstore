package com.opensourcefinder.app.install;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionCompare {
    private VersionCompare() {}

    private static final Pattern RUN = Pattern.compile("\\d+(?:[._-]\\d+)*");
    private static final Pattern WORD_PRE = Pattern.compile(
            "(?<![a-z])(alpha|beta|preview|prerelease|pre|rc|cr|candidate|snapshot|nightly|canary|dev|insider|unstable)(?![a-z])[\\s._-]*(\\d+)?");
    private static final Pattern SHORT_PRE = Pattern.compile("(?<=\\d)(a|b)(\\d+)(?![a-z])");

    private static final class Parsed {
        final List<String> parts;
        final boolean pre;
        final int rank;
        final long preNumber;
        final String prefix;

        Parsed(List<String> parts, boolean pre, int rank, long preNumber, String prefix) {
            this.parts = parts;
            this.pre = pre;
            this.rank = rank;
            this.preNumber = preNumber;
            this.prefix = prefix;
        }
    }

    private static Parsed parse(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        Matcher m = RUN.matcher(s);
        String best = null;
        int bestStart = 0;
        int bestParts = 0;
        while (m.find()) {
            String run = m.group();
            int parts = run.split("[._-]").length;
            if (parts > bestParts) {
                best = run;
                bestParts = parts;
                bestStart = m.start();
            }
        }
        if (best == null) return null;
        List<String> parts = new ArrayList<>();
        for (String p : best.split("[._-]")) parts.add(stripZeros(p));
        while (parts.size() > 1 && parts.get(parts.size() - 1).equals("0")) parts.remove(parts.size() - 1);
        String prefix = s.substring(0, bestStart).replaceAll("[^a-z]", "");
        if (prefix.equals("v") || prefix.equals("ver") || prefix.equals("version") || prefix.equals("release")) prefix = "";

        boolean pre = false;
        int rank = Integer.MAX_VALUE;
        long preNumber = 0;
        String tail = s.substring(bestStart);
        int tailAfter = best.length();
        String after = tail.length() > tailAfter ? tail.substring(tailAfter) : "";
        Matcher w = WORD_PRE.matcher(after);
        if (w.find()) {
            pre = true;
            rank = rankOf(w.group(1));
            preNumber = parseNumber(w.group(2));
        } else {
            Matcher sm = SHORT_PRE.matcher(tail.substring(0, Math.min(tail.length(), best.length() + 8)));
            if (sm.find() && sm.start() >= best.length() - 1) {
                pre = true;
                rank = sm.group(1).equals("a") ? 1 : 2;
                preNumber = parseNumber(sm.group(2));
            } else {
                Matcher before = WORD_PRE.matcher(s.substring(0, bestStart));
                if (before.find()) {
                    pre = true;
                    rank = rankOf(before.group(1));
                    preNumber = parseNumber(before.group(2));
                }
            }
        }
        return new Parsed(parts, pre, rank, preNumber, prefix);
    }

    private static int rankOf(String kind) {
        switch (kind) {
            case "dev":
            case "nightly":
            case "snapshot":
            case "canary":
            case "insider":
            case "unstable":
                return 0;
            case "alpha":
                return 1;
            case "beta":
            case "preview":
            case "prerelease":
            case "pre":
                return 2;
            default:
                return 3;
        }
    }

    private static long parseNumber(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    private static String stripZeros(String s) {
        int i = 0;
        while (i < s.length() - 1 && s.charAt(i) == '0') i++;
        return s.substring(i);
    }

    private static int compareNumeric(String a, String b) {
        if (a.length() != b.length()) return a.length() < b.length() ? -1 : 1;
        int c = a.compareTo(b);
        return c < 0 ? -1 : (c > 0 ? 1 : 0);
    }

    private static int compareParsed(Parsed a, Parsed b) {
        int n = Math.max(a.parts.size(), b.parts.size());
        for (int i = 0; i < n; i++) {
            String x = i < a.parts.size() ? a.parts.get(i) : "0";
            String y = i < b.parts.size() ? b.parts.get(i) : "0";
            int c = compareNumeric(x, y);
            if (c != 0) return c;
        }
        if (a.pre != b.pre) return a.pre ? -1 : 1;
        if (!a.pre) return 0;
        if (a.rank != b.rank) return a.rank < b.rank ? -1 : 1;
        return Long.compare(a.preNumber, b.preNumber);
    }

    public static int compareCore(String a, String b) {
        Parsed x = parse(a);
        Parsed y = parse(b);
        if (x == null || y == null) return 0;
        int n = Math.max(x.parts.size(), y.parts.size());
        for (int i = 0; i < n; i++) {
            String p = i < x.parts.size() ? x.parts.get(i) : "0";
            String q = i < y.parts.size() ? y.parts.get(i) : "0";
            int c = compareNumeric(p, q);
            if (c != 0) return c;
        }
        return 0;
    }

    public static boolean comparable(String a, String b) {
        return parse(a) != null && parse(b) != null;
    }

    public static int compare(String a, String b) {
        Parsed x = parse(a);
        Parsed y = parse(b);
        if (x == null || y == null) return 0;
        return compareParsed(x, y);
    }

    public static boolean isNewer(String remote, String local) {
        return compare(remote, local) > 0;
    }

    public static boolean isPreRelease(String s) {
        Parsed p = parse(s);
        if (p != null) return p.pre;
        if (s == null) return false;
        return WORD_PRE.matcher(s.toLowerCase(Locale.ROOT)).find();
    }

    public static boolean sameFamily(String a, String b) {
        Parsed x = parse(a);
        Parsed y = parse(b);
        if (x == null || y == null) return false;
        if (!x.prefix.equals(y.prefix)) return false;
        return x.parts.get(0).length() == y.parts.get(0).length();
    }
}
