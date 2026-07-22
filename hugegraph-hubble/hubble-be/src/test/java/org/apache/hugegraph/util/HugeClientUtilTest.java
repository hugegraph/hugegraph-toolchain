/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hugegraph.util;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hugegraph.api.gremlin.GremlinRequest;
import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.entity.GraphConnection;
import org.junit.Assert;
import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class HugeClientUtilTest {

    @Test
    public void testTokenClientSendsBearerToGremlin() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",
                                                                    0), 0);
        server.createContext("/versions", exchange -> respond(exchange,
                             "{\"versions\":{\"core\":\"1.8.0\"," +
                             "\"gremlin\":\"3.7.3\",\"api\":\"0.71\"}}"));
        server.createContext("/gremlin", exchange -> {
            authorization.set(exchange.getRequestHeaders()
                                      .getFirst("Authorization"));
            respond(exchange, "{\"requestId\":\"1\"," +
                     "\"status\":{\"message\":\"\",\"code\":200," +
                     "\"attributes\":{}}," +
                     "\"result\":{\"data\":[1],\"meta\":{}}}");
        });
        server.start();

        GraphConnection connection = GraphConnection.builder()
                                                    .graphSpace("DEFAULT")
                                                    .graph("hugegraph")
                                                    .host("127.0.0.1")
                                                    .port(server.getAddress()
                                                                .getPort())
                                                    .timeout(5)
                                                    .token("jwt-value")
                                                    .build();
        try (HugeClient client = HugeClientUtil.tryConnect(connection)) {
            client.gremlin().execute(new GremlinRequest("g.V().count()"));
            Assert.assertEquals("Bearer jwt-value", authorization.get());
        } finally {
            server.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, String response)
                                throws IOException {
        byte[] body = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
