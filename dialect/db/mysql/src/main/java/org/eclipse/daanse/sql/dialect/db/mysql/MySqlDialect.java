/*
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
 *
 * For more information please visit the Project: Hitachi Vantara - Mondrian
 *
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial adapt parts of Syntax.class
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.sql.dialect.db.mysql;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.model.schema.Trigger;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming;
import org.eclipse.daanse.sql.model.sql.BitOperation;
import org.eclipse.daanse.sql.dialect.api.generator.KnownFunction;
import org.eclipse.daanse.sql.dialect.api.generator.StatementHint;
import org.eclipse.daanse.sql.model.sql.OrderedColumn;
import org.eclipse.daanse.sql.dialect.db.common.AbstractJdbcDialect;
import org.eclipse.daanse.sql.dialect.db.common.DialectUtil;

/**
 * @author jhyde
 * @since Nov 23, 2008
 */
public class MySqlDialect extends AbstractJdbcDialect {
    private static final String SUPPORTED_PRODUCT_NAME = "MYSQL";

    private volatile org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator cachedPaginationGenerator;
    private volatile org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator cachedMergeGenerator;
    private volatile org.eclipse.daanse.sql.dialect.api.generator.CteGenerator cachedCteGenerator;

    /** JDBC-free constructor for SQL generation. Uses MySQL backtick quoting. */
    public MySqlDialect() {
        super(org.eclipse.daanse.sql.dialect.api.DialectInitData.ansiDefaults().withQuoteIdentifierString("`"));
    }

    /** Construct from a captured snapshot — the canonical entry point. */
    public MySqlDialect(org.eclipse.daanse.sql.dialect.api.DialectInitData init) {
        super(init);
    }

    public static boolean looksLikeInfobright(org.eclipse.daanse.sql.dialect.api.DialectInitData init) {
        String version = init.productVersion();
        return version != null && version.toLowerCase(java.util.Locale.ROOT).contains("infobright");
    }

    /** MySQL/MariaDB: {@code LIMIT [offset,] count} — both forms accepted. */
    @Override
    public org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator paginationGenerator() {
        var local = cachedPaginationGenerator;
        if (local != null)
            return local;
        local = new org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator() {
            @Override
            public String paginate(java.util.OptionalLong limit, java.util.OptionalLong offset) {
                StringBuilder sb = new StringBuilder();
                if (limit.isPresent()) {
                    if (limit.getAsLong() < 0)
                        throw new IllegalArgumentException("limit must be >= 0");
                    sb.append(" LIMIT ");
                    if (offset.isPresent()) {
                        if (offset.getAsLong() < 0)
                            throw new IllegalArgumentException("offset must be >= 0");
                        sb.append(offset.getAsLong()).append(", ");
                    }
                    sb.append(limit.getAsLong());
                } else if (offset.isPresent()) {
                    // MySQL has no syntax for OFFSET without LIMIT — use the documented huge-limit
                    // trick.
                    if (offset.getAsLong() < 0)
                        throw new IllegalArgumentException("offset must be >= 0");
                    sb.append(" LIMIT ").append(offset.getAsLong()).append(", 18446744073709551615");
                }
                return sb.toString();
            }
        };
        cachedPaginationGenerator = local;
        return local;
    }

    /**
     * MySQL/MariaDB:
     * {@code INSERT ... ON DUPLICATE KEY UPDATE col = VALUES(col), ...}.
     */
    @Override
    public org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator mergeGenerator() {
        var local = cachedMergeGenerator;
        if (local != null)
            return local;
        local = new org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator() {
            @Override
            public boolean supportsMerge() {
                return true;
            }

            @Override
            public java.util.Optional<String> upsert(UpsertSpec spec, java.util.List<String> values) {
                if (values == null || values.size() != spec.insertColumns().size()) {
                    throw new IllegalArgumentException("values must match insertColumns in length");
                }
                StringBuilder sb = new StringBuilder("INSERT INTO ").append(qualified(spec.target())).append(" (");
                appendQuotedCsv(sb, spec.insertColumns());
                sb.append(") VALUES (");
                for (int i = 0; i < values.size(); i++) {
                    if (i > 0)
                        sb.append(", ");
                    sb.append(values.get(i));
                }
                sb.append(")");
                if (!spec.updateColumns().isEmpty()) {
                    sb.append(" ON DUPLICATE KEY UPDATE ");
                    boolean first = true;
                    for (String c : spec.updateColumns()) {
                        if (!first)
                            sb.append(", ");
                        sb.append(quoteIdentifier(c)).append(" = VALUES(").append(quoteIdentifier(c)).append(")");
                        first = false;
                    }
                }
                return java.util.Optional.of(sb.toString());
            }
        };
        cachedMergeGenerator = local;
        return local;
    }

