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

    public String getResolvedStatementText(String sqlStr) throws JSQLParserException {

        Statement st = CCJSqlParserUtil.parse(sqlStr);
        if (st instanceof Select) {
            Select select = (Select) st;
            select.accept((SelectVisitor<JdbcResultSetMetaData>) this, JdbcMetaData.copyOf(metaData));
            stripCurrentCatalogQualifier(select);
        }

        return dialectDeparser.deparse(st, dialect);
    }

    // The resolver only ever fills in a catalog qualifier that is missing; it never clears one that
    // was already explicit in the query, even when it names the current catalog. The database
    // connection is already scoped to that catalog, so only the schema-qualified name may be sent.
    private void stripCurrentCatalogQualifier(Select select) {
        String currentCatalogName = metaData.getCurrentCatalogName();
        if (currentCatalogName == null || currentCatalogName.isEmpty()) {
            return;
        }
        new TablesNamesFinder<Void>() {
            @Override
            public <S> Void visit(Table table, S context) {
                if (currentCatalogName.equalsIgnoreCase(table.getUnquotedDatabaseName())) {
                    table.setDatabaseName(null);
                }
                return super.visit(table, context);
            }
        }.getTables((Statement) select);
    }

}
