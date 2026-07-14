/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.jdbc.impl.sqlgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.JDBCType;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.model.schema.PrimaryKey;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnDefinitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.PrimaryKeyRecord;
import org.junit.jupiter.api.Test;

class DialectDdlOfflineTest {

    private static final SchemaReference PUBLIC = new SchemaReference(Optional.empty(), "PUBLIC");
    private static final TableReference EMPLOYEES =
            new TableReference(Optional.of(PUBLIC), "EMPLOYEES", TableReference.TYPE_TABLE);

    private static ColumnDefinition col(String name, JDBCType jdbc, ColumnMetaData.Nullability nullability,
            OptionalInt size) {
        ColumnReference ref = new ColumnReference(Optional.of(EMPLOYEES), name);
        ColumnMetaData meta = new ColumnMetaDataRecord(
                jdbc,
                jdbc.getName(),
                size,
                OptionalInt.empty(),
                OptionalInt.empty(),
                nullability,
                OptionalInt.empty(),
                Optional.empty(),
                Optional.empty(),
                ColumnMetaData.AutoIncrement.UNKNOWN,
                ColumnMetaData.GeneratedColumn.UNKNOWN);
        return new ColumnDefinitionRecord(ref, meta);
    }

    private static List<ColumnDefinition> sampleColumns() {
        return List.of(
                col("EMP_ID", JDBCType.INTEGER, ColumnMetaData.Nullability.NO_NULLS, OptionalInt.empty()),
                col("FIRST_NAME", JDBCType.VARCHAR, ColumnMetaData.Nullability.NO_NULLS, OptionalInt.of(50)),
                col("SALARY", JDBCType.DECIMAL, ColumnMetaData.Nullability.NULLABLE, OptionalInt.of(10)));
    }

    @Test
    void ansi_dialect_no_arg_ctor_uses_double_quote() {
        Dialect dialect = new AnsiDialect();
        assertThat(dialect.getQuoteIdentifierString()).isEqualTo("\"");
    }

    @Test
    void createTable_with_primary_key() {
        Dialect dialect = new AnsiDialect();
        List<ColumnDefinition> columns = sampleColumns();
        PrimaryKey pk = new PrimaryKeyRecord(
                EMPLOYEES,
                List.of(columns.get(0).column()),
                Optional.empty());
        String sql = dialect.ddlGenerator().createTable(EMPLOYEES, columns, pk, true);
        assertThat(sql).isEqualTo(
                "CREATE TABLE IF NOT EXISTS \"PUBLIC\".\"EMPLOYEES\" (\n"
                        + "  \"EMP_ID\" INTEGER NOT NULL,\n"
                        + "  \"FIRST_NAME\" VARCHAR(50) NOT NULL,\n"
                        + "  \"SALARY\" DECIMAL(10),\n"
                        + "  PRIMARY KEY (\"EMP_ID\")\n"
                        + ")");
    }

    @Test
    void insertInto_parameterised() {
        Dialect dialect = new AnsiDialect();
        String sql = dialect.ddlGenerator().insertInto(EMPLOYEES, sampleColumns());
        assertThat(sql).isEqualTo(
                "INSERT INTO \"PUBLIC\".\"EMPLOYEES\" (\"EMP_ID\", \"FIRST_NAME\", \"SALARY\") VALUES (?, ?, ?)");
    }

    @Test
    void selectFrom_with_columns() {
        Dialect dialect = new AnsiDialect();
        String sql = dialect.ddlGenerator().selectFrom(EMPLOYEES, sampleColumns());
        assertThat(sql).isEqualTo(
                "SELECT \"EMP_ID\", \"FIRST_NAME\", \"SALARY\" FROM \"PUBLIC\".\"EMPLOYEES\"");
    }

    @Test
    void selectAll_star() {
        Dialect dialect = new AnsiDialect();
        assertThat(dialect.ddlGenerator().selectAll(EMPLOYEES))
                .isEqualTo("SELECT * FROM \"PUBLIC\".\"EMPLOYEES\"");
    }

    @Test
    void update_with_where_columns() {
        Dialect dialect = new AnsiDialect();
        List<ColumnDefinition> all = sampleColumns();
        String sql = dialect.ddlGenerator().update(
                EMPLOYEES,
                List.of(all.get(1), all.get(2)),
                List.of(all.get(0)));
        assertThat(sql).isEqualTo(
                "UPDATE \"PUBLIC\".\"EMPLOYEES\" SET \"FIRST_NAME\" = ?, \"SALARY\" = ? WHERE \"EMP_ID\" = ?");
    }

