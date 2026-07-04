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
package org.eclipse.daanse.sql.statement.api.model;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.api.schema.TableReference;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;

/**
 * An {@code INSERT INTO table (cols...)} statement, sourcing rows either from
 * inline {@code VALUES} or from a sub-query ({@code INSERT ... SELECT}).
 *
 * @param table         the target table (shared jdbc.db identifier)
 * @param columns       target column names (unquoted); empty means positional
 *                      insert
 * @param rows          inline value rows (each row's size should match
 *                      {@code columns}); used when {@code source} is empty
 * @param source        a sub-query to insert from; when present, {@code rows}
 *                      must be empty
 * @param footerComment an optional trailing explanatory comment, appended on
 *                      its own line at the very end of the rendered statement —
 *                      only when the renderer is asked to emit comments; never
 *                      part of the executed SQL
 */
public record InsertStatement(TableReference table, List<String> columns, List<List<SqlExpression>> rows,
        Optional<Statement> source, Optional<String> footerComment) implements Statement {

    /** Backwards-compatible form without a footer comment. */
    public InsertStatement(TableReference table, List<String> columns, List<List<SqlExpression>> rows,
            Optional<Statement> source) {
        this(table, columns, rows, source, Optional.empty());
    }
}
