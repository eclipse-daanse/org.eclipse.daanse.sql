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
package org.eclipse.daanse.sql.statement.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.sql.statement.demo.ResultReaderDemo.Product;
import org.junit.jupiter.api.Test;

/**
 * Real in-memory H2 round-trip: build + render + execute (INSERT and SELECT) + map rows back
 * into a record and into a generic attribute map via the result-reading layer.
 */
class ResultReaderDemoTest {

    @Test
    void roundTrip_intoRecordsAndAttributeMaps() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:rrt;DB_CLOSE_DELAY=-1", "sa", "")) {
            try (Statement s = c.createStatement()) {
                s.execute(ResultReaderDemo.createTableSql());
            }

            List<Product> products = ResultReaderDemo.loadProducts(c);
            assertEquals(2, products.size());
            assertEquals(new Product(1, "Widget"), products.get(0));
            assertEquals(new Product(2, "Gadget"), products.get(1));

            List<Map<String, Object>> attributes = ResultReaderDemo.loadAsAttributes(c);
            assertEquals(2, attributes.size());
            assertEquals(1, attributes.get(0).get("id"));
            assertEquals("Widget", attributes.get(0).get("name"));
            assertEquals(2, attributes.get(1).get("id"));
            assertEquals("Gadget", attributes.get(1).get("name"));
        }
    }
}
