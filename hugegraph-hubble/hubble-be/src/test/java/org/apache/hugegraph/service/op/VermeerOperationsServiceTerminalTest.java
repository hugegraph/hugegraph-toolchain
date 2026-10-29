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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import org.apache.hugegraph.testutil.Assert;

public class VermeerOperationsServiceTerminalTest {

    private static final Set<String> ADMIN = OperationsCapabilityService.forLevel("ADMIN");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testKnownTerminalAtDeadlineDiscardsHistoricalSnapshot() throws Exception {
        for (int status : List.of(401, 403, 404, 405)) {
            ControlledHttp http = new ControlledHttp(status);
            BoundaryClock clock = new BoundaryClock(http);
            VermeerOperationsService service = service(http, clock, new AtomicReference<>("old"));
            Assert.assertEquals("UP", cluster(service, false).get("status"));
            ExecutorService callers = Executors.newSingleThreadExecutor();
            try {
                Future<Map<?, ?>> request = callers.submit(() -> {
                    clock.caller.set(Thread.currentThread());
                    return cluster(service, true);
                });
                Assert.assertTrue(http.denyStarted.await(2L, TimeUnit.SECONDS));
                clock.pause = true;
                http.releaseDeny.countDown();
                Assert.assertTrue(clock.workerPaused.await(2L, TimeUnit.SECONDS));
                // Keep the deadline waiter outside the state lock so the already
                // known rejection can complete after its deadline, before timeout.
                Assert.assertTrue(clock.callerPaused.await(2L, TimeUnit.SECONDS));
                clock.releaseWorker.countDown();
                awaitIdle(http.denier.get());
                clock.releaseCaller.countDown();
                assertEmptyTerminal(request.get(2L, TimeUnit.SECONDS), status);
                assertEmptyTerminal(cluster(service, false), status);
            } finally {
                clock.releaseWorker.countDown();
                clock.releaseCaller.countDown();
                http.releaseDeny.countDown();
                callers.shutdownNow();
            }
        }
    }

    @Test
    public void testLateTerminalClearsOnlyItsCachedHistoricalSnapshot() throws Exception {
        for (int status : List.of(401, 403, 404, 405)) {
            ControlledHttp http = new ControlledHttp(status);
            VermeerOperationsService service = service(http, Clock.systemUTC(), new AtomicReference<>("old"));
            Assert.assertEquals("UP", cluster(service, false).get("status"));
            ExecutorService callers = Executors.newSingleThreadExecutor();
            try {
                Map<?, ?> timedOut = timedOutDenial(service, http, callers);
                Assert.assertEquals(1, ((List<?>) timedOut.get("workers")).size());
                http.releaseDeny.countDown();
                awaitIdle(http.denier.get());

                assertEmptyTerminal(cluster(service, false), status);
                // A settled response is immutable, even when the retained cache is revoked.
                Assert.assertEquals(1, ((List<?>) timedOut.get("workers")).size());
                Assert.assertEquals(true, timedOut.get("stale"));
            } finally {
                http.releaseDeny.countDown();
                callers.shutdownNow();
            }
        }
    }

