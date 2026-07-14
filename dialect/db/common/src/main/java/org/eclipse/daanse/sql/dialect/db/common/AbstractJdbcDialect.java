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
package org.eclipse.daanse.sql.dialect.db.common;

import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.api.IdentifierQuotingPolicy;
import org.eclipse.daanse.sql.dialect.api.capability.AggregateCapabilities;
import org.eclipse.daanse.sql.dialect.api.capability.JoinCapabilities;
import org.eclipse.daanse.sql.dialect.api.capability.OrderByCapabilities;
import org.eclipse.daanse.sql.dialect.api.capability.WindowFunctionCapabilities;
import org.eclipse.daanse.sql.dialect.api.generator.AggregationGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.FunctionGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.HintGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.OrderByGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.RegexGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.SqlGenerator;
import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.sql.model.type.Datatype;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jhyde
 * @since Oct 10, 2008
 */
public abstract class AbstractJdbcDialect implements Dialect, SqlGenerator, DdlGenerator, OrderByGenerator,
        RegexGenerator, AggregationGenerator, FunctionGenerator, HintGenerator {

    @Override
    public SqlGenerator sqlGenerator() {
        return this;
    }

    @Override
    public DdlGenerator ddlGenerator() {
        return this;
    }

    @Override
    public OrderByGenerator orderByGenerator() {
        return this;
    }

    @Override
    public RegexGenerator regexGenerator() {
        return this;
    }

    @Override
    public AggregationGenerator aggregationGenerator() {
        return this;
    }

    @Override
    public FunctionGenerator functionGenerator() {
        return this;
    }

    @Override
    public HintGenerator hintGenerator() {
        return this;
    }

    // SQL keyword constants moved with their consumers: ORDER_BY/WITHIN_GROUP/
    // OVER/IGNORE_NULLS/RESPECT_NULLS/COMMA/OPEN_PAREN/CLOSE_PAREN/DESC live on
    // AggregationGenerator; CASE_WHEN/DESC/ASC live on OrderByGenerator.

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractJdbcDialect.class);

    private static final int[] RESULT_SET_TYPE_VALUES = { ResultSet.TYPE_FORWARD_ONLY,
            ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.TYPE_SCROLL_SENSITIVE };

    private static final int[] CONCURRENCY_VALUES = { ResultSet.CONCUR_READ_ONLY, ResultSet.CONCUR_UPDATABLE };
    private String quoteIdentifierString = "";

    private String productName = "";

    protected String productVersion = "";

    protected org.eclipse.daanse.sql.dialect.api.DialectVersion dialectVersion = org.eclipse.daanse.sql.dialect.api.DialectVersion.UNKNOWN;

    private Set<List<Integer>> supportedResultSetTypes = null;

    private boolean readOnly = true;

    private int maxColumnNameLength = 0;

    private IdentifierQuotingPolicy quotingPolicy = IdentifierQuotingPolicy.ALWAYS;

    private Set<String> sqlKeywordsLower = Set.of();

    private final JdbcCapabilityFlags caps = new JdbcCapabilityFlags(this);
    private final JdbcDdlEmitter ddl = new JdbcDdlEmitter(this);
    private final JdbcLiteralQuoter literals = new JdbcLiteralQuoter(new JdbcLiteralQuoter.Hooks() {
        @Override
        public void quoteDateLiteral(StringBuilder buf, java.sql.Date date) {
            AbstractJdbcDialect.this.quoteDateLiteral(buf, date);
        }

        @Override
        public void quoteTimestampLiteral(StringBuilder buf, String value, java.sql.Timestamp timestamp) {
            AbstractJdbcDialect.this.quoteTimestampLiteral(buf, value, timestamp);
        }
    });
    private final JdbcInlineDataGenerator inline = new JdbcInlineDataGenerator(this);
    private final JdbcIdentifierQuoter identQuoter = new JdbcIdentifierQuoter(() -> quoteIdentifierString,
            () -> quotingPolicy, this::caseFolding, () -> sqlKeywordsLower, this::needsQuoting);

    private final JdbcCapabilityRecords capRecords = new JdbcCapabilityRecords(this);

    // SINGLE_QUOTE_SIZE / DOUBLE_QUOTE_SIZE / TRIVIAL_IDENTIFIER moved to
    // JdbcIdentifierQuoter

    // DEFAULT_TYPE_MAP moved to JdbcTypeMapper

    protected void setQuoteIdentifierString(String quote) {
        this.quoteIdentifierString = quote;
    }

    public AbstractJdbcDialect(org.eclipse.daanse.sql.dialect.api.DialectInitData init) {
        if (init == null) {
            init = org.eclipse.daanse.sql.dialect.api.DialectInitData.ansiDefaults();
        }
        applyInit(init);
    }

    private void applyInit(org.eclipse.daanse.sql.dialect.api.DialectInitData init) {
        // Pass null through directly: null means "driver doesn't support identifier
        // quoting" and emitIdentifier emits verbatim in that case.
        this.quoteIdentifierString = init.quoteIdentifierString();
        this.productName = init.productName();
        this.productVersion = init.productVersion();
        this.dialectVersion = init.version();
        this.supportedResultSetTypes = init.supportedResultSetStyles();
        this.readOnly = init.readOnly();
        if (init.maxColumnNameLength() > 0) {
            this.maxColumnNameLength = init.maxColumnNameLength();
        }
        java.util.Set<String> combined = new HashSet<>(SQL92_RESERVED_LOWER);
        combined.addAll(init.sqlKeywordsLower());
        this.sqlKeywordsLower = java.util.Set.copyOf(combined);
        if (init.quotingPolicy() != null) {
            setQuotingPolicy(init.quotingPolicy());
        }
    }

    private static Set<String> deduceSqlKeywords(DatabaseMetaData metaData) {
        Set<String> result = new HashSet<>(SQL92_RESERVED_LOWER);
        try {
            String csv = metaData.getSQLKeywords();
            if (csv != null && !csv.isEmpty()) {
                for (String kw : csv.split(",")) {
                    String trimmed = kw.trim().toLowerCase(java.util.Locale.ROOT);
                    if (!trimmed.isEmpty()) {
                        result.add(trimmed);
                    }
                }
            }
        } catch (SQLException ignored) {
            // Driver doesn't support getSQLKeywords; fall back to SQL-92 baseline.
        }
        return Set.copyOf(result);
    }

    private static final Set<String> SQL92_RESERVED_LOWER = Set.of("all", "and", "any", "as", "asc", "between", "by",
            "case", "check", "column", "create", "cross", "current_date", "current_time", "current_timestamp",
            "default", "delete", "desc", "distinct", "drop", "else", "end", "escape", "except", "exists", "false",
            "for", "foreign", "from", "full", "group", "having", "in", "inner", "insert", "intersect", "into", "is",
            "join", "key", "left", "like", "natural", "not", "null", "on", "or", "order", "outer", "primary",
            "references", "right", "select", "set", "some", "table", "then", "true", "union", "unique", "update",
            "user", "using", "values", "view", "when", "where", "with");

    @Override
    public boolean allowsDialectSharing() {
        return caps.allowsDialectSharing();
    }

    protected boolean supportsNullsOrdering() {
        return false;
    }

    @Override
    public String quoteIdentifier(final CharSequence val) {
        return identQuoter.quote(val);
    }

    @Override
    public void quoteIdentifier(final String val, final StringBuilder buf) {
        identQuoter.quote(val, buf);
    }

    @Override
    public void quoteIdentifierWith(final String val, final StringBuilder buf, IdentifierQuotingPolicy policy) {
        identQuoter.quoteWith(val, buf, policy);
    }

    @Override
    public String quoteIdentifierWith(CharSequence val, IdentifierQuotingPolicy policy) {
        return identQuoter.quoteWith(val, policy);
    }

    @Override
    public IdentifierQuotingPolicy quotingPolicy() {
        return quotingPolicy;
    }

    public void setQuotingPolicy(IdentifierQuotingPolicy policy) {
        this.quotingPolicy = (policy == null) ? IdentifierQuotingPolicy.ALWAYS : policy;
    }

    @Override
    public String quoteIdentifierIfNeeded(CharSequence val) {
        return identQuoter.quoteIfNeeded(val);
    }

    /** Subclass-overridable hook — default delegates to the helper. */
    protected boolean needsQuoting(String val) {
        return identQuoter.defaultNeedsQuoting(val);
    }

    @Override
    public String quoteIdentifier(final String qual, final String name) {
        return identQuoter.quoteQualified(qual, name);
    }

    @Override
    public void quoteIdentifier(final StringBuilder buf, final String... names) {
        identQuoter.quoteParts(buf, names);
    }

    @Override
    public String getQuoteIdentifierString() {
        return quoteIdentifierString;
    }

    @Override
    public void quoteStringLiteral(StringBuilder buf, String s) {
        literals.quoteString(buf, s);
    }

    @Override
    public void quoteNumericLiteral(StringBuilder buf, String value) {
        literals.quoteNumeric(buf, value);
    }

    @Override
    public StringBuilder quoteDecimalLiteral(CharSequence value) {
        return literals.quoteDecimal(value);
    }

    @Override
    public void quoteBooleanLiteral(StringBuilder buf, String value) {
        literals.quoteBoolean(buf, value);
    }

    @Override
    public void quoteDateLiteral(StringBuilder buf, String value) {
        literals.quoteDate(buf, value);
    }

    /** Subclass-overridable hook for date-literal emission. */
    protected void quoteDateLiteral(StringBuilder buf, Date date) {
        JdbcLiteralQuoter.defaultQuoteDateLiteral(buf, date);
    }

    @Override
    public void quoteTimeLiteral(StringBuilder buf, String value) {
        literals.quoteTime(buf, value);
    }

    @Override
    public void quoteTimestampLiteral(StringBuilder buf, String value) {
        literals.quoteTimestamp(buf, value);
    }

    /** Subclass-overridable hook for timestamp-literal emission. */
    protected void quoteTimestampLiteral(StringBuilder buf, String value, Timestamp timestamp) {
        JdbcLiteralQuoter.defaultQuoteTimestampLiteral(buf, value, timestamp);
    }

    @Override
    public boolean requiresAliasForFromQuery() {
        return caps.requiresAliasForFromQuery();
    }

    @Override
    public boolean allowsFromAlias() {
        return caps.allowsFromAlias();
    }

    @Override
    public boolean allowsFromQuery() {
        return caps.allowsFromQuery();
    }

    @Override
    public boolean allowsCompoundCountDistinct() {
        return caps.allowsCompoundCountDistinct();
    }

    @Override
    public boolean allowsCountDistinct() {
        return caps.allowsCountDistinct();
    }

    @Override
    public boolean allowsMultipleCountDistinct() {
        return caps.allowsMultipleCountDistinct();
    }

    @Override
    public boolean allowsMultipleDistinctSqlMeasures() {
        return caps.allowsMultipleDistinctSqlMeasures();
    }

    @Override
    public boolean allowsCountDistinctWithOtherAggs() {
        return caps.allowsCountDistinctWithOtherAggs();
    }

    @Override
    public StringBuilder generateInline(List<String> columnNames, List<String> columnTypes, List<String[]> valueList) {
        return inline.generateInline(columnNames, columnTypes, valueList);
    }

    /** @return Expression that returns the given values */
    protected StringBuilder generateInlineGeneric(List<String> columnNames, List<String> columnTypes,
            List<String[]> valueList, String fromClause, boolean cast) {
        return inline.generateInlineGeneric(columnNames, columnTypes, valueList, fromClause, cast);
    }

    /** @return Expression that returns the given values via SQL-2003 VALUES */
    public StringBuilder generateInlineForAnsi(String alias, List<String> columnNames, List<String> columnTypes,
            List<String[]> valueList, boolean cast) {
        return inline.generateInlineForAnsi(alias, columnNames, columnTypes, valueList, cast);
    }

    @Override
    public boolean needsExponent(Object value, String valueString) {
        return false;
    }

    @Override
    public void quote(StringBuilder buf, Object value, Datatype datatype) {
        inline.quote(buf, value, datatype);
    }

    @Override
    public boolean supportsDdl() {
        return !readOnly;
    }

    @Override
    public boolean supportsGroupByExpressions() {
        return caps.supportsGroupByExpressions();
    }

    // allowsSelectNotInGroupBy(): inherits the interface default (false).
    // Dialects that allow MySQL-style implicit ANY (Hive, MySQL with non-strict
    // SQL_MODE) can override directly.

    @Override
    public boolean allowsJoinOn() {
        return caps.allowsJoinOn();
    }

    @Override
    public boolean supportsGroupingSets() {
        return caps.supportsGroupingSets();
    }

    @Override
    public boolean supportsUnlimitedValueList() {
        return caps.supportsUnlimitedValueList();
    }

    @Override
    public boolean requiresGroupByAlias() {
        return caps.requiresGroupByAlias();
    }

    @Override
    public boolean requiresOrderByAlias() {
        return caps.requiresOrderByAlias();
    }

    @Override
    public boolean requiresHavingAlias() {
        return caps.requiresHavingAlias();
    }

    @Override
    public boolean allowsOrderByAlias() {
        return caps.allowsOrderByAlias();
    }

    @Override
    public boolean requiresUnionOrderByOrdinal() {
        return caps.requiresUnionOrderByOrdinal();
    }

    @Override
    public boolean requiresUnionOrderByExprInSelect() {
        return caps.requiresUnionOrderByExprInSelect();
    }

    @Override
    public boolean supportsMultiValueInExpr() {
        return caps.supportsMultiValueInExpr();
    }

    @Override
    public boolean supportsResultSetConcurrency(int type, int concurrency) {
        return supportedResultSetTypes.contains(Arrays.asList(type, concurrency));
    }

    @Override
    public String toString() {
        return productName;
    }

    @Override
    public int getMaxColumnNameLength() {
        return maxColumnNameLength;
    }

    @Override
    public boolean allowsRegularExpressionInWhereClause() {
        return caps.allowsRegularExpressionInWhereClause();
    }

    @Override
    public BestFitColumnType getType(ResultSetMetaData metaData, int columnIndex) throws SQLException {
        BestFitColumnType internalType = JdbcTypeMapper.resolveType(metaData, columnIndex);
        logTypeInfo(metaData, columnIndex, internalType);
        return internalType;
    }

    protected void logTypeInfo(ResultSetMetaData metaData, int columnIndex, BestFitColumnType internalType)
            throws SQLException {
        if (LOGGER.isDebugEnabled()) {
            final int columnType = metaData.getColumnType(columnIndex + 1);
            final int precision = metaData.getPrecision(columnIndex + 1);
            final int scale = metaData.getScale(columnIndex + 1);
            final String columnName = metaData.getColumnName(columnIndex + 1);
            LOGGER.debug(new StringBuilder("AbstractJdbcDialect.getType ").append("Dialect- ").append(this.name())
                    .append(", Column-").append(columnName).append(" is of internal type ").append(internalType)
                    .append(". JDBC type was ").append(columnType).append(".  Column precision=").append(precision)
                    .append(".  Column scale=").append(scale).toString());
        }
    }

    @Override
    public boolean requiresDrillthroughMaxRowsInLimit() {
        return caps.requiresDrillthroughMaxRowsInLimit();
    }

    @Override
    public boolean allowsFieldAlias() {
        return caps.allowsFieldAlias();
    }

    @Override
    public boolean allowsInnerDistinct() {
        return caps.allowsInnerDistinct();
    }

    @Override
    public String clearTable(String schemaName, String tableName) {
        return ddl.clearTable(schemaName, tableName);
    }

    @Override
    public String dropTable(String schemaName, String tableName, boolean ifExists) {
        return ddl.dropTable(schemaName, tableName, ifExists);
    }

    @Override
    public String createSchema(String schemaName, boolean ifNotExists) {
        return ddl.createSchema(schemaName, ifNotExists);
    }

    @Override
    public String dropSchema(String schemaName, boolean ifExists, boolean cascade) {
        return ddl.dropSchema(schemaName, ifExists, cascade);
    }

    @Override
    public boolean supportsParallelLoading() {
        return caps.supportsParallelLoading();
    }

    @Override
    public boolean supportsBatchOperations() {
        return caps.supportsBatchOperations();
    }

    protected Set<List<Integer>> deduceSupportedResultSetStyles(DatabaseMetaData databaseMetaData) {
        Set<List<Integer>> supports = new HashSet<List<Integer>>();
        for (int type : RESULT_SET_TYPE_VALUES) {
            for (int concurrency : CONCURRENCY_VALUES) {
                try {
                    if (databaseMetaData.supportsResultSetConcurrency(type, concurrency)) {
                        String driverName = databaseMetaData.getDriverName();
                        if (type != ResultSet.TYPE_FORWARD_ONLY
                                && driverName.equals("JDBC-ODBC Bridge (odbcjt32.dll)")) {
                            // In JDK 1.6, the Jdbc-Odbc bridge announces
                            // that it can handle TYPE_SCROLL_INSENSITIVE
                            // but it does so by generating a 'COUNT(*)'
                            // query, and this query is invalid if the query
                            // contains a single-quote. So, override the
                            // driver.
                            continue;
                        }
                        supports.add(new ArrayList<Integer>(Arrays.asList(type, concurrency)));
                    }
                } catch (SQLException e) {
                    // DB2 throws "com.ibm.db2.jcc.b.SqlException: Unknown type
                    // or Concurrency" for certain values of type/concurrency.
                    // No harm in interpreting all such exceptions as 'this
                    // database does not support this type/concurrency
                    // combination'.
                    // Util.discard(e);
                }
            }
        }
        return supports;
    }

    // Capability record caching for performance optimization

    @Override
    public AggregateCapabilities getAggregateCapabilities() {
        return capRecords.aggregate();
    }

    @Override
    public JoinCapabilities getJoinCapabilities() {
        return capRecords.join();
    }

    @Override
    public OrderByCapabilities getOrderByCapabilities() {
        return capRecords.orderBy(supportsNullsOrdering());
    }

    @Override
    public WindowFunctionCapabilities getWindowFunctionCapabilities() {
        return capRecords.windowFunction();
    }

}
