/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.apache.hugegraph.service.op;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class OperationsHttpClient {

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final int callTimeoutMillis;
    private final int maxResponseBytes;
    private final OkHttpClient client;

    public OperationsHttpClient(int connectTimeoutMillis,
                                int readTimeoutMillis,
                                int maxResponseBytes) {
        if (connectTimeoutMillis <= 0 || readTimeoutMillis <= 0 ||
            maxResponseBytes <= 0) {
            throw new IllegalArgumentException(
                      "Operations HTTP limits must be positive");
        }
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.callTimeoutMillis = (int) Math.min(
                Integer.MAX_VALUE,
                (long) connectTimeoutMillis + readTimeoutMillis);
        this.maxResponseBytes = maxResponseBytes;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(this.connectTimeoutMillis,
                                TimeUnit.MILLISECONDS)
                .readTimeout(this.readTimeoutMillis, TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .proxy(Proxy.NO_PROXY)
                .build();
    }

    public String get(URI target, String username, String password) {
        return this.get(target, username, password, Set.of());
    }

    public String get(URI target, String username, String password,
                      Set<String> allowedOrigins) {
        return this.get(target, username, password, allowedOrigins,
                        "application/json");
    }

    public String get(URI target, String username, String password,
                      Set<String> allowedOrigins, String accept) {
        return this.request(target, username, password, allowedOrigins, accept,
                            false).getBody();
    }

    public String getBearer(URI target, String token,
                            Set<String> allowedOrigins) {
        String authorization = token == null || token.isEmpty() ?
                               null : "Bearer " + token;
        return this.requestAuthorized(target, authorization, allowedOrigins,
                                      "application/json", false, true).getBody();
    }

    // Readiness uses 503 with a structured explanation; ordinary GETs still reject it.
    public ProbeResponse probe(URI target, String username, String password,
                               Set<String> allowedOrigins) {
        return this.request(target, username, password, allowedOrigins,
                            "application/json", true);
    }

    private ProbeResponse request(URI target, String username, String password,
                                   Set<String> allowedOrigins, String accept,
                                   boolean readiness) {
        String authorization = null;
        if (username != null && !username.trim().isEmpty()) {
            String credential = username + ':' +
                                (password == null ? "" : password);
            String encoded = Base64.getEncoder().encodeToString(
                    credential.getBytes(StandardCharsets.UTF_8));
            authorization = "Basic " + encoded;
        }
        return this.requestAuthorized(target, authorization, allowedOrigins,
                                      accept, readiness, false);
    }

    private ProbeResponse requestAuthorized(URI target, String authorization,
                                             Set<String> allowedOrigins,
                                             String accept, boolean readiness,
                                             boolean boundedCall) {
        validateTarget(target, allowedOrigins);
        if (!"application/json".equals(accept) &&
            !"text/plain".equals(accept)) {
            throw new IllegalArgumentException("Invalid operations media type");
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(target.getHost());
            validateResolvedAddresses(target.getHost(), addresses);
            OkHttpClient.Builder requestBuilder = this.client.newBuilder()
                    .dns(pinnedDns(target.getHost(), addresses));
            if (boundedCall) {
                // Vermeer is an optional enrichment on the overview request.
                // Bound its whole exchange, including a progressing response body,
                // without changing the existing idle-read semantics of PD/Store probes.
                requestBuilder.callTimeout(this.callTimeoutMillis,
                                           TimeUnit.MILLISECONDS);
            }
            OkHttpClient requestClient = requestBuilder.build();
            Request.Builder request = new Request.Builder()
                    .url(target.toString())
                    .get()
                    .header("Accept", accept);
            if (authorization != null) {
                request.header("Authorization", authorization);
            }
            try (Response response = requestClient.newCall(request.build())
                                              .execute()) {
                int status = response.code();
                if ((status < 200 || status >= 300) && !(readiness && status == 503)) {
                    throw new UpstreamRequestException(
                              "upstream_http_status_" + status, null,
                              Map.of("request_path", target.getRawPath(), "http_status", status));
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new UpstreamRequestException("upstream_empty_body", null,
                                                       Map.of("request_path", target.getRawPath()));
                }
                if (body.contentLength() > this.maxResponseBytes) {
                    throw new UpstreamResponseTooLargeException(
                            target.getRawPath(), this.maxResponseBytes, body.contentLength(), true);
                }
                try (InputStream input = body.byteStream()) {
                    return new ProbeResponse(status, this.readLimited(input, target.getRawPath()));
                }
            }
        } catch (UpstreamRequestException e) {
            throw e;
        } catch (InterruptedIOException e) {
            String reason = boundedCall || e instanceof SocketTimeoutException ?
                            "upstream_timeout" : "upstream_unavailable";
            throw new UpstreamRequestException(reason, e,
                                               Map.of("request_path", target.getRawPath()));
        } catch (IOException | IllegalArgumentException e) {
            throw new UpstreamRequestException("upstream_unavailable", e,
                                               Map.of("request_path", target.getRawPath()));
        }
    }

    public static final class ProbeResponse {

        private final int status;
        private final String body;

        private ProbeResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }

        public int getStatus() {
            return this.status;
        }

        public String getBody() {
            return this.body;
        }
    }

    public static void validateTarget(URI target, Set<String> allowedOrigins) {
        if (target == null || target.getScheme() == null ||
            target.getHost() == null || target.getUserInfo() != null ||
            target.getFragment() != null) {
            throw new IllegalArgumentException("Invalid operations target");
        }
        String scheme = target.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Invalid operations protocol");
        }
        int port = target.getPort();
        if (port < 0) {
            port = "https".equals(scheme) ? 443 : 80;
        }
        String origin = scheme + "://" + normalizedHost(target) + ':' + port;
        if (allowedOrigins != null && !allowedOrigins.isEmpty() &&
            !allowedOrigins.contains(origin)) {
            throw new IllegalArgumentException("Untrusted operations target");
        }
    }

    public static String authority(URI target) {
        validateTarget(target, Set.of());
        String scheme = target.getScheme().toLowerCase(Locale.ROOT);
        int port = target.getPort();
        if (port < 0) {
            port = "https".equals(scheme) ? 443 : 80;
        }
        return normalizedHost(target) + ':' + port;
    }

    public static String origin(URI target) {
        validateTarget(target, Set.of());
        return target.getScheme().toLowerCase(Locale.ROOT) + "://" +
               authority(target);
    }

    static URI resolveTarget(URI target) {
        validateTarget(target, Set.of());
        try {
            InetAddress[] addresses = InetAddress.getAllByName(target.getHost());
            return resolveTarget(target, addresses);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unresolvable operations target",
                                               e);
        }
    }

    static URI resolveTarget(URI target, InetAddress[] addresses) {
        validateTarget(target, Set.of());
        validateResolvedAddresses(target.getHost(), addresses);
        return target;
    }

    static Dns pinnedDns(String expectedHost, InetAddress[] addresses) {
        List<InetAddress> pinned = Arrays.asList(addresses.clone());
        return hostname -> {
            if (!expectedHost.equalsIgnoreCase(hostname)) {
                throw new UnknownHostException("Unexpected operations host");
            }
            return pinned;
        };
    }

    static void validateResolvedAddresses(String requestedHost,
                                          InetAddress[] addresses) {
        if (addresses == null || addresses.length == 0) {
            throw new IllegalArgumentException("Unresolvable operations target");
        }
        boolean literal = isIpLiteral(requestedHost);
        for (InetAddress address : addresses) {
            if (address.isAnyLocalAddress() || address.isLinkLocalAddress() ||
                address.isMulticastAddress() ||
                address.isLoopbackAddress() && !literal) {
                throw new IllegalArgumentException("Untrusted operations address");
            }
        }
    }

    private static boolean isIpLiteral(String host) {
        if (host.indexOf(':') >= 0) {
            return true;
        }
        return host.matches("(?:\\d{1,3}\\.){3}\\d{1,3}");
    }

    private static String normalizedHost(URI target) {
        String host = target.getHost().toLowerCase(Locale.ROOT);
        if (host.indexOf(':') < 0 || host.startsWith("[")) {
            return host;
        }
        return '[' + host + ']';
    }

    private String readLimited(InputStream input, String path) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[Math.min(4096, this.maxResponseBytes + 1)];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > this.maxResponseBytes) {
                throw new UpstreamResponseTooLargeException(
                          path, this.maxResponseBytes, total, false);
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
