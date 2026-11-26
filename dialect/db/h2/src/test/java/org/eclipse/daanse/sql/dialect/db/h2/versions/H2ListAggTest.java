/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.h2.versions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.dialect.db.h2.H2Dialect;
import org.eclipse.daanse.sql.model.sql.NullsOrder;
import org.eclipse.daanse.sql.model.sql.OrderedColumn;
import org.eclipse.daanse.sql.model.sql.SortDirection;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * H2 lists the values of a group with {@code LISTAGG(x, 'sep') WITHIN GROUP (ORDER BY …)} on every version from
 * 1.4.200 on, with {@code ON OVERFLOW TRUNCATE} from 2.0. The dialect is built from a live connection of each version,
 * and the list it writes must come in the order asked for, the NULLs of an order column first or last as asked,
 * without the NULL values, or with their stand-in, DISTINCT kept, with any separator, a quote and a backslash too, and
 * per group; the overflow clause only where H2 reads it. 1.4 orders WITHIN GROUP by one expression only, without
 * NULLS FIRST/LAST: a list in the order of more, or of its NULLs, the dialect does not write there; the n-th value's
 * ARRAY_AGG takes both on every version.
 */
@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
class H2ListAggTest {

    @ParameterizedTest(name = "h2 {0} -> ON OVERFLOW {1}")
    @CsvSource({ "1.4.200, false", "2.0.206, true", "2.1.214, true", "2.2.224, true", "2.3.232, true" })
    void listAgg(String version, boolean overflow) throws Exception {
        try (H2Versions.Session h2 = H2Versions.open(version)) {
            h2.execute("CREATE TABLE EMP(DEPT VARCHAR(1), NAME VARCHAR(10), SALARY INT)");
            h2.execute("INSERT INTO EMP VALUES ('a', 'bob', 30), ('a', NULL, 40), ('a', 'alice', 20), ('a', 'bob', 10),"
                    + " ('b', 'dave', 5)");
            H2Dialect d = h2.dialect();
            assertThat(d.supportsListAgg()).isTrue();

            assertThat(list(h2, "NAME", false, ", ", null, order("NAME", SortDirection.ASC)))
                    .as("ordered, the NULL left out").isEqualTo("alice, bob, bob");
            assertThat(list(h2, "NAME", false, ", ", null, order("SALARY", SortDirection.DESC)))
                    .as("ordered by another column").isEqualTo("bob, alice, bob");
            assertThat(list(h2, "NAME", true, ";", null, order("NAME", SortDirection.ASC)))
                    .as("DISTINCT").isEqualTo("alice;bob");
            assertThat(list(h2, "NAME", false, "'", null, order("NAME", SortDirection.ASC)))
                    .as("a quote as separator").isEqualTo("alice'bob'bob");
            assertThat(list(h2, "NAME", false, "\\", null, order("NAME", SortDirection.ASC)))
                    .as("a backslash as separator").isEqualTo("alice\\bob\\bob");
            assertThat(list(h2, "NAME", false, null, null, order("NAME", SortDirection.ASC)))
                    .as("no separator: H2's comma and blank").isEqualTo("alice, bob, bob");
            assertThat(list(h2, "NAME", false, ",", "it's", List.of(new OrderedColumn("SALARY", null,
                    SortDirection.ASC)))).as("the NULL's stand-in, a quote in it").isEqualTo("bob,alice,bob,it's");
            List<OrderedColumn> nullsFirst = List.of(new OrderedColumn("NAME", null, Optional.of(SortDirection.ASC),
                    Optional.of(NullsOrder.FIRST)), new OrderedColumn("SALARY", null, SortDirection.ASC));
            List<OrderedColumn> nullsLast = List.of(new OrderedColumn("NAME", null, Optional.of(SortDirection.ASC),
                    Optional.of(NullsOrder.LAST)), new OrderedColumn("SALARY", null, SortDirection.ASC));
            if (overflow) {
                assertThat(list(h2, "SALARY", false, ",", null, nullsFirst)).as("NULLS FIRST, then a second column")
                        .isEqualTo("40,20,10,30");
                assertThat(list(h2, "SALARY", false, ",", null, nullsLast)).as("NULLS LAST, then a second column")
                        .isEqualTo("20,10,30,40");
            } else {
                assertThat(d.generateListAgg("SALARY", false, ",", null, null, nullsFirst))
                        .as("1.4: no order of the NULLs, nor a second column, in WITHIN GROUP").isEmpty();
                assertThat(d.generateListAgg("SALARY", false, ",", null, null, order("NAME", SortDirection.ASC)
                        .stream().toList())).as("one column, no order of its NULLs").isPresent();
            }

            // the n-th value's ARRAY_AGG takes NULLS FIRST/LAST on every version
            for (NullsOrder nulls : NullsOrder.values()) {
                String nth = d.generateNthValueAgg("SALARY", false, 1, List.of(new OrderedColumn("NAME", null,
                        Optional.of(SortDirection.ASC), Optional.of(nulls)))).orElseThrow();
                assertThat(h2.rows("SELECT CAST(" + nth + " AS VARCHAR) FROM EMP WHERE DEPT = 'a'"))
                        .as("the 1st value, NULLS " + nulls + ": " + nth)
                        .containsExactly(nulls == NullsOrder.FIRST ? "40" : "20");
            }

            String truncated = d.generateListAgg("NAME", false, ",", null, "...", order("NAME", SortDirection.ASC))
                    .orElseThrow();
            assertThat(truncated.contains("ON OVERFLOW TRUNCATE")).as("the overflow clause").isEqualTo(overflow);
            assertThat(h2.rows("SELECT " + truncated + " FROM EMP WHERE DEPT = 'a'")).as("runs: " + truncated)
                    .containsExactly("alice,bob,bob");

            String perDept = d.generateListAgg("NAME", false, ",", null, null, order("NAME", SortDirection.ASC))
                    .orElseThrow();
            assertThat(h2.rows("SELECT DEPT || '=' || " + perDept + " FROM EMP GROUP BY DEPT ORDER BY DEPT"))
                    .as("per group").containsExactly("a=alice,bob,bob", "b=dave");
        }
    }

    private static List<OrderedColumn> order(String column, SortDirection direction) {
        return List.of(new OrderedColumn(column, null, direction));
    }

    private static String list(H2Versions.Session h2, String operand, boolean distinct, String separator,
            String coalesce, List<OrderedColumn> columns) throws Exception {
        String agg = h2.dialect().generateListAgg(operand, distinct, separator, coalesce, null, columns).orElseThrow();
        List<String> rows = h2.rows("SELECT " + agg + " FROM EMP WHERE DEPT = 'a'");
        assertThat(rows).as(agg).hasSize(1);
        return rows.get(0);
    }
}
