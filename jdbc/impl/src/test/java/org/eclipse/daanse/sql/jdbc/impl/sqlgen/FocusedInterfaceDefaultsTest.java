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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.api.generator.AggregationGenerator;
import org.eclipse.daanse.sql.model.sql.BitOperation;
import org.eclipse.daanse.sql.dialect.api.generator.FunctionGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.HintGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.OrderByGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.RegexGenerator;
import org.eclipse.daanse.sql.model.type.Datatype;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FocusedInterfaceDefaultsTest {

    private final Dialect dialect = new AnsiDialect();

    // -------------------- Dialect identity --------------------

    @Test
    void dialect_identity_name_is_ansi() {
        assertThat(dialect.name()).isEqualTo("ansi");
    }

    // -------------------- accessors return narrowed views --------------------

    @Test
    void accessors_return_narrowed_views() {
        assertThat((Object) dialect.sqlGenerator()).isSameAs(dialect);
        assertThat((Object) dialect.ddlGenerator()).isSameAs(dialect);
        assertThat((Object) dialect.orderByGenerator()).isSameAs(dialect);
        assertThat((Object) dialect.regexGenerator()).isSameAs(dialect);
        assertThat((Object) dialect.aggregationGenerator()).isSameAs(dialect);
        assertThat((Object) dialect.functionGenerator()).isSameAs(dialect);
        assertThat((Object) dialect.hintGenerator()).isSameAs(dialect);
    }

    // -------------------- OrderByGenerator --------------------

    @Nested
    class OrderBy {
        private final OrderByGenerator g = dialect.orderByGenerator();

        @Test
        void non_nullable_asc() {
            assertThat(g.generateOrderItem("col", false, true, false).toString()).isEqualTo("col ASC");
        }

        @Test
        void non_nullable_desc() {
            assertThat(g.generateOrderItem("col", false, false, false).toString()).isEqualTo("col DESC");
        }

        @Test
        void nullable_asc_nulls_last_uses_case_when_default() {
            assertThat(g.generateOrderItem("col", true, true, true).toString())
                    .isEqualTo("CASE WHEN col IS NULL THEN 1 ELSE 0 END, col ASC");
        }

        @Test
        void nullable_desc_nulls_first_uses_case_when_default() {
            assertThat(g.generateOrderItem("col", true, false, false).toString())
                    .isEqualTo("CASE WHEN col IS NULL THEN 0 ELSE 1 END, col DESC");
        }

        @Test
        void ansi_form_emits_nulls_last() {
            assertThat(g.generateOrderByNullsAnsi("col", true, true).toString())
                    .isEqualTo("col ASC NULLS LAST");
        }

        @Test
        void ansi_form_emits_nulls_first() {
            assertThat(g.generateOrderByNullsAnsi("col", false, false).toString())
                    .isEqualTo("col DESC NULLS FIRST");
        }

        @Test
        void order_value_quotes_via_dialect_literal_quoter() {
            // VARCHAR sentinel — quoted with single-quote literal form.
            String sql = g.generateOrderItemForOrderValue("col", "X", Datatype.VARCHAR, true, true).toString();
            assertThat(sql).isEqualTo("CASE WHEN col = 'X' THEN 1 ELSE 0 END, col ASC");
        }
    }

    // -------------------- RegexGenerator --------------------

    @Nested
    class Regex {
        private final RegexGenerator g = dialect.regexGenerator();

        @Test
        void default_returns_empty_when_unsupported() {
            assertThat(g.generateRegularExpression("c", "[a-z]+")).isEmpty();
        }

        @Test
        void embedded_flags_extracted_and_translated() {
            StringBuilder dialectFlags = new StringBuilder();
            String stripped = g.extractEmbeddedFlags("(?is).*Hello.*",
                    new String[][] { { "i", "i" }, { "s", "n" } }, dialectFlags);
            assertThat(stripped).isEqualTo(".*Hello.*");
            assertThat(dialectFlags).hasToString("in");
        }

        @Test
        void no_flags_means_no_change() {
            StringBuilder dialectFlags = new StringBuilder();
            String stripped = g.extractEmbeddedFlags(".*Hello.*",
                    new String[][] { { "i", "i" } }, dialectFlags);
            assertThat(stripped).isEqualTo(".*Hello.*");
            assertThat(dialectFlags).isEmpty();
        }
    }

    // -------------------- FunctionGenerator --------------------

    @Nested
    class Functions {
        private final FunctionGenerator g = dialect.functionGenerator();

        @Test
        void upper_wraps_in_ansi_form() {
            assertThat(g.wrapIntoSqlUpperCaseFunction("col").toString()).isEqualTo("UPPER(col)");
        }

        @Test
        void if_then_else_uses_case_when_in_ansi_form() {
            assertThat(g.wrapIntoSqlIfThenElseFunction("a > b", "a", "b").toString())
                    .isEqualTo("CASE WHEN a > b THEN a ELSE b END");
        }

    }

    // -------------------- AggregationGenerator --------------------

    @Nested
    class Aggregation {
        private final AggregationGenerator g = dialect.aggregationGenerator();

        @Test
        void list_agg_is_unsupported_by_default() {
            assertThat(g.supportsListAgg()).isFalse();
            assertThat(g.generateListAgg("c", false, ",", null, null, null)).isEmpty();
        }

        @Test
        void nth_value_is_unsupported_by_default() {
            assertThat(g.supportsNthValue()).isFalse();
            assertThat(g.supportsNthValueIgnoreNulls()).isFalse();
            assertThat(g.generateNthValueAgg("c", false, 1, null)).isEmpty();
        }

        @Test
        void percentile_is_unsupported_by_default() {
            assertThat(g.supportsPercentileDisc()).isFalse();
            assertThat(g.supportsPercentileCont()).isFalse();
            assertThat(g.generatePercentileDisc(0.5, false, null, "c")).isEmpty();
            assertThat(g.generatePercentileCont(0.5, false, null, "c")).isEmpty();
        }

        @Test
        void bit_aggregation_is_unsupported_by_default() {
            assertThat(g.supportsBitAggregation(BitOperation.AND)).isFalse();
            assertThat(g.generateBitAggregation(BitOperation.AND, "c")).isEmpty();
        }

        @Test
        void buildPercentileFunction_emits_within_group_form() {
            String sql = g.buildPercentileFunction("PERCENTILE_DISC", 0.5, false, null, "salary").toString();
            assertThat(sql).isEqualTo("PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY \"salary\")");
        }

        @Test
        void buildPercentileFunction_with_table_qualifies() {
            String sql = g.buildPercentileFunction("PERCENTILE_CONT", 0.9, true, "emp", "salary").toString();
            assertThat(sql).isEqualTo(
                    "PERCENTILE_CONT(0.9) WITHIN GROUP (ORDER BY \"emp\".\"salary\" DESC)");
        }
    }

    // -------------------- HintGenerator --------------------

    @Nested
    class Hints {
        private final HintGenerator g = dialect.hintGenerator();

        @Test
        void default_is_noop() {
            StringBuilder buf = new StringBuilder("SELECT * FROM t");
            g.appendHintsAfterFromClause(buf, Map.of("hint", "value"));
            assertThat(buf).hasToString("SELECT * FROM t");
        }
    }

    // -------------------- Datatype polish --------------------

    @Nested
    class DatatypePredicates {
        @Test
        void isNumeric_isText_isTemporal_partition_cleanly() {
            for (Datatype t : Datatype.values()) {
                int trues = (t.isNumeric() ? 1 : 0) + (t.isText() ? 1 : 0) + (t.isTemporal() ? 1 : 0)
                        + (t.isBinary() ? 1 : 0) + (t.isComposite() ? 1 : 0);
                // Boolean has no bucket; everything else must belong to exactly one.
                if (t == Datatype.BOOLEAN) {
                    assertThat(trues).isZero();
                } else {
                    assertThat(trues).as("type %s should belong to exactly one bucket", t).isEqualTo(1);
                }
            }
        }

        @Test
        void fromValueOrNull_resolves_sql99_aliases() {
            assertThat(Datatype.fromValueOrNull("CHARACTER VARYING")).isEqualTo(Datatype.VARCHAR);
            assertThat(Datatype.fromValueOrNull("DOUBLE PRECISION")).isEqualTo(Datatype.DOUBLE);
            assertThat(Datatype.fromValueOrNull("INT")).isEqualTo(Datatype.INTEGER);
            assertThat(Datatype.fromValueOrNull("nothingmatches")).isNull();
            assertThat(Datatype.fromValueOrNull(null)).isNull();
        }
    }
}
