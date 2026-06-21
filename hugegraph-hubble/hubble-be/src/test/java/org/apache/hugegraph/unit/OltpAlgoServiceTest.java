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
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hugegraph.unit;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.Test;
import org.mockito.Mockito;

import org.apache.hugegraph.driver.GraphManager;
import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.entity.query.GraphView;
import org.apache.hugegraph.service.algorithm.OltpAlgoService;
import org.apache.hugegraph.structure.graph.Edge;
import org.apache.hugegraph.structure.graph.Path;
import org.apache.hugegraph.structure.graph.Vertex;
import org.apache.hugegraph.testutil.Assert;

public class OltpAlgoServiceTest {

    @Test
    public void testBuildPathGraphViewKeepsPathEdges() throws Exception {
        Vertex marko = new Vertex("person");
        marko.id("marko");
        Vertex vadas = new Vertex("person");
        vadas.id("vadas");

        Edge knows = new Edge("knows");
        knows.id("S1:marko>vadas");
        knows.source(marko);
        knows.target(vadas);

        HugeClient client = Mockito.mock(HugeClient.class);
        Path path = new Path(Arrays.asList(marko, knows, vadas));

        GraphView graphView = this.buildPathGraphView(client, path);

        Assert.assertEquals(2, graphView.getVertices().size());
        Assert.assertEquals(1, graphView.getEdges().size());
        Assert.assertTrue(graphView.getEdges().contains(knows));
    }

    @Test
    public void testBuildPathGraphViewBackfillsEdgeEndpointVertices()
           throws Exception {
        Vertex marko = new Vertex("person");
        marko.id("marko");
        Vertex vadas = new Vertex("person");
        vadas.id("vadas");

        Edge knows = new Edge("knows");
        knows.id("S1:marko>vadas");
        knows.source(marko);
        knows.target(vadas);

        GraphManager graph = Mockito.mock(GraphManager.class);
        Mockito.when(graph.getVertex("marko")).thenReturn(marko);
        Mockito.when(graph.getVertex("vadas")).thenReturn(vadas);
        HugeClient client = Mockito.mock(HugeClient.class);
        Mockito.when(client.graph()).thenReturn(graph);
        Path path = new Path(Arrays.asList(knows));

        GraphView graphView = this.buildPathGraphView(client, path);

        Assert.assertEquals(2, graphView.getVertices().size());
        Assert.assertEquals(1, graphView.getEdges().size());
        Assert.assertTrue(graphView.getEdges().contains(knows));
    }

    private GraphView buildPathGraphView(HugeClient client, Path path)
                                  throws Exception {
        OltpAlgoService service = new OltpAlgoService();
        Method method = OltpAlgoService.class.getDeclaredMethod("buildPathGraphView",
                                                               HugeClient.class,
                                                               Path.class);
        method.setAccessible(true);
        return (GraphView) method.invoke(service, client, path);
    }
}
