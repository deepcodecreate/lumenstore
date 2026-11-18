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

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

final class StatsCardUrls {

    private StatsCardUrls() {}

    private static final Pattern ANIMATED_STATS_CARD_HOST = Pattern.compile(
            "streak-stats|github-readme-stats|github-profile-trophy|readme-activity-graph",
            Pattern.CASE_INSENSITIVE);

    @NonNull
    static String withAnimationsDisabled(@NonNull String url) {
        if (!ANIMATED_STATS_CARD_HOST.matcher(url).find()) return url;
        if (url.contains("disable_animations=")) return url;
        return url + (url.contains("?") ? "&" : "?") + "disable_animations=true";
    }
}
