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

import java.sql.SQLException;
import java.util.List;

import org.eclipse.daanse.sql.dialect.api.generator.CteGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.CteGenerator.Cte;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * H2 runs a recursive common table expression only with the list of its columns, {@code WITH RECURSIVE n(k) AS (…)},
 * in every version; without, "Column not found", in 2.3 a syntax error. An expression of the generator has no such
 * list: the dialect built from a live connection of each version says it has no recursive ones and writes no
 * RECURSIVE. Two expressions of one clause, the second reading the first, run on every version.
 */
@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
class H2RecursiveCteTest {

    private static final List<Cte> NUMBERS = List
            .of(new Cte("N", "SELECT 1 AS K UNION ALL SELECT K + 1 FROM N WHERE K < 5"));

    @ParameterizedTest(name = "h2 {0}")
    @ValueSource(strings = { "1.4.200", "2.0.206", "2.1.214", "2.2.224", "2.3.232" })
    void recursiveCte(String version) throws Exception {
        try (H2Versions.Session h2 = H2Versions.open(version)) {
            h2.execute("CREATE TABLE EMP(ID INT, BOSS INT, NAME VARCHAR(10))");
            h2.execute("INSERT INTO EMP VALUES (1, NULL, 'ceo'), (2, 1, 'a'), (3, 1, 'b'), (4, 2, 'c')");
            CteGenerator cte = h2.dialect().cteGenerator();
            assertThat(cte.supportsRecursiveCte()).as("supportsRecursiveCte").isFalse();
            assertThat(cte.withClause(NUMBERS, true)).as("no RECURSIVE written").startsWith("WITH N AS (");

            List<Cte> bosses = List.of(new Cte("BOSSES", "SELECT DISTINCT BOSS FROM EMP WHERE BOSS IS NOT NULL"),
                    new Cte("NAMED", "SELECT E.NAME FROM EMP E JOIN BOSSES B ON E.ID = B.BOSS"));
            assertThat(h2.rows(cte.withClause(bosses, false) + "SELECT NAME FROM NAMED ORDER BY NAME"))
                    .as("two of one clause, the second reading the first").containsExactly("a", "ceo");

            assertThatThrownBy(() -> h2.rows("WITH RECURSIVE N AS (SELECT 1 AS K UNION ALL SELECT K + 1 FROM N"
                    + " WHERE K < 5) SELECT SUM(K) FROM N")).as("RECURSIVE without the list of columns")
                    .isInstanceOf(SQLException.class);
            assertThat(h2.rows("WITH RECURSIVE N(K) AS (SELECT 1 UNION ALL SELECT K + 1 FROM N WHERE K < 5)"
                    + " SELECT CAST(SUM(K) AS VARCHAR) FROM N")).as("with it, which no expression gives")
                    .containsExactly("15");
        }
    }
}
