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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import org.apache.hugegraph.entity.GraphConnection;
import org.apache.hugegraph.entity.load.EdgeMapping;
import org.apache.hugegraph.entity.load.FileMapping;
import org.apache.hugegraph.entity.load.NullValues;
import org.apache.hugegraph.entity.load.VertexMapping;
import org.apache.hugegraph.entity.schema.EdgeLabelEntity;
import org.apache.hugegraph.entity.schema.VertexLabelEntity;
import org.apache.hugegraph.service.load.LoadTaskService;
import org.apache.hugegraph.service.schema.EdgeLabelService;
import org.apache.hugegraph.service.schema.VertexLabelService;
import org.apache.hugegraph.structure.constant.IdStrategy;
import org.apache.hugegraph.testutil.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class LoadTaskServiceTest {

    @Test
    public void testCustomizedVertexIdUsesScalarIdByDefault() throws Exception {
        LoadTaskService service = new LoadTaskService();
        VertexLabelService vlService = Mockito.mock(VertexLabelService.class);
        Mockito.when(vlService.get("person", 1))
               .thenReturn(VertexLabelEntity.builder()
                                            .name("person")
                                            .idStrategy(IdStrategy.CUSTOMIZE_STRING)
                                            .build());
        this.setField(service, "vlService", vlService);

        VertexMapping vertexMapping = VertexMapping.builder()
                                                   .idFields(Collections.singletonList("name"))
                                                   .build();
        vertexMapping.setLabel("person");
        vertexMapping.setNullValues(new NullValues(Collections.emptySet(),
                                                  Collections.emptySet()));
        FileMapping fileMapping = new FileMapping();
        fileMapping.setVertexMappings(Collections.singleton(vertexMapping));
        GraphConnection connection = GraphConnection.builder().id(1).build();

        List<?> mappings = this.buildVertexMappings(service, connection, fileMapping);
        Object loaderMapping = mappings.get(0);

        Method unfold = loaderMapping.getClass().getMethod("unfold");
        Assert.assertFalse((Boolean) unfold.invoke(loaderMapping));
    }

    @Test
    public void testCustomizedEdgeEndpointIdsUseScalarIdByDefault()
           throws Exception {
        LoadTaskService service = new LoadTaskService();
        VertexLabelService vlService = Mockito.mock(VertexLabelService.class);
        Mockito.when(vlService.get("person", 1))
               .thenReturn(VertexLabelEntity.builder()
                                            .name("person")
                                            .idStrategy(IdStrategy.CUSTOMIZE_STRING)
                                            .build());
        EdgeLabelService elService = Mockito.mock(EdgeLabelService.class);
        Mockito.when(elService.get("knows", 1))
               .thenReturn(EdgeLabelEntity.builder()
                                          .name("knows")
                                          .sourceLabel("person")
                                          .targetLabel("person")
                                          .build());
        this.setField(service, "vlService", vlService);
        this.setField(service, "elService", elService);

        EdgeMapping edgeMapping = EdgeMapping.builder()
                                             .sourceFields(Collections.singletonList("source"))
                                             .targetFields(Collections.singletonList("target"))
                                             .build();
        edgeMapping.setLabel("knows");
        edgeMapping.setNullValues(new NullValues(Collections.emptySet(),
                                                Collections.emptySet()));
        FileMapping fileMapping = new FileMapping();
        fileMapping.setEdgeMappings(Collections.singleton(edgeMapping));
        GraphConnection connection = GraphConnection.builder().id(1).build();

        List<?> mappings = this.buildEdgeMappings(service, connection, fileMapping);
        Object loaderMapping = mappings.get(0);

        Method unfoldSource = loaderMapping.getClass().getMethod("unfoldSource");
        Method unfoldTarget = loaderMapping.getClass().getMethod("unfoldTarget");
        Assert.assertFalse((Boolean) unfoldSource.invoke(loaderMapping));
        Assert.assertFalse((Boolean) unfoldTarget.invoke(loaderMapping));
    }

    @SuppressWarnings("unchecked")
    private List<?> buildVertexMappings(LoadTaskService service,
                                        GraphConnection connection,
                                        FileMapping fileMapping) throws Exception {
        Method method = LoadTaskService.class.getDeclaredMethod("buildVertexMappings",
                                                              GraphConnection.class,
                                                              FileMapping.class);
        method.setAccessible(true);
        return (List<?>) method.invoke(service, connection, fileMapping);
    }

    @SuppressWarnings("unchecked")
    private List<?> buildEdgeMappings(LoadTaskService service,
                                      GraphConnection connection,
                                      FileMapping fileMapping) throws Exception {
        Method method = LoadTaskService.class.getDeclaredMethod("buildEdgeMappings",
                                                              GraphConnection.class,
                                                              FileMapping.class);
        method.setAccessible(true);
        return (List<?>) method.invoke(service, connection, fileMapping);
    }

    private void setField(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
}
