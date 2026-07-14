/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator;
import org.junit.jupiter.api.Test;

class SqliteDialectTest {

    private static final TableReference USERS = new TableReference(
            Optional.of(new SchemaReference(Optional.empty(), "main")), "USERS", TableReference.TYPE_TABLE);

    @Test
    void capability_flags_reflect_sqlite_constraints() {
        SqliteDialect dialect = new SqliteDialect();
        // SQLite has no SQL sequences (uses ROWID/AUTOINCREMENT).
        assertFalse(dialect.supportsSequences());
        // Constraints can't be dropped on existing tables — must rebuild.
        assertFalse(dialect.supportsDropConstraintIfExists());
        // No OR REPLACE on CREATE VIEW.
        assertFalse(dialect.supportsCreateOrReplaceView());
        // DROP TABLE doesn't accept CASCADE.
        assertFalse(dialect.supportsDropTableCascade());
        // IF EXISTS forms ARE supported on most DDL — defaults remain true.
        assertTrue(dialect.supportsDropTableIfExists());
        assertTrue(dialect.supportsCreateTableIfNotExists());
    }

    /** No-arg ctor → version=UNKNOWN → modern-engine assumption: emit RETURNING. */
    @Test
    void returning_emitted_when_version_unknown() {
        SqliteDialect d = new SqliteDialect();
        assertThat(d.returningGenerator().returning(List.of("id"))).contains(" RETURNING \"id\"");
    }

    /** SQLite ≥3.35: emit RETURNING. */
    @Test
    void returning_emitted_when_version_at_least_3_35() {
        SqliteDialect d = withVersion(3, 35);
        assertThat(d.returningGenerator().supportsReturning()).isTrue();
        assertThat(d.returningGenerator().returning(List.of("id", "name")).orElseThrow())
                .isEqualTo(" RETURNING \"id\", \"name\"");
    }

    /** SQLite < 3.35 (e.g., 3.34): falls through to empty default. */
    @Test
    void returning_empty_when_version_below_3_35() {
        SqliteDialect d = withVersion(3, 34);
        assertThat(d.returningGenerator().returning(List.of("id"))).isEmpty();
    }

    /** SQLite ≥3.24: ON CONFLICT … DO UPDATE. */
    @Test
    void merge_emits_on_conflict_when_version_at_least_3_24() {
        SqliteDialect d = withVersion(3, 24);
        MergeGenerator.UpsertSpec spec = new MergeGenerator.UpsertSpec(USERS, List.of("id"), List.of("id", "name"),
                List.of("name"));
        String sql = d.mergeGenerator().upsert(spec, List.of("1", "'foo'")).orElseThrow();
        assertThat(sql).contains("INSERT INTO \"main\".\"USERS\"")
                .contains("ON CONFLICT (\"id\") DO UPDATE SET \"name\" = excluded.\"name\"");
    }

    /** SQLite < 3.24 (e.g., 3.23): falls through to empty default. */
    @Test
    void merge_unsupported_when_version_below_3_24() {
        SqliteDialect d = withVersion(3, 23);
        MergeGenerator.UpsertSpec spec = new MergeGenerator.UpsertSpec(USERS, List.of("id"), List.of("id", "name"),
                List.of("name"));
        assertThat(d.mergeGenerator().upsert(spec, List.of("1", "'foo'"))).isEmpty();
    }

    private static SqliteDialect withVersion(int major, int minor) {
        return new SqliteDialect(DialectInitData.ansiDefaults().withVersion(major, minor));
    }
}
