/*
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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.apache.hugegraph.config.HugeConfig;
import org.apache.hugegraph.options.HubbleOptions;

/**
 * A deployment-scoped source, separate from the graph cluster's topology.
 * Only an operator-configured Master is contacted; worker addresses are data.
 */
@Service
public class VermeerOperationsService {

    private static final int MAX_TOKEN_BYTES = 16 * 1024;
    private static final List<String> NODE_TEXT_FIELDS = List.of(
            "id", "name", "group", "grpc_peer", "version", "launch_time");
    private static final List<String> CHECK_FIELDS = List.of(
            "registered", "connection_ready", "load_stream_ready",
            "compute_stream_ready", "superstep_stream_ready");
    private static final List<String> MASTER_CHECK_FIELDS = List.of(
            "http_ready", "initialized", "service_online");
    private static final Set<String> METRIC_AVAILABILITIES = Set.of(
            "AVAILABLE", "UNAVAILABLE", "UNSUPPORTED", "PARTIAL", "MALFORMED");
    private static final Set<String> CLEAR_SUCCESS_REASONS = Set.of(
            "vermeer_access_denied", "vermeer_cluster_api_unsupported",
            "vermeer_configuration_invalid");

    private final boolean enabled;
    private final String address;
    private final String tokenFile;
    private final long ttlNanos;
    private final OperationsHttpClient http;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final LongSupplier ticker;
    private final VermeerTokenReader tokenReader;
    private final int tokenReadTimeoutMillis;
    private final int collectionTimeoutMillis;
    private final ThreadPoolExecutor collector;
    private long appliedCredentialRead;
    private boolean credentialReadSucceeded;
    private String credentialFingerprint;
    private Map<String, Object> cached;
    private Map<String, Object> lastSuccess;
    private long cachedAtNanos;
    private CollectionFlight cachedFlight;
    private CollectionFlight inFlight;

    @Autowired
    public VermeerOperationsService(HugeConfig config, ObjectMapper mapper) {
        this(config.get(HubbleOptions.OPERATIONS_VERMEER_ENABLED),
             config.get(HubbleOptions.OPERATIONS_VERMEER_ADDRESS),
             config.get(HubbleOptions.OPERATIONS_VERMEER_TOKEN_FILE),
             config.get(HubbleOptions.OPERATIONS_CACHE_TTL),
             new OperationsHttpClient(
                     config.get(HubbleOptions.OPERATIONS_CONNECT_TIMEOUT),
                     config.get(HubbleOptions.OPERATIONS_READ_TIMEOUT),
                     config.get(HubbleOptions.OPERATIONS_MAX_RESPONSE_BYTES)),
             mapper, Clock.systemUTC(), System::nanoTime,
             config.get(HubbleOptions.OPERATIONS_READ_TIMEOUT), null);
    }

    VermeerOperationsService(boolean enabled, String address, String tokenFile,
                             int ttlSeconds, OperationsHttpClient http,
                             ObjectMapper mapper, Clock clock) {
        this(enabled, address, tokenFile, ttlSeconds, http, mapper, clock,
             System::nanoTime, 2500, null);
    }

