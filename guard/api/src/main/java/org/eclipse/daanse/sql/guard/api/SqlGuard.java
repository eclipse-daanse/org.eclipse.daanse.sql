/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.sql.guard.api;

import org.eclipse.daanse.sql.guard.api.exception.GuardException;

/**
 * Narrows an untrusted SQL string to a read-only query over an explicitly
 * whitelisted catalog. The guard is the trust boundary between caller-supplied
 * SQL and the database.
 */
public interface SqlGuard {

    /**
     * Validates {@code sql} and, if it is accepted, returns an equivalent read-only
     * {@code SELECT} with every table and column fully qualified against the
     * whitelisted catalog and rendered in the configured dialect.
     * <p>
     * The statement is rejected with a {@link GuardException} unless it is a plain,
     * read-only {@code SELECT}. In particular the guard rejects:
     * <ul>
     * <li>any non-{@code SELECT} statement (INSERT/UPDATE/DELETE/DDL/…) —
     * {@link org.eclipse.daanse.sql.guard.api.exception.UnallowedStatementTypeGuardException};</li>
     * <li>a data-modifying CTE, e.g. {@code WITH x AS (UPDATE foo ...) SELECT ...};</li>
     * <li>{@code SELECT ... INTO} (writes a table) and {@code SELECT ... FOR UPDATE}
     * / {@code FOR SHARE} (row locks);</li>
     * <li>any reference to a table or column outside the whitelisted catalog —
     * {@link org.eclipse.daanse.sql.guard.api.exception.UnresolvableObjectsGuardException};</li>
     * <li>any function not permitted by the configured function whitelist (see
     * {@link SqlGuardFactory#create});</li>
     * <li>empty input
     * ({@link org.eclipse.daanse.sql.guard.api.exception.EmptyStatementGuardException})
     * and unparsable input
     * ({@link org.eclipse.daanse.sql.guard.api.exception.UnparsableStatementGuardException}).</li>
     * </ul>
     * The guard fails closed: any unexpected error during validation results in a
     * {@link GuardException} rather than an approved statement.
     *
     * @param sql the untrusted SQL to validate.
     * @return the rewritten, fully qualified read-only statement.
     * @throws GuardException if the statement is not an allowed read-only query.
     */
    String guard(String sql) throws GuardException;

}
