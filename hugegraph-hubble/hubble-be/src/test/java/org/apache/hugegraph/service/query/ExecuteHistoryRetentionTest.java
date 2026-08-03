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
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.apache.hugegraph.service.query;

import java.sql.Timestamp;
import java.util.List;

import javax.sql.DataSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import org.apache.hugegraph.mapper.query.ExecuteHistoryMapper;
import org.apache.hugegraph.testutil.Assert;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ExecuteHistoryRetentionTest.TestConfiguration.class,
                webEnvironment = SpringBootTest.WebEnvironment.NONE,
                properties = {
                    "spring.datasource.url=jdbc:h2:mem:execute-history-retention;" +
                    "DB_CLOSE_DELAY=-1",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.datasource.username=sa",
                    "spring.datasource.password=",
                    "spring.datasource.initialization-mode=never",
                    "spring.datasource.hikari.maximum-pool-size=2",
                    "spring.autoconfigure.exclude=" +
                    "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration",
                    "mybatis.configuration.map-underscore-to-camel-case=true",
                    "mybatis.configuration.use-generated-keys=true",
                    "mybatis-plus.type-enums-package=org.apache.hugegraph.entity.enums"
                })
public class ExecuteHistoryRetentionTest {

    private static final String SPACE = "DEFAULT";

    @Autowired
    private ExecuteHistoryMapper mapper;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @Before
    public void setup() {
        this.jdbc = new JdbcTemplate(this.dataSource);
        this.jdbc.execute("CREATE TABLE IF NOT EXISTS `execute_history` (" +
                          "`id` INT NOT NULL AUTO_INCREMENT, " +
                          "`conn_id` INT, " +
                          "`graphspace` VARCHAR(48) NOT NULL, " +
                          "`graph` VARCHAR(48) NOT NULL, " +
                          "`async_id` BIGINT NOT NULL, " +
                          "`execute_type` TINYINT NOT NULL, " +
                          "`content` VARCHAR(65535) NOT NULL, " +
                          "`text` VARCHAR(65535) NOT NULL, " +
                          "`execute_status` TINYINT NOT NULL, " +
                          "`failure_reason` VARCHAR(64) DEFAULT NULL, " +
                          "`async_status` TINYINT NOT NULL DEFAULT 0, " +
                          "`duration` BIGINT NOT NULL, " +
                          "`create_time` DATETIME(6) NOT NULL, " +
                          "PRIMARY KEY (`id`))");
        this.jdbc.update("DELETE FROM `execute_history`");
    }

    @Test
    public void testKeepsNewestRowsPerGraphAndDeletesTheRest() {
        // A busy graph and a quiet one, interleaved in time
        for (int i = 0; i < 6; i++) {
            this.insert("busy", "busy-" + i, 1000L + (i * 1000L));
        }
        this.insert("quiet", "quiet-0", 1000L);
        this.insert("quiet", "quiet-1", 2000L);

        this.mapper.deleteExceedLimit(2);

        // The quiet graph is untouched, the busy one keeps only the newest 2
        Assert.assertEquals(2, this.count("quiet"));
        Assert.assertEquals(2, this.count("busy"));
        List<String> kept = this.jdbc.queryForList(
                "SELECT `content` FROM `execute_history` " +
                "WHERE `graph` = 'busy' ORDER BY `create_time` DESC",
                String.class);
        Assert.assertEquals(2, kept.size());
        Assert.assertEquals("busy-5", kept.get(0));
        Assert.assertEquals("busy-4", kept.get(1));
    }

    @Test
    public void testKeepsEverythingWhenUnderTheLimit() {
        this.insert("small", "only-0", 1000L);
        this.insert("small", "only-1", 2000L);

        this.mapper.deleteExceedLimit(500);

        Assert.assertEquals(2, this.count("small"));
    }

    private void insert(String graph, String content, long millis) {
        this.jdbc.update("INSERT INTO `execute_history` " +
                         "(`graphspace`, `graph`, `async_id`, " +
                         "`execute_type`, `content`, `text`, " +
                         "`execute_status`, `async_status`, `duration`, " +
                         "`create_time`) VALUES (?, ?, 0, 1, ?, '', 1, 0, " +
                         "0, ?)",
                         SPACE, graph, content, new Timestamp(millis));
    }

    private int count(String graph) {
        return this.jdbc.queryForObject(
                "SELECT COUNT(*) FROM `execute_history` " +
                "WHERE `graphspace` = ? AND `graph` = ?",
                Integer.class, SPACE, graph);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableTransactionManagement
    @MapperScan("org.apache.hugegraph.mapper.query")
    public static class TestConfiguration {
    }
}
