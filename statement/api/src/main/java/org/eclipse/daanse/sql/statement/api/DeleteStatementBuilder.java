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

import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.DeleteStatement;

/** Mutable, dialect-free builder for a {@link DeleteStatement}. */
public final class DeleteStatementBuilder {

    private TableReference table;
    private final List<Predicate> filters = new ArrayList<>();
    private String footerComment;

    private DeleteStatementBuilder() {
    }

    public static DeleteStatementBuilder create() {
        return new DeleteStatementBuilder();
    }

    public DeleteStatementBuilder from(String table) {
        this.table = new TableReference(table);
        return this;
    }

    public DeleteStatementBuilder from(String schema, String table) {
        this.table = From.tableRef(schema, table);
        return this;
    }

    public DeleteStatementBuilder from(TableReference table) {
        this.table = table;
        return this;
    }

    public DeleteStatementBuilder where(Predicate predicate) {
        filters.add(predicate);
        return this;
    }

    /**
     * Sets an optional trailing explanatory comment, appended at the very end
     * (emitted only when comments are on).
     */
    public DeleteStatementBuilder footerComment(String comment) {
        this.footerComment = comment;
        return this;
    }

    public DeleteStatement build() {
        return new DeleteStatement(Objects.requireNonNull(table, "table"), List.copyOf(filters),
                java.util.Optional.ofNullable(footerComment));
    }
}
