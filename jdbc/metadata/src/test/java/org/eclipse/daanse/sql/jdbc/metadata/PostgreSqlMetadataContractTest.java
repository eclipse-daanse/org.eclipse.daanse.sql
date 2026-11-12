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
import org.testcontainers.containers.PostgreSQLContainer;

/** The metadata contract on PostgreSQL, in a container. */
@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
class PostgreSqlMetadataContractTest extends MetadataProviderContract {

    @SuppressWarnings("resource")
    private final PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine");

    @Override
    protected Connection open() throws Exception {
        container.start();
        return DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    @Override
    protected List<String> createMaterializedView() {
        return List.of("CREATE MATERIALIZED VIEW sales.mv_orders AS SELECT id, qty FROM sales.orders");
    }

    @Override
    protected List<String> createFilteredIndex() {
        return List.of("CREATE INDEX ix_orders_large ON sales.orders (qty) WHERE qty > 10");
    }

    @Override
    protected List<String> createDeferrableForeignKey() {
        return List.of("CREATE TABLE sales.late_line (id INT NOT NULL, orders_id INT,"
                + " CONSTRAINT pk_late_line PRIMARY KEY (id), CONSTRAINT fk_late_line_orders FOREIGN KEY (orders_id)"
                + " REFERENCES sales.orders (id) DEFERRABLE INITIALLY DEFERRED)");
    }

    @Override
    protected Optional<String> functionLanguage() {
        return Optional.of("sql");
    }

    @Override
    protected MetadataProvider provider() {
        return new PostgreSqlMetadataProvider();
    }

    @Override
    protected void close() {
        container.stop();
    }

    @Override
    protected List<String> createCommentedFunction() {
        return List.of("CREATE FUNCTION sales.f_answer() RETURNS INT LANGUAGE SQL AS 'SELECT 42'",
                "COMMENT ON FUNCTION sales.f_answer() IS '" + FUNCTION_COMMENT + "'");
    }

    @Override
    protected List<String> createOverloads() {
        return List.of("CREATE FUNCTION sales.f_total(qty INT) RETURNS INT LANGUAGE SQL AS 'SELECT qty'",
                "CREATE FUNCTION sales.f_total(qty INT, INOUT price DECIMAL(10, 2), OUT total DECIMAL(10, 2))"
                        + " LANGUAGE SQL AS 'SELECT price, qty * price'",
                "GRANT EXECUTE ON FUNCTION sales.f_total(INT) TO PUBLIC");
    }
}
