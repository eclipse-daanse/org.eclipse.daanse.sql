/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.sql.dialect.api.type;

/** Identifier quote characters supported by JDBC engines. */
public enum QuoteStyle {

    /** ANSI standard double quotes ({@code "name"}) — Postgres, Oracle, Db2. */
    DOUBLE_QUOTE("\"", "\""),

    /** MySQL/MariaDB-style backticks ({@code `name`}). */
    BACKTICK("`", "`"),

    /** SQL Server / Sybase delimited identifiers ({@code [name]}). */
    SQUARE_BRACKET("[", "]"),

    /** No quoting — emitted SQL exposes the identifier verbatim. */
    NONE("", "");

    private final String openQuote;
    private final String closeQuote;

    QuoteStyle(String openQuote, String closeQuote) {
        this.openQuote = openQuote;
        this.closeQuote = closeQuote;
    }

    /** @return the opening quote character (empty for {@link #NONE}) */
    public String openQuote() {
        return openQuote;
    }

    /** @return the closing quote character (empty for {@link #NONE}) */
    public String closeQuote() {
        return closeQuote;
    }

    /**
     * @param identifier identifier to wrap; may be {@code null}
     * @return identifier wrapped in this style's quotes; the input unchanged when
     *         this style is {@link #NONE} or {@code identifier} is {@code null}
     */
    public String quote(String identifier) {
        if (this == NONE || identifier == null) {
            return identifier;
        }
        return openQuote + identifier + closeQuote;
    }

    /**
     * @param buf        the {@link StringBuilder} to append to
     * @param identifier identifier to append; {@code null} appends nothing
     */
    public void quote(StringBuilder buf, String identifier) {
        if (identifier == null) {
            return;
        }
        buf.append(openQuote).append(identifier).append(closeQuote);
    }
}
