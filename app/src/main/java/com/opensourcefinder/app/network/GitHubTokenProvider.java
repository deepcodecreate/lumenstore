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

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class GitHubTokenProvider {

    private static final String PREFS_NAME = "github_auth_prefs";
    private static final String KEY_TOKEN = "github_token";
    private static final String KEY_TOKEN_ENCRYPTED = "github_token_enc";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "lumenstore_github_token_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private static volatile String cachedToken = "";
    private static volatile boolean initialized = false;

    public static synchronized void init(Context context) {
        if (initialized) return;
        SharedPreferences prefs = prefs(context);
        String legacy = prefs.getString(KEY_TOKEN, "");
        String encrypted = prefs.getString(KEY_TOKEN_ENCRYPTED, "");
        if (encrypted != null && !encrypted.isEmpty()) {
            String plain = decrypt(encrypted);
            if (plain == null) {
                prefs.edit().remove(KEY_TOKEN_ENCRYPTED).apply();
                cachedToken = "";
            } else {
                cachedToken = plain;
            }
        } else if (legacy != null && !legacy.trim().isEmpty()) {
            cachedToken = legacy.trim();
            String sealed = encrypt(cachedToken);
            SharedPreferences.Editor editor = prefs.edit().remove(KEY_TOKEN);
            if (sealed != null) editor.putString(KEY_TOKEN_ENCRYPTED, sealed);
            editor.apply();
        }
        initialized = true;
    }

    public static void setToken(String token) {
        cachedToken = token == null ? "" : token.trim();
    }

    public static void saveToken(Context context, String token) {
        setToken(token);
        SharedPreferences.Editor editor = prefs(context).edit().remove(KEY_TOKEN);
        String sealed = cachedToken.isEmpty() ? null : encrypt(cachedToken);
        if (sealed != null) {
            editor.putString(KEY_TOKEN_ENCRYPTED, sealed);
        } else {
            editor.remove(KEY_TOKEN_ENCRYPTED);
        }
        editor.apply();
    }

    public static void clearToken(Context context) {
        cachedToken = "";
        prefs(context).edit().remove(KEY_TOKEN).remove(KEY_TOKEN_ENCRYPTED).apply();
    }

    public static String getToken() {
        return cachedToken;
    }

    public static boolean hasToken() {
        return cachedToken != null && !cachedToken.trim().isEmpty();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static SecretKey secretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            generator.init(new KeyGenParameterSpec.Builder(
                    KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return generator.generateKey();
        }
        return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
    }

    private static String encrypt(String plain) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey());
            byte[] iv = cipher.getIV();
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(sealed, 0, out, iv.length, sealed.length);
            return Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private static String decrypt(String encoded) {
        try {
            byte[] data = Base64.decode(encoded, Base64.NO_WRAP);
            if (data.length <= IV_LENGTH) return null;
            byte[] iv = Arrays.copyOfRange(data, 0, IV_LENGTH);
            byte[] sealed = Arrays.copyOfRange(data, IV_LENGTH, data.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(sealed), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
