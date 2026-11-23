package com.opensourcefinder.app.scanner;

import com.opensourcefinder.app.install.InstallState;
import com.opensourcefinder.app.install.VersionCompare;
import com.opensourcefinder.app.model.GitHubRelease;
import com.opensourcefinder.app.model.SourceRepository;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class RepoReleasePolicy {
    public static final String CHANNEL_AUTO = "auto";
    public static final String CHANNEL_STABLE = "stable";
    public static final String CHANNEL_PRE = "pre";
    public static final String CHANNEL_ANY = "any";

    private RepoReleasePolicy() {}

    public static boolean isPre(GitHubRelease release) {
        return release.isPrerelease() || InstallState.isPreRelease(release.getTagName());
    }

    public static boolean allows(SourceRepository source, GitHubRelease release) {
        String tag = release.getTagName();
        if (tag == null || tag.isEmpty() || release.isDraft()) return false;
        String channel = source.getChannel();
        if (CHANNEL_STABLE.equals(channel) && isPre(release)) return false;
        if (CHANNEL_PRE.equals(channel) && !isPre(release)) return false;
        String filter = source.getTagFilter();
        if (filter != null && !filter.trim().isEmpty() && !matchesFilter(tag, filter)) return false;
        String min = source.getMinVersion();
        if (min != null && !min.trim().isEmpty() && VersionCompare.comparable(tag, min)) {
            int c = VersionCompare.isPreRelease(min) ? VersionCompare.compare(tag, min) : VersionCompare.compareCore(tag, min);
            if (c < 0) return false;
        }
        return true;
    }

    public static boolean matchesFilter(String tag, String filter) {
        String raw = filter.trim();
        if (raw.regionMatches(true, 0, "re:", 0, 3)) {
            try {
                return Pattern.compile(raw.substring(3).trim(), Pattern.CASE_INSENSITIVE).matcher(tag).find();
            } catch (Exception e) {
                return tag.toLowerCase(Locale.ROOT).contains(raw.substring(3).trim().toLowerCase(Locale.ROOT));
            }
        }
        String lower = tag.toLowerCase(Locale.ROOT);
        boolean any = false;
        for (String token : raw.split("[,;|]+")) {
            String t = token.trim().toLowerCase(Locale.ROOT);
            if (t.isEmpty()) continue;
            any = true;
            if (lower.contains(t)) return true;
        }
        return !any;
    }

    public static GitHubRelease pick(List<GitHubRelease> releases, SourceRepository source) {
        return GitHubRelease.bestMatching(releases, false, release -> allows(source, release));
    }

    public static boolean assetMatches(String name, String pattern) {
        if (name == null || pattern == null) return false;
        String p = pattern.trim().toLowerCase(Locale.ROOT);
        if (p.isEmpty()) return false;
        String n = name.toLowerCase(Locale.ROOT);
        if (p.indexOf('*') < 0) return n.contains(p);
        StringBuilder regex = new StringBuilder();
        for (String part : p.split("\\*", -1)) {
            if (regex.length() > 0) regex.append(".*");
            regex.append(Pattern.quote(part));
        }
        return Pattern.compile(regex.toString(), Pattern.DOTALL).matcher(n).matches();
    }

    public static GitHubRelease.Asset pickAsset(GitHubRelease release, SourceRepository source) {
        if (release == null) return null;
        String pattern = source.getAssetPattern();
        if (pattern != null && !pattern.trim().isEmpty() && release.getAssets() != null) {
            GitHubRelease.Asset exact = null;
            GitHubRelease.Asset loose = null;
            for (GitHubRelease.Asset asset : release.getAssets()) {
                String name = asset.getName();
                if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
                if (asset.getBrowserDownloadUrl() == null) continue;
                if (name.equalsIgnoreCase(pattern.trim())) return asset;
                if (loose == null && assetMatches(name, pattern)) loose = asset;
            }
            if (loose != null) return loose;
        }
        return release.findApkAsset(false);
    }

    public static String derivePattern(String assetName, String tag) {
        if (assetName == null) return "";
        if (tag == null || tag.trim().isEmpty()) return assetName;
        String t = tag.trim();
        String bare = t.replaceFirst("(?i)^v", "");
        java.util.regex.Matcher core = Pattern.compile("^\\d+(?:[._-]\\d+)*").matcher(bare);
        String[] candidates = {t, bare, core.find() ? core.group() : ""};
        String lower = assetName.toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (candidate.length() < 2) continue;
            int at = lower.indexOf(candidate.toLowerCase(Locale.ROOT));
            if (at >= 0) return assetName.substring(0, at) + "*" + assetName.substring(at + candidate.length());
        }
        return assetName;
    }
}
