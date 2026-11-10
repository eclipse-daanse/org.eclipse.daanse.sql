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
package org.eclipse.daanse.sql.dialect.db.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.sql.model.sql.OrderedColumn;
import org.eclipse.daanse.sql.model.sql.SortDirection;
import org.junit.jupiter.api.Test;

/**
 * The n-th value aggregate runs on H2 and gives NULL for a group with fewer values: H2 fails an array element past
 * the end, so the n-th value of four values asked for the fifth broke the query (TheNthValueIsPerCellTest on H2,
 * database run 18.10.2026).
 */
class H2NthValuePastTheEndTest {

    private static List<Object> nth(int n, String rows) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:nth" + n)) {
            H2Dialect dialect = new H2Dialect(DialectInitData.fromConnection(connection));
            String nth = dialect.generateNthValueAgg("\"x\"", false, n,
                    List.of(new OrderedColumn("x", null, SortDirection.DESC))).orElseThrow();
            List<Object> values = new ArrayList<>();
            try (ResultSet result = connection.createStatement()
                    .executeQuery("SELECT \"g\", " + nth + " FROM (VALUES " + rows + ") t(\"g\", \"x\") GROUP BY \"g\" ORDER BY \"g\"")) {
                while (result.next()) {
                    values.add(result.getObject(2));
                }
            }
            return values;
        }
    }

    @Test
    void theNthValueOfEachGroupInTheOrderOfTheColumns() throws SQLException {
        assertEquals(List.of(2, 7), nth(2, "(1, 1), (1, 2), (1, 3), (2, 7), (2, 8)"));
    }

    @Test
    void aGroupWithFewerValuesGivesNull() throws SQLException {
        List<Object> values = nth(5, "(1, 1), (1, 2), (1, 3), (1, 4)");
        assertEquals(1, values.size());
        assertNull(values.get(0));
    }
}
