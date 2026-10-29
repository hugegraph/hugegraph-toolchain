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

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.apache.hugegraph.exception.ForbiddenException;
import org.apache.hugegraph.testutil.Assert;

public class VermeerOperationsServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ADMIN =
            OperationsCapabilityService.forLevel("ADMIN");

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void testDisabledIsNeutralAndNeverReadsTokenOrCallsUpstream() {
        StubHttp http = new StubHttp();
        VermeerOperationsService service = new VermeerOperationsService(
                false, "invalid", "/not/a/token", 5, http, MAPPER, new TestClock());

        Map<String, Object> response = service.enrich(main("UP"), ADMIN, false);
        Map<?, ?> cluster = cluster(response);

        Assert.assertEquals("UP", response.get("status"));
        Assert.assertEquals("UP", response.get("main_cluster_status"));
        Assert.assertEquals(false, cluster.get("configured"));
        Assert.assertEquals("UNSUPPORTED", cluster.get("availability"));
        Assert.assertEquals("vermeer_disabled", cluster.get("reason"));
        Assert.assertEquals(0, http.requests);
    }

    @Test
    public void testHealthySnapshotKeepsGraphTopologySeparateAndDropsUnknownFields() {
        StubHttp http = new StubHttp();
        VermeerOperationsService service = service(http);
        Map<String, Object> main = main("UP");
        List<String> mainNodes = List.of("server-1", "pd-1", "store-1");
        main.put("nodes", mainNodes);

        Map<String, Object> response = service.enrich(main, ADMIN, false);
        Map<?, ?> cluster = cluster(response);

        Assert.assertSame(mainNodes, response.get("nodes"));
        Assert.assertEquals("UP", response.get("status"));
        Assert.assertEquals("UP", cluster.get("status"));
        Assert.assertEquals(1, cluster.get("registered_workers"));
        Assert.assertEquals(1, cluster.get("ready_workers"));
        Assert.assertEquals("AVAILABLE", cluster.get("metrics_availability"));
        Assert.assertEquals(Map.of("http_ready", true, "initialized", true,
                                   "service_online", true),
                            ((Map<?, ?>) cluster.get("master")).get("checks"));
        Assert.assertEquals("/api/v1/admin/cluster", http.target.getPath());
        Assert.assertEquals(Set.of("http://vermeer.internal:6688"), http.allowed);
        Assert.assertFalse(response.toString().contains("private-signing-factor"));
        Assert.assertFalse(response.toString().contains("auth_token"));
    }

    @Test
    public void testUnreadyWorkerDegradesOverallWithoutChangingMainCluster() {
        StubHttp http = new StubHttp();
        http.payload = payload(1, false, "AVAILABLE");

        Map<String, Object> response = service(http).enrich(main("UP"), ADMIN, false);

        Assert.assertEquals("DEGRADED", response.get("status"));
        Assert.assertEquals("UP", response.get("main_cluster_status"));
        Assert.assertEquals("vermeer_attention", response.get("reason"));
        Assert.assertEquals("DEGRADED", cluster(response).get("status"));
        Assert.assertEquals(0, cluster(response).get("ready_workers"));
    }

    @Test
    public void testNoWorkersIsNotHealthy() {
        StubHttp http = new StubHttp();
        http.payload = payload(0, false, "AVAILABLE");

        Map<?, ?> cluster = cluster(service(http).enrich(main("UP"), ADMIN, false));

        Assert.assertEquals("DEGRADED", cluster.get("status"));
        Assert.assertEquals("no_workers", cluster.get("reason"));
    }

    @Test
    public void testResourceFailureDoesNotMakeReadyWorkerUnavailable() {
        StubHttp http = new StubHttp();
        http.payload = payload(1, true, "UNAVAILABLE");

        Map<?, ?> cluster = cluster(service(http).enrich(main("UP"), ADMIN, false));

        Assert.assertEquals("UP", cluster.get("status"));
        Assert.assertEquals(1, cluster.get("ready_workers"));
        Assert.assertEquals("UNAVAILABLE", cluster.get("metrics_availability"));
    }

    @Test
    public void testUpstreamMetricFreshnessSurvivesCollectorNormalization() throws Exception {
        List<Map<String, Boolean>> cases = List.of(
                Map.of("stale", true), Map.of("fresh", false),
                Map.of("stale", true, "fresh", false));
        for (Map<String, Boolean> flags : cases) {
            StubHttp http = new StubHttp();
            ObjectNode upstream = (ObjectNode) MAPPER.readTree(http.payload);
            ObjectNode worker = (ObjectNode) upstream.path("workers").get(0);
            ObjectNode metrics = (ObjectNode) worker.path("metric_status");
            metrics.put("observed_at", 500L);
            flags.forEach((key, value) -> metrics.put(key, value.booleanValue()));
            http.payload = MAPPER.writeValueAsString(upstream);

            Map<?, ?> cluster = cluster(service(http).enrich(main("UP"), ADMIN, false));
            Map<?, ?> normalized = (Map<?, ?>) ((List<?>) cluster.get("workers")).get(0);
            Map<?, ?> metricStatus = (Map<?, ?>) normalized.get("metric_status");

            flags.forEach((key, value) -> Assert.assertEquals(value, metricStatus.get(key)));
            Assert.assertEquals(500L, metricStatus.get("observed_at"));
            Assert.assertEquals("AVAILABLE", metricStatus.get("availability"));
            Assert.assertEquals(0.42, ((Map<?, ?>) normalized.get("metrics")).get("cpu_cores"));
            Assert.assertEquals(false, cluster.get("stale"));
            Assert.assertEquals("UP", cluster.get("status"));
            Assert.assertEquals(true, normalized.get("ready"));
        }
    }

    @Test
    public void testTransientFailureRetainsOnlyExplicitlyStaleEvidence() {
        StubHttp http = new StubHttp();
        VermeerOperationsService service = service(http);
        service.enrich(main("UP"), ADMIN, false);
        http.failure = new UpstreamRequestException("upstream_timeout");

        Map<?, ?> cluster = cluster(service.enrich(main("UP"), ADMIN, true));
        Map<?, ?> worker = (Map<?, ?>) ((List<?>) cluster.get("workers")).get(0);

        Assert.assertEquals("UNAVAILABLE", cluster.get("availability"));
        Assert.assertEquals("UNKNOWN", cluster.get("status"));
        Assert.assertEquals(true, cluster.get("stale"));
        Assert.assertNotNull(cluster.get("last_success_at"));
        Assert.assertNull(cluster.get("ready_workers"));
        Assert.assertEquals("UNKNOWN", worker.get("status"));
        Assert.assertNull(worker.get("ready"));
        Assert.assertEquals(true, worker.get("stale"));
    }

    @Test
    public void testRetainedFailuresPreserveSourceTimeAndSuccessfulCollectionTime() {
        long sourceTime = Instant.parse("2026-10-29T09:00:00Z").toEpochMilli();
        long collectedTime = Instant.parse("2026-10-29T09:15:00Z").toEpochMilli();
        for (String failure : List.of("http", "malformed", "deadline")) {
            StubHttp http = new StubHttp();
            http.payload = http.payload.replace("\"observed_at\":1000", "\"observed_at\":" + sourceTime);
            TestClock clock = new TestClock();
            clock.now = collectedTime;
            VermeerOperationsService service = service(http, clock);
            try {
                Map<?, ?> fresh = cluster(service.enrich(main("UP"), ADMIN, false));
                Assert.assertEquals(sourceTime, fresh.get("observed_at"));
                Assert.assertEquals(collectedTime, fresh.get("last_success_at"));
                clock.now += TimeUnit.MINUTES.toMillis(1L);
                configureFailure(http, failure);

                Map<?, ?> retained = cluster(service.enrich(main("UP"), ADMIN, true));

                Assert.assertEquals(sourceTime, retained.get("observed_at"));
                Assert.assertEquals(collectedTime, retained.get("last_success_at"));
                Assert.assertEquals("UNKNOWN", retained.get("status"));
                Assert.assertEquals(true, retained.get("stale"));
                Assert.assertEquals(sourceTime, ((Map<?, ?>) retained.get("master")).get("observed_at"));
                Map<?, ?> worker = (Map<?, ?>) ((List<?>) retained.get("workers")).get(0);
                Assert.assertEquals(sourceTime, worker.get("observed_at"));
            } finally {
                if (http.release != null) {
                    http.release.countDown();
                }
                service.close();
            }
        }
    }

    @Test
    public void testFailureWithoutSuccessRetainsAttemptTimeWithoutHistoricalData() {
        for (String failure : List.of("http", "malformed", "deadline")) {
            StubHttp http = new StubHttp();
            configureFailure(http, failure);
            TestClock clock = new TestClock();
            clock.now = Instant.parse("2026-10-29T09:16:00Z").toEpochMilli();
            VermeerOperationsService service = service(http, clock);
            try {
                Map<?, ?> failed = cluster(service.enrich(main("UP"), ADMIN, false));
                Assert.assertEquals(clock.now, failed.get("observed_at"));
                Assert.assertNull(failed.get("last_success_at"));
                Assert.assertNull(failed.get("master"));
                Assert.assertEquals(List.of(), failed.get("workers"));
                Assert.assertEquals(false, failed.get("stale"));
            } finally {
                if (http.release != null) {
                    http.release.countDown();
                }
                service.close();
            }
        }
    }

    @Test
    public void testDeniedServiceIdentityClearsPreviouslySuccessfulSnapshot() {
        for (int status : List.of(401, 403)) {
            StubHttp http = new StubHttp();
            VermeerOperationsService service = service(http);
            service.enrich(main("UP"), ADMIN, false);
            http.failure = new UpstreamRequestException("upstream_http_status_" + status);

            Map<?, ?> cluster = cluster(service.enrich(main("UP"), ADMIN, true));

            Assert.assertEquals("vermeer_access_denied", cluster.get("reason"));
            Assert.assertEquals(false, cluster.get("stale"));
            Assert.assertNull(cluster.get("master"));
            Assert.assertEquals(List.of(), cluster.get("workers"));
        }
    }

    @Test
    public void testDeniedAndUnsupportedSnapshotsRespectCacheTtl() {
        for (int status : List.of(401, 403, 404, 405)) {
            StubHttp http = new StubHttp();
            TestClock clock = new TestClock();
            VermeerOperationsService service = service(http, clock);
            service.enrich(main("UP"), ADMIN, false);
            http.failure = new UpstreamRequestException("upstream_http_status_" + status);
            Map<?, ?> denied = cluster(service.enrich(main("UP"), ADMIN, true));
            Assert.assertEquals(2, http.requests);
            Assert.assertNull(denied.get("master"));
            Assert.assertNull(denied.get("last_success_at"));

            service.enrich(main("UP"), ADMIN, false);
            clock.elapsedMillis += 4999;
            service.enrich(main("UP"), ADMIN, false);
            Assert.assertEquals(2, http.requests);
            clock.elapsedMillis++;
            service.enrich(main("UP"), ADMIN, false);
            Assert.assertEquals(3, http.requests);
            service.enrich(main("UP"), ADMIN, true);
            Assert.assertEquals(4, http.requests);

            http.failure = null;
            clock.elapsedMillis += 5000;
            Assert.assertEquals("UP", cluster(service.enrich(main("UP"), ADMIN, false)).get("status"));
            Assert.assertEquals(5, http.requests);
        }
    }

    @Test
    public void testCredentialRotationBypassesCachedDenialAndUnsupportedResponses() throws Exception {
        Path file = this.temporary.newFile("rotating-token").toPath();
        for (int status : List.of(401, 403, 404, 405)) {
            Files.writeString(file, "original-token\n");
            StubHttp http = new StubHttp();
            VermeerOperationsService service = new VermeerOperationsService(
                    true, "http://vermeer.internal:6688", file.toString(),
                    5, http, MAPPER, new TestClock());
            service.enrich(main("UP"), ADMIN, false);
            http.failure = new UpstreamRequestException("upstream_http_status_" + status);
            service.enrich(main("UP"), ADMIN, true);
            service.enrich(main("UP"), ADMIN, false);
            Assert.assertEquals(2, http.requests);

            Files.writeString(file, "rotated-token\n");
            http.failure = null;
            Map<?, ?> fresh = cluster(service.enrich(main("UP"), ADMIN, false));

            Assert.assertEquals(3, http.requests);
            Assert.assertEquals("rotated-token", http.token);
            Assert.assertEquals("UP", fresh.get("status"));
            Assert.assertEquals(false, fresh.get("stale"));
        }
    }

    @Test
    public void testMasterAndWorkerMayShareAnIdAcrossRoles() throws Exception {
        StubHttp http = new StubHttp();
        ObjectNode upstream = (ObjectNode) MAPPER.readTree(http.payload);
        ObjectNode master = (ObjectNode) upstream.path("master");
        ObjectNode worker = (ObjectNode) upstream.path("workers").get(0);
        master.put("id", "master").put("name", "master");
        worker.put("id", "master").put("name", "master");
        http.payload = MAPPER.writeValueAsString(upstream);

        Map<?, ?> normalized = cluster(service(http).enrich(main("UP"), ADMIN, false));
        Map<?, ?> normalizedMaster = (Map<?, ?>) normalized.get("master");
        Map<?, ?> normalizedWorker = (Map<?, ?>) ((List<?>) normalized.get("workers")).get(0);

        Assert.assertEquals("UP", normalized.get("status"));
        Assert.assertEquals("master", normalizedMaster.get("id"));
        Assert.assertEquals("master", normalizedWorker.get("id"));
        Assert.assertEquals("MASTER", normalizedMaster.get("role"));
        Assert.assertEquals("WORKER", normalizedWorker.get("role"));
    }

    @Test
    public void testWorkerIdsRemainUniqueWithinTheirRole() throws Exception {
        StubHttp http = new StubHttp();
        ObjectNode upstream = (ObjectNode) MAPPER.readTree(payload(2, true, "AVAILABLE"));
        ((ObjectNode) upstream.path("workers").get(1)).put("id", "worker-0");
        http.payload = MAPPER.writeValueAsString(upstream);

        Map<?, ?> normalized = cluster(service(http).enrich(main("UP"), ADMIN, false));

        Assert.assertEquals("MALFORMED", normalized.get("availability"));
        Assert.assertEquals(List.of(), normalized.get("workers"));
    }

    @Test
    public void testOldApiIsExplicitlyUnsupportedNotHealthy() {
        StubHttp http = new StubHttp();
        http.failure = new UpstreamRequestException("upstream_http_status_404");

        Map<?, ?> cluster = cluster(service(http).enrich(main("UP"), ADMIN, false));

        Assert.assertEquals(true, cluster.get("configured"));
        Assert.assertEquals("UNSUPPORTED", cluster.get("availability"));
        Assert.assertEquals("UNKNOWN", cluster.get("status"));
        Assert.assertEquals("vermeer_cluster_api_unsupported", cluster.get("reason"));
    }

    @Test
    public void testInvalidCountsAndMissingChecksAreMalformed() {
        List<String> payloads = List.of(
                payload(1, true, "AVAILABLE").replace("\"ready_workers\":1", "\"ready_workers\":2"),
                payload(1, true, "AVAILABLE").replace("\"observed_at\":1000", "\"observed_at\":0"),
                payload(1, true, "AVAILABLE").replace("\"compute_stream_ready\":true",
                                                       "\"compute_stream_ready\":false"),
                "{\"schema_version\":1,\"workers\":[],\"master\":{}}",
                "{invalid json");
        for (String payload : payloads) {
            StubHttp http = new StubHttp();
            http.payload = payload;

            Map<?, ?> cluster = cluster(service(http).enrich(main("UP"), ADMIN, false));

            Assert.assertEquals("MALFORMED", cluster.get("availability"));
            Assert.assertEquals("UNKNOWN", cluster.get("status"));
            Assert.assertEquals(List.of(), cluster.get("workers"));
        }
    }

    @Test
    public void testCredentialFileRotatesWithoutReusingCachedPrivilegedData() throws Exception {
        Path file = this.temporary.newFile("token").toPath();
        Files.writeString(file, "first-token\n");
        StubHttp http = new StubHttp();
        VermeerOperationsService service = new VermeerOperationsService(
                true, "http://vermeer.internal:6688", file.toString(),
                5, http, MAPPER, new TestClock());
        service.enrich(main("UP"), ADMIN, false);
        Assert.assertEquals("first-token", http.token);
        http.failure = new UpstreamRequestException("upstream_timeout");
        Files.writeString(file, "second-token\n");

        Map<?, ?> cluster = cluster(service.enrich(main("UP"), ADMIN, false));

        Assert.assertEquals("second-token", http.token);
        Assert.assertEquals(2, http.requests);
        Assert.assertEquals(false, cluster.get("stale"));
        Assert.assertNull(cluster.get("master"));
        Assert.assertFalse(cluster.toString().contains("second-token"));
        Files.delete(file);
        Map<?, ?> missing = cluster(service.enrich(main("UP"), ADMIN, false));
        Assert.assertEquals("vermeer_credentials_unavailable", missing.get("reason"));
        Assert.assertNull(missing.get("master"));
    }

    @Test
    public void testHealthAndTopologyCapabilitiesDoNotLeakResourceData() {
        VermeerOperationsService service = service(new StubHttp());
        Map<?, ?> health = cluster(service.enrich(main("UP"),
                Set.of(OperationsCapabilityService.HEALTH_READ), false));
        Assert.assertFalse(health.containsKey("workers"));
        Assert.assertFalse(health.containsKey("master"));
        Assert.assertFalse(health.containsKey("registered_workers"));

        Map<?, ?> topology = cluster(service.enrich(main("UP"), Set.of(
                OperationsCapabilityService.HEALTH_READ,
                OperationsCapabilityService.TOPOLOGY_READ), false));
        Map<?, ?> worker = (Map<?, ?>) ((List<?>) topology.get("workers")).get(0);
        Assert.assertFalse(worker.containsKey("metrics"));
        Assert.assertFalse(worker.containsKey("metric_status"));
    }

    @Test(expected = ForbiddenException.class)
    public void testHealthPermissionRequiredBeforeCollecting() {
        service(new StubHttp()).enrich(main("UP"), Set.of(), false);
    }

    @Test
    public void testCacheRefreshAndExpiration() {
        StubHttp http = new StubHttp();
        TestClock clock = new TestClock();
        VermeerOperationsService service = service(http, clock);
        service.enrich(main("UP"), ADMIN, false);
        service.enrich(main("UP"), ADMIN, false);
        Assert.assertEquals(1, http.requests);
        service.enrich(main("UP"), ADMIN, true);
        Assert.assertEquals(2, http.requests);
        clock.elapsedMillis += 5001;
        service.enrich(main("UP"), ADMIN, false);
        Assert.assertEquals(3, http.requests);
    }

    @Test
    public void testWallClockRollbackDoesNotExtendCacheLifetime() {
        StubHttp http = new StubHttp();
        TestClock clock = new TestClock();
        VermeerOperationsService service = service(http, clock);
        service.enrich(main("UP"), ADMIN, false);

        clock.now -= TimeUnit.HOURS.toMillis(1);
        clock.elapsedMillis += 5000;
        http.failure = new UpstreamRequestException("upstream_timeout");
        Map<?, ?> expired = cluster(service.enrich(main("UP"), ADMIN, false));

        Assert.assertEquals(2, http.requests);
        Assert.assertEquals("UNKNOWN", expired.get("status"));
        Assert.assertEquals(1000L, expired.get("observed_at"));
    }

    @Test
    public void testWallClockForwardDoesNotExpireCacheEarly() {
        StubHttp http = new StubHttp();
        TestClock clock = new TestClock();
        VermeerOperationsService service = service(http, clock);
        service.enrich(main("UP"), ADMIN, false);

        clock.now += TimeUnit.HOURS.toMillis(1);
        clock.elapsedMillis += 4999;
        service.enrich(main("UP"), ADMIN, false);
        Assert.assertEquals(1, http.requests);

        clock.elapsedMillis++;
        service.enrich(main("UP"), ADMIN, false);
        Assert.assertEquals(2, http.requests);
    }

    @Test
    public void testInvalidOriginCannotChangeCredentialDestination() {
        for (String origin : List.of("http://secret@host:6688", "file:///etc/passwd",
                                     "http://host:6688/path", "http://host:6688?x=1")) {
            StubHttp http = new StubHttp();
            VermeerOperationsService service = new VermeerOperationsService(
                    true, origin, "", 5, http, MAPPER, new TestClock());

            Map<?, ?> cluster = cluster(service.enrich(main("UP"), ADMIN, false));

            Assert.assertEquals("vermeer_configuration_invalid", cluster.get("reason"));
            Assert.assertEquals(0, http.requests);
        }
    }

    @Test
    public void testMainClusterFailureAndReasonAreNotOverwritten() {
        Map<String, Object> main = main("DOWN");
        main.put("reason", "cluster_not_ready");

        Map<String, Object> response = service(new StubHttp()).enrich(main, ADMIN, false);

        Assert.assertEquals("DOWN", response.get("status"));
        Assert.assertEquals("DOWN", response.get("main_cluster_status"));
        Assert.assertEquals("cluster_not_ready", response.get("main_cluster_reason"));
    }

    private static VermeerOperationsService service(StubHttp http) {
        return service(http, new TestClock());
    }

    private static void configureFailure(StubHttp http, String failure) {
        if ("http".equals(failure)) {
            http.failure = new UpstreamRequestException("upstream_timeout");
        } else if ("malformed".equals(failure)) {
            http.payload = "{invalid-json";
        } else {
            http.release = new CountDownLatch(1);
        }
    }

    private static VermeerOperationsService service(StubHttp http, TestClock clock) {
        return new VermeerOperationsService(true, "http://vermeer.internal:6688", "",
                                            5, http, MAPPER, clock,
                                            () -> TimeUnit.MILLISECONDS.toNanos(clock.elapsedMillis),
                                            2500, null);
    }

    private static Map<String, Object> main(String status) {
        return new LinkedHashMap<>(Map.of("status", status));
    }

    private static Map<?, ?> cluster(Map<String, Object> response) {
        return (Map<?, ?>) response.get("vermeer");
    }

    private static String payload(int workers, boolean ready, String metrics) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema_version", 1);
        root.put("observed_at", 1000L);
        root.put("status", workers > 0 && ready ? "UP" : "DEGRADED");
        root.put("reason", workers == 0 ? "no_workers" : "workers_not_ready");
        root.put("registered_workers", workers);
        root.put("ready_workers", ready ? workers : 0);
        root.put("master", node("master-01", true, metrics));
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            nodes.add(node("worker-" + i, ready, metrics));
        }
        root.put("workers", nodes);
        root.put("auth_token", "private-signing-factor");
        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Map<String, Object> node(String name, boolean ready, String metrics) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", name);
        node.put("name", name);
        node.put("ready", ready);
        node.put("reason", "compute_stream_not_ready");
        node.put("checks", name.startsWith("master") ?
                Map.of("http_ready", true, "initialized", true, "service_online", true) :
                Map.of("registered", true, "connection_ready", true,
                       "load_stream_ready", true, "compute_stream_ready", ready,
                       "superstep_stream_ready", true));
        node.put("metrics", Map.of("cpu_cores", 0.42, "rss_bytes", 1200000000L,
                                   "sample_window_seconds", 30));
        node.put("metric_status", Map.of("availability", metrics, "observed_at", 1000L));
        node.put("auth_token", "private-signing-factor");
        return node;
    }

    private static final class StubHttp extends OperationsHttpClient {

        private String payload = payload(1, true, "AVAILABLE");
        private UpstreamRequestException failure;
        private int requests;
        private URI target;
        private Set<String> allowed;
        private String token;
        private CountDownLatch release;

        private StubHttp() {
            super(100, 100, 65536);
        }

        @Override
        public String getBearer(URI target, String token, Set<String> allowed) {
            this.requests++;
            this.target = target;
            this.token = token;
            this.allowed = allowed;
            if (this.release != null) {
                try {
                    if (!this.release.await(5L, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Controlled request was not released");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new UpstreamRequestException("upstream_interrupted");
                }
            }
            if (this.failure != null) {
                throw this.failure;
            }
            return this.payload;
        }
    }

    private static final class TestClock extends Clock {

        private long now = 1000L;
        private long elapsedMillis;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(this.now);
        }
    }
}
