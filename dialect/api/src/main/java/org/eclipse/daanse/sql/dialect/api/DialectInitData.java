/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.api;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.sql.DataSource;

/**
 * @param quoteIdentifierString    e.g. {@code "}, {@code `}, {@code [}
 * @param productName              {@link DatabaseMetaData#getDatabaseProductName()}
 * @param productVersion           {@link DatabaseMetaData#getDatabaseProductVersion()}
 * @param databaseMajorVersion     {@link DatabaseMetaData#getDatabaseMajorVersion()}
 * @param databaseMinorVersion     {@link DatabaseMetaData#getDatabaseMinorVersion()}
 * @param supportedResultSetStyles encoded as {@code List<Integer>} pairs of
 *                                 {@code (type, concurrency)}
 * @param readOnly                 {@link DatabaseMetaData#isReadOnly()}
 * @param maxColumnNameLength      {@link DatabaseMetaData#getMaxColumnNameLength()}
 * @param sqlKeywordsLower         engine-specific reserved words from
 *                                 {@link DatabaseMetaData#getSQLKeywords()},
 *                                 lower-cased
 * @param quotingPolicy            initial policy; defaults to
 *                                 {@link IdentifierQuotingPolicy#ALWAYS} when
 *                                 unset
 */
public record DialectInitData(String quoteIdentifierString, String productName, String productVersion,
        int databaseMajorVersion, int databaseMinorVersion, Set<List<Integer>> supportedResultSetStyles,
        boolean readOnly, int maxColumnNameLength, Set<String> sqlKeywordsLower,
        IdentifierQuotingPolicy quotingPolicy) {

    /**
     * Defensive copies — record components stay immutable even if callers mutate
     * the input set.
     */
    public DialectInitData {
        supportedResultSetStyles = supportedResultSetStyles == null ? Set.of() : Set.copyOf(supportedResultSetStyles);
        sqlKeywordsLower = sqlKeywordsLower == null ? Set.of() : Set.copyOf(sqlKeywordsLower);
        if (quotingPolicy == null) {
            quotingPolicy = IdentifierQuotingPolicy.ALWAYS;
        }
    }

    public static DialectInitData ansiDefaults() {
        return new DialectInitData("\"", "", "", 0, 0, Set.of(), true, 0, Set.of(), IdentifierQuotingPolicy.ALWAYS);
    }

    public static DialectInitData fromConnection(Connection connection) throws SQLException {
        if (connection == null) {
            return ansiDefaults();
        }
        DatabaseMetaData md = connection.getMetaData();

        // null means "driver doesn't support identifier quoting" — emitIdentifier
        // honours this by emitting verbatim regardless of policy. We only fall
        // back to ANSI double-quote when the driver throws or returns blank.
        String quote = null;
        try {
            String q = md.getIdentifierQuoteString();
            if (q != null && !q.isEmpty() && !" ".equals(q))
                quote = q;
        } catch (SQLException ignore) {
            quote = "\"";
        }

        String productName = "";
        try {
            productName = nullToEmpty(md.getDatabaseProductName());
        } catch (SQLException ignore) {
            /* keep default */ }

        String productVersion = "";
        try {
            productVersion = nullToEmpty(md.getDatabaseProductVersion());
        } catch (SQLException ignore) {
            /* keep default */ }

        int major = 0;
        try {
            major = md.getDatabaseMajorVersion();
        } catch (SQLException | UnsupportedOperationException ignore) {
            /* keep 0 */ }

        int minor = 0;
        try {
            minor = md.getDatabaseMinorVersion();
        } catch (SQLException | UnsupportedOperationException ignore) {
            /* keep 0 */ }

        boolean readOnly = false;
        try {
            readOnly = md.isReadOnly();
        } catch (SQLException ignore) {
            /* keep default */ }

        int maxColLen = 0;
        try {
            maxColLen = md.getMaxColumnNameLength();
        } catch (SQLException ignore) {
            /* keep 0 */ }

        Set<String> keywords = new HashSet<>();
        try {
            String csv = md.getSQLKeywords();
            if (csv != null) {
                for (String kw : csv.split(",")) {
                    String t = kw.trim().toLowerCase(Locale.ROOT);
                    if (!t.isEmpty())
                        keywords.add(t);
                }
            }
        } catch (SQLException ignore) {
            /* leave empty */ }

        Set<List<Integer>> styles = new HashSet<>();
        for (int t : new int[] { ResultSet.TYPE_FORWARD_ONLY, ResultSet.TYPE_SCROLL_INSENSITIVE,
                ResultSet.TYPE_SCROLL_SENSITIVE }) {
            for (int c : new int[] { ResultSet.CONCUR_READ_ONLY, ResultSet.CONCUR_UPDATABLE }) {
                try {
                    if (md.supportsResultSetConcurrency(t, c))
                        styles.add(List.of(t, c));
                } catch (SQLException ignore) {
                    /* skip */ }
            }
        }

        return new DialectInitData(quote, productName, productVersion, major, minor, styles, readOnly, maxColLen,
                keywords, IdentifierQuotingPolicy.ALWAYS);
    }

    public static DialectInitData fromDataSource(DataSource dataSource) throws SQLException {
        if (dataSource == null) {
            return ansiDefaults();
        }
        try (Connection c = dataSource.getConnection()) {
            return fromConnection(c);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * {@link DialectVersion} view of {@link #databaseMajorVersion} /
     * {@link #databaseMinorVersion}.
     */
    public DialectVersion version() {
        return new DialectVersion(databaseMajorVersion, databaseMinorVersion);
    }

    /** Returns a copy with a different {@link IdentifierQuotingPolicy}. */
    public DialectInitData withQuotingPolicy(IdentifierQuotingPolicy policy) {
        return new DialectInitData(quoteIdentifierString, productName, productVersion, databaseMajorVersion,
                databaseMinorVersion, supportedResultSetStyles, readOnly, maxColumnNameLength, sqlKeywordsLower,
                policy);
    }

    /**
     * Returns a copy with a different identifier quote string (e.g. {@code `} for
     * MySQL).
     */
    public DialectInitData withQuoteIdentifierString(String quote) {
        return new DialectInitData(quote, productName, productVersion, databaseMajorVersion, databaseMinorVersion,
                supportedResultSetStyles, readOnly, maxColumnNameLength, sqlKeywordsLower, quotingPolicy);
    }

    /** Returns a copy with a different reserved-word set. */
    public DialectInitData withSqlKeywordsLower(Set<String> keywords) {
        return new DialectInitData(quoteIdentifierString, productName, productVersion, databaseMajorVersion,
                databaseMinorVersion, supportedResultSetStyles, readOnly, maxColumnNameLength, keywords, quotingPolicy);
    }

    /**
     * @param major database major version (≥ 0; 0 means "unknown")
     * @param minor database minor version (≥ 0)
     * @return a new {@code DialectInitData} with the specified version
     */
    public DialectInitData withVersion(int major, int minor) {
        return new DialectInitData(quoteIdentifierString, productName, productVersion, major, minor,
                supportedResultSetStyles, readOnly, maxColumnNameLength, sqlKeywordsLower, quotingPolicy);
    }
}
