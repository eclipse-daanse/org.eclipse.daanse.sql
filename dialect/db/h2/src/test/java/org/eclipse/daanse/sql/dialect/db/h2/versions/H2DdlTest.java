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

import java.sql.DatabaseMetaData;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.IndexColumn;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.IndexDefinition;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.SequenceDefinition;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnDefinitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.PrimaryKeyRecord;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The DDL of the H2 dialect, on every version from 1.4.200 on: a schema, tables with a named primary key, unique,
 * check and foreign key constraints added later, a descending index, a view replaced, renames of a constraint, an
 * index, a view, a column and a table, a sequence, and the drops, IF [NOT] EXISTS and CASCADE among them. The dialect
 * is built from a live connection of each version, and each statement it writes must run there and do what it says,
 * read back through the driver's metadata, which reads alike on 1.4 and 2.x.
 */
@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
class H2DdlTest {

    private static final Optional<SchemaReference> S1 = Optional.of(new SchemaReference(Optional.empty(), "S1"));
    private static final TableReference A = new TableReference(S1, "A", TableReference.TYPE_TABLE);
    private static final TableReference B = new TableReference(S1, "B", TableReference.TYPE_TABLE);
    private static final TableReference V = new TableReference(S1, "V", TableReference.TYPE_VIEW);