    VermeerOperationsService(boolean enabled, String address, String tokenFile,
                             int ttlSeconds, OperationsHttpClient http,
                             ObjectMapper mapper, Clock clock,
                             LongSupplier ticker, int tokenReadTimeoutMillis,
                             VermeerTokenReader.Reader reader) {
        this.enabled = enabled;
        this.address = address == null ? "" : address.trim();
        this.tokenFile = tokenFile == null ? "" : tokenFile.trim();
        this.ttlNanos = TimeUnit.SECONDS.toNanos(ttlSeconds);
        this.http = http;
        this.mapper = mapper;
        this.clock = clock;
        this.ticker = ticker;
        this.tokenReadTimeoutMillis = tokenReadTimeoutMillis;
        this.tokenReader = new VermeerTokenReader(reader == null ? this::readToken : reader);
        this.collectionTimeoutMillis = http.callTimeoutMillis();
        this.collector = new ThreadPoolExecutor(
                2, 2, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), task -> {
                    Thread thread = new Thread(task, "vermeer-cluster-collector");
                    thread.setDaemon(true);
                    return thread;
                });
        this.collector.allowCoreThreadTimeOut(true);
    }

    public Map<String, Object> enrich(Map<String, Object> overview,
                                      Set<String> capabilities,
                                      boolean refresh) {
        OperationsCapabilityService.requireHealth(capabilities);
        Map<String, Object> vermeer = this.project(this.snapshot(refresh),
                                                   capabilities);
        overview.put("main_cluster_status", overview.get("status"));
        overview.put("main_cluster_reason", overview.get("reason"));
        overview.put("vermeer", vermeer);
        if (Boolean.TRUE.equals(vermeer.get("configured")) &&
            !"UP".equals(vermeer.get("status")) &&
            "UP".equals(overview.get("status"))) {
            overview.put("status", "DEGRADED");
            overview.put("reason", "vermeer_attention");
        }
        return overview;
    }

    private Map<String, Object> snapshot(boolean refresh) {
        if (!this.enabled) {
            return this.empty("UNSUPPORTED", "vermeer_disabled", this.clock.millis());
        }
        VermeerTokenReader.Read credentials = this.tokenFile.isEmpty() ?
                new VermeerTokenReader.Read(0L, "") :
                this.tokenReader.read(this.tokenReadTimeoutMillis);
        long now = this.clock.millis();
        String token;
        String fingerprint;
        CollectionFlight flight;
        boolean collect = false;
        synchronized (this) {
            if (credentials.token() == null) {
                // A late failure must not erase a newer accepted credential read.
                if (credentials.sequence() > this.appliedCredentialRead ||
                    credentials.sequence() == this.appliedCredentialRead &&
                    !this.credentialReadSucceeded) {
                    this.clearLocked();
                    this.appliedCredentialRead = credentials.sequence();
                    this.credentialReadSucceeded = false;
                }
                return this.empty("UNAVAILABLE",
                                  "vermeer_credentials_unavailable", now);
            }
            token = credentials.token();
            fingerprint = fingerprint(token);
            if (credentials.sequence() < this.appliedCredentialRead) {
                if (!this.credentialReadSucceeded ||
                    !Objects.equals(fingerprint, this.credentialFingerprint)) {
                    return this.empty("UNAVAILABLE",
                                      "vermeer_credentials_unavailable", now);
                }
            } else {
                this.appliedCredentialRead = credentials.sequence();
                this.credentialReadSucceeded = true;
            }
            if (!Objects.equals(fingerprint, this.credentialFingerprint)) {
                this.clearLocked();
                this.credentialFingerprint = fingerprint;
            }
            if (!refresh && this.cached != null &&
                this.ticker.getAsLong() - this.cachedAtNanos < this.ttlNanos) {
                return this.cached;
            }
            if (this.inFlight != null) {
                flight = this.inFlight;
            } else {
                flight = new CollectionFlight(token, fingerprint, this.lastSuccess,
                                              this.collectionTimeoutMillis);
                this.inFlight = flight;
                collect = true;
            }
        }
        if (collect) {
            try {
                this.collector.execute(() -> this.collectAndPublish(flight, now));
            } catch (RejectedExecutionException e) {
                this.complete(flight, this.failed("UNAVAILABLE", "vermeer_upstream_unavailable",
                                                 this.clock.millis(), flight.previousSuccess), null);
            }
        }
        return this.await(flight);
    }

    private void collectAndPublish(CollectionFlight flight, long now) {
        // A queued task may have expired while every collector was stuck in DNS.
        if (flight.result.isDone() || flight.remainingNanos() <= 0) {
            this.complete(flight, null, null);
            return;
        }
        try {
            this.complete(flight, this.collect(flight.token, now, flight.previousSuccess), null);
        } catch (RuntimeException e) {
            this.complete(flight, null, e);
        }
    }

    private void complete(CollectionFlight flight, Map<String, Object> result, RuntimeException error) {
        long now = this.clock.millis();
        synchronized (this) {
            Object reason = result == null ? null : result.get("reason");
            boolean terminal = reason != null && CLEAR_SUCCESS_REASONS.contains(reason);
            if (flight.result.isDone()) {
                // A late rejection may revoke only the history still owned by this
                // flight or carried forward by another failure. Never alter a
                // settled response or a newer successful snapshot.
                boolean sameHistory = this.cached != null &&
                                      Boolean.TRUE.equals(this.cached.get("stale")) &&
                                      this.lastSuccess != null && this.lastSuccess == flight.previousSuccess;
                if (terminal && (this.cachedFlight == flight || sameHistory) &&
                    Objects.equals(this.credentialFingerprint, flight.fingerprint)) {
                    this.lastSuccess = null;
                    this.cached = this.empty((String) result.get("availability"),
                                             (String) result.get("reason"), now);
                    this.cachedAtNanos = this.ticker.getAsLong();
                }
                return;
            }
            if (!terminal && (flight.remainingNanos() <= 0 || result == null && error == null)) {
                result = this.failed("UNAVAILABLE", "vermeer_upstream_timeout", now,
                                     flight.previousSuccess);
                error = null;
            }
            boolean current = this.inFlight == flight &&
                              Objects.equals(this.credentialFingerprint, flight.fingerprint);
            if (error != null) {
                if (current) {
                    this.inFlight = null;
                }
                flight.result.completeExceptionally(error);
                return;
            }
            if (current) {
                // Another terminal result may have revoked the history captured
                // before this pending flight began. A failure cannot restore it.
                if (Boolean.TRUE.equals(result.get("stale")) &&
                    this.lastSuccess != flight.previousSuccess) {
                    result = this.empty((String) result.get("availability"),
                                        (String) result.get("reason"), now);
                }
                if ("AVAILABLE".equals(result.get("availability"))) {
                    this.lastSuccess = result;
                } else if (CLEAR_SUCCESS_REASONS.contains(result.get("reason"))) {
                    this.lastSuccess = null;
                }
                this.cached = result;
                this.cachedFlight = flight;
                this.cachedAtNanos = this.ticker.getAsLong();
                this.inFlight = null;
            }
            // Every completion takes this monitor. Publish before waking callers,
            // so their next refresh observes the completed cache and its TTL.
            flight.result.complete(result);
        }
    }

    private Map<String, Object> collect(String token, long now,
                                        Map<String, Object> previousSuccess) {
        Map<String, Object> result;
        try {
            URI origin = this.origin();
            URI target = origin.resolve("/api/v1/admin/cluster");
            String payload = this.http.getBearer(
                    target, token, Set.of(OperationsHttpClient.origin(origin)));
            try {
                result = this.normalize(this.mapper.readTree(payload), now);
            } catch (IOException | IllegalArgumentException e) {
                result = this.failed("MALFORMED",
                                     "vermeer_cluster_response_invalid", now,
                                     previousSuccess);
            }
        } catch (UpstreamRequestException e) {
            String reason = e.getMessage();
            if ("upstream_http_status_401".equals(reason) ||
                "upstream_http_status_403".equals(reason)) {
                result = this.empty("UNAVAILABLE", "vermeer_access_denied", now);
            } else if ("upstream_http_status_404".equals(reason) ||
                       "upstream_http_status_405".equals(reason)) {
                result = this.empty("UNSUPPORTED",
                                    "vermeer_cluster_api_unsupported", now);
            } else {
                result = this.failed("UNAVAILABLE",
                                     "vermeer_upstream_unavailable", now,
                                     previousSuccess);
            }
        } catch (IllegalArgumentException e) {
            result = this.empty("UNAVAILABLE",
                                "vermeer_configuration_invalid", now);
        }
        return result;
    }

    private Map<String, Object> await(CollectionFlight flight) {
        try {
            return flight.result.get(Math.max(1L, flight.remainingNanos()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            this.complete(flight, null, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return this.failed("UNAVAILABLE", "vermeer_upstream_unavailable", this.clock.millis(),
                               flight.previousSuccess);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new CompletionException(e.getCause());
        }
        return flight.result.getNow(null);
    }

    private static final class CollectionFlight {

        private final String token;
        private final String fingerprint;
        private final Map<String, Object> previousSuccess;
        private final long deadlineNanos;
        private final CompletableFuture<Map<String, Object>> result = new CompletableFuture<>();

        private CollectionFlight(String token, String fingerprint, Map<String, Object> previousSuccess,
                                  int timeoutMillis) {
            this.token = token;
            this.fingerprint = fingerprint;
            this.previousSuccess = previousSuccess;
            this.deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        }

        private long remainingNanos() {
            return this.deadlineNanos - System.nanoTime();
        }
    }

    private URI origin() {
        URI origin = URI.create(this.address);
        OperationsHttpClient.validateTarget(origin, Set.of());
        String path = origin.getPath();
        if (origin.getQuery() != null ||
            path != null && !path.isEmpty() && !"/".equals(path)) {
            throw new IllegalArgumentException("Invalid Vermeer origin");
        }
        return origin;
    }

    private String readToken() throws IOException {
        if (this.tokenFile.isEmpty()) {
            return "";
        }
        byte[] bytes;
        try (InputStream input = Files.newInputStream(Path.of(this.tokenFile))) {
            bytes = input.readNBytes(MAX_TOKEN_BYTES + 1);
        }
        if (bytes.length > MAX_TOKEN_BYTES) {
            throw new IOException("Invalid Vermeer token file");
        }
        String token = new String(bytes, StandardCharsets.UTF_8).trim();
        if (token.isEmpty() || token.chars().anyMatch(Character::isWhitespace) ||
            token.chars().anyMatch(Character::isISOControl)) {
            throw new IOException("Invalid Vermeer token file");
        }
        return token;
    }

    private static String fingerprint(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void clearLocked() {
        this.cached = null;
        this.cachedFlight = null;
        this.lastSuccess = null;
        this.credentialFingerprint = null;
        this.cachedAtNanos = 0L;
        this.inFlight = null;
    }

    private Map<String, Object> normalize(JsonNode root, long now) {
        if (root == null || !root.isObject() ||
            !root.path("schema_version").isIntegralNumber() ||
            !root.path("schema_version").canConvertToInt() ||
            root.path("schema_version").intValue() != 1 ||
            !root.path("observed_at").isIntegralNumber() ||
            !root.path("observed_at").canConvertToLong() ||
            root.path("observed_at").longValue() <= 0 ||
            !root.path("workers").isArray() ||
            !Set.of("UP", "DEGRADED").contains(root.path("status").asText())) {
            throw new IllegalArgumentException("Invalid Vermeer snapshot");
        }
        long observed = timestamp(root.get("observed_at"), now);
        Map<String, Object> master = this.node(root.path("master"),
                                               "MASTER", observed);
        List<Map<String, Object>> workers = new ArrayList<>();
        int ready = 0;
        Set<String> workerIds = new java.util.HashSet<>();
        for (JsonNode worker : root.path("workers")) {
            Map<String, Object> node = this.node(worker, "WORKER", observed);
            if (!workerIds.add((String) node.get("id"))) {
                throw new IllegalArgumentException("Duplicate Vermeer worker");
            }
            workers.add(node);
            if (Boolean.TRUE.equals(node.get("ready"))) {
                ready++;
            }
        }
        if (!root.path("registered_workers").isIntegralNumber() ||
            !root.path("registered_workers").canConvertToInt() ||
            root.path("registered_workers").intValue() != workers.size() ||
            !root.path("ready_workers").isIntegralNumber() ||
            !root.path("ready_workers").canConvertToInt() ||
            root.path("ready_workers").intValue() != ready) {
            throw new IllegalArgumentException("Invalid Vermeer worker counts");
        }
        boolean up = Boolean.TRUE.equals(master.get("ready")) &&
                     !workers.isEmpty() && ready == workers.size();
        String reason = workers.isEmpty() ? "no_workers" : "workers_not_ready";
        if (!Boolean.TRUE.equals(master.get("ready"))) {
            reason = "master_not_ready";
        }
        if ("DEGRADED".equals(root.path("status").asText())) {
            up = false;
            reason = reason(root.get("reason"), reason);
        }
        Map<String, Object> result = this.empty("AVAILABLE", null, now);
        result.put("status", up ? "UP" : "DEGRADED");
        result.put("reason", up ? null : reason);
        result.put("observed_at", observed);
        result.put("last_success_at", now);
        result.put("master", master);
        result.put("workers", workers);
        result.put("registered_workers", workers.size());
        result.put("ready_workers", ready);
        List<Map<String, Object>> nodes = new ArrayList<>(workers);
        nodes.add(master);
        Set<Object> metricStates = new java.util.HashSet<>();
        for (Map<String, Object> node : nodes) {
            metricStates.add(((Map<?, ?>) node.get("metric_status"))
                                                 .get("availability"));
        }
        result.put("metrics_availability", metricStates.size() == 1 ?
                   metricStates.iterator().next() : "PARTIAL");
        return result;
    }

    private Map<String, Object> node(JsonNode source, String role, long observed) {
        if (!source.isObject() || !source.path("ready").isBoolean() ||
            !source.path("name").isTextual() ||
            source.path("name").asText().trim().isEmpty() ||
            source.path("name").asText().length() > 512) {
            throw new IllegalArgumentException("Invalid Vermeer node");
        }
        Map<String, Object> node = new LinkedHashMap<>();
        for (String field : NODE_TEXT_FIELDS) {
            JsonNode value = source.get(field);
            if (value != null && value.isTextual() && value.asText().length() <= 512) {
                node.put(field, value.asText());
            }
        }
        node.putIfAbsent("id", node.get("name"));
        node.put("role", role);
        boolean ready = source.path("ready").booleanValue();
        node.put("ready", ready);
        node.put("status", ready ? "UP" : "DEGRADED");
        node.put("reason", ready ? null : reason(source.get("reason"),
                "MASTER".equals(role) ? "master_not_ready" : "worker_not_ready"));
        node.put("observed_at", observed);
        Map<String, Object> checks = new LinkedHashMap<>();
        List<String> expectedChecks = "MASTER".equals(role) ?
                                      MASTER_CHECK_FIELDS : CHECK_FIELDS;
        for (String field : expectedChecks) {
            JsonNode value = source.path("checks").get(field);
            if (value != null && value.isBoolean()) {
                checks.put(field, value.booleanValue());
            }
        }
        if (ready &&
            (checks.size() != expectedChecks.size() ||
             checks.values().stream().anyMatch(value -> !Boolean.TRUE.equals(value)))) {
            throw new IllegalArgumentException("Inconsistent Vermeer readiness");
        }
        node.put("checks", checks);
        Map<String, Object> metrics = new LinkedHashMap<>();
        JsonNode cpu = source.path("metrics").get("cpu_cores");
        if (cpu != null && cpu.isNumber() && Double.isFinite(cpu.doubleValue()) &&
            cpu.doubleValue() >= 0) {
            metrics.put("cpu_cores", cpu.doubleValue());
        }
        JsonNode rss = source.path("metrics").get("rss_bytes");
        if (rss != null && rss.isIntegralNumber() && rss.canConvertToLong() &&
            rss.longValue() >= 0) {
            metrics.put("rss_bytes", rss.longValue());
        }
        JsonNode window = source.path("metrics").get("sample_window_seconds");
        if (window != null && window.isNumber() &&
            Double.isFinite(window.doubleValue()) && window.doubleValue() > 0) {
            metrics.put("sample_window_seconds", window.doubleValue());
        }
        node.put("metrics", metrics);
        JsonNode rawMetricStatus = source.path("metric_status");
        String availability = rawMetricStatus.path("availability").asText();
        if (!METRIC_AVAILABILITIES.contains(availability)) {
            availability = "UNSUPPORTED";
        }
        if ("AVAILABLE".equals(availability) &&
            (!metrics.containsKey("cpu_cores") || !metrics.containsKey("rss_bytes"))) {
            availability = metrics.containsKey("cpu_cores") || metrics.containsKey("rss_bytes") ?
                           "PARTIAL" : "UNAVAILABLE";
        }
        Map<String, Object> metricStatus = new LinkedHashMap<>();
        metricStatus.put("availability", availability);
        metricStatus.put("observed_at", timestamp(
                         rawMetricStatus.get("observed_at"), observed));
        metricStatus.put("reason", reason(rawMetricStatus.get("reason"), null));
        for (String field : List.of("stale", "fresh")) {
            JsonNode value = rawMetricStatus.get(field);
            if (value != null && value.isBoolean()) {
                metricStatus.put(field, value.booleanValue());
            }
        }
        node.put("metric_status", metricStatus);
        return node;
    }

    private static long timestamp(JsonNode value, long fallback) {
        return value != null && value.isIntegralNumber() &&
               value.canConvertToLong() && value.longValue() > 0 ?
               value.longValue() : fallback;
    }

    private static String reason(JsonNode value, String fallback) {
        return value != null && value.isTextual() &&
               value.asText().matches("[a-z][a-z0-9_]{0,79}") ?
               value.asText() : fallback;
    }

    private Map<String, Object> empty(String availability, String reason,
                                      long now) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", this.enabled);
        result.put("availability", availability);
        result.put("status", "UNKNOWN");
        result.put("reason", reason);
        result.put("observed_at", now);
        result.put("last_success_at", null);
        result.put("stale", false);
        result.put("registered_workers", this.enabled ? null : 0);
        result.put("ready_workers", this.enabled ? null : 0);
        result.put("master", null);
        result.put("workers", Collections.emptyList());
        result.put("metrics_availability", "UNSUPPORTED");
        return result;
    }

    private Map<String, Object> failed(String availability, String reason,
                                       long now,
                                       Map<String, Object> previousSuccess) {
        if (previousSuccess == null) {
            return this.empty(availability, reason, now);
        }
        Map<String, Object> result = new LinkedHashMap<>(previousSuccess);
        result.put("availability", availability);
        result.put("status", "UNKNOWN");
        result.put("reason", reason);
        result.put("observed_at", now);
        result.put("stale", true);
        result.put("ready_workers", null);
        result.put("metrics_availability", "UNAVAILABLE");
        return result;
    }

    private Map<String, Object> project(Map<String, Object> snapshot,
                                        Set<String> capabilities) {
        Map<String, Object> result = new LinkedHashMap<>(snapshot);
        if (!capabilities.contains(OperationsCapabilityService.TOPOLOGY_READ)) {
            result.remove("master");
            result.remove("workers");
            result.remove("registered_workers");
            result.remove("ready_workers");
            return result;
        }
        boolean metrics = capabilities.contains(
                          OperationsCapabilityService.METRICS_READ);
        boolean stale = Boolean.TRUE.equals(snapshot.get("stale"));
        result.put("master", projectNode(snapshot.get("master"), metrics, stale));
        List<Map<String, Object>> workers = new ArrayList<>();
        for (Object worker : (List<?>) snapshot.get("workers")) {
            workers.add(projectNode(worker, metrics, stale));
        }
        result.put("workers", workers);
        return result;
    }

    private static Map<String, Object> projectNode(Object value,
                                                   boolean includeMetrics,
                                                   boolean stale) {
        if (!(value instanceof Map)) {
            return null;
        }
        Map<String, Object> node = new LinkedHashMap<>();
        ((Map<?, ?>) value).forEach((key, field) -> {
            Object copy = field instanceof Map ?
                          new LinkedHashMap<>((Map<?, ?>) field) : field;
            node.put((String) key, copy);
        });
        node.put("stale", stale);
        if (stale) {
            node.put("status", "UNKNOWN");
            node.put("ready", null);
            Object metricStatus = node.get("metric_status");
            if (metricStatus instanceof Map) {
                Map<String, Object> status = new LinkedHashMap<>();
                ((Map<?, ?>) metricStatus).forEach((key, item) ->
                                                  status.put((String) key, item));
                status.put("stale", true);
                status.put("fresh", false);
                node.put("metric_status", status);
            }
        }
        if (!includeMetrics) {
            node.remove("metrics");
            node.remove("metric_status");
        }
        return node;
    }
}
