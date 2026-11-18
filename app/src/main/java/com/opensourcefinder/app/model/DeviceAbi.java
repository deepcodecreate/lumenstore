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

package com.opensourcefinder.app.model;

import android.os.Build;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DeviceAbi {

    public static final String UNIVERSAL = "universal";
    private static final int INCOMPATIBLE = 1000;
    private static final int UNSPECIFIED = 1;
    private static final Pattern NAME_TOKEN = Pattern.compile(
            "(?<![a-z0-9])(arm64-v8a|arm64v8a|arm64|aarch64|armv8|armeabi-v7a|armeabi|armv7a|armv7|arm32|arm|x86[-_]64|amd64|x64|x86|i386|i686|universal|all)(?![a-z0-9])");
    private static volatile List<String> supported;

    private DeviceAbi() {}

    public static List<String> supported() {
        List<String> local = supported;
        if (local == null) {
            local = new ArrayList<>();
            String[] raw = Build.SUPPORTED_ABIS;
            if (raw != null) {
                for (String abi : raw) {
                    if (abi != null) local.add(canonical(abi));
                }
            }
            supported = local;
        }
        return local;
    }

    public static String canonical(String token) {
        if (token == null) return null;
        String t = token.trim().toLowerCase(Locale.ROOT);
        switch (t) {
            case "arm64-v8a":
            case "arm64v8a":
            case "arm64":
            case "aarch64":
            case "armv8":
                return "arm64-v8a";
            case "armeabi-v7a":
            case "armeabi":
            case "armv7a":
            case "armv7":
            case "arm32":
            case "arm":
                return "armeabi-v7a";
            case "x86_64":
            case "x86-64":
            case "amd64":
            case "x64":
                return "x86_64";
            case "x86":
            case "i386":
            case "i686":
                return "x86";
            case "universal":
            case "all":
                return UNIVERSAL;
            default:
                return t;
        }
    }

    public static String fromFileName(String fileName) {
        if (fileName == null) return null;
        Matcher matcher = NAME_TOKEN.matcher(fileName.toLowerCase(Locale.ROOT));
        if (!matcher.find()) return null;
        return canonical(matcher.group(1));
    }

    public static int rank(String abi) {
        String c = canonical(abi);
        if (c == null || c.isEmpty() || UNIVERSAL.equals(c)) return UNSPECIFIED;
        List<String> list = supported();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equals(c)) return i * 2;
        }
        return INCOMPATIBLE;
    }

    public static int rankList(String abiList) {
        if (abiList == null || abiList.trim().isEmpty()) return UNSPECIFIED;
        int best = INCOMPATIBLE;
        for (String part : abiList.split("[,;\\s]+")) {
            if (part.isEmpty()) continue;
            best = Math.min(best, rank(part));
        }
        return best;
    }

    public static int pickBest(long[] codes, String[] names, String[] archs, int count) {
        if (count <= 0) return -1;
        int top = 0;
        for (int i = 1; i < count; i++) {
            if (codes[i] > codes[top]) top = i;
        }
        boolean anyArch = false;
        for (int i = 0; i < count; i++) {
            if (archs[i] != null && !archs[i].trim().isEmpty()) {
                anyArch = true;
                break;
            }
        }
        if (!anyArch) return top;
        int best = top;
        int bestRank = rankList(archs[top]);
        for (int i = 0; i < count; i++) {
            if (!sameRelease(names[i], names[top])) continue;
            int r = rankList(archs[i]);
            if (r < bestRank || (r == bestRank && codes[i] > codes[best])) {
                best = i;
                bestRank = r;
            }
        }
        return best;
    }

    private static boolean sameRelease(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
