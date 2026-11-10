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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.sql.dialect.db.derby;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.sql.dialect.api.generator.KnownFunction;
import org.junit.jupiter.api.Test;

/**
 * A number of every type reads as text on Derby, a DOUBLE and a REAL among them, which Derby casts to no text; whole
 * numbers make a date.
 */
class DerbyNumberAsTextTest {

    @Test
    void everyNumberReadsAsText() throws Exception {
        DerbyDialect dialect = new DerbyDialect();
        try (Connection connection = DriverManager.getConnection("jdbc:derby:memory:numberastext;create=true");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE t (d DOUBLE, r REAL, n DECIMAL(10, 2), i INTEGER)");
            statement.execute("INSERT INTO t VALUES (2.24, 1.05, 2.50, 100), (17, -3.5, -3, -7)");
            List<String> texts = new ArrayList<>();
            for (String column : List.of("d", "r", "n", "i")) {
                String text = dialect.generateKnownFunction(KnownFunction.TO_TEXT, List.of(column)).toString();
                try (ResultSet rows = statement.executeQuery("SELECT " + text + " FROM t")) {
                    while (rows.next()) {
                        texts.add(rows.getString(1));
                    }
                }
            }
            assertThat(texts).containsExactly("2.24", "17.0", "1.05", "-3.5", "2.50", "-3.00", "100", "-7");
        }
    }

    @Test
    void aDateOfWholeNumbersReadsAsADate() throws Exception {
        DerbyDialect dialect = new DerbyDialect();
        try (Connection connection = DriverManager.getConnection("jdbc:derby:memory:datefromparts;create=true");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE t (y SMALLINT, m SMALLINT)");
            statement.execute("INSERT INTO t VALUES (2013, 7), (2014, 12)");
            String date = dialect.generateKnownFunction(KnownFunction.DATE_FROM_PARTS, List.of("y", "m", "1"))
                    .toString();
            List<String> dates = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery("SELECT " + date + " FROM t ORDER BY y")) {
                while (rows.next()) {
                    dates.add(rows.getDate(1).toString());
                }
            }
            assertThat(dates).containsExactly("2013-07-01", "2014-12-01");
        }
    }
}
