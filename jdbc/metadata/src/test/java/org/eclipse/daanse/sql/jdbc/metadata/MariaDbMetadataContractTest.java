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
import java.time.Duration;
import java.util.List;

import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/** The metadata contract on MariaDB, in a container: a schema is a database. */
@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
class MariaDbMetadataContractTest extends MetadataProviderContract {

    private static final String PASSWORD = "contract";

    @SuppressWarnings("resource")
    private final GenericContainer<?> container = new GenericContainer<>("mariadb:11.4")
            .withEnv("MARIADB_ROOT_PASSWORD", PASSWORD).withExposedPorts(3306)
            .waitingFor(Wait.forLogMessage(".*ready for connections.*\\n", 2)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    @Override
    protected Connection open() throws Exception {
        container.start();
        return DriverManager.getConnection("jdbc:mariadb://" + container.getHost() + ":"
                + container.getMappedPort(3306) + "/", "root", PASSWORD);
    }

    @Override
    protected MetadataProvider provider() {
        return new MariaDbMetadataProvider();
    }

    @Override
    protected void close() {
        container.stop();
    }

    @Override
    protected List<String> createSchema(String name) {
        return List.of("CREATE DATABASE " + name);
    }

    /**
     * A table's comment is set by {@code ALTER TABLE}, a column's by restating the column; a view
     * keeps none.
     */
    @Override
    protected List<String> comments() {
        return List.of("ALTER TABLE stock.product COMMENT = '" + PRODUCT_COMMENT + "'",
                "ALTER TABLE stock.product MODIFY code VARCHAR(20) NOT NULL COMMENT '" + CODE_COMMENT + "'");
    }

    @Override
    protected boolean viewComments() {
        return false;
    }

    @Override
    protected List<String> createCommentedFunction() {
        return List.of("CREATE FUNCTION sales.f_answer() RETURNS INT DETERMINISTIC NO SQL COMMENT '"
                + FUNCTION_COMMENT + "' RETURN 42");
    }
}
