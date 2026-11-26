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

import java.util.EnumMap;
import java.util.Map;

import org.eclipse.daanse.sql.model.sql.BitOperation;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * H2 has the bitwise aggregates {@code BIT_AND_AGG}, {@code BIT_OR_AGG}, {@code BIT_XOR_AGG}, {@code BIT_NAND_AGG},
 * {@code BIT_NOR_AGG} and {@code BIT_XNOR_AGG} from 2.0; 1.4 has {@code BIT_AND} and {@code BIT_OR} only. The dialect
 * is built from a live connection of each version, and each aggregate it offers must give the bits of 6, 3 and 5;
 * those 1.4 has not, it offers not.
 */
@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
class H2BitAggregationTest {

    /** 6, 3 and 5: AND 0, OR 7, XOR 0; NAND, NOR and XNOR their complements. */
    private static final Map<BitOperation, String> EXPECTED = new EnumMap<>(Map.of(BitOperation.AND, "0",
            BitOperation.OR, "7", BitOperation.XOR, "0", BitOperation.NAND, "-1", BitOperation.NOR, "-8",
            BitOperation.NXOR, "-1"));

    @ParameterizedTest(name = "h2 {0} -> all six {1}")
    @CsvSource({ "1.4.200, false", "2.0.206, true", "2.1.214, true", "2.2.224, true", "2.3.232, true" })
    void bitAggregation(String version, boolean allSix) throws Exception {
        try (H2Versions.Session h2 = H2Versions.open(version)) {
            h2.execute("CREATE TABLE T(X INT)");
            h2.execute("INSERT INTO T VALUES (6), (3), (5), (NULL)");
            for (BitOperation op : BitOperation.values()) {
                boolean offered = allSix || op == BitOperation.AND || op == BitOperation.OR;
                assertThat(h2.dialect().supportsBitAggregation(op)).as("supports " + op).isEqualTo(offered);
                if (!offered) {
                    assertThat(h2.dialect().generateBitAggregation(op, "X")).as(op + " not before 2.0").isEmpty();
                    continue;
                }
                String agg = h2.dialect().generateBitAggregation(op, "X").orElseThrow();
                assertThat(h2.rows("SELECT CAST(" + agg + " AS VARCHAR) FROM T")).as(op + ": " + agg)
                        .containsExactly(EXPECTED.get(op));
            }
        }
    }
}
