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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.hugegraph.api.graphs.GraphBackupsAPI;
import org.apache.hugegraph.client.RestClient;
import org.apache.hugegraph.rest.RestResult;
import org.apache.hugegraph.testutil.Assert;
import org.junit.Before;
import org.junit.Test;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class GraphBackupsAPITest extends BaseUnitTest {

    private RestClient mockClient;
    private GraphBackupsAPI backupsAPI;

    @Before
    public void setup() {
        this.mockClient = Mockito.mock(RestClient.class);
        this.backupsAPI = new GraphBackupsAPI(this.mockClient, "DEFAULT",
                                              "test-graph");
    }

    @Test
    public void testCreateSubmitsRepositoryAndKeepNumber() {
        RestResult result = Mockito.mock(RestResult.class);
        Mockito.when(result.readObject(Map.class)).thenReturn(
                Collections.singletonMap("task_id", 42));
        Mockito.when(this.mockClient.post(Mockito.anyString(), Mockito.any()))
               .thenReturn(result);

        Assert.assertEquals(42L, this.backupsAPI.create("daily", 3));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> body =
                ArgumentCaptor.forClass(Map.class);
        Mockito.verify(this.mockClient).post(
                Mockito.eq("graphspaces/DEFAULT/graphs/test-graph/backups"),
                body.capture());
        Assert.assertEquals("daily", body.getValue().get("repository"));
        Assert.assertEquals(3, body.getValue().get("keep_num"));
    }

    @Test
    public void testRestoreSubmitsTargetVersionAndConfirmation() {
        RestResult result = Mockito.mock(RestResult.class);
        Mockito.when(result.readObject(Map.class)).thenReturn(
                Collections.singletonMap("task_id", 43));
        Mockito.when(this.mockClient.post(Mockito.anyString(), Mockito.any()))
               .thenReturn(result);

        Assert.assertEquals(43L, this.backupsAPI.restore("daily", "v2", true));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> body =
                ArgumentCaptor.forClass(Map.class);
        Mockito.verify(this.mockClient).post(
                Mockito.eq("graphspaces/DEFAULT/graphs/test-graph/backups/restore"),
                body.capture());
        Assert.assertEquals("daily", body.getValue().get("repository"));
        Assert.assertEquals("v2", body.getValue().get("backup_id"));
        Assert.assertEquals(true, body.getValue().get("confirm"));
    }

    @Test
    public void testCreateAndRestoreSubmitRequestId() {
        RestResult createResult = Mockito.mock(RestResult.class);
        Mockito.when(createResult.readObject(Map.class)).thenReturn(
                Collections.singletonMap("task_id", 44));
        RestResult restoreResult = Mockito.mock(RestResult.class);
        Mockito.when(restoreResult.readObject(Map.class)).thenReturn(
                Collections.singletonMap("task_id", 45));
        Mockito.when(this.mockClient.post(Mockito.anyString(), Mockito.any()))
               .thenReturn(createResult, restoreResult);

        Assert.assertEquals(44L, this.backupsAPI.create("daily", 3,
                                                       "backup-request"));
        Assert.assertEquals(45L, this.backupsAPI.restore("daily", "v2", true,
                                                         "restore-request"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> body =
                ArgumentCaptor.forClass(Map.class);
        Mockito.verify(this.mockClient, Mockito.times(2)).post(
                Mockito.anyString(), body.capture());
        Assert.assertEquals("backup-request", body.getAllValues().get(0)
                                             .get("request_id"));
        Assert.assertEquals("restore-request", body.getAllValues().get(1)
                                              .get("request_id"));
    }

    @Test
    public void testListsAndGetsServerManagedVersions() {
        RestResult listResult = Mockito.mock(RestResult.class);
        Map<String, Object> response = new LinkedHashMap<>();
        List<Map<String, Object>> versions = Collections.singletonList(
                Collections.singletonMap("backup_id", "v1"));
        response.put("backups", versions);
        Mockito.when(listResult.readObject(Map.class)).thenReturn(response);
        Mockito.when(this.mockClient.get(
                "graphspaces/DEFAULT/graphs/test-graph/backups"))
               .thenReturn(listResult);
        Assert.assertEquals(versions, this.backupsAPI.list());

        RestResult getResult = Mockito.mock(RestResult.class);
        Map<String, Object> version = Collections.singletonMap("backup_id", "v1");
        Mockito.when(getResult.readObject(Map.class)).thenReturn(version);
        Mockito.when(this.mockClient.get(
                "graphspaces/DEFAULT/graphs/test-graph/backups", "v1"))
               .thenReturn(getResult);
        Assert.assertEquals(version, this.backupsAPI.get("v1"));
    }

    @Test
    public void testListsAndGetsVersionsFromRepository() {
        RestResult listResult = Mockito.mock(RestResult.class);
        Map<String, Object> response = new LinkedHashMap<>();
        List<Map<String, Object>> versions = Collections.singletonList(
                Collections.singletonMap("backup_id", "v1"));
        response.put("backups", versions);
        Mockito.when(listResult.readObject(Map.class)).thenReturn(response);
        Mockito.when(this.mockClient.get(
                Mockito.eq("graphspaces/DEFAULT/graphs/test-graph/backups"),
                Mockito.eq(Collections.singletonMap("repository", "daily"))))
               .thenReturn(listResult);
        Assert.assertEquals(versions, this.backupsAPI.list("daily"));

        RestResult getResult = Mockito.mock(RestResult.class);
        Map<String, Object> version = Collections.singletonMap("backup_id", "v1");
        Mockito.when(getResult.readObject(Map.class)).thenReturn(version);
        Mockito.when(this.mockClient.get(
                Mockito.eq("graphspaces/DEFAULT/graphs/test-graph/backups/v1"),
                Mockito.eq(Collections.singletonMap("repository", "daily"))))
               .thenReturn(getResult);
        Assert.assertEquals(version, this.backupsAPI.get("daily", "v1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateRejectsInvalidRepository() {
        this.backupsAPI.create("../data", 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRestoreRequiresConfirmation() {
        this.backupsAPI.restore("daily", "v1", false);
    }

    @Test
    public void testRestoreRejectsBlankBackupId() {
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            this.backupsAPI.restore("daily", "  ", true);
        });
        Mockito.verify(this.mockClient, Mockito.never()).post(
                Mockito.anyString(), Mockito.any());
    }

    @Test
    public void testRestoreWithoutBackupIdUsesLatestVersion() {
        RestResult result = Mockito.mock(RestResult.class);
        Mockito.when(result.readObject(Map.class)).thenReturn(
                Collections.singletonMap("task_id", 46));
        Mockito.when(this.mockClient.post(Mockito.anyString(), Mockito.any()))
               .thenReturn(result);

        Assert.assertEquals(46L, this.backupsAPI.restore("daily", null, true));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> body =
                ArgumentCaptor.forClass(Map.class);
        Mockito.verify(this.mockClient).post(
                Mockito.eq("graphspaces/DEFAULT/graphs/test-graph/backups/restore"),
                body.capture());
        Assert.assertFalse(body.getValue().containsKey("backup_id"));
    }

    @Test
    public void testGetRejectsBlankBackupIdBeforeRequest() {
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            this.backupsAPI.get(" ");
        });
        Mockito.verify(this.mockClient, Mockito.never()).get(
                Mockito.anyString(), Mockito.anyString());
    }

    @Test(expected = IllegalStateException.class)
    public void testCreateRejectsNonIntegralTaskId() {
        RestResult result = Mockito.mock(RestResult.class);
        Mockito.when(result.readObject(Map.class)).thenReturn(
                Collections.<String, Object>singletonMap("task_id", 42.5));
        Mockito.when(this.mockClient.post(Mockito.anyString(), Mockito.any()))
               .thenReturn(result);

        this.backupsAPI.create("daily", 1);
    }

    @Test(expected = IllegalStateException.class)
    public void testCreateRejectsNonPositiveTaskId() {
        RestResult result = Mockito.mock(RestResult.class);
        Mockito.when(result.readObject(Map.class)).thenReturn(
                Collections.<String, Object>singletonMap("task_id", 0));
        Mockito.when(this.mockClient.post(Mockito.anyString(), Mockito.any()))
               .thenReturn(result);

        this.backupsAPI.create("daily", 1);
    }

    @Test(expected = IllegalStateException.class)
    public void testListRejectsMissingBackupsField() {
        RestResult result = Mockito.mock(RestResult.class);
        Mockito.when(result.readObject(Map.class)).thenReturn(
                Collections.<String, Object>emptyMap());
        Mockito.when(this.mockClient.get(
                "graphspaces/DEFAULT/graphs/test-graph/backups"))
               .thenReturn(result);

        this.backupsAPI.list();
    }

    @Test
    public void testListAcceptsExplicitEmptyBackups() {
        RestResult result = Mockito.mock(RestResult.class);
        Mockito.when(result.readObject(Map.class)).thenReturn(
                Collections.<String, Object>singletonMap("backups",
                        Collections.emptyList()));
        Mockito.when(this.mockClient.get(
                "graphspaces/DEFAULT/graphs/test-graph/backups"))
               .thenReturn(result);

        Assert.assertTrue(this.backupsAPI.list().isEmpty());
    }
}