    @Test
    void deleteFrom_with_where() {
        Dialect dialect = new AnsiDialect();
        String sql = dialect.ddlGenerator().deleteFrom(EMPLOYEES, List.of(sampleColumns().get(0)));
        assertThat(sql).isEqualTo("DELETE FROM \"PUBLIC\".\"EMPLOYEES\" WHERE \"EMP_ID\" = ?");
    }

    @Test
    void deleteFrom_unfiltered_emits_no_where() {
        Dialect dialect = new AnsiDialect();
        String sql = dialect.ddlGenerator().deleteFrom(EMPLOYEES, List.of());
        assertThat(sql).isEqualTo("DELETE FROM \"PUBLIC\".\"EMPLOYEES\"");
    }

    @Test
    void dropTable_if_exists() {
        Dialect dialect = new AnsiDialect();
        String sql = dialect.ddlGenerator().dropTable(EMPLOYEES, true);
        // Delegates to dialect.dropTable — exact format is dialect-specific; both
        // schema and table identifiers must appear quoted.
        assertThat(sql).contains("DROP TABLE")
                .contains("IF EXISTS")
                .contains("\"PUBLIC\"")
                .contains("\"EMPLOYEES\"");
        // Default overload doesn't add CASCADE.
        assertThat(sql).doesNotContain("CASCADE");
    }

    @Test
    void dropTable_with_cascade_appends_cascade() {
        Dialect dialect = new AnsiDialect();
        String sql = dialect.ddlGenerator().dropTable(EMPLOYEES, true, true);
        assertThat(sql).contains("DROP TABLE").endsWith("CASCADE");
    }

    @Test
    void dropTable_cascade_silently_dropped_for_dialects_without_support() {
        // Anonymous dialect that mimics MySQL's lack of CASCADE support.
        Dialect noCascadeDialect = new AnsiDialect() {
            @Override
            public boolean supportsDropTableCascade() {
                return false;
            }
        };
        String sql = noCascadeDialect.ddlGenerator().dropTable(EMPLOYEES, true, true);
        assertThat(sql).contains("DROP TABLE").doesNotContain("CASCADE");
    }

    @Test
    void createSequence_with_full_options() {
        Dialect dialect = new AnsiDialect();
        DdlGenerator.SequenceDefinition seq = new DdlGenerator.SequenceDefinition(
                "PUBLIC", "ORDER_SEQ",
                100L,    // startWith
                1L,      // incrementBy
                100L,    // minValue
                999_999L,// maxValue
                false,   // cycle
                20L);    // cache
        assertThat(dialect.ddlGenerator().createSequence(seq, true)).contains(
                "CREATE SEQUENCE IF NOT EXISTS \"PUBLIC\".\"ORDER_SEQ\""
                        + " START WITH 100 INCREMENT BY 1 MINVALUE 100 MAXVALUE 999999"
                        + " NO CYCLE CACHE 20");
    }

    @Test
    void createSequence_minimal() {
        Dialect dialect = new AnsiDialect();
        assertThat(dialect.ddlGenerator().createSequence(
                DdlGenerator.SequenceDefinition.of("ORDER_SEQ"), false))
                .contains("CREATE SEQUENCE \"ORDER_SEQ\"");
    }

    @Test
    void dropSequence_if_exists() {
        Dialect dialect = new AnsiDialect();
        assertThat(dialect.ddlGenerator().dropSequence("PUBLIC", "ORDER_SEQ", true))
                .contains("DROP SEQUENCE IF EXISTS \"PUBLIC\".\"ORDER_SEQ\"");
    }

    @Test
    void nextValueFor_emits_standard_form() {
        Dialect dialect = new AnsiDialect();
        assertThat(dialect.ddlGenerator().nextValueFor("PUBLIC", "ORDER_SEQ"))
                .contains("NEXT VALUE FOR \"PUBLIC\".\"ORDER_SEQ\"");
    }

    @Test
    void sequence_methods_return_empty_when_dialect_lacks_support() {
        Dialect noSeq = new AnsiDialect() {
            @Override
            public boolean supportsSequences() {
                return false;
            }
        };
        assertThat(noSeq.ddlGenerator().createSequence(
                DdlGenerator.SequenceDefinition.of("ORDER_SEQ"), false)).isEmpty();
        assertThat(noSeq.ddlGenerator().dropSequence(null, "ORDER_SEQ", false)).isEmpty();
        assertThat(noSeq.ddlGenerator().nextValueFor(null, "ORDER_SEQ")).isEmpty();
    }