    /** MySQL silently ignores {@code CASCADE} on {@code DROP TABLE}. */
    @Override
    public boolean supportsDropTableCascade() {
        return false;
    }

    /** MySQL has no SQL sequences — use {@code AUTO_INCREMENT} columns. */
    @Override
    public boolean supportsSequences() {
        return false;
    }

    /**
     * MySQL/MariaDB: index names are table-scoped —
     * {@code DROP INDEX name ON table}.
     */
    @Override
    public boolean dropIndexRequiresTable() {
        return true;
    }

    /**
     * MySQL rejects {@code IF NOT EXISTS} on {@code CREATE INDEX} (MariaDB accepts
     * it).
     */
    @Override
    public boolean supportsCreateIndexIfNotExists() {
        return false;
    }

    @Override
    public org.eclipse.daanse.sql.dialect.api.generator.CteGenerator cteGenerator() {
        var local = cachedCteGenerator;
        if (local != null)
            return local;
        boolean supports = org.eclipse.daanse.sql.dialect.api.DialectVersion.UNKNOWN.equals(dialectVersion)
                || dialectVersion.atLeast(8, 0);
        local = new org.eclipse.daanse.sql.dialect.api.generator.CteGenerator() {
            @Override
            public boolean supportsRecursiveCte() {
                return supports;
            }
        };
        cachedCteGenerator = local;
        return local;
    }

    /**
     * MySQL rejects {@code IF EXISTS} on {@code DROP INDEX} (MariaDB accepts it).
     */
    @Override
    public boolean supportsDropIndexIfExists() {
        return false;
    }

    /** MySQL rejects {@code IF EXISTS} on {@code ALTER TABLE … DROP CONSTRAINT}. */
    @Override
    public boolean supportsDropConstraintIfExists() {
        return false;
    }

    /**
     * @param metaData DatabaseMetaData
     * @return Whether this is Infobright
     */
    public static boolean isInfobright(DatabaseMetaData metaData) {
        // Infobright detection is currently disabled. A separate Infobright dialect
        // or a configurable flag could be added if Infobright support is needed.
        // Detection would require querying for the BRIGHTHOUSE engine presence.
        return false;
    }

    @Override
    public void appendHintsAfterFromClause(StringBuilder buf, Map<String, String> hints) {
        if (hints != null) {
            String forcedIndex = hints.get("force_index");
            if (forcedIndex != null) {
                buf.append(" FORCE INDEX (");
                buf.append(forcedIndex);
                buf.append(")");
            }
        }
    }

    /**
     * MySQL spells statement hints as an optimizer block directly after the {@code SELECT}
     * keyword: {@code /*+ name(arg1, arg2) name2 *}{@code /} (trailing space). Hints are
     * joined by a space; a hint without arguments is the bare name. Any {@code *}{@code /}
     * inside a name or argument is stripped so the block cannot terminate early.
     */
    @Override
    public StringBuilder selectHint(List<StatementHint> hints) {
        if (hints.isEmpty()) {
            return new StringBuilder();
        }
        StringBuilder sb = new StringBuilder("/*+ ");
        boolean first = true;
        for (StatementHint hint : hints) {
            if (!first) {
                sb.append(' ');
            }
            first = false;
            sb.append(stripCommentEnd(hint.name()));
            if (!hint.arguments().isEmpty()) {
                sb.append('(');
                for (int i = 0; i < hint.arguments().size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(stripCommentEnd(hint.arguments().get(i)));
                }
                sb.append(')');
            }
        }
        return sb.append(" */ ");
    }

