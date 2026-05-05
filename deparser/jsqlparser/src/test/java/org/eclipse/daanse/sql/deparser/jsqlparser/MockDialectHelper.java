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
package org.eclipse.daanse.sql.deparser.jsqlparser;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.IdentifierQuotingPolicy;

public class MockDialectHelper {

    public static Dialect createAnsiDialect() {
        return createDialectWithQuote("\"");
    }

    public static Dialect createMySqlDialect() {
        return createDialectWithQuote("`");
    }

    public static Dialect createSqlServerDialect() {
        Dialect dialect = mock(Dialect.class);

        doAnswer(inv -> {
            String val = inv.getArgument(0);
            StringBuilder buf = inv.getArgument(1);
            IdentifierQuotingPolicy policy = inv.getArgument(2);
            if (val == null)
                return null;
            if (policy == IdentifierQuotingPolicy.NEVER) {
                buf.append(val);
            } else {
                buf.append("[").append(val).append("]");
            }
            return null;
        }).when(dialect).quoteIdentifierWith(anyString(), any(StringBuilder.class), any(IdentifierQuotingPolicy.class));

        doAnswer(inv -> {
            StringBuilder buf = inv.getArgument(0);
            String s = inv.getArgument(1);
            buf.append("'").append(s.replace("'", "''")).append("'");
            return null;
        }).when(dialect).quoteStringLiteral(any(StringBuilder.class), anyString());

        doAnswer(inv -> {
            StringBuilder buf = inv.getArgument(0);
            String value = inv.getArgument(1);
            buf.append("CONVERT(DATE, '").append(value).append("')");
            return null;
        }).when(dialect).quoteDateLiteral(any(StringBuilder.class), anyString());

        doAnswer(inv -> {
            StringBuilder buf = inv.getArgument(0);
            String value = inv.getArgument(1);
            buf.append("CONVERT(TIME, '").append(value).append("')");
            return null;
        }).when(dialect).quoteTimeLiteral(any(StringBuilder.class), anyString());

        doAnswer(inv -> {
            StringBuilder buf = inv.getArgument(0);
            String value = inv.getArgument(1);
            buf.append("CONVERT(DATETIME, '").append(value).append("')");
            return null;
        }).when(dialect).quoteTimestampLiteral(any(StringBuilder.class), anyString());

        doAnswer(inv -> {
            StringBuilder buf = inv.getArgument(0);
            String value = inv.getArgument(1);
            buf.append(value);
            return null;
        }).when(dialect).quoteNumericLiteral(any(StringBuilder.class), anyString());

        when(dialect.allowsFromAlias()).thenReturn(true);
        when(dialect.allowsFieldAlias()).thenReturn(true);
        when(dialect.needsExponent(any(), anyString())).thenReturn(false);

        return dialect;
    }

    public static Dialect createDialectWithQuote(String quoteChar) {
        Dialect dialect = mock(Dialect.class);

        doAnswer(inv -> {
            String val = inv.getArgument(0);
            StringBuilder buf = inv.getArgument(1);
            IdentifierQuotingPolicy policy = inv.getArgument(2);
            if (val == null)
                return null;
            if (policy == IdentifierQuotingPolicy.NEVER) {
                buf.append(val);
            } else {
                buf.append(quoteChar).append(val).append(quoteChar);
            }
            return null;
        }).when(dialect).quoteIdentifierWith(anyString(), any(StringBuilder.class), any(IdentifierQuotingPolicy.class));

        doAnswer(inv -> {
            StringBuilder buf = inv.getArgument(0);
            String s = inv.getArgument(1);
            buf.append("'").append(s.replace("'", "''")).append("'");
            return null;
        }).when(dialect).quoteStringLiteral(any(StringBuilder.class), anyString());

        doAnswer(inv -> {
            StringBuilder buf = inv.getArgument(0);
            String value = inv.getArgument(1);
            buf.append("DATE '").append(value).append("'");
            return null;
        }).when(dialect).quoteDateLiteral(any(StringBuilder.class), anyString());

        doAnswer(inv -> {
            StringBuilder buf = inv.getArgument(0);
            String value = inv.getArgument(1);
            buf.append("TIME '").append(value).append("'");
            return null;
        }).when(dialect).quoteTimeLiteral(any(StringBuilder.class), anyString());

        doAnswer(inv -> {
            StringBuilder buf = inv.getArgument(0);
            String value = inv.getArgument(1);
            buf.append("TIMESTAMP '").append(value).append("'");
            return null;
        }).when(dialect).quoteTimestampLiteral(any(StringBuilder.class), anyString());

        doAnswer(inv -> {
            StringBuilder buf = inv.getArgument(0);
            String value = inv.getArgument(1);
            buf.append(value);
            return null;
        }).when(dialect).quoteNumericLiteral(any(StringBuilder.class), anyString());

        when(dialect.allowsFromAlias()).thenReturn(true);
        when(dialect.allowsFieldAlias()).thenReturn(true);
        when(dialect.needsExponent(any(), anyString())).thenReturn(false);

        return dialect;
    }

    public static Dialect createDialectWithoutAs() {
        Dialect dialect = createAnsiDialect();
        when(dialect.allowsFromAlias()).thenReturn(false);
        when(dialect.allowsFieldAlias()).thenReturn(false);
        return dialect;
    }
}
