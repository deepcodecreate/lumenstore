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

import java.net.HttpURLConnection;
import java.net.URL;
import okhttp3.OkHttpClient;

public final class HttpClients {
    private static final OkHttpClient SHARED = new OkHttpClient.Builder()
            .dns(AppDns.INSTANCE)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    private HttpClients() {}

    public static OkHttpClient shared() {
        return SHARED;
    }

    public static HttpURLConnection open(URL url) throws java.io.IOException {
        return new OkHttpUrlConnection(url, SHARED);
    }
}
