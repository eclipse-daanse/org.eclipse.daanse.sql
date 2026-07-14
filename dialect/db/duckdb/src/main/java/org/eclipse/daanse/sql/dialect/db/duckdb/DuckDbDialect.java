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
*/
package org.eclipse.daanse.sql.dialect.db.duckdb;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.daanse.sql.dialect.db.common.AbstractJdbcDialect;
import org.eclipse.daanse.sql.dialect.db.common.DialectUtil;

/**
 * Dialect for DuckDB (embedded, Postgres-flavored ANSI analytical database).
 *
 * <p>DuckDB tracks PostgreSQL syntax closely: identifiers are quoted with
 * double quotes (the ANSI default reported by the driver), identifier matching
 * is case-insensitive but case-preserving, and {@code LIMIT n OFFSET m},
 * {@code ORDER BY ... NULLS FIRST/LAST} and {@code GROUPING SETS} /
 * {@code ROLLUP} / {@code CUBE} are all documented core features. Those are
 * the only capabilities this dialect pins; everything else inherits the
 * conservative ANSI defaults of {@link AbstractJdbcDialect}.
 *
 * <p>Regular expressions in {@code WHERE} are supported via the documented
 * {@code regexp_matches(string, pattern[, options])} scalar function (RE2
 * engine). See {@link #generateRegularExpression(String, String)} for the
 * Java-regex translation choices.
 *
 * <p>Capabilities deliberately left at their conservative inherited defaults
 * (DuckDB may well support them, but they are not needed and not verified
 * here):
 * <ul>
 * <li>{@code supportsMultiValueInExpr()} — row-value {@code IN} lists are not
 * relied upon.</li>
 * <li>{@code allowsCompoundCountDistinct()} — multi-column
 * {@code COUNT(DISTINCT a, b)} stays disabled.</li>
 * <li>{@code FETCH NEXT ... ROWS ONLY} pagination — DuckDB documents
 * {@code LIMIT/OFFSET}; the ANSI fetch-first form is not assumed, hence the
 * explicit {@link #paginationGenerator()} override.</li>
 * </ul>
 */
public class DuckDbDialect extends AbstractJdbcDialect {

    private static final String SUPPORTED_PRODUCT_NAME = "DUCKDB";

    private volatile org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator cachedPaginationGenerator;

    /** JDBC-free constructor for SQL generation. */
    public DuckDbDialect() {
        super(org.eclipse.daanse.sql.dialect.api.DialectInitData.ansiDefaults());
    }

    /** Construct from a captured snapshot — the canonical entry point. */
    public DuckDbDialect(org.eclipse.daanse.sql.dialect.api.DialectInitData init) {
        super(init);
    }

    @Override
    public String name() {
        return SUPPORTED_PRODUCT_NAME.toLowerCase();
    }

    /**
     * DuckDB matches unquoted identifiers case-insensitively but preserves the
     * case they were created with — no automatic folding.
     */
    @Override
    public org.eclipse.daanse.sql.dialect.api.IdentifierCaseFolding caseFolding() {
        return org.eclipse.daanse.sql.dialect.api.IdentifierCaseFolding.PRESERVE;
    }

    /**
     * DuckDB supports regular-expression predicates in {@code WHERE} through the
     * documented {@code regexp_matches()} scalar function.
     */
    @Override
    public boolean allowsRegularExpressionInWhereClause() {
        return true;
    }

    /**
     * duckdb_jdbc silently ignores {@link java.sql.Statement#setMaxRows(int)}, so the
     * drill-through row limit must be rendered into the SQL as {@code LIMIT n}
     * (same treatment as ClickHouse).
     */
    @Override
    public boolean requiresDrillthroughMaxRowsInLimit() {
        return true;
    }

