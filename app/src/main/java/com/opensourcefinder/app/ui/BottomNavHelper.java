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

import android.app.Activity;
import android.content.Intent;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.opensourcefinder.app.MainActivity;
import com.opensourcefinder.app.R;

public final class BottomNavHelper {
    private BottomNavHelper() {}

    public static void bind(Activity activity, int selectedId) {
        BottomNavigationView nav = activity.findViewById(R.id.bottomNav);
        if (nav == null) return;
        nav.setSelectedItemId(selectedId);
        NavCapsule.install(nav);
        nav.post(() -> MotionHelper.bounceNavItem(nav, selectedId));
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == selectedId) return true;
            String tag;
            if (id == R.id.nav_home) tag = MainActivity.TAG_HOME;
            else if (id == R.id.nav_explore) tag = MainActivity.TAG_EXPLORE;
            else if (id == R.id.nav_github) tag = MainActivity.TAG_GITHUB;
            else if (id == R.id.nav_downloads) tag = MainActivity.TAG_DOWNLOADS;
            else return false;
            Intent intent = new Intent(activity, MainActivity.class);
            intent.putExtra("open_tab", tag);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
            activity.overridePendingTransition(0, 0);
            if (!(activity instanceof MainActivity)) activity.finish();
            return true;
        });
    }

    private static int tabOrder(int id) {
        if (id == R.id.nav_home) return 0;
        if (id == R.id.nav_explore) return 1;
        if (id == R.id.nav_downloads) return 3;
        if (id == R.id.nav_settings) return 4;
        return 0;
    }
}
