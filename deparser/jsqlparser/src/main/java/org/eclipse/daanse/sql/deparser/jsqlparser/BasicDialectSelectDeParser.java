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

import java.util.HashSet;
import java.util.Set;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.IdentifierQuotingPolicy;

import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.ExpressionVisitor;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
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
    public <S> StringBuilder visit(PlainSelect plainSelect, S context) {
        Set<String> aliases = collectAliases(plainSelect);
        BasicDialectExpressionDeParser exprDeParser = currentExpressionDeParser();
        if (exprDeParser != null) {
            exprDeParser.pushAliasScope(aliases);
            try {
                return super.visit(plainSelect, context);
            } finally {
                exprDeParser.popAliasScope();
            }
        }
        return super.visit(plainSelect, context);
    }

    private BasicDialectExpressionDeParser currentExpressionDeParser() {
        ExpressionVisitor<StringBuilder> visitor = getExpressionVisitor();
        return visitor instanceof BasicDialectExpressionDeParser
                ? (BasicDialectExpressionDeParser) visitor
                : null;
    }

    private static Set<String> collectAliases(PlainSelect ps) {
        Set<String> out = new HashSet<>();
        addAlias(out, ps.getFromItem());
        if (ps.getJoins() != null) {
            for (Join j : ps.getJoins()) {
                addAlias(out, j.getFromItem());
            }
        }
        return out;
    }

    private static void addAlias(Set<String> out, FromItem fi) {
        if (fi != null && fi.getAlias() != null) {
            out.add(fi.getAlias().getName());
        }
    }

    @Override
    public <S> StringBuilder visit(Table table, S context) {
        // Quote catalog, schema, and table name
        String catalog = table.getDatabaseName(); // Gets database/catalog
        String schema = table.getSchemaName();
        String tableName = table.getName();

        if (catalog != null && !catalog.isEmpty()) {
            dialect.quoteIdentifierWith(catalog, builder, IdentifierQuotingPolicy.ALWAYS);
            builder.append(".");
        }

        if (schema != null && !schema.isEmpty()) {
            dialect.quoteIdentifierWith(schema, builder, IdentifierQuotingPolicy.ALWAYS);
            builder.append(".");
        }

        if (tableName != null) {
            dialect.quoteIdentifierWith(tableName, builder, IdentifierQuotingPolicy.ALWAYS);
        }

        // Handle table alias
        Alias alias = table.getAlias();
        if (alias != null) {
            if (dialect.allowsFromAlias()) {
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
            if (dialect.allowsFieldAlias() && alias.isUseAs()) {
                builder.append(" AS ");
            } else {
                builder.append(" ");
            }
            builder.append(alias.getName());
        }

        return builder;
    }
}
