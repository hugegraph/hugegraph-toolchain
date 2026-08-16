/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hugegraph.service.auth;

import java.util.Collections;

import org.junit.Test;
import org.mockito.Mockito;

import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.exception.ParameterizedException;
import org.apache.hugegraph.testutil.Assert;

public class GraphSpaceUserServiceTest {

    @Test
    public void testPermissionPresetFailureHasActionableErrorKey() {
        HugeClient client = Mockito.mock(HugeClient.class);
        Mockito.when(client.supportsDefaultRole()).thenReturn(false);
        GraphSpaceUserService service = new GraphSpaceUserService();

        ParameterizedException error = null;
        try {
            service.validatePermissionPresets(
                    client, Collections.emptyList(), "GS_READ_ONLY");
        } catch (ParameterizedException e) {
            error = e;
        }

        Assert.assertNotNull(error);
        Assert.assertEquals("auth.permission-preset.unsupported",
                            error.getMessage());
    }
}
