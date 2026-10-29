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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Bounds callers' wait even when opening or reading a mounted file never returns.
 * A timed-out read keeps its sole daemon occupied until the actual I/O finishes.
 */
final class VermeerTokenReader {

    @FunctionalInterface
    interface Reader {
        String read() throws IOException;
    }

    record Read(long sequence, String token) {
    }

    private final Reader reader;
    private final ThreadPoolExecutor executor;
    private Attempt inFlight;
    private long sequence;

    VermeerTokenReader(Reader reader) {
        this.reader = reader;
        this.executor = new ThreadPoolExecutor(
                1, 1, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), task -> {
                    Thread thread = new Thread(task, "vermeer-credential-reader");
                    thread.setDaemon(true);
                    return thread;
                });
        this.executor.allowCoreThreadTimeOut(true);
    }

    Read read(int timeoutMillis) {
        Attempt attempt;
        synchronized (this) {
            // Completed results are never a token cache: re-read to detect rotation.
            if (this.inFlight == null || this.inFlight.result.isDone()) {
                attempt = new Attempt(++this.sequence);
                this.inFlight = attempt;
                this.executor.execute(() -> {
                    try {
                        attempt.result.complete(this.reader.read());
                    } catch (IOException | RuntimeException e) {
                        attempt.result.completeExceptionally(e);
                    }
                });
            } else {
                attempt = this.inFlight;
            }
            if (attempt.expired) {
                return new Read(attempt.sequence, null);
            }
        }
        try {
            String token = attempt.result.get(timeoutMillis, TimeUnit.MILLISECONDS);
            synchronized (this) {
                return new Read(attempt.sequence, attempt.expired ? null : token);
            }
        } catch (TimeoutException e) {
            synchronized (this) {
                // Never reuse a late result or cancel/reopen an uninterruptible read.
                attempt.expired = true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // The response deliberately contains neither the token nor file details.
        }
        return new Read(attempt.sequence, null);
    }

    private static final class Attempt {

        private final long sequence;
        private final CompletableFuture<String> result = new CompletableFuture<>();
        private boolean expired;

        private Attempt(long sequence) {
            this.sequence = sequence;
        }
    }
}
