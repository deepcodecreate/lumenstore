package com.opensourcefinder.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RepositoryLinkExtractor {

    private static final Pattern URL = Pattern.compile(
            "(?i)\\b((?:https?://)?(?:[a-z0-9-]+\\.)+[a-z]{2,}(?::\\d+)?/[^\\s<>\"'`]+)");

    private static final String[] KNOWN_HOSTS = {
            "github.com", "gitlab.com", "codeberg.org", "bitbucket.org", "gitea.com",
            "sr.ht", "git.sr.ht", "notabug.org", "framagit.org", "salsa.debian.org",
            "invent.kde.org", "gitee.com", "gitflic.ru", "git.fosscommit.org", "f-droid.org",
            "apt.izzysoft.de", "git.disroot.org", "git.mobilizon.org", "sourceforge.net"
    };

    private RepositoryLinkExtractor() {}

    @Nullable
    public static String fromClipboard(@NonNull Context context) {
        try {
            ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager == null || !manager.hasPrimaryClip()) return null;
            ClipData clip = manager.getPrimaryClip();
            if (clip == null) return null;
            for (int i = 0; i < clip.getItemCount(); i++) {
                CharSequence text = clip.getItemAt(i).coerceToText(context);
                String link = extract(text == null ? null : text.toString());
                if (link != null) return link;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    public static String extract(@Nullable String text) {
        if (text == null || text.trim().isEmpty()) return null;
        Matcher matcher = URL.matcher(text);
        while (matcher.find()) {
            String candidate = trimTail(matcher.group(1));
            String link = accept(candidate);
            if (link != null) return link;
        }
        return null;
    }

    private static String trimTail(String value) {
        String v = value;
        while (!v.isEmpty()) {
            char c = v.charAt(v.length() - 1);
            if (c == '.' || c == ',' || c == ';' || c == ':' || c == '!' || c == '?' || c == ')'
                    || c == ']' || c == '}' || c == '>' || c == '\u060C' || c == '\u061B') {
                v = v.substring(0, v.length() - 1);
            } else {
                break;
            }
        }
        return v;
    }

    @Nullable
    private static String accept(String candidate) {
        try {
            String value = candidate.startsWith("http://") || candidate.startsWith("https://")
                    ? candidate : "https://" + candidate;
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host == null) return null;
            String h = host.toLowerCase(Locale.ROOT);
            if (h.startsWith("www.")) h = h.substring(4);
            String path = uri.getPath() == null ? "" : uri.getPath();
            String[] raw = path.split("/");
            int count = 0;
            for (String s : raw) if (!s.isEmpty()) count++;
            if (count == 0) return null;
            if (h.equals("github.com")) {
                if (count < 2) return null;
                String[] segments = new String[2];
                int idx = 0;
                for (String s : raw) {
                    if (s.isEmpty()) continue;
                    segments[idx++] = s;
                    if (idx == 2) break;
                }
                String repo = segments[1].replaceAll("(?i)\\.git$", "");
                if (repo.isEmpty()) return null;
                return "https://github.com/" + segments[0] + "/" + repo;
            }
            if (isKnownHost(h) || h.contains("gitlab") || h.contains("gitea") || h.contains("forgejo")
                    || h.startsWith("git.") || h.startsWith("code.") || h.startsWith("src.")) {
                if (count < 2 && !h.equals("f-droid.org") && !h.equals("apt.izzysoft.de")) return null;
                StringBuilder out = new StringBuilder();
                out.append(uri.getScheme() == null ? "https" : uri.getScheme()).append("://").append(host);
                if (uri.getPort() > 0) out.append(':').append(uri.getPort());
                out.append(path.replaceAll("/+$", ""));
                String result = out.toString().replaceAll("(?i)\\.git$", "");
                return result;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean isKnownHost(String host) {
        for (String known : KNOWN_HOSTS) {
            if (host.equals(known) || host.endsWith("." + known)) return true;
        }
        return false;
    }
}
