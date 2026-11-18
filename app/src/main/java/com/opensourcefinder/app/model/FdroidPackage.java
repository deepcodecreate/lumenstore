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

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class FdroidPackage {
    @SerializedName("packageName")
    private String packageName;
    @SerializedName("suggestedVersionCode")
    private long suggestedVersionCode;
    @SerializedName("packages")
    private List<Version> packages;

    public String getPackageName() { return packageName; }
    public long getSuggestedVersionCode() { return suggestedVersionCode; }
    public List<Version> getPackages() { return packages; }

    public static class Version {
        @SerializedName("versionName")
        private String versionName;
        @SerializedName("versionCode")
        private long versionCode;
        public String getVersionName() { return versionName; }
        public long getVersionCode() { return versionCode; }
    }
}
