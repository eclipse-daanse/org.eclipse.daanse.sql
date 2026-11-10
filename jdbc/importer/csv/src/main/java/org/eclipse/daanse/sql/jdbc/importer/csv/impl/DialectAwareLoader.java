/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.sql.jdbc.importer.csv.impl;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads a delimited file with the dialect's native bulk-load statement, if it
 * has one; otherwise the caller falls back to batched INSERTs.
 */
final class DialectAwareLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(DialectAwareLoader.class);

    private DialectAwareLoader() {
        // static access only
    }

    /**
     * @param skipLines lines before the first data line
     * @return whether the file was loaded; {@code false} means the caller has to
     *         do it row by row
     */
    static boolean loadNatively(Connection connection, Dialect dialect, TableReference target,
            List<ColumnDefinition> columns, Path file, char delimiter, int skipLines, String nullLiteral)
            throws SQLException {
        if (!dialect.bulkLoadGenerator().supportsBulkLoad()) {
            return false;
        }
        Optional<String> statement = dialect.bulkLoadGenerator().loadFromDelimitedFile(target,
                columns.stream().map(column -> column.column().name()).toList(), file, delimiter, skipLines, nullLiteral);
        if (statement.isEmpty()) {
            LOGGER.debug("{} has no bulk load for a file with {} leading lines; loading {} row by row",
                    dialect.name(), skipLines, file.getFileName());
            return false;
        }
        long started = System.currentTimeMillis();
        try (Statement direct = connection.createStatement()) {
            direct.execute(statement.get());
        } catch (SQLException e) {
            // Typically the server cannot see the file or local reads are off.
            LOGGER.warn("{} refused to read {} itself ({}); loading row by row", dialect.name(), file.getFileName(),
                    e.getMessage());
            // Truncate whatever the failed attempt left, or the fallback load adds up.
            try (Statement direct = connection.createStatement()) {
                direct.executeUpdate(dialect.ddlGenerator().truncate(target));
            }
            return false;
        }
        LOGGER.info("{} read {} itself in {} ms", dialect.name(), file.getFileName(),
                System.currentTimeMillis() - started);
        return true;
    }
}
