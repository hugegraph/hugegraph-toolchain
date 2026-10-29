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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
        try {
            Future<Map<String, Object>> first = executor.submit(() ->
                    service.enrich(main(), ADMIN, true));
            Assert.assertTrue(http.awaitBlocked());
            Future<Map<String, Object>> second = executor.submit(() -> {
                secondStarted.countDown();
                return service.enrich(main(), ADMIN, true);
            });
            Assert.assertTrue(secondStarted.await(2L, TimeUnit.SECONDS));

            http.release();
            first.get(5L, TimeUnit.SECONDS);
            second.get(5L, TimeUnit.SECONDS);
            Assert.assertEquals(1, http.requests());
        } finally {
            http.release();
            executor.shutdownNow();
        }
    }

    private static VermeerOperationsService service(OperationsHttpClient http)
                                                    throws Exception {
        Path token = Files.createTempFile("vermeer-concurrency", ".token");
        Files.writeString(token, "service-token");
        token.toFile().deleteOnExit();
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

    private static String snapshot() {
        return "{" +
               "\"schema_version\":1," +
               "\"observed_at\":" + OBSERVED_AT + "," +
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
               "\"id\":\"worker-1\",\"name\":\"worker-1\",\"ready\":true," +
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
                        throw new IllegalStateException("Timed out waiting to release request");
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
}
