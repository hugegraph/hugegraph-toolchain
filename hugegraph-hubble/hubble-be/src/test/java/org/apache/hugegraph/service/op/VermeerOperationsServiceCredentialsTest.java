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

public class VermeerOperationsServiceCredentialsTest {

    private static final Set<String> ADMIN = OperationsCapabilityService.forLevel("ADMIN");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testBlockedReadTimesOutWithoutCachedHealthOrUnboundedReaders() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        AtomicReference<String> token = new AtomicReference<>("original");
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        StubHttp http = new StubHttp();
        VermeerOperationsService service = service(http, Clock.systemUTC(), () -> {
            String captured = token.get();
            if (reads.incrementAndGet() == 2) {
                blocked.countDown();
                await(release);
            }
            return captured;
        });
        Assert.assertEquals("UP", cluster(service, false).get("status"));
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<Map<?, ?>> first = executor.submit(() -> cluster(service, true));
            Assert.assertTrue(blocked.await(2L, TimeUnit.SECONDS));
            Future<Map<?, ?>> second = executor.submit(() -> cluster(service, false));
            Future<Boolean> unlocked = executor.submit(() -> {
                synchronized (service) {
                    return true;
                }
            });
            Assert.assertTrue(unlocked.get(1L, TimeUnit.SECONDS));
            assertCredentialsUnavailable(first.get(1L, TimeUnit.SECONDS));
            assertCredentialsUnavailable(second.get(1L, TimeUnit.SECONDS));
            for (int i = 0; i < 20; i++) {
                Future<Map<?, ?>> retried = executor.submit(() -> cluster(service, false));
                assertCredentialsUnavailable(retried.get(1L, TimeUnit.SECONDS));
            }
            Assert.assertEquals(2, reads.get());
            Assert.assertEquals(1, http.requests.get());

            // The stalled read captured the old token before the rotation. Its late
            // completion must be discarded, including by subsequent request callers.
            token.set("rotated");
            release.countDown();
            Assert.assertEquals("worker-rotated", worker(awaitHealthy(service)));
            Assert.assertEquals(3, reads.get());
            Assert.assertEquals(2, http.requests.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void testOlderSuccessfulReadCannotRollBackNewCredentialCache() throws Exception {
        AtomicReference<String> token = new AtomicReference<>("old");
        assertLateReadDoesNotChangeNewCache(token::get, () -> token.set("new"));
    }

    @Test
    public void testOlderSameCredentialReadCanUseNewerSuccessfulCache() throws Exception {
        DelayedClock clock = new DelayedClock();
        StubHttp http = new StubHttp();
        VermeerOperationsService service = service(http, clock, () -> "unchanged");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Map<?, ?>> old = executor.submit(() -> {
                clock.delayedThread.set(Thread.currentThread());
                return cluster(service, false);
            });
            Assert.assertTrue(clock.delayed.await(2L, TimeUnit.SECONDS));
            Assert.assertEquals("UP", cluster(service, false).get("status"));

            clock.release.countDown();
            Map<?, ?> shared = old.get(2L, TimeUnit.SECONDS);
            Assert.assertEquals("UP", shared.get("status"));
            Assert.assertEquals("worker-unchanged", worker(shared));
            Assert.assertEquals(1, http.requests.get());
        } finally {
            clock.release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void testOlderFailedReadCannotClearNewCredentialCache() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        assertLateReadDoesNotChangeNewCache(() -> {
            if (reads.incrementAndGet() == 1) {
                throw new IOException("unavailable");
            }
            return "new";
        }, () -> { });
    }

    @Test
    public void testOlderTimedOutReadCannotClearNewCredentialCache() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        CountDownLatch releaseReader = new CountDownLatch(1);
        try {
            assertLateReadDoesNotChangeNewCache(() -> {
                if (reads.incrementAndGet() == 1) {
                    await(releaseReader);
                    return "old";
                }
                return "new";
            }, releaseReader::countDown);
        } finally {
            releaseReader.countDown();
        }
    }

