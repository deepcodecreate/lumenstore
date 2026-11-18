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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;

public final class IconLoader {
    private IconLoader() {}

    public static void load(@NonNull ImageView view, @Nullable String url) {
        load(view, url, null);
    }

    public static void load(@NonNull ImageView view, @Nullable String url, @Nullable RequestOptions options) {
        Context context = view.getContext();
        RequestBuilder<Drawable> request = Glide.with(view).load(url);
        if (options != null) request = request.apply(options);

        request.placeholder(new ShimmerDrawable(context, false))
                .error(empty(context))
                .into(view);
    }

    public static Drawable empty(@NonNull Context context) {
        return new ShimmerDrawable(context, false, false);
    }

    public static Drawable loading(@NonNull Context context) {
        return new ShimmerDrawable(context, false, true);
    }

    public static void loadAvatar(@NonNull ImageView view, @Nullable String url) {
        Context context = view.getContext();
        Glide.with(view).load(url)
                .placeholder(new ShimmerDrawable(context, true))
                .error(new ShimmerDrawable(context, true, false))
                .transform(new CircleCrop())
                .into(view);
    }
}
