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
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    UserService userService;
    @Autowired
    private HugeConfig config;

    @PostMapping("/login")
    public Object login(@RequestBody Login login) {
        // Set Expire: 1 Month
        login.expire(TOKEN_EXPIRE_SECONDS);
        LoginResult result;
        HugeClient client = null;
        if (!this.config.get(HubbleOptions.PD_ENABLED)) {
            result = this.loginStandalone(login);
        } else {
            client = this.hugeClientPoolService.createTempBasicClient(
                    login.name(), login.password());
            result = client.auth().login(login);
        }
        this.setUser(login.name());
        this.setCredentialPassword(login.password());
        this.setToken(result.token());
        if (client != null) {
            client.close();
        }
        clearRequestHugeClient();

        if (!this.config.get(HubbleOptions.PD_ENABLED)) {
            return currentUser(login.name());
        }

        client = this.authClient(PDHugeClientFactory.DEFAULT_GRAPHSPACE, null);
        UserEntity u = userService.getUser(client, login.name());
        u.setSuperadmin(userService.isSuperAdmin(client));
        client.close();

        return u;
    }

    protected LoginResult loginStandalone(Login login) {
        String endpoint = this.config.get(HubbleOptions.SERVER_URL) +
                          "/auth/login";
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(endpoint)
                                           .openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
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
            Map<?, ?> response = MAPPER.readValue(connection.getInputStream(),
                                                 Map.class);
            LoginResult result = new LoginResult();
            result.token(String.valueOf(response.get("token")));
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to login HugeGraph Server", e);
        }
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
