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

import com.opensourcefinder.app.model.GitHubCodeSearchResponse;
import com.opensourcefinder.app.model.GitHubEvent;
import com.opensourcefinder.app.model.GitHubOrg;
import com.opensourcefinder.app.model.GitHubRelease;
import com.opensourcefinder.app.model.GitHubRepo;
import com.opensourcefinder.app.model.GitHubSearchResponse;
import com.opensourcefinder.app.model.GitHubTree;
import com.opensourcefinder.app.model.GitHubUser;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface GitHubApiService {

    @GET("search/repositories")
    Call<GitHubSearchResponse> searchRepositories(@Query("q") String query, @Query("per_page") int perPage);

    @GET("search/repositories")
    Call<GitHubSearchResponse> searchRepositories(@Query("q") String query,
                                                   @Query("per_page") int perPage,
                                                   @Query("sort") String sort,
                                                   @Query("order") String order);

    @GET("search/repositories")
    Call<GitHubSearchResponse> searchRepositoriesPage(@Query("q") String query,
                                                       @Query("per_page") int perPage,
                                                       @Query("page") int page,
                                                       @Query("sort") String sort,
                                                       @Query("order") String order);

    @GET("search/code")
    Call<GitHubCodeSearchResponse> searchCode(@Query("q") String query, @Query("per_page") int perPage);

    @GET("repos/{owner}/{repo}")
    Call<GitHubRepo> getRepo(@Path("owner") String owner, @Path("repo") String repo);

    @GET("repos/{owner}/{repo}/releases/latest")
    Call<GitHubRelease> getLatestRelease(@Path("owner") String owner, @Path("repo") String repo);

    @GET("repos/{owner}/{repo}/releases")
    Call<List<GitHubRelease>> listReleases(@Path("owner") String owner, @Path("repo") String repo, @Query("per_page") int perPage);

    @GET("repos/{owner}/{repo}/readme")
    Call<String> getReadmeRaw(@Path("owner") String owner, @Path("repo") String repo, @Header("Accept") String acceptRaw);

    @GET("repos/{owner}/{repo}/git/trees/{branch}")
    Call<GitHubTree> getTree(@Path("owner") String owner,
                              @Path("repo") String repo,
                              @Path("branch") String branch,
                              @Query("recursive") int recursive);

    @GET("repos/{owner}/{repo}/contents/{path}")
    Call<String> getFileContentRaw(@Path("owner") String owner,
                                    @Path("repo") String repo,
                                    @Path(value = "path", encoded = true) String path,
                                    @Header("Accept") String acceptRaw);

    @GET("user")
    Call<GitHubUser> getAuthenticatedUser();

    @GET("user/repos")
    Call<List<GitHubRepo>> listMyRepos(@Query("per_page") int perPage,
                                       @Query("sort") String sort,
                                       @Query("affiliation") String affiliation);

    @GET("user/starred")
    Call<List<GitHubRepo>> listMyStarred(@Query("per_page") int perPage, @Query("page") int page);

    @GET("user/orgs")
    Call<List<GitHubOrg>> listMyOrgs(@Query("per_page") int perPage);

    @GET("user/followers")
    Call<List<GitHubUser>> listMyFollowers(@Query("per_page") int perPage);

    @GET("users/{login}/events/public")
    Call<List<GitHubEvent>> listPublicEvents(@Path("login") String login, @Query("per_page") int perPage);
}
