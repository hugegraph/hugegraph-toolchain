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

package org.apache.hugegraph.api.graphs;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.hugegraph.api.API;
import org.apache.hugegraph.client.RestClient;
import org.apache.hugegraph.rest.RestResult;
import org.apache.hugegraph.structure.constant.HugeType;
import org.apache.hugegraph.util.E;

public class GraphBackupsAPI extends API {

    private static final String PATH = "graphspaces/%s/graphs/%s/backups";
    private static final String TASK_ID = "task_id";
    private static final String BACKUPS = "backups";
    private static final String REPOSITORY_PATTERN =
            "[A-Za-z0-9][A-Za-z0-9._-]{0,62}";

    public GraphBackupsAPI(RestClient client, String graphSpace, String graph) {
        super(client);
        this.path(String.format(PATH, graphSpace, graph));
    }

    @Override
    protected String type() {
        return HugeType.TASK.string();
    }

    public long create(String repository, int keepNum) {
        return this.create(repository, keepNum, null);
    }

    public long create(String repository, int keepNum, String requestId) {
        // Server resolves the repository and runs RocksDB BackupEngine. This
        // client never reads the graph data directory or copies DB files.
        checkRepository(repository);
        E.checkArgument(keepNum >= 0, "Keep number must be non-negative");
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("repository", repository);
        body.put("keep_num", keepNum);
        if (requestId != null && !requestId.trim().isEmpty()) {
            body.put("request_id", requestId);
        }
        RestResult result = this.client.post(this.path(), body);
        Map<String, Object> response = result.readObject(Map.class);
        return parseTaskId(response, "Backup");
    }

    public long restore(String repository, String backupId, boolean confirm) {
        return this.restore(repository, backupId, confirm, null);
    }

    public long restore(String repository, String backupId, boolean confirm,
                        String requestId) {
        // A graph backup version is restored by the Server as a coordinated
        // set of Store backup ids; the client only submits the task.
        E.checkArgument(confirm, "Snapshot restore requires --confirm");
        checkRepository(repository);
        checkBackupId(backupId);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("repository", repository);
        if (backupId != null && !backupId.isEmpty()) {
            body.put("backup_id", backupId);
        }
        body.put("confirm", true);
        if (requestId != null && !requestId.trim().isEmpty()) {
            body.put("request_id", requestId);
        }
        RestResult result = this.client.post(this.path() + "/restore", body);
        Map<String, Object> response = result.readObject(Map.class);
        return parseTaskId(response, "Restore");
    }

    public Map<String, Object> get(String backupId) {
        checkRequiredBackupId(backupId);
        RestResult result = this.client.get(this.path(), backupId);
        return readBackup(result, backupId);
    }

    public Map<String, Object> get(String repository, String backupId) {
        checkRepository(repository);
        checkPathBackupId(backupId);
        RestResult result = this.client.get(this.path() + "/" + backupId,
                                           Collections.singletonMap(
                                                   "repository", repository));
        return readBackup(result, backupId);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> list() {
        RestResult result = this.client.get(this.path());
        return readBackupList(result);
    }

    public List<Map<String, Object>> list(String repository) {
        checkRepository(repository);
        RestResult result = this.client.get(this.path(),
                                           Collections.singletonMap(
                                                   "repository", repository));
        return readBackupList(result);
    }

    private static Map<String, Object> readBackup(RestResult result,
                                                   String backupId) {
        Map<String, Object> response = result.readObject(Map.class);
        E.checkState(response != null,
                     "Backup response for '%s' must be an object", backupId);
        return response;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> readBackupList(RestResult result) {
        Map<String, Object> response = result.readObject(Map.class);
        E.checkState(response != null && response.containsKey(BACKUPS),
                     "Backup list response must contain '%s': %s",
                     BACKUPS, response);
        Object value = response.get(BACKUPS);
        E.checkState(value instanceof List,
                     "Backup list response '%s' must be a list: %s",
                     BACKUPS, response);
        List<Map<String, Object>> backups = new ArrayList<>();
        for (Object backup : (List<?>) value) {
            E.checkState(backup instanceof Map,
                         "Backup list entry must be an object: %s", backup);
            backups.add((Map<String, Object>) backup);
        }
        return backups;
    }

    private static long parseTaskId(Map<String, Object> response,
                                    String operation) {
        Object taskId = response == null ? null : response.get(TASK_ID);
        E.checkState(taskId instanceof Number,
                     "%s response must contain numeric '%s': %s",
                     operation, TASK_ID, response);
        try {
            long id = new BigDecimal(taskId.toString()).longValueExact();
            E.checkState(id > 0,
                         "%s response must contain a positive '%s': %s",
                         operation, TASK_ID, response);
            return id;
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalStateException(String.format(
                    "%s response must contain an integral '%s': %s",
                    operation, TASK_ID, response), e);
        }
    }

    private static void checkBackupId(String backupId) {
        E.checkArgument(backupId == null || !backupId.trim().isEmpty(),
                        "Backup id must not be blank");
    }

    private static void checkRequiredBackupId(String backupId) {
        E.checkArgument(backupId != null && !backupId.trim().isEmpty(),
                        "Backup id must not be blank");
    }

    private static void checkPathBackupId(String backupId) {
        checkRequiredBackupId(backupId);
        E.checkArgument(backupId.matches("[A-Za-z0-9][A-Za-z0-9._-]*"),
                        "Invalid backup id '%s'", backupId);
    }

    private static void checkRepository(String repository) {
        E.checkArgument(repository != null &&
                        repository.matches(REPOSITORY_PATTERN),
                        "Invalid repository name '%s'", repository);
    }
}
