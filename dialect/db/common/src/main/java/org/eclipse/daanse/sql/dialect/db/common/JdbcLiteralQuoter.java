/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.common;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

/**
 * Default SQL literal quoting for the standard scalar types. Date and timestamp
 * delegate to {@link Hooks} for the protected overload that subclasses extend.
 */
final class JdbcLiteralQuoter {

    /** Subclass-overridable protected hooks the dialect provides via callbacks. */
    interface Hooks {
        void quoteDateLiteral(StringBuilder buf, Date date);

        void quoteTimestampLiteral(StringBuilder buf, String value, Timestamp timestamp);
    }

    private final Hooks hooks;

    JdbcLiteralQuoter(Hooks hooks) {
        this.hooks = hooks;
    }

    void quoteString(StringBuilder buf, String s) {
        DialectUtil.singleQuoteString(s, buf);
    }

    void quoteNumeric(StringBuilder buf, String value) {
        buf.append(value);
    }

    StringBuilder quoteDecimal(CharSequence value) {
        return new StringBuilder(value);
    }

    void quoteBoolean(StringBuilder buf, String value) {
        // Boolean-typed levels are frequently stored physically as 0/1 (e.g. SMALLINT, for
        // portability — DuckDB reads such a column via getInt). Accept the numeric spelling and
        // emit it verbatim so the predicate matches the integer-backed column, rather than
        // rejecting it as an illegal boolean literal.
        if (value.equals("0") || value.equals("1")) {
            buf.append(value);
            return;
        }
        if (!value.equalsIgnoreCase("TRUE") && !(value.equalsIgnoreCase("FALSE"))) {
            throw new NumberFormatException("Illegal BOOLEAN literal:  " + value);
        }
        buf.append(value);
    }

    void quoteDate(StringBuilder buf, String value) {
        Date date;
        try {
            date = Date.valueOf(value);
        } catch (IllegalArgumentException ex) {
            try {
                date = new Date(Timestamp.valueOf(value).getTime());
            } catch (IllegalArgumentException ex2) {
                throw new NumberFormatException("Illegal DATE literal:  " + value);
            }
        }
        hooks.quoteDateLiteral(buf, date);
    }

    void quoteTime(StringBuilder buf, String value) {
        try {
            Time.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new NumberFormatException("Illegal TIME literal:  " + value);
        }
        buf.append("TIME ");
        DialectUtil.singleQuoteString(value, buf);
    }

    void quoteTimestamp(StringBuilder buf, String value) {
        Timestamp timestamp;
        try {
            timestamp = Timestamp.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new NumberFormatException("Illegal TIMESTAMP literal:  " + value);
        }
        hooks.quoteTimestampLiteral(buf, timestamp.toString(), timestamp);
    }

    /**
     * Default implementation of the protected {@code quoteDateLiteral(buf, Date)}
     * hook.
     */
    static void defaultQuoteDateLiteral(StringBuilder buf, Date date) {
        buf.append("DATE ");
        DialectUtil.singleQuoteString(date.toString(), buf);
    }

    /**
     * Default implementation of the protected
     * {@code quoteTimestampLiteral(buf, value, Timestamp)} hook.
     */
    static void defaultQuoteTimestampLiteral(StringBuilder buf, String value, Timestamp timestamp) {
        buf.append("TIMESTAMP ");
        DialectUtil.singleQuoteString(value, buf);
    }
}
