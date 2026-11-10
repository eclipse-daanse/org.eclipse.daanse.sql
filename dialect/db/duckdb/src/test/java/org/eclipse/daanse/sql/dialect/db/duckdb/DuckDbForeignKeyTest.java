/*
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/
package org.eclipse.daanse.sql.dialect.db.duckdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.JDBCType;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnDefinitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.PrimaryKeyRecord;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.api.Test;

/** DuckDB cannot add a foreign key to a table; it declares it in {@code CREATE TABLE}. */
class DuckDbForeignKeyTest {

    private static final Optional<SchemaReference> MAIN = Optional.of(new SchemaReference(Optional.empty(), "main"));
    private static final TableReference CUSTOMER = new TableReference(MAIN, "CUSTOMER");
    private static final TableReference ORDERS = new TableReference(MAIN, "ORDERS");

    private final DdlGenerator ddl = new DuckDbDialect().ddlGenerator();

    @Test
    void aForeignKeyCannotBeAddedLater() {
        assertThat(ddl.supportsAddForeignKeyConstraint()).isFalse();
    }

    @Test
    void aForeignKeyIsAClauseOfCreateTable() {
        String fk = ddl.foreignKeyClause("FK_ORDERS_CUSTOMER", List.of("CUSTOMER_ID"), CUSTOMER, List.of("ID"),
                "NO ACTION", "NO ACTION");
        String create = ddl.createTable(ORDERS, List.of(column("ID"), column("CUSTOMER_ID")),
                new PrimaryKeyRecord(ORDERS, List.of(new ColumnReference(Optional.of(ORDERS), "ID")),
                        Optional.empty()),
                List.of(fk), false);

        assertThat(fk).startsWith("CONSTRAINT \"FK_ORDERS_CUSTOMER\" FOREIGN KEY (\"CUSTOMER_ID\") REFERENCES ")
                .contains("\"CUSTOMER\"").contains("(\"ID\")");
        assertThat(create).startsWith("CREATE TABLE ").contains("PRIMARY KEY (\"ID\")")
                .contains(",\n  " + fk);
        assertThat(create.indexOf(fk)).isGreaterThan(create.indexOf("PRIMARY KEY"));
    }

    @Test
    void aForeignKeyWritesOnlyTheActionsDuckDbKnows() {
        String cascade = ddl.foreignKeyClause("FK", List.of("CUSTOMER_ID"), CUSTOMER, List.of("ID"), "CASCADE",
                "SET NULL");
        String restrict = ddl.foreignKeyClause("FK", List.of("CUSTOMER_ID"), CUSTOMER, List.of("ID"), "restrict",
                "NO ACTION");

        assertThat(cascade).doesNotContain(" ON DELETE").doesNotContain(" ON UPDATE");
        assertThat(restrict).endsWith(" ON DELETE RESTRICT ON UPDATE NO ACTION");
    }

    private static ColumnDefinitionRecord column(String name) {
        return new ColumnDefinitionRecord(new ColumnReference(Optional.of(ORDERS), name),
                new ColumnMetaDataRecord(JDBCType.BIGINT, "BIGINT", OptionalInt.empty(), OptionalInt.empty(),
                        OptionalInt.empty(), ColumnMetaData.Nullability.NULLABLE, OptionalInt.empty(),
                        Optional.empty(), Optional.empty(), ColumnMetaData.AutoIncrement.UNKNOWN,
                        ColumnMetaData.GeneratedColumn.UNKNOWN));
    }
}