    /** Neutralizes a premature comment terminator inside a hint name/argument. */
    private static String stripCommentEnd(String s) {
        return s.replace("*/", "");
    }

    @Override
    public boolean requiresAliasForFromQuery() {
        return true;
    }

    @Override
    public boolean allowsFromQuery() {
        // MySQL before 4.0 does not allow FROM subqueries in the FROM clause.
        // Compare numerically: the former lexicographic productVersion
        // comparison against "4." misclassified two-digit majors (MariaDB
        // "10.x"/"11.x" < "4." as strings) as pre-4.0.
        return dialectVersion.isUnknownOrAtLeast(4, 0);
    }

    @Override
    public boolean allowsCompoundCountDistinct() {
        return true;
    }

    @Override
    public void quoteStringLiteral(StringBuilder buf, String s) {
        // Go beyond standard singleQuoteString; also quote backslash.
        buf.append('\'');
        String s0 = s.replace("'", "''");
        String s1 = s0.replace("\\", "\\\\");
        buf.append(s1);
        buf.append('\'');
    }

    @Override
    public void quoteBooleanLiteral(StringBuilder buf, String value) {
        if (!value.equalsIgnoreCase("1") && !(value.equalsIgnoreCase("0"))) {
            super.quoteBooleanLiteral(buf, value);
        } else {
            buf.append(value);
        }
    }

    @Override
    public StringBuilder generateInline(List<String> columnNames, List<String> columnTypes, List<String[]> valueList) {
        return generateInlineGeneric(columnNames, columnTypes, valueList, null, false);
    }

    @Override
    public StringBuilder generateOrderByNulls(CharSequence expr, boolean ascending, boolean collateNullsLast) {
        // In MYSQL, Null values are worth negative infinity.
        return DialectUtil.generateOrderByNullsWithIsnull(expr, ascending, collateNullsLast);
    }

    @Override
    public boolean requiresHavingAlias() {
        return true;
    }

    @Override
    public boolean supportsMultiValueInExpr() {
        return true;
    }

    private enum Scope {
        SESSION, GLOBAL
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

        // We might have to use case-insensitive matching
        javaRegex = DialectUtil.cleanUnicodeAwareCaseFlag(javaRegex);
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
        sb.append(source);
        sb.append(" IS NOT NULL AND ");
        if (caseSensitive) {
            sb.append(source);
        } else {
            sb.append("UPPER(");
            sb.append(source);
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

    /**
     * @return true when MySQL version is 5.7 or larger
     */
    @Override
    public boolean requiresOrderByAlias() {
        // Compare numerically: the former lexicographic productVersion comparison
        // against "5.7" misclassified two-digit majors (MariaDB "10.x"/"11.x" < "5.7"
        // as strings) as pre-5.7. MySQL byte-neutral: majors 4-9 agree in both forms.
        return dialectVersion.isUnknownOrAtLeast(5, 7);
    }

    @Override
    public String name() {
        return SUPPORTED_PRODUCT_NAME.toLowerCase();
    }

    @Override
    public org.eclipse.daanse.sql.dialect.api.IdentifierCaseFolding caseFolding() {
        return org.eclipse.daanse.sql.dialect.api.IdentifierCaseFolding.PRESERVE;
    }

    @Override
    public StringBuilder generateKnownFunction(KnownFunction function, List<? extends CharSequence> arguments) {
        return switch (function) {
        case CONCAT -> {
            // MySQL treats || as logical OR (unless PIPES_AS_CONCAT); use CONCAT(...).
            if (arguments.size() < 2) {
                throw new IllegalArgumentException("CONCAT expects 2 or more argument(s), got " + arguments.size());
            }
            StringBuilder sb = new StringBuilder("CONCAT(");
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(arguments.get(i));
            }
            yield sb.append(")");
        }
        case INDEX_OF -> {
            if (arguments.size() != 2) {
                throw new IllegalArgumentException("INDEX_OF expects 2 argument(s), got " + arguments.size());
            }
            yield new StringBuilder("LOCATE(").append(arguments.get(0)).append(", ").append(arguments.get(1))
                    .append(")");
        }
        // LENGTH stays CHAR_LENGTH: MySQL's LENGTH() counts bytes, CHAR_LENGTH() characters.
        default -> super.generateKnownFunction(function, arguments);
        };
    }

