/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
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

package org.eclipse.daanse.sql.dialect.api;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

public interface DialectFactory {

    /** Build a dialect from a pre-captured snapshot — the canonical entry point. */
    Dialect createDialect(DialectInitData init);

    default Dialect createDialect(Connection connection) throws SQLException {
        return createDialect(DialectInitData.fromConnection(connection));
    }

    default Dialect createDialect(DataSource dataSource) throws SQLException {
        return createDialect(DialectInitData.fromDataSource(dataSource));
    }
}
