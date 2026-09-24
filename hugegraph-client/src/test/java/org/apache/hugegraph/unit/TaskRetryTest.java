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

package org.apache.hugegraph.unit;

import java.io.IOException;

import org.apache.hugegraph.api.task.TaskAPI;
import org.apache.hugegraph.client.RestClient;
import org.apache.hugegraph.exception.ServerException;
import org.apache.hugegraph.rest.ClientException;
import org.apache.hugegraph.rest.RestResult;
import org.apache.hugegraph.structure.Task;
import org.apache.hugegraph.testutil.Assert;
import org.junit.Test;

import org.mockito.Mockito;

public class TaskRetryTest extends BaseUnitTest {

    @Test
    public void testWaitRetriesAfterTemporaryServerFailure() {
        RestClient client = Mockito.mock(RestClient.class);
        RestResult result = Mockito.mock(RestResult.class);
        Task task = Mockito.mock(Task.class);
        Mockito.when(task.success()).thenReturn(true);
        Mockito.when(result.readObject(Task.class)).thenReturn(task);
        Mockito.when(client.get(Mockito.anyString(), Mockito.anyString()))
               .thenThrow(new ClientException("server unavailable",
                                               new IOException("reset")))
               .thenReturn(result);

        Task completed = new TaskAPI(client, "DEFAULT", "test-graph")
                         .waitUntilTaskSuccessWithRetry(1, 2);

        Assert.assertEquals(task, completed);
        Mockito.verify(client, Mockito.times(2)).get(Mockito.anyString(),
                                                      Mockito.anyString());
    }

    @Test
    public void testWaitDoesNotRetryUnauthorizedTaskRequest() {
        RestClient client = Mockito.mock(RestClient.class);
        ServerException unauthorized = new ServerException("unauthorized");
        unauthorized.status(401);
        Mockito.when(client.get(Mockito.anyString(), Mockito.anyString()))
               .thenThrow(unauthorized);

        Throwable error = Assert.assertThrows(ServerException.class,
                () -> new TaskAPI(client, "DEFAULT", "test-graph")
                        .waitUntilTaskSuccessWithRetry(1, 0));

        Assert.assertEquals(unauthorized, error);
        Mockito.verify(client, Mockito.times(1)).get(Mockito.anyString(),
                                                      Mockito.anyString());
    }
}
