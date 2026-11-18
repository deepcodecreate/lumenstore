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
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.PictureDrawable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.R;
import com.google.android.material.color.MaterialColors;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.AsyncDrawable;
import io.noties.markwon.image.ImageSize;
import io.noties.markwon.image.ImageSizeResolver;
import io.noties.markwon.image.glide.GlideImagesPlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TableAwareMovementMethod;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tables.TableTheme;

public final class MarkdownRenderer {

    private MarkdownRenderer() {}

    private static final ExecutorService README_EXECUTOR = Executors.newSingleThreadExecutor();

    public static void render(TextView view, String markdown) {
        if (view == null || markdown == null) return;

        int width = view.getWidth() - view.getPaddingLeft() - view.getPaddingRight();
        if (width > 0) {
            renderNow(view, markdown, width);
        } else {

            view.post(() -> {
                int postWidth = view.getWidth() - view.getPaddingLeft() - view.getPaddingRight();
                renderNow(view, markdown, postWidth);
            });
        }
    }

    public static void renderSegmented(RecyclerView recyclerView, String markdown) {
        renderSegmented(recyclerView, markdown, null, null);
    }

    public static void renderSegmented(RecyclerView recyclerView, String markdown, String owner, String repo) {
        if (recyclerView == null || markdown == null) return;

        if (recyclerView.getLayoutManager() == null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        }
        recyclerView.setNestedScrollingEnabled(false);
        recyclerView.setItemAnimator(null);

        recyclerView.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_LTR);

        int width = recyclerView.getWidth() - recyclerView.getPaddingLeft() - recyclerView.getPaddingRight();
        if (width <= 0) {

            recyclerView.post(() -> renderSegmented(recyclerView, markdown, owner, repo));
            return;
        }

