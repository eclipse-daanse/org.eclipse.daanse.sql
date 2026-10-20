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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.sql.jdbc.importer.csv.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Optional;

import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.api.Test;

/**
 * A dialect without DROP TABLE IF EXISTS (Derby) drops a table only after finding it: the lookup is by exact name in
 * the table's schema, and an underscore in the name is no wildcard.
 */
class ATableIsLookedUpBeforeItIsDroppedTest {

    @Test
    void aTableIsFoundByItsExactName() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:lookup" + System.nanoTime());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA \"shop\"");
            statement.execute("CREATE TABLE \"shop\".\"store_csv\" (\"id\" INT)");
            statement.execute("CREATE TABLE \"line\" (\"id\" INT)");
            Optional<SchemaReference> shop = Optional.of(new SchemaReference("shop"));

            assertTrue(CsvDataImporter.exists(connection, new TableReference(shop, "store_csv")));
            assertTrue(CsvDataImporter.exists(connection, new TableReference("line")), "the current schema");
            assertFalse(CsvDataImporter.exists(connection, new TableReference(shop, "storeXcsv")), "_ is no wildcard");
            assertFalse(CsvDataImporter.exists(connection, new TableReference(shop, "line")), "another schema");
            assertFalse(CsvDataImporter.exists(connection, new TableReference(shop, "STORE_CSV")), "the exact name");
        }
    }
}