    @Test
    public void testPendingFailureCannotRestoreHistoryRevokedByLateDenial() throws Exception {
        ControlledHttp http = new ControlledHttp(403);
        http.failNext = true;
        VermeerOperationsService service = service(http, Clock.systemUTC(), new AtomicReference<>("old"));
        Assert.assertEquals("UP", cluster(service, false).get("status"));
        ExecutorService callers = Executors.newSingleThreadExecutor();
        try {
            timedOutDenial(service, http, callers);
            Future<Map<?, ?>> pending = callers.submit(() -> cluster(service, true));
            Assert.assertTrue(http.nextStarted.await(2L, TimeUnit.SECONDS));
            http.releaseDeny.countDown();
            awaitIdle(http.denier.get());
            assertEmptyDenied(cluster(service, false));

            http.releaseNext.countDown();
            Map<?, ?> failed = pending.get(2L, TimeUnit.SECONDS);
            assertNoHistory(failed);
            Assert.assertEquals("UNKNOWN", failed.get("status"));
            assertNoHistory(cluster(service, false));
        } finally {
            http.releaseDeny.countDown();
            http.releaseNext.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    public void testLateDenialCannotEraseNewSuccessOrRotatedCredentialCache() throws Exception {
        for (boolean rotate : List.of(false, true)) {
            ControlledHttp http = new ControlledHttp(403);
            AtomicReference<String> token = new AtomicReference<>("old");
            VermeerOperationsService service = service(http, Clock.systemUTC(), token);
            Assert.assertEquals("UP", cluster(service, false).get("status"));
            ExecutorService callers = Executors.newSingleThreadExecutor();
            try {
                timedOutDenial(service, http, callers);
                if (rotate) {
                    token.set("new");
                }
                http.releaseNext.countDown();
                Map<?, ?> newer = cluster(service, true);
                Assert.assertEquals("worker-3", worker(newer));
                http.releaseDeny.countDown();
                awaitIdle(http.denier.get());

                Assert.assertEquals("worker-3", worker(cluster(service, false)));
                Assert.assertEquals(3, http.requests.get());
            } finally {
                http.releaseDeny.countDown();
                http.releaseNext.countDown();
                callers.shutdownNow();
            }
        }
    }

    @Test
    public void testLateDenialClearsSameHistoryAlreadyPublishedByNewerFailure() throws Exception {
        ControlledHttp http = new ControlledHttp(403);
        http.failNext = true;
        VermeerOperationsService service = service(http, Clock.systemUTC(), new AtomicReference<>("old"));
        Assert.assertEquals("UP", cluster(service, false).get("status"));
        ExecutorService callers = Executors.newSingleThreadExecutor();
        try {
            timedOutDenial(service, http, callers);
            http.releaseNext.countDown();
            Map<?, ?> newerFailure = cluster(service, true);
            Assert.assertEquals(true, newerFailure.get("stale"));
            Assert.assertEquals(1, ((List<?>) newerFailure.get("workers")).size());

            http.releaseDeny.countDown();
            awaitIdle(http.denier.get());
            assertEmptyDenied(cluster(service, false));
        } finally {
            http.releaseDeny.countDown();
            http.releaseNext.countDown();
            callers.shutdownNow();
        }
    }

    private static Map<?, ?> timedOutDenial(VermeerOperationsService service, ControlledHttp http,
                                            ExecutorService callers) throws Exception {
        Future<Map<?, ?>> request = callers.submit(() -> cluster(service, true));
        Assert.assertTrue(http.denyStarted.await(2L, TimeUnit.SECONDS));
        Map<?, ?> result = request.get(2L, TimeUnit.SECONDS);
        Assert.assertEquals("vermeer_upstream_timeout", result.get("reason"));
        return result;
    }

    private static VermeerOperationsService service(ControlledHttp http, Clock clock,
                                                     AtomicReference<String> token) {
        return new VermeerOperationsService(true, "http://127.0.0.1:6688", "controlled-token",
                                            30, http, MAPPER, clock, System::nanoTime, 1000, token::get);
    }

    private static Map<?, ?> cluster(VermeerOperationsService service, boolean refresh) {
        Map<String, Object> main = new LinkedHashMap<>(Map.of("status", "UP"));
        return (Map<?, ?>) service.enrich(main, ADMIN, refresh).get("vermeer");
    }

    private static String worker(Map<?, ?> cluster) {
        return (String) ((Map<?, ?>) ((List<?>) cluster.get("workers")).get(0)).get("id");
    }

    private static void assertEmptyDenied(Map<?, ?> cluster) {
        assertEmptyTerminal(cluster, 403);
    }

    private static void assertEmptyTerminal(Map<?, ?> cluster, int status) {
        Assert.assertEquals(status >= 404 ? "vermeer_cluster_api_unsupported" : "vermeer_access_denied",
                            cluster.get("reason"));
        Assert.assertEquals(status >= 404 ? "UNSUPPORTED" : "UNAVAILABLE", cluster.get("availability"));
        assertNoHistory(cluster);
    }

    private static void assertNoHistory(Map<?, ?> cluster) {
        Assert.assertNull(cluster.get("master"));
        Assert.assertNull(cluster.get("last_success_at"));
        Assert.assertEquals(List.of(), cluster.get("workers"));
        Assert.assertEquals(false, cluster.get("stale"));
    }

    private static void awaitIdle(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        while (System.nanoTime() < deadline) {
            boolean collecting = false;
            for (StackTraceElement frame : thread.getStackTrace()) {
                collecting |= frame.getClassName().equals(VermeerOperationsService.class.getName());
            }
            if (!collecting) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1L));
        }
        throw new AssertionError("Released collector did not finish");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5L, TimeUnit.SECONDS)) {
                throw new AssertionError("Controlled operation was not released");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static final class BoundaryClock extends Clock {

        private final ControlledHttp http;
        private final AtomicReference<Thread> caller = new AtomicReference<>();
        private final CountDownLatch workerPaused = new CountDownLatch(1);
        private final CountDownLatch callerPaused = new CountDownLatch(1);
        private final CountDownLatch releaseWorker = new CountDownLatch(1);
        private final CountDownLatch releaseCaller = new CountDownLatch(1);
        private volatile boolean pause;

        private BoundaryClock(ControlledHttp http) {
            this.http = http;
        }

        @Override
        public long millis() {
            if (this.pause && this.http.denialReturned) {
                if (Thread.currentThread() == this.http.denier.get()) {
                    this.workerPaused.countDown();
                    await(this.releaseWorker);
                } else if (Thread.currentThread() == this.caller.get()) {
                    this.callerPaused.countDown();
                    await(this.releaseCaller);
                }
            }
            return 1000L;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(this.millis());
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private static final class ControlledHttp extends OperationsHttpClient {

        private final int denial;
        private final AtomicInteger requests = new AtomicInteger();
        private final AtomicReference<Thread> denier = new AtomicReference<>();
        private final CountDownLatch denyStarted = new CountDownLatch(1);
        private final CountDownLatch nextStarted = new CountDownLatch(1);
        private final CountDownLatch releaseDeny = new CountDownLatch(1);
        private final CountDownLatch releaseNext = new CountDownLatch(1);
        private volatile boolean denialReturned;
        private volatile boolean failNext;

        private ControlledHttp(int denial) {
            super(250, 250, 65536);
            this.denial = denial;
        }

        @Override
        public String getBearer(URI target, String token, Set<String> allowed) {
            int call = this.requests.incrementAndGet();
            if (call == 2) {
                this.denier.set(Thread.currentThread());
                this.denyStarted.countDown();
                await(this.releaseDeny);
                this.denialReturned = true;
                throw new UpstreamRequestException("upstream_http_status_" + this.denial);
            }
            if (call == 3) {
                this.nextStarted.countDown();
                await(this.releaseNext);
                if (this.failNext) {
                    throw new UpstreamRequestException("upstream_timeout");
                }
            }
            Map<String, Object> master = Map.of("id", "master", "name", "master", "ready", true,
                    "checks", Map.of("http_ready", true, "initialized", true, "service_online", true));
            Map<String, Object> worker = Map.of("id", "worker-" + call, "name", "worker-" + call,
                    "ready", true, "checks", Map.of("registered", true, "connection_ready", true,
                            "load_stream_ready", true, "compute_stream_ready", true, "superstep_stream_ready", true));
            try {
                return MAPPER.writeValueAsString(Map.of("schema_version", 1, "observed_at", 1000L,
                        "status", "UP", "registered_workers", 1, "ready_workers", 1,
                        "master", master, "workers", List.of(worker)));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }
}
