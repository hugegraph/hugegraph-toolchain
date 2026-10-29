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

import java.util.Date;
import java.util.List;

import org.apache.hugegraph.controller.schema.VertexLabelController;
import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.driver.SchemaManager;
import org.apache.hugegraph.entity.schema.EdgeLabelEntity;
import org.apache.hugegraph.entity.schema.VertexLabelEntity;
import org.apache.hugegraph.entity.schema.VertexLabelStyle;
import org.apache.hugegraph.service.schema.PropertyIndexService;
import org.apache.hugegraph.service.schema.VertexLabelService;
import org.apache.hugegraph.structure.schema.VertexLabel;
import org.apache.hugegraph.util.JsonUtil;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

public class SchemaCreateDefaultsTest {

    @Test
    public void testControllerAcceptsMissingAndNullStyle() {
        for (String options : List.of("", ",\"style\":null,\"open_label_index\":null")) {
            HugeClient client = Mockito.mock(HugeClient.class);
            VertexLabelService service = Mockito.mock(VertexLabelService.class);
            VertexLabelController controller = new VertexLabelController() {
                @Override
                protected HugeClient authClient(String space, String graph) {
                    return client;
                }
            };
            ReflectionTestUtils.setField(controller, "vlService", service);
            VertexLabelEntity entity = JsonUtil.fromJson(
                    "{\"name\":\"person\",\"id_strategy\":\"AUTOMATIC\"" + options + "}",
                    VertexLabelEntity.class);

            controller.create("DEFAULT", "hugegraph", entity);

            Mockito.verify(service).add(entity, client);
            Assert.assertNotNull(entity.getCreateTime());
            Assert.assertEquals(List.of("~id"), entity.getStyle().getDisplayFields());
            Assert.assertTrue(entity.getOpenLabelIndex());
        }
    }

    @Test
    public void testServiceSendsDefaultStyleAndLabelIndexToServer() {
        for (String options : List.of("", ",\"style\":null,\"open_label_index\":null", ",\"style\":{}")) {
            VertexLabelEntity entity = entity(options);
            SchemaManager schema = Mockito.mock(SchemaManager.class);
            HugeClient client = Mockito.mock(HugeClient.class);
            Mockito.when(client.schema()).thenReturn(schema);
            Mockito.when(schema.vertexLabel("person"))
                   .thenReturn(new VertexLabel.BuilderImpl("person", null));
            VertexLabelService service = new VertexLabelService();
            ReflectionTestUtils.setField(service, "piService", Mockito.mock(PropertyIndexService.class));

            service.add(entity, client);

            ArgumentCaptor<VertexLabel> sent = ArgumentCaptor.forClass(VertexLabel.class);
            Mockito.verify(schema).addVertexLabel(sent.capture());
            Assert.assertTrue(sent.getValue().enableLabelIndex());
            Assert.assertTrue(sent.getValue().userdata().get("~style").toString().contains("~id"));
            VertexLabelStyle stored = JsonUtil.fromJson(
                    sent.getValue().userdata().get("~style").toString(), VertexLabelStyle.class);
            VertexLabelStyle fallback = ReflectionTestUtils.invokeMethod(VertexLabelService.class, "getStyle",
                    new VertexLabel.BuilderImpl("person", null).build());
            Assert.assertEquals(entity.hasStyle() ? new VertexLabelStyle().getColor() : fallback.getColor(),
                                stored.getColor());
            Assert.assertFalse(JsonUtil.toJson(entity).contains("hasStyle"));
        }
    }

    @Test
    public void testExplicitFalseAndCustomStyleArePreserved() {
        VertexLabelEntity entity = entity(
                ",\"open_label_index\":false,\"style\":{\"color\":\"#123456\"," +
                "\"display_fields\":[\"~label\"]}");
        Assert.assertFalse(entity.getOpenLabelIndex());
        Assert.assertEquals("#123456", entity.getStyle().getColor());
        Assert.assertEquals(List.of("~label"), entity.getStyle().getDisplayFields());
    }

    @Test
    public void testBuilderAndSettersUseTheSameDefaults() {
        VertexLabelEntity vertex = VertexLabelEntity.builder().name("person").build();
        EdgeLabelEntity edge = EdgeLabelEntity.builder().name("knows").build();
        Assert.assertTrue(vertex.getOpenLabelIndex());
        Assert.assertTrue(edge.getOpenLabelIndex());
        Assert.assertNotNull(vertex.getStyle());
        Assert.assertNotNull(edge.getStyle());
        vertex.setOpenLabelIndex(false);
        edge.setOpenLabelIndex(false);
        Assert.assertFalse(vertex.getOpenLabelIndex());
        Assert.assertFalse(edge.getOpenLabelIndex());
        vertex.setStyle(null);
        edge.setStyle(null);
        Assert.assertEquals(List.of("~id"), vertex.getStyle().getDisplayFields());
        Assert.assertEquals(List.of("~id"), edge.getStyle().getDisplayFields());
    }

    private static VertexLabelEntity entity(String options) {
        VertexLabelEntity entity = JsonUtil.fromJson(
                "{\"name\":\"person\",\"id_strategy\":\"AUTOMATIC\"" + options + "}",
                VertexLabelEntity.class);
        entity.setCreateTime(new Date());
        return entity;
    }
}
