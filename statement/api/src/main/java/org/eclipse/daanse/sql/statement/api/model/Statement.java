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

import java.util.Optional;

/**
 * A renderable, immutable SQL statement. May be a read
 * ({@link SelectStatement}, or a {@link SetOperation} such as
 * {@code UNION ALL}) or a write
 * ({@link InsertStatement}/{@link UpdateStatement}/{@link DeleteStatement}).
 */
public sealed interface Statement
        permits SelectStatement, SetOperation, InsertStatement, UpdateStatement, DeleteStatement, WithStatement {

    /**
     * An optional trailing explanatory comment. The renderer appends it as a
     * {@code /* ... *}{@code /} block on its own line at the very end of the
     * rendered statement — only when comment emission is on; with comments off the
     * rendered SQL is byte-identical to a statement without a footer (cache-key
     * stability). Multi-line text stays multi-line inside the block.
     */
    Optional<String> footerComment();
}
