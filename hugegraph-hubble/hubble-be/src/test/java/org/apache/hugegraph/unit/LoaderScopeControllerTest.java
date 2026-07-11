/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.
 * Licensed under the Apache License, Version 2.0.
 */
package org.apache.hugegraph.unit;

import java.util.Collections;
import java.util.Arrays;

import org.apache.hugegraph.controller.load.FileMappingController;
import org.apache.hugegraph.controller.load.JobManagerController;
import org.apache.hugegraph.controller.load.LoadTaskController;
import org.apache.hugegraph.entity.load.FileMapping;
import org.apache.hugegraph.entity.load.JobManager;
import org.apache.hugegraph.entity.load.LoadTask;
import org.apache.hugegraph.service.load.FileMappingService;
import org.apache.hugegraph.service.load.JobManagerService;
import org.apache.hugegraph.service.load.LoadTaskService;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

public class LoaderScopeControllerTest {

    @Test
    public void testJobLookupUsesGraphScope() {
        JobManagerService service = Mockito.mock(JobManagerService.class);
        JobManager expected = new JobManager();
        Mockito.when(service.get("space-a", "graph-a", 7))
               .thenReturn(expected);

        JobManagerController controller = new JobManagerController(service);

        org.junit.Assert.assertSame(expected,
                                    controller.get("space-a", "graph-a", 7));
    }

    @Test
    public void testFileClearUsesNestedScope() {
        FileMappingService service = Mockito.mock(FileMappingService.class);
        FileMapping mapping = FileMapping.builder().id(11).build();
        Mockito.when(service.listByJob("space-a", "graph-a", 7))
               .thenReturn(Collections.singletonList(mapping));
        FileMappingController controller = new FileMappingController();
        ReflectionTestUtils.setField(controller, "service", service);

        controller.clear("space-a", "graph-a", 7);

        Mockito.verify(service).remove(11);
        Mockito.verify(service, Mockito.never()).listAll();
    }

    @Test
    public void testLoadTaskLookupUsesNestedScope() {
        LoadTaskService service = Mockito.mock(LoadTaskService.class);
        LoadTask expected = new LoadTask();
        Mockito.when(service.get("space-a", "graph-a", 7, 13))
               .thenReturn(expected);
        LoadTaskController controller = new LoadTaskController(service);

        org.junit.Assert.assertSame(expected,
                                    controller.get("space-a", "graph-a", 7,
                                                   13));
    }

    @Test
    public void testLoadTaskBatchLookupUsesNestedScope() {
        LoadTaskService service = Mockito.mock(LoadTaskService.class);
        LoadTaskController controller = new LoadTaskController(service);

        controller.list("space-a", "graph-a", 7, Arrays.asList(13, 14));

        Mockito.verify(service).list("space-a", "graph-a", 7,
                                     Arrays.asList(13, 14));
    }
}
