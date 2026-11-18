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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;

public final class AppDns implements Dns {
    public static final AppDns INSTANCE = new AppDns();

    private static final long CACHE_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final long NEGATIVE_CACHE_MILLIS = TimeUnit.SECONDS.toMillis(20);
    private static final long RACE_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(8);

    private static final class Cached {
        final List<InetAddress> addresses;
        final long storedAt;

        Cached(List<InetAddress> addresses) {
            this.addresses = addresses;
            this.storedAt = System.currentTimeMillis();
        }
    }

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> failures = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "app-dns");
        thread.setDaemon(true);
        return thread;
    });
    private volatile List<Dns> fallbackResolvers;

    private AppDns() {}

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        long now = System.currentTimeMillis();
        Cached cached = cache.get(hostname);
        if (cached != null && now - cached.storedAt < CACHE_MILLIS) {
            return cached.addresses;
        }
        List<InetAddress> system = null;
        UnknownHostException systemError = null;
        try {
            system = Dns.SYSTEM.lookup(hostname);
        } catch (UnknownHostException e) {
            systemError = e;
        }
        if (isUsable(system)) return system;

        Long failedAt = failures.get(hostname);
        if (failedAt == null || now - failedAt >= NEGATIVE_CACHE_MILLIS) {
            List<InetAddress> raced = race(hostname);
            if (raced != null) {
                failures.remove(hostname);
                cache.put(hostname, new Cached(raced));
                return raced;
            }
            failures.put(hostname, System.currentTimeMillis());
        }
        if (cached != null) return cached.addresses;
        if (system != null && !system.isEmpty()) return system;
        if (systemError != null) throw systemError;
        throw new UnknownHostException(hostname);
    }

    private List<InetAddress> race(String hostname) {
        List<Dns> list = resolvers();
        if (list.isEmpty()) return null;
        ExecutorCompletionService<List<InetAddress>> completion = new ExecutorCompletionService<>(executor);
        List<Future<List<InetAddress>>> pending = new ArrayList<>();
        for (Dns resolver : list) {
            pending.add(completion.submit(() -> resolver.lookup(hostname)));
        }
        long deadline = System.currentTimeMillis() + RACE_TIMEOUT_MILLIS;
        try {
            for (int i = 0; i < pending.size(); i++) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) break;
                Future<List<InetAddress>> done = completion.poll(left, TimeUnit.MILLISECONDS);
                if (done == null) break;
                try {
                    List<InetAddress> result = done.get();
                    if (isUsable(result)) return result;
                } catch (ExecutionException ignored) {
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            for (Future<List<InetAddress>> f : pending) f.cancel(true);
        }
        return null;
    }

    private static boolean isUsable(List<InetAddress> addresses) {
        if (addresses == null || addresses.isEmpty()) return false;
        for (InetAddress a : addresses) {
            if (!a.isAnyLocalAddress() && !a.isLoopbackAddress()
                    && !a.isSiteLocalAddress() && !a.isLinkLocalAddress()) {
                return true;
            }
        }
        return false;
    }

    private List<Dns> resolvers() {
        List<Dns> local = fallbackResolvers;
        if (local != null) return local;
        synchronized (this) {
            if (fallbackResolvers != null) return fallbackResolvers;
            OkHttpClient bootstrap = new OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .callTimeout(7, TimeUnit.SECONDS)
                    .build();
            List<Dns> list = new ArrayList<>();
            addResolver(list, bootstrap, "https://free.shecan.ir/dns-query", "178.22.122.100", "185.51.200.2");
            addResolver(list, bootstrap, "https://dns.403.online/dns-query", "10.202.10.202", "10.202.10.102");
            addResolver(list, bootstrap, "https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1");
            addResolver(list, bootstrap, "https://dns.google/dns-query", "8.8.8.8", "8.8.4.4");
            fallbackResolvers = list;
            return list;
        }
    }

    private static void addResolver(List<Dns> list, OkHttpClient bootstrap, String url, String... ips) {
        try {
            List<InetAddress> hosts = new ArrayList<>();
            for (String ip : ips) hosts.add(InetAddress.getByName(ip));
            list.add(new DnsOverHttps.Builder()
                    .client(bootstrap)
                    .url(HttpUrl.get(url))
                    .bootstrapDnsHosts(hosts)
                    .includeIPv6(false)
                    .resolvePrivateAddresses(true)
                    .build());
        } catch (Exception ignored) {
        }
    }
}
