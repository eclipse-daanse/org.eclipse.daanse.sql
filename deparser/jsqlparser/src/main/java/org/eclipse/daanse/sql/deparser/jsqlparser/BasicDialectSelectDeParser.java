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

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;

import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.ExpressionVisitor;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.util.deparser.SelectDeParser;

public class BasicDialectSelectDeParser extends SelectDeParser {

    private final Dialect dialect;

    public BasicDialectSelectDeParser(StringBuilder buffer, Dialect dialect) {
        super(buffer);
        this.dialect = dialect;
    }

    public BasicDialectSelectDeParser(ExpressionVisitor<StringBuilder> expressionVisitor, StringBuilder buffer,
            Dialect dialect) {
        super(expressionVisitor, buffer);
        this.dialect = dialect;
    }

    @Override
    public <S> StringBuilder visit(Table table, S context) {
        // Quote catalog, schema, and table name
        String catalog = table.getDatabaseName(); // Gets database/catalog
        String schema = table.getSchemaName();
        String tableName = table.getName();

        if (catalog != null && !catalog.isEmpty()) {
            dialect.quoteIdentifier(builder, catalog);
            builder.append(".");
        }

        if (schema != null && !schema.isEmpty()) {
            dialect.quoteIdentifier(builder, schema);
            builder.append(".");
        }

        if (tableName != null) {
            dialect.quoteIdentifier(builder, tableName);
        }

        // Handle table alias
        Alias alias = table.getAlias();
        if (alias != null) {
            if (dialect.allowsAs()) {
                builder.append(" AS ");
            } else {
                builder.append(" ");
            }
            // Don't quote the alias - it's user-defined and may be intentionally unquoted
            builder.append(alias.getName());
        }

        // Handle pivot/unpivot if present
        if (table.getPivot() != null) {
            table.getPivot().accept(this, context);
        }
        if (table.getUnPivot() != null) {
            table.getUnPivot().accept(this, context);
        }

        // Handle sample clause
        if (table.getSampleClause() != null) {
            builder.append(table.getSampleClause());
        }

        return builder;
    }

    @Override
    public <S> StringBuilder visit(SelectItem<?> selectItem, S context) {
        // Use parent implementation for the expression
        selectItem.getExpression().accept(getExpressionVisitor(), context);

        // Handle alias
        Alias alias = selectItem.getAlias();
        if (alias != null) {
            if (dialect.allowsFieldAs() && alias.isUseAs()) {
                builder.append(" AS ");
            } else {
                builder.append(" ");
            }
            builder.append(alias.getName());
        }

        return builder;
    }
}