    /**
     * Translate a Java regex into a DuckDB {@code regexp_matches} predicate.
     *
     * <p>Semantics and translation choices (kept deliberately in line with the
     * MySQL/PostgreSQL generators so native and non-native evaluation agree on
     * the engine's {@code .*}-wrapped MATCHES patterns):
     * <ul>
     * <li>{@code regexp_matches(string, pattern[, options])} is DuckDB's
     * documented partial-match predicate (true if the pattern matches anywhere
     * in the string) — the same contains-semantics as MySQL {@code REGEXP} and
     * PostgreSQL {@code ~} used by those dialects' generators.</li>
     * <li>A leading Java {@code (?i)} embedded flag is extracted and passed as
     * the documented {@code 'i'} (case-insensitive) options argument instead of
     * being left inline.</li>
     * <li>DuckDB's regex engine is RE2, which does not support Java's
     * {@code \Q…\E} literal quoting — quoted sections are unescaped the same
     * way the MySQL generator does.</li>
     * <li>A {@code source IS NOT NULL AND} guard is prepended, matching the
     * other generators, so NULL captions never match.</li>
     * <li>If the input is not a valid Java regex the translation is refused
     * (empty result), which makes the engine fall back to non-native
     * evaluation.</li>
     * </ul>
     */
    @Override
    public Optional<String> generateRegularExpression(String source, String javaRegex) {
        try {
            Pattern.compile(javaRegex);
        } catch (PatternSyntaxException e) {
            // Not a valid Java regex. Too risky to continue.
            return Optional.empty();
        }
        javaRegex = DialectUtil.cleanUnicodeAwareCaseFlag(javaRegex);
        StringBuilder mappedFlags = new StringBuilder();
        String[][] mapping = new String[][] { { "i", "i" } };
        javaRegex = extractEmbeddedFlags(javaRegex, mapping, mappedFlags);
        // RE2 has no \Q...\E support: unescape quoted sections (same as MySqlDialect).
        final Matcher escapeMatcher = DialectUtil.ESCAPE_PATTERN.matcher(javaRegex);
        while (escapeMatcher.find()) {
            javaRegex = javaRegex.replace(escapeMatcher.group(1), escapeMatcher.group(2));
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(source);
        // regexp_matches needs VARCHAR; DuckDB won't coerce numeric columns (e.g. store_sqft),
        // so cast like PostgreSqlDialect does.
        sb.append(" IS NOT NULL AND regexp_matches(CAST(");
        sb.append(source);
        sb.append(" AS VARCHAR), ");
        quoteStringLiteral(sb, javaRegex);
        if (mappedFlags.length() > 0) {
            sb.append(", ");
            quoteStringLiteral(sb, mappedFlags.toString());
        }
        sb.append(")");
        return Optional.of(sb.toString());
    }

    /** DuckDB supports {@code GROUPING SETS}, {@code ROLLUP} and {@code CUBE}. */
    @Override
    public boolean supportsGroupingSets() {
        return true;
    }

    /** DuckDB supports {@code ORDER BY ... NULLS FIRST/LAST} natively. */
    @Override
    protected boolean supportsNullsOrdering() {
        return true;
    }

    @Override
    public StringBuilder generateOrderByNulls(CharSequence expr, boolean ascending, boolean collateNullsLast) {
        return generateOrderByNullsAnsi(expr, ascending, collateNullsLast);
    }

    /**
     * DuckDB: {@code LIMIT n OFFSET m} (Postgres form) — both clauses optional.
     * The inherited ANSI {@code OFFSET ... ROWS FETCH NEXT ... ROWS ONLY} form
     * is not assumed to be supported.
     */
    @Override
    public org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator paginationGenerator() {
        var local = cachedPaginationGenerator;
        if (local != null)
            return local;
        local = new org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator() {
            @Override
            public String paginate(java.util.OptionalLong limit, java.util.OptionalLong offset) {
                StringBuilder sb = new StringBuilder();
                limit.ifPresent(l -> {
                    if (l < 0)
                        throw new IllegalArgumentException("limit must be >= 0");
                    sb.append(" LIMIT ").append(l);
                });
                offset.ifPresent(o -> {
                    if (o < 0)
                        throw new IllegalArgumentException("offset must be >= 0");
                    sb.append(" OFFSET ").append(o);
                });
                return sb.toString();
            }
        };
        cachedPaginationGenerator = local;
        return local;
    }

}
