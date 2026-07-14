/*
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*   SmartCity Jena - initial
*/
package org.eclipse.daanse.sql.dialect.db.duckdb;

import java.util.function.Function;

import org.eclipse.daanse.sql.dialect.api.DialectFactory;
import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.sql.dialect.api.DialectName;
import org.eclipse.daanse.sql.dialect.db.common.AbstractDialectFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

/**
 * Factory for {@link DuckDbDialect}. The JDBC driver reports the database
 * product name as {@code "DuckDB"}.
 */
@DialectName("DUCKDB")
@Component(service = DialectFactory.class, scope = ServiceScope.SINGLETON)
public class DuckDbDialectFactory extends AbstractDialectFactory<DuckDbDialect> {

    @Override
    public Function<DialectInitData, DuckDbDialect> getConstructorFunction() {
        return DuckDbDialect::new;
    }

}
