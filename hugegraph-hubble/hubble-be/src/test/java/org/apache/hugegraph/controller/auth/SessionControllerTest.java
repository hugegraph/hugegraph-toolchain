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

package org.apache.hugegraph.controller.auth;

import java.util.Map;

import org.apache.hugegraph.common.Constant;
import org.apache.hugegraph.exception.ParameterizedException;
import org.apache.hugegraph.exception.UnauthorizedException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

public class SessionControllerTest {

    private MockHttpServletRequest request;
    private MockHttpSession session;
    private SessionController controller;

    @Before
    public void setup() {
        this.request = new MockHttpServletRequest();
        this.session = new MockHttpSession();
        this.session.setAttribute(Constant.USERNAME_KEY, "alice");
        this.session.setAttribute(Constant.TOKEN_KEY, "token");
        this.session.setMaxInactiveInterval(48 * 60 * 60);
        this.request.setSession(this.session);
        this.controller = new SessionController() {
            @Override
            protected jakarta.servlet.http.HttpServletRequest getRequest() {
                return SessionControllerTest.this.request;
            }
        };
    }

    @Test
    public void testReadPreservesDeploymentDefaultAndExposesBounds() {
        Map<String, Object> settings = this.controller.get();
        Assert.assertEquals(Integer.valueOf(48 * 60 * 60),
                            settings.get("idle_timeout_seconds"));
        Assert.assertEquals(Integer.valueOf(900), settings.get("min_idle_seconds"));
        Assert.assertEquals(Integer.valueOf(604800), settings.get("max_idle_seconds"));
    }

    @Test
    public void testActivityScopeIsStableAndDistinctFromTheSessionCredential() {
        Object first = this.controller.get().get("activity_scope");
        Assert.assertNotNull(first);
        Assert.assertEquals(first, this.controller.update(settings(3600)).get("activity_scope"));
        Assert.assertNotEquals(this.session.getId(), first);
        this.session.removeAttribute(SessionController.ACTIVITY_SCOPE_KEY);
        Assert.assertNotEquals(first, this.controller.get().get("activity_scope"));
    }

    @Test
    public void testNewLoginRestoresOriginalDeploymentTimeoutAndScope() {
        this.session.setMaxInactiveInterval(7200);
        Object scope = this.controller.get().get("activity_scope");
        this.controller.update(settings(3600));
        this.controller.update(settings(900));
        SessionController.resetPreferences(this.session);
        Assert.assertEquals(7200, this.session.getMaxInactiveInterval());
        Assert.assertNotEquals(scope, this.controller.get().get("activity_scope"));
    }

    @Test
    public void testUpdateOnlyChangesCurrentSession() {
        MockHttpSession other = new MockHttpSession();
        other.setMaxInactiveInterval(172800);
        this.controller.update(settings(3600));
        Assert.assertEquals(3600, this.session.getMaxInactiveInterval());
        Assert.assertEquals(172800, other.getMaxInactiveInterval());
        Assert.assertEquals("token", this.session.getAttribute(Constant.TOKEN_KEY));
    }

    @Test
    public void testInvalidAndUnlimitedTimeoutsAreRejectedWithoutMutation() {
        assertThrows(ParameterizedException.class,
                            () -> this.controller.update(null));
        for (Integer seconds : new Integer[]{null, -1, 0, 899, 604801, Integer.MAX_VALUE}) {
            assertThrows(ParameterizedException.class,
                                () -> this.controller.update(settings(seconds)));
            Assert.assertEquals(172800, this.session.getMaxInactiveInterval());
        }
        this.controller.update(settings(900));
        Assert.assertEquals(900, this.session.getMaxInactiveInterval());
        this.controller.update(settings(604800));
        Assert.assertEquals(604800, this.session.getMaxInactiveInterval());
    }

    @Test
    public void testRequiresActualAuthenticatedSession() {
        this.request.setSession(null);
        assertThrows(UnauthorizedException.class, () -> this.controller.get());
        assertThrows(UnauthorizedException.class,
                            () -> this.controller.update(settings(3600)));
        Assert.assertNull(this.request.getSession(false));
        this.request.setSession(this.session);
        this.session.setAttribute(Constant.TOKEN_KEY, " ");
        assertThrows(UnauthorizedException.class, () -> this.controller.get());
    }

    private static void assertThrows(Class<? extends RuntimeException> type,
                                     Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException error) {
            Assert.assertTrue(type.isInstance(error));
            return;
        }
        Assert.fail("Expected " + type.getSimpleName());
    }

    private static SessionController.Settings settings(Integer seconds) {
        SessionController.Settings settings = new SessionController.Settings();
        settings.setIdleTimeoutSeconds(seconds);
        return settings;
    }
}
