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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(Constant.API_VERSION + "auth/session")
public class SessionController extends BaseController {

    static final String ACTIVITY_SCOPE_KEY = "hubble.activity_scope";

    private static final int MIN_IDLE_SECONDS = 15 * 60;
    private static final int MAX_IDLE_SECONDS = 7 * 24 * 60 * 60;

    @GetMapping
    public Map<String, Object> get() {
        return settings(this.currentSession());
    }

    @PutMapping
    public Map<String, Object> update(@RequestBody Settings settings) {
        HttpSession session = this.currentSession();
        Integer seconds = settings == null ? null : settings.idleTimeoutSeconds;
        if (seconds == null || seconds < MIN_IDLE_SECONDS ||
            seconds > MAX_IDLE_SECONDS) {
            throw new ParameterizedException(
                    "Idle timeout must be between %s and %s seconds",
                    MIN_IDLE_SECONDS, MAX_IDLE_SECONDS);
        }
        session.setMaxInactiveInterval(seconds);
        return settings(session);
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

    private static Map<String, Object> settings(HttpSession session) {
        String scope;
        synchronized (session) {
            Object current = session.getAttribute(ACTIVITY_SCOPE_KEY);
            if (!(current instanceof String)) {
                current = UUID.randomUUID().toString();
                session.setAttribute(ACTIVITY_SCOPE_KEY, current);
            }
            scope = (String) current;
        }
        // An activity correlation key is not a servlet session ID or credential.
        return Map.of("idle_timeout_seconds", session.getMaxInactiveInterval(),
                      "min_idle_seconds", MIN_IDLE_SECONDS,
                      "max_idle_seconds", MAX_IDLE_SECONDS,
                      "activity_scope", scope);
    }

    public static class Settings {

        @com.fasterxml.jackson.annotation.JsonProperty("idle_timeout_seconds")
        private Integer idleTimeoutSeconds;

        public void setIdleTimeoutSeconds(Integer seconds) {
            this.idleTimeoutSeconds = seconds;
        }
    }
}