    // Unified BitOperation methods

    @Override
    public java.util.Optional<String> generateBitAggregation(BitOperation operation, CharSequence operand) {
        StringBuilder buf = new StringBuilder(64);
        StringBuilder result = switch (operation) {
        case AND -> buf.append("BIT_AND(").append(operand).append(")");
        case OR -> buf.append("BIT_OR(").append(operand).append(")");
        case XOR -> buf.append("BIT_XOR(").append(operand).append(")");
        case NAND -> buf.append("NOT(BIT_AND(").append(operand).append("))");
        case NOR -> buf.append("NOT(BIT_OR(").append(operand).append("))");
        case NXOR -> buf.append("NOT(BIT_XOR(").append(operand).append("))");
        };
        return java.util.Optional.of(result.toString());
    }

    @Override
    public boolean supportsBitAggregation(BitOperation operation) {
        return true; // MySQL supports all bit operations
    }

    @Override
    public java.util.Optional<String> generatePercentileDisc(double percentile, boolean desc, String tableName,
            String columnName) {
        return java.util.Optional
                .of((buildPercentileFunction("PERCENTILE_DISC", percentile, desc, tableName, columnName)).toString());
    }

    @Override
    public java.util.Optional<String> generatePercentileCont(double percentile, boolean desc, String tableName,
            String columnName) {
        return java.util.Optional
                .of((buildPercentileFunction("PERCENTILE_CONT", percentile, desc, tableName, columnName)).toString());
    }

    @Override
    public boolean supportsPercentileDisc() {
        // Numeric compare — see requiresOrderByAlias (MariaDB "10.x"/"11.x" vs "8.0").
        return dialectVersion.isUnknownOrAtLeast(8, 0);
    }

    @Override
    public boolean supportsPercentileCont() {
        // Numeric compare — see requiresOrderByAlias (MariaDB "10.x"/"11.x" vs "8.0").
        return dialectVersion.isUnknownOrAtLeast(8, 0);
    }

    @Override
    public java.util.Optional<String> generateListAgg(CharSequence operand, boolean distinct, String separator,
            String coalesce, String onOverflowTruncate, List<OrderedColumn> columns) {
        StringBuilder buf = new StringBuilder(64);
        buf.append("GROUP_CONCAT");
        buf.append("( ");
        if (distinct) {
            buf.append("DISTINCT ");
        }
        buf.append(operand);
        if (columns != null && !columns.isEmpty()) {
            buf.append(" ORDER BY ");
            buf.append(buildOrderedColumnsClause(columns));
        }
        if (separator != null) {
            buf.append(" SEPARATOR '").append(separator).append("'");
        }
        buf.append(")");
        // GROUP_CONCAT(DISTINCT cate_id ORDER BY cate_id ASC SEPARATOR ' ')
        return java.util.Optional.of((buf).toString());
    }

    @Override
    public java.util.Optional<String> generateNthValueAgg(CharSequence operand, boolean ignoreNulls, Integer n,
            List<OrderedColumn> columns) {
        return java.util.Optional
                .of((buildNthValueFunction("NTH_VALUE", operand, ignoreNulls, n, columns, false)).toString());
    }

    @Override
    public boolean supportsNthValue() {
        return true;
    }

    @Override
    public boolean supportsListAgg() {
        return true;
    }

    // -------------------- Trigger procedures --------------------

