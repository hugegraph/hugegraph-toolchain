/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
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
