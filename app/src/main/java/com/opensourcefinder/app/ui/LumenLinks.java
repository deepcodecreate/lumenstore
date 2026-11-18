package com.opensourcefinder.app.ui;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.opensourcefinder.app.install.InstallState;
import com.opensourcefinder.app.model.ExploreApp;
import com.opensourcefinder.app.model.InstalledAppInfo;
import com.opensourcefinder.app.scanner.AppScanner;
import com.opensourcefinder.app.scanner.FdroidIndexManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class LumenLinks {

    public static final String SCHEME = "lumenstore";
    public static final String HOST = "app";
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");

    private LumenLinks() {}

    public static String build(String packageName) {
        return SCHEME + "://" + HOST + "/" + Uri.encode(packageName);
    }

    public static String shareText(@Nullable String name, String packageName, @Nullable String sourceUrl) {
        StringBuilder text = new StringBuilder();
        if (name != null && !name.trim().isEmpty()) text.append(name.trim()).append('\n');
        text.append(build(packageName));
        if (sourceUrl != null && !sourceUrl.trim().isEmpty()) text.append('\n').append(sourceUrl.trim());
        return text.toString();
    }

    @Nullable
    public static String parse(@Nullable Uri uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) return null;
        if (!SCHEME.equalsIgnoreCase(uri.getScheme()) || !HOST.equals(uri.getHost().toLowerCase(Locale.ROOT))) return null;
        List<String> segments = uri.getPathSegments();
        String pkg = segments.isEmpty() ? uri.getQueryParameter("id") : segments.get(0);
        return pkg != null && PACKAGE.matcher(pkg).matches() ? pkg : null;
    }

    @Nullable
    public static ExploreApp find(Context context, String packageName) {
        try {
            Set<String> packages = new HashSet<>();
            Map<String, Long> versions = new HashMap<>();
            for (InstalledAppInfo info : AppScanner.listUserInstalledApps(context)) {
                packages.add(info.getPackageName());
                versions.put(info.getPackageName(), info.getVersionCode());
            }
            List<ExploreApp> catalog = new FdroidIndexManager(context).loadExploreCatalog(packages, null);
            Map<String, InstallState.Info> live = InstallState.readAll(context);
            for (ExploreApp app : catalog) {
                if (!packageName.equalsIgnoreCase(app.getPackageName())) continue;
                Long code = versions.get(app.getPackageName());
                app.setInstalled(code != null);
                app.setInstalledVersionCode(code != null ? code : 0L);
                InstallState.refresh(app, live.get(app.getPackageName()));
                return app;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }
}
