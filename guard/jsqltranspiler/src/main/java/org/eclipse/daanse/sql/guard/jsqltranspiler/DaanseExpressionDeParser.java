/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.sql.guard.jsqltranspiler;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;

import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;

public class DaanseExpressionDeParser extends ExpressionDeParser {

    private Dialect dialect;

    public DaanseExpressionDeParser(Dialect dialect) {
        super();
        this.dialect = dialect;
    }

    @Override
    public <S> StringBuilder visit(Column tableColumn, S context) {
        final Table table = tableColumn.getTable();
        String tableName = null;
        if (table != null) {
            if (table.getAlias() != null) {
                tableName = table.getAlias().getName();
            } else {
                tableName = table.getFullyQualifiedName();
            }
        }
        if (tableName != null && !tableName.isEmpty()) {
            dialect.quoteIdentifier(builder, tableName, tableColumn.getColumnName());
        } else {
            dialect.quoteIdentifier(builder, tableColumn.getColumnName());
        }

        if (tableColumn.getArrayConstructor() != null) {
            tableColumn.getArrayConstructor().accept(this, context);
        }

        if (tableColumn.getCommentText() != null) {
            builder.append(" /* ").append(tableColumn.getCommentText()).append("*/ ");
        }

        return builder;
    }

}
