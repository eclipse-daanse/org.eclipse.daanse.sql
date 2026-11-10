/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.mariadb.sqlgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.JDBCType;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.sql.dialect.api.IdentifierQuotingPolicy;
import org.eclipse.daanse.sql.dialect.db.mariadb.MariaDBDialect;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.junit.jupiter.api.Test;

/**
 * MariaDB extends MySqlDialect — confirms the MySQL overrides are inherited.
 */
class MariaDbAlterRenameOfflineTest {

    private static final SchemaReference S = new SchemaReference(Optional.empty(), "appdb");
    private static final TableReference T = new TableReference(Optional.of(S), "EMPLOYEES", TableReference.TYPE_TABLE);

    private final MariaDBDialect dialect = new MariaDBDialect();

    /**
     * MariaDB 10.4.34 — below the 10.5.2 threshold where native
     * {@code RENAME COLUMN} landed. Major 10 alone would satisfy the inherited
     * MySQL parent's 8.0 gate, so this version only falls back to CHANGE if
     * MariaDB's own 10.5 threshold is actually consulted.
     */
    private final MariaDBDialect dialect104 = new MariaDBDialect(
            new DialectInitData("`", "MariaDB", "10.4.34", 10, 4, Set.of(), true, 0, Set.of(),
                    IdentifierQuotingPolicy.ALWAYS));

    /** MariaDB 10.6.5 — above the 10.5.2 threshold: native {@code RENAME COLUMN}. */
    private final MariaDBDialect dialect106 = new MariaDBDialect(
            new DialectInitData("`", "MariaDB", "10.6.5", 10, 6, Set.of(), true, 0, Set.of(),
                    IdentifierQuotingPolicy.ALWAYS));

    private static ColumnMetaData meta(JDBCType jdbc, OptionalInt size) {
        return new ColumnMetaDataRecord(jdbc, jdbc.getName(), size, OptionalInt.empty(), OptionalInt.empty(),
                ColumnMetaData.Nullability.NULLABLE, OptionalInt.empty(), Optional.empty(), Optional.empty(),
                ColumnMetaData.AutoIncrement.UNKNOWN, ColumnMetaData.GeneratedColumn.UNKNOWN);
    }

    @Test
    void inherits_MODIFY_COLUMN_from_MySqlDialect() {
        assertThat(dialect.ddlGenerator().alterColumnType(T, "SALARY", meta(JDBCType.DECIMAL, OptionalInt.of(12))))
                .contains("MODIFY COLUMN");
    }

    @Test
    void inherits_table_scoped_renameIndex() {
        assertThat(dialect.ddlGenerator().renameIndex("IDX_OLD", "IDX_NEW", T)).startsWith("ALTER TABLE ")
                .contains("RENAME INDEX");
    }

    @Test
    void inherits_renameConstraint_returning_null() {
        assertThat(dialect.ddlGenerator().renameConstraint(T, "OLD_FK", "NEW_FK")).isNull();
    }

    @Test
    void renameColumn_10_4_34_falls_back_to_CHANGE_not_MySQLs_8_0_gate() {
        // Major 10 alone would satisfy the inherited MySQL parent's "8.0+" check,
        // so this failing without metadata proves MariaDB's own 10.5 gate — not
        // MySQL's — is what governs here.
        assertThat(dialect104.ddlGenerator().supportsRenameColumn()).isTrue();
        assertThat(dialect104.ddlGenerator().renameColumn(T, "OLD", "NEW")).isNull();
    }

    @Test
    void renameColumn_10_4_34_with_metadata_uses_CHANGE() {
        assertThat(dialect104.ddlGenerator().renameColumn(T, "OLD", "NEW",
                meta(JDBCType.DECIMAL, OptionalInt.of(12))))
                .startsWith("ALTER TABLE `appdb`.`EMPLOYEES` CHANGE `OLD` `NEW` ")
                .doesNotContain("RENAME COLUMN");
    }

    @Test
    void renameColumn_10_6_5_uses_native_RENAME_COLUMN() {
        assertThat(dialect106.ddlGenerator().renameColumn(T, "OLD", "NEW"))
                .isEqualTo("ALTER TABLE `appdb`.`EMPLOYEES` RENAME COLUMN `OLD` TO `NEW`");
    }

    @Test
    void renameSequence_uses_ALTER_TABLE_not_ALTER_SEQUENCE() {
        // MariaDB stores sequences in the table namespace — there is no
        // ALTER SEQUENCE ... RENAME TO in its grammar; verified live.
        assertThat(dialect.ddlGenerator().supportsRenameSequence()).isTrue();
        assertThat(dialect.ddlGenerator().renameSequence("appdb", "SEQ_EMP", "SEQ_STAFF"))
                .contains("ALTER TABLE `appdb`.`SEQ_EMP` RENAME TO `SEQ_STAFF`");
    }

    @Test
    void renameSequence_10_4_34_unsupported_below_the_10_5_2_threshold() {
        assertThat(dialect104.ddlGenerator().supportsRenameSequence()).isFalse();
        assertThat(dialect104.ddlGenerator().renameSequence("appdb", "SEQ_EMP", "SEQ_STAFF")).isEmpty();
    }

    @Test
    void renameSequence_10_6_5_uses_ALTER_TABLE_RENAME_TO() {
        assertThat(dialect106.ddlGenerator().supportsRenameSequence()).isTrue();
        assertThat(dialect106.ddlGenerator().renameSequence("appdb", "SEQ_EMP", "SEQ_STAFF"))
                .contains("ALTER TABLE `appdb`.`SEQ_EMP` RENAME TO `SEQ_STAFF`");
    }
}
