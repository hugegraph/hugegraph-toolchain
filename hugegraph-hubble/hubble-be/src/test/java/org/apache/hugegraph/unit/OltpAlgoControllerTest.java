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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.hugegraph.controller.algorithm.OltpAlgoController;
import org.apache.hugegraph.testutil.Assert;
import org.junit.Test;
import org.springframework.web.bind.annotation.PostMapping;

public class OltpAlgoControllerTest {

    @Test
    public void testShortestPathIsExposedWithFrontendCompatibleAlias() {
        List<String> endpoints = Arrays.stream(OltpAlgoController.class
                                       .getDeclaredMethods())
                                       .map(this::postMappingPath)
                                       .flatMap(List::stream)
                                       .collect(Collectors.toList());

        Assert.assertEquals(2, endpoints.size());
        Assert.assertTrue(endpoints.contains("shortestPath"));
        Assert.assertTrue(endpoints.contains("shortpath"));
    }

    private List<String> postMappingPath(Method method) {
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        if (mapping == null || mapping.value().length == 0) {
            return Arrays.asList();
        }
        return Arrays.asList(mapping.value());
    }
}
