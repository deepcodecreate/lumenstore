/*
 * This file is part of OpenSourceFinder.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.opensourcefinder.app.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class OkHttpUrlConnection extends HttpURLConnection {
    private final OkHttpClient base;
    private final Map<String, List<String>> requestHeaders = new LinkedHashMap<>();
    private ByteArrayOutputStream body;
    private Call call;
    private Response response;
    private IOException failure;

    OkHttpUrlConnection(URL url, OkHttpClient base) {
        super(url);
        this.base = base;
    }

    @Override
    public void setRequestProperty(String key, String value) {
        if (key == null) return;
        List<String> values = new ArrayList<>();
        values.add(value);
        requestHeaders.put(key, values);
    }

    @Override
    public void addRequestProperty(String key, String value) {
        if (key == null) return;
        List<String> values = requestHeaders.get(key);
        if (values == null) {
            values = new ArrayList<>();
            requestHeaders.put(key, values);
        }
        values.add(value);
    }

    @Override
    public String getRequestProperty(String key) {
        List<String> values = requestHeaders.get(key);
        return values == null || values.isEmpty() ? null : values.get(values.size() - 1);
    }

    @Override
    public Map<String, List<String>> getRequestProperties() {
        return requestHeaders;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (!doOutput) throw new ProtocolException("doOutput is false");
        if (body == null) body = new ByteArrayOutputStream();
        return body;
    }

    @Override
    public void connect() throws IOException {
        execute();
    }

    private synchronized Response execute() throws IOException {
        if (response != null) return response;
        if (failure != null) throw failure;
        connected = true;
        OkHttpClient client = base.newBuilder()
                .connectTimeout(getConnectTimeout() > 0 ? getConnectTimeout() : 10_000, TimeUnit.MILLISECONDS)
                .readTimeout(getReadTimeout() > 0 ? getReadTimeout() : 10_000, TimeUnit.MILLISECONDS)
                .followRedirects(getInstanceFollowRedirects())
                .followSslRedirects(getInstanceFollowRedirects())
                .build();
        Request.Builder builder = new Request.Builder().url(getURL());
        for (Map.Entry<String, List<String>> entry : requestHeaders.entrySet()) {
            for (String value : entry.getValue()) {
                builder.addHeader(entry.getKey(), value);
            }
        }
        String httpMethod = getRequestMethod();
        RequestBody requestBody = null;
        if (body != null || "POST".equals(httpMethod) || "PUT".equals(httpMethod) || "PATCH".equals(httpMethod)) {
            String type = getRequestProperty("Content-Type");
            byte[] bytes = body == null ? new byte[0] : body.toByteArray();
            requestBody = RequestBody.create(bytes, type == null ? null : MediaType.parse(type));
        }
        builder.method(httpMethod, requestBody);
        call = client.newCall(builder.build());
        try {
            response = call.execute();
        } catch (IOException e) {
            failure = e;
            throw e;
        }
        responseCode = response.code();
        responseMessage = response.message();
        return response;
    }

    @Override
    public int getResponseCode() throws IOException {
        return execute().code();
    }

    @Override
    public String getResponseMessage() throws IOException {
        return execute().message();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        Response r = execute();
        if (r.code() >= 400) throw new IOException("HTTP " + r.code() + " for " + getURL());
        ResponseBody responseBody = r.body();
        if (responseBody == null) throw new IOException("No response body");
        return responseBody.byteStream();
    }

    @Override
    public InputStream getErrorStream() {
        try {
            Response r = execute();
            if (r.code() < 400 || r.body() == null) return null;
            return r.body().byteStream();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public String getHeaderField(String name) {
        try {
            return name == null ? null : execute().header(name);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public String getHeaderField(int position) {
        try {
            Headers headers = execute().headers();
            return position >= 0 && position < headers.size() ? headers.value(position) : null;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public String getHeaderFieldKey(int position) {
        try {
            Headers headers = execute().headers();
            return position >= 0 && position < headers.size() ? headers.name(position) : null;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        try {
            return execute().headers().toMultimap();
        } catch (IOException e) {
            return new LinkedHashMap<>();
        }
    }

    @Override
    public long getContentLengthLong() {
        String value = getHeaderField("Content-Length");
        if (value == null) return -1L;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    @Override
    public int getContentLength() {
        long length = getContentLengthLong();
        return length > Integer.MAX_VALUE ? -1 : (int) length;
    }

    @Override
    public String getContentType() {
        return getHeaderField("Content-Type");
    }

    @Override
    public void disconnect() {
        Call c = call;
        if (c != null) c.cancel();
        Response r = response;
        if (r != null) {
            try { r.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public boolean usingProxy() {
        return false;
    }
}
