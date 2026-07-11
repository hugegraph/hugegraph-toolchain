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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hugegraph.config.HugeConfig;
import org.apache.hugegraph.entity.auth.UserEntity;
import com.google.common.collect.ImmutableMap;
import org.apache.hugegraph.driver.factory.PDHugeClientFactory;
import org.apache.hugegraph.options.HubbleOptions;
import org.apache.hugegraph.service.auth.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.structure.auth.Login;
import org.apache.hugegraph.common.Constant;
import org.apache.hugegraph.controller.BaseController;
import org.apache.hugegraph.structure.auth.LoginResult;

@RestController
@RequestMapping(Constant.API_VERSION + "auth")
public class LoginController extends BaseController {

    private static final int TOKEN_EXPIRE_SECONDS = 60 * 60 * 24 * 30;
    private static final int CONNECT_TIMEOUT_MILLIS = 5000;
    private static final int READ_TIMEOUT_MILLIS = 30000;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    UserService userService;
    @Autowired
    private HugeConfig config;

    @PostMapping("/login")
    public Object login(@RequestBody Login login) {
        // Set Expire: 1 Month
        login.expire(TOKEN_EXPIRE_SECONDS);
        try {
            boolean pdEnabled = this.config.get(HubbleOptions.PD_ENABLED);
            LoginResult result;
            Object user;
            if (!pdEnabled) {
                result = this.loginStandalone(login);
                user = currentUser(login.name());
            } else {
                try (HugeClient client =
                             this.createLoginClient(login.name(),
                                                    login.password())) {
                    result = client.auth().login(login);
                }
                try (HugeClient client =
                             this.createLoginTokenClient(result.token())) {
                    client.assignGraph(PDHugeClientFactory.DEFAULT_GRAPHSPACE,
                                       null);
                    UserEntity entity = this.userService.getUser(client,
                                                                 login.name());
                    entity.setSuperadmin(
                            this.userService.isSuperAdmin(client));
                    user = entity;
                }
            }

            this.getRequest().getSession();
            this.getRequest().changeSessionId();
            this.setUser(login.name());
            this.setCredentialPassword(login.password());
            this.setToken(result.token());
            return user;
        } catch (Throwable e) {
            this.clearAuthSession();
            throw e;
        } finally {
            this.clearRequestHugeClient();
        }
    }

    protected LoginResult loginStandalone(Login login) {
        String endpoint = this.config.get(HubbleOptions.SERVER_URL) +
                          "/auth/login";
        HttpURLConnection connection = null;
        try {
            connection = this.openConnection(new URL(endpoint));
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestProperty("Content-Type",
                                          "application/json;charset=UTF-8");
            String auth = login.name() + ":" + login.password();
            String basic = Base64.getEncoder().encodeToString(
                           auth.getBytes(StandardCharsets.UTF_8));
            connection.setRequestProperty("Authorization", "Basic " + basic);
            Map<String, Object> body = ImmutableMap.of(
                    "user_name", login.name(),
                    "user_password", login.password(),
                    "token_expire", TOKEN_EXPIRE_SECONDS
            );
            try (OutputStream output = connection.getOutputStream()) {
                output.write(MAPPER.writeValueAsBytes(body));
            }
            if (connection.getResponseCode() >= 400) {
                throw new IOException("Standalone login failed: HTTP " +
                                      connection.getResponseCode());
            }
            Map<?, ?> response;
            try (InputStream input = connection.getInputStream()) {
                response = MAPPER.readValue(input, Map.class);
            }
            LoginResult result = new LoginResult();
            result.token(String.valueOf(response.get("token")));
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to login HugeGraph Server", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    protected HttpURLConnection openConnection(URL endpoint)
                                               throws IOException {
        return (HttpURLConnection) endpoint.openConnection();
    }

    protected HugeClient createLoginClient(String username, String password) {
        return this.hugeClientPoolService.createTempBasicClient(username,
                                                                password);
    }

    protected HugeClient createLoginTokenClient(String token) {
        return this.hugeClientPoolService.createTempTokenClient(token);
    }

    private static UserEntity currentUser(String username) {
        UserEntity user = new UserEntity();
        user.setId(username);
        user.setName(username);
        user.setNickname(username);
        user.setAdminSpaces(Collections.emptyList());
        user.setResSpaces(Collections.emptyList());
        user.setSpacenum(0);
        user.setSuperadmin(false);
        return user;
    }

    @GetMapping("/status")
    public Object status() {

        HugeClient client = authClient(null, null);

        String level = userService.userLevel(client);

        return ImmutableMap.of("level", level);
    }

    @GetMapping("/logout")
    public void logout() {
        this.clearAuthSession();
    }
}
