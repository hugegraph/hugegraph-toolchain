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

package org.apache.hugegraph.unit;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import org.apache.hugegraph.client.RestClient;
import org.apache.hugegraph.driver.GraphManager;
import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.entity.graph.VertexEntity;
import org.apache.hugegraph.entity.schema.VertexLabelEntity;
import org.apache.hugegraph.rest.RestResult;
import org.apache.hugegraph.service.graph.GraphService;
import org.apache.hugegraph.service.schema.VertexLabelService;
import org.apache.hugegraph.structure.constant.IdStrategy;
import org.apache.hugegraph.structure.graph.Vertex;

public class GraphServiceUpdateIdTest {

    @Test
    public void testCustomNumberUpdatePreservesNumericSdkPath() throws Exception {
        for (long id : new long[]{9000001L, Long.MAX_VALUE}) {
            this.assertUpdate(IdStrategy.CUSTOMIZE_NUMBER, Long.toString(id),
                              Long.toString(id), id);
        }
    }

    @Test
    public void testNumericLookingStringUpdatePreservesQuotedSdkPath() throws Exception {
        this.assertUpdate(IdStrategy.CUSTOMIZE_STRING, "9000001",
                          "\"9000001\"", "9000001");
        this.assertUpdate(IdStrategy.CUSTOMIZE_STRING, "001", "\"001\"", "001");
    }

    @Test
    public void testCustomUuidUpdatePreservesUuidSdkPath() throws Exception {
        String id = "835e1153-62c4-407c-832a-058419744e2d";
        this.assertUpdate(IdStrategy.CUSTOMIZE_UUID, id, "U\"" + id + "\"", UUID.fromString(id));
    }

    @Test
    public void testPrimaryKeyUpdateRetainsStringPathFallback() throws Exception {
        this.assertUpdate(IdStrategy.PRIMARY_KEY, "1:alice", "\"1:alice\"", null);
    }

    private void assertUpdate(IdStrategy strategy, String rawId,
                              String encodedId, Object typedId) throws Exception {
        GraphService service = new GraphService();
        VertexLabelService labels = Mockito.mock(VertexLabelService.class);
        Mockito.when(labels.get(Mockito.eq("Person"), Mockito.any()))
               .thenReturn(VertexLabelEntity.builder()
                                            .name("Person")
                                            .idStrategy(strategy)
                                            .properties(Collections.emptySet())
                                            .build());
        Field field = GraphService.class.getDeclaredField("vlService");
        field.setAccessible(true);
        field.set(service, labels);

        RestClient rest = Mockito.mock(RestClient.class);
        RestResult result = Mockito.mock(RestResult.class);
        Vertex response = new Vertex("Person");
        Mockito.when(result.readObject(Vertex.class)).thenReturn(response);
        Mockito.when(rest.put(Mockito.anyString(), Mockito.anyString(),
                              Mockito.any(Vertex.class), Mockito.anyMap()))
               .thenReturn(result);
        GraphManager graph = new GraphManager(rest, "DEFAULT", "hubble_ux_3000");
        HugeClient client = Mockito.mock(HugeClient.class);
        Mockito.when(client.graph()).thenReturn(graph);
        VertexEntity entity = VertexEntity.builder()
                                          .id(rawId)
                                          .label("Person")
                                          .properties(Collections.emptyMap())
                                          .build();

        Assert.assertSame(response, service.updateVertex(client, rawId, entity));

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Vertex> body = ArgumentCaptor.forClass(Vertex.class);
        Mockito.verify(rest).put(path.capture(), id.capture(), body.capture(),
                                 Mockito.eq(Collections.singletonMap("action", "append")));
        Assert.assertEquals("graphspaces/DEFAULT/graphs/hubble_ux_3000/graph/vertices", path.getValue());
        Assert.assertEquals(encodedId, id.getValue());
        Assert.assertEquals(typedId, body.getValue().id());
        Assert.assertEquals("Person", body.getValue().label());
    }
}
