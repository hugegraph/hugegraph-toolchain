/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.
 * Licensed under the Apache License, Version 2.0.
 */

package org.apache.hugegraph.unit;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.hugegraph.exception.LoginThrottledException;
import org.apache.hugegraph.service.auth.LoginAttemptGuard;
import org.junit.Assert;
import org.junit.Test;

public class LoginAttemptGuardTest {

    @Test
    public void testExponentialBackoffAndReset() {
        AtomicLong now = new AtomicLong(1000L);
        LoginAttemptGuard guard = new LoginAttemptGuard(4, 5000L, 600_000L,
                                                        10_000L, now::get);
        String username = "admin";
        String address = "127.0.0.1";

        for (int i = 0; i < 3; i++) {
            guard.recordFailure(username, address);
            guard.checkAllowed(username, address);
        }

        guard.recordFailure(username, address);
        assertBlocked(guard, username, address);

        now.addAndGet(5000L);
        guard.checkAllowed(username, address);
        guard.recordFailure(username, address);
        assertBlocked(guard, username, address);

        now.addAndGet(9999L);
        assertBlocked(guard, username, address);
        now.incrementAndGet();
        guard.checkAllowed(username, address);

        guard.reset(username, address);
        guard.checkAllowed(username, address);
    }

    @Test
    public void testBackoffStopsAtConfiguredMaximum() {
        AtomicLong now = new AtomicLong();
        LoginAttemptGuard guard = new LoginAttemptGuard(1, 5L, 20L, 100L,
                                                        now::get);

        guard.recordFailure("admin", "127.0.0.1");
        now.addAndGet(5L);
        guard.recordFailure("admin", "127.0.0.1");
        now.addAndGet(10L);
        guard.recordFailure("admin", "127.0.0.1");
        now.addAndGet(20L);
        guard.recordFailure("admin", "127.0.0.1");

        now.addAndGet(19L);
        assertBlocked(guard, "admin", "127.0.0.1");
        now.incrementAndGet();
        guard.checkAllowed("admin", "127.0.0.1");
    }

    @Test
    public void testConcurrentAttemptIsRejectedWithoutWaiting() {
        LoginAttemptGuard guard = new LoginAttemptGuard(4, 5000L, 600_000L,
                                                        10_000L,
                                                        System::currentTimeMillis);

        guard.checkAllowed("admin", "127.0.0.1");
        assertBlocked(guard, "admin", "127.0.0.1");

        guard.release("admin", "127.0.0.1");
        guard.checkAllowed("admin", "127.0.0.1");
    }

    private static void assertBlocked(LoginAttemptGuard guard, String username,
                                      String address) {
        try {
            guard.checkAllowed(username, address);
            Assert.fail("Expected login attempt to be throttled");
        } catch (LoginThrottledException e) {
            Assert.assertTrue(e.getRetrySeconds() > 0L);
        }
    }
}
