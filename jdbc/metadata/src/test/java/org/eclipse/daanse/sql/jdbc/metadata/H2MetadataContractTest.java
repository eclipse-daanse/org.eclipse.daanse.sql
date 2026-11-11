/*
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/
package org.eclipse.daanse.sql.jdbc.metadata;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;

/** The metadata contract on H2, in memory; H2 folds unquoted names to upper case. */
class H2MetadataContractTest extends MetadataProviderContract {

    @Override
    protected Connection open() throws Exception {
        return DriverManager.getConnection("jdbc:h2:mem:metadataContract;DB_CLOSE_DELAY=-1", "sa", "");
    }

    @Override
    protected boolean makesForeignKeyIndexes() {
        return true;
    }

    @Override
    protected MetadataProvider provider() {
        return new H2MetadataProvider();
    }

    @Override
    protected void close() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:metadataContract", "sa", "");
                var s = c.createStatement()) {
            s.execute("DROP ALL OBJECTS");
        }
    }

    /** A function is a Java method under an alias. */
    @Override
    protected List<String> createCommentedFunction() {
        return List.of("CREATE ALIAS SALES.F_ANSWER FOR 'java.lang.System.currentTimeMillis'",
                "COMMENT ON ALIAS SALES.F_ANSWER IS '" + FUNCTION_COMMENT + "'");
    }

    @Override
    protected String sales() {
        return "SALES";
    }

    @Override
    protected String stock() {
        return "STOCK";
    }
}
