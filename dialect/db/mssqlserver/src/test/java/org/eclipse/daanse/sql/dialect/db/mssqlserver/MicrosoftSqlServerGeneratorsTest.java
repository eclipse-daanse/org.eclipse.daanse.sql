/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.mssqlserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.daanse.sql.dialect.db.testsupport.GeneratorTestSupport.table;
import static org.eclipse.daanse.sql.dialect.db.testsupport.GeneratorTestSupport.upsertSpec;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.eclipse.daanse.sql.dialect.api.generator.KnownFunction;
import org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.StatementHint;
import org.junit.jupiter.api.Test;

/** Smoke tests for SQL Server's engine-specific overrides of new generators. */
class MicrosoftSqlServerGeneratorsTest {

    private final MicrosoftSqlServerDialect d = new MicrosoftSqlServerDialect();

    @Test
    void output_clause() {
        assertThat(d.returningGenerator().supportsReturning()).isTrue();
        assertThat(d.returningGenerator().returning(List.of("ID", "NAME")).orElseThrow())
                .isEqualTo(" OUTPUT INSERTED.\"ID\", INSERTED.\"NAME\"");
    }

    @Test
    void output_star() {
        assertThat(d.returningGenerator().returning(List.of("*")).orElseThrow()).isEqualTo(" OUTPUT INSERTED.*");
    }

    @Test
    void merge_into_using_values() {
        MergeGenerator.UpsertSpec spec = upsertSpec(table("dbo", "USERS"), "ID", "NAME");
        String sql = d.mergeGenerator().upsert(spec, List.of("1", "'foo'")).orElseThrow();
        assertThat(sql).contains("MERGE INTO \"dbo\".\"USERS\" AS T USING (VALUES (1, 'foo')) AS S")
                .contains("WHEN MATCHED THEN UPDATE SET T.\"NAME\" = S.\"NAME\"")
                .contains("WHEN NOT MATCHED THEN INSERT");
    }

    @Test
    void cte_no_recursive_keyword() {
        var ctes = List.of(new org.eclipse.daanse.sql.dialect.api.generator.CteGenerator.Cte("t", "SELECT 1"));
        assertThat(d.cteGenerator().withClause(ctes, true)).doesNotContain("RECURSIVE").contains("WITH t AS");
    }

    @Test
    void known_function_length_uses_len() {
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.LENGTH, List.of("x")))
                .hasToString("LEN(x)");
    }

    @Test
    void known_function_index_of_uses_charindex() {
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.INDEX_OF, List.of("n", "h")))
                .hasToString("CHARINDEX(n, h)");
    }

    @Test
    void known_function_concat_uses_concat_call() {
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.CONCAT, List.of("a", "b", "c")))
                .hasToString("CONCAT(a, b, c)");
    }

    @Test
    void known_function_datetime_parts_use_datepart() {
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.YEAR, List.of("x")))
                .hasToString("DATEPART(year, x)");
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.MONTH, List.of("x")))
                .hasToString("DATEPART(month, x)");
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.DAY, List.of("x")))
                .hasToString("DATEPART(day, x)");
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.HOUR, List.of("x")))
                .hasToString("DATEPART(hour, x)");
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.MINUTE, List.of("x")))
                .hasToString("DATEPART(minute, x)");
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.SECOND, List.of("x")))
                .hasToString("DATEPART(second, x)");
    }

    @Test
    void known_function_now_uses_getdate() {
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.NOW, List.of()))
                .hasToString("GETDATE()");
    }

    @Test
    void known_function_ceiling_and_defaults_delegate() {
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.CEILING, List.of("x")))
                .hasToString("CEILING(x)");
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.ABS, List.of("x")))
                .hasToString("ABS(x)");
    }

    @Test
    void statement_option_trailing_clause() {
        assertThat(d.hintGenerator().statementOption(List.of(new StatementHint("RECOMPILE", List.of()),
                new StatementHint("MAXDOP", List.of("2")))))
                .hasToString(" OPTION (RECOMPILE, MAXDOP 2)");
    }

    @Test
    void statement_option_multi_argument_hint() {
        assertThat(d.hintGenerator().statementOption(
                List.of(new StatementHint("USE HINT", List.of("'DISABLE_OPTIMIZER_ROWGOAL'", "'RECOMPILE'")))))
                .hasToString(" OPTION (USE HINT 'DISABLE_OPTIMIZER_ROWGOAL' 'RECOMPILE')");
    }

    @Test
    void statement_option_empty_list_emits_nothing() {
        assertThat(d.hintGenerator().statementOption(List.of())).isEmpty();
    }

    @Test
    void select_hint_stays_default_empty() {
        assertThat(d.hintGenerator().selectHint(List.of(new StatementHint("MAXDOP", List.of("2"))))).isEmpty();
    }

    @Test
    void known_function_override_guards_arity() {
        assertThatThrownBy(() -> d.functionGenerator().generateKnownFunction(KnownFunction.LENGTH, List.of("x", "y")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("LENGTH");
        assertThatThrownBy(() -> d.functionGenerator().generateKnownFunction(KnownFunction.NOW, List.of("x")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("NOW");
    }
}
