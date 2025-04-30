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

import ai.starlake.transpiler.JSQLColumResolver;
import ai.starlake.transpiler.schema.JdbcMetaData;
import ai.starlake.transpiler.schema.JdbcResultSetMetaData;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectVisitor;
import net.sf.jsqlparser.util.deparser.StatementDeParser;

public class DaanseJSQLColumResolver extends JSQLColumResolver {

    final JdbcMetaData metaData;
    private Dialect dialect;

    public DaanseJSQLColumResolver(JdbcMetaData metaData, Dialect dialect) {
        super(metaData);
        this.metaData = metaData;
        this.dialect = dialect;
    }

    public String getResolvedStatementText(String sqlStr) throws JSQLParserException {
        StringBuilder builder = new StringBuilder();
        StatementDeParser deParser = new DaanseStatementDeParser(builder, this.dialect);

        Statement st = CCJSqlParserUtil.parse(sqlStr);
        if (st instanceof Select) {
            Select select = (Select) st;
            select.accept((SelectVisitor<JdbcResultSetMetaData>) this, JdbcMetaData.copyOf(metaData));
        }
        st.accept(deParser);
        return builder.toString();
    }

}
