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

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import org.apache.hugegraph.config.HugeConfig;
import org.apache.hugegraph.controller.ConfigController;
import org.apache.hugegraph.options.HubbleOptions;

public class ConfigControllerTest {

    @Test
    public void testBootstrapConfigDoesNotExposeBackendUrl() {
        HugeConfig config = Mockito.mock(HugeConfig.class);
        Mockito.when(config.get(HubbleOptions.PD_ENABLED)).thenReturn(false);
        Mockito.when(config.get(HubbleOptions.AUTH_ENABLED)).thenReturn(true);

        ConfigController controller = new ConfigController();
        ReflectionTestUtils.setField(controller, "config", config);

        Map<String, Object> result = controller.getConfig();

        Assert.assertEquals(Map.of("pd_enabled", false,
                                   "auth_enabled", true), result);
        Mockito.verify(config, Mockito.never()).get(HubbleOptions.SERVER_URL);
    }
}
