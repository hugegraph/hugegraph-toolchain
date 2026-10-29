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
import java.util.UUID;

import jakarta.servlet.http.HttpSession;
import org.apache.hugegraph.common.Constant;
import org.apache.hugegraph.controller.BaseController;
import org.apache.hugegraph.exception.ParameterizedException;
import org.apache.hugegraph.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(Constant.API_VERSION + "auth/session")
public class SessionController extends BaseController {

    static final String ACTIVITY_SCOPE_KEY = "hubble.activity_scope";

    private static final String ORIGINAL_IDLE_KEY = "hubble.original_idle_seconds";
    private static final int MIN_IDLE_SECONDS = 15 * 60;

    @Value("${hubble.session.max-idle-seconds:0}")
    private int configuredMaxIdleSeconds;

    @GetMapping
    public Map<String, Object> get() {
        return this.settings(this.currentSession());
    }

    @PutMapping
    public Map<String, Object> update(@RequestBody Settings settings) {
        HttpSession session = this.currentSession();
        Integer seconds = settings == null ? null : settings.idleTimeoutSeconds;
        synchronized (session) {
            int max = applyPolicy(session, this.configuredMaxIdleSeconds);
            if (seconds == null || seconds < MIN_IDLE_SECONDS || seconds > max) {
                throw new ParameterizedException(
                        "Idle timeout must be between %s and %s seconds",
                        MIN_IDLE_SECONDS, max);
            }
            session.setMaxInactiveInterval(seconds);
        }
        return this.settings(session);
    }

    static void resetPreferences(HttpSession session, int configuredMax) {
        synchronized (session) {
            int original = originalIdleSeconds(session);
            session.setMaxInactiveInterval(original);
            applyPolicy(session, configuredMax);
            session.removeAttribute(ACTIVITY_SCOPE_KEY);
        }
    }

    private HttpSession currentSession() {
        HttpSession session = this.getRequest().getSession(false);
        if (session == null ||
            !hasText(session.getAttribute(Constant.USERNAME_KEY)) ||
            !hasText(session.getAttribute(Constant.TOKEN_KEY))) {
            throw new UnauthorizedException();
        }
        return session;
    }

    private static boolean hasText(Object value) {
        return value instanceof String && !((String) value).isBlank();
    }

    private static int originalIdleSeconds(HttpSession session) {
        Object original = session.getAttribute(ORIGINAL_IDLE_KEY);
        if (!(original instanceof Integer)) {
            original = session.getMaxInactiveInterval();
            session.setAttribute(ORIGINAL_IDLE_KEY, original);
        }
        return (Integer) original;
    }

    private static int applyPolicy(HttpSession session, int configuredMax) {
        int original = originalIdleSeconds(session);
        // A short deployment default stays read-only rather than being relaxed by the editor.
        int max = original > 0 && original < MIN_IDLE_SECONDS ?
                  (configuredMax > 0 ? Math.min(original, configuredMax) : original) :
                  (configuredMax > 0 ? configuredMax : Math.max(original, 0));
        int current = session.getMaxInactiveInterval();
        if (max > 0 && (current <= 0 || current > max)) {
            session.setMaxInactiveInterval(max);
        }
        return max;
    }

    private Map<String, Object> settings(HttpSession session) {
        synchronized (session) {
            int max = applyPolicy(session, this.configuredMaxIdleSeconds);
            Object current = session.getAttribute(ACTIVITY_SCOPE_KEY);
            if (!(current instanceof String)) {
                current = UUID.randomUUID().toString();
                session.setAttribute(ACTIVITY_SCOPE_KEY, current);
            }
            // An activity correlation key is not a servlet session ID or credential.
            return Map.of("idle_timeout_seconds", session.getMaxInactiveInterval(),
                          "min_idle_seconds", MIN_IDLE_SECONDS,
                          "max_idle_seconds", max,
                          "activity_scope", current);
        }
    }

    public static class Settings {

        @com.fasterxml.jackson.annotation.JsonProperty("idle_timeout_seconds")
        private Integer idleTimeoutSeconds;

        public void setIdleTimeoutSeconds(Integer seconds) {
            this.idleTimeoutSeconds = seconds;
        }
    }
}
