/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.sql.dialect.db.h2;

import org.eclipse.daanse.sql.dialect.api.generator.AggregationGenerator;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.model.sql.BitOperation;
import org.eclipse.daanse.sql.model.sql.OrderedColumn;
import org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator.PagingParam;
import org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator.PreparedPaging;
import org.eclipse.daanse.sql.dialect.db.common.AbstractJdbcDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class H2Dialect extends AbstractJdbcDialect {

    private volatile org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator cachedPaginationGenerator;
    private static final Logger LOGGER = LoggerFactory.getLogger(H2Dialect.class);

    private static final String SUPPORTED_PRODUCT_NAME = "H2";

    @Override
    public boolean allowsRegularExpressionInWhereClause() {
        return true;
    }

    /**
     * {@code source IS NOT NULL AND REGEXP_LIKE(source, '\A(?:javaRegex)\z')}.
     *
     * <p>H2 evaluates REGEXP_LIKE with java.util.regex, on every version from 1.4.200 on, with FIND semantics, while
     * the MDX MATCHES operator is anchored ({@code Pattern.matches}): the expression is wrapped in {@code \A(?:…)\z}
     * and otherwise passed as it is, Java's to Java. Its flags stay inside it, where Java reads them for the rest of
     * the group: taking them out to REGEXP_LIKE's flags, as before, lost {@code s} and {@code u}, and {@code \Q…\E},
     * unquoted before, quotes as it does in Java. H2 folds the case beyond ASCII under {@code (?i)}, as Java does only
     * with {@code u}: {@code (?-u)} before the expression turns that off.
     */
    @Override
    public Optional<String> generateRegularExpression(String source, String javaRegex) {
        try {
            java.util.regex.Pattern.compile(javaRegex);
        } catch (java.util.regex.PatternSyntaxException e) {
            // Not a valid Java regex. Too risky to continue.
            return Optional.empty();
        }
        javaRegex = org.eclipse.daanse.sql.dialect.db.common.DialectUtil.cleanUnicodeAwareCaseFlag(javaRegex);
        final StringBuilder sb = new StringBuilder();
        sb.append(source);
        sb.append(" IS NOT NULL AND ");
        sb.append("REGEXP_LIKE(");
        sb.append(source);
        sb.append(", ");
        // H2 compiles with UNICODE_CASE: (?-u) gives Java's ASCII-only (?i) back, an (?iu) inside turns it on again
        quoteStringLiteral(sb, "(?-u)\\A(?:" + javaRegex + ")\\z");
        sb.append(")");
        return Optional.of(sb.toString());
    }

    /**
     * {@code ANALYZE}.
     *
     * <p>
     * H2 samples 10000 rows per table by default; {@code ANALYZE SAMPLE_SIZE 0}
     * makes it read every row.
     */
    @Override
    public java.util.Optional<String> analyzeSchema() {
        return java.util.Optional.of("ANALYZE");
    }

    /**
     * {@code ANALYZE TABLE <table>}.
     */
    @Override
    public java.util.Optional<String> analyzeTable(org.eclipse.daanse.sql.model.schema.TableReference table) {
        return java.util.Optional.of("ANALYZE TABLE " + qualified(table));
    }

    /** JDBC-free constructor for SQL generation. */
    public H2Dialect() {
        super(org.eclipse.daanse.sql.dialect.api.DialectInitData.ansiDefaults());
    }

    /** Construct from a captured snapshot — the canonical entry point. */
    public H2Dialect(org.eclipse.daanse.sql.dialect.api.DialectInitData init) {
        super(init);
    }

    /**
     * H2 divides integers to an integer. MDX divides with a fraction, and a native condition over a quotient has to as
     * well: the dividend is read as DOUBLE (F-10.9, a native Filter over Units Shipped / Units Ordered, database run
     * 20.10.).
     */
    @Override
    public CharSequence quotientDividend(CharSequence dividend) {
        return "CAST(" + dividend + " AS DOUBLE)";
    }

    @Override
    public String name() {
        return SUPPORTED_PRODUCT_NAME.toLowerCase();
    }

    @Override
    protected boolean supportsNullsOrdering() {
        return true; // H2 supports NULLS FIRST/LAST
    }

    // Unified BitOperation methods

    /**
     * {@code BIT_AND_AGG}, {@code BIT_OR_AGG}, {@code BIT_XOR_AGG}, {@code BIT_NAND_AGG}, {@code BIT_NOR_AGG} and
     * {@code BIT_XNOR_AGG} from H2 2.0. 1.4 has {@code BIT_AND} and {@code BIT_OR} only, and no aggregate of the
     * others ("Function not found"): those are refused there. A dialect made without a connection takes such a
     * version.
     */
    @Override
    public java.util.Optional<String> generateBitAggregation(BitOperation operation, CharSequence operand) {
        if (!supportsBitAggregation(operation)) {
            return java.util.Optional.empty();
        }
        boolean agg = dialectVersion.isUnknownOrAtLeast(2, 0);
        String function = switch (operation) {
        case AND -> agg ? "BIT_AND_AGG(" : "BIT_AND(";
        case OR -> agg ? "BIT_OR_AGG(" : "BIT_OR(";
        case XOR -> "BIT_XOR_AGG(";
        case NAND -> "BIT_NAND_AGG(";
        case NOR -> "BIT_NOR_AGG(";
        case NXOR -> "BIT_XNOR_AGG(";
        };
        return java.util.Optional.of(function + operand + ")");
    }

    @Override
    public boolean supportsBitAggregation(BitOperation operation) {
        return dialectVersion.isUnknownOrAtLeast(2, 0) || operation == BitOperation.AND
                || operation == BitOperation.OR;
    }

    /**
     * The first value of the sorted group whose cumulative share reaches p, the {@code max(1, ceil(p n))}-th of its n
     * values that are not NULL, as the standard has it: the element of the group's sorted {@code ARRAY_AGG}, sliced
     * out with a NULL appended, so that a group of no value gives NULL. H2's own {@code PERCENTILE_DISC}, in every
     * version from 1.4.200 on, takes the nearest of the positions {@code p (n - 1)}, a half rounded down: 20, not 10,
     * at 0.25 of 10, 20, 30, 40, and 2, not 1, at 0.1 of 1 to 10. The p is an exact numeric in H2, CEILING of 0.3 * 10
     * is 3.
     */
    @Override
    public java.util.Optional<String> generatePercentileDisc(double percentile, boolean desc, String tableName,
            String columnName) {
        StringBuilder col = new StringBuilder();
        if (tableName != null) {
            quoteIdentifier(col, tableName, columnName);
        } else {
            quoteIdentifier(col, columnName);
        }
        String k = "GREATEST(1, CAST(CEILING(" + percentile + " * COUNT(" + col + ")) AS INT))";
        return java.util.Optional.of("(ARRAY_SLICE(ARRAY_AGG(" + col + " ORDER BY " + col + (desc ? " DESC" : "")
                + ") FILTER (WHERE " + col + " IS NOT NULL), " + k + ", " + k + ") || ARRAY[NULL])[1]");
    }

    /** H2's own {@code PERCENTILE_CONT}, which interpolates as the standard does, on every version from 1.4.200 on. */
    @Override
    public java.util.Optional<String> generatePercentileCont(double percentile, boolean desc, String tableName,
            String columnName) {
        return java.util.Optional
                .of((buildPercentileFunction("PERCENTILE_CONT", percentile, desc, tableName, columnName)).toString());
    }

    /**
     * {@code LISTAGG([DISTINCT] x, 'sep' [ON OVERFLOW TRUNCATE 'filler' WITHOUT COUNT]) [WITHIN GROUP (ORDER BY …)]},
     * the separator and the stand-in of a NULL as string literals: a quote in them ended the literal before. Without a
     * separator {@code ', '}. {@code ON OVERFLOW TRUNCATE} from H2 2.0, a syntax error in 1.4: there the list is not
     * truncated. 1.4 orders a {@code WITHIN GROUP} by one expression only, without {@code NULLS FIRST} or
     * {@code NULLS LAST} (a syntax error after the first): a list in the order of more columns, or with an order of
     * its NULLs, is refused there. A dialect made without a connection takes such a version.
     */
    @Override
    public java.util.Optional<String> generateListAgg(CharSequence operand, boolean distinct, String separator,
            String coalesce, String onOverflowTruncate, List<OrderedColumn> columns) {
        if (!dialectVersion.isUnknownOrAtLeast(2, 0) && columns != null
                && (columns.size() > 1 || columns.stream().anyMatch(c -> c.nullsOrder().isPresent()))) {
            return java.util.Optional.empty();
        }
        StringBuilder buf = new StringBuilder(64);
        buf.append("LISTAGG( ");
        if (distinct) {
            buf.append("DISTINCT ");
        }
        if (coalesce != null) {
            buf.append("COALESCE(").append(operand).append(", ");
            quoteStringLiteral(buf, coalesce);
            buf.append(")");
        } else {
            buf.append(operand);
        }
        buf.append(", ");
        quoteStringLiteral(buf, separator != null ? separator : ", ");
        if (onOverflowTruncate != null && dialectVersion.isUnknownOrAtLeast(2, 0)) {
            buf.append(" ON OVERFLOW TRUNCATE ");
            quoteStringLiteral(buf, onOverflowTruncate);
            buf.append(" WITHOUT COUNT");
        }
        buf.append(")");
        if (columns != null && !columns.isEmpty()) {
            buf.append(" WITHIN GROUP (ORDER BY ");
            buf.append(buildOrderedColumnsClause(columns));
            buf.append(")");
        }
        return java.util.Optional.of(buf.toString());
    }

    /**
     * The n-th value of the group's rows in their order: an ordered array, its n-th element (V-82). H2 fails an
     * element reference past the end ("Array element error", also ARRAY_GET), so the element is sliced out and a NULL
     * appended: a group with fewer values gives NULL, as the aggregate promises.
     */
    @Override
    public java.util.Optional<String> generateNthValueAgg(CharSequence operand, boolean ignoreNulls, Integer n,
            List<OrderedColumn> columns) {
        int index = AggregationGenerator.nthIndex(n);
        return java.util.Optional.of(buildNthValueOfArray("ARRAY_AGG", operand, ignoreNulls, columns, "(ARRAY_SLICE(",
                ", " + index + ", " + index + ") || ARRAY[NULL])[1]").toString());
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
    public boolean supportsNthValueIgnoreNulls() {
        return true;
    }

    @Override
    public boolean supportsListAgg() {
        return true;
    }

    /** H2 has no ADMIN OPTION on role grants — the flag is ignored. */
    @Override
    public String grantRole(String roleName, String grantee, boolean withAdminOption) {
        return "GRANT " + quoteIdentifier(roleName) + " TO " + quoteIdentifier(grantee);
    }

    /**
     * No recursive one: H2 runs {@code WITH RECURSIVE n(k) AS (…)} only with the list of its columns, in every
     * version ("Column not found" without, a syntax error in 2.3), and an expression of the generator has no such
     * list. Expressions that are not recursive run on every version.
     */
    @Override
    public org.eclipse.daanse.sql.dialect.api.generator.CteGenerator cteGenerator() {
        return new org.eclipse.daanse.sql.dialect.api.generator.CteGenerator() {
            @Override
            public boolean supportsRecursiveCte() {
                return false;
            }
        };
    }

    /**
     * {@code LIMIT n [OFFSET m]}, an offset alone {@code OFFSET m ROWS}, on every version from 1.4.200 on: H2 2.x
     * refuses {@code LIMIT -1} ("Invalid value -1 for parameter result FETCH").
     */
    @Override
    public org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator paginationGenerator() {
        var local = cachedPaginationGenerator;
        if (local != null)
            return local;
        local = new org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator() {
            @Override
            public String paginate(java.util.OptionalLong limit, java.util.OptionalLong offset) {
                if (limit.isEmpty() && offset.isEmpty())
                    return "";
                StringBuilder sb = new StringBuilder();
                if (limit.isPresent()) {
                    long l = limit.getAsLong();
                    if (l < 0)
                        throw new IllegalArgumentException("limit must be >= 0");
                    sb.append(" LIMIT ").append(l);
                }
                if (offset.isPresent()) {
                    long o = offset.getAsLong();
                    if (o < 0)
                        throw new IllegalArgumentException("offset must be >= 0");
                    // an offset alone: OFFSET n ROWS; LIMIT -1, written before, H2 2.x refuses
                    sb.append(" OFFSET ").append(o).append(limit.isPresent() ? "" : " ROWS");
                }
                return sb.toString();
            }

            @Override
            public PreparedPaging paginatePrepared(boolean withLimit, boolean withOffset) {
                if (withLimit && withOffset) {
                    return new PreparedPaging(
                        "LIMIT ? OFFSET ?",
                        List.of(PagingParam.LIMIT, PagingParam.OFFSET)
                    );
                } else if (withLimit) {
                    return new PreparedPaging(
                        "LIMIT ?",
                        List.of(PagingParam.LIMIT)
                    );
                } else if (withOffset) {
                    return new PreparedPaging(
                        "OFFSET ?",
                        List.of(PagingParam.OFFSET)
                    );
                } else {
                    return new PreparedPaging("", List.of());
                }
            }
        };
        cachedPaginationGenerator = local;
        return local;
    }

    /** H2 synonyms point to tables and views of the same database (the default spelling). */
    @Override
    public boolean supportsSynonyms() {
        return true;
    }

    /**
     * H2 creates a user only with a password or with a salt and a password hash. A user is created
     * with an empty salt and an empty hash, which no password hashes to, {@code SALT X'' HASH X''}:
     * it receives grants and logs in once it is given a password ({@code ALTER USER … SET PASSWORD}).
     */
    @Override
    public boolean supportsUsers() {
        return true;
    }

    @Override
    public Optional<String> createUser(String userName, boolean ifNotExists) {
        return Optional.of("CREATE USER " + (ifNotExists ? "IF NOT EXISTS " : "") + quoteIdentifier(userName)
                + " SALT X'' HASH X''");
    }

    /** H2 keeps the order of each column of an index. */
    @Override
    public boolean supportsDescendingIndexColumns() {
        return true;
    }
}
