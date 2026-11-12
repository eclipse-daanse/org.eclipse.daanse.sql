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

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.oracle.OracleContainer;

/**
 * The metadata contract on Oracle, in a container. A schema is a user: the orders lie in the
 * container's user, the products in the user STOCK. SYSTEM creates the objects, as only it may create
 * them in both; the container's user reads them.
 */
@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
class OracleMetadataContractTest extends MetadataProviderContract {

    @SuppressWarnings("resource")
    private final OracleContainer container = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    private Connection system;

    @Override
    protected Connection open() throws Exception {
        container.start();
        system = DriverManager.getConnection(container.getJdbcUrl(), "system", container.getPassword());
        try (var s = system.createStatement()) {
            s.execute("CREATE USER STOCK IDENTIFIED BY stock QUOTA UNLIMITED ON USERS");
        }
        return DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    @Override
    protected Connection fixtureConnection() {
        return system;
    }

    /** SYSTEM creates it; its owner needs the rights to own one. */
    @Override
    protected List<String> createMaterializedView() {
        return List.of("GRANT CREATE MATERIALIZED VIEW, CREATE TABLE TO " + sales(),
                "CREATE MATERIALIZED VIEW " + sales() + ".mv_orders AS SELECT id, qty FROM " + sales() + ".orders");
    }

    @Override
    protected List<String> createDeferrableForeignKey() {
        return List.of("CREATE TABLE " + sales() + ".late_line (id INT NOT NULL, orders_id INT,"
                + " CONSTRAINT pk_late_line PRIMARY KEY (id), CONSTRAINT fk_late_line_orders FOREIGN KEY (orders_id)"
                + " REFERENCES " + sales() + ".orders (id) DEFERRABLE INITIALLY DEFERRED)");
    }

    @Override
    protected MetadataProvider provider() {
        return new OracleMetadataProvider();
    }

    @Override
    protected void close() throws Exception {
        if (system != null) {
            system.close();
        }
        container.stop();
    }

    @Override
    protected String sales() {
        return container.getUsername().toUpperCase(Locale.ROOT);
    }

    @Override
    protected String stock() {
        return "STOCK";
    }

    /** A text column counts characters, not bytes, as the DDL of the Oracle dialect declares it. */
    @Override
    protected String text(int length) {
        return "VARCHAR2(" + length + " CHAR)";
    }

    @Override
    protected List<String> createSchema(String name) {
        return List.of();
    }

    /** SYSTEM creates the index; without the schema it would be SYSTEM's. */
    @Override
    protected String indexName(String schema, String name) {
        return schema + "." + name;
    }

    /** Oracle has no {@code COMMENT ON VIEW}; a view takes {@code COMMENT ON TABLE}. */
    @Override
    protected List<String> comments() {
        return List.of("COMMENT ON TABLE STOCK.product IS '" + PRODUCT_COMMENT + "'",
                "COMMENT ON COLUMN STOCK.product.code IS '" + CODE_COMMENT + "'",
                "COMMENT ON TABLE " + sales() + ".v_orders IS '" + VIEW_COMMENT + "'");
    }

    @Override
    protected List<String> afterStock() {
        return List.of("GRANT REFERENCES ON STOCK.product TO " + sales());
    }

    @Test
    void theSequencesOfOracleItselfAreRead() throws SQLException {
        assertThat(provider().getAllSequences(connection(), null, "SYS")).isNotEmpty();
    }
}
