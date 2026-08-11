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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.sql.DataSource;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
public class DatabaseSchemaMigrator implements BeanPostProcessor,
                                               PriorityOrdered {

    private static final int FILE_MAPPING_PATH_LENGTH = 2048;
    private static final int COLLECTION_NAME_LENGTH = 48;
    private static final String DEFAULT_GRAPHSPACE = "DEFAULT";
    private static final String DEFAULT_GRAPH = "hugegraph";
    private static final String GREMLIN_TYPE = "GREMLIN";
    private static final String FILE_MAPPING_TABLE = "file_mapping";
    private static final String FILE_MAPPING_PATH_COLUMN = "path";
    private static final String EXECUTE_HISTORY_TABLE = "execute_history";
    private static final String FAILURE_REASON_COLUMN = "failure_reason";
    private static final String LOAD_TASK_TABLE = "load_task";
    private static final String GREMLIN_COLLECTION_TABLE =
                                "gremlin_collection";
    private static final String GREMLIN_COLLECTION_SCOPE_INDEX =
                                "gremlin_collection_scope_name";
    /*
     * Columns added to `execute_history` after the legacy schema was
     * released. Each entry is {column, type, backfill value or null}.
     */
    private static final String[][] EXECUTE_HISTORY_COLUMNS = {
        {"conn_id", "INT", null},
        {"graphspace", "VARCHAR(48)", null},
        {"graph", "VARCHAR(48)", null},
        {"async_id", "BIGINT", "0"},
        {"text", "TEXT", "''"},
        {"async_status", "TINYINT", "0"}
    };
    /* Columns added to `gremlin_collection` after the legacy schema. */
    private static final String[][] GREMLIN_COLLECTION_COLUMNS = {
        {"conn_id", "INT", null},
        {"graphspace", "VARCHAR(48)", null},
        {"graph", "VARCHAR(48)", null},
        {"type", "VARCHAR(48)", null}
    };
    private static final String[] LOAD_OPTION_CREDENTIALS = {
        "password", "token", "pdToken", "trustStoreToken"
    };
    private static final String[][] REQUIRED_INDEXES = {
        {"execute_history", "execute_history_graph_create_time",
         "graphspace", "graph", "create_time"},
        {"file_mapping", "file_mapping_job_id", "job_id"},
        {"load_task", "load_task_job_id", "job_id"},
        {"job_manager", "job_manager_graph_create_time",
         "graphspace", "graph", "create_time"},
        {"async_task", "async_task_graph", "graphspace", "graph"}
    };
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public int getOrder() {
        // Boot's DataSourceInitializerPostProcessor runs at HIGHEST + 1.
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
            throws BeansException {
        if (!(bean instanceof DataSource)) {
            return bean;
        }
        try (Connection conn = ((DataSource) bean).getConnection()) {
            this.migrate(conn);
        } catch (SQLException e) {
            throw new BeanInitializationException(
                      "Failed to migrate Hubble database schema", e);
        }
        return bean;
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
        this.migrateLegacyQueryScopes(conn);
        this.migrateGremlinCollectionUniqueConstraint(conn);
        this.addMissingIndexes(conn);
        this.removeLegacyLoadTaskCredentials(conn);
    }

    private void migrateLegacyQueryScopes(Connection conn)
            throws SQLException {
        List<LegacyScope> scopes = this.legacyScopes(conn);
        LegacyScope fallback = scopes.isEmpty() ?
                               new LegacyScope(null, DEFAULT_GRAPHSPACE,
                                               DEFAULT_GRAPH) : scopes.get(0);
        int updated = 0;
        if (this.tableExists(conn, EXECUTE_HISTORY_TABLE)) {
            updated += this.backfillScopes(conn, EXECUTE_HISTORY_TABLE, scopes,
                                           fallback);
        }
        if (this.tableExists(conn, GREMLIN_COLLECTION_TABLE)) {
            updated += this.backfillScopes(conn, GREMLIN_COLLECTION_TABLE,
                                           scopes, fallback);
            try (PreparedStatement statement = conn.prepareStatement(
                    "UPDATE `gremlin_collection` SET `type` = ? " +
                    "WHERE `type` IS NULL OR `type` = ''")) {
                statement.setString(1, GREMLIN_TYPE);
                updated += statement.executeUpdate();
            }
        }
        if (updated > 0) {
            log.info("Recovered scope for {} legacy query records", updated);
        }
    }

    private int backfillScopes(Connection conn, String table,
                               List<LegacyScope> scopes,
                               LegacyScope fallback) throws SQLException {
        int updated = 0;
        for (LegacyScope scope : scopes) {
            updated += this.backfillConnectionScope(conn, table, scope);
        }
        try (PreparedStatement statement = conn.prepareStatement(
                String.format("UPDATE `%s` SET `graphspace` = ? " +
                              "WHERE `graphspace` IS NULL OR " +
                              "`graphspace` = ''", table))) {
            statement.setString(1, fallback.graphspace);
            updated += statement.executeUpdate();
        }
        try (PreparedStatement statement = conn.prepareStatement(
                String.format("UPDATE `%s` SET `graph` = ? " +
                              "WHERE `graph` IS NULL OR `graph` = ''",
                              table))) {
            statement.setString(1, fallback.graph);
            updated += statement.executeUpdate();
        }
        return updated;
    }

    private int backfillConnectionScope(Connection conn, String table,
                                        LegacyScope scope) throws SQLException {
        int updated = 0;
        try (PreparedStatement statement = conn.prepareStatement(
                String.format("UPDATE `%s` SET `graphspace` = ? WHERE " +
                              "`conn_id` = ? AND (`graphspace` IS NULL OR " +
                              "`graphspace` = '')", table))) {
            statement.setString(1, scope.graphspace);
            statement.setInt(2, scope.connectionId);
            updated += statement.executeUpdate();
        }
        try (PreparedStatement statement = conn.prepareStatement(
                String.format("UPDATE `%s` SET `graph` = ? WHERE " +
                              "`conn_id` = ? AND (`graph` IS NULL OR " +
                              "`graph` = '')", table))) {
            statement.setString(1, scope.graph);
            statement.setInt(2, scope.connectionId);
            updated += statement.executeUpdate();
        }
        return updated;
    }

    private List<LegacyScope> legacyScopes(Connection conn)
            throws SQLException {
        List<LegacyScope> scopes = new ArrayList<>();
        if (!this.tableExists(conn, "graph_connection") ||
            !this.columnExists(conn, "graph_connection", "id") ||
            !this.columnExists(conn, "graph_connection", "graph")) {
            return scopes;
        }

        boolean hasGraphspace = this.columnExists(conn, "graph_connection",
                                                  "graphspace");
        String sql = hasGraphspace ?
                     "SELECT `id`, `graphspace`, `graph` FROM " +
                     "`graph_connection` ORDER BY `id`" :
                     "SELECT `id`, `graph` FROM `graph_connection` " +
                     "ORDER BY `id`";
        try (Statement statement = conn.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                Integer connectionId = rows.getInt(1);
                String graphspace = hasGraphspace ? rows.getString(2) : null;
                String graph = rows.getString(hasGraphspace ? 3 : 2);
                scopes.add(new LegacyScope(
                        connectionId,
                        blank(graphspace) ? DEFAULT_GRAPHSPACE : graphspace,
                        blank(graph) ? DEFAULT_GRAPH : graph));
            }
        }
        return scopes;
    }

    private void migrateGremlinCollectionUniqueConstraint(Connection conn)
            throws SQLException {
        if (!this.tableExists(conn, GREMLIN_COLLECTION_TABLE) ||
            !this.columnExists(conn, GREMLIN_COLLECTION_TABLE,
                               "graphspace") ||
            !this.columnExists(conn, GREMLIN_COLLECTION_TABLE, "graph") ||
            !this.columnExists(conn, GREMLIN_COLLECTION_TABLE, "name") ||
            !this.columnExists(conn, GREMLIN_COLLECTION_TABLE, "type")) {
            return;
        }

        String product = conn.getMetaData().getDatabaseProductName();
        String normalizedProduct = product.toLowerCase(Locale.ROOT);
        if (!normalizedProduct.contains("h2") &&
            !normalizedProduct.contains("mysql") &&
            !normalizedProduct.contains("mariadb")) {
            log.warn("Skip gremlin_collection unique-key migration for " +
                     "unsupported database product {}", product);
            return;
        }
        this.renameLegacyCollectionCollisions(conn);
        List<String> obsolete = normalizedProduct.contains("h2") ?
                                this.h2ObsoleteUniqueConstraints(conn) :
                                this.mysqlObsoleteUniqueIndexes(conn, product);
        for (String name : obsolete) {
            try (Statement statement = conn.createStatement()) {
                statement.execute(dropUniqueKeySql(product, name));
            }
            log.info("Removed obsolete gremlin_collection unique key {}",
                     name);
        }

        Map<String, List<String>> uniqueIndexes = this.uniqueIndexes(conn,
                                                      GREMLIN_COLLECTION_TABLE);
        if (this.hasScopeUniqueIndex(uniqueIndexes)) {
            return;
        }
        try (Statement statement = conn.createStatement()) {
            statement.execute("CREATE UNIQUE INDEX `" +
                              GREMLIN_COLLECTION_SCOPE_INDEX + "` ON `" +
                              GREMLIN_COLLECTION_TABLE + "`(" +
                              "`graphspace`, `graph`, `name`, `type`)");
        }
        log.info("Added scoped gremlin_collection unique index {}",
                 GREMLIN_COLLECTION_SCOPE_INDEX);
    }

    private void renameLegacyCollectionCollisions(Connection conn)
            throws SQLException {
        List<LegacyCollectionCollision> collisions = new ArrayList<>();
        try (Statement statement = conn.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT `graphspace`, `graph`, `name`, `type` FROM " +
                     "`gremlin_collection` GROUP BY `graphspace`, `graph`, " +
                     "`name`, `type` HAVING COUNT(*) > 1")) {
            while (rows.next()) {
                collisions.add(new LegacyCollectionCollision(
                        rows.getString(1), rows.getString(2),
                        rows.getString(3), rows.getString(4)));
            }
        }

        for (LegacyCollectionCollision collision : collisions) {
            List<LegacyCollectionRow> duplicateRows =
                    this.legacyCollectionRows(conn, collision);
            for (int i = 1; i < duplicateRows.size(); i++) {
                LegacyCollectionRow row = duplicateRows.get(i);
                String name = this.uniqueLegacyCollectionName(conn, collision,
                                                              row);
                try (PreparedStatement statement = conn.prepareStatement(
                        "UPDATE `gremlin_collection` SET `name` = ? " +
                        "WHERE `id` = ?")) {
                    statement.setString(1, name);
                    statement.setInt(2, row.id);
                    statement.executeUpdate();
                }
                log.warn("Renamed colliding legacy saved query {} (id {}) " +
                         "to {} while recovering graph scope", collision.name,
                         row.id, name);
            }
        }
    }

    private List<LegacyCollectionRow> legacyCollectionRows(
            Connection conn, LegacyCollectionCollision collision)
            throws SQLException {
        List<LegacyCollectionRow> rows = new ArrayList<>();
        try (PreparedStatement statement = conn.prepareStatement(
                "SELECT `id`, `conn_id` FROM `gremlin_collection` WHERE " +
                "`graphspace` = ? AND `graph` = ? AND `name` = ? AND " +
                "`type` = ? ORDER BY `id`")) {
            statement.setString(1, collision.graphspace);
            statement.setString(2, collision.graph);
            statement.setString(3, collision.name);
            statement.setString(4, collision.type);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int connectionId = result.getInt(2);
                    rows.add(new LegacyCollectionRow(
                            result.getInt(1),
                            result.wasNull() ? null : connectionId));
                }
            }
        }
        return rows;
    }

    private String uniqueLegacyCollectionName(
            Connection conn, LegacyCollectionCollision collision,
            LegacyCollectionRow row) throws SQLException {
        for (int attempt = 0; ; attempt++) {
            String suffix = "_" + row.id +
                            (attempt == 0 ? "" : "_" + attempt);
            int baseLength = Math.min(collision.name.length(),
                                      COLLECTION_NAME_LENGTH - suffix.length());
            String candidate = collision.name.substring(0, baseLength) + suffix;
            if (!this.legacyCollectionNameExists(conn, collision, row,
                                                 candidate)) {
                return candidate;
            }
        }
    }

    private boolean legacyCollectionNameExists(
            Connection conn, LegacyCollectionCollision collision,
            LegacyCollectionRow row, String name) throws SQLException {
        String connectionPredicate = row.connectionId == null ?
                                     "`conn_id` IS NULL" : "`conn_id` = ?";
        try (PreparedStatement statement = conn.prepareStatement(
                "SELECT 1 FROM `gremlin_collection` WHERE `name` = ? AND " +
                "((`graphspace` = ? AND `graph` = ? AND `type` = ?) OR " +
                connectionPredicate + ")")) {
            statement.setString(1, name);
            statement.setString(2, collision.graphspace);
            statement.setString(3, collision.graph);
            statement.setString(4, collision.type);
            if (row.connectionId != null) {
                statement.setInt(5, row.connectionId);
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private List<String> h2ObsoleteUniqueConstraints(Connection conn)
            throws SQLException {
        List<String> constraints = new ArrayList<>();
        try (PreparedStatement statement = conn.prepareStatement(
                "SELECT `CONSTRAINT_NAME`, `COLUMN_LIST` FROM " +
                "`INFORMATION_SCHEMA`.`CONSTRAINTS` WHERE " +
                "UPPER(`TABLE_NAME`) = ? AND `CONSTRAINT_TYPE` = 'UNIQUE'")) {
            statement.setString(1,
                                GREMLIN_COLLECTION_TABLE.toUpperCase(Locale.ROOT));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (obsoleteUniqueColumns(rows.getString(2))) {
                        constraints.add(rows.getString(1));
                    }
                }
            }
        }
        return constraints;
    }

    private List<String> mysqlObsoleteUniqueIndexes(Connection conn,
                                                      String product)
            throws SQLException {
        String normalized = product.toLowerCase(Locale.ROOT);
        if (!normalized.contains("mysql") &&
            !normalized.contains("mariadb")) {
            log.warn("Skip gremlin_collection unique-key migration for " +
                     "unsupported database product {}", product);
            return new ArrayList<>();
        }
        List<String> indexes = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry :
             this.uniqueIndexes(conn, GREMLIN_COLLECTION_TABLE).entrySet()) {
            if (obsoleteUniqueColumns(String.join(",", entry.getValue()))) {
                indexes.add(entry.getKey());
            }
        }
        return indexes;
    }

    private Map<String, List<String>> uniqueIndexes(Connection conn,
                                                     String table)
            throws SQLException {
        Map<String, SortedMap<Short, String>> columns = new LinkedHashMap<>();
        DatabaseMetaData metaData = conn.getMetaData();
        String[] tables = {table, table.toUpperCase(Locale.ROOT)};
        for (String tableName : tables) {
            try (ResultSet rows = metaData.getIndexInfo(conn.getCatalog(), null,
                                                        tableName, true,
                                                        false)) {
                while (rows.next()) {
                    String index = rows.getString("INDEX_NAME");
                    String column = rows.getString("COLUMN_NAME");
                    if (index == null || column == null ||
                        "PRIMARY".equalsIgnoreCase(index) ||
                        index.toUpperCase(Locale.ROOT).startsWith("PRIMARY_KEY")) {
                        continue;
                    }
                    short position = rows.getShort("ORDINAL_POSITION");
                    columns.computeIfAbsent(index, key -> new TreeMap<>())
                           .put(position, column.toLowerCase(Locale.ROOT));
                }
            }
            if (!columns.isEmpty()) {
                break;
            }
        }
        Map<String, List<String>> indexes = new LinkedHashMap<>();
        for (Map.Entry<String, SortedMap<Short, String>> entry :
             columns.entrySet()) {
            indexes.put(entry.getKey(),
                        new ArrayList<>(entry.getValue().values()));
        }
        return indexes;
    }

    private boolean hasScopeUniqueIndex(Map<String, List<String>> indexes) {
        for (List<String> columns : indexes.values()) {
            if ("graphspace,graph,name,type".equals(String.join(",", columns))) {
                return true;
            }
        }
        return false;
    }

    private static boolean obsoleteUniqueColumns(String columns) {
        String normalized = columns.toLowerCase(Locale.ROOT)
                                   .replace("`", "")
                                   .replace(" ", "");
        return "name".equals(normalized) ||
               "conn_id,name".equals(normalized);
    }

    static String dropUniqueKeySql(String productName, String name) {
        String operation = productName.toLowerCase(Locale.ROOT).contains("h2") ?
                           "DROP CONSTRAINT" : "DROP INDEX";
        return "ALTER TABLE `gremlin_collection` " + operation + " `" +
               name.replace("`", "``") + "`";
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
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

    private void addMissingIndexes(Connection conn) throws SQLException {
        for (String[] definition : REQUIRED_INDEXES) {
            String table = definition[0];
            String index = definition[1];
            if (!this.tableExists(conn, table) ||
                this.indexExists(conn, table, index)) {
                continue;
            }
            boolean columnsExist = true;
            for (int i = 2; i < definition.length; i++) {
                columnsExist &= this.columnExists(conn, table, definition[i]);
            }
            if (!columnsExist) {
                continue;
            }

            StringBuilder columns = new StringBuilder();
            for (int i = 2; i < definition.length; i++) {
                if (columns.length() > 0) {
                    columns.append(", ");
                }
                columns.append('`').append(definition[i]).append('`');
            }
            try (Statement statement = conn.createStatement()) {
                statement.execute(String.format(
                        "CREATE INDEX `%s` ON `%s`(%s)", index, table,
                        columns));
            } catch (SQLException e) {
                if (!this.indexExists(conn, table, index)) {
                    throw e;
                }
                log.info("Index {} on {} was added concurrently", index,
                         table);
                continue;
            }
            log.info("Added index {} on {}", index, table);
        }
    }

    private boolean indexExists(Connection conn, String table, String index)
            throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        String[] tables = {table, table.toUpperCase(Locale.ROOT)};
        for (String tableName : tables) {
            try (ResultSet rs = metaData.getIndexInfo(conn.getCatalog(), null,
                                                      tableName, false,
                                                      false)) {
                while (rs.next()) {
                    if (index.equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
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

    private static final class LegacyScope {

        private final Integer connectionId;
        private final String graphspace;
        private final String graph;

        private LegacyScope(Integer connectionId, String graphspace,
                            String graph) {
            this.connectionId = connectionId;
            this.graphspace = graphspace;
            this.graph = graph;
        }
    }

    private static final class LegacyCollectionCollision {

        private final String graphspace;
        private final String graph;
        private final String name;
        private final String type;

        private LegacyCollectionCollision(String graphspace, String graph,
                                          String name, String type) {
            this.graphspace = graphspace;
            this.graph = graph;
            this.name = name;
            this.type = type;
        }
    }

    private static final class LegacyCollectionRow {

        private final int id;
        private final Integer connectionId;

        private LegacyCollectionRow(int id, Integer connectionId) {
            this.id = id;
            this.connectionId = connectionId;
        }
    }
}
