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

import java.util.List;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.guard.api.elements.DatabaseCatalog;

public interface SqlGuardFactory {

    /**
     * Creates a {@link SqlGuard} bound to a whitelisted catalog and a function
     * whitelist.
     *
     * @param currentCatalogName      the catalog assumed for unqualified names.
     * @param currentSchemaName       the schema assumed for unqualified names.
     * @param databaseCatalog         the catalog, schemas, tables and columns that
     *                                queries are allowed to reference; anything not
     *                                described here is rejected.
     * @param whitelistFunctionsPatterns the functions a query may call, as a list of
     *            <strong>full-match, case-sensitive</strong> regular expressions
     *            (evaluated with {@link java.util.regex.Pattern#matches}). A pattern
     *            is tested against every function name appearing anywhere in the
     *            query. A function passes only if at least one pattern matches its
     *            whole name; a single unmatched function rejects the statement.
     *            <ul>
     *            <li>An <strong>empty list rejects every function</strong> — this is
     *            the secure default.</li>
     *            <li>{@code "sum"} matches only {@code sum}, not {@code SUM}; use
     *            {@code "(?i)sum"} or list both spellings for case-insensitivity.</li>
     *            <li>{@code List.of(".*")} allows <em>every</em> function and is
     *            strongly discouraged: it re-enables side-effecting and
     *            denial-of-service functions (e.g. {@code nextval}, {@code setval},
     *            {@code pg_sleep}, {@code dblink}, {@code pg_read_file},
     *            {@code xp_cmdshell}).</li>
     *            </ul>
     *            Keep this list as tight as the use case allows. See
     *            {@code guard/README.md} for the rationale and guidance.
     * @param dialect                 the SQL dialect used to render the rewritten,
     *                                fully qualified output.
     * @return a guard that enforces the read-only contract described on
     *         {@link SqlGuard#guard(String)}.
     */
    SqlGuard create(String currentCatalogName, String currentSchemaName, DatabaseCatalog databaseCatalog, List<String> whitelistFunctionsPatterns, Dialect dialect);
}
