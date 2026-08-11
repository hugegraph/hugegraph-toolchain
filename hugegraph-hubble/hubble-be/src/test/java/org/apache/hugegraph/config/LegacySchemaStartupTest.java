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
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.apache.hugegraph.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.junit4.SpringRunner;

import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.entity.enums.ExecuteType;
import org.apache.hugegraph.entity.query.ExecuteHistory;
import org.apache.hugegraph.entity.query.GremlinCollection;
import org.apache.hugegraph.options.HubbleOptions;
import org.apache.hugegraph.service.query.ExecuteHistoryService;
import org.apache.hugegraph.service.query.GremlinCollectionService;
import org.apache.hugegraph.testutil.Assert;
import com.baomidou.mybatisplus.core.metadata.IPage;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = LegacySchemaStartupTest.TestConfiguration.class,
                webEnvironment = SpringBootTest.WebEnvironment.NONE,
                properties = {
                    "spring.datasource.url=" + LegacySchemaStartupTest.JDBC_URL,
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.datasource.username=sa",
                    "spring.datasource.password=",
                    "spring.datasource.schema=" +
                    "file:src/main/resources/database/schema.sql",
                    "spring.datasource.initialization-mode=always",
                    "spring.datasource.hikari.maximum-pool-size=2",
                    "spring.autoconfigure.exclude=" +
                    "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration",
                    "mybatis.configuration.map-underscore-to-camel-case=true",
                    "mybatis.configuration.use-generated-keys=true",
                    "mybatis-plus.type-enums-package=" +
                    "org.apache.hugegraph.entity.enums"
                })
public class LegacySchemaStartupTest {

    static final String JDBC_URL =
            "jdbc:h2:./target/legacy-schema-startup;DB_CLOSE_DELAY=-1";

    static {
        prepareLegacyDatabase();
    }

    @Autowired
    private DataSource dataSource;
    @Autowired
    private DatabaseSchemaMigrator migrator;
    @Autowired
    private ExecuteHistoryService historyService;
    @Autowired
    private GremlinCollectionService collectionService;
    @MockBean
    private HugeConfig config;

    private HugeClient client;

    @Before
    public void setup() throws Exception {
        Mockito.when(this.config.get(HubbleOptions.EXECUTE_HISTORY_SHOW_LIMIT))
               .thenReturn(500);
        this.client = Mockito.mock(HugeClient.class);
        Mockito.when(this.client.getGraphSpaceName()).thenReturn("DEFAULT");
        Mockito.when(this.client.getGraphName()).thenReturn("legacygraph");
        try (Connection conn = this.dataSource.getConnection()) {
            this.migrator.migrate(conn);
        }
    }

    @Test
    public void testLegacyDatabaseStartsAndRowsAreVisibleThroughServices()
            throws Exception {
        IPage<ExecuteHistory> histories = this.historyService.list(
                this.client, ExecuteType.GREMLIN.getValue(), 1L, 10L, false);
        Assert.assertEquals(1L, histories.getTotal());
        Assert.assertEquals("g.V()", histories.getRecords().get(0).getContent());

        IPage<GremlinCollection> collections = this.collectionService.list(
                this.client, null, "GREMLIN", null, false, 1L, 10L);
        Assert.assertEquals(1L, collections.getTotal());
        Assert.assertEquals("saved", collections.getRecords().get(0).getName());

        try (Connection conn = this.dataSource.getConnection()) {
            this.assertScopedRows(conn);
        }
    }

