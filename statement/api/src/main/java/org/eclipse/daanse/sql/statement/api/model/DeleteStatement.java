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

import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;

/**
 * A {@code DELETE FROM table [WHERE ...]} statement.
 *
 * @param table         the target table (shared sql.model identifier)
 * @param filters       {@code WHERE} predicates, combined with {@code AND}
 *                      (empty = delete all)
 * @param footerComment an optional trailing explanatory comment, appended on
 *                      its own line at the very end of the rendered statement —
 *                      only when the renderer is asked to emit comments; never
 *                      part of the executed SQL
 */
public record DeleteStatement(TableReference table, List<Predicate> filters, Optional<String> footerComment)
        implements Statement {

    /** Backwards-compatible form without a footer comment. */
    public DeleteStatement(TableReference table, List<Predicate> filters) {
        this(table, filters, Optional.empty());
    }
}
