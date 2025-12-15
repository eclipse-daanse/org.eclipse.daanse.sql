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

import java.util.List;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.sql.deparser.api.DialectDeparser;
import org.eclipse.daanse.sql.guard.api.SqlGuard;
import org.eclipse.daanse.sql.guard.api.SqlGuardFactory;
import org.eclipse.daanse.sql.guard.api.elements.DatabaseCatalog;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;

@Component(scope = ServiceScope.SINGLETON)
public class TranspilerSqlGuardFactory implements SqlGuardFactory {

    @Reference
    DialectDeparser dialectDeparser;

    @Override
    public SqlGuard create(String currentCatalogName, String currentSchemaName, DatabaseCatalog databaseCatalog,
            List<String> whitelistFunctionsPatterns, Dialect dialect) {
        return new TranspilerSqlGuard(currentCatalogName, currentSchemaName, databaseCatalog,
                whitelistFunctionsPatterns, dialect,dialectDeparser);
    }

}
