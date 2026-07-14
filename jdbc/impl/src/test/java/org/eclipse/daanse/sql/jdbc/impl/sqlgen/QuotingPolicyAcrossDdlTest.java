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
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.api.IdentifierQuotingPolicy;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnDefinitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.junit.jupiter.api.Test;

class QuotingPolicyAcrossDdlTest {

    private static final SchemaReference PUBLIC = new SchemaReference(Optional.empty(), "PUBLIC");
    private static final TableReference EMP =
            new TableReference(Optional.of(PUBLIC), "EMPLOYEES", TableReference.TYPE_TABLE);
    private static final TableReference DEPT =
            new TableReference(Optional.of(PUBLIC), "DEPARTMENTS", TableReference.TYPE_TABLE);

    private static ColumnDefinition col(String name) {
        ColumnReference ref = new ColumnReference(Optional.of(EMP), name);
        ColumnMetaData meta = new ColumnMetaDataRecord(
                JDBCType.INTEGER, "INTEGER",
                OptionalInt.of(10), OptionalInt.empty(), OptionalInt.empty(),
                ColumnMetaData.Nullability.NO_NULLS,
                OptionalInt.empty(),
                Optional.empty(), Optional.empty(),
                ColumnMetaData.AutoIncrement.UNKNOWN,
                ColumnMetaData.GeneratedColumn.UNKNOWN);
        return new ColumnDefinitionRecord(ref, meta);
    }

    private static AnsiDialect dialectWith(IdentifierQuotingPolicy policy) {
        AnsiDialect d = new AnsiDialect();
        d.setQuotingPolicy(policy);
        return d;
    }

    @Test
    void createTable_respects_policy() {
        String always = dialectWith(IdentifierQuotingPolicy.ALWAYS)
                .ddlGenerator().createTable(EMP, List.of(col("ID"), col("SALARY")), null, false);
        String never  = dialectWith(IdentifierQuotingPolicy.NEVER)
                .ddlGenerator().createTable(EMP, List.of(col("ID"), col("SALARY")), null, false);

        assertThat(always).contains("\"PUBLIC\".\"EMPLOYEES\"").contains("\"ID\"").contains("\"SALARY\"");
        assertThat(never).doesNotContain("\"")
                .contains("PUBLIC.EMPLOYEES").contains("ID").contains("SALARY");
    }

    @Test
    void createIndex_respects_policy() {
        DdlGenerator dAlways = dialectWith(IdentifierQuotingPolicy.ALWAYS).ddlGenerator();
        DdlGenerator dNever  = dialectWith(IdentifierQuotingPolicy.NEVER).ddlGenerator();

        String always = dAlways.createIndex("IDX_EMP_NAME", EMP, List.of("NAME"), false, false);
        String never  = dNever.createIndex("IDX_EMP_NAME", EMP, List.of("NAME"), false, false);

        assertThat(always).contains("\"IDX_EMP_NAME\"").contains("\"EMPLOYEES\"").contains("\"NAME\"");
        assertThat(never).doesNotContain("\"")
                .contains("IDX_EMP_NAME").contains("EMPLOYEES").contains("NAME");
    }

    @Test
    void createSequence_respects_policy() {
        var seq = new DdlGenerator.SequenceDefinition(
                "PUBLIC", "ORDER_SEQ", 1L, 1L, null, null, null, null);

        String always = dialectWith(IdentifierQuotingPolicy.ALWAYS)
                .ddlGenerator().createSequence(seq, false).orElseThrow();
        String never  = dialectWith(IdentifierQuotingPolicy.NEVER)
                .ddlGenerator().createSequence(seq, false).orElseThrow();

        assertThat(always).contains("\"PUBLIC\".\"ORDER_SEQ\"");
        assertThat(never).doesNotContain("\"").contains("PUBLIC.ORDER_SEQ");
    }

    @Test
    void primaryKey_constraint_respects_policy() {
        String always = dialectWith(IdentifierQuotingPolicy.ALWAYS)
                .ddlGenerator().addPrimaryKeyConstraint(EMP, "PK_EMP", List.of("ID"));
        String never  = dialectWith(IdentifierQuotingPolicy.NEVER)
                .ddlGenerator().addPrimaryKeyConstraint(EMP, "PK_EMP", List.of("ID"));

        assertThat(always).contains("\"EMPLOYEES\"").contains("\"PK_EMP\"").contains("\"ID\"");
        assertThat(never).doesNotContain("\"")
                .contains("EMPLOYEES").contains("PK_EMP").contains("ID");
    }

