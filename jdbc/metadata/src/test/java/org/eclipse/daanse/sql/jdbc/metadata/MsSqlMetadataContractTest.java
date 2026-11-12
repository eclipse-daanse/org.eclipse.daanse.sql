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
import java.util.Optional;

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
    protected List<String> createFilteredIndex() {
        return List.of("CREATE INDEX ix_orders_large ON sales.orders (qty) WHERE qty > 10");
    }

    @Override
    protected Optional<String> functionLanguage() {
        return Optional.of("tsql");
    }

    @Override
    protected MetadataProvider provider() {
        return new MicrosoftSqlServerMetadataProvider();
    }

    @Override
    protected void close() {
        container.stop();
    }

    /** SQL Server keeps a comment as the extended property {@code MS_Description}. */
    @Override
    protected List<String> comments() {
        return List.of(description(PRODUCT_COMMENT, "stock", "TABLE", "product", null),
                description(CODE_COMMENT, "stock", "TABLE", "product", "code"),
                description(VIEW_COMMENT, "sales", "VIEW", "v_orders", null),
                description(VIEW_COLUMN_COMMENT, "sales", "VIEW", "v_orders", "qty"));
    }

    @Override
    protected List<String> createCommentedFunction() {
        return List.of("CREATE FUNCTION sales.f_answer() RETURNS INT AS BEGIN RETURN 42 END",
                description(FUNCTION_COMMENT, "sales", "FUNCTION", "f_answer", null));
    }

    private static String description(String comment, String schema, String type, String name, String column) {
        return "EXEC sys.sp_addextendedproperty @name = N'MS_Description', @value = N'" + comment
                + "', @level0type = N'SCHEMA', @level0name = N'" + schema + "', @level1type = N'" + type
                + "', @level1name = N'" + name + "'"
                + (column == null ? "" : ", @level2type = N'COLUMN', @level2name = N'" + column + "'");
    }

    @Override
    protected String createSequence(String name, boolean limited) {
        return "CREATE SEQUENCE " + name + (limited ? " AS INT MINVALUE 1 MAXVALUE 999" : "");
    }
}
