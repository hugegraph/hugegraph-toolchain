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

package org.apache.hugegraph.manager;

import java.util.UUID;

import org.apache.hugegraph.base.Printer;
import org.apache.hugegraph.base.ToolClient;
import org.apache.hugegraph.base.ToolManager;
import org.apache.hugegraph.cmd.SubCommands;
import org.apache.hugegraph.structure.Task;
import org.apache.hugegraph.util.E;

public class SnapshotRestoreManager extends ToolManager {

    public SnapshotRestoreManager(ToolClient.ConnectionInfo info) {
        super(info, "snapshot-restore");
    }

    public long restore(SubCommands.SnapshotRestore command) {
        E.checkNotNull(command, "command");
        String requestId = command.requestId();
        if (requestId == null || requestId.trim().isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }
        Printer.printKV("Request id", requestId);
        long id = this.client.graphs().restoreBackup(this.graph(),
                                                    command.repository(),
                                                    command.backupId(),
                                                    command.confirm(), requestId);
        Printer.printKV("Task id", id);
        Task task = this.client.tasks().waitUntilTaskCompletedWithRetry(
                id, command.taskTimeout());
        if (task != null) {
            Printer.printKV("Task status", task.status());
        }
        if (task != null && task.result() != null) {
            Printer.printKV("Restore result", task.result());
        }
        return task == null ? id : task.id();
    }
}
