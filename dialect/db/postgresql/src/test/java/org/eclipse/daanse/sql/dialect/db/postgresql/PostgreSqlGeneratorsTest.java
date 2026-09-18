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

import java.sql.JDBCType;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnDefinitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.PrimaryKeyRecord;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
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

    @Test
    void comment_on_table_and_column_standard_form() {
        assertThat(d.commentOnTable(table("public", "USERS"), "it's the users").orElseThrow())
                .isEqualTo("COMMENT ON TABLE \"public\".\"USERS\" IS 'it''s the users'");
        assertThat(d.commentOnColumn(table("public", "USERS"), "NAME", "full name", null).orElseThrow())
                .isEqualTo("COMMENT ON COLUMN \"public\".\"USERS\".\"NAME\" IS 'full name'");
    }

    @Test
    void comment_null_removes_it() {
        assertThat(d.commentOnTable(table("public", "USERS"), null).orElseThrow()).endsWith(" IS NULL");
    }

    @Test
    void rename_index_is_schema_qualified() {
        assertThat(d.renameIndex("ix_old", "ix_new", table("sales", "t")))
                .isEqualTo("ALTER INDEX \"sales\".\"ix_old\" RENAME TO \"ix_new\"");
    }

    @Test
    void create_table_names_the_primary_key() {
        TableReference t = table("sales", "t");
        ColumnDefinition id = new ColumnDefinitionRecord(new ColumnReference(Optional.of(t), "id"),
                new ColumnMetaDataRecord(JDBCType.INTEGER, "INTEGER", OptionalInt.empty(), OptionalInt.empty(),
                        OptionalInt.empty(), ColumnMetaData.Nullability.NO_NULLS, OptionalInt.empty(),
                        Optional.empty(), Optional.empty(), ColumnMetaData.AutoIncrement.UNKNOWN,
                        ColumnMetaData.GeneratedColumn.UNKNOWN));
        assertThat(d.createTable(t, List.of(id), new PrimaryKeyRecord(t, List.of(id.column()), Optional.of("pk_t")),
                false)).contains("CONSTRAINT \"pk_t\" PRIMARY KEY (\"id\")");
        assertThat(d.createTable(t, List.of(id), new PrimaryKeyRecord(t, List.of(id.column()), Optional.empty()),
                false)).contains(",\n  PRIMARY KEY (\"id\")").doesNotContain("CONSTRAINT");
    }
}
