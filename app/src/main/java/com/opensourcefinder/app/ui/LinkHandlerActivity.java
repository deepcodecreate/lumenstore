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

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.loadingindicator.LoadingIndicator;
import com.opensourcefinder.app.MainActivity;
import com.opensourcefinder.app.R;
import com.opensourcefinder.app.install.InstallState;
import com.opensourcefinder.app.model.ExploreApp;
import com.opensourcefinder.app.model.FdroidPackage;
import com.opensourcefinder.app.model.MatchedApp;
import com.opensourcefinder.app.network.PlayStoreClient;
import com.opensourcefinder.app.network.RetrofitClient;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Response;

public class LinkHandlerActivity extends BaseActivity {

    public static final String EXTRA_OPEN_REPO = "open_repo";

    private static final Set<String> GITHUB_RESERVED = new HashSet<>(Arrays.asList(
            "orgs", "settings", "marketplace", "topics", "search", "explore", "login", "sponsors",
            "notifications", "features", "pricing", "about", "enterprise", "collections", "trending",
            "apps", "users", "sessions", "new", "issues", "pulls", "codespaces", "organizations",
            "readme", "security", "customer-stories", "solutions", "resources", "site", "join",
            "account", "contact", "events", "git-guides", "premium-support"));
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");
    private static final Pattern URL_IN_TEXT = Pattern.compile("(https?://|market://|lumenstore://)[^\\s<>\"']+");

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Uri link;
    private TextView title;
    private TextView subtitle;
    private TextView message;
    private LoadingIndicator progress;
    private MaterialButton browserButton;
    private MaterialButton cancelButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        link = resolveLink(getIntent());
        if (link == null) {
            if (getIntent() != null && Intent.ACTION_SEND.equals(getIntent().getAction())) {
                Toast.makeText(this, R.string.link_unsupported, Toast.LENGTH_SHORT).show();
            }
            finish();
            return;
        }

        String scheme = link.getScheme() == null ? "" : link.getScheme().toLowerCase(Locale.ROOT);
        String host = link.getHost() == null ? "" : link.getHost().toLowerCase(Locale.ROOT);

        if ("market".equals(scheme) || host.equals("play.google.com")) {
            String pkg = PlayStoreClient.extractPackage(link.toString());
            if (pkg == null) { openExternally(); return; }
            Intent play = new Intent(this, PlaySearchActivity.class);
            play.putExtra(PlaySearchActivity.EXTRA_PACKAGE, pkg);
            startActivity(play);
            finish();
            return;
        }

        String lumenPackage = LumenLinks.parse(link);
        boolean github = host.equals("github.com") || host.equals("www.github.com");
        boolean fdroid = host.equals("f-droid.org") || host.equals("www.f-droid.org");

        if (github) {
            List<String> segments = link.getPathSegments();
            if (segments.size() < 2 || GITHUB_RESERVED.contains(segments.get(0).toLowerCase(Locale.ROOT))) {
                openExternally();
                return;
            }
            String repo = segments.get(1);
            if (repo.endsWith(".git")) repo = repo.substring(0, repo.length() - 4);
            openRepository(segments.get(0), repo);
            return;
        }

        String fdroidPackage = fdroid ? fdroidPackage(link.getPathSegments()) : null;
        if (lumenPackage == null && fdroidPackage == null) {
            openExternally();
            return;
        }

        setContentView(R.layout.activity_link_handler);
        ThemeManager.applyWindow(this);
        title = findViewById(R.id.linkTitle);
        subtitle = findViewById(R.id.linkSubtitle);
        message = findViewById(R.id.linkMessage);
        progress = findViewById(R.id.linkProgress);
        browserButton = findViewById(R.id.linkBrowserButton);
        cancelButton = findViewById(R.id.linkCancelButton);
        findViewById(R.id.linkAddButton).setVisibility(View.GONE);
        browserButton.setVisibility(View.GONE);
        cancelButton.setOnClickListener(v -> finish());
        browserButton.setOnClickListener(v -> openExternally());

