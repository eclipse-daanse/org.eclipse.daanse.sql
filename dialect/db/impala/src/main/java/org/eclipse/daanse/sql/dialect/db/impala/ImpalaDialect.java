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
package org.eclipse.daanse.sql.dialect.db.impala;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.sql.dialect.db.common.DialectUtil;
import org.eclipse.daanse.sql.dialect.db.hive.HiveDialect;

/**
 * Dialect for Apache Impala. Connections are expected through the Apache
 * Hive JDBC driver (HiveServer2 protocol, port 21050, {@code auth=noSasl});
 * Impala has no single-argument {@code ISNULL}, so NULL ordering uses the
 * CASE WHEN sentinels of {@link #generateOrderItem} instead of Hive's form.
 */
public class ImpalaDialect extends HiveDialect {

    private static final String SUPPORTED_PRODUCT_NAME = "IMPALA";
    public static final String CAST = "cast(";
    public static final String AS_STRING = " as string)";

    /** JDBC-free constructor for SQL generation. Uses Impala backtick quoting. */
    public ImpalaDialect() {
        super(DialectInitData.ansiDefaults().withQuoteIdentifierString("`"));
    }

    /** Construct from a captured snapshot — the canonical entry point. */
    public ImpalaDialect(DialectInitData init) {
        super(init);
    }

    @Override
    public StringBuilder generateOrderByNulls(CharSequence expr, boolean ascending, boolean collateNullsLast) {
        StringBuilder sb = new StringBuilder(expr);
        if (ascending) {
            return sb.append(" ASC");
        } else {
            return sb.append(" DESC");
        }
    }

    @Override
    public StringBuilder generateOrderItem(CharSequence expr, boolean nullable, boolean ascending,
            boolean collateNullsLast) {
        StringBuilder ret = new StringBuilder();

        if (nullable && collateNullsLast) {
            ret.append("CASE WHEN ").append(expr).append(" IS NULL THEN 1 ELSE 0 END, ");
        } else {
            ret.append("CASE WHEN ").append(expr).append(" IS NULL THEN 0 ELSE 1 END, ");
        }

        if (ascending) {
            ret.append(expr).append(" ASC");
        } else {
            ret.append(expr).append(" DESC");
        }

        return ret;
    }

    @Override
    public boolean allowsMultipleCountDistinct() {
        return false;
    }

    @Override
    public boolean requiresOrderByAlias() {
        return false;
    }

    @Override
    public boolean supportsGroupByExpressions() {
        return false;
    }

    @Override
    public boolean allowsSelectNotInGroupBy() {
        return false;
    }

    @Override
    public StringBuilder generateInline(List<String> columnNames, List<String> columnTypes, List<String[]> valueList) {
        return generateInlineGeneric(columnNames, columnTypes, valueList, null, false);
    }

    @Override
    public void quoteStringLiteral(StringBuilder buf, String value) {
        String quote = "\'";
        String s0 = value;

        if (s0.contains("\\")) {
            // NOTE: no-op replacement, kept for behavioral fidelity with the
            // legacy (Mondrian-era) dialect — backslashes are never doubled.
            s0 = s0.replace("\\\\", "\\\\");
        }
        if (s0.contains(quote)) {
            s0 = s0.replace(quote, "\\\\" + quote);
        }

        buf.append(quote);

        buf.append(s0);

        buf.append(quote);
    }

    @Override
    public boolean allowsRegularExpressionInWhereClause() {
        return true;
    }

    @Override
    public Optional<String> generateRegularExpression(String source, String javaRegex) {
        try {
            Pattern.compile(javaRegex);
        } catch (PatternSyntaxException e) {
            // Not a valid Java regex. Too risky to continue.
            return Optional.empty();
        }
        javaRegex = DialectUtil.cleanUnicodeAwareCaseFlag(javaRegex);
        // We might have to use case-insensitive matching
        StringBuilder mappedFlags = new StringBuilder();
        String[][] mapping = new String[][] { { "i", "i" } };
        javaRegex = extractEmbeddedFlags(javaRegex, mapping, mappedFlags);
        boolean caseSensitive = true;
        if (mappedFlags.toString().contains("i")) {
            caseSensitive = false;
        }
        final Matcher escapeMatcher = DialectUtil.ESCAPE_PATTERN.matcher(javaRegex);
        while (escapeMatcher.find()) {
            javaRegex = javaRegex.replace(escapeMatcher.group(1), escapeMatcher.group(2));
        }

        final StringBuilder sb = new StringBuilder();
        // Now build the string.
        sb.append(CAST);
        sb.append(source);
        sb.append(AS_STRING);
        sb.append(" IS NOT NULL AND ");
        if (caseSensitive) {
            sb.append(CAST).append(source).append(AS_STRING);
        } else {
            sb.append("UPPER(");
            sb.append(CAST).append(source).append(AS_STRING);
            sb.append(")");
        }
        sb.append(" REGEXP ");
        if (caseSensitive) {
            quoteStringLiteral(sb, javaRegex);
        } else {
            quoteStringLiteral(sb, javaRegex.toUpperCase());
        }
        return Optional.of(sb.toString());
    }

    @Override
    public boolean supportsDdl() {
        return true;
    }

    @Override
    public String name() {
        return SUPPORTED_PRODUCT_NAME.toLowerCase();
    }

}
