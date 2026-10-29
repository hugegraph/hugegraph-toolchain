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
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import org.apache.hugegraph.testutil.Assert;

public class OperationsHttpClientCallTimeoutTest {

    @Test(timeout = 5000L)
    public void testOrdinaryGetRetainsIdleReadTimeoutSemantics()
           throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            try {
                exchange.sendResponseHeaders(200, 10L);
                for (int index = 0; index < 10; index++) {
                    exchange.getResponseBody().write('x');
                    exchange.getResponseBody().flush();
                    Thread.sleep(100L);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        OperationsHttpClient client = new OperationsHttpClient(100, 500, 64);
        try {
            String body = client.get(uri(server), null, null,
                                     Collections.emptySet());
            Assert.assertEquals("xxxxxxxxxx", body);
        } finally {
            server.stop(0);
        }
    }

    private static URI uri(HttpServer server) {
        return URI.create("http://127.0.0.1:" +
                          server.getAddress().getPort() + '/');
    }
}
