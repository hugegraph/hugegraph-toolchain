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

package org.apache.hugegraph.loader.test.unit;

import org.apache.hadoop.ipc.protobuf.ProtobufRpcEngineProtos.RequestHeaderProto;
import org.apache.hugegraph.pd.grpc.discovery.Query;
import org.apache.hugegraph.testutil.Assert;
import org.junit.Test;

public class ProtobufRuntimeTest {

    @Test
    public void testPdDiscoveryMessageRoundTrip() throws Exception {
        Query query = Query.newBuilder().setAppName("loader-fixture")
                           .putLabels("GRAPHSPACE", "DEFAULT")
                           .putLabels("SERVICE_NAME", "fixture").build();
        Query decoded = Query.parseFrom(query.toByteArray());
        Assert.assertEquals(query, decoded);
        Assert.assertEquals("DEFAULT", decoded.getLabelsMap().get("GRAPHSPACE"));
    }

    @Test
    public void testHadoopRpcMessageRoundTrip() throws Exception {
        RequestHeaderProto header = RequestHeaderProto.newBuilder()
                .setMethodName("getFileInfo")
                .setDeclaringClassProtocolName("org.apache.hadoop.hdfs.protocol.ClientProtocol")
                .setClientProtocolVersion(1L).build();
        RequestHeaderProto decoded = RequestHeaderProto.parseFrom(header.toByteArray());
        Assert.assertEquals(header, decoded);
        Assert.assertEquals("getFileInfo", decoded.getMethodName());
    }
}
