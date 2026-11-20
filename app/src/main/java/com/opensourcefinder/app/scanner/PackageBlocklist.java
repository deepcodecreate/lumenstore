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

package com.opensourcefinder.app.scanner;

public final class PackageBlocklist {
    private static final String[] BLOCKED = {"com.rk.xededitor"};

    private PackageBlocklist() {}

    public static boolean isBlocked(String packageName) {
        if (packageName == null) return false;
        for (String blocked : BLOCKED) {
            if (packageName.equals(blocked) || packageName.startsWith(blocked + "@")) return true;
        }
        return false;
    }
}