        final Context context = recyclerView.getContext();
        README_EXECUTOR.execute(() -> {
            List<ReadmeBadgeParser.Segment> segments;
            try {
                segments = ReadmeBadgeParser.parse(MarkdownPreprocessor.process(markdown, owner, repo));
            } catch (RuntimeException malformed) {
                segments = ReadmeBadgeParser.parse(markdown);
            }
            Markwon markwon = build(context, width);
            for (ReadmeBadgeParser.Segment segment : segments) {
                if (!segment.isBadgeRow && segment.animatedSvgUrl == null && !segment.isCode() && segment.markdown != null) {
                    try {
                        segment.renderedText = markwon.render(markwon.parse(segment.markdown));
                    } catch (RuntimeException malformedSegment) {

                    }
                }
            }
            final List<ReadmeBadgeParser.Segment> ready = segments;
            recyclerView.post(() -> recyclerView.setAdapter(new ReadmeSegmentAdapter(ready, markwon)));
        });
    }

    private static void renderNow(TextView view, String markdown, int widthPx) {

        view.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_LTR);
        view.setTextDirection(android.view.View.TEXT_DIRECTION_LTR);
        Markwon markwon = build(view.getContext(), widthPx);
        String prepared;
        try {
            prepared = MarkdownPreprocessor.process(markdown, null, null);
        } catch (RuntimeException malformed) {
            prepared = markdown;
        }
        markwon.setMarkdown(view, prepared);
        view.setMovementMethod(TableAwareMovementMethod.create());
    }

    @Deprecated
    public static Markwon get(Context context) {

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        return build(context, screenWidth);
    }

    private static Markwon build(Context context, int targetWidthPx) {

        final int outline = MaterialColors.getColor(context, R.attr.colorOutline, "MarkdownRenderer");
        final int outlineVariant = MaterialColors.getColor(context, R.attr.colorOutlineVariant, "MarkdownRenderer");
        final int surfaceVariant = MaterialColors.getColor(context, R.attr.colorSurfaceVariant, "MarkdownRenderer");
        final int surfaceContainer = MaterialColors.getColor(context, R.attr.colorSurfaceContainer, "MarkdownRenderer");
        final int surfaceContainerLow = MaterialColors.getColor(context, R.attr.colorSurfaceContainerLow, "MarkdownRenderer");
        final int surfaceContainerHigh = MaterialColors.getColor(context, R.attr.colorSurfaceContainerHigh, "MarkdownRenderer");
        final int onSurface = MaterialColors.getColor(context, R.attr.colorOnSurface, "MarkdownRenderer");
        final int onSurfaceVariant = MaterialColors.getColor(context, R.attr.colorOnSurfaceVariant, "MarkdownRenderer");
        final int primary = MaterialColors.getColor(context, android.R.attr.colorPrimary, "MarkdownRenderer");

        TableTheme tableTheme = new TableTheme.Builder()
                .tableBorderWidth(dp(context, 1))
                .tableBorderColor(outline)
                .tableCellPadding(dp(context, 8))
                .tableHeaderRowBackgroundColor(surfaceContainerHigh)
                .tableOddRowBackgroundColor(surfaceContainer)
                .tableEvenRowBackgroundColor(0)
                .build();

        Typeface mono = Typeface.MONOSPACE;
        final Context app = context.getApplicationContext();
        final int maxWidthPx = Math.max(0, targetWidthPx);
        final int unknownSizeFallbackPx = dp(context, 96);
        final float density = context.getResources().getDisplayMetrics().density;
        final float fontPx = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_SP, 14f, context.getResources().getDisplayMetrics());

        return Markwon.builder(context)
                .usePlugin(HtmlPlugin.create())
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(tableTheme))

                .usePlugin(GlideImagesPlugin.create(new GlideImagesPlugin.GlideStore() {
                    @NonNull
                    @Override
                    public RequestBuilder load(@NonNull AsyncDrawable drawable) {
                        String url = StatsCardUrls.withAnimationsDisabled(drawable.getDestination());
                        RequestBuilder<android.graphics.drawable.Drawable> request = Glide.with(app)
                                .asDrawable()
                                .load(url)
                                .error(Glide.with(app).as(PictureDrawable.class).load(url));
                        if (maxWidthPx > 0) {
                            request = request
                                    .downsample(DownsampleStrategy.AT_MOST)
                                    .override(maxWidthPx * 2, maxWidthPx * 16);
                        }
                        return request;
                    }

                    @Override
                    public void cancel(@NonNull Target target) {
                        Glide.with(app).clear(target);
                    }
                }))

                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureConfiguration(@NonNull MarkwonConfiguration.Builder builder) {
                        builder.imageSizeResolver(new ImageSizeResolver() {
                            @NonNull
                            @Override
                            public Rect resolveImageSize(@NonNull AsyncDrawable drawable) {
                                float[] natural = ImageSizing.natural(drawable.getResult(), density);
                                ImageSize requested = drawable.getImageSize();
                                float requestedWidth = ImageSizing.NONE;
                                float requestedHeight = ImageSizing.NONE;
                                if (requested != null) {
                                    requestedWidth = resolveDimension(requested.width, maxWidthPx, fontPx, density);
                                    requestedHeight = resolveDimension(requested.height,
                                            ImageSizing.percentBaseForHeight(natural, maxWidthPx), fontPx, density);
                                }
                                float fallbackSide = maxWidthPx > 0
                                        ? Math.min(maxWidthPx, unknownSizeFallbackPx)
                                        : unknownSizeFallbackPx;
                                float[] size = ImageSizing.fit(natural, requestedWidth, requestedHeight,
                                        maxWidthPx, fallbackSide);
                                return new Rect(0, 0, Math.max(Math.round(size[0]), 1), Math.max(Math.round(size[1]), 1));
                            }
                        });
                    }

                    @Override
                    public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                        builder

                                .linkColor(primary)
                                .blockQuoteColor(withAlpha(onSurfaceVariant, 140))
                                .blockQuoteWidth(dp(context, 3))
                                .listItemColor(onSurface)
                                .thematicBreakColor(outlineVariant)

                                .headingTypeface(Typeface.DEFAULT_BOLD)
                                .headingTextSizeMultipliers(new float[]{1.6f, 1.4f, 1.25f, 1.15f, 1.05f, 1f})
                                .headingBreakColor(outlineVariant)

                                .codeTextColor(onSurface)
                                .codeBackgroundColor(surfaceContainerHigh)
                                .codeBlockTextColor(onSurface)
                                .codeBlockBackgroundColor(surfaceContainerLow)
                                .codeTypeface(mono)
                                .codeBlockTypeface(mono)
                                .codeBlockMargin(dp(context, 12));
                    }
                })
                .build();
    }

    private static float resolveDimension(ImageSize.Dimension dimension, float percentBase, float fontPx, float density) {
        if (dimension == null) return ImageSizing.NONE;
        return ImageSizing.resolve(dimension.value, dimension.unit, percentBase, fontPx, density);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static int dp(Context context, int value) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
