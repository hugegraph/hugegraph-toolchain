/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import org.apache.hugegraph.driver.GraphsManager;
import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.exception.ExternalException;
import org.apache.hugegraph.service.graphs.GraphsService;

public class GraphsServiceDefaultTest {

    private HugeClient client;
    private GraphsManager graphs;
    private GraphsService service;

    @Before
    public void setup() {
        this.client = Mockito.mock(HugeClient.class);
        this.graphs = Mockito.mock(GraphsManager.class);
        Mockito.when(this.client.graphs()).thenReturn(this.graphs);
        this.service = new GraphsService();
    }

    @Test
    public void testSetDefaultReplacesAllPreviousDefaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("default_graph", Arrays.asList("old_a", "old_b"));
        Mockito.when(this.graphs.getDefault()).thenReturn(defaults);

        this.service.setDefault(this.client, "target");

        InOrder order = Mockito.inOrder(this.graphs);
        order.verify(this.graphs).getDefault();
        order.verify(this.graphs).setDefault("target");
        order.verify(this.graphs).unSetDefault("old_a");
        order.verify(this.graphs).unSetDefault("old_b");
    }

    @Test
    public void testClearGraphUsesExplicitDestructiveConfirmation() {
        Mockito.when(this.graphs.getDefault()).thenReturn(
                Collections.singletonMap("default_graph",
                                         Collections.singletonList("default")));

        this.service.clearGraph(this.client, "graph_a");

        InOrder order = Mockito.inOrder(this.graphs);
        order.verify(this.graphs).getDefault();
        order.verify(this.graphs).clearGraph(
              "graph_a", "I'm sure to delete all data");
    }

    @Test
    public void testClearGraphRejectsDefaultGraph() {
        Mockito.when(this.graphs.getDefault()).thenReturn(
                Collections.singletonMap("default_graph",
                                         Arrays.asList("other", "graph_a")));

        try {
            this.service.clearGraph(this.client, "graph_a");
            Assert.fail("Expected default graph rejection");
        } catch (ExternalException ignored) {
            // Expected: default graphs must not be cleared.
        }

        Mockito.verify(this.graphs).getDefault();
        Mockito.verify(this.graphs, Mockito.never())
               .clearGraph(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testSetDefaultIsNoopWhenTargetIsOnlyDefault() {
        Mockito.when(this.graphs.getDefault()).thenReturn(
                Collections.singletonMap("default_graph",
                                         Collections.singletonList("target")));

        this.service.setDefault(this.client, "target");

        Mockito.verify(this.graphs).getDefault();
        Mockito.verify(this.graphs, Mockito.never()).unSetDefault(Mockito.anyString());
        Mockito.verify(this.graphs, Mockito.never()).setDefault(Mockito.anyString());
    }

    @Test
    public void testSetDefaultKeepsTargetAndDeduplicatesOldDefaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("default_graph", Arrays.asList("old", "target", "old"));
        Mockito.when(this.graphs.getDefault()).thenReturn(defaults);

        this.service.setDefault(this.client, "target");

        Mockito.verify(this.graphs, Mockito.never()).setDefault(Mockito.anyString());
        Mockito.verify(this.graphs).unSetDefault("old");
        Mockito.verify(this.graphs, Mockito.never()).unSetDefault("target");
    }

    @Test
    public void testSetFailureDoesNotClearExistingDefault() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("default_graph", Collections.singletonList("old"));
        Mockito.when(this.graphs.getDefault()).thenReturn(defaults);
        Mockito.when(this.graphs.setDefault("target"))
               .thenThrow(new IllegalStateException("set failed"));

        try {
            this.service.setDefault(this.client, "target");
            Assert.fail("Expected set failure");
        } catch (IllegalStateException ignored) {
            // Expected: the failure propagates without clearing the old default.
        }

        Mockito.verify(this.graphs, Mockito.never()).unSetDefault(Mockito.anyString());
    }
}
