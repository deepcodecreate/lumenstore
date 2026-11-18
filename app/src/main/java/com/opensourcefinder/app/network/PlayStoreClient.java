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

package com.opensourcefinder.app.network;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.opensourcefinder.app.install.InstallState;
import com.opensourcefinder.app.model.MatchedApp;
import com.opensourcefinder.app.model.PlayApp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlayStoreClient {
    public static final String SOURCE_TYPE = "play";

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final int MAX_BYTES = 6 * 1024 * 1024;
    private static final int MAX_SEARCH_RESULTS = 15;

    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");
    private static final Pattern ID_PARAM = Pattern.compile("[?&]id=([A-Za-z0-9_.]+)");
    private static final Pattern DETAILS_LINK = Pattern.compile("/store/apps/details\\?id=([A-Za-z0-9_.]+)");
    private static final Pattern JSON_LD = Pattern.compile(
            "<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>", Pattern.DOTALL);
    private static final Pattern VERSION_FALLBACK = Pattern.compile(
            "\\[\\[\\[\"(\\d+(?:\\.\\d+)+[^\"\\\\]{0,24})\"\\]\\]");

    private PlayStoreClient() {}

    public static String extractPackage(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.isEmpty()) return null;
        Matcher m = ID_PARAM.matcher(t);
        if (m.find()) return m.group(1);
        if (PACKAGE.matcher(t).matches()) return t;
        return null;
    }

    public static boolean looksLikeLink(String raw) {
        if (raw == null) return false;
        String t = raw.trim().toLowerCase(Locale.ROOT);
        return t.startsWith("http://") || t.startsWith("https://") || t.startsWith("market://")
                || t.contains("play.google.com");
    }

    public static boolean isPlayUrl(String url) {
        if (url == null) return false;
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains("play.google.com/store/apps/details");
    }

    public static boolean isPlayApp(MatchedApp app) {
        return app != null && (SOURCE_TYPE.equalsIgnoreCase(app.getSourceType()) || isPlayUrl(app.getSourceUrl()));
    }

    public static String appUrl(String packageName) {
        return "https://play.google.com/store/apps/details?id=" + packageName;
    }

    public static void openInPlayStore(Context context, String packageName) {
        if (context == null || packageName == null) return;
        try {
            Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName));
            market.setPackage("com.android.vending");
            market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(market);
            return;
        } catch (Exception ignored) {}
        try {
            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(appUrl(packageName)));
            web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(web);
        } catch (Exception ignored) {}
    }

    public static List<String> search(String query) throws IOException {
        String url = "https://play.google.com/store/search?q=" + URLEncoder.encode(query, "UTF-8")
                + "&c=apps&hl=en&gl=US";
        String body = normalise(download(url));
        Set<String> ids = new LinkedHashSet<>();
        Matcher m = DETAILS_LINK.matcher(body);
        while (m.find() && ids.size() < MAX_SEARCH_RESULTS) ids.add(m.group(1));
        return new ArrayList<>(ids);
    }

    public static PlayApp fetchApp(String packageName) throws IOException {
        if (packageName == null || !PACKAGE.matcher(packageName).matches()) return null;
        String html;
        try {
            html = download(appUrl(packageName) + "&hl=en&gl=US");
        } catch (FileNotFoundException notFound) {
            return null;
        }
        PlayApp app = new PlayApp(packageName);
        app.url = appUrl(packageName);

        String title = meta(html, "og:title");
        if (title != null) title = title.replaceAll("(?i)\\s+-\\s+Apps on Google Play.*$", "").trim();
        app.name = title;
        app.iconUrl = sizedIcon(meta(html, "og:image"));
        app.description = meta(html, "og:description");

        JSONObject ld = jsonLd(html);
        if (ld != null) {
            String name = ld.optString("name", "");
            if (!name.isEmpty()) app.name = name;
            String desc = ld.optString("description", "");
            if (!desc.isEmpty()) app.description = desc;
            String image = ld.optString("image", "");
            if (!image.isEmpty() && app.iconUrl == null) app.iconUrl = sizedIcon(image);
            String category = ld.optString("applicationCategory", "");
            if (!category.isEmpty()) app.category = category.replace('_', ' ');
            JSONObject author = ld.optJSONObject("author");
            if (author != null) app.developer = author.optString("name", null);
            JSONObject rating = ld.optJSONObject("aggregateRating");
            if (rating != null) {
                app.rating = rating.optString("ratingValue", null);
                app.ratingCount = rating.optString("ratingCount", null);
            }
        }
        readBlob(html, app);
        app.description = clean(app.description);
        app.loaded = app.name != null && !app.name.isEmpty();
        return app.loaded ? app : null;
    }

    private static void readBlob(String html, PlayApp app) {
        String version = null;
        int from = 0;
        while (true) {
            int cb = html.indexOf("AF_initDataCallback", from);
            if (cb < 0) break;
            from = cb + 19;
            int data = html.indexOf("data:", cb);
            if (data < 0) break;
            int open = html.indexOf('[', data);
            if (open < 0 || open - data > 8) continue;
            String json = balanced(html, open);
            if (json == null) continue;
            try {
                JSONArray root = new JSONArray(json);
                String v = pathString(root, 1, 2, 140, 0, 0, 0);
                if (v != null && version == null) version = v;
                String installs = pathString(root, 1, 2, 13, 0);
                if (installs != null && app.installs == null) app.installs = installs;
            } catch (Exception ignored) {}
        }
        if (version == null) {
            Matcher m = VERSION_FALLBACK.matcher(normalise(html));
            if (m.find()) version = m.group(1);
        }
        if (version != null && version.matches(".*\\d.*")) app.version = version.trim();
    }

    private static String pathString(JSONArray root, int... path) {
        Object cur = root;
        for (int index : path) {
            if (!(cur instanceof JSONArray)) return null;
            JSONArray arr = (JSONArray) cur;
            if (index >= arr.length() || arr.isNull(index)) return null;
            cur = arr.opt(index);
        }
        return cur instanceof String ? (String) cur : null;
    }

    private static String balanced(String s, int open) {
        int depth = 0;
        boolean inString = false;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\') i++;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return s.substring(open, i + 1);
            }
        }
        return null;
    }

    private static JSONObject jsonLd(String html) {
        Matcher m = JSON_LD.matcher(html);
        while (m.find()) {
            try {
                JSONObject o = new JSONObject(m.group(1).trim());
                String type = o.optString("@type", "");
                if (type.contains("SoftwareApplication") || type.contains("MobileApplication")) return o;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String meta(String html, String property) {
        Pattern p = Pattern.compile("<meta[^>]+(?:property|name)=[\"']" + Pattern.quote(property)
                + "[\"'][^>]*content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(html);
        if (m.find()) return htmlDecode(m.group(1));
        Pattern reversed = Pattern.compile("<meta[^>]+content=[\"']([^\"']*)[\"'][^>]*(?:property|name)=[\"']"
                + Pattern.quote(property) + "[\"']", Pattern.CASE_INSENSITIVE);
        m = reversed.matcher(html);
        return m.find() ? htmlDecode(m.group(1)) : null;
    }

    private static String sizedIcon(String url) {
        if (url == null || url.isEmpty()) return null;
        int eq = url.lastIndexOf('=');
        if (eq > 0 && url.contains("googleusercontent.com")) return url.substring(0, eq) + "=s256";
        return url;
    }

    private static String clean(String s) {
        if (s == null) return null;
        String t = htmlDecode(s).replace("\r", "").trim();
        return t.isEmpty() ? null : t;
    }

    private static String htmlDecode(String s) {
        if (s == null) return null;
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
    }

    private static String normalise(String s) {
        return s.replace("\\u003d", "=").replace("\\u0026", "&").replace("\\/", "/");
    }

    public static MatchedApp toMatchedApp(Context context, PlayApp app) {
        MatchedApp m = new MatchedApp();
        m.setPackageName(app.packageName);
        m.setAppLabel(app.displayName());
        m.setSourceType(SOURCE_TYPE);
        m.setSourceUrl(appUrl(app.packageName));
        m.setOrigin("manual");
        m.setLatestTag(app.version);
        m.setDescription(app.description);
        m.setIconUrl(app.iconUrl);
        InstallState.refresh(context, m);
        return m;
    }

    private static String download(String address) throws IOException {
        HttpURLConnection c = com.opensourcefinder.app.network.HttpClients.open(new URL(address));
        try {
            c.setConnectTimeout(12000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            int code = c.getResponseCode();
            if (code == 404) throw new FileNotFoundException(address);
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
            String host = c.getURL().getHost();
            if (host != null && host.startsWith("consent.")) throw new IOException("consent required");
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BYTES) break;
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }
}
