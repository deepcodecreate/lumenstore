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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.Glide;
import com.opensourcefinder.app.R;
import java.util.ArrayList;
import java.util.Locale;

public class ScreenshotPreviewActivity extends BaseActivity {
    public static final String EXTRA_URLS = "extra_urls";
    public static final String EXTRA_START_INDEX = "extra_start_index";

    @Override protected boolean wantsLiveBackground() { return false; }

    @Override protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        MotionHelper.applyDistinctActivityTransition(this);
        setContentView(R.layout.activity_screenshot_preview);
        MotionHelper.animateActivityContent(this);

        ArrayList<String> urls = getIntent().getStringArrayListExtra(EXTRA_URLS);
        if (urls == null) urls = new ArrayList<>();
        int startIndex = getIntent().getIntExtra(EXTRA_START_INDEX, 0);
        if (startIndex < 0 || startIndex >= urls.size()) startIndex = 0;

        ViewPager2 pager = findViewById(R.id.screenshotPager);
        TextView counter = findViewById(R.id.screenshotPreviewCounter);
        View close = findViewById(R.id.screenshotPreviewClose);

        int total = urls.size();
        pager.setAdapter(new ScreenshotPagerAdapter(urls));
        pager.setCurrentItem(startIndex, false);
        updateCounter(counter, startIndex, total);

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                updateCounter(counter, position, total);
            }
        });

        close.setOnClickListener(v -> finish());
    }

    private static void updateCounter(TextView counter, int position, int total) {
        if (total <= 1) {
            counter.setVisibility(View.GONE);
            return;
        }
        counter.setVisibility(View.VISIBLE);
        counter.setText(String.format(Locale.getDefault(), "%d / %d", position + 1, total));
    }

    private static class ScreenshotPagerAdapter extends RecyclerView.Adapter<ScreenshotPagerAdapter.PageHolder> {
        private final ArrayList<String> urls;

        ScreenshotPagerAdapter(ArrayList<String> urls) { this.urls = urls; }

        @NonNull @Override public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_screenshot_preview_page, parent, false);
            return new PageHolder(v);
        }

        @Override public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            holder.loading.setVisibility(View.VISIBLE);
            Glide.with(holder.image).load(urls.get(position))
                    .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(200))
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e,
                                Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                boolean isFirstResource) {
                            holder.loading.setVisibility(View.GONE);
                            return false;
                        }
                        @Override public boolean onResourceReady(android.graphics.drawable.Drawable resource,
                                Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            holder.loading.setVisibility(View.GONE);
                            return false;
                        }
                    })
                    .into(holder.image);
        }

        @Override public int getItemCount() { return urls.size(); }

        static class PageHolder extends RecyclerView.ViewHolder {
            final ImageView image;
            final View loading;
            PageHolder(View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.screenshotPreviewImage);
                loading = itemView.findViewById(R.id.screenshotPreviewLoading);
            }
        }
    }
}
