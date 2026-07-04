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

package org.apache.hugegraph.unit;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.apache.hugegraph.common.Constant;
import org.apache.hugegraph.common.Response;
import org.apache.hugegraph.config.HugeConfig;
import org.apache.hugegraph.config.IngestionProxyServlet;
import org.apache.hugegraph.controller.BaseController;
import org.apache.hugegraph.controller.auth.LoginController;
import org.apache.hugegraph.exception.UnauthorizedException;
import org.apache.hugegraph.handler.ExceptionAdvisor;
import org.apache.hugegraph.handler.LoginInterceptor;
import org.apache.hugegraph.options.HubbleOptions;
import org.apache.hugegraph.structure.auth.Login;
import org.apache.hugegraph.structure.auth.LoginResult;

public class AuthSecurityTest {

    @After
    public void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void testLoginInterceptorRejectsMissingSessionAuth() {
        LoginInterceptor interceptor = new LoginInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest(
                                            "GET", "/api/v1.3/auth/status");

        assertThrows(UnauthorizedException.class, () -> {
            interceptor.preHandle(request, new MockHttpServletResponse(), null);
        });
    }

    @Test
    public void testLoginInterceptorRequiresTokenAndUsername() {
        LoginInterceptor interceptor = new LoginInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest(
                                            "GET", "/api/v1.3/auth/status");
        request.getSession().setAttribute(Constant.TOKEN_KEY, "token");

        assertThrows(UnauthorizedException.class, () -> {
            interceptor.preHandle(request, new MockHttpServletResponse(), null);
        });

        request.getSession().setAttribute(Constant.USERNAME_KEY, "admin");
        Assert.assertTrue(interceptor.preHandle(request,
                                               new MockHttpServletResponse(),
                                               null));
    }

    @Test
    public void testLoginInterceptorRejectsBlankSessionAuth() {
        LoginInterceptor interceptor = new LoginInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest(
                                            "GET", "/api/v1.3/auth/status");
        request.getSession().setAttribute(Constant.TOKEN_KEY, " ");
        request.getSession().setAttribute(Constant.USERNAME_KEY, "admin");

        assertThrows(UnauthorizedException.class, () -> {
            interceptor.preHandle(request, new MockHttpServletResponse(), null);
        });

        request.getSession().setAttribute(Constant.TOKEN_KEY, "token");
        request.getSession().setAttribute(Constant.USERNAME_KEY, " ");

        assertThrows(UnauthorizedException.class, () -> {
            interceptor.preHandle(request, new MockHttpServletResponse(), null);
        });
    }

    @Test
    public void testLoginInterceptorAllowsOptionsPreflight() {
        LoginInterceptor interceptor = new LoginInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest(
                                            "OPTIONS", "/api/v1.3/auth/status");

        Assert.assertTrue(interceptor.preHandle(request,
                                               new MockHttpServletResponse(),
                                               null));
    }

    @Test
    public void testUnauthorizedExceptionUsesHttp401() throws Exception {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
        ExceptionAdvisor advisor = new ExceptionAdvisor();
        Response response = advisor.exceptionHandler(new UnauthorizedException());

        Assert.assertEquals(Constant.STATUS_UNAUTHORIZED, response.getStatus());

        Method method = ExceptionAdvisor.class.getMethod("exceptionHandler",
                                                        UnauthorizedException.class);
        ResponseStatus status = method.getAnnotation(ResponseStatus.class);
        Assert.assertEquals(HttpStatus.UNAUTHORIZED, status.value());
    }

    @Test
    public void testIngestionProxyRejectsMissingSessionWithHttp401()
           throws Exception {
        TestIngestionProxyServlet servlet = new TestIngestionProxyServlet();
        MockHttpServletRequest request = new MockHttpServletRequest(
                                            "GET", "/ingest/tasks");

        HttpResponse response = servlet.execute(request);

        Assert.assertEquals(Constant.STATUS_UNAUTHORIZED,
                            response.getStatusLine().getStatusCode());
        Assert.assertEquals("{\"status\": 401}",
                            EntityUtils.toString(response.getEntity()));
    }

