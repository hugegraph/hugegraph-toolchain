/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hugegraph.controller.space;

import org.junit.Test;
import org.mockito.Mockito;

import org.apache.hugegraph.config.HugeConfig;
import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.exception.ForbiddenException;
import org.apache.hugegraph.options.HubbleOptions;
import org.apache.hugegraph.structure.space.SchemaTemplate;
import org.apache.hugegraph.testutil.Assert;

public class SchemaTemplateControllerSecurityTest {

    @Test
    public void testCreateRequiresGraphSpaceWrite() {
        TestController controller = controller();

        Assert.assertThrows(ForbiddenException.class, () ->
                controller.create("space", Mockito.mock(SchemaTemplate.class)));
    }

    @Test
    public void testUpdateRequiresGraphSpaceWrite() {
        TestController controller = controller();

        Assert.assertThrows(ForbiddenException.class, () ->
                controller.update("space", "template",
                                  Mockito.mock(SchemaTemplate.class)));
    }

    @Test
    public void testDeleteRequiresGraphSpaceWrite() {
        TestController controller = controller();

        Assert.assertThrows(ForbiddenException.class, () ->
                controller.delete("space", "template"));
    }

    private static TestController controller() {
        TestController controller = new TestController();
        HugeConfig config = Mockito.mock(HugeConfig.class);
        Mockito.when(config.get(HubbleOptions.PD_ENABLED)).thenReturn(true);
        controller.config = config;
        return controller;
    }

    private static class TestController extends SchemaTemplateController {

        @Override
        protected HugeClient requireGraphSpaceWrite(String graphSpace) {
            throw new ForbiddenException(
                    "Permission denied: write graphspace resources");
        }
    }
}