    @ParameterizedTest(name = "h2 {0}")
    @ValueSource(strings = { "1.4.200", "2.0.206", "2.1.214", "2.2.224", "2.3.232" })
    void ddl(String version) throws Exception {
        try (H2Versions.Session h2 = H2Versions.open(version)) {
            DdlGenerator ddl = h2.dialect().ddlGenerator();
            DatabaseMetaData md = h2.connection().getMetaData();

            // a schema, IF NOT EXISTS twice
            h2.execute(ddl.createSchema("S1", true));
            h2.execute(ddl.createSchema("S1", true));
            assertThat(names(md.getSchemas(null, "S1"), "TABLE_SCHEM")).as("the schema").containsExactly("S1");

            // tables, a named primary key, IF NOT EXISTS twice
            String createA = ddl.createTable(A, List.of(column(A, "ID", JDBCType.INTEGER, false),
                    column(A, "EMAIL", JDBCType.VARCHAR, true), column(A, "N", JDBCType.INTEGER, true)),
                    new PrimaryKeyRecord(A, List.of(new ColumnReference(Optional.of(A), "ID")), Optional.of("PK_A")),
                    true);
            h2.execute(createA);
            h2.execute(createA);
            h2.execute(ddl.createTable(B, List.of(column(B, "ID", JDBCType.INTEGER, false),
                    column(B, "AID", JDBCType.INTEGER, true)), null, false));
            assertThat(names(md.getPrimaryKeys(null, "S1", "A"), "PK_NAME")).as("the named primary key")
                    .containsExactly("PK_A");

            // constraints added later hold
            h2.execute(ddl.addUniqueConstraint(A, "UQ_EMAIL", List.of("EMAIL")));
            h2.execute(ddl.addCheckConstraint(A, "CK_N", "\"N\" > 0"));
            h2.execute(ddl.addForeignKeyConstraint(B, "FK_B_A", List.of("AID"), A, List.of("ID"), "CASCADE", null));
            h2.execute("INSERT INTO S1.A VALUES (1, 'a@x', 1), (2, 'b@x', 2)");
            h2.execute("INSERT INTO S1.B VALUES (10, 1), (11, 2), (12, NULL)");
            assertRefused(h2, "INSERT INTO S1.A VALUES (3, 'a@x', 3)", "a second email");
            assertRefused(h2, "INSERT INTO S1.A VALUES (3, 'c@x', 0)", "n not above 0");
            assertRefused(h2, "INSERT INTO S1.B VALUES (13, 99)", "of no A");
            h2.execute("DELETE FROM S1.A WHERE ID = 1");
            assertThat(h2.rows("SELECT CAST(COUNT(*) AS VARCHAR) FROM S1.B")).as("ON DELETE CASCADE")
                    .containsExactly("2");
            assertThat(names(md.getImportedKeys(null, "S1", "B"), "FK_NAME")).as("the foreign key")
                    .containsExactly("FK_B_A");

            // a constraint renamed and dropped, IF EXISTS
            h2.execute(ddl.renameConstraint(A, "CK_N", "CK_N2"));
            assertRefused(h2, ddl.dropConstraint(A, "CK_N", false), "the old name of the renamed check");
            assertRefused(h2, "INSERT INTO S1.A VALUES (4, 'd@x', 0)", "the renamed check still holds");
            h2.execute(ddl.dropConstraint(A, "CK_N2", true));
            h2.execute(ddl.dropConstraint(A, "CK_N2", true));
            h2.execute("INSERT INTO S1.A VALUES (4, 'd@x', 0)");

            // indexes: IF NOT EXISTS twice, a descending column, a rename, IF EXISTS
            h2.execute(ddl.createIndex("IX_N", A, List.of("N"), false, true));
            h2.execute(ddl.createIndex("IX_N", A, List.of("N"), false, true));
            h2.execute(ddl.createIndex(new IndexDefinition("IX_D", A,
                    List.of(IndexColumn.ascending("ID"), new IndexColumn("EMAIL", true)), false, null), false)
                    .orElseThrow());
            assertThat(indexColumns(md, "IX_D")).as("the descending column").containsExactly("ID:A", "EMAIL:D");
            h2.execute(ddl.renameIndex("IX_N", "IX_N2", A));
            assertThat(indexColumns(md, "IX_N2")).as("the renamed index").containsExactly("N:A");
            assertThat(indexColumns(md, "IX_N")).as("not under its old name").isEmpty();
            h2.execute(ddl.dropIndex("IX_N2", A, true));
            h2.execute(ddl.dropIndex("IX_N2", A, true));
            assertThat(indexColumns(md, "IX_N2")).as("dropped").isEmpty();

            // a view, replaced, renamed, dropped, IF EXISTS
            h2.execute(ddl.createView(V, "SELECT \"ID\" FROM \"S1\".\"A\"", false));
            assertRefused(h2, ddl.createView(V, "SELECT \"ID\" FROM \"S1\".\"A\"", false), "a second view");
            h2.execute(ddl.createView(V, "SELECT \"ID\", \"N\" FROM \"S1\".\"A\"", true));
            assertThat(names(md.getColumns(null, "S1", "V", null), "COLUMN_NAME")).as("replaced, its columns too")
                    .containsExactly("ID", "N");
            h2.execute(ddl.renameView(V, "V2"));
            assertThat(h2.rows("SELECT CAST(COUNT(*) AS VARCHAR) FROM S1.V2")).as("the renamed view reads")
                    .containsExactly("2");
            h2.execute(ddl.dropView(new TableReference(S1, "V2", TableReference.TYPE_VIEW), true));
            h2.execute(ddl.dropView(new TableReference(S1, "V2", TableReference.TYPE_VIEW), true));

            // a column and a table renamed
            h2.execute(ddl.renameColumn(A, "N", "QTY"));
            assertThat(names(md.getColumns(null, "S1", "A", null), "COLUMN_NAME")).as("the renamed column")
                    .containsExactly("ID", "EMAIL", "QTY");
            h2.execute(ddl.renameTable(B, "B2"));
            assertThat(h2.rows("SELECT CAST(COUNT(*) AS VARCHAR) FROM S1.B2")).as("the renamed table, its rows")
                    .containsExactly("2");

            // a sequence: start, increment, cycle; IF NOT EXISTS, IF EXISTS
            assertThat(ddl.supportsSequences()).isTrue();
            h2.execute(ddl.createSequence(new SequenceDefinition("S1", "SEQ", 10L, 5L, 10L, 20L, true, null), true)
                    .orElseThrow());
            h2.execute(ddl.createSequence(SequenceDefinition.of("S1", "SEQ"), true).orElseThrow());
            List<String> values = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                values.addAll(h2.rows("SELECT CAST(NEXT VALUE FOR S1.SEQ AS VARCHAR)"));
            }
            assertThat(values).as("from 10 by 5 to 20, then round").containsExactly("10", "15", "20", "10");
            h2.execute(ddl.dropSequence("S1", "SEQ", true).orElseThrow());
            h2.execute(ddl.dropSequence("S1", "SEQ", true).orElseThrow());
            assertRefused(h2, "SELECT NEXT VALUE FOR S1.SEQ", "the dropped sequence");

            // drops: a table with a view on it, CASCADE; IF EXISTS; the schema, CASCADE
            h2.execute(ddl.createView(V, "SELECT \"ID\" FROM \"S1\".\"A\"", false));
            h2.execute(ddl.dropTable(A, true, true));
            assertThat(names(md.getTables(null, "S1", null, null), "TABLE_NAME")).as("A and its view gone")
                    .containsExactly("B2");
            h2.execute(ddl.dropTable(A, true));
            h2.execute(ddl.dropSchema("S1", true, true));
            assertThat(names(md.getSchemas(null, "S1"), "TABLE_SCHEM")).as("the schema gone, B2 with it").isEmpty();
            h2.execute(ddl.dropSchema("S1", true, false));
        }
    }

    private static void assertRefused(H2Versions.Session h2, String sql, String what) {
        assertThatThrownBy(() -> h2.execute(sql)).as(what + ": " + sql).isInstanceOf(SQLException.class);
    }

    /** The columns of the index, in order, each with A or D. */
    private static List<String> indexColumns(DatabaseMetaData md, String index) throws SQLException {
        List<String> out = new ArrayList<>();
        try (ResultSet rs = md.getIndexInfo(null, "S1", "A", false, false)) {
            while (rs.next()) {
                if (index.equals(rs.getString("INDEX_NAME"))) {
                    out.add(rs.getShort("ORDINAL_POSITION") - 1, rs.getString("COLUMN_NAME") + ":"
                            + rs.getString("ASC_OR_DESC"));
                }
            }
        }
        return out;
    }

    private static List<String> names(ResultSet rs, String column) throws SQLException {
        List<String> out = new ArrayList<>();
        try (rs) {
            while (rs.next()) {
                out.add(rs.getString(column));
            }
        }
        return out;
    }

    private static ColumnDefinition column(TableReference table, String name, JDBCType type, boolean nullable) {
        return new ColumnDefinitionRecord(new ColumnReference(Optional.of(table), name),
                new ColumnMetaDataRecord(type, type.getName(),
                        type == JDBCType.VARCHAR ? OptionalInt.of(20) : OptionalInt.empty(), OptionalInt.empty(),
                        OptionalInt.empty(), nullable ? ColumnMetaData.Nullability.NULLABLE
                                : ColumnMetaData.Nullability.NO_NULLS,
                        OptionalInt.empty(), Optional.empty(), Optional.empty(), ColumnMetaData.AutoIncrement.UNKNOWN,
                        ColumnMetaData.GeneratedColumn.UNKNOWN));
    }
}
