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

public class MockDialectHelper {

    public static Dialect createAnsiDialect() {
        return createDialectWithQuote("\"");
    }

    public static Dialect createMySqlDialect() {
        return createDialectWithQuote("`");
    }

    public static Dialect createSqlServerDialect() {
        Dialect dialect = mock(Dialect.class);

        when(dialect.getQuoteIdentifierString()).thenReturn("[");

        when(dialect.quoteIdentifier(any(CharSequence.class))).thenAnswer(inv -> {
            CharSequence val = inv.getArgument(0);
            return new StringBuilder("[").append(val).append("]");
        });

        doAnswer(inv -> {
            String val = inv.getArgument(0);
            StringBuilder buf = inv.getArgument(1);
            if (val != null) {
                buf.append("[").append(val).append("]");
            }
            return null;
        }).when(dialect).quoteIdentifier(anyString(), any(StringBuilder.class));

        when(dialect.quoteIdentifier(anyString(), anyString())).thenAnswer(inv -> {
            String qual = inv.getArgument(0);
            String name = inv.getArgument(1);
            StringBuilder sb = new StringBuilder();
            if (qual != null) {
                sb.append("[").append(qual).append("].");
            }
            sb.append("[").append(name).append("]");
            return sb.toString();
        });

        doAnswer(inv -> {
            StringBuilder buf = inv.getArgument(0);
            Object[] args = inv.getArguments();
            boolean first = true;
            for (int i = 1; i < args.length; i++) {
                String name = (String) args[i];
                if (name == null)
                    continue;
                if (!first)
                    buf.append(".");
                buf.append("[").append(name).append("]");
                first = false;
            }
            return null;
        }).when(dialect).quoteIdentifier(any(StringBuilder.class), (String[]) any());

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

        when(dialect.allowsAs()).thenReturn(true);
        when(dialect.allowsFieldAs()).thenReturn(true);
        when(dialect.needsExponent(any(), anyString())).thenReturn(false);
        when(dialect.getDialectName()).thenReturn("sqlserver");

        return dialect;
    }

    public static Dialect createDialectWithQuote(String quoteChar) {
        Dialect dialect = mock(Dialect.class);

        when(dialect.getQuoteIdentifierString()).thenReturn(quoteChar);

        when(dialect.quoteIdentifier(any(CharSequence.class))).thenAnswer(inv -> {
            CharSequence val = inv.getArgument(0);
            return new StringBuilder(quoteChar).append(val).append(quoteChar);
        });

        doAnswer(inv -> {
            String val = inv.getArgument(0);
            StringBuilder buf = inv.getArgument(1);
            if (val != null) {
                buf.append(quoteChar).append(val).append(quoteChar);
            }
            return null;
        }).when(dialect).quoteIdentifier(anyString(), any(StringBuilder.class));

        when(dialect.quoteIdentifier(anyString(), anyString())).thenAnswer(inv -> {
            String qual = inv.getArgument(0);
            String name = inv.getArgument(1);
            StringBuilder sb = new StringBuilder();
            if (qual != null) {
                sb.append(quoteChar).append(qual).append(quoteChar).append(".");
            }
            sb.append(quoteChar).append(name).append(quoteChar);
            return sb.toString();
        });

        doAnswer(inv -> {
            StringBuilder buf = inv.getArgument(0);
            Object[] args = inv.getArguments();
            boolean first = true;
            for (int i = 1; i < args.length; i++) {
                String name = (String) args[i];
                if (name == null)
                    continue;
                if (!first)
                    buf.append(".");
                buf.append(quoteChar).append(name).append(quoteChar);
                first = false;
            }
            return null;
        }).when(dialect).quoteIdentifier(any(StringBuilder.class), (String[]) any());

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

        when(dialect.allowsAs()).thenReturn(true);
        when(dialect.allowsFieldAs()).thenReturn(true);
        when(dialect.needsExponent(any(), anyString())).thenReturn(false);
        when(dialect.getDialectName()).thenReturn("mock");

        return dialect;
    }

    public static Dialect createDialectWithoutAs() {
        Dialect dialect = createAnsiDialect();
        when(dialect.allowsAs()).thenReturn(false);
        when(dialect.allowsFieldAs()).thenReturn(false);
        return dialect;
    }
}
