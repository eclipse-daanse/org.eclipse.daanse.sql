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
package org.eclipse.daanse.sql.statement.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.eclipse.daanse.sql.statement.api.model.InsertStatement;
import org.eclipse.daanse.sql.statement.api.model.Statement;

/**
 * Mutable, dialect-free builder for an {@link InsertStatement} — either
 * {@code INSERT ...
 * VALUES} (via {@link #addRow}) or {@code INSERT ... SELECT} (via
 * {@link #fromSelect}).
 */
public final class InsertStatementBuilder {

    private TableReference table;
    private final List<String> columns = new ArrayList<>();
    private final List<List<SqlExpression>> rows = new ArrayList<>();
    private Statement source;
    private String footerComment;

    private InsertStatementBuilder() {
    }

    public static InsertStatementBuilder create() {
        return new InsertStatementBuilder();
    }

    public InsertStatementBuilder into(String table) {
        this.table = new TableReference(table);
        return this;
    }

    public InsertStatementBuilder into(String schema, String table) {
        this.table = From.tableRef(schema, table);
        return this;
    }

    public InsertStatementBuilder into(TableReference table) {
        this.table = table;
        return this;
    }

    public InsertStatementBuilder columns(String... names) {
        columns.addAll(List.of(names));
        return this;
    }

    /** Adds one VALUES row; its length should match {@link #columns(String...)}. */
    public InsertStatementBuilder addRow(SqlExpression... values) {
        rows.add(List.of(values));
        return this;
    }

    /** Sources the rows from a sub-query ({@code INSERT ... SELECT}). */
    public InsertStatementBuilder fromSelect(Statement source) {
        this.source = source;
        return this;
    }

    /**
     * Sets an optional trailing explanatory comment, appended at the very end
     * (emitted only when comments are on).
     */
    public InsertStatementBuilder footerComment(String comment) {
        this.footerComment = comment;
        return this;
    }

    public InsertStatement build() {
        if (source != null && !rows.isEmpty()) {
            throw new IllegalStateException("insert has both VALUES rows and a SELECT source");
        }
        return new InsertStatement(Objects.requireNonNull(table, "table"), List.copyOf(columns), List.copyOf(rows),
                Optional.ofNullable(source), Optional.ofNullable(footerComment));
    }
}
