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

package org.apache.hugegraph.snapshot;

import org.apache.hugegraph.cmd.HugeGraphCommand;
import org.apache.hugegraph.cmd.SubCommands;
import org.junit.Assert;
import org.junit.Test;

public class SnapshotCommandTest {

    @Test
    public void testParseSnapshotBackupCommand() {
        HugeGraphCommand command = new HugeGraphCommand();
        command.parseCommand(new String[] {
                "--graph", "hugegraph",
                "snapshot-backup",
                "--repository", "daily",
                "--keep-num", "2",
                "--request-id", "backup-request",
                "--task-timeout", "90",
        });
        SubCommands.SnapshotBackup backup =
                command.subCommand("snapshot-backup");
        Assert.assertEquals("daily", backup.repository());
        Assert.assertEquals(2, backup.keepNum());
        Assert.assertEquals("backup-request", backup.requestId());
        Assert.assertEquals(90, backup.taskTimeout());
    }

    @Test
    public void testParseSnapshotRestoreCommand() {
        HugeGraphCommand command = new HugeGraphCommand();
        command.parseCommand(new String[] {
                "--graph", "hugegraph",
                "snapshot-restore",
                "--repository", "daily",
                "--backup-id", "20260912-100000",
                "--request-id", "restore-request",
                "--confirm"
        });
        SubCommands.SnapshotRestore restore =
                command.subCommand("snapshot-restore");
        Assert.assertEquals("daily", restore.repository());
        Assert.assertEquals("20260912-100000", restore.backupId());
        Assert.assertTrue(restore.confirm());
        Assert.assertEquals("restore-request", restore.requestId());
    }

    @Test
    public void testParseSnapshotDiscoveryCommands() {
        HugeGraphCommand listCommand = new HugeGraphCommand();
        listCommand.parseCommand(new String[] {
                "--graph", "hugegraph",
                "snapshot-list",
                "--repository", "daily"
        });
        SubCommands.SnapshotList list = listCommand.subCommand("snapshot-list");
        Assert.assertEquals("daily", list.repository());

        HugeGraphCommand getCommand = new HugeGraphCommand();
        getCommand.parseCommand(new String[] {
                "--graph", "hugegraph",
                "snapshot-get",
                "--repository", "daily",
                "--backup-id", "20260912-100000"
        });
        SubCommands.SnapshotGet get = getCommand.subCommand("snapshot-get");
        Assert.assertEquals("daily", get.repository());
        Assert.assertEquals("20260912-100000", get.backupId());
    }
}
