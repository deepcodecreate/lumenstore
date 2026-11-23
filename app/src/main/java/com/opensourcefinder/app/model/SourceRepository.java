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

public class SourceRepository implements Serializable {
    private String url;
    private String name;
    private String type;
    private String packageName;
    private String channel;
    private String tagFilter;
    private String minVersion;
    private String assetPattern;
    private String baselineTag;

    public SourceRepository() {}

    public SourceRepository(String url, String name, String type) {
        this.url = url;
        this.name = name;
        this.type = type;
    }

    public String getUrl() { return url; }
    public String getName() { return name; }
    public String getType() { return type; }
    public void setUrl(String url) { this.url = url; }
    public void setName(String name) { this.name = name; }
    public void setType(String type) { this.type = type; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getTagFilter() { return tagFilter; }
    public void setTagFilter(String tagFilter) { this.tagFilter = tagFilter; }
    public String getMinVersion() { return minVersion; }
    public void setMinVersion(String minVersion) { this.minVersion = minVersion; }
    public String getAssetPattern() { return assetPattern; }
    public void setAssetPattern(String assetPattern) { this.assetPattern = assetPattern; }
    public String getBaselineTag() { return baselineTag; }
    public void setBaselineTag(String baselineTag) { this.baselineTag = baselineTag; }

    private static boolean filled(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public boolean hasReleaseSettings() {
        return (filled(channel) && !"auto".equals(channel)) || filled(tagFilter) || filled(minVersion) || filled(assetPattern);
    }

    public boolean hasCustomization() {
        return hasReleaseSettings() || filled(packageName) || filled(baselineTag);
    }
}
