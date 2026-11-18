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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GitHubRepo implements Serializable {

    private String name;

    @SerializedName("full_name")
    private String fullName;

    @SerializedName("html_url")
    private String htmlUrl;

    private String description;

    @SerializedName("stargazers_count")
    private int stargazersCount;

    @SerializedName("forks_count")
    private int forksCount;

    @SerializedName("open_issues_count")
    private int openIssuesCount;

    @SerializedName("default_branch")
    private String defaultBranch;

    private String language;
    private List<String> topics;
    private License license;
    private String pushed_at;

    private boolean fork;

    private Owner owner;

    public GitHubRepo() {}

    public GitHubRepo(String ownerLogin, String name, String description,
                      String defaultBranch, boolean fork) {
        this(ownerLogin, name, description, defaultBranch, fork,
                ownerLogin == null || name == null ? null : "https://github.com/" + ownerLogin + "/" + name);
    }

    public GitHubRepo(String ownerLogin, String name, String description,
                      String defaultBranch, boolean fork, String htmlUrl) {
        this.name = name;
        this.fullName = ownerLogin == null || name == null ? name : ownerLogin + "/" + name;
        this.htmlUrl = htmlUrl;
        this.description = description;
        this.defaultBranch = defaultBranch;
        this.fork = fork;
        this.owner = new Owner();
        this.owner.login = ownerLogin;
    }

    public static class Owner implements Serializable {
        private String login;

        @SerializedName("avatar_url")
        private String avatarUrl;

        public String getLogin() {
            return login;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }
    }

    public String getName() {
        return name;
    }

    public String getFullName() {
        return fullName;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public String getDescription() {
        return description;
    }

    public int getStargazersCount() {
        return stargazersCount;
    }

    public int getForksCount() {
        return forksCount;
    }

    public int getOpenIssuesCount() {
        return openIssuesCount;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public String getLanguage() {
        return language;
    }

    public List<String> getTopics() {
        return topics == null ? Collections.emptyList() : new ArrayList<>(topics);
    }

    public String getLicenseName() {
        return license == null ? null : license.name;
    }

    public String getPushedAt() {
        return pushed_at;
    }

    private static class License implements Serializable {
        private String name;
    }

    public boolean isFork() {
        return fork;
    }

    public String getOwnerAvatarUrl() {
        return owner == null ? null : owner.getAvatarUrl();
    }

    public String getOwnerLogin() {
        return owner != null ? owner.getLogin() : null;
    }
}
