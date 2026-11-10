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

/**
 * The kind of join in a {@link FromClause.FromJoin}.
 * <p>
 * Only {@link #INNER} may fall back to an old-style comma join (condition
 * pushed into {@code WHERE}) when the dialect does not allow
 * {@code JOIN ... ON}; every outer kind always renders the ANSI keyword.
 */
public enum JoinKind {
    INNER, LEFT,
    /**
     * Right outer join, always rendered as ANSI {@code right join ... on}. There is
     * no comma fallback and no emulation: a dialect that does not support RIGHT
     * JOIN fails loudly at the database.
     */
    RIGHT,
    /**
     * Full outer join, always rendered as ANSI {@code full join ... on}. There is
     * no comma fallback and no emulation: a dialect that does not support FULL JOIN
     * fails loudly at the database.
     */
    FULL, CROSS
}