    @Test
    void uniqueConstraint_respects_policy() {
        String always = dialectWith(IdentifierQuotingPolicy.ALWAYS)
                .ddlGenerator().addUniqueConstraint(EMP, "UQ_EMP_EMAIL", List.of("EMAIL"));
        String never = dialectWith(IdentifierQuotingPolicy.NEVER)
                .ddlGenerator().addUniqueConstraint(EMP, "UQ_EMP_EMAIL", List.of("EMAIL"));

        assertThat(always).contains("\"EMPLOYEES\"").contains("\"UQ_EMP_EMAIL\"").contains("\"EMAIL\"");
        assertThat(never).doesNotContain("\"")
                .contains("EMPLOYEES").contains("UQ_EMP_EMAIL").contains("EMAIL");
    }

    @Test
    void foreignKey_constraint_respects_policy() {
        String always = dialectWith(IdentifierQuotingPolicy.ALWAYS).ddlGenerator()
                .addForeignKeyConstraint(EMP, "FK_EMP_DEPT", List.of("DEPT_ID"),
                        DEPT, List.of("ID"), "NO ACTION", "NO ACTION");
        String never = dialectWith(IdentifierQuotingPolicy.NEVER).ddlGenerator()
                .addForeignKeyConstraint(EMP, "FK_EMP_DEPT", List.of("DEPT_ID"),
                        DEPT, List.of("ID"), "NO ACTION", "NO ACTION");

        assertThat(always)
                .contains("\"EMPLOYEES\"").contains("\"FK_EMP_DEPT\"")
                .contains("\"DEPT_ID\"").contains("\"DEPARTMENTS\"").contains("\"ID\"");
        assertThat(never).doesNotContain("\"")
                .contains("EMPLOYEES").contains("FK_EMP_DEPT")
                .contains("DEPT_ID").contains("DEPARTMENTS").contains("ID");
    }

    @Test
    void checkConstraint_name_and_table_respect_policy() {
        String always = dialectWith(IdentifierQuotingPolicy.ALWAYS)
                .ddlGenerator().addCheckConstraint(EMP, "CK_EMP_SALARY", "salary > 0");
        String never  = dialectWith(IdentifierQuotingPolicy.NEVER)
                .ddlGenerator().addCheckConstraint(EMP, "CK_EMP_SALARY", "salary > 0");

        assertThat(always).contains("\"EMPLOYEES\"").contains("\"CK_EMP_SALARY\"");
        assertThat(never).doesNotContain("\"")
                .contains("EMPLOYEES").contains("CK_EMP_SALARY");
    }

    @Test
    void dropConstraint_respects_policy() {
        String always = dialectWith(IdentifierQuotingPolicy.ALWAYS)
                .ddlGenerator().dropConstraint(EMP, "PK_EMP", true);
        String never  = dialectWith(IdentifierQuotingPolicy.NEVER)
                .ddlGenerator().dropConstraint(EMP, "PK_EMP", true);

        assertThat(always).contains("\"EMPLOYEES\"").contains("\"PK_EMP\"");
        assertThat(never).doesNotContain("\"").contains("EMPLOYEES").contains("PK_EMP");
    }

    @Test
    void renameTable_respects_policy() {
        String always = dialectWith(IdentifierQuotingPolicy.ALWAYS)
                .ddlGenerator().renameTable(EMP, "PERSON");
        String never  = dialectWith(IdentifierQuotingPolicy.NEVER)
                .ddlGenerator().renameTable(EMP, "PERSON");

        assertThat(always).contains("\"EMPLOYEES\"").contains("\"PERSON\"");
        assertThat(never).doesNotContain("\"").contains("EMPLOYEES").contains("PERSON");
    }

    @Test
    void renameColumn_respects_policy() {
        String always = dialectWith(IdentifierQuotingPolicy.ALWAYS)
                .ddlGenerator().renameColumn(EMP, "OLD_NAME", "NEW_NAME");
        String never  = dialectWith(IdentifierQuotingPolicy.NEVER)
                .ddlGenerator().renameColumn(EMP, "OLD_NAME", "NEW_NAME");

        assertThat(always).contains("\"OLD_NAME\"").contains("\"NEW_NAME\"");
        assertThat(never).doesNotContain("\"")
                .contains("OLD_NAME").contains("NEW_NAME");
    }

    @Test
    void alterColumnSetNullability_respects_policy() {
        String always = dialectWith(IdentifierQuotingPolicy.ALWAYS)
                .ddlGenerator().alterColumnSetNullability(EMP, "EMAIL", false);
        String never = dialectWith(IdentifierQuotingPolicy.NEVER)
                .ddlGenerator().alterColumnSetNullability(EMP, "EMAIL", false);

        assertThat(always).contains("\"EMPLOYEES\"").contains("\"EMAIL\"");
        assertThat(never).doesNotContain("\"")
                .contains("EMPLOYEES").contains("EMAIL");
    }
}
