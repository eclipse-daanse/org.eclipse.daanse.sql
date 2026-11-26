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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator.PagingParam;
import org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator.PreparedPaging;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * H2 limits and skips rows with {@code LIMIT n OFFSET m} and {@code LIMIT n}, an offset alone with
 * {@code OFFSET m ROWS}, on every version from 1.4.200 on; {@code LIMIT -1}, which the dialect wrote for an offset
 * alone, H2 2.x refuses. The dialect is built from a live connection of each version, and the paging it writes must
 * skip and limit the rows, limit to none, skip past the end, and run with its parameters bound in the order it gives.
 */
@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
class H2PaginationTest {

    private static final String SORTED = "SELECT CAST(X AS VARCHAR) FROM T ORDER BY X";

    @ParameterizedTest(name = "h2 {0}")
    @ValueSource(strings = { "1.4.200", "2.0.206", "2.1.214", "2.2.224", "2.3.232" })
    void pagination(String version) throws Exception {
        try (H2Versions.Session h2 = H2Versions.open(version)) {
            h2.execute("CREATE TABLE T(X INT)");
            h2.execute("INSERT INTO T VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9)");
            PaginationGenerator p = h2.dialect().paginationGenerator();
            assertThat(p.supportsOffset()).isTrue();

            assertThat(h2.rows(SORTED + paging(p, 3, 2))).as("skip 2, take 3").containsExactly("2", "3", "4");
            assertThat(h2.rows(SORTED + paging(p, 3, null))).as("take 3").containsExactly("0", "1", "2");
            assertThat(h2.rows(SORTED + paging(p, null, 7))).as("skip 7, an offset alone").containsExactly("7", "8",
                    "9");
            assertThat(h2.rows(SORTED + paging(p, 0, null))).as("take none").isEmpty();
            assertThat(h2.rows(SORTED + paging(p, null, 20))).as("skip past the end").isEmpty();
            assertThat(h2.rows(SORTED + paging(p, null, null))).as("no paging").hasSize(10);
            assertThat(h2.rows("SELECT COUNT(*) FROM (SELECT X FROM T" + paging(p, null, 4) + ")"))
                    .as("an offset alone without ORDER BY").containsExactly("6");

            assertThat(prepared(h2, p, 3L, 2L)).as("prepared, skip 2, take 3").containsExactly("2", "3", "4");
            assertThat(prepared(h2, p, 3L, null)).as("prepared, take 3").containsExactly("0", "1", "2");
            assertThat(prepared(h2, p, null, 7L)).as("prepared, skip 7").containsExactly("7", "8", "9");

            if (!version.startsWith("1.")) {
                assertThatThrownBy(() -> h2.rows(SORTED + " LIMIT -1 OFFSET 7")).as("LIMIT -1, refused by 2.x")
                        .isInstanceOf(SQLException.class);
            }
        }
    }

    private static String paging(PaginationGenerator p, Integer limit, Integer offset) {
        return p.paginate(limit == null ? OptionalLong.empty() : OptionalLong.of(limit),
                offset == null ? OptionalLong.empty() : OptionalLong.of(offset));
    }

    private static List<String> prepared(H2Versions.Session h2, PaginationGenerator p, Long limit, Long offset)
            throws SQLException {
        PreparedPaging paging = p.paginatePrepared(limit != null, offset != null);
        String sql = SORTED + " " + paging.clause();
        try (PreparedStatement ps = h2.connection().prepareStatement(sql)) {
            int i = 1;
            for (PagingParam param : paging.order()) {
                ps.setLong(i++, param == PagingParam.LIMIT ? limit : offset);
            }
            List<String> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            return out;
        }
    }
}
