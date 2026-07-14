/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.postgresql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.daanse.sql.dialect.db.testsupport.GeneratorTestSupport.table;
import static org.eclipse.daanse.sql.dialect.db.testsupport.GeneratorTestSupport.upsertSpec;
import static org.eclipse.daanse.sql.dialect.db.testsupport.GeneratorTestSupport.upsertSpecDoNothing;

import java.util.List;
import java.util.OptionalLong;

import org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator;
import org.junit.jupiter.api.Test;

/** Smoke tests for PostgreSQL's engine-specific overrides of new generators. */
class PostgreSqlGeneratorsTest {

    private final PostgreSqlDialect d = new PostgreSqlDialect();

    @Test
    void pagination_limit_offset() {
        assertThat(d.paginationGenerator().paginate(OptionalLong.of(20), OptionalLong.of(40)))
                .isEqualTo(" LIMIT 20 OFFSET 40");
    }

    @Test
    void pagination_limit_only() {
        assertThat(d.paginationGenerator().paginate(OptionalLong.of(20), OptionalLong.empty())).isEqualTo(" LIMIT 20");
    }

    @Test
    void returning_columns() {
        assertThat(d.returningGenerator().supportsReturning()).isTrue();
        assertThat(d.returningGenerator().returning(List.of("id", "name")).orElseThrow())
                .isEqualTo(" RETURNING \"id\", \"name\"");
    }

    @Test
    void returning_star() {
        assertThat(d.returningGenerator().returning(List.of("*")).orElseThrow()).isEqualTo(" RETURNING *");
    }

    @Test
    void upsert_on_conflict_do_update() {
        MergeGenerator.UpsertSpec spec = upsertSpec(table("public", "USERS"), "ID", "NAME");
        String sql = d.mergeGenerator().upsert(spec, List.of("1", "'foo'")).orElseThrow();
        assertThat(sql).contains("INSERT INTO \"public\".\"USERS\"").contains("ON CONFLICT (\"ID\")")
                .contains("DO UPDATE SET \"NAME\" = EXCLUDED.\"NAME\"");
    }

    @Test
    void upsert_on_conflict_do_nothing() {
        MergeGenerator.UpsertSpec spec = upsertSpecDoNothing(table("public", "USERS"), "ID", "NAME");
        String sql = d.mergeGenerator().upsert(spec, List.of("1", "'foo'")).orElseThrow();
        assertThat(sql).contains("ON CONFLICT (\"ID\") DO NOTHING");
    }
}
