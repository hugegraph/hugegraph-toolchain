/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hugegraph.controller.query;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.apache.hugegraph.exception.ExternalException;
import org.apache.hugegraph.testutil.Assert;
import org.junit.Test;

public class CypherControllerTimeoutTest {

    @Test
    public void testRawAndWrappedTransportTimeoutsUseCypherGatewayTimeout() {
        List<Throwable> failures = List.of(
                new SocketTimeoutException("private host"),
                new RuntimeException(new HttpTimeoutException("private host")),
                new RuntimeException(new RuntimeException(
                        new TimeoutException("private host"))));

        for (Throwable failure : failures) {
            ExternalException error = (ExternalException) Assert.assertThrows(
                    ExternalException.class,
                    () -> CypherController.throwIfTransportTimeout(failure));
            Assert.assertEquals(504, error.status());
            Assert.assertEquals("cypher.execute.timeout", error.getMessage());
            org.junit.Assert.assertSame(failure, error.getCause());
        }
    }

    @Test
    public void testNonTimeoutFailureIsNotReclassified() {
        CypherController.throwIfTransportTimeout(
                new IllegalStateException("ordinary submission failure"));
    }
}
