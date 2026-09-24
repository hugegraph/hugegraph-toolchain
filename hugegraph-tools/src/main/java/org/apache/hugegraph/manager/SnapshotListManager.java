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

import java.util.List;
import java.util.Map;

import org.apache.hugegraph.base.Printer;
import org.apache.hugegraph.base.ToolClient;
import org.apache.hugegraph.base.ToolManager;
import org.apache.hugegraph.cmd.SubCommands;

public class SnapshotListManager extends ToolManager {

    public SnapshotListManager(ToolClient.ConnectionInfo info) {
        super(info, "snapshot-list");
    }

    public List<Map<String, Object>> list(SubCommands.SnapshotList command) {
        List<Map<String, Object>> backups =
                this.client.graphs().listBackups(this.graph(),
                                                command.repository());
        Printer.printList("Backups", backups);
        return backups;
    }
}
