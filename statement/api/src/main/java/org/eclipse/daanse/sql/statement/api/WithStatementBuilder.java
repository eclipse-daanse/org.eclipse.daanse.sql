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

import org.eclipse.daanse.sql.statement.api.model.CommonTableExpression;
import org.eclipse.daanse.sql.statement.api.model.Statement;
import org.eclipse.daanse.sql.statement.api.model.WithStatement;

/**
 * Mutable, dialect-free builder for a {@link WithStatement}
 * ({@code WITH ... <body>}).
 */
public final class WithStatementBuilder {

    private final List<CommonTableExpression> ctes = new ArrayList<>();
    private boolean recursive;
    private Statement body;
    private String footerComment;

    private WithStatementBuilder() {
    }

    public static WithStatementBuilder create() {
        return new WithStatementBuilder();
    }

    /**
     * Adds a common-table expression. Reference it from the body via
     * {@code From.table(name, alias)}.
     */
    public WithStatementBuilder cte(String name, Statement query) {
        ctes.add(new CommonTableExpression(name, List.of(), query));
        return this;
    }

    /**
     * Adds a CTE with an explicit column list (recommended/required for recursive
     * CTEs).
     */
    public WithStatementBuilder cte(String name, List<String> columns, Statement query) {
        ctes.add(new CommonTableExpression(name, List.copyOf(columns), query));
        return this;
    }

    /**
     * Marks the CTEs as recursive (emitted as {@code WITH RECURSIVE} where the
     * dialect supports it).
     */
    public WithStatementBuilder recursive(boolean value) {
        this.recursive = value;
        return this;
    }

    /** Sets the main statement that may reference the CTEs. */
    public WithStatementBuilder body(Statement body) {
        this.body = body;
        return this;
    }

    /**
     * Sets an optional trailing explanatory comment, appended at the very end
     * (emitted only when comments are on).
     */
    public WithStatementBuilder footerComment(String comment) {
        this.footerComment = comment;
        return this;
    }

    public WithStatement build() {
        if (ctes.isEmpty()) {
            throw new IllegalStateException("WITH requires at least one common-table expression");
        }
        return new WithStatement(List.copyOf(ctes), recursive, Objects.requireNonNull(body, "body"),
                java.util.Optional.ofNullable(footerComment));
    }
}