    private static void assertLateReadDoesNotChangeNewCache(VermeerTokenReader.Reader reader,
                                                            Runnable beforeNewRead) throws Exception {
        DelayedClock clock = new DelayedClock();
        StubHttp http = new StubHttp();
        VermeerOperationsService service = service(http, clock, reader);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Map<?, ?>> old = executor.submit(() -> {
                clock.delayedThread.set(Thread.currentThread());
                return cluster(service, false);
            });
            Assert.assertTrue(clock.delayed.await(2L, TimeUnit.SECONDS));
            beforeNewRead.run();
            Assert.assertEquals("worker-new", worker(awaitHealthy(service)));
            Assert.assertEquals(1, http.requests.get());

            clock.release.countDown();
            assertCredentialsUnavailable(old.get(2L, TimeUnit.SECONDS));
            Assert.assertEquals("worker-new", worker(cluster(service, false)));
            Assert.assertEquals(1, http.requests.get());
        } finally {
            clock.release.countDown();
            executor.shutdownNow();
        }
    }

    private static VermeerOperationsService service(StubHttp http, Clock clock,
                                                     VermeerTokenReader.Reader reader) {
        return new VermeerOperationsService(true, "http://127.0.0.1:6688", "controlled-token",
                                            30, http, MAPPER, clock, System::nanoTime, 100, reader);
    }

    private static Map<?, ?> awaitHealthy(VermeerOperationsService service) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        do {
            Map<?, ?> result = cluster(service, false);
            if ("UP".equals(result.get("status"))) {
                return result;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1L));
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Credential reader did not recover after the actual I/O finished");
    }

    private static Map<?, ?> cluster(VermeerOperationsService service, boolean refresh) {
        Map<String, Object> main = new LinkedHashMap<>(Map.of("status", "UP"));
        return (Map<?, ?>) service.enrich(main, ADMIN, refresh).get("vermeer");
    }

    private static String worker(Map<?, ?> cluster) {
        return (String) ((Map<?, ?>) ((List<?>) cluster.get("workers")).get(0)).get("id");
    }

    private static void assertCredentialsUnavailable(Map<?, ?> cluster) {
        Assert.assertEquals("vermeer_credentials_unavailable", cluster.get("reason"));
        Assert.assertEquals("UNKNOWN", cluster.get("status"));
        Assert.assertEquals("UNAVAILABLE", cluster.get("availability"));
        Assert.assertEquals(false, cluster.get("stale"));
        Assert.assertNull(cluster.get("master"));
        Assert.assertNull(cluster.get("last_success_at"));
        Assert.assertEquals(List.of(), cluster.get("workers"));
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5L, TimeUnit.SECONDS)) {
                throw new IOException("Timed out waiting to release reader");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private static final class DelayedClock extends Clock {

        private final AtomicReference<Thread> delayedThread = new AtomicReference<>();
        private final CountDownLatch delayed = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public long millis() {
            if (this.delayedThread.compareAndSet(Thread.currentThread(), null)) {
                this.delayed.countDown();
                try {
                    await(this.release);
                } catch (IOException e) {
                    throw new AssertionError(e);
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

    private static final class StubHttp extends OperationsHttpClient {

        private final AtomicInteger requests = new AtomicInteger();

        private StubHttp() {
            super(100, 100, 65536);
        }

        @Override
        public String getBearer(URI target, String token, Set<String> allowed) {
            this.requests.incrementAndGet();
            Map<String, Object> master = Map.of("id", "master", "name", "master", "ready", true,
                    "checks", Map.of("http_ready", true, "initialized", true, "service_online", true));
            Map<String, Object> worker = Map.of("id", "worker-" + token,
                    "name", "worker-" + token, "ready", true,
                    "checks", Map.of("registered", true, "connection_ready", true,
                                     "load_stream_ready", true, "compute_stream_ready", true,
                                     "superstep_stream_ready", true));
            try {
                return MAPPER.writeValueAsString(Map.of(
                        "schema_version", 1, "observed_at", 1000L, "status", "UP",
                        "registered_workers", 1, "ready_workers", 1,
                        "master", master, "workers", List.of(worker)));
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
    }
}
