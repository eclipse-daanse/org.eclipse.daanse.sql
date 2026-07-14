/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.common;

import java.util.List;
import java.util.Map;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.model.type.Datatype;

/**
 * Default emission of inline data ({@code SELECT … UNION ALL} and SQL-2003
 * {@code VALUES} forms) plus per-cell {@code quote(buf, value, datatype)}.
 * Calls back to the dialect for identifier quoting, alias-allowed flag, and the
 * exponent-needed hook.
 */
final class JdbcInlineDataGenerator {

    private final Dialect dialect;

    JdbcInlineDataGenerator(Dialect dialect) {
        this.dialect = dialect;
    }

    StringBuilder generateInline(List<String> columnNames, List<String> columnTypes, List<String[]> valueList) {
        return generateInlineForAnsi("t", columnNames, columnTypes, valueList, false);
    }

    StringBuilder generateInlineGeneric(List<String> columnNames, List<String> columnTypes, List<String[]> valueList,
            String fromClause, boolean cast) {
        final StringBuilder buf = new StringBuilder();
        int columnCount = columnNames.size();
        assert columnTypes.size() == columnCount;

        Integer[] maxLengths = new Integer[columnCount];
        if (cast) {
            fillMaxLengthsArray(maxLengths, columnTypes, valueList);
        }

        for (int i = 0; i < valueList.size(); i++) {
            if (i > 0) {
                buf.append(" union all ");
            }
            String[] values = valueList.get(i);
            buf.append("select ");
            formSelectFieldsForInlineGeneric(buf, values, columnTypes, columnNames, maxLengths);
            if (fromClause != null) {
                buf.append(fromClause);
            }
        }
        return buf;
    }


    StringBuilder generateInlineForAnsi(String alias, List<String> columnNames, List<String> columnTypes,
            List<String[]> valueList, boolean cast) {
        final StringBuilder buf = new StringBuilder();
        buf.append("SELECT * FROM (VALUES ");
        String[] castTypes = null;
        if (cast) {
            castTypes = getCastTypes(columnNames, columnTypes, valueList);
        }
        formSelectFieldsForInlineForAnsi(buf, valueList, columnTypes, castTypes);
        buf.append(") AS ");
        dialect.quoteIdentifier(alias, buf);
        buf.append(" (");
        for (int j = 0; j < columnNames.size(); j++) {
            final String columnName = columnNames.get(j);
            if (j > 0) {
                buf.append(", ");
            }
            dialect.quoteIdentifier(columnName, buf);
        }
        buf.append(")");
        return buf;
    }

    void quote(StringBuilder buf, Object value, Datatype datatype) {
        if (value == null) {
            buf.append("null");
        } else {
            String valueString = value.toString();
            if (dialect.needsExponent(value, valueString)) {
                valueString += "E0";
            }
            dialect.quoteLiteral(datatype, buf, valueString);
        }
    }

    private void formSelectFieldsForInlineGeneric(StringBuilder buf, String[] values, List<String> columnTypes,
            List<String> columnNames, Integer[] maxLengths) {
        for (int j = 0; j < values.length; j++) {
            String value = values[j];
            if (j > 0) {
                buf.append(", ");
            }
            final String columnType = columnTypes.get(j);
            final String columnName = columnNames.get(j);
            Datatype datatype = Datatype.fromValue(columnType);
            final Integer maxLength = maxLengths[j];
            if (maxLength != null) {
                buf.append("CAST(");
                quote(buf, value, datatype);
                buf.append(" AS VARCHAR(").append(maxLength).append("))");
            } else {
                quote(buf, value, datatype);
            }
            if (dialect.allowsFromAlias()) {
                buf.append(" as ");
            } else {
                buf.append(' ');
            }
            dialect.quoteIdentifier(columnName, buf);
        }
    }

    private void formSelectFieldsForInlineForAnsi(StringBuilder buf, List<String[]> valueList, List<String> columnTypes,
            String[] castTypes) {
        for (int i = 0; i < valueList.size(); i++) {
            if (i > 0) {
                buf.append(", ");
            }
            String[] values = valueList.get(i);
            buf.append("(");
            for (int j = 0; j < values.length; j++) {
                String value = values[j];
                if (j > 0) {
                    buf.append(", ");
                }
                final String columnType = columnTypes.get(j);
                Datatype datatype = Datatype.fromValue(columnType);
                if (value == null) {
                    String sqlType = guessSqlType(columnType, valueList, j);
                    buf.append("CAST(NULL AS ").append(sqlType).append(")");
                } else if (castTypes != null && castTypes[j] != null) {
                    buf.append("CAST(");
                    quote(buf, value, datatype);
                    buf.append(" AS ").append(castTypes[j]).append(")");
                } else {
                    quote(buf, value, datatype);
                }
            }
            buf.append(")");
        }
    }

    private void fillMaxLengthsArray(final Integer[] maxLengths, final List<String> columnTypes,
            final List<String[]> valueList) {
        for (int i = 0; i < columnTypes.size(); i++) {
            String columnType = columnTypes.get(i);
            Datatype datatype = Datatype.fromValue(columnType);
            if (datatype == Datatype.VARCHAR) {
                maxLengths[i] = getMaxLen(valueList, i);
            }
        }
    }

    private Integer getMaxLen(List<String[]> valueList, int i) {
        int maxLen = -1;
        for (String[] strings : valueList) {
            if (strings[i] != null && strings[i].length() > maxLen) {
                maxLen = strings[i].length();
            }
        }
        return maxLen;
    }

    private String[] getCastTypes(List<String> columnNames, List<String> columnTypes, List<String[]> valueList) {
        String[] castTypes = new String[columnNames.size()];
        for (int i = 0; i < columnNames.size(); i++) {
            String columnType = columnTypes.get(i);
            if (Datatype.fromValue(columnType) == Datatype.VARCHAR) {
                castTypes[i] = guessSqlType(columnType, valueList, i);
            }
        }
        return castTypes;
    }

    private static String guessSqlType(String basicType, List<String[]> valueList, int column) {
        if (Datatype.fromValue(basicType) == Datatype.VARCHAR) {
            int maxLen = 1;
            for (String[] values : valueList) {
                final String value = values[column];
                if (value == null) {
                    continue;
                }
                maxLen = Math.max(maxLen, value.length());
            }
            return new StringBuilder("VARCHAR(").append(maxLen).append(")").toString();
        } else {
            return "INTEGER";
        }
    }
}
