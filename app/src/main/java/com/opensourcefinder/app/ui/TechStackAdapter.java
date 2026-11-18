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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PictureDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.opensourcefinder.app.R;

import java.util.List;

public final class TechStackAdapter extends RecyclerView.Adapter<TechStackAdapter.BadgeHolder> {

    private static final int PLACEHOLDER_HEIGHT_DP = 20;
    private static final int ITEM_MARGIN_DP = 8;

    private final List<String> badgeUrls;
    private final List<String> badgeWidths;
    private final List<String> badgeHeights;
    private final List<String> badgeLinks;

    public TechStackAdapter(@NonNull List<String> badgeUrls) {
        this(badgeUrls, null, null, null);
    }

    public TechStackAdapter(@NonNull List<String> badgeUrls,
                            @Nullable List<String> badgeWidths,
                            @Nullable List<String> badgeHeights) {
        this(badgeUrls, badgeWidths, badgeHeights, null);
    }

    public TechStackAdapter(@NonNull List<String> badgeUrls,
                            @Nullable List<String> badgeWidths,
                            @Nullable List<String> badgeHeights,
                            @Nullable List<String> badgeLinks) {
        this.badgeUrls = badgeUrls;
        this.badgeWidths = badgeWidths;
        this.badgeHeights = badgeHeights;
        this.badgeLinks = badgeLinks;
    }

    @NonNull
    @Override
    public BadgeHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImageView imageView = (ImageView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tech_badge, parent, false);
        return new BadgeHolder(imageView);
    }

    @Override
    public void onBindViewHolder(@NonNull BadgeHolder holder, int position) {
        final String url = StatsCardUrls.withAnimationsDisabled(badgeUrls.get(position));
        final String widthSpec = specAt(badgeWidths, position);
        final String heightSpec = specAt(badgeHeights, position);
        final ImageView imageView = holder.imageView;
        final Context context = imageView.getContext();
        imageView.setImageDrawable(null);
        bindLink(imageView, specAt(badgeLinks, position));

        float density = context.getResources().getDisplayMetrics().density;
        float fontPx = fontPx(context);
        float available = availableWidth(imageView);
        float placeholderWidth = ImageSizing.resolve(ImageSizing.parse(widthSpec), available, fontPx, density);
        float placeholderHeight = ImageSizing.resolve(ImageSizing.parse(heightSpec), available, fontPx, density);
        applySize(imageView,
                placeholderWidth > 0f ? Math.round(Math.min(placeholderWidth, available)) : ViewGroup.LayoutParams.WRAP_CONTENT,
                placeholderHeight > 0f ? Math.round(placeholderHeight) : dp(context, PLACEHOLDER_HEIGHT_DP));

        Glide.with(context)
                .asDrawable()
                .load(url)
                .error(Glide.with(context).as(PictureDrawable.class).load(url))
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        imageView.setImageDrawable(resource);
                        sizeToContent(imageView, resource, widthSpec, heightSpec);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        imageView.setImageDrawable(placeholder);
                    }
                });
    }

    private static void bindLink(ImageView imageView, @Nullable String link) {
        if (link == null || !(link.startsWith("http://") || link.startsWith("https://"))) {
            imageView.setOnClickListener(null);
            imageView.setClickable(false);
            return;
        }
        imageView.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                v.getContext().startActivity(intent);
            } catch (ActivityNotFoundException ignored) {
            }
        });
    }

    @Nullable
    private static String specAt(@Nullable List<String> specs, int position) {
        if (specs == null || position >= specs.size()) return null;
        return specs.get(position);
    }

    private static float availableWidth(View view) {
        Context context = view.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        View parent = view.getParent() instanceof View ? (View) view.getParent() : null;
        int width = parent != null ? parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight() : 0;
        if (width <= 0) width = context.getResources().getDisplayMetrics().widthPixels - Math.round(32 * density);
        return Math.max(1f, width - ITEM_MARGIN_DP * density);
    }

    private static float fontPx(Context context) {
        return android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_SP, 14f, context.getResources().getDisplayMetrics());
    }

    private static void sizeToContent(ImageView imageView, Drawable drawable, @Nullable String widthSpec, @Nullable String heightSpec) {
        Context context = imageView.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        float[] natural = ImageSizing.natural(drawable, density);
        float maxWidth = availableWidth(imageView);
        float fontPx = fontPx(context);

        float requestedWidth = ImageSizing.resolve(ImageSizing.parse(widthSpec), maxWidth, fontPx, density);
        float requestedHeight = ImageSizing.resolve(ImageSizing.parse(heightSpec),
                ImageSizing.percentBaseForHeight(natural, maxWidth), fontPx, density);

        float[] size = ImageSizing.fit(natural, requestedWidth, requestedHeight, maxWidth, 0f);
        if (size == null) return;
        applySize(imageView, Math.max(Math.round(size[0]), 1), Math.max(Math.round(size[1]), 1));
    }

    private static void applySize(ImageView imageView, int width, int height) {
        ViewGroup.LayoutParams params = imageView.getLayoutParams();
        boolean sizeChanged = params.width != width || params.height != height;
        params.width = width;
        params.height = height;
        imageView.setLayoutParams(params);
        if (sizeChanged) {
            ReadmeSegmentAdapter.requestLayoutUpTheChain(imageView);
        }
    }

    private static int dp(Context context, int value) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    @Override
    public int getItemCount() {
        return badgeUrls.size();
    }

    static final class BadgeHolder extends RecyclerView.ViewHolder {
        final ImageView imageView;

        BadgeHolder(@NonNull ImageView itemView) {
            super(itemView);
            this.imageView = itemView;
        }
    }
}
