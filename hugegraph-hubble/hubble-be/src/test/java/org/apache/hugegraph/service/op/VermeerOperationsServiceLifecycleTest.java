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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import jakarta.annotation.PreDestroy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import org.apache.hugegraph.testutil.Assert;

public class VermeerOperationsServiceLifecycleTest {

    private static final Set<String> ADMIN = OperationsCapabilityService.forLevel("ADMIN");

    @Test
    public void testCloseInterruptsActiveCollectorsAndDiscardsQueuedWork() throws Exception {
        BlockingHttp http = new BlockingHttp();
        AtomicReference<String> token = new AtomicReference<>("first");
        VermeerOperationsService service = service(http, token::get);
        ThreadPoolExecutor collector = (ThreadPoolExecutor) field(service, "collector");
        ExecutorService callers = Executors.newFixedThreadPool(3);
        try {
            callers.submit(() -> service.enrich(main(), ADMIN, true));
            awaitRequests(http, 1);
            token.set("second");
            callers.submit(() -> service.enrich(main(), ADMIN, true));
            awaitRequests(http, 2);
            token.set("third");
            callers.submit(() -> service.enrich(main(), ADMIN, true));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1L);
            while (collector.getQueue().size() != 1 && System.nanoTime() < deadline) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1L));
            }
            Assert.assertEquals(1, collector.getQueue().size());

            close(service);
            close(service);

            Assert.assertTrue(http.interrupted.await(1L, TimeUnit.SECONDS));
            Assert.assertTrue(collector.isTerminated());
            Assert.assertTrue(collector.getQueue().isEmpty());
            Assert.assertEquals(2, http.requests.get());
            ThreadPoolExecutor tokenExecutor = (ThreadPoolExecutor) field(field(service, "tokenReader"), "executor");
            Assert.assertTrue(tokenExecutor.isTerminated());
        } finally {
            http.release.countDown();
            callers.shutdownNow();
            collector.shutdownNow();
        }
    }

    @Test
    public void testCloseInterruptsActiveTokenReaderAndIsIdempotent() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        VermeerOperationsService service = service(new BlockingHttp(), () -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
            return "token";
        });
        ExecutorService callers = Executors.newSingleThreadExecutor();
        try {
            Future<Map<String, Object>> request = callers.submit(() -> service.enrich(main(), ADMIN, false));
            Assert.assertTrue(started.await(1L, TimeUnit.SECONDS));
            close(service);
            close(service);
            Assert.assertTrue(interrupted.await(1L, TimeUnit.SECONDS));
            Assert.assertEquals("vermeer_credentials_unavailable",
                                ((Map<?, ?>) request.get(1L, TimeUnit.SECONDS).get("vermeer")).get("reason"));
            ThreadPoolExecutor reader = (ThreadPoolExecutor) field(field(service, "tokenReader"), "executor");
            Assert.assertTrue(reader.isTerminated());
            Assert.assertTrue(((ThreadPoolExecutor) field(service, "collector")).isTerminated());
        } finally {
            release.countDown();
            callers.shutdownNow();
        }
    }

    private static VermeerOperationsService service(BlockingHttp http, VermeerTokenReader.Reader reader) {
        return new VermeerOperationsService(true, "http://127.0.0.1:6688", "controlled-token", 30,
                                            http, new ObjectMapper(), Clock.systemUTC(), System::nanoTime,
                                            2000, reader);
    }

    private static void close(VermeerOperationsService service) throws Exception {
        Method close = VermeerOperationsService.class.getMethod("close");
        Assert.assertNotNull(close.getAnnotation(PreDestroy.class));
        close.invoke(service);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void awaitRequests(BlockingHttp http, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1L);
        while (http.requests.get() < expected && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1L));
        }
        Assert.assertEquals(expected, http.requests.get());
    }

    private static Map<String, Object> main() {
        return new LinkedHashMap<>(Map.of("status", "UP"));
    }

    private static final class BlockingHttp extends OperationsHttpClient {

        private final AtomicInteger requests = new AtomicInteger();
        private final CountDownLatch interrupted = new CountDownLatch(2);
        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingHttp() {
            super(1000, 1000, 65536);
        }

        @Override
        public String getBearer(URI target, String token, Set<String> allowedOrigins) {
            this.requests.incrementAndGet();
            try {
                this.release.await();
            } catch (InterruptedException e) {
                this.interrupted.countDown();
                Thread.currentThread().interrupt();
                throw new UpstreamRequestException("upstream_interrupted");
            }
            return "{}";
        }
    }
}
