/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.daanse.sql.dialect.db.testsupport.GeneratorTestSupport.table;
import static org.eclipse.daanse.sql.dialect.db.testsupport.GeneratorTestSupport.upsertSpec;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.OptionalLong;

import org.eclipse.daanse.sql.dialect.api.generator.KnownFunction;
import org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.StatementHint;
import org.junit.jupiter.api.Test;

/** Smoke tests for MySQL's engine-specific overrides of new generators. */
class MySqlGeneratorsTest {

    private final MySqlDialect d = new MySqlDialect();

    @Test
    void pagination_offset_limit() {
        assertThat(d.paginationGenerator().paginate(OptionalLong.of(20), OptionalLong.of(40)))
                .isEqualTo(" LIMIT 40, 20");
    }

    @Test
    void pagination_limit_only() {
        assertThat(d.paginationGenerator().paginate(OptionalLong.of(20), OptionalLong.empty())).isEqualTo(" LIMIT 20");
    }

    @Test
    void pagination_offset_only_uses_huge_limit_trick() {
        assertThat(d.paginationGenerator().paginate(OptionalLong.empty(), OptionalLong.of(40)))
                .isEqualTo(" LIMIT 40, 18446744073709551615");
    }

    @Test
    void upsert_on_duplicate_key_update() {
        MergeGenerator.UpsertSpec spec = upsertSpec(table("test", "USERS"), "ID", "NAME");
        String sql = d.mergeGenerator().upsert(spec, List.of("1", "'foo'")).orElseThrow();
        assertThat(sql).contains("INSERT INTO `test`.`USERS`")
                .contains("ON DUPLICATE KEY UPDATE `NAME` = VALUES(`NAME`)");
    }

    @Test
    void known_function_concat_uses_concat_call() {
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.CONCAT, List.of("a", "b", "c")))
                .hasToString("CONCAT(a, b, c)");
    }

    @Test
    void known_function_index_of_uses_locate() {
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.INDEX_OF, List.of("n", "h")))
                .hasToString("LOCATE(n, h)");
    }

    @Test
    void known_function_length_stays_char_length() {
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.LENGTH, List.of("x")))
                .hasToString("CHAR_LENGTH(x)");
    }

    @Test
    void known_function_delegates_default_spellings() {
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.YEAR, List.of("x")))
                .hasToString("EXTRACT(YEAR FROM x)");
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.NOW, List.of()))
                .hasToString("CURRENT_TIMESTAMP");
    }

    @Test
    void select_hint_block_after_select_keyword() {
        assertThat(d.hintGenerator().selectHint(List.of(new StatementHint("MAX_EXECUTION_TIME", List.of("1000")),
                new StatementHint("NO_INDEX_MERGE", List.of()))))
                .hasToString("/*+ MAX_EXECUTION_TIME(1000) NO_INDEX_MERGE */ ");
    }

    @Test
    void select_hint_multi_argument_hint() {
        assertThat(d.hintGenerator().selectHint(
                List.of(new StatementHint("BKA", List.of("t1", "t2")))))
                .hasToString("/*+ BKA(t1, t2) */ ");
    }

    @Test
    void select_hint_strips_comment_terminator() {
        assertThat(d.hintGenerator().selectHint(
                List.of(new StatementHint("EVIL*/", List.of("a*/rg")))))
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
        assertThatThrownBy(() -> d.functionGenerator().generateKnownFunction(KnownFunction.CONCAT, List.of("a")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CONCAT");
        assertThatThrownBy(() -> d.functionGenerator().generateKnownFunction(KnownFunction.INDEX_OF, List.of("n")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("INDEX_OF");
    }
}
