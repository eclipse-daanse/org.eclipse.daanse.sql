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

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.deparser.api.DialectDeparser;

import ai.starlake.transpiler.JSQLColumResolver;
import ai.starlake.transpiler.schema.JdbcMetaData;
import ai.starlake.transpiler.schema.JdbcResultSetMetaData;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectVisitor;
import net.sf.jsqlparser.util.TablesNamesFinder;

public class DeparserColumResolver extends JSQLColumResolver {

    final JdbcMetaData metaData;
    private Dialect dialect;
    private DialectDeparser dialectDeparser;

    public DeparserColumResolver(JdbcMetaData metaData, Dialect dialect, DialectDeparser dialectDeparser) {
        super(metaData);
        this.metaData = metaData;
        this.dialect = dialect;
        this.dialectDeparser = dialectDeparser;
    }

    /**
     * Parses the text as a script and resolves its one statement; a text of more than one statement is refused by
     * name, as the guard refuses it. {@code CCJSqlParserUtil.parse} stopped after the first statement before JSqlParser
     * 5.4.162 and resolved "SELECT ...; DROP ..." as if the rest were not there; it refuses the rest now, but only with
     * a parser message.
     */
    @Override
    public String getResolvedStatementText(String sqlStr) throws JSQLParserException {
        net.sf.jsqlparser.statement.Statements statements =
                CCJSqlParserUtil.parseStatements(BlankLines.collapse(sqlStr));
        if (statements == null || statements.size() != 1) {
            throw new JSQLParserException("expected exactly one statement, the text holds "
                    + (statements == null ? 0 : statements.size()));
        }
        return getResolvedStatementText(statements.get(0));
    }

    /**
     * Resolves and deparses an already parsed statement, so a caller that has the AST
     * in hand (the guard, which validated it first) does not pay for a second parse.
     */
    public String getResolvedStatementText(Statement st) {
        if (st instanceof Select) {
            Select select = (Select) st;
            select.accept((SelectVisitor<JdbcResultSetMetaData>) this, JdbcMetaData.copyOf(metaData));
            normaliseQualifiers(select);
        }

        return dialectDeparser.deparse(st, dialect);
    }

    // The resolvers only ever fill in qualifiers, they never clear them. The connection is
    // already scoped to the current catalog, so a table may only be sent schema-qualified.
    // Column references keep just the table name or alias: the FROM item carries the schema.
    // (JSQLResolver expands "*" with the FROM item's full qualification, JSQLColumResolver
    // with the bare name; this normalisation makes both paths emit the same text.)
    private void normaliseQualifiers(Select select) {
        String currentCatalogName = metaData.getCurrentCatalogName();
        String currentSchemaName = metaData.getCurrentSchemaName();
        new TablesNamesFinder<Void>() {
            @Override
            public <S> Void visit(Table table, S context) {
                stripCurrentCatalog(table);
                return super.visit(table, context);
            }

            @Override
            public <S> Void visit(Column column, S context) {
                Table table = column.getTable();
                if (table != null) {
                    stripCurrentCatalog(table);
                    if (currentSchemaName != null && !currentSchemaName.isEmpty()
                            && currentSchemaName.equalsIgnoreCase(table.getUnquotedSchemaName())) {
                        table.setSchemaName(null);
                    }
                }
                return super.visit(column, context);
            }

            private void stripCurrentCatalog(Table table) {
                if (currentCatalogName != null && !currentCatalogName.isEmpty()
                        && currentCatalogName.equalsIgnoreCase(table.getUnquotedDatabaseName())) {
                    table.setDatabaseName(null);
                }
            }
        }.getTables((Statement) select);
    }

}