    @Test
    public void testIngestionProxyRejectsUsernameWithoutToken()
           throws Exception {
        TestIngestionProxyServlet servlet = new TestIngestionProxyServlet();
        MockHttpServletRequest request = new MockHttpServletRequest(
                                            "GET", "/ingest/tasks");
        request.getSession().setAttribute(Constant.USERNAME_KEY, "admin");

        HttpResponse response = servlet.execute(request);

        Assert.assertEquals(Constant.STATUS_UNAUTHORIZED,
                            response.getStatusLine().getStatusCode());
        Assert.assertEquals("{\"status\": 401}",
                            EntityUtils.toString(response.getEntity()));
    }

    @Test
    public void testCredentialPasswordIsShortLivedAndNotLegacySessionKey() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));
        TestBaseController controller = new TestBaseController();

        controller.savePassword("pa");

        Assert.assertEquals("pa", controller.readPassword());
        Assert.assertNull(request.getSession().getAttribute("password"));

        request.getSession().setAttribute(Constant.CREDENTIAL_EXPIRES_AT_KEY,
                                          System.currentTimeMillis() - 1L);
        Assert.assertNull(controller.readPassword());
        Assert.assertNull(request.getSession().getAttribute(
                          Constant.CREDENTIAL_PASSWORD_KEY));
    }

    @Test
    public void testClearAuthSessionClearsCredentialState() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));
        TestBaseController controller = new TestBaseController();

        request.getSession().setAttribute(Constant.TOKEN_KEY, "token");
        request.getSession().setAttribute(Constant.USERNAME_KEY, "admin");
        controller.savePassword("pa");

        controller.clearAuth();

        Assert.assertNull(request.getSession().getAttribute(Constant.TOKEN_KEY));
        Assert.assertNull(request.getSession().getAttribute(Constant.USERNAME_KEY));
        Assert.assertNull(request.getSession().getAttribute(
                          Constant.CREDENTIAL_PASSWORD_KEY));
        Assert.assertNull(request.getSession().getAttribute(
                          Constant.CREDENTIAL_EXPIRES_AT_KEY));
    }

    @Test
    public void testStandaloneLoginUsesServerPr3008Payload()
           throws Exception {
        AtomicReference<String> bodyRef = new AtomicReference<>();
        AtomicReference<String> authRef = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",
                                                                    0),
                                              0);
        server.createContext("/auth/login", exchange -> {
            authRef.set(exchange.getRequestHeaders()
                                .getFirst("Authorization"));
            bodyRef.set(new String(exchange.getRequestBody().readAllBytes(),
                                   StandardCharsets.UTF_8));
            byte[] body = "{\"token\":\"server-token\"}".getBytes(
                          StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(Constant.STATUS_OK, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            TestLoginController controller = new TestLoginController();
            HugeConfig config = Mockito.mock(HugeConfig.class);
            Mockito.when(config.get(HubbleOptions.SERVER_URL))
                   .thenReturn("http://127.0.0.1:" +
                               server.getAddress().getPort());
            setField(controller, "config", config);
            Login login = new Login();
            login.name("admin");
            login.password("pa");

            LoginResult result = controller.standalone(login);

            Assert.assertEquals("server-token", result.token());
            Assert.assertTrue(authRef.get().startsWith("Basic "));
            Assert.assertTrue(bodyRef.get().contains("\"user_name\":\"admin\""));
            Assert.assertTrue(bodyRef.get().contains("\"user_password\":\"pa\""));
            Assert.assertTrue(bodyRef.get().contains("\"token_expire\":"));
        } finally {
            server.stop(0);
        }
    }

    private static class TestIngestionProxyServlet
                   extends IngestionProxyServlet {

        public HttpResponse execute(MockHttpServletRequest request)
               throws IOException {
            return this.doExecute(request, new MockHttpServletResponse(),
                                  new HttpGet("/ingest/tasks"));
        }
    }

    private static class TestBaseController extends BaseController {

        public void savePassword(String password) {
            this.setCredentialPassword(password);
        }

        public String readPassword() {
            return this.getCredentialPassword();
        }

        public void clearAuth() {
            this.clearAuthSession();
        }
    }

    private static class TestLoginController extends LoginController {

        public LoginResult standalone(Login login) {
            return this.loginStandalone(login);
        }
    }

    private static void setField(Object object, String name, Object value)
                                 throws Exception {
        Class<?> type = object.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(object, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void assertThrows(Class<? extends Throwable> expected,
                                     ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) {
                return;
            }
            throw new AssertionError("Unexpected exception type", actual);
        }
        throw new AssertionError("Expected exception: " + expected.getName());
    }

    private interface ThrowingRunnable {

        void run() throws Throwable;
    }
}
