/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.daanse.sql.dialect.db.testsupport.GeneratorTestSupport.table;
import static org.eclipse.daanse.sql.dialect.db.testsupport.GeneratorTestSupport.upsertSpec;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.eclipse.daanse.sql.dialect.api.generator.KnownFunction;
import org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.StatementHint;
import org.junit.jupiter.api.Test;

/** Smoke tests for Oracle's engine-specific overrides of new generators. */
class OracleGeneratorsTest {

    private final OracleDialect d = new OracleDialect();

    @Test
    void merge_into_using_dual() {
        MergeGenerator.UpsertSpec spec = upsertSpec(table("RT", "USERS"), "ID", "NAME");
        String sql = d.mergeGenerator().upsert(spec, List.of("1", "'foo'")).orElseThrow();
        assertThat(sql)
                .contains("MERGE INTO \"RT\".\"USERS\" T USING (SELECT 1 AS \"ID\", 'foo' AS \"NAME\" FROM dual) S")
                .contains("ON (T.\"ID\" = S.\"ID\")").contains("WHEN MATCHED THEN UPDATE SET T.\"NAME\" = S.\"NAME\"")
                .contains("WHEN NOT MATCHED THEN INSERT");
    }

    @Test
    void known_function_substring_uses_substr() {
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.SUBSTRING, List.of("x", "2")))
                .hasToString("SUBSTR(x, 2)");
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.SUBSTRING, List.of("x", "2", "3")))
                .hasToString("SUBSTR(x, 2, 3)");
    }

    @Test
    void known_function_index_of_uses_instr_with_swapped_arguments() {
        // INDEX_OF(needle, haystack) but Oracle INSTR(haystack, needle).
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.INDEX_OF, List.of("n", "h")))
                .hasToString("INSTR(h, n)");
    }

    @Test
    void known_function_delegates_default_spellings() {
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.TRIM, List.of("x")))
                .hasToString("TRIM(x)");
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.MOD, List.of("a", "b")))
                .hasToString("MOD(a, b)");
    }

    @Test
    void select_hint_block_after_select_keyword() {
        assertThat(d.hintGenerator().selectHint(List.of(new StatementHint("FIRST_ROWS", List.of("10")),
                new StatementHint("PARALLEL", List.of("t", "4")))))
                .hasToString("/*+ FIRST_ROWS(10) PARALLEL(t, 4) */ ");
    }

    @Test
    void select_hint_bare_name_and_sanitizing() {
        assertThat(d.hintGenerator().selectHint(List.of(new StatementHint("ALL_ROWS", List.of()))))
                .hasToString("/*+ ALL_ROWS */ ");
        assertThat(d.hintGenerator().selectHint(List.of(new StatementHint("EVIL*/", List.of("a*/rg")))))
                .hasToString("/*+ EVIL(arg) */ ");
    }

    @Test
    void select_hint_empty_list_emits_nothing() {
        assertThat(d.hintGenerator().selectHint(List.of())).isEmpty();
    }

    @Test
    void statement_option_stays_default_empty() {
        assertThat(d.hintGenerator().statementOption(List.of(new StatementHint("RECOMPILE", List.of())))).isEmpty();
    }

    @Test
    void known_function_override_guards_arity() {
        assertThatThrownBy(() -> d.functionGenerator().generateKnownFunction(KnownFunction.SUBSTRING, List.of("x")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SUBSTRING");
        assertThatThrownBy(
                () -> d.functionGenerator().generateKnownFunction(KnownFunction.INDEX_OF, List.of("n", "h", "z")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("INDEX_OF");
    }
}
