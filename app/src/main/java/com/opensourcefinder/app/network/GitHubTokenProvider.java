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
import android.content.SharedPreferences;

public class GitHubTokenProvider {

    private static final String PREFS_NAME = "github_auth_prefs";
    private static final String KEY_TOKEN = "github_token";

    private static volatile String cachedToken = "";
    private static volatile boolean initialized = false;

    public static synchronized void init(Context context) {
        if (initialized) return;
        SharedPreferences prefs = prefs(context);
        cachedToken = prefs.getString(KEY_TOKEN, "");
        initialized = true;
    }

    public static void setToken(String token) {
        cachedToken = token == null ? "" : token.trim();
    }

    public static void saveToken(Context context, String token) {
        setToken(token);
        prefs(context).edit().putString(KEY_TOKEN, cachedToken).apply();
    }

    public static void clearToken(Context context) {
        cachedToken = "";
        prefs(context).edit().remove(KEY_TOKEN).apply();
    }

    public static String getToken() {
        return cachedToken;
    }

    public static boolean hasToken() {
        return cachedToken != null && !cachedToken.trim().isEmpty();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
