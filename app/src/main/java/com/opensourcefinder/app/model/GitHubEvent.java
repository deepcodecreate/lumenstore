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

import java.io.Serializable;

public class GitHubEvent implements Serializable {
    private String type;

    @SerializedName("created_at")
    private String createdAt;

    private Repo repo;
    private Payload payload;

    public String getType() { return type; }
    public String getCreatedAt() { return createdAt; }
    public String getRepoName() { return repo != null ? repo.name : null; }
    public String getAction() { return payload != null ? payload.action : null; }
    public String getRefType() { return payload != null ? payload.refType : null; }

    private static class Repo implements Serializable {
        private String name;
    }

    private static class Payload implements Serializable {
        private String action;

        @SerializedName("ref_type")
        private String refType;
    }
}
