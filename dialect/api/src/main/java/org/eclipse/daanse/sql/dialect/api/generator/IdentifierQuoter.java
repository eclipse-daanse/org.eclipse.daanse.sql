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
package org.eclipse.daanse.sql.dialect.api.generator;

import java.util.List;

import org.eclipse.daanse.sql.dialect.api.IdentifierQuotingPolicy;
import org.eclipse.daanse.sql.dialect.api.type.QuoteStyle;

public interface IdentifierQuoter {

    String quoteIdentifier(CharSequence val);

    /** @param val identifier to quote (must not be null) */
    void quoteIdentifier(String val, StringBuilder buf);

    /**
     * @param qual qualifier (schema name); if not null, prepended with separator
     */
    String quoteIdentifier(String qual, String name);

    /** @param names list of names to be quoted */
    void quoteIdentifier(StringBuilder buf, String... names);

    String getQuoteIdentifierString();

    /**
     * @param val identifier to quote conditionally
     * @return either {@code val} unquoted (as a fresh {@code StringBuilder}) or the
     *         result of {@link #quoteIdentifier(CharSequence)}
     */
    default String quoteIdentifierIfNeeded(CharSequence val) {
        return quoteIdentifier(val);
    }

    /**
     * @param policy desired quoting style for this single call; {@code null} is
     *               treated as {@link IdentifierQuotingPolicy#ALWAYS}
     */
    default String quoteIdentifierWith(CharSequence val, IdentifierQuotingPolicy policy) {
        if (val == null) {
            return "";
        }
        IdentifierQuotingPolicy p = (policy == null) ? IdentifierQuotingPolicy.ALWAYS : policy;
        return switch (p) {
        case ALWAYS -> quoteIdentifier(val);
        case WHEN_NEEDED -> quoteIdentifierIfNeeded(val);
        case NEVER -> val.toString();
        };
    }

    default void quoteIdentifierWith(String val, StringBuilder buf, IdentifierQuotingPolicy policy) {
        if (val == null) {
            return;
        }
        buf.append(quoteIdentifierWith(val, policy));
    }

    default void quoteIdentifierWith(StringBuilder buf, IdentifierQuotingPolicy policy, String... names) {
        int nonNull = 0;
        for (String name : names) {
            if (name == null) {
                continue;
            }
            if (nonNull > 0) {
                buf.append('.');
            }
            quoteIdentifierWith(name, buf, policy);
            ++nonNull;
        }
    }

    /**
     * @param buf         buffer to append to (must not be null)
     * @param identifiers identifiers to quote (must not be null; may be empty)
     */
    default void appendQuotedCsv(StringBuilder buf, List<String> identifiers) {
        boolean first = true;
        for (String id : identifiers) {
            if (!first)
                buf.append(", ");
            buf.append(quoteIdentifier(id));
            first = false;
        }
    }

    default QuoteStyle getQuoteStyle() {
        String quote = getQuoteIdentifierString();
        if (quote == null || quote.isEmpty()) {
            return QuoteStyle.NONE;
        }
        return switch (quote) {
        case "\"" -> QuoteStyle.DOUBLE_QUOTE;
        case "`" -> QuoteStyle.BACKTICK;
        case "[" -> QuoteStyle.SQUARE_BRACKET;
        default -> QuoteStyle.DOUBLE_QUOTE;
        };
    }
}
