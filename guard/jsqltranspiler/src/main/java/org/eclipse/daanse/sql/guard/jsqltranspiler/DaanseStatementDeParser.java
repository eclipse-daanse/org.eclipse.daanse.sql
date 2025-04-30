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

import net.sf.jsqlparser.util.deparser.SelectDeParser;
import net.sf.jsqlparser.util.deparser.StatementDeParser;

public class DaanseStatementDeParser extends StatementDeParser {

    public DaanseStatementDeParser(StringBuilder buffer, Dialect dialect) {
        super(new DaanseExpressionDeParser(dialect), new SelectDeParser(), buffer);
    }
}