        if (lumenPackage != null) {
            subtitle.setText(R.string.link_lumen_subtitle);
            openApp(lumenPackage, false);
        } else {
            subtitle.setText(R.string.link_fdroid_subtitle);
            openApp(fdroidPackage, true);
        }
    }

    @Nullable
    private static Uri resolveLink(@Nullable Intent intent) {
        if (intent == null) return null;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (text == null) return null;
            Matcher matcher = URL_IN_TEXT.matcher(text);
            if (!matcher.find()) return null;
            String raw = matcher.group();
            while (raw.endsWith(".") || raw.endsWith(",") || raw.endsWith(")") || raw.endsWith(";")) {
                raw = raw.substring(0, raw.length() - 1);
            }
            return Uri.parse(raw);
        }
        return intent.getData();
    }

    private void openRepository(String owner, String repo) {
        Intent home = new Intent(this, MainActivity.class);
        home.putExtra("open_tab", MainActivity.TAG_GITHUB);
        home.putExtra(EXTRA_OPEN_REPO, owner + "/" + repo);
        home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(home);
        finish();
    }

    private void openApp(final String pkg, final boolean fdroidFallback) {
        title.setText(pkg);
        message.setText(R.string.loading);
        progress.setVisibility(View.VISIBLE);
        io.execute(() -> {
            ExploreApp found = LumenLinks.find(this, pkg);
            MatchedApp fallback = found == null && fdroidFallback ? fetchFdroid(pkg) : null;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (found != null) {
                    Intent detail = new Intent(this, ExploreDetailActivity.class);
                    detail.putExtra(ExploreDetailActivity.EXTRA_EXPLORE_APP, found);
                    startActivity(detail);
                    finish();
                    return;
                }
                if (fallback != null) {
                    Intent detail = new Intent(this, ExploreDetailActivity.class);
                    detail.putExtra(ExploreDetailActivity.EXTRA_MATCHED_APP, fallback);
                    startActivity(detail);
                    finish();
                    return;
                }
                progress.setVisibility(View.GONE);
                message.setText(fdroidFallback ? R.string.link_fdroid_not_found : R.string.link_lumen_not_found);
                if (fdroidFallback) browserButton.setVisibility(View.VISIBLE);
            });
        });
    }

    @Nullable
    private MatchedApp fetchFdroid(String pkg) {
        FdroidPackage info = null;
        try {
            Response<FdroidPackage> resp = RetrofitClient.getFdroidService().getPackage(pkg).execute();
            if (resp.isSuccessful()) info = resp.body();
        } catch (Exception ignored) {
            return null;
        }
        if (info == null || info.getPackages() == null || info.getPackages().isEmpty()) return null;
        long code = info.getSuggestedVersionCode();
        String versionName = null;
        for (FdroidPackage.Version v : info.getPackages()) {
            if (v != null && v.getVersionCode() == code) { versionName = v.getVersionName(); break; }
        }
        if (versionName == null) {
            FdroidPackage.Version first = info.getPackages().get(0);
            code = first.getVersionCode();
            versionName = first.getVersionName();
        }
        String label = fdroidTitle(pkg);
        if (label == null || label.isEmpty()) label = pkg;
        MatchedApp m = new MatchedApp();
        m.setPackageName(pkg);
        m.setAppLabel(label);
        m.setSourceType("fdroid");
        m.setSourceUrl("https://f-droid.org/packages/" + pkg);
        m.setOrigin("manual");
        m.setLatestTag(versionName);
        m.setLatestVersionCode(code);
        m.setDownloadUrl("https://f-droid.org/repo/" + pkg + "_" + code + ".apk");
        InstallState.refresh(this, m);
        return m;
    }

    private String fdroidTitle(String pkg) {
        HttpURLConnection c = null;
        try {
            c = com.opensourcefinder.app.network.HttpClients.open(new URL("https://f-droid.org/packages/" + pkg + "/"));
            c.setConnectTimeout(10000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent", "OpenSourceFinder/1.0");
            if (c.getResponseCode() != 200) return null;
            byte[] data = new byte[64 * 1024];
            int read = 0, n;
            java.io.InputStream in = c.getInputStream();
            while (read < data.length && (n = in.read(data, read, data.length - read)) > 0) read += n;
            String html = new String(data, 0, read, java.nio.charset.StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("<title>\\s*([^<|]+?)\\s*\\|").matcher(html);
            return m.find() ? m.group(1).replace("&amp;", "&").trim() : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String fdroidPackage(List<String> segments) {
        for (int i = 0; i < segments.size() - 1; i++) {
            if ("packages".equals(segments.get(i))) {
                String pkg = segments.get(i + 1);
                return PACKAGE.matcher(pkg).matches() ? pkg : null;
            }
        }
        return null;
    }

    private void openExternally() {
        openInBrowser(this, link);
        finish();
    }

    public static boolean openInBrowser(Context context, Uri uri) {
        try {
            Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"));
            probe.addCategory(Intent.CATEGORY_BROWSABLE);
            PackageManager pm = context.getPackageManager();
            String own = context.getPackageName();
            String browser = null;
            for (ResolveInfo ri : pm.queryIntentActivities(probe, 0)) {
                String p = ri.activityInfo.packageName;
                if (!own.equals(p)) { browser = p; break; }
            }
            ResolveInfo def = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY);
            if (def != null && def.activityInfo != null) {
                String p = def.activityInfo.packageName;
                if (!own.equals(p) && !"android".equals(p)) browser = p;
            }
            if (browser == null) return false;
            Intent view = new Intent(Intent.ACTION_VIEW, uri);
            view.setPackage(browser);
            if (!(context instanceof android.app.Activity)) view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(view);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