    @Override
    public Optional<String> createTriggerProcedure(String procedureName, String schemaName, String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body must not be blank for MySQL trigger procedure");
        }
        String qualified = schemaName != null && !schemaName.isBlank()
                ? quoteIdentifier(schemaName, procedureName).toString()
                : quoteIdentifier(procedureName).toString();
        return Optional.of("CREATE PROCEDURE " + qualified + "() " + body);
    }

    @Override
    public String createTriggerUsingProcedure(String triggerName, String schemaName,
            org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming timing,
            org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent event,
            org.eclipse.daanse.sql.model.schema.TableReference table,
            org.eclipse.daanse.sql.model.schema.Trigger.TriggerScope scope, String whenCondition,
            String procedureName) {
        String qualified = schemaName != null && !schemaName.isBlank()
                ? quoteIdentifier(schemaName, procedureName).toString()
                : quoteIdentifier(procedureName).toString();
        return createTrigger(triggerName, timing, event, table, scope, whenCondition, "CALL " + qualified + "()");
    }

    /** MySQL/MariaDB: {@code DROP PROCEDURE [IF EXISTS] schema.procedureName}. */
    @Override
    public Optional<String> dropProcedure(String procedureName, String schemaName, boolean ifExists) {
        String qualified = schemaName != null && !schemaName.isBlank()
                ? quoteIdentifier(schemaName, procedureName).toString()
                : quoteIdentifier(procedureName).toString();
        return Optional.of("DROP PROCEDURE " + (ifExists ? "IF EXISTS " : "") + qualified);
    }

    // -------------------- DDL — ALTER (MySQL: MODIFY syntax) --------------------

    /** MySQL: {@code ALTER TABLE x MODIFY COLUMN c <type>}. */
    @Override
    public String alterColumnType(TableReference table, String columnName, ColumnMetaData newMeta) {
        if (newMeta == null) {
            throw new IllegalArgumentException("newMeta must not be null for ALTER COLUMN");
        }
        return new StringBuilder("ALTER TABLE ").append(qualified(table)).append(" MODIFY COLUMN ")
                .append(quoteIdentifier(columnName)).append(' ').append(nativeType(newMeta)).toString();
    }

    @Override
    public String alterColumnSetNullability(TableReference table, String columnName, boolean nullable) {
        throw new UnsupportedOperationException("MySQL requires the column type when altering nullability — use the "
                + "alterColumnSetNullability(table, column, nullable, currentMeta) overload.");
    }

    @Override
    public String alterColumnSetNullability(TableReference table, String columnName, boolean nullable,
            ColumnMetaData currentMeta) {
        if (currentMeta == null) {
            throw new IllegalArgumentException("currentMeta must not be null for MySQL ALTER COLUMN");
        }
        return new StringBuilder("ALTER TABLE ").append(qualified(table)).append(" MODIFY COLUMN ")
                .append(quoteIdentifier(columnName)).append(' ').append(nativeType(currentMeta))
                .append(nullable ? " NULL" : " NOT NULL").toString();
    }

    // SET DEFAULT / DROP DEFAULT inherit the SQL-99 default — MySQL 8.0+ accepts
    // it.

    /**
     * MySQL: {@code ALTER TABLE x RENAME INDEX old TO new} — index names are
     * table-scoped.
     */
    @Override
    public String renameIndex(String oldName, String newName, TableReference table) {
        if (table == null) {
            throw new IllegalArgumentException("table must not be null for MySQL RENAME INDEX");
        }
        return new StringBuilder("ALTER TABLE ").append(qualified(table)).append(" RENAME INDEX ")
                .append(quoteIdentifier(oldName)).append(" TO ").append(quoteIdentifier(newName)).toString();
    }

    /** MySQL has no constraint rename. */
    @Override
    public String renameConstraint(TableReference table, String oldName, String newName) {
        return null;
    }

    private static org.eclipse.daanse.sql.dialect.api.DialectInitData initDataFor(java.sql.Connection c) {
        try {
            return org.eclipse.daanse.sql.dialect.api.DialectInitData.fromConnection(c);
        } catch (java.sql.SQLException e) {
            return org.eclipse.daanse.sql.dialect.api.DialectInitData.ansiDefaults();
        }
    }
}
