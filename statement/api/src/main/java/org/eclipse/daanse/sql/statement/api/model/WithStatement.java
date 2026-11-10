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

/**
 * A {@code WITH [RECURSIVE] cte1 AS (...), ... <body>} statement: one or more
 * {@link CommonTableExpression}s prefixed onto a body statement. The
 * {@code recursive} flag is honoured by the dialect (it emits the
 * {@code RECURSIVE} keyword only where supported).
 *
 * @param ctes          the common-table expressions (at least one)
 * @param recursive     whether the CTEs are recursive
 * @param body          the main statement that may reference the CTEs by name
 * @param footerComment an optional trailing explanatory comment, appended on
 *                      its own line at the very end of the rendered statement —
 *                      only when the renderer is asked to emit comments; never
 *                      part of the executed SQL
 */
public record WithStatement(List<CommonTableExpression> ctes, boolean recursive, Statement body,
        Optional<String> footerComment) implements Statement {

    /** Backwards-compatible form without a footer comment. */
    public WithStatement(List<CommonTableExpression> ctes, boolean recursive, Statement body) {
        this(ctes, recursive, body, Optional.empty());
    }
}
