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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.driver.SchemaManager;
import org.apache.hugegraph.entity.schema.VertexLabelEntity;
import org.apache.hugegraph.entity.schema.VertexLabelStyle;
import org.apache.hugegraph.service.schema.SchemaService;
import org.apache.hugegraph.service.schema.VertexLabelService;
import org.apache.hugegraph.structure.schema.VertexLabel;
import org.apache.hugegraph.util.JsonUtil;

public class VertexLabelStyleDefaultsTest {

    @Test
    public void testUnstyledLabelsHaveStableColorsAcrossReadOrder() {
        VertexLabel person = label("Person");
        VertexLabel team = label("Team");
        VertexLabel project = label("Project");
        HugeClient client = client(person, team, project);
        Mockito.when(client.schema().getVertexLabels())
               .thenReturn(List.of(person, team, project),
                           List.of(project, person, team));

        Map<String, String> first = colors(new VertexLabelService().list(client));
        Map<String, String> reordered = colors(new VertexLabelService().list(client));

        Assert.assertEquals(3, new HashSet<>(first.values()).size());
        Assert.assertEquals(first, reordered);
        first.forEach((name, color) -> {
            Assert.assertTrue(color.matches("#[0-9A-Fa-f]{6}"));
            Assert.assertEquals(color, new VertexLabelService().get(name, client)
                                                              .getStyle().getColor());
        });
        for (VertexLabel label : List.of(person, team, project)) {
            Assert.assertFalse(label.userdata().containsKey(SchemaService.USER_KEY_STYLE));
        }
    }

    @Test
    public void testSavedBluePreservesEveryCustomField() {
        VertexLabel person = label("Person");
        VertexLabelStyle saved = new VertexLabelStyle(
                "person-icon", "#5C73E6", VertexLabelStyle.Size.BIG,
                List.of("name", "community"), List.of(" / "));
        String raw = JsonUtil.toJson(saved);
        person.userdata().put(SchemaService.USER_KEY_STYLE, raw);
        Map<String, Object> before = new HashMap<>(person.userdata());

        VertexLabelStyle result = new VertexLabelService().get("Person", client(person))
                                                         .getStyle();

        Assert.assertEquals(saved.getIcon(), result.getIcon());
        Assert.assertEquals(saved.getColor(), result.getColor());
        Assert.assertEquals(saved.getSize(), result.getSize());
        Assert.assertEquals(saved.getDisplayFields(), result.getDisplayFields());
        Assert.assertEquals(saved.getJoinSymbols(), result.getJoinSymbols());
        Assert.assertEquals(before, person.userdata());
    }

    @Test
    public void testPartialSavedStylesKeepTheirExistingDefaults() {
        for (String raw : List.of("{\"color\":\"#5C73E6\"}", "{\"size\":\"BIG\"}", "{}")) {
            VertexLabel person = label("Person");
            person.userdata().put(SchemaService.USER_KEY_STYLE, raw);

            VertexLabelStyle result = new VertexLabelService().get("Person", client(person))
                                                             .getStyle();

            Assert.assertEquals("#5C73E6", result.getColor());
            Assert.assertEquals(raw.contains("BIG") ? VertexLabelStyle.Size.BIG :
                                VertexLabelStyle.Size.NORMAL, result.getSize());
            Assert.assertEquals(List.of("~id"), result.getDisplayFields());
            Assert.assertEquals(List.of("-"), result.getJoinSymbols());
            Assert.assertEquals(raw, person.userdata().get(SchemaService.USER_KEY_STYLE));
        }
    }

    private static VertexLabel label(String name) {
        VertexLabel label = new VertexLabel.BuilderImpl(name, null)
                                          .enableLabelIndex(true).build();
        label.userdata().put(SchemaService.USER_KEY_CREATE_TIME, 0L);
        return label;
    }

    private static HugeClient client(VertexLabel... labels) {
        SchemaManager schema = Mockito.mock(SchemaManager.class);
        HugeClient client = Mockito.mock(HugeClient.class);
        Mockito.when(client.schema()).thenReturn(schema);
        Mockito.when(schema.getIndexLabels()).thenReturn(Collections.emptyList());
        for (VertexLabel label : labels) {
            Mockito.when(schema.getVertexLabel(label.name())).thenReturn(label);
        }
        return client;
    }

    private static Map<String, String> colors(List<VertexLabelEntity> labels) {
        return labels.stream().collect(Collectors.toMap(
                VertexLabelEntity::getName, label -> label.getStyle().getColor()));
    }
}
