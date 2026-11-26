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
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.eclipse.daanse.sql.dialect.db.h2.H2Dialect;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * H2 has {@code PERCENTILE_DISC} and {@code PERCENTILE_CONT … WITHIN GROUP (ORDER BY …)} on every version from
 * 1.4.200 on; its discrete one takes the nearest of the positions p (n - 1), not the standard's first value whose
 * cumulative share reaches p, and the dialect computes it on the sorted group. Built from a live connection of each
 * version, its percentiles must give what the standard says: the discrete one a value of the group, the first whose
 * cumulative share reaches p, the continuous one interpolated, ascending and descending, the NULLs left out, p at its
 * ends, a column named with its table, per group, and NULL for a group of no value.
 */
@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
class H2PercentileTest {

    @ParameterizedTest(name = "h2 {0}")
    @ValueSource(strings = { "1.4.200", "2.0.206", "2.1.214", "2.2.224", "2.3.232" })
    void percentile(String version) throws Exception {
        try (H2Versions.Session h2 = H2Versions.open(version)) {
            // group a: 10, 20, 30, 40 and a NULL every aggregate leaves out; b: 5; c: only a NULL
            h2.execute("CREATE TABLE NUMS(G VARCHAR(1), V INT)");
            h2.execute("INSERT INTO NUMS VALUES ('a', 30), ('a', 10), ('a', NULL), ('a', 40), ('a', 20), ('b', 5),"
                    + " ('c', NULL)");
            H2Dialect d = h2.dialect();
            assertThat(d.supportsPercentileDisc()).isTrue();
            assertThat(d.supportsPercentileCont()).isTrue();

            assertThat(disc(h2, 0.5, false, "NUMS")).as("disc 0.5").isEqualTo(20);
            assertThat(disc(h2, 0.25, false, null)).as("disc 0.25: cume_dist of 10 is 0.25").isEqualTo(10);
            assertThat(disc(h2, 0.7, false, null)).as("disc 0.7").isEqualTo(30);
            assertThat(disc(h2, 0, false, null)).as("disc 0, the least").isEqualTo(10);
            assertThat(disc(h2, 1, false, null)).as("disc 1, the greatest").isEqualTo(40);
            assertThat(disc(h2, 0.5, true, null)).as("disc 0.5 descending").isEqualTo(30);
            assertThat(disc(h2, 0.25, true, "NUMS")).as("disc 0.25 descending").isEqualTo(40);

            // 1 to 10, where H2's own discrete percentile is one off at 0.1, 0.2 and 0.3
            h2.execute("CREATE TABLE TEN(V INT)");
            h2.execute("INSERT INTO TEN SELECT X FROM SYSTEM_RANGE(1, 10)");
            for (double[] pv : new double[][] { { 0.1, 1 }, { 0.2, 2 }, { 0.25, 3 }, { 0.3, 3 }, { 0.5, 5 },
                    { 0.75, 8 }, { 0.9, 9 }, { 1, 10 } }) {
                String disc = d.generatePercentileDisc(pv[0], false, null, "V").orElseThrow();
                assertThat(h2.rows("SELECT CAST(" + disc + " AS VARCHAR) FROM TEN")).as("disc " + pv[0] + " of 1..10")
                        .containsExactly(String.valueOf((int) pv[1]));
            }
            assertThat(h2.rows("SELECT CAST(PERCENTILE_DISC(0.1) WITHIN GROUP (ORDER BY V) AS VARCHAR) FROM TEN"))
                    .as("H2's own, the nearest of the positions p (n - 1)").containsExactly("2");

            assertThat(cont(h2, 0.5, false, "NUMS")).as("cont 0.5").isEqualTo(25);
            assertThat(cont(h2, 0.25, false, null)).as("cont 0.25").isEqualTo(17.5);
            assertThat(cont(h2, 0.7, false, null)).as("cont 0.7").isCloseTo(31, within(1e-9));
            assertThat(cont(h2, 0, false, null)).as("cont 0, the least").isEqualTo(10);
            assertThat(cont(h2, 1, false, null)).as("cont 1, the greatest").isEqualTo(40);
            assertThat(cont(h2, 0.25, true, null)).as("cont 0.25 descending").isEqualTo(32.5);
            assertThat(cont(h2, 1, true, "NUMS")).as("descending, the end: the least").isEqualTo(10);

            String perGroup = "SELECT G || '=' || COALESCE(CAST(%s AS VARCHAR), 'null') FROM NUMS GROUP BY G"
                    + " ORDER BY G";
            assertThat(h2.rows(perGroup.formatted(d.generatePercentileDisc(0.5, false, null, "V").orElseThrow())))
                    .as("disc per group, one value, no value").containsExactly("a=20", "b=5", "c=null");
            List<String> cont = h2.rows(perGroup.formatted(d.generatePercentileCont(0.5, false, null, "V")
                    .orElseThrow()));
            assertThat(cont).as("cont per group, one value, no value").hasSize(3);
            assertThat(Double.parseDouble(cont.get(0).substring(2))).isEqualTo(25);
            assertThat(Double.parseDouble(cont.get(1).substring(2))).isEqualTo(5);
            assertThat(cont.get(2)).isEqualTo("c=null");
        }
    }

    private static double disc(H2Versions.Session h2, double p, boolean desc, String table) throws Exception {
        return value(h2, h2.dialect().generatePercentileDisc(p, desc, table, "V").orElseThrow());
    }

    private static double cont(H2Versions.Session h2, double p, boolean desc, String table) throws Exception {
        return value(h2, h2.dialect().generatePercentileCont(p, desc, table, "V").orElseThrow());
    }

    /** The percentile of group a. */
    private static double value(H2Versions.Session h2, String percentile) throws Exception {
        List<String> rows = h2.rows("SELECT CAST(" + percentile + " AS VARCHAR) FROM NUMS WHERE G = 'a'");
        assertThat(rows).as(percentile).hasSize(1);
        assertThat(rows.get(0)).as(percentile).isNotNull();
        return Double.parseDouble(rows.get(0));
    }
}
