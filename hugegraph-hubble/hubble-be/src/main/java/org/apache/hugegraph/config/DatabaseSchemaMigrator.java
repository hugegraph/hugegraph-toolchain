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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
public class DatabaseSchemaMigrator implements ApplicationRunner {

    private static final int FILE_MAPPING_PATH_LENGTH = 2048;
    private static final String FILE_MAPPING_TABLE = "file_mapping";
    private static final String FILE_MAPPING_PATH_COLUMN = "path";
    private static final String EXECUTE_HISTORY_TABLE = "execute_history";
    private static final String FAILURE_REASON_COLUMN = "failure_reason";
    private static final String LOAD_TASK_TABLE = "load_task";
    private static final String GREMLIN_COLLECTION_TABLE =
                                "gremlin_collection";
    /*
     * Columns added to `execute_history` after the legacy schema was
     * released. Each entry is {column, type, backfill value or null}.
     */
    private static final String[][] EXECUTE_HISTORY_COLUMNS = {
        {"conn_id", "INT", null},
        {"graphspace", "VARCHAR(48)", "''"},
        {"graph", "VARCHAR(48)", "''"},
        {"async_id", "BIGINT", "0"},
        {"text", "TEXT", "''"},
        {"async_status", "TINYINT", "0"}
    };
    /* Columns added to `gremlin_collection` after the legacy schema. */
    private static final String[][] GREMLIN_COLLECTION_COLUMNS = {
        {"conn_id", "INT", null},
        {"graphspace", "VARCHAR(48)", "''"},
        {"graph", "VARCHAR(48)", "''"},
        {"type", "VARCHAR(48)", "''"}
    };
    private static final String[] LOAD_OPTION_CREDENTIALS = {
        "password", "token", "pdToken", "trustStoreToken"
    };
    private static final ObjectMapper JSON = new ObjectMapper();

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
        this.addMissingColumns(conn, EXECUTE_HISTORY_TABLE,
                               EXECUTE_HISTORY_COLUMNS);
        this.addMissingColumns(conn, GREMLIN_COLLECTION_TABLE,
                               GREMLIN_COLLECTION_COLUMNS);
        this.removeLegacyLoadTaskCredentials(conn);
    }

    /**
     * Add the columns a legacy Hubble database is missing. `CREATE TABLE IF
     * NOT EXISTS` is a no-op against a pre-existing table, so an H2 file
     * created by an older Hubble keeps its old column set and every query
     * touching the newer columns fails. Each column is added nullable and
     * then backfilled, which keeps the statement portable and idempotent:
     * on a fresh database every column already exists and nothing is run.
     */
    private void addMissingColumns(Connection conn, String table,
                                   String[][] columns) throws SQLException {
        if (!this.tableExists(conn, table)) {
            return;
        }

        String product = conn.getMetaData().getDatabaseProductName()
                             .toLowerCase(Locale.ROOT);
        if (!product.contains("h2") && !product.contains("mysql") &&
            !product.contains("mariadb")) {
            log.warn("Skip {} column migration for unsupported database " +
                     "product {}", table,
                     conn.getMetaData().getDatabaseProductName());
            return;
        }

        for (String[] column : columns) {
            String name = column[0];
            boolean added = false;
            if (!this.columnExists(conn, table, name)) {
                try (Statement statement = conn.createStatement()) {
                    statement.execute(String.format(
                            "ALTER TABLE `%s` ADD COLUMN `%s` %s DEFAULT NULL",
                            table, name, column[1]));
                }
                added = true;
            }
            // Backfill unconditionally. The ALTER and the UPDATE commit
            // separately, so a crash between them would leave the column
            // present but permanently NULL: the next startup would see the
            // column already exists and skip the backfill for good. The
            // statement only touches NULL rows, so repeating it is a no-op.
            if (column[2] != null) {
                try (Statement statement = conn.createStatement()) {
                    statement.executeUpdate(String.format(
                            "UPDATE `%s` SET `%s` = %s WHERE `%s` IS NULL",
                            table, name, column[2], name));
                }
            }
            if (added) {
                log.info("Added {}.{} {}", table, name, column[1]);
            }
        }
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

    private void removeLegacyLoadTaskCredentials(Connection conn)
            throws SQLException {
        if (!this.tableExists(conn, LOAD_TASK_TABLE)) {
            return;
        }

        int updated = 0;
        try (Statement select = conn.createStatement();
             ResultSet rows = select.executeQuery(
                     "SELECT `id`, `options` FROM `load_task`");
             PreparedStatement update = conn.prepareStatement(
                     "UPDATE `load_task` SET `options` = ? WHERE `id` = ?")) {
            while (rows.next()) {
                int id = rows.getInt(1);
                String options = rows.getString(2);
                String sanitized = this.removeCredentials(options, id);
                if (sanitized == null) {
                    continue;
                }
                update.setString(1, sanitized);
                update.setInt(2, id);
                update.addBatch();
                updated++;
            }
            if (updated > 0) {
                update.executeBatch();
            }
        }
        if (updated > 0) {
            log.info("Removed credentials from {} legacy load tasks", updated);
        }
    }

    private String removeCredentials(String options, int id)
            throws SQLException {
        try {
            JsonNode parsed = JSON.readTree(options);
            if (!(parsed instanceof ObjectNode)) {
                throw new SQLException("Invalid load task options for task " +
                                       id);
            }
            ObjectNode object = (ObjectNode) parsed;
            boolean changed = false;
            for (String credential : LOAD_OPTION_CREDENTIALS) {
                changed |= object.remove(credential) != null;
            }
            return changed ? JSON.writeValueAsString(object) : null;
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to sanitize load task options for " +
                                   "task " + id, e);
        }
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

    private boolean columnExists(Connection conn, String table, String column)
            throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        String[] tables = {table, table.toUpperCase(Locale.ROOT)};
        String[] columns = {column, column.toUpperCase(Locale.ROOT)};
        for (String tableName : tables) {
            for (String columnName : columns) {
                try (ResultSet rs = metaData.getColumns(null, null, tableName,
                                                        columnName)) {
                    if (rs.next()) {
                        return true;
                    }
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
