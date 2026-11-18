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

import android.text.Spanned;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReadmeBadgeParser {

    private ReadmeBadgeParser() {}

    enum Alignment {
        LEFT, CENTER, RIGHT
    }

    static final class Segment {
        final boolean isBadgeRow;
        final String markdown;
        final List<String> badgeUrls;
        final List<String> badgeWidths;
        final List<String> badgeHeights;
        final List<String> badgeLinks;

        final Alignment alignment;

        final boolean centerAsMedia;

        final String animatedSvgUrl;

        final String codeText;

        Spanned renderedText;

        private Segment(boolean isBadgeRow, String markdown, List<String> badgeUrls,
                         List<String> badgeWidths, List<String> badgeHeights, List<String> badgeLinks,
                         Alignment alignment, boolean centerAsMedia, String animatedSvgUrl, String codeText) {
            this.isBadgeRow = isBadgeRow;
            this.markdown = markdown;
            this.badgeUrls = badgeUrls;
            this.badgeWidths = badgeWidths;
            this.badgeHeights = badgeHeights;
            this.badgeLinks = badgeLinks;
            this.alignment = alignment;
            this.centerAsMedia = centerAsMedia;
            this.animatedSvgUrl = animatedSvgUrl;
            this.codeText = codeText;
        }

        boolean isCode() {
            return codeText != null;
        }

        static Segment text(String markdown) {
            return new Segment(false, markdown, null, null, null, null, Alignment.LEFT, false, null, null);
        }

        static Segment singleImage(String markdown, Alignment alignment) {
            return new Segment(false, markdown, null, null, null, null, alignment, true, null, null);
        }

        static Segment aligned(String markdown, Alignment alignment) {
            return new Segment(false, markdown, null, null, null, null, alignment, true, null, null);
        }

        static Segment badges(List<String> urls, List<String> widths, List<String> heights,
                              List<String> links, Alignment alignment) {
            return new Segment(true, null, urls, widths, heights, links, alignment, true, null, null);
        }

        static Segment animatedSvg(String url, Alignment alignment) {
            return new Segment(false, null, null, null, null, null, alignment, true, url, null);
        }

        static Segment code(String text) {
            return new Segment(false, null, null, null, null, null, Alignment.LEFT, false, null, text);
        }
    }

    private static final int CHUNK_HEADING_SPLIT_CHARS = 1500;

    private static final Pattern MD_IMAGE =
            Pattern.compile("!\\[[^\\]]*]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern MD_LINKED_IMAGE =
            Pattern.compile("\\[\\s*!\\[[^\\]]*]\\([^)\\s]+(?:\\s+\"[^\"]*\")?\\)\\s*]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern MD_REFERENCE_IMAGE =
            Pattern.compile("!\\[[^\\]]*]\\[[^\\]]*]");
    private static final Pattern HTML_IMAGE =
            Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"'][^>]*/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_ANCHOR =
            Pattern.compile("<a\\b[^>]*?href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern EMPTY_MD_LINK = Pattern.compile("\\[\\s*]\\([^)\\n]*\\)");
    private static final Pattern WRAPPER_TAGS =
            Pattern.compile("</?(?:p|div|a|br|span|picture|source|center|figure|figcaption)(?=[\\s/>])[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPACE_ENTITIES = Pattern.compile("&(?:nbsp|emsp|ensp|thinsp|#160);", Pattern.CASE_INSENSITIVE);
    private static final String DIMENSION_VALUE = "([0-9]*\\.?[0-9]+\\s*(?:%|px|em|rem|pt|in|cm|mm)?)";
    private static final Pattern ATTR_WIDTH =
            Pattern.compile("(?<![\\w-])width\\s*=\\s*[\"']?\\s*" + DIMENSION_VALUE, Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_HEIGHT =
            Pattern.compile("(?<![\\w-])height\\s*=\\s*[\"']?\\s*" + DIMENSION_VALUE, Pattern.CASE_INSENSITIVE);
    private static final Pattern STYLE_ATTR =
            Pattern.compile("\\bstyle\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", Pattern.CASE_INSENSITIVE);
    private static final Pattern STYLE_WIDTH =
            Pattern.compile("(?<![\\w-])width\\s*:\\s*" + DIMENSION_VALUE, Pattern.CASE_INSENSITIVE);
    private static final Pattern STYLE_HEIGHT =
            Pattern.compile("(?<![\\w-])height\\s*:\\s*" + DIMENSION_VALUE, Pattern.CASE_INSENSITIVE);
    private static final Pattern ALIGN_LEFT = Pattern.compile("align\\s*=\\s*[\"']?left[\"']?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALIGN_RIGHT = Pattern.compile("align\\s*=\\s*[\"']?right[\"']?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALIGN_CENTER = Pattern.compile("align\\s*=\\s*[\"']?center[\"']?|<center\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FENCE_LINE = Pattern.compile("^( {0,3})(`{3,}|~{3,})([^`]*)$");
    private static final Pattern REFERENCE_DEF = Pattern.compile("^ {0,3}\\[[^\\]]+]:[ \\t]*\\S+.*$");
    private static final Pattern REFERENCE_USAGE = Pattern.compile("]\\[|\\[[^\\]\\n]+](?![(:\\[])");
    private static final Pattern HEADING_START = Pattern.compile("^ {0,3}#{1,6}(?:\\s|$)");

    private static final Pattern ANIMATED_SVG_HOST =
            Pattern.compile("typing-svg|capsule-render", Pattern.CASE_INSENSITIVE);

    private static final class Block {
        final boolean code;
        final String content;

        Block(boolean code, String content) {
            this.code = code;
            this.content = content;
        }
    }

    @NonNull
    static List<Segment> parse(@NonNull String markdown) {
        List<Block> blocks = splitBlocks(markdown);
        List<String> definitions = collectDefinitions(blocks);

        List<Segment> segments = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        for (Block block : blocks) {
            if (block.code) {
                flush(segments, chunk, definitions);
                segments.add(Segment.code(block.content));
                continue;
            }
            String text = block.content;
            if (isDefinitionsOnly(text)) continue;

            Segment media = extractMediaSegmentIfImageOnly(text);
            if (media != null) {
                flush(segments, chunk, definitions);
                segments.add(media);
                continue;
            }
            Alignment explicit = explicitAlignment(text);
            if (explicit != null) {
                flush(segments, chunk, definitions);
                segments.add(Segment.aligned(withDefinitions(text, definitions), explicit));
                continue;
            }
            if (containsImage(text)) {
                flush(segments, chunk, definitions);
                segments.add(Segment.text(withDefinitions(text, definitions)));
                continue;
            }
            if (chunk.length() >= CHUNK_HEADING_SPLIT_CHARS && HEADING_START.matcher(text).find()) {
                flush(segments, chunk, definitions);
            }
            if (chunk.length() > 0) chunk.append("\n\n");
            chunk.append(text);
        }
        flush(segments, chunk, definitions);
        return segments;
    }

    private static void flush(List<Segment> segments, StringBuilder chunk, List<String> definitions) {
        if (chunk.length() == 0) return;
        segments.add(Segment.text(withDefinitions(chunk.toString(), definitions)));
        chunk.setLength(0);
    }

    private static List<Block> splitBlocks(String markdown) {
        List<Block> blocks = new ArrayList<>();
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder text = new StringBuilder();
        StringBuilder code = new StringBuilder();
        boolean inFence = false;
        char fenceChar = 0;
        int fenceLength = 0;
        int fenceIndent = 0;
        for (String line : lines) {
            if (inFence) {
                if (closesFence(line, fenceChar, fenceLength)) {
                    addCode(blocks, code);
                    inFence = false;
                } else {
                    code.append(stripIndent(line, fenceIndent)).append('\n');
                }
                continue;
            }
            Matcher fence = FENCE_LINE.matcher(line);
            if (fence.matches()) {
                addText(blocks, text);
                inFence = true;
                fenceIndent = fence.group(1).length();
                fenceChar = fence.group(2).charAt(0);
                fenceLength = fence.group(2).length();
                continue;
            }
            if (line.trim().isEmpty()) {
                addText(blocks, text);
            } else {
                if (text.length() > 0) text.append('\n');
                text.append(line);
            }
        }
        addText(blocks, text);
        if (inFence) addCode(blocks, code);
        return blocks;
    }

    private static boolean closesFence(String line, char fenceChar, int fenceLength) {
        int i = 0;
        int indent = 0;
        while (i < line.length() && line.charAt(i) == ' ' && indent < 4) {
            i++;
            indent++;
        }
        if (indent > 3) return false;
        int n = 0;
        while (i + n < line.length() && line.charAt(i + n) == fenceChar) n++;
        if (n < fenceLength) return false;
        return line.substring(i + n).trim().isEmpty();
    }

    private static String stripIndent(String line, int indent) {
        int i = 0;
        while (i < indent && i < line.length() && line.charAt(i) == ' ') i++;
        return line.substring(i);
    }

    private static void addText(List<Block> blocks, StringBuilder text) {
        if (text.length() == 0) return;
        blocks.add(new Block(false, text.toString()));
        text.setLength(0);
    }

    private static void addCode(List<Block> blocks, StringBuilder code) {
        String content = code.toString();
        if (content.endsWith("\n")) content = content.substring(0, content.length() - 1);
        code.setLength(0);
        if (content.trim().isEmpty()) return;
        blocks.add(new Block(true, content));
    }

    private static List<String> collectDefinitions(List<Block> blocks) {
        List<String> definitions = new ArrayList<>();
        for (Block block : blocks) {
            if (block.code) continue;
            for (String line : block.content.split("\n")) {
                if (REFERENCE_DEF.matcher(line).matches()) definitions.add(line.trim());
            }
        }
        return definitions;
    }

    private static boolean isDefinitionsOnly(String text) {
        for (String line : text.split("\n")) {
            if (line.trim().isEmpty()) continue;
            if (!REFERENCE_DEF.matcher(line).matches()) return false;
        }
        return true;
    }

    private static String withDefinitions(String text, List<String> definitions) {
        if (definitions.isEmpty() || !REFERENCE_USAGE.matcher(text).find()) return text;
        StringBuilder sb = new StringBuilder(text).append("\n\n");
        for (String definition : definitions) sb.append(definition).append('\n');
        return sb.toString();
    }

    private static boolean containsImage(String text) {
        return MD_IMAGE.matcher(text).find() || HTML_IMAGE.matcher(text).find()
                || MD_REFERENCE_IMAGE.matcher(text).find();
    }

    @Nullable
    private static Alignment explicitAlignment(String text) {
        if (ALIGN_CENTER.matcher(text).find()) return Alignment.CENTER;
        if (ALIGN_RIGHT.matcher(text).find()) return Alignment.RIGHT;
        return null;
    }

    @Nullable
    private static Segment extractMediaSegmentIfImageOnly(String block) {
        List<int[]> order = new ArrayList<>();
        List<String> found = new ArrayList<>();
        List<String> foundWidths = new ArrayList<>();
        List<String> foundHeights = new ArrayList<>();

        Matcher m = MD_IMAGE.matcher(block);
        while (m.find()) {
            order.add(new int[]{m.start(), found.size()});
            found.add(m.group(1));
            foundWidths.add(null);
            foundHeights.add(null);
        }

        Matcher h = HTML_IMAGE.matcher(block);
        while (h.find()) {
            order.add(new int[]{h.start(), found.size()});
            found.add(h.group(1));
            String tag = h.group();
            foundWidths.add(dimensionOf(ATTR_WIDTH, STYLE_WIDTH, tag));
            foundHeights.add(dimensionOf(ATTR_HEIGHT, STYLE_HEIGHT, tag));
        }

        if (found.isEmpty()) return null;

        String residual = MD_IMAGE.matcher(block).replaceAll("");
        residual = HTML_IMAGE.matcher(residual).replaceAll("");
        residual = EMPTY_MD_LINK.matcher(residual).replaceAll("");
        residual = WRAPPER_TAGS.matcher(residual).replaceAll("");
        residual = SPACE_ENTITIES.matcher(residual).replaceAll("");
        if (!residual.trim().isEmpty()) return null;

        List<int[]> linkRanges = new ArrayList<>();
        List<String> linkTargets = new ArrayList<>();
        Matcher linkedMd = MD_LINKED_IMAGE.matcher(block);
        while (linkedMd.find()) {
            linkRanges.add(new int[]{linkedMd.start(), linkedMd.end()});
            linkTargets.add(linkedMd.group(1));
        }
        Matcher anchor = HTML_ANCHOR.matcher(block);
        while (anchor.find()) {
            linkRanges.add(new int[]{anchor.start(), anchor.end()});
            linkTargets.add(anchor.group(1));
        }

        order.sort((x, y) -> Integer.compare(x[0], y[0]));
        List<String> urls = new ArrayList<>();
        List<String> widths = new ArrayList<>();
        List<String> heights = new ArrayList<>();
        List<String> links = new ArrayList<>();
        for (int[] entry : order) {
            urls.add(found.get(entry[1]));
            widths.add(foundWidths.get(entry[1]));
            heights.add(foundHeights.get(entry[1]));
            String link = null;
            for (int i = 0; i < linkRanges.size(); i++) {
                int[] range = linkRanges.get(i);
                if (entry[0] >= range[0] && entry[0] < range[1]) {
                    link = linkTargets.get(i);
                    break;
                }
            }
            links.add(link);
        }

        Alignment alignment = detectAlignment(block);
        if (urls.size() >= 2) {
            return Segment.badges(urls, widths, heights, links, alignment);
        }
        String url = urls.get(0);
        if (links.get(0) == null && ANIMATED_SVG_HOST.matcher(url).find()) {
            return Segment.animatedSvg(url, alignment);
        }
        return Segment.singleImage(block, alignment);
    }

    @Nullable
    private static String firstGroup(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) return null;
        String value = matcher.group(1);
        return value == null ? null : value.replaceAll("\\s+", "");
    }

    @Nullable
    private static String dimensionOf(Pattern attribute, Pattern style, String tag) {
        Matcher styleMatcher = STYLE_ATTR.matcher(tag);
        String tagWithoutStyle = tag;
        if (styleMatcher.find()) {
            String css = styleMatcher.group(1) != null ? styleMatcher.group(1) : styleMatcher.group(2);
            String fromStyle = firstGroup(style, css);
            if (fromStyle != null) return fromStyle;
            tagWithoutStyle = tag.substring(0, styleMatcher.start()) + tag.substring(styleMatcher.end());
        }
        return firstGroup(attribute, tagWithoutStyle);
    }

    private static Alignment detectAlignment(String block) {
        if (ALIGN_LEFT.matcher(block).find()) return Alignment.LEFT;
        if (ALIGN_RIGHT.matcher(block).find()) return Alignment.RIGHT;
        return Alignment.CENTER;
    }
}
