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
package org.eclipse.daanse.sql.dialect.api;

import java.util.Locale;

/**
 * How an engine canonicalizes unquoted identifiers when storing and looking
 * them up in the catalog.
 */
public enum IdentifierCaseFolding {

    /**
     * Unquoted identifiers are folded to upper case (Oracle, Db2, H2-default,
     * Snowflake, SAP HANA).
     */
    UPPER,

    /**
     * Unquoted identifiers are folded to lower case (PostgreSQL, Redshift,
     * Greenplum).
     */
    LOWER,

    /**
     * Unquoted identifiers are stored exactly as written (MySQL with case-sensitive
     * collation, SQL Server).
     */
    PRESERVE;

    /**
     * @param name identifier to fold; may be {@code null}
     * @return the input folded according to this policy, or {@code null} if input
     *         was {@code null}
     */
    public String fold(String name) {
        if (name == null) {
            return null;
        }
        return switch (this) {
        case UPPER -> name.toUpperCase(Locale.ROOT);
        case LOWER -> name.toLowerCase(Locale.ROOT);
        case PRESERVE -> name;
        };
    }

    /**
     * @param name identifier to test; may be {@code null}
     * @return {@code true} when {@code name} already matches its folded form (i.e.
     *         quoting it would not change resolution)
     */
    public boolean isCanonical(String name) {
        return name != null && name.equals(fold(name));
    }
}
