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

package org.apache.hugegraph.unit;

import java.util.Date;
import java.util.List;

import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.driver.SchemaManager;
import org.apache.hugegraph.entity.schema.EdgeLabelEntity;
import org.apache.hugegraph.entity.schema.EdgeLabelStyle;
import org.apache.hugegraph.entity.schema.VertexLabelEntity;
import org.apache.hugegraph.entity.schema.VertexLabelStyle;
import org.apache.hugegraph.service.schema.EdgeLabelService;
import org.apache.hugegraph.service.schema.PropertyIndexService;
import org.apache.hugegraph.service.schema.VertexLabelService;
import org.apache.hugegraph.structure.schema.EdgeLabel;
import org.apache.hugegraph.structure.schema.VertexLabel;
import org.apache.hugegraph.util.JsonUtil;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

public class SchemaCreateServiceDefaultsTest {

    @Test
    public void testVertexServicePreservesExplicitFalseAndCustomStyle() {
        VertexLabelEntity entity = JsonUtil.fromJson(
                "{\"name\":\"person\",\"id_strategy\":\"AUTOMATIC\"," +
                "\"open_label_index\":false,\"style\":{\"color\":\"#123456\"," +
                "\"display_fields\":[\"~label\"]}}",
                VertexLabelEntity.class);
        entity.setCreateTime(new Date());
        SchemaManager schema = Mockito.mock(SchemaManager.class);
        HugeClient client = Mockito.mock(HugeClient.class);
        Mockito.when(client.schema()).thenReturn(schema);
        Mockito.when(schema.vertexLabel("person"))
               .thenReturn(new VertexLabel.BuilderImpl("person", null));
        VertexLabelService service = new VertexLabelService();
        ReflectionTestUtils.setField(service, "piService",
                                     Mockito.mock(PropertyIndexService.class));

        service.add(entity, client);

        ArgumentCaptor<VertexLabel> sent = ArgumentCaptor.forClass(VertexLabel.class);
        Mockito.verify(schema).addVertexLabel(sent.capture());
        Assert.assertFalse(sent.getValue().enableLabelIndex());
        VertexLabelStyle stored = JsonUtil.fromJson(
                sent.getValue().userdata().get("~style").toString(),
                VertexLabelStyle.class);
        Assert.assertEquals("#123456", stored.getColor());
        Assert.assertEquals(List.of("~label"), stored.getDisplayFields());
    }

    @Test
    public void testOrdinaryEdgeServiceSendsDefaultStyleAndLabelIndex() {
        EdgeLabelEntity entity = JsonUtil.fromJson(
                "{\"name\":\"knows\",\"edgelabel_type\":\"NORMAL\"," +
                "\"source_label\":\"person\",\"target_label\":\"person\"," +
                "\"properties\":[],\"sort_keys\":[],\"property_indexes\":[]}",
                EdgeLabelEntity.class);
        entity.setCreateTime(new Date());
        SchemaManager schema = Mockito.mock(SchemaManager.class);
        HugeClient client = Mockito.mock(HugeClient.class);
        Mockito.when(client.schema()).thenReturn(schema);
        Mockito.when(schema.edgeLabel("knows"))
               .thenReturn(new EdgeLabel.BuilderImpl("knows", null));
        EdgeLabelService service = new EdgeLabelService();
        ReflectionTestUtils.setField(service, "piService",
                                     Mockito.mock(PropertyIndexService.class));

        service.add(entity, client);

        ArgumentCaptor<EdgeLabel> sent = ArgumentCaptor.forClass(EdgeLabel.class);
        Mockito.verify(schema).addEdgeLabel(sent.capture());
        Assert.assertTrue(sent.getValue().enableLabelIndex());
        EdgeLabelStyle stored = JsonUtil.fromJson(
                sent.getValue().userdata().get("~style").toString(),
                EdgeLabelStyle.class);
        Assert.assertEquals(List.of("~id"), stored.getDisplayFields());
    }
}
