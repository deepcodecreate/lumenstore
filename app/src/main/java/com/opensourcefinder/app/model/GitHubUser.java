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

public class GitHubUser implements Serializable {

    private String login;

    private String name;

    private String bio;

    @SerializedName("avatar_url")
    private String avatarUrl;

    @SerializedName("html_url")
    private String htmlUrl;

    private String company;

    private String location;

    private String blog;

    @SerializedName("public_repos")
    private int publicRepos;

    private int followers;

    private int following;

    @SerializedName("public_gists")
    private int publicGists;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("twitter_username")
    private String twitterUsername;

    public String getLogin() {
        return login;
    }

    public String getName() {
        return name;
    }

    public String getBio() {
        return bio;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public String getCompany() {
        return company;
    }

    public String getLocation() {
        return location;
    }

    public String getBlog() {
        return blog;
    }

    public int getPublicRepos() {
        return publicRepos;
    }

    public int getFollowers() {
        return followers;
    }

    public int getFollowing() {
        return following;
    }

    public String getDisplayName() {
        return (name != null && !name.trim().isEmpty()) ? name : login;
    }

    public int getPublicGists() {
        return publicGists;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getTwitterUsername() {
        return twitterUsername;
    }
}
