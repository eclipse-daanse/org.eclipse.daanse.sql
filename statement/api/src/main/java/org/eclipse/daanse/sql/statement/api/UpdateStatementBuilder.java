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

import org.eclipse.daanse.jdbc.db.api.schema.TableReference;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.eclipse.daanse.sql.statement.api.model.UpdateStatement;
import org.eclipse.daanse.sql.statement.api.model.UpdateStatement.Assignment;

/** Mutable, dialect-free builder for an {@link UpdateStatement}. */
public final class UpdateStatementBuilder {

    private TableReference table;
    private final List<Assignment> assignments = new ArrayList<>();
    private final List<Predicate> filters = new ArrayList<>();
    private String footerComment;

    private UpdateStatementBuilder() {
    }

    public static UpdateStatementBuilder create() {
        return new UpdateStatementBuilder();
    }

    public UpdateStatementBuilder table(String table) {
        this.table = new TableReference(table);
        return this;
    }

    public UpdateStatementBuilder table(String schema, String table) {
        this.table = From.tableRef(schema, table);
        return this;
    }

    public UpdateStatementBuilder table(TableReference table) {
        this.table = table;
        return this;
    }

    /** Adds a {@code SET column = value} assignment. */
    public UpdateStatementBuilder set(String column, SqlExpression value) {
        assignments.add(new Assignment(column, value));
        return this;
    }

    public UpdateStatementBuilder where(Predicate predicate) {
        filters.add(predicate);
        return this;
    }

    /**
     * Sets an optional trailing explanatory comment, appended at the very end
     * (emitted only when comments are on).
     */
    public UpdateStatementBuilder footerComment(String comment) {
        this.footerComment = comment;
        return this;
    }

    public UpdateStatement build() {
        return new UpdateStatement(Objects.requireNonNull(table, "table"), List.copyOf(assignments),
                List.copyOf(filters), java.util.Optional.ofNullable(footerComment));
    }
}
