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

package org.apache.hugegraph.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
public class DatabaseSchemaMigrator implements ApplicationRunner {

    private static final int FILE_MAPPING_PATH_LENGTH = 2048;
    private static final String FILE_MAPPING_TABLE = "file_mapping";
    private static final String FILE_MAPPING_PATH_COLUMN = "path";
    private static final String EXECUTE_HISTORY_TABLE = "execute_history";
    private static final String FAILURE_REASON_COLUMN = "failure_reason";

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection conn = this.dataSource.getConnection()) {
            this.migrate(conn);
        }
    }

    public void migrate(Connection conn) throws SQLException {
        int currentLength = this.columnSize(conn, FILE_MAPPING_TABLE,
                                            FILE_MAPPING_PATH_COLUMN);
        if (currentLength > 0 && currentLength < FILE_MAPPING_PATH_LENGTH) {
            this.migrateFileMappingPath(conn, currentLength);
        }

        this.migrateExecuteHistoryFailureReason(conn);
    }

    private void migrateFileMappingPath(Connection conn, int currentLength)
            throws SQLException {
        String sql = this.alterFileMappingPathSql(
                     conn.getMetaData().getDatabaseProductName());
        if (sql == null) {
            log.warn("Skip file_mapping.path migration for unsupported " +
                     "database product {}",
                     conn.getMetaData().getDatabaseProductName());
            return;
        }

        try (Statement statement = conn.createStatement()) {
            statement.execute(sql);
        }
        log.info("Migrated file_mapping.path from {} to VARCHAR({})",
                 currentLength, FILE_MAPPING_PATH_LENGTH);
    }

    private void migrateExecuteHistoryFailureReason(Connection conn)
            throws SQLException {
        if (!this.tableExists(conn, EXECUTE_HISTORY_TABLE) ||
            this.columnSize(conn, EXECUTE_HISTORY_TABLE,
                            FAILURE_REASON_COLUMN) > 0) {
            return;
        }

        String product = conn.getMetaData().getDatabaseProductName()
                             .toLowerCase(Locale.ROOT);
        if (!product.contains("h2") && !product.contains("mysql") &&
            !product.contains("mariadb")) {
            log.warn("Skip execute_history.failure_reason migration for " +
                     "unsupported database product {}",
                     conn.getMetaData().getDatabaseProductName());
            return;
        }

        try (Statement statement = conn.createStatement()) {
            statement.execute("ALTER TABLE `execute_history` ADD COLUMN " +
                              "`failure_reason` VARCHAR(64) DEFAULT NULL");
        }
        log.info("Added execute_history.failure_reason VARCHAR(64)");
    }

    private boolean tableExists(Connection conn, String table)
            throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        String[] tables = {table, table.toUpperCase(Locale.ROOT)};
        for (String tableName : tables) {
            try (ResultSet rs = metaData.getTables(null, null, tableName,
                                                   new String[]{"TABLE"})) {
                if (rs.next()) {
                    return true;
                }
            }
        }
        return false;
    }

    private int columnSize(Connection conn, String table, String column)
            throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        String[] tables = {table, table.toUpperCase(Locale.ROOT)};
        String[] columns = {column, column.toUpperCase(Locale.ROOT)};
        for (String tableName : tables) {
            for (String columnName : columns) {
                try (ResultSet rs = metaData.getColumns(null, null, tableName,
                                                        columnName)) {
                    if (rs.next()) {
                        return rs.getInt("COLUMN_SIZE");
                    }
                }
            }
        }
        return 0;
    }

    private String alterFileMappingPathSql(String productName) {
        String product = productName.toLowerCase(Locale.ROOT);
        if (product.contains("h2")) {
            return "ALTER TABLE `file_mapping` ALTER COLUMN `path` " +
                   "VARCHAR(2048) NOT NULL";
        }
        if (product.contains("mysql") || product.contains("mariadb")) {
            return "ALTER TABLE `file_mapping` MODIFY COLUMN `path` " +
                   "VARCHAR(2048) NOT NULL";
        }
        return null;
    }
}
