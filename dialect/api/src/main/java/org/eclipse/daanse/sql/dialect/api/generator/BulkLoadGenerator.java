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
package org.eclipse.daanse.sql.dialect.api.generator;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.model.schema.TableReference;

/**
 * Generates the database-native bulk-load statement for a local delimited file
 * (H2 {@code CSVREAD}, DuckDB {@code read_csv}, PostgreSQL {@code COPY}, MySQL
 * {@code LOAD DATA LOCAL INFILE}). The default is empty; callers fall back to
 * batched INSERTs. The file path must be visible to whatever executes the
 * statement.
 */
public interface BulkLoadGenerator {

    /**
     * The bulk-load statement for a delimited text file whose data begins after
     * {@code skipLines} lines. Values are read by position; the caller has already
     * created the table with {@code columns} in this order.
     *
     * @param skipLines   lines before the first data line, headers included
     * @param nullLiteral text that stands for an absent value
     * @return the executable SQL statement, or empty when unsupported
     */
    default Optional<String> loadFromDelimitedFile(TableReference target, List<String> columns, Path csvFile,
            char delimiter, int skipLines, String nullLiteral) {
        return Optional.empty();
    }

    /** Whether this dialect generates native bulk-load statements. */
    default boolean supportsBulkLoad() {
        return false;
    }
}
