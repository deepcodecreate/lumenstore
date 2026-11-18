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

package com.opensourcefinder.app.glide;

import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.SimpleResource;
import com.caverock.androidsvg.SVG;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SvgDecoder implements ResourceDecoder<InputStream, SVG> {

    @Override
    public boolean handles(@NonNull InputStream source, @NonNull Options options) {
        return true;
    }

    @Nullable
    @Override
    public Resource<SVG> decode(@NonNull InputStream source, int width, int height, @NonNull Options options) {
        try {
            String xml = readFully(source);
            xml = inlineNestedSvgImages(xml);
            xml = patchBareHrefAttributes(xml);
            SVG svg = SVG.getFromString(xml);
            return new SimpleResource<>(svg);
        } catch (Exception e) {
            return null;
        }
    }

    private static String readFully(InputStream source) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = source.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
    }

    private static final Pattern IMAGE_TAG = Pattern.compile("<image\\b([^>]*?)/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_X = Pattern.compile("\\bx\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_Y = Pattern.compile("\\by\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_WIDTH = Pattern.compile("\\bwidth\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_HEIGHT = Pattern.compile("\\bheight\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_HREF =
            Pattern.compile("(?:xlink:href|href)\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern INNER_SVG_OPEN = Pattern.compile("<svg\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern INNER_VIEWBOX = Pattern.compile("viewBox\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    private static String inlineNestedSvgImages(String xml) {
        Matcher imageMatcher = IMAGE_TAG.matcher(xml);
        StringBuffer patched = new StringBuffer();
        while (imageMatcher.find()) {
            String attrs = imageMatcher.group(1);
            String replacement = tryInlineOne(attrs);
            imageMatcher.appendReplacement(patched,
                    replacement != null ? Matcher.quoteReplacement(replacement) : Matcher.quoteReplacement(imageMatcher.group()));
        }
        imageMatcher.appendTail(patched);
        return patched.toString();
    }

    @Nullable
    private static String tryInlineOne(String attrs) {
        String href = firstGroup(ATTR_HREF, attrs);
        if (href == null || !href.startsWith("data:image/svg+xml")) return null;

        try {
            String innerXml = decodeSvgDataUri(href);
            Matcher openTag = INNER_SVG_OPEN.matcher(innerXml);
            if (!openTag.find()) return null;

            String innerViewBox = firstGroup(INNER_VIEWBOX, openTag.group());
            int bodyStart = openTag.end();
            int bodyEnd = innerXml.lastIndexOf("</svg>");
            if (bodyEnd < bodyStart) return null;
            String innerBody = innerXml.substring(bodyStart, bodyEnd);

            String x = firstGroup(ATTR_X, attrs);
            String y = firstGroup(ATTR_Y, attrs);
            String w = firstGroup(ATTR_WIDTH, attrs);
            String h = firstGroup(ATTR_HEIGHT, attrs);

            StringBuilder out = new StringBuilder("<svg");
            if (x != null) out.append(" x=\"").append(x).append('"');
            if (y != null) out.append(" y=\"").append(y).append('"');
            if (w != null) out.append(" width=\"").append(w).append('"');
            if (h != null) out.append(" height=\"").append(h).append('"');
            if (innerViewBox != null) out.append(" viewBox=\"").append(innerViewBox).append('"');
            out.append('>').append(innerBody).append("</svg>");
            return out.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String decodeSvgDataUri(String href) throws UnsupportedEncodingException {
        int comma = href.indexOf(',');
        String meta = href.substring(0, comma);
        String data = href.substring(comma + 1);
        if (meta.contains(";base64")) {
            byte[] bytes = Base64.decode(data, Base64.DEFAULT);
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return URLDecoder.decode(data, "UTF-8");
    }

    @Nullable
    private static String firstGroup(Pattern pattern, String input) {
        Matcher m = pattern.matcher(input);
        return m.find() ? m.group(1) : null;
    }

    private static final Pattern BARE_HREF =
            Pattern.compile("(?<!xlink:)\\bhref\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern SVG_ROOT_OPEN = Pattern.compile("<svg\\b");
    private static final String XLINK_NS = "xmlns:xlink=\"http://www.w3.org/1999/xlink\"";

    private static String patchBareHrefAttributes(String xml) {
        Matcher hrefMatcher = BARE_HREF.matcher(xml);
        if (!hrefMatcher.find()) return xml;

        StringBuffer patched = new StringBuffer();
        hrefMatcher.reset();
        while (hrefMatcher.find()) {
            String value = hrefMatcher.group(1);
            String replacement = Matcher.quoteReplacement(
                    "xlink:href=\"" + value + "\" href=\"" + value + "\"");
            hrefMatcher.appendReplacement(patched, replacement);
        }
        hrefMatcher.appendTail(patched);
        xml = patched.toString();

        if (!xml.contains("xmlns:xlink")) {
            Matcher rootMatcher = SVG_ROOT_OPEN.matcher(xml);
            if (rootMatcher.find()) {
                xml = new StringBuilder(xml).insert(rootMatcher.end(), " " + XLINK_NS).toString();
            }
        }
        return xml;
    }
}
