/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hugegraph.spark.connector.builder;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

public class EdgeBuilderFieldIndexesTest {

    @Test
    public void matchesFieldsInMappingOrder() {
        Assert.assertArrayEquals(new int[]{2, 0},
                                 EdgeBuilder.findFieldIndexes(Arrays.asList("tenant", "id"),
                                                              new String[]{"id", "name",
                                                                           "tenant"}));
    }

    @Test
    public void missingFieldIsRejectedInsteadOfUsingFirstColumn() {
        org.apache.hugegraph.testutil.Assert.assertThrows(
                IllegalArgumentException.class,
                () -> EdgeBuilder.findFieldIndexes(Arrays.asList("source", "missing"),
                                                   new String[]{"source", "target"}),
                e -> Assert.assertTrue(e.getMessage().contains("missing")));
    }

    @Test
    public void duplicateFieldIsRejectedInsteadOfOverflowing() {
        org.apache.hugegraph.testutil.Assert.assertThrows(
                IllegalArgumentException.class,
                () -> EdgeBuilder.findFieldIndexes(Collections.singletonList("source"),
                                                   new String[]{"source", "source"}),
                e -> Assert.assertTrue(e.getMessage().contains("source")));
    }

    @Test
    public void duplicateUnrelatedColumnsDoNotChangeTheMapping() {
        Assert.assertArrayEquals(new int[]{1},
                                 EdgeBuilder.findFieldIndexes(Collections.singletonList("id"),
                                                              new String[]{"other", "id",
                                                                           "other"}));
    }

    @Test
    public void repeatedMappingFieldKeepsExistingBehavior() {
        Assert.assertArrayEquals(new int[]{1, 1},
                                 EdgeBuilder.findFieldIndexes(Arrays.asList("id", "id"),
                                                              new String[]{"other", "id"}));
    }
}
