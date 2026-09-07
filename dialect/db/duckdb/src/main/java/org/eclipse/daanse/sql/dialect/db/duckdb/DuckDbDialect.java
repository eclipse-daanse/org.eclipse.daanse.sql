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

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.daanse.sql.dialect.db.common.AbstractJdbcDialect;
import org.eclipse.daanse.sql.dialect.db.common.DialectUtil;
import org.eclipse.daanse.sql.model.sql.BitOperation;
import org.eclipse.daanse.sql.model.sql.OrderedColumn;

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

    /**
     * {@code SMALLINT}.
     *
     * <p>
     * DuckDB has a native BOOLEAN, and it is still the wrong choice here: the
     * consumers read boolean levels back with {@code ResultSet.getInt}, and
     * DuckDB answers a native boolean column with the string "true", which
     * {@code getInt} rejects. SMALLINT is what the legacy loader used, for the
     * same reason.
     */
    @Override
    public String booleanTypeName() {
        return "SMALLINT";
    }

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
        // anchored like Pattern.matches (RE2 supports \A and \z)
        quoteStringLiteral(sb, "\\A(?:" + javaRegex + ")\\z");
        if (mappedFlags.length() > 0) {
            sb.append(", ");
            quoteStringLiteral(sb, mappedFlags.toString());
        }
        sb.append(")");
        return Optional.of(sb.toString());
    }

    /** DuckDB has no {@code ALTER INDEX ... RENAME} — verified unsupported. */
    @Override
    public boolean supportsRenameIndex() {
        return false;
    }

    /** DuckDB has no constraint rename — verified unsupported. */
    @Override
    public boolean supportsRenameConstraint() {
        return false;
    }

    /**
     * True — DuckDB supports the ANSI {@code RENAME TO} syntax. It still rejects
     * it at runtime with a "Dependency Error" if the table has a dependent
     * object (e.g. an index); that's a runtime constraint, not a syntax gap, so
     * the flag stays true and callers must handle the failure themselves.
     */
    @Override
    public boolean supportsRenameTable() {
        return true;
    }

    /**
     * True — DuckDB supports the ANSI {@code RENAME COLUMN} syntax. Same runtime
     * caveat as {@link #supportsRenameTable}: rejected with a "Dependency Error"
     * when the column has a dependent index, not because the syntax is
     * unsupported.
     */
    @Override
    public boolean supportsRenameColumn() {
        return true;
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


    /**
     * Bulk load via {@code read_csv}. Uses {@code header=false} with {@code skip}
     * because {@code header=true} would take the type line as the first data row
     * and turn every column into VARCHAR.
     */
    @Override
    public org.eclipse.daanse.sql.dialect.api.generator.BulkLoadGenerator bulkLoadGenerator() {
        return new org.eclipse.daanse.sql.dialect.api.generator.BulkLoadGenerator() {

            @Override
            public boolean supportsBulkLoad() {
                return true;
            }

            @Override
            public java.util.Optional<String> loadFromDelimitedFile(
                    org.eclipse.daanse.sql.model.schema.TableReference target, java.util.List<String> columns,
                    java.nio.file.Path csvFile, char delimiter, int skipLines, String nullLiteral) {
                String quotedColumns = columns.stream().map(DuckDbDialect.this::quoteIdentifier)
                        .collect(java.util.stream.Collectors.joining(", "));
                String file = csvFile.toAbsolutePath().toString().replace("'", "''");
                return java.util.Optional.of("INSERT INTO " + qualified(target) + " (" + quotedColumns
                        + ") SELECT * FROM read_csv('" + file + "', header=false, skip=" + skipLines + ", delim='"
                        + (delimiter == '\'' ? "''" : String.valueOf(delimiter)) + "', nullstr='"
                        + nullLiteral.replace("'", "''") + "')");
            }
        };
    }

    @Override
    public java.util.Optional<String> generateListAgg(CharSequence operand, boolean distinct, String separator,
            String coalesce, String onOverflowTruncate, List<OrderedColumn> columns) {
        StringBuilder buf = new StringBuilder(64);
        buf.append("STRING_AGG");
        buf.append("( ");
        if (distinct && (columns == null || columns.isEmpty())) { // DISTINCT has error if we have ORDER BY for duckDb
            buf.append("DISTINCT ");
        }
        buf.append(operand);
        if (separator != null) {
            buf.append(", '").append(separator).append("' ");
        }

        if (columns != null && !columns.isEmpty()) {
            buf.append("ORDER BY ");
            buf.append(buildOrderedColumnsClause(columns));
        }
        buf.append(")");
        // STRING_AGG(name, ', ' ORDER BY hire_date)

        return java.util.Optional.of((buf).toString());
    }


    @Override
    public java.util.Optional<String> generateBitAggregation(BitOperation operation, CharSequence operand) {
        StringBuilder buf = new StringBuilder(64);
        StringBuilder result = switch (operation) {
        case AND -> buf.append("bit_and(").append(operand).append(")");
        case OR -> buf.append("bit_or(").append(operand).append(")");
        case XOR -> buf.append("bit_xor(").append(operand).append(")");
        case NAND -> buf.append("~(bit_and(").append(operand).append("))");
        case NOR -> buf.append("~(bit_or(").append(operand).append("))");
        case NXOR -> buf.append("~(bit_xor(").append(operand).append("))");
        };
        return java.util.Optional.of(result.toString());
    }

    @Override
    public boolean supportsBitAggregation(BitOperation operation) {
        return true; // DucDb supports all bit operations
    }

    @Override
    public java.util.Optional<String> generatePercentileDisc(double percentile, boolean desc, String tableName,
            String columnName) {
        return java.util.Optional
                .of((buildPercentileFunction("quantile_disc", percentile, desc, tableName, columnName)).toString());
    }

    @Override
    public java.util.Optional<String> generatePercentileCont(double percentile, boolean desc, String tableName,
            String columnName) {
        return java.util.Optional
                .of((buildPercentileFunction("quantile_cont", percentile, desc, tableName, columnName)).toString());
    }

    public StringBuilder buildPercentileFunction(String functionName, double percentile, boolean desc, String tableName,
            String columnName) {
        StringBuilder buf = new StringBuilder(64);
        buf.append(functionName).append("(");
        if (tableName != null) {
            quoteIdentifier(buf, tableName, columnName);
        } else {
            quoteIdentifier(buf, columnName);
        }
        buf.append(", ");
        buf.append(percentile);
        buf.append(")");
        return buf;
    }

    @Override
    public java.util.Optional<String> generateNthValueAgg(CharSequence operand, boolean ignoreNulls, Integer n,
            List<OrderedColumn> columns) {
        return java.util.Optional
                .of((buildNthValueFunction("NTH_VALUE", operand, ignoreNulls, n, columns, false)).toString());
    }

    /**
     * No.
     *
     * <p>
     * DuckDB, like SQLite, is single-writer: concurrent {@code CREATE TABLE}
     * transactions from separate connections against the same database
     * conflict on the shared catalog ("Catalog write-write conflict") instead
     * of dividing the work.
     */
    @Override
    public boolean supportsParallelLoading() {
        return false;
    }

    @Override
    public boolean supportsPercentileDisc() {
        return true;
    }

    @Override
    public boolean supportsPercentileCont() {
        return true;
    }

    @Override
    public boolean supportsNthValue() {
        return true;
    }

    @Override
    public boolean supportsListAgg() {
        return true;
    }
}
