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

import net.sf.jsqlparser.util.deparser.StatementDeParser;

public class BasicDialectStatementDeParser extends StatementDeParser {

    public BasicDialectStatementDeParser(StringBuilder buffer, Dialect dialect) {

        super(createExpressionDeParser(dialect, buffer), createSelectDeParser(buffer, dialect), buffer);

        BasicDialectSelectDeParser selectDeParser = (BasicDialectSelectDeParser) getSelectDeParser();
        BasicDialectExpressionDeParser expressionDeParser = (BasicDialectExpressionDeParser) getExpressionDeParser();
        selectDeParser.setExpressionVisitor(expressionDeParser);
        expressionDeParser.setSelectVisitor(selectDeParser);
    }

    private static BasicDialectExpressionDeParser createExpressionDeParser(Dialect dialect, StringBuilder buffer) {
        return new BasicDialectExpressionDeParser(dialect);
    }

    private static BasicDialectSelectDeParser createSelectDeParser(StringBuilder buffer, Dialect dialect) {
        return new BasicDialectSelectDeParser(buffer, dialect);
    }

}
