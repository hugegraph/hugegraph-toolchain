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

package org.apache.hugegraph.driver;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.hugegraph.api.graphs.GraphBackupsAPI;
import org.apache.hugegraph.api.graphs.GraphsAPI;
import org.apache.hugegraph.rest.ClientException;
import org.apache.hugegraph.structure.constant.GraphMode;
import org.apache.hugegraph.structure.constant.GraphReadMode;
import org.apache.hugegraph.client.RestClient;

public class GraphsManager {

    private final GraphsAPI graphsAPI;
    private final RestClient client;
    private final String graphSpace;

    public GraphsManager(RestClient client, String graphSpace) {
        this.graphsAPI = new GraphsAPI(client, graphSpace);
        this.client = client;
        this.graphSpace = graphSpace;
    }

    public Map<String, String> createGraph(String name, String config) {
        return this.graphsAPI.create(name, null, config);
    }

    public Map<String, String> createGraph(String name, File file) {
        String config;
        try {
            config = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ClientException("Failed to read config file: %s", file);
        }
        return this.graphsAPI.create(name, null, config);
    }

    public Map<String, String> cloneGraph(String name, String cloneGraphName) {
        return this.graphsAPI.create(name, cloneGraphName, null);
    }

    public Map<String, String> cloneGraph(String name, String cloneGraphName,
                                          String configText) {
        return this.graphsAPI.create(name, cloneGraphName, configText);
    }

    public Map<String, String> getGraph(String graph) {
        return this.graphsAPI.get(graph);
    }

    public List<String> listGraph() {
        return this.graphsAPI.list();
    }

    public List<Map<String, Object>> listProfile() {
        return this.graphsAPI.listProfile(null);
    }

    public List<Map<String, Object>> listProfile(String prefix) {
        return this.graphsAPI.listProfile(prefix);
    }

    public Map<String, Object> setDefault(String name) {
        return this.graphsAPI.setDefault(name);
    }

    public Map<String, Object> unSetDefault(String name) {
        return this.graphsAPI.unSetDefault(name);
    }

    public Map<String, Object> getDefault() {
        return this.graphsAPI.getDefault();
    }

    // TODO(@Thespica): see org.apache.hugegraph.api.graphs.GraphsAPI.clear
    public void clearGraph(String graph, String message) {
        this.graphsAPI.clear(graph, message);
    }

    public void update(String graph, String nickname) {
        this.graphsAPI.update(graph, nickname);
    }

    // TODO(@Thespica): see org.apache.hugegraph.api.graphs.GraphsAPI.drop
    public void dropGraph(String graph, String message) {
        this.graphsAPI.drop(graph, message);
    }

    /**
     * @deprecated Current Server per-graph management accepts only update.
     *             Use {@link #reload()} for a whole-server graph reload.
     */
    @Deprecated
    public void reload(String graph) {
        this.graphsAPI.reload(graph);
    }

    public void reload() {
        this.graphsAPI.reload();
    }

    public void mode(String graph, GraphMode mode) {
        this.graphsAPI.mode(graph, mode);
    }

    public GraphMode mode(String graph) {
        return this.graphsAPI.mode(graph);
    }

    public void readMode(String graph, GraphReadMode readMode) {
        this.graphsAPI.readMode(graph, readMode);
    }

    public GraphReadMode readMode(String graph) {
        return this.graphsAPI.readMode(graph);
    }

    /**
     * @deprecated Use {@link #createBackup(String, String, int)}. Snapshot
     *             capture is owned by the Server backup task.
     */
    @Deprecated
    public Map<String, String> createSnapshot(String graph) {
        return this.graphsAPI.createSnapshot(graph);
    }

    /**
     * @deprecated Use {@link #restoreBackup(String, String, String, boolean)}.
     *             Restore is owned by the Server backup task.
     */
    @Deprecated
    public Map<String, String> resumeSnapshot(String graph) {
        return this.graphsAPI.resumeSnapshot(graph);
    }

    public String clone(String graph, Map<String, Object> body) {
        return this.graphsAPI.clone(graph, body);
    }

    public long createBackup(String graph, String repository, int keepNum) {
        return this.createBackup(graph, repository, keepNum, null);
    }

    public long createBackup(String graph, String repository, int keepNum,
                             String requestId) {
        // The repository is resolved by Server. Tools must not access the
        // Server data directory or implement file-level incremental logic.
        return new GraphBackupsAPI(this.client, this.graphSpace, graph)
               .create(repository, keepNum, requestId);
    }

    public long restoreBackup(String graph, String repository,
                              String backupId, boolean confirm) {
        return this.restoreBackup(graph, repository, backupId, confirm, null);
    }

    public long restoreBackup(String graph, String repository,
                              String backupId, boolean confirm,
                              String requestId) {
        return new GraphBackupsAPI(this.client, this.graphSpace, graph)
               .restore(repository, backupId, confirm, requestId);
    }

    public Map<String, Object> getBackup(String graph, String backupId) {
        return new GraphBackupsAPI(this.client, this.graphSpace, graph)
               .get(backupId);
    }

    public Map<String, Object> getBackup(String graph, String repository,
                                         String backupId) {
        return new GraphBackupsAPI(this.client, this.graphSpace, graph)
                .get(repository, backupId);
    }

    public List<Map<String, Object>> listBackups(String graph) {
        return new GraphBackupsAPI(this.client, this.graphSpace, graph).list();
    }

    public List<Map<String, Object>> listBackups(String graph,
                                                 String repository) {
        return new GraphBackupsAPI(this.client, this.graphSpace, graph)
                .list(repository);
    }
}
