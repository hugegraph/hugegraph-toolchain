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
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import org.apache.hugegraph.testutil.Assert;

public class VermeerOperationsServiceConcurrencyTest {

    private static final Set<String> ADMIN = Set.of(
            OperationsCapabilityService.HEALTH_READ,
            OperationsCapabilityService.TOPOLOGY_READ,
            OperationsCapabilityService.METRICS_READ);
    private static final long OBSERVED_AT = 1_700_000_000_000L;

    @Test
    public void testBlockedCollectionBoundsLeaderAndFollower() throws Exception {
        DeadlineHttp http = new DeadlineHttp(1);
        VermeerOperationsService service = service(http);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<Map<String, Object>> leader = callers.submit(() -> service.enrich(main(), ADMIN, true));
            Assert.assertTrue(http.firstBlocked.await(2L, TimeUnit.SECONDS));
            Future<Map<String, Object>> follower = callers.submit(() -> service.enrich(main(), ADMIN, true));

            assertCollectionUnavailable(leader.get(1L, TimeUnit.SECONDS));
            assertCollectionUnavailable(follower.get(1L, TimeUnit.SECONDS));
            Assert.assertEquals(1, http.requests.get());
            assertCollectionUnavailable(service.enrich(main(), ADMIN, false));
            Assert.assertEquals(1, http.requests.get());
        } finally {
            http.release.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    public void testBlockedCollectionsCannotCreateUnlimitedReadersOrRunExpiredQueue() throws Exception {
        DeadlineHttp http = new DeadlineHttp(Integer.MAX_VALUE);
        VermeerOperationsService service = service(http);
        ExecutorService callers = Executors.newSingleThreadExecutor();
        try {
            for (int i = 0; i < 8; i++) {
                Future<Map<String, Object>> request = callers.submit(() -> service.enrich(main(), ADMIN, true));
                assertCollectionUnavailable(request.get(1L, TimeUnit.SECONDS));
            }
            Assert.assertEquals(2, http.requests.get());
            http.release.countDown();
            http.awaitCollectorsIdle();
            // An abandoned queued request must never begin its DNS/HTTP work.
            Assert.assertEquals(2, http.requests.get());
            assertCollectionUnavailable(service.enrich(main(), ADMIN, false));
            Assert.assertEquals("worker-3", workerId(service.enrich(main(), ADMIN, true)));
            Assert.assertEquals(3, http.requests.get());
        } finally {
            http.release.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    public void testLateCollectionCannotReplaceNewCredentialCache() throws Exception {
        Path token = Files.createTempFile("vermeer-collection-deadline", ".token");
        token.toFile().deleteOnExit();
        Files.writeString(token, "old-token");
        DeadlineHttp http = new DeadlineHttp(1);
        VermeerOperationsService service = service(http, token);
        ExecutorService callers = Executors.newSingleThreadExecutor();
        try {
            Future<Map<String, Object>> old = callers.submit(() -> service.enrich(main(), ADMIN, true));
            Assert.assertTrue(http.firstBlocked.await(2L, TimeUnit.SECONDS));
            assertCollectionUnavailable(old.get(1L, TimeUnit.SECONDS));

            Files.writeString(token, "new-token");
            Assert.assertEquals("worker-2", workerId(service.enrich(main(), ADMIN, true)));
            http.release.countDown();
            http.awaitCollectorsIdle();

            Assert.assertEquals("worker-2", workerId(service.enrich(main(), ADMIN, false)));
            Assert.assertEquals(2, http.requests.get());
        } finally {
            http.release.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    public void testCollectionDeadlineIncludesJsonParsing() throws Exception {
        DeadlineHttp http = new DeadlineHttp(0);
        BlockingMapper mapper = new BlockingMapper();
        VermeerOperationsService service = new VermeerOperationsService(
                true, "http://127.0.0.1:6688", "", 30, http, mapper, Clock.systemUTC());
        ExecutorService callers = Executors.newSingleThreadExecutor();
        try {
            Future<Map<String, Object>> request = callers.submit(() -> service.enrich(main(), ADMIN, true));
            Assert.assertTrue(mapper.blocked.await(2L, TimeUnit.SECONDS));
            Map<String, Object> timedOut = request.get(1L, TimeUnit.SECONDS);
            assertCollectionUnavailable(timedOut);
            Assert.assertEquals("vermeer_upstream_timeout", ((Map<?, ?>) timedOut.get("vermeer")).get("reason"));
            mapper.release.countDown();
            http.awaitCollectorsIdle();
            assertCollectionUnavailable(service.enrich(main(), ADMIN, false));
            Assert.assertEquals(1, http.requests.get());
        } finally {
            mapper.release.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    public void testCompletionWaitsForCachePublication() throws Exception {
        BlockingHttpClient http = new BlockingHttpClient(snapshot(), 0);
        CountDownLatch publishing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicLong ticks = new AtomicLong();
        AtomicInteger reads = new AtomicInteger();
        VermeerOperationsService service = new VermeerOperationsService(
                true, "http://127.0.0.1:6688", "", 30, http, new ObjectMapper(), Clock.systemUTC(), () -> {
                    if (reads.incrementAndGet() == 1) {
                        publishing.countDown();
                        try {
                            if (!release.await(2L, TimeUnit.SECONDS)) {
                                throw new AssertionError("Cache publication was never released");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(e);
                        }
                    }
                    return ticks.get();
                }, 2500, null);
        ExecutorService callers = Executors.newSingleThreadExecutor();
        try {
            Future<Map<String, Object>> request = callers.submit(() -> service.enrich(main(), ADMIN, false));
            Assert.assertTrue(publishing.await(2L, TimeUnit.SECONDS));
            Assert.assertThrows(TimeoutException.class, () -> request.get(50L, TimeUnit.MILLISECONDS));
            release.countDown();
            Assert.assertEquals("worker-1", workerId(request.get(1L, TimeUnit.SECONDS)));

            ticks.set(TimeUnit.SECONDS.toNanos(30L) - 1);
            service.enrich(main(), ADMIN, false);
            Assert.assertEquals(1, http.requests());
            ticks.incrementAndGet();
            service.enrich(main(), ADMIN, false);
            Assert.assertEquals(2, http.requests());
        } finally {
            release.countDown();
            callers.shutdownNow();
        }
    }

    private static void assertCollectionUnavailable(Map<String, Object> overview) {
        Map<?, ?> cluster = (Map<?, ?>) overview.get("vermeer");
        Assert.assertEquals("UP", overview.get("main_cluster_status"));
        Assert.assertEquals("UNKNOWN", cluster.get("status"));
        Assert.assertEquals("UNAVAILABLE", cluster.get("availability"));
        Assert.assertTrue(Set.of("vermeer_upstream_timeout", "vermeer_upstream_unavailable")
                             .contains(cluster.get("reason")));
    }

    @Test
    public void testCachedReadDoesNotWaitForExplicitRefresh() throws Exception {
        BlockingHttpClient http = new BlockingHttpClient(snapshot(), 2);
        VermeerOperationsService service = service(http);
        service.enrich(main(), ADMIN, false);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Map<String, Object>> refresh = executor.submit(() ->
                    service.enrich(main(), ADMIN, true));
            Assert.assertTrue(http.awaitBlocked());

            Future<Map<String, Object>> cached = executor.submit(() ->
                    service.enrich(main(), ADMIN, false));
            Map<String, Object> overview = cached.get(2L, TimeUnit.SECONDS);
            Assert.assertEquals(OBSERVED_AT,
                                ((Map<?, ?>) overview.get("vermeer"))
                                           .get("observed_at"));
            Assert.assertEquals(2, http.requests());

            http.release();
            refresh.get(5L, TimeUnit.SECONDS);
        } finally {
            http.release();
            executor.shutdownNow();
        }
    }

    @Test
    public void testConcurrentRefreshesShareOneCollection() throws Exception {
        BlockingHttpClient http = new BlockingHttpClient(snapshot(), 1);
        VermeerOperationsService service = service(http);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicReference<Thread> secondThread = new AtomicReference<>();
        try {
            Future<Map<String, Object>> first = executor.submit(() ->
                    service.enrich(main(), ADMIN, true));
            Assert.assertTrue(http.awaitBlocked());
            Future<Map<String, Object>> second = executor.submit(() -> {
                secondThread.set(Thread.currentThread());
                secondStarted.countDown();
                return service.enrich(main(), ADMIN, true);
            });
            Assert.assertTrue(secondStarted.await(2L, TimeUnit.SECONDS));
            awaitPendingCollection(secondThread.get(), second);

            http.release();
            first.get(5L, TimeUnit.SECONDS);
            second.get(5L, TimeUnit.SECONDS);
            Assert.assertEquals(1, http.requests());
        } finally {
            http.release();
            executor.shutdownNow();
        }
    }

    private static void awaitPendingCollection(Thread thread, Future<?> request) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        while (!request.isDone() && System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            if ((state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) && !request.isDone()) {
                for (StackTraceElement frame : thread.getStackTrace()) {
                    if (frame.getClassName().equals(VermeerOperationsService.class.getName()) &&
                        frame.getMethodName().equals("await")) {
                        return;
                    }
                }
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1L));
        }
        Assert.fail("Second refresh did not wait for the in-flight collection");
    }

    @Test
    public void testRotatedCredentialPreventsOldRequestFromReplacingCache()
           throws Exception {
        Path token = Files.createTempFile("vermeer-rotation", ".token");
        Files.writeString(token, "old-token");
        token.toFile().deleteOnExit();
        RotatingHttpClient http = new RotatingHttpClient();
        VermeerOperationsService service = service(http, token);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Map<String, Object>> oldRequest = executor.submit(() ->
                    service.enrich(main(), ADMIN, true));
            Assert.assertTrue(http.awaitFirstBlocked());

            Files.writeString(token, "new-token");
            Map<String, Object> newest = service.enrich(main(), ADMIN, true);
            Assert.assertEquals("worker-new", workerId(newest));
            Assert.assertEquals("new-token", http.secondToken.get());

            http.releaseFirst();
            Assert.assertEquals("worker-old",
                                workerId(oldRequest.get(5L, TimeUnit.SECONDS)));
            Assert.assertEquals("old-token", http.firstToken.get());

            Map<String, Object> cached = service.enrich(main(), ADMIN, false);
            Assert.assertEquals("worker-new", workerId(cached));
            Assert.assertEquals(2, http.requests.get());
        } finally {
            http.releaseFirst();
            executor.shutdownNow();
        }
    }

    private static VermeerOperationsService service(OperationsHttpClient http)
                                                    throws Exception {
        Path token = Files.createTempFile("vermeer-concurrency", ".token");
        Files.writeString(token, "service-token");
        token.toFile().deleteOnExit();
        return service(http, token);
    }

    private static VermeerOperationsService service(OperationsHttpClient http,
                                                     Path token) {
        return new VermeerOperationsService(
                true, "http://127.0.0.1:6688", token.toString(), 30,
                http, new ObjectMapper(),
                Clock.fixed(Instant.ofEpochMilli(OBSERVED_AT), ZoneOffset.UTC));
    }

    private static Map<String, Object> main() {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("status", "UP");
        overview.put("reason", null);
        return overview;
    }

    private static String workerId(Map<String, Object> overview) {
        Map<?, ?> cluster = (Map<?, ?>) overview.get("vermeer");
        Map<?, ?> worker = (Map<?, ?>) ((List<?>) cluster.get("workers")).get(0);
        return (String) worker.get("id");
    }

    private static String snapshot() {
        return snapshot("worker-1", OBSERVED_AT);
    }

    private static String snapshot(String worker, long observedAt) {
        return "{" +
               "\"schema_version\":1," +
               "\"observed_at\":" + observedAt + "," +
               "\"status\":\"UP\"," +
               "\"registered_workers\":1," +
               "\"ready_workers\":1," +
               "\"master\":{" +
               "\"id\":\"master\",\"name\":\"master\",\"ready\":true," +
               "\"checks\":{\"http_ready\":true,\"initialized\":true," +
               "\"service_online\":true}," +
               "\"metrics\":{\"cpu_cores\":0.1,\"rss_bytes\":1024}," +
               "\"metric_status\":{\"availability\":\"AVAILABLE\"}}," +
               "\"workers\":[{" +
               "\"id\":\"" + worker + "\",\"name\":\"" + worker +
               "\",\"ready\":true," +
               "\"checks\":{\"registered\":true,\"connection_ready\":true," +
               "\"load_stream_ready\":true,\"compute_stream_ready\":true," +
               "\"superstep_stream_ready\":true}," +
               "\"metrics\":{\"cpu_cores\":0.2,\"rss_bytes\":2048}," +
               "\"metric_status\":{\"availability\":\"AVAILABLE\"}}]}";
    }

    private static final class BlockingHttpClient extends OperationsHttpClient {

        private final String response;
        private final int blockRequest;
        private final AtomicInteger requests = new AtomicInteger();
        private final CountDownLatch blocked = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingHttpClient(String response, int blockRequest) {
            super(1000, 1000, 1024 * 1024);
            this.response = response;
            this.blockRequest = blockRequest;
        }

        @Override
        public String getBearer(URI target, String token,
                                Set<String> allowedOrigins) {
            int request = this.requests.incrementAndGet();
            if (request == this.blockRequest) {
                this.blocked.countDown();
                try {
                    if (!this.release.await(5L, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Timed out waiting to release request");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            return this.response;
        }

        private boolean awaitBlocked() throws InterruptedException {
            return this.blocked.await(2L, TimeUnit.SECONDS);
        }

        private int requests() {
            return this.requests.get();
        }

        private void release() {
            this.release.countDown();
        }
    }

    private static final class DeadlineHttp extends OperationsHttpClient {

        private final int blockRequests;
        private final AtomicInteger requests = new AtomicInteger();
        private final CountDownLatch firstBlocked = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final List<Thread> workers = new CopyOnWriteArrayList<>();

        private DeadlineHttp(int blockRequests) {
            super(100, 100, 1024 * 1024);
            this.blockRequests = blockRequests;
        }

        @Override
        public String getBearer(URI target, String token, Set<String> allowedOrigins) {
            int request = this.requests.incrementAndGet();
            this.workers.add(Thread.currentThread());
            if (request <= this.blockRequests) {
                this.firstBlocked.countDown();
                boolean interrupted = false;
                // Simulate a resolver that does not respond to cancellation.
                while (this.release.getCount() != 0) {
                    try {
                        this.release.await();
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            return snapshot("worker-" + request, OBSERVED_AT + request);
        }

        private void awaitCollectorsIdle() {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
            while (System.nanoTime() < deadline) {
                boolean collecting = this.workers.stream().anyMatch(worker -> {
                    for (StackTraceElement frame : worker.getStackTrace()) {
                        if (frame.getClassName().equals(VermeerOperationsService.class.getName())) {
                            return true;
                        }
                    }
                    return false;
                });
                if (!collecting) {
                    return;
                }
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1L));
            }
            Assert.fail("Released collectors did not finish");
        }
    }

    private static final class BlockingMapper extends ObjectMapper {

        private final CountDownLatch blocked = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public JsonNode readTree(String content) throws JsonProcessingException {
            this.blocked.countDown();
            boolean interrupted = false;
            while (this.release.getCount() != 0) {
                try {
                    this.release.await();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            return super.readTree(content);
        }
    }

    private static final class RotatingHttpClient extends OperationsHttpClient {

        private final AtomicInteger requests = new AtomicInteger();
        private final AtomicReference<String> firstToken = new AtomicReference<>();
        private final AtomicReference<String> secondToken = new AtomicReference<>();
        private final CountDownLatch firstBlocked = new CountDownLatch(1);
        private final CountDownLatch releaseFirst = new CountDownLatch(1);

        private RotatingHttpClient() {
            super(1000, 1000, 1024 * 1024);
        }

        @Override
        public String getBearer(URI target, String token,
                                Set<String> allowedOrigins) {
            int request = this.requests.incrementAndGet();
            if (request == 1) {
                this.firstToken.set(token);
                this.firstBlocked.countDown();
                try {
                    if (!this.releaseFirst.await(5L, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Timed out waiting to release old request");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return snapshot("worker-old", OBSERVED_AT);
            }
            this.secondToken.set(token);
            return snapshot("worker-new", OBSERVED_AT + 1L);
        }

        private boolean awaitFirstBlocked() throws InterruptedException {
            return this.firstBlocked.await(2L, TimeUnit.SECONDS);
        }

        private void releaseFirst() {
            this.releaseFirst.countDown();
        }
    }
}