    @Test
    void sequence_definition_rejects_blank_name() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> DdlGenerator.SequenceDefinition.of(""));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> DdlGenerator.SequenceDefinition.of(null));
    }

    @Test
    void truncate_delegates_to_clearTable() {
        Dialect dialect = new AnsiDialect();
        String sql = dialect.ddlGenerator().truncate(EMPLOYEES);
        // H2's clearTable is "TRUNCATE TABLE schema.table".
        assertThat(sql).contains("TRUNCATE")
                .contains("\"PUBLIC\"")
                .contains("\"EMPLOYEES\"");
    }

    @Test
    void alterTable_addColumn() {
        Dialect dialect = new AnsiDialect();
        ColumnDefinition newCol =
                col("HIRE_DATE", JDBCType.DATE, ColumnMetaData.Nullability.NULLABLE, OptionalInt.empty());
        String sql = dialect.ddlGenerator().alterTableAddColumn(EMPLOYEES, newCol);
        assertThat(sql).isEqualTo(
                "ALTER TABLE \"PUBLIC\".\"EMPLOYEES\" ADD COLUMN \"HIRE_DATE\" DATE");
    }

    @Test
    void alterTable_dropColumn() {
        Dialect dialect = new AnsiDialect();
        String sql = dialect.ddlGenerator().alterTableDropColumn(EMPLOYEES, "FIRST_NAME");
        assertThat(sql).isEqualTo(
                "ALTER TABLE \"PUBLIC\".\"EMPLOYEES\" DROP COLUMN \"FIRST_NAME\"");
    }

    @Test
    void createIndex_unique_ifNotExists() {
        Dialect dialect = new AnsiDialect();
        String sql = dialect.ddlGenerator().createIndex(
                "IDX_EMP_NAME", EMPLOYEES, List.of("FIRST_NAME"), true, true);
        assertThat(sql).isEqualTo(
                "CREATE UNIQUE INDEX IF NOT EXISTS \"IDX_EMP_NAME\" ON \"PUBLIC\".\"EMPLOYEES\" (\"FIRST_NAME\")");
    }

    @Test
    void createIndex_compound_non_unique() {
        Dialect dialect = new AnsiDialect();
        String sql = dialect.ddlGenerator().createIndex(
                "IDX_EMP_COMP", EMPLOYEES, List.of("EMP_ID", "FIRST_NAME"), false, false);
        assertThat(sql).isEqualTo(
                "CREATE INDEX \"IDX_EMP_COMP\" ON \"PUBLIC\".\"EMPLOYEES\" (\"EMP_ID\", \"FIRST_NAME\")");
    }

    @Test
    void dropIndex_if_exists() {
        Dialect dialect = new AnsiDialect();
        String sql = dialect.ddlGenerator().dropIndex("IDX_EMP_NAME", true);
        assertThat(sql).isEqualTo("DROP INDEX IF EXISTS \"IDX_EMP_NAME\"");
    }

    @Test
    void createView_or_replace() {
        Dialect dialect = new AnsiDialect();
        TableReference view = new TableReference(Optional.of(PUBLIC), "EMP_NAMES", TableReference.TYPE_VIEW);
        String selectSql = dialect.ddlGenerator().selectFrom(EMPLOYEES, List.of(sampleColumns().get(1)));
        String sql = dialect.ddlGenerator().createView(view, selectSql, true);
        assertThat(sql).isEqualTo(
                "CREATE OR REPLACE VIEW \"PUBLIC\".\"EMP_NAMES\" AS "
                        + "SELECT \"FIRST_NAME\" FROM \"PUBLIC\".\"EMPLOYEES\"");
    }

    @Test
    void dropView_if_exists() {
        Dialect dialect = new AnsiDialect();
        TableReference view = new TableReference(Optional.of(PUBLIC), "EMP_NAMES", TableReference.TYPE_VIEW);
        String sql = dialect.ddlGenerator().dropView(view, true);
        assertThat(sql).isEqualTo("DROP VIEW IF EXISTS \"PUBLIC\".\"EMP_NAMES\"");
    }

    @Test
    void dropIndex_with_table_qualifies_with_schema_on_engines_that_dont_require_on_table() {
        Dialect dialect = new AnsiDialect();
        String sql = dialect.ddlGenerator().dropIndex("IDX_EMP_NAME", EMPLOYEES, true);
        // Schema-qualified, no ON table clause (H2.dropIndexRequiresTable=false).
        assertThat(sql).isEqualTo("DROP INDEX IF EXISTS \"PUBLIC\".\"IDX_EMP_NAME\"");
    }

    @Test
    void dropIndex_with_table_appends_on_table_when_dialect_requires() {
        Dialect onTableDialect = new AnsiDialect() {
            @Override public boolean dropIndexRequiresTable() { return true; }
        };
        String sql = onTableDialect.ddlGenerator().dropIndex("IDX_EMP_NAME", EMPLOYEES, true);
        // MySQL/MariaDB-style: bare name + ON table.
        assertThat(sql).isEqualTo(
                "DROP INDEX IF EXISTS \"IDX_EMP_NAME\" ON \"PUBLIC\".\"EMPLOYEES\"");
    }

    @Test
    void dropIndex_strips_if_exists_when_dialect_lacks_support() {
        Dialect noIfExistsDialect = new AnsiDialect() {
            @Override public boolean supportsDropIndexIfExists() { return false; }
        };
        String sql = noIfExistsDialect.ddlGenerator().dropIndex("IDX_EMP_NAME", EMPLOYEES, true);
        assertThat(sql).isEqualTo("DROP INDEX \"PUBLIC\".\"IDX_EMP_NAME\"");
    }

    @Test
    void dropConstraint_strips_if_exists_when_dialect_lacks_support() {
        Dialect noDropConstraintIfExists = new AnsiDialect() {
            @Override public boolean supportsDropConstraintIfExists() { return false; }
        };
        String sql = noDropConstraintIfExists.ddlGenerator().dropConstraint(EMPLOYEES, "FK_EMP", true);
        assertThat(sql).isEqualTo("ALTER TABLE \"PUBLIC\".\"EMPLOYEES\" DROP CONSTRAINT \"FK_EMP\"");
    }

    @Test
    void dropConstraint_keeps_if_exists_by_default() {
        Dialect dialect = new AnsiDialect();
        String sql = dialect.ddlGenerator().dropConstraint(EMPLOYEES, "FK_EMP", true);
        assertThat(sql).isEqualTo(
                "ALTER TABLE \"PUBLIC\".\"EMPLOYEES\" DROP CONSTRAINT IF EXISTS \"FK_EMP\"");
    }

    @Test
    void dropSchema_delegates_to_dialect() {
        Dialect dialect = new AnsiDialect();
        String sql = dialect.ddlGenerator().dropSchema("PUBLIC", true, true);
        // Default impl in AbstractJdbcDialect emits IF EXISTS + CASCADE.
        assertThat(sql).isEqualTo("DROP SCHEMA IF EXISTS \"PUBLIC\" CASCADE");
    }

    @Test
    void dropSchema_strips_if_exists_when_dialect_lacks_support() {
        Dialect dialect = new AnsiDialect() {
            @Override public boolean supportsDropSchemaIfExists() { return false; }
        };
        String sql = dialect.ddlGenerator().dropSchema("PUBLIC", true, true);
        assertThat(sql).isEqualTo("DROP SCHEMA \"PUBLIC\" CASCADE");
    }

    @Test
    void dropSchema_strips_cascade_when_dialect_lacks_support() {
        Dialect dialect = new AnsiDialect() {
            @Override public boolean supportsDropTableCascade() { return false; }
        };
        String sql = dialect.ddlGenerator().dropSchema("PUBLIC", true, true);
        assertThat(sql).isEqualTo("DROP SCHEMA IF EXISTS \"PUBLIC\"");
    }

    @Test
    void dropSchema_emits_restrict_when_dialect_requires_it() {
        Dialect dialect = new AnsiDialect() {
            @Override public boolean requiresDropSchemaRestrict() { return true; }
            @Override public boolean supportsDropSchemaIfExists() { return false; }
        };
        // RESTRICT-required engines (Derby/DB2) reject IF EXISTS and CASCADE.
        String sql = dialect.ddlGenerator().dropSchema("PUBLIC", true, true);
        assertThat(sql).isEqualTo("DROP SCHEMA \"PUBLIC\" RESTRICT");
    }

    @Test
    void dropTable_strips_if_exists_when_dialect_lacks_support() {
        Dialect dialect = new AnsiDialect() {
            @Override public boolean supportsDropTableIfExists() { return false; }
        };
        String sql = dialect.ddlGenerator().dropTable(EMPLOYEES, true);
        assertThat(sql).contains("DROP TABLE")
                .doesNotContain("IF EXISTS")
                .contains("\"PUBLIC\"")
                .contains("\"EMPLOYEES\"");
    }

}
