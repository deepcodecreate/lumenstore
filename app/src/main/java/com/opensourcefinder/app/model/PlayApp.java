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

package com.opensourcefinder.app.model;

import java.io.Serializable;

public class PlayApp implements Serializable {
    public String packageName;
    public String name;
    public String developer;
    public String iconUrl;
    public String description;

    public String version;
    public String rating;
    public String ratingCount;
    public String installs;
    public String category;
    public String url;

    public boolean loaded;

    public PlayApp() {}

    public PlayApp(String packageName) {
        this.packageName = packageName;
    }

    public String displayName() {
        return name == null || name.trim().isEmpty() ? packageName : name;
    }
}
