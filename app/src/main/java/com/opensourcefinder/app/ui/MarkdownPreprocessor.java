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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MarkdownPreprocessor {

    private MarkdownPreprocessor() {}

    private static final Pattern FENCE_OPEN = Pattern.compile("^(?:[ \\t]*>)*[ \\t]*(`{3,}|~{3,})(.*)$");
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern DETAILS_TAG = Pattern.compile("</?details\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUMMARY = Pattern.compile("<summary\\b[^>]*>(.*?)</summary>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern KBD = Pattern.compile("<kbd\\b[^>]*>(.*?)</kbd>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ANY_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern TABLE = Pattern.compile("<table\\b[^>]*>(.*?)</table>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ROW = Pattern.compile("<tr\\b[^>]*>(.*?)</tr>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CELL = Pattern.compile("<t([dh])\\b[^>]*>(.*?)</t\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CELL_WRAPPER = Pattern.compile("</?(?:p|div|span|thead|tbody|tfoot|center)\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALERT = Pattern.compile("^((?:[ \\t]*>)+[ \\t]*)\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\\][ \\t]*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TASK_ITEM = Pattern.compile("^([ \\t]*(?:[-*+]|\\d+[.)])[ \\t]+)\\[([ xX])\\](?=[ \\t])");
    private static final Pattern EMOJI_CODE = Pattern.compile("(?<![\\w:/])\\:([a-z0-9_+\\-]{1,40})\\:(?![\\w:])");
    private static final Pattern REFERENCE_DEF = Pattern.compile("^[ ]{0,3}\\[[^\\]]+\\]:[ \\t]*\\S+.*$");
    private static final Pattern HTML_TAG = Pattern.compile("<[A-Za-z/!][^>]*>");
    private static final Pattern BARE_URL = Pattern.compile("https?://[^\\s<>\"'`\\\\^{|}\\[\\]]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_URL_ATTR = Pattern.compile("(\\b(src|href)\\s*=\\s*)([\"'])(.*?)\\3", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ABSOLUTE_SCHEME = Pattern.compile("^(?:[a-zA-Z][a-zA-Z0-9+.\\-]*:|#|//)");

    private static final Map<String, String> EMOJI = new HashMap<>();

    static {
        String[] pairs = {
                "smile", "\uD83D\uDE04", "smiley", "\uD83D\uDE03", "grinning", "\uD83D\uDE00", "laughing", "\uD83D\uDE06",
                "joy", "\uD83D\uDE02", "wink", "\uD83D\uDE09", "blush", "\uD83D\uDE0A", "heart_eyes", "\uD83D\uDE0D",
                "sunglasses", "\uD83D\uDE0E", "thinking", "\uD83E\uDD14", "sob", "\uD83D\uDE2D", "cry", "\uD83D\uDE22",
                "sweat_smile", "\uD83D\uDE05", "rofl", "\uD83E\uDD23", "slightly_smiling_face", "\uD83D\uDE42",
                "relaxed", "\u263A\uFE0F", "neutral_face", "\uD83D\uDE10", "confused", "\uD83D\uDE15",
                "+1", "\uD83D\uDC4D", "thumbsup", "\uD83D\uDC4D", "-1", "\uD83D\uDC4E", "thumbsdown", "\uD83D\uDC4E",
                "clap", "\uD83D\uDC4F", "wave", "\uD83D\uDC4B", "pray", "\uD83D\uDE4F", "muscle", "\uD83D\uDCAA",
                "raised_hands", "\uD83D\uDE4C", "ok_hand", "\uD83D\uDC4C", "point_right", "\uD83D\uDC49",
                "point_left", "\uD83D\uDC48", "point_up", "\u261D\uFE0F", "point_down", "\uD83D\uDC47",
                "eyes", "\uD83D\uDC40", "handshake", "\uD83E\uDD1D", "heart", "\u2764\uFE0F", "broken_heart", "\uD83D\uDC94",
                "blue_heart", "\uD83D\uDC99", "green_heart", "\uD83D\uDC9A", "yellow_heart", "\uD83D\uDC9B",
                "purple_heart", "\uD83D\uDC9C", "sparkling_heart", "\uD83D\uDC96", "star", "\u2B50", "star2", "\uD83C\uDF1F",
                "sparkles", "\u2728", "fire", "\uD83D\uDD25", "zap", "\u26A1", "boom", "\uD83D\uDCA5", "tada", "\uD83C\uDF89",
                "rocket", "\uD83D\uDE80", "trophy", "\uD83C\uDFC6", "medal", "\uD83C\uDFC5", "gift", "\uD83C\uDF81",
                "bulb", "\uD83D\uDCA1", "warning", "\u26A0\uFE0F", "no_entry", "\u26D4", "x", "\u274C",
                "heavy_check_mark", "\u2714\uFE0F", "white_check_mark", "\u2705", "ballot_box_with_check", "\u2611\uFE0F",
                "question", "\u2753", "exclamation", "\u2757", "information_source", "\u2139\uFE0F",
                "bug", "\uD83D\uDC1B", "construction", "\uD83D\uDEA7", "wrench", "\uD83D\uDD27", "hammer", "\uD83D\uDD28",
                "gear", "\u2699\uFE0F", "tools", "\uD83D\uDEE0\uFE0F", "package", "\uD83D\uDCE6", "lock", "\uD83D\uDD12",
                "unlock", "\uD83D\uDD13", "key", "\uD83D\uDD11", "shield", "\uD83D\uDEE1\uFE0F", "link", "\uD83D\uDD17",
                "book", "\uD83D\uDCD6", "books", "\uD83D\uDCDA", "memo", "\uD83D\uDCDD", "pencil", "\u270F\uFE0F",
                "pencil2", "\u270F\uFE0F", "bookmark", "\uD83D\uDD16", "clipboard", "\uD83D\uDCCB", "calendar", "\uD83D\uDCC5",
                "chart_with_upwards_trend", "\uD83D\uDCC8", "bar_chart", "\uD83D\uDCCA", "mag", "\uD83D\uDD0D",
                "mag_right", "\uD83D\uDD0E", "computer", "\uD83D\uDCBB", "iphone", "\uD83D\uDCF1", "phone", "\u260E\uFE0F",
                "email", "\uD83D\uDCE7", "envelope", "\u2709\uFE0F", "inbox_tray", "\uD83D\uDCE5", "outbox_tray", "\uD83D\uDCE4",
                "globe_with_meridians", "\uD83C\uDF10", "earth_africa", "\uD83C\uDF0D", "earth_americas", "\uD83C\uDF0E",
                "earth_asia", "\uD83C\uDF0F", "house", "\uD83C\uDFE0", "art", "\uD83C\uDFA8", "musical_note", "\uD83C\uDFB5",
                "video_game", "\uD83C\uDFAE", "camera", "\uD83D\uDCF7", "movie_camera", "\uD83C\uDFA5", "tv", "\uD83D\uDCFA",
                "sunny", "\u2600\uFE0F", "cloud", "\u2601\uFE0F", "umbrella", "\u2614", "snowflake", "\u2744\uFE0F",
                "rainbow", "\uD83C\uDF08", "seedling", "\uD83C\uDF31", "evergreen_tree", "\uD83C\uDF32", "cactus", "\uD83C\uDF35",
                "coffee", "\u2615", "beer", "\uD83C\uDF7A", "pizza", "\uD83C\uDF55", "cake", "\uD83C\uDF70", "apple", "\uD83C\uDF4E",
                "dog", "\uD83D\uDC36", "cat", "\uD83D\uDC31", "penguin", "\uD83D\uDC27", "octocat", "\uD83D\uDC19",
                "robot", "\uD83E\uDD16", "ghost", "\uD83D\uDC7B", "skull", "\uD83D\uDC80", "100", "\uD83D\uDCAF",
                "arrow_right", "\u27A1\uFE0F", "arrow_left", "\u2B05\uFE0F", "arrow_up", "\u2B06\uFE0F", "arrow_down", "\u2B07\uFE0F",
                "arrow_forward", "\u25B6\uFE0F", "recycle", "\u267B\uFE0F", "hourglass", "\u231B", "stopwatch", "\u23F1\uFE0F",
                "alarm_clock", "\u23F0", "bell", "\uD83D\uDD14", "loudspeaker", "\uD83D\uDCE2", "mega", "\uD83D\uDCE3",
                "construction_worker", "\uD83D\uDC77", "technologist", "\uD83E\uDDD1\u200D\uD83D\uDCBB", "man_technologist", "\uD83D\uDC68\u200D\uD83D\uDCBB",
                "woman_technologist", "\uD83D\uDC69\u200D\uD83D\uDCBB", "dart", "\uD83C\uDFAF", "crown", "\uD83D\uDC51",
                "gem", "\uD83D\uDC8E", "moneybag", "\uD83D\uDCB0", "dollar", "\uD83D\uDCB5", "label", "\uD83C\uDFF7\uFE0F",
                "pushpin", "\uD83D\uDCCC", "paperclip", "\uD83D\uDCCE", "scissors", "\u2702\uFE0F", "wastebasket", "\uD83D\uDDD1\uFE0F",
                "file_folder", "\uD83D\uDCC1", "open_file_folder", "\uD83D\uDCC2", "page_facing_up", "\uD83D\uDCC4",
                "newspaper", "\uD83D\uDCF0", "speech_balloon", "\uD83D\uDCAC", "thought_balloon", "\uD83D\uDCAD",
                "checkered_flag", "\uD83C\uDFC1", "triangular_flag_on_post", "\uD83D\uDEA9", "flags", "\uD83C\uDF8F",
                "package_deliver", "\uD83D\uDCE6", "hugs", "\uD83E\uDD17", "grin", "\uD83D\uDE01", "smirk", "\uD83D\uDE0F",
                "unamused", "\uD83D\uDE12", "angry", "\uD83D\uDE20", "scream", "\uD83D\uDE31", "sleeping", "\uD83D\uDE34",
                "partying_face", "\uD83E\uDD73", "nerd_face", "\uD83E\uDD13", "wink2", "\uD83D\uDE09", "v", "\u270C\uFE0F",
                "fist", "\u270A", "raised_hand", "\u270B", "writing_hand", "\u270D\uFE0F", "brain", "\uD83E\uDDE0",
                "flashlight", "\uD83D\uDD26", "battery", "\uD83D\uDD0B", "electric_plug", "\uD83D\uDD0C", "satellite", "\uD83D\uDEF0\uFE0F",
                "microscope", "\uD83D\uDD2C", "telescope", "\uD83D\uDD2D", "test_tube", "\uD83E\uDDEA", "dna", "\uD83E\uDDEC",
                "hammer_and_wrench", "\uD83D\uDEE0\uFE0F", "building_construction", "\uD83C\uDFD7\uFE0F", "factory", "\uD83C\uDFED",
                "airplane", "\u2708\uFE0F", "car", "\uD83D\uDE97", "bike", "\uD83D\uDEB2", "ship", "\uD83D\uDEA2",
                "traffic_light", "\uD83D\uDEA6", "anchor", "\u2693", "compass", "\uD83E\uDDED", "map", "\uD83D\uDDFA\uFE0F"
        };
        for (int i = 0; i + 1 < pairs.length; i += 2) EMOJI.put(pairs[i], pairs[i + 1]);
    }

    static String process(String markdown, String owner, String repo) {
        if (markdown == null || markdown.isEmpty()) return "";
        String text = markdown.replace("\r\n", "\n").replace('\r', '\n');
        if (text.startsWith("\uFEFF")) text = text.substring(1);

        String rawBase = null;
        String blobBase = null;
        if (owner != null && repo != null && !owner.isEmpty() && !repo.isEmpty()) {
            rawBase = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/HEAD/";
            blobBase = "https://github.com/" + owner + "/" + repo + "/blob/HEAD/";
        }

        List<String> regions = new ArrayList<>();
        List<Boolean> isCode = new ArrayList<>();
        splitRegions(text, regions, isCode);

        StringBuilder out = new StringBuilder(text.length() + 256);
        for (int i = 0; i < regions.size(); i++) {
            String region = regions.get(i);
            if (isCode.get(i)) {
                out.append(region);
            } else {
                out.append(processProse(region, rawBase, blobBase));
            }
        }
        return out.toString();
    }

    private static void splitRegions(String text, List<String> regions, List<Boolean> isCode) {
        String[] lines = text.split("\n", -1);
        StringBuilder buffer = new StringBuilder();
        boolean inFence = false;
        char fenceChar = 0;
        int fenceLength = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean last = i == lines.length - 1;
            String withBreak = last ? line : line + "\n";
            if (!inFence) {
                Matcher m = FENCE_OPEN.matcher(line);
                if (m.matches()) {
                    String marker = m.group(1);
                    boolean valid = marker.charAt(0) != '`' || m.group(2).indexOf('`') < 0;
                    if (valid) {
                        if (buffer.length() > 0) {
                            regions.add(buffer.toString());
                            isCode.add(Boolean.FALSE);
                            buffer.setLength(0);
                        }
                        inFence = true;
                        fenceChar = marker.charAt(0);
                        fenceLength = marker.length();
                        buffer.append(withBreak);
                        continue;
                    }
                }
                buffer.append(withBreak);
            } else {
                buffer.append(withBreak);
                if (closesFence(line, fenceChar, fenceLength)) {
                    regions.add(buffer.toString());
                    isCode.add(Boolean.TRUE);
                    buffer.setLength(0);
                    inFence = false;
                }
            }
        }
        if (buffer.length() > 0) {
            regions.add(buffer.toString());
            isCode.add(inFence);
        }
    }

    private static boolean closesFence(String line, char fenceChar, int fenceLength) {
        String stripped = line.replaceFirst("^(?:[ \\t]*>)*[ \\t]*", "");
        int n = 0;
        while (n < stripped.length() && stripped.charAt(n) == fenceChar) n++;
        if (n < fenceLength) return false;
        return stripped.substring(n).trim().isEmpty();
    }

    private static String processProse(String region, String rawBase, String blobBase) {
        String text = HTML_COMMENT.matcher(region).replaceAll("");
        text = DETAILS_TAG.matcher(text).replaceAll("\n");
        text = replaceSummaries(text);
        text = replaceKeyboard(text);
        text = convertTables(text);

        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder(text.length() + 64);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher alert = ALERT.matcher(line);
            if (alert.matches()) {
                String prefix = alert.group(1);
                out.append(prefix).append("**").append(alertTitle(alert.group(2))).append("**\n").append(prefix.replaceAll("[ \\t]+$", ""));
            } else {
                out.append(transformLine(line, rawBase, blobBase));
            }
            if (i < lines.length - 1) out.append('\n');
        }
        return out.toString();
    }

    private static String alertTitle(String kind) {
        switch (kind.toUpperCase(Locale.ROOT)) {
            case "TIP": return "\uD83D\uDCA1 Tip";
            case "IMPORTANT": return "\u2757 Important";
            case "WARNING": return "\u26A0\uFE0F Warning";
            case "CAUTION": return "\uD83D\uDED1 Caution";
            case "NOTE":
            default: return "\u2139\uFE0F Note";
        }
    }

    private static String replaceSummaries(String text) {
        Matcher m = SUMMARY.matcher(text);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(text, last, m.start());
            String title = ANY_TAG.matcher(m.group(1)).replaceAll("").replaceAll("\\s+", " ").trim();
            if (!title.isEmpty()) sb.append("\n\n**\u25B8 ").append(title).append("**\n\n");
            else sb.append("\n");
            last = m.end();
        }
        sb.append(text, last, text.length());
        return sb.toString();
    }

    private static String replaceKeyboard(String text) {
        Matcher m = KBD.matcher(text);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(text, last, m.start())
                    .append('`').append(ANY_TAG.matcher(m.group(1)).replaceAll("").trim()).append('`');
            last = m.end();
        }
        sb.append(text, last, text.length());
        return sb.toString();
    }

    private static String convertTables(String text) {
        Matcher t = TABLE.matcher(text);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (t.find()) {
            sb.append(text, last, t.start());
            sb.append(renderTable(t.group(1)));
            last = t.end();
        }
        sb.append(text, last, text.length());
        return sb.toString();
    }

    private static String renderTable(String inner) {
        List<List<String>> rows = new ArrayList<>();
        boolean hasImage = false;
        Matcher r = ROW.matcher(inner);
        while (r.find()) {
            List<String> cells = new ArrayList<>();
            Matcher c = CELL.matcher(r.group(1));
            while (c.find()) {
                String content = CELL_WRAPPER.matcher(c.group(2)).replaceAll(" ");
                content = content.replaceAll("\\s+", " ").trim();
                if (content.toLowerCase(Locale.ROOT).contains("<img") || content.contains("![")) hasImage = true;
                cells.add(content);
            }
            if (!cells.isEmpty()) rows.add(cells);
        }
        if (rows.isEmpty()) return "\n\n";

        StringBuilder out = new StringBuilder("\n\n");
        if (hasImage) {
            for (List<String> row : rows) {
                StringBuilder line = new StringBuilder();
                for (String cell : row) {
                    if (cell.isEmpty()) continue;
                    if (line.length() > 0) line.append(" &nbsp; ");
                    line.append(cell);
                }
                if (line.length() > 0) out.append(line).append("\n\n");
            }
            return out.toString();
        }

        int columns = 0;
        for (List<String> row : rows) columns = Math.max(columns, row.size());
        appendTableRow(out, rows.get(0), columns);
        out.append('|');
        for (int i = 0; i < columns; i++) out.append(" --- |");
        out.append('\n');
        for (int i = 1; i < rows.size(); i++) appendTableRow(out, rows.get(i), columns);
        return out.append('\n').toString();
    }

    private static void appendTableRow(StringBuilder out, List<String> cells, int columns) {
        out.append('|');
        for (int i = 0; i < columns; i++) {
            String cell = i < cells.size() ? cells.get(i) : "";
            out.append(' ').append(cell.replace("|", "\\|")).append(" |");
        }
        out.append('\n');
    }

    private static String transformLine(String line, String rawBase, String blobBase) {
        if (line.isEmpty()) return line;
        if (REFERENCE_DEF.matcher(line).matches()) {
            return rawBase == null ? line : resolveReferenceDefinition(line, rawBase, blobBase);
        }

        Matcher task = TASK_ITEM.matcher(line);
        if (task.find()) {
            String mark = " ".equals(task.group(2)) ? "\u2610" : "\u2611";
            line = task.group(1) + mark + line.substring(task.end());
        }

        StringBuilder out = new StringBuilder(line.length() + 16);
        int length = line.length();
        int i = 0;
        while (i < length) {
            char ch = line.charAt(i);
            if (ch == '`') {
                int run = 1;
                while (i + run < length && line.charAt(i + run) == '`') run++;
                int close = findBacktickClose(line, i + run, run);
                if (close < 0) {
                    out.append(line, i, i + run);
                    i += run;
                } else {
                    out.append(line, i, close + run);
                    i = close + run;
                }
                continue;
            }
            int next = i;
            while (next < length && line.charAt(next) != '`') next++;
            out.append(transformInline(line.substring(i, next), rawBase, blobBase));
            i = next;
        }
        return out.toString();
    }

    private static int findBacktickClose(String line, int from, int run) {
        int i = from;
        while (i < line.length()) {
            if (line.charAt(i) == '`') {
                int n = 1;
                while (i + n < line.length() && line.charAt(i + n) == '`') n++;
                if (n == run) return i;
                i += n;
            } else {
                i++;
            }
        }
        return -1;
    }

    private static String transformInline(String segment, String rawBase, String blobBase) {
        if (segment.isEmpty()) return segment;
        String text = segment;
        if (rawBase != null) {
            text = rewriteMarkdownDestinations(text, rawBase, blobBase);
            text = rewriteHtmlAttributes(text, rawBase, blobBase);
        }
        text = replaceOutsideTags(text);
        return text;
    }

    private static String replaceOutsideTags(String text) {
        Matcher tags = HTML_TAG.matcher(text);
        StringBuilder out = new StringBuilder(text.length() + 16);
        int last = 0;
        while (tags.find()) {
            out.append(transformPlainText(text, last, tags.start()));
            out.append(tags.group());
            last = tags.end();
        }
        out.append(transformPlainText(text, last, text.length()));
        return out.toString();
    }

    private static String transformPlainText(String text, int start, int end) {
        if (start >= end) return "";
        String piece = text.substring(start, end);
        piece = replaceEmoji(piece);
        piece = autolink(piece, text, start);
        return piece;
    }

    private static String replaceEmoji(String piece) {
        if (piece.indexOf(':') < 0) return piece;
        Matcher m = EMOJI_CODE.matcher(piece);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String emoji = EMOJI.get(m.group(1));
            if (emoji == null) continue;
            sb.append(piece, last, m.start()).append(emoji);
            last = m.end();
        }
        sb.append(piece, last, piece.length());
        return sb.toString();
    }

    private static String autolink(String piece, String full, int offset) {
        if (piece.indexOf("http") < 0) return piece;
        Matcher m = BARE_URL.matcher(piece);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            int start = m.start();
            char before = start > 0 ? piece.charAt(start - 1) : (offset > 0 ? full.charAt(offset - 1) : ' ');
            if (before == '<' || before == '"' || before == '\'' || before == '=' || before == '[' || before == '/' || before == '@') {
                continue;
            }
            if (before == '(') {
                char beforeParen;
                if (start > 1) beforeParen = piece.charAt(start - 2);
                else if (start == 1) beforeParen = offset > 0 ? full.charAt(offset - 1) : ' ';
                else beforeParen = offset > 1 ? full.charAt(offset - 2) : ' ';
                if (beforeParen == ']') continue;
            }
            String url = m.group();
            int end = trimUrlEnd(url);
            if (end < 12) continue;
            String clean = url.substring(0, end);
            int cleanEnd = start + end;
            char after = cleanEnd < piece.length() ? piece.charAt(cleanEnd) : ' ';
            if (after == ']' && cleanEnd + 1 < piece.length() && piece.charAt(cleanEnd + 1) == '(') continue;
            sb.append(piece, last, start).append('<').append(clean).append('>');
            last = cleanEnd;
        }
        sb.append(piece, last, piece.length());
        return sb.toString();
    }

    private static int trimUrlEnd(String url) {
        int end = url.length();
        while (end > 0) {
            char c = url.charAt(end - 1);
            if (c == '.' || c == ',' || c == ';' || c == ':' || c == '!' || c == '?' || c == '*' || c == '_' || c == '~') {
                end--;
            } else if (c == ')') {
                int open = 0;
                int close = 0;
                for (int i = 0; i < end; i++) {
                    char x = url.charAt(i);
                    if (x == '(') open++;
                    else if (x == ')') close++;
                }
                if (close > open) end--;
                else break;
            } else {
                break;
            }
        }
        return end;
    }

    private static String rewriteMarkdownDestinations(String text, String rawBase, String blobBase) {
        StringBuilder out = new StringBuilder(text.length() + 32);
        int i = 0;
        while (i < text.length()) {
            int close = text.indexOf("](", i);
            if (close < 0) {
                out.append(text, i, text.length());
                break;
            }
            int destStart = close + 2;
            int j = destStart;
            while (j < text.length() && (text.charAt(j) == ' ' || text.charAt(j) == '\t')) j++;
            boolean angle = j < text.length() && text.charAt(j) == '<';
            int urlStart = angle ? j + 1 : j;
            int urlEnd = urlStart;
            if (angle) {
                while (urlEnd < text.length() && text.charAt(urlEnd) != '>') urlEnd++;
            } else {
                int depth = 0;
                while (urlEnd < text.length()) {
                    char c = text.charAt(urlEnd);
                    if (c == ' ' || c == '\t') break;
                    if (c == '(') depth++;
                    if (c == ')') {
                        if (depth == 0) break;
                        depth--;
                    }
                    urlEnd++;
                }
            }
            if (urlEnd > text.length() || urlStart > urlEnd) {
                out.append(text, i, destStart);
                i = destStart;
                continue;
            }
            String url = text.substring(urlStart, urlEnd);
            boolean image = isImageLabel(text, close);
            String resolved = resolveUrl(url, image ? rawBase : blobBase);
            out.append(text, i, urlStart).append(resolved);
            i = urlEnd;
        }
        return out.toString();
    }

    private static boolean isImageLabel(String text, int closeBracket) {
        int depth = 0;
        for (int k = closeBracket; k >= 0; k--) {
            char c = text.charAt(k);
            if (c == ']') depth++;
            else if (c == '[') {
                depth--;
                if (depth == 0) return k > 0 && text.charAt(k - 1) == '!';
            }
        }
        return false;
    }

    private static String rewriteHtmlAttributes(String text, String rawBase, String blobBase) {
        if (text.indexOf('<') < 0) return text;
        Matcher m = HTML_URL_ATTR.matcher(text);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            boolean isSrc = "src".equalsIgnoreCase(m.group(2));
            String resolved = resolveUrl(m.group(4), isSrc ? rawBase : blobBase);
            sb.append(text, last, m.start())
                    .append(m.group(1)).append(m.group(3)).append(resolved).append(m.group(3));
            last = m.end();
        }
        sb.append(text, last, text.length());
        return sb.toString();
    }

    private static String resolveReferenceDefinition(String line, String rawBase, String blobBase) {
        int colon = line.indexOf("]:");
        if (colon < 0) return line;
        int start = colon + 2;
        while (start < line.length() && (line.charAt(start) == ' ' || line.charAt(start) == '\t')) start++;
        int end = start;
        boolean angle = start < line.length() && line.charAt(start) == '<';
        if (angle) {
            start++;
            end = start;
            while (end < line.length() && line.charAt(end) != '>') end++;
        } else {
            while (end < line.length() && line.charAt(end) != ' ' && line.charAt(end) != '\t') end++;
        }
        if (end > line.length() || start > end) return line;
        String url = line.substring(start, end);
        String lower = url.toLowerCase(Locale.ROOT);
        boolean image = lower.matches(".*\\.(png|jpe?g|gif|svg|webp|bmp|ico)(?:[?#].*)?$");
        return line.substring(0, start) + resolveUrl(url, image ? rawBase : blobBase) + line.substring(end);
    }

    static String resolveUrl(String url, String base) {
        if (url == null || url.isEmpty() || base == null) return url;
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return url;
        if (trimmed.startsWith("//")) return "https:" + trimmed;
        if (ABSOLUTE_SCHEME.matcher(trimmed).find()) return url;

        String suffix = "";
        int cut = indexOfAny(trimmed, '?', '#');
        String path = trimmed;
        if (cut >= 0) {
            path = trimmed.substring(0, cut);
            suffix = trimmed.substring(cut);
        }
        List<String> segments = new ArrayList<>();
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (!segments.isEmpty()) segments.remove(segments.size() - 1);
                continue;
            }
            segments.add(part);
        }
        StringBuilder sb = new StringBuilder(base);
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) sb.append('/');
            sb.append(segments.get(i).replace(" ", "%20"));
        }
        if (path.endsWith("/") && !segments.isEmpty()) sb.append('/');
        return sb.append(suffix).toString();
    }

    private static int indexOfAny(String s, char a, char b) {
        int x = s.indexOf(a);
        int y = s.indexOf(b);
        if (x < 0) return y;
        if (y < 0) return x;
        return Math.min(x, y);
    }
}
