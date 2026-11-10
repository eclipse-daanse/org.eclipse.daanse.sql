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

import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.MSSQLServerContainer;

/** The metadata contract on SQL Server, in a container. */
@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
class MsSqlMetadataContractTest extends MetadataProviderContract {

    @SuppressWarnings("resource")
    private final MSSQLServerContainer<?> container = new MSSQLServerContainer<>(
            "mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();

    @Override
    protected Connection open() throws Exception {
        container.start();
        return DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    @Override
    protected MetadataProvider provider() {
        return new MicrosoftSqlServerMetadataProvider();
    }

    @Override
    protected void close() {
        container.stop();
    }

    @Override
    protected String createSequence(String name, boolean limited) {
        return "CREATE SEQUENCE " + name + (limited ? " AS INT MINVALUE 1 MAXVALUE 999" : "");
    }
}