    @Test
    public void testSavedQueryConstraintMatchesApplicationScope()
            throws Exception {
        try (Connection conn = this.dataSource.getConnection();
             Statement statement = conn.createStatement()) {
            statement.execute("DELETE FROM `gremlin_collection` " +
                              "WHERE `graph` = 'othergraph'");
            statement.execute("INSERT INTO `gremlin_collection` " +
                              "(`conn_id`, `graphspace`, `graph`, `name`, " +
                              "`type`, `content`, `create_time`) VALUES " +
                              "(1, 'DEFAULT', 'othergraph', 'saved', " +
                              "'GREMLIN', 'g.E()', CURRENT_TIMESTAMP)");
            try (ResultSet rows = statement.executeQuery(
                    "SELECT COUNT(*) FROM `gremlin_collection` WHERE " +
                    "`graphspace` = 'DEFAULT' AND `graph` = 'othergraph' " +
                    "AND `name` = 'saved' AND `type` = 'GREMLIN'")) {
                Assert.assertTrue(rows.next());
                Assert.assertEquals(1L, rows.getLong(1));
            }
            Assert.assertThrows(SQLException.class, () ->
                    statement.execute("INSERT INTO `gremlin_collection` " +
                                      "(`conn_id`, `graphspace`, `graph`, " +
                                      "`name`, `type`, `content`, " +
                                      "`create_time`) VALUES " +
                                      "(1, 'DEFAULT', 'legacygraph', " +
                                      "'saved', 'GREMLIN', 'g.E()', " +
                                      "CURRENT_TIMESTAMP)"));
        }
    }

    @Test
    public void testLegacyScopeBackfillRequiresAllTargetColumns()
            throws Exception {
        try (Connection conn = this.dataSource.getConnection();
             Statement statement = conn.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS `scope_guard`");
            statement.execute("CREATE TABLE `scope_guard` (`conn_id` INT)");
            Assert.assertFalse(this.migrator.scopeColumnsExist(conn,
                                                               "scope_guard"));
            statement.execute("ALTER TABLE `scope_guard` ADD COLUMN " +
                              "`graphspace` VARCHAR(48)");
            statement.execute("ALTER TABLE `scope_guard` ADD COLUMN " +
                              "`graph` VARCHAR(48)");
            Assert.assertTrue(this.migrator.scopeColumnsExist(conn,
                                                              "scope_guard"));
            statement.execute("DROP TABLE `scope_guard`");
        }
    }

    @Test
    public void testMysqlUsesDropIndexForLegacyUniqueKeys() {
        Assert.assertEquals("ALTER TABLE `gremlin_collection` DROP INDEX " +
                            "`legacy_name_key`",
                            DatabaseSchemaMigrator.dropUniqueKeySql(
                                    "MySQL", "legacy_name_key"));
    }

    private void assertScopedRows(Connection conn) throws Exception {
        try (Statement statement = conn.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT `conn_id`, `graphspace`, `graph` FROM " +
                     "`execute_history`")) {
            Assert.assertTrue(rows.next());
            Assert.assertNull(rows.getObject(1));
            Assert.assertEquals("DEFAULT", rows.getString(2));
            Assert.assertEquals("legacygraph", rows.getString(3));
        }
    }

    private static void prepareLegacyDatabase() {
        Path database = Paths.get("target/legacy-schema-startup.mv.db");
        try {
            Files.createDirectories(database.getParent());
            Files.deleteIfExists(database);
            try (Connection conn = DriverManager.getConnection(JDBC_URL,
                                                               "sa", "");
                 Statement statement = conn.createStatement()) {
                ScriptUtils.executeSqlScript(conn, new FileSystemResource(
                        Paths.get("src/test/resources/database/schema.sql")));
                statement.execute("INSERT INTO `graph_connection` " +
                                  "(`name`, `graph`, `host`, `port`, " +
                                  "`create_time`) VALUES ('legacy', " +
                                  "'legacygraph', 'localhost', 8080, " +
                                  "CURRENT_TIMESTAMP)");
                statement.execute("INSERT INTO `execute_history` " +
                                  "(`execute_type`, `content`, " +
                                  "`execute_status`, `duration`, " +
                                  "`create_time`) VALUES " +
                                  "(0, 'g.V()', 1, 10, CURRENT_TIMESTAMP)");
                statement.execute("INSERT INTO `gremlin_collection` " +
                                  "(`name`, `content`, `create_time`) VALUES " +
                                  "('saved', 'g.E()', CURRENT_TIMESTAMP)");
            }
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("org.apache.hugegraph.mapper.query")
    @Import({DatabaseSchemaMigrator.class, MybatisPlusConfig.class,
             ExecuteHistoryService.class, GremlinCollectionService.class})
    public static class TestConfiguration {
    }
}
