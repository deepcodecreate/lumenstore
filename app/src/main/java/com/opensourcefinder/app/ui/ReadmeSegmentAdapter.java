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
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.flexbox.JustifyContent;
import com.google.android.material.R;
import com.google.android.material.color.MaterialColors;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TableAwareMovementMethod;

import java.util.List;
import java.util.WeakHashMap;

final class ReadmeSegmentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_TEXT = 0;
    private static final int TYPE_BADGE_ROW = 1;
    private static final int TYPE_ANIMATED_SVG = 2;
    private static final int TYPE_TOGGLE = 3;
    private static final int TYPE_CODE = 4;

    private static final int PREVIEW_SEGMENT_COUNT = 1;

    private static final int COLLAPSED_TEXT_MAX_LINES = 4;

    private final List<ReadmeBadgeParser.Segment> segments;
    private final Markwon markwon;
    private final boolean collapsible;
    private boolean expanded;

    ReadmeSegmentAdapter(@NonNull List<ReadmeBadgeParser.Segment> segments, @NonNull Markwon markwon) {
        this.segments = segments;
        this.markwon = markwon;
        this.collapsible = hasMoreContent(segments);
        this.expanded = !collapsible;
    }

    private static boolean hasMoreContent(List<ReadmeBadgeParser.Segment> segments) {
        if (segments == null || segments.isEmpty()) return false;
        if (segments.size() > PREVIEW_SEGMENT_COUNT) return true;
        for (ReadmeBadgeParser.Segment segment : segments) {
            if (segment != null && segment.markdown != null) {
                int lines = segment.markdown.split("\\r?\\n", -1).length;
                if (lines > COLLAPSED_TEXT_MAX_LINES) return true;
            }
        }
        return false;
    }

    private int previewSegmentIndex() {
        if (segments.isEmpty()) return -1;

        for (int i = 0; i < segments.size(); i++) {
            ReadmeBadgeParser.Segment segment = segments.get(i);
            if (segment != null && !segment.isBadgeRow && segment.animatedSvgUrl == null
                    && segment.markdown != null && !segment.markdown.trim().isEmpty()) {
                return i;
            }
        }
        return 0;
    }

    private int visibleSegmentCount() {
        if (expanded) return segments.size();
        return segments.isEmpty() ? 0 : PREVIEW_SEGMENT_COUNT;
    }

    private int segmentIndexForPosition(int position) {
        return expanded ? position : previewSegmentIndex();
    }

    private void setExpanded(boolean expanded) {
        if (this.expanded == expanded) return;
        this.expanded = expanded;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (collapsible && position == visibleSegmentCount()) return TYPE_TOGGLE;
        ReadmeBadgeParser.Segment segment = segments.get(segmentIndexForPosition(position));
        if (segment.isBadgeRow) return TYPE_BADGE_ROW;
        if (segment.animatedSvgUrl != null) return TYPE_ANIMATED_SVG;
        if (segment.isCode()) return TYPE_CODE;
        return TYPE_TEXT;
    }

    @Override
    public int getItemCount() {
        return visibleSegmentCount() + (collapsible ? 1 : 0);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();

        if (viewType == TYPE_TOGGLE) {
            TextView toggle = new TextView(context);
            toggle.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            android.util.TypedValue typedValue = new android.util.TypedValue();
            context.getTheme().resolveAttribute(R.attr.textAppearanceLabelLarge, typedValue, true);
            toggle.setTextAppearance(typedValue.resourceId);
            toggle.setTextColor(MaterialColors.getColor(context, android.R.attr.colorPrimary, "ReadmeSegmentAdapter"));
            int hPad = dp(context, 4);
            int vPad = dp(context, 10);
            toggle.setPadding(hPad, vPad, hPad, vPad);
            toggle.setMinHeight(dp(context, 32));
            toggle.setGravity(Gravity.CENTER_VERTICAL);
            return new ToggleHolder(toggle);
        }

        if (viewType == TYPE_CODE) {
            return new CodeHolder(createCodeView(context));
        }

        if (viewType == TYPE_BADGE_ROW) {
            RecyclerView row = new RecyclerView(context);
            row.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setNestedScrollingEnabled(false);
            row.setOverScrollMode(RecyclerView.OVER_SCROLL_NEVER);

            row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            return new BadgeRowHolder(row);
        }

        if (viewType == TYPE_ANIMATED_SVG) {
            WebView webView = new WebView(context);
            webView.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 56)));
            webView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

            webView.setBackgroundColor(Color.TRANSPARENT);
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            webView.getSettings().setJavaScriptEnabled(true);
            webView.setVerticalScrollBarEnabled(false);
            webView.setHorizontalScrollBarEnabled(false);
            webView.setNestedScrollingEnabled(false);
            return new AnimatedSvgHolder(webView);
        }

        TextView textView = new TextView(context);
        textView.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        android.util.TypedValue typedValue = new android.util.TypedValue();
        context.getTheme().resolveAttribute(R.attr.textAppearanceBodyMedium, typedValue, true);
        textView.setTextAppearance(typedValue.resourceId);
        textView.setTextColor(MaterialColors.getColor(context, R.attr.colorOnSurface, "ReadmeSegmentAdapter"));
        textView.setLineSpacing(dp(context, 4), 1f);

        textView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        textView.setTextDirection(View.TEXT_DIRECTION_LTR);
        return new TextHolder(textView);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ToggleHolder) {
            bindToggle((ToggleHolder) holder);
            return;
        }

        ReadmeBadgeParser.Segment segment = segments.get(segmentIndexForPosition(position));

        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
        params.topMargin = position == 0 ? 0 : dp(holder.itemView.getContext(), 12);
        holder.itemView.setLayoutParams(params);

        if (holder instanceof BadgeRowHolder) {
            RecyclerView row = ((BadgeRowHolder) holder).recyclerView;
            FlexboxLayoutManager layoutManager = new FlexboxLayoutManager(row.getContext());
            layoutManager.setFlexWrap(FlexWrap.WRAP);
            layoutManager.setJustifyContent(justifyContentFor(segment.alignment));
            row.setLayoutManager(layoutManager);
            row.setAdapter(new TechStackAdapter(segment.badgeUrls, segment.badgeWidths, segment.badgeHeights, segment.badgeLinks));
        } else if (holder instanceof CodeHolder) {
            ((CodeHolder) holder).textView.setText(segment.codeText);
            ((CodeHolder) holder).scrollView.scrollTo(0, 0);
        } else if (holder instanceof AnimatedSvgHolder) {
            bindAnimatedSvg((AnimatedSvgHolder) holder, segment);
        } else {
            TextView textView = ((TextHolder) holder).textView;
            textView.setGravity(gravityFor(segment));

            boolean isLastVisibleWhileCollapsed = collapsible && !expanded
                    && position == visibleSegmentCount() - 1;
            if (isLastVisibleWhileCollapsed) {
                textView.setMaxLines(COLLAPSED_TEXT_MAX_LINES);
                textView.setEllipsize(TextUtils.TruncateAt.END);
            } else {
                textView.setMaxLines(Integer.MAX_VALUE);
                textView.setEllipsize(null);
            }

            if (segment.renderedText != null) {

                markwon.setParsedMarkdown(textView, segment.renderedText);
            } else {
                markwon.setMarkdown(textView, segment.markdown);
            }
            textView.setMovementMethod(TableAwareMovementMethod.create());
        }
    }

    private static HorizontalScrollView createCodeView(Context context) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(true);
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        GradientDrawable background = new GradientDrawable();
        background.setColor(MaterialColors.getColor(context, R.attr.colorSurfaceContainerLow, "ReadmeSegmentAdapter"));
        background.setCornerRadius(dp(context, 8));
        scroll.setBackground(background);

        TextView code = new TextView(context);
        code.setLayoutParams(new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
        code.setTextColor(MaterialColors.getColor(context, R.attr.colorOnSurface, "ReadmeSegmentAdapter"));
        code.setHorizontallyScrolling(true);
        code.setLineSpacing(dp(context, 2), 1f);
        code.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        code.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        code.setTextDirection(View.TEXT_DIRECTION_LTR);
        scroll.addView(code);
        return scroll;
    }

    private void bindToggle(ToggleHolder holder) {
        TextView toggle = holder.toggle;
        toggle.setText(expanded ? com.opensourcefinder.app.R.string.show_less : com.opensourcefinder.app.R.string.read_more);
        toggle.setOnClickListener(v -> setExpanded(!expanded));
    }

    private static void bindAnimatedSvg(AnimatedSvgHolder holder, ReadmeBadgeParser.Segment segment) {
        WebView webView = holder.webView;
        ViewGroup.LayoutParams webParams = webView.getLayoutParams();
        int initialHeight = Math.round(56 * webView.getResources().getDisplayMetrics().density);
        if (webParams != null && webParams.height != initialHeight) {
            webParams.height = initialHeight;
            webView.setLayoutParams(webParams);
        }
        String justify;
        switch (segment.alignment) {
            case LEFT:
                justify = "flex-start";
                break;
            case RIGHT:
                justify = "flex-end";
                break;
            case CENTER:
            default:
                justify = "center";
        }
        String html = "<html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>html,body{margin:0;padding:0;background:transparent;}"
                + "body{display:flex;justify-content:" + justify + ";align-items:flex-start;}"
                + "img{display:block;max-width:100%;height:auto;}</style></head><body>"
                + "<img src=\"" + segment.animatedSvgUrl + "\"/>"
                + "</body></html>";

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                measureAnimatedSvg(view, 0);
            }
        });
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    private static final int MEASURE_MAX_ATTEMPTS = 40;
    private static final long MEASURE_RETRY_MILLIS = 250L;

    private static void measureAnimatedSvg(WebView view, int attempt) {
        if (!view.isAttachedToWindow() || attempt >= MEASURE_MAX_ATTEMPTS) return;
        String script = "(function(){var i=document.images[0];"
                + "if(!i||!i.complete)return -1;"
                + "return Math.ceil(i.getBoundingClientRect().height);})()";
        view.evaluateJavascript(script, value -> {
            double cssHeight;
            try {
                cssHeight = Double.parseDouble(value);
            } catch (NumberFormatException | NullPointerException e) {
                cssHeight = -1;
            }
            if (cssHeight <= 0) {
                view.postDelayed(() -> measureAnimatedSvg(view, attempt + 1), MEASURE_RETRY_MILLIS);
                return;
            }
            int pxHeight = (int) Math.ceil(cssHeight * view.getResources().getDisplayMetrics().density);
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (lp.height != pxHeight) {
                lp.height = pxHeight;
                view.setLayoutParams(lp);
                requestLayoutUpTheChain(view);
            }
        });
    }

    private static final WeakHashMap<RecyclerView, Boolean> PENDING_LAYOUT = new WeakHashMap<>();

    static void requestLayoutUpTheChain(View view) {
        ViewParent parent = view.getParent();
        while (parent instanceof View) {
            if (parent instanceof RecyclerView) {
                scheduleLayout((RecyclerView) parent);
            }
            parent = ((View) parent).getParent();
        }
    }

    private static void scheduleLayout(RecyclerView recyclerView) {
        if (PENDING_LAYOUT.put(recyclerView, Boolean.TRUE) != null) return;
        recyclerView.postOnAnimation(() -> {
            PENDING_LAYOUT.remove(recyclerView);
            recyclerView.requestLayout();
        });
    }

    private static int gravityFor(ReadmeBadgeParser.Segment segment) {
        if (!segment.centerAsMedia) return Gravity.START;
        switch (segment.alignment) {
            case LEFT:
                return Gravity.START;
            case RIGHT:
                return Gravity.END;
            case CENTER:
            default:
                return Gravity.CENTER_HORIZONTAL;
        }
    }

    private static int justifyContentFor(ReadmeBadgeParser.Alignment alignment) {
        switch (alignment) {
            case LEFT:
                return JustifyContent.FLEX_START;
            case RIGHT:
                return JustifyContent.FLEX_END;
            case CENTER:
            default:
                return JustifyContent.CENTER;
        }
    }

    private static int dp(Context context, int value) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    static final class TextHolder extends RecyclerView.ViewHolder {
        final TextView textView;

        TextHolder(@NonNull TextView itemView) {
            super(itemView);
            this.textView = itemView;
        }
    }

    static final class BadgeRowHolder extends RecyclerView.ViewHolder {
        final RecyclerView recyclerView;

        BadgeRowHolder(@NonNull RecyclerView itemView) {
            super(itemView);
            this.recyclerView = itemView;
        }
    }

    static final class CodeHolder extends RecyclerView.ViewHolder {
        final HorizontalScrollView scrollView;
        final TextView textView;

        CodeHolder(@NonNull HorizontalScrollView itemView) {
            super(itemView);
            this.scrollView = itemView;
            this.textView = (TextView) itemView.getChildAt(0);
        }
    }

    static final class AnimatedSvgHolder extends RecyclerView.ViewHolder {
        final WebView webView;

        AnimatedSvgHolder(@NonNull WebView itemView) {
            super(itemView);
            this.webView = itemView;
        }
    }

    static final class ToggleHolder extends RecyclerView.ViewHolder {
        final TextView toggle;

        ToggleHolder(@NonNull TextView itemView) {
            super(itemView);
            this.toggle = itemView;
        }
    }
}
