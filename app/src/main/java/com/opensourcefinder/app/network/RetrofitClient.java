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

import java.util.concurrent.TimeUnit;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

public class RetrofitClient {
    private static final String GITHUB_BASE_URL = "https://api.github.com/";
    private static final String FDROID_BASE_URL = "https://f-droid.org/";
    private static GitHubApiService githubService;
    private static GitHubApiService publicGithubService;
    private static FdroidApiService fdroidService;

    private static OkHttpClient client() {
        return client(true);
    }

    private static OkHttpClient client(boolean withAuthentication) {
        Interceptor authInterceptor = chain -> {
            Request original = chain.request();

            Request.Builder builder = original.newBuilder()
                    .header("X-GitHub-Api-Version", "2022-11-28");

            if (original.header("Accept") == null) {
                builder.header("Accept", "application/vnd.github+json");
            }
            if (withAuthentication && GitHubTokenProvider.hasToken()) {
                builder.header("Authorization", "Bearer " + GitHubTokenProvider.getToken());
            }
            return chain.proceed(builder.build());
        };
        return new OkHttpClient.Builder()
                .dns(AppDns.INSTANCE)
                .addInterceptor(authInterceptor)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized GitHubApiService getService() {
        if (githubService == null) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(GITHUB_BASE_URL)
                    .client(client())
                    .addConverterFactory(ScalarsConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            githubService = retrofit.create(GitHubApiService.class);
        }
        return githubService;
    }

    public static synchronized GitHubApiService getPublicService() {
        if (publicGithubService == null) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(GITHUB_BASE_URL)
                    .client(client(false))
                    .addConverterFactory(ScalarsConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            publicGithubService = retrofit.create(GitHubApiService.class);
        }
        return publicGithubService;
    }

    public static synchronized FdroidApiService getFdroidService() {
        if (fdroidService == null) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(FDROID_BASE_URL)
                    .client(new OkHttpClient.Builder()
                            .dns(AppDns.INSTANCE)
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(20, TimeUnit.SECONDS)
                            .callTimeout(30, TimeUnit.SECONDS)
                            .build())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            fdroidService = retrofit.create(FdroidApiService.class);
        }
        return fdroidService;
    }
}
