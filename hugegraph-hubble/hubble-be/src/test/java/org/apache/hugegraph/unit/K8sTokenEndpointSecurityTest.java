/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the
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

package org.apache.hugegraph.unit;

import java.lang.reflect.Method;

import org.apache.hugegraph.controller.op.K8sTokenController;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

public class K8sTokenEndpointSecurityTest {

    @Test
    public void testK8sTokenEndpointHasNoWebMappings() {
        Assert.assertNull(K8sTokenController.class.getDeclaredAnnotation(
                          RestController.class));
        Assert.assertNull(K8sTokenController.class.getDeclaredAnnotation(
                          RequestMapping.class));

        for (Method method : K8sTokenController.class.getDeclaredMethods()) {
            Assert.assertFalse(AnnotatedElementUtils.hasAnnotation(
                               method, RequestMapping.class));
        }
    }
}
