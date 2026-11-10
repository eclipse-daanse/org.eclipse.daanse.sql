/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.sql.dialect.db.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.sql.dialect.api.IdentifierCaseFolding;
import org.eclipse.daanse.sql.dialect.api.IdentifierQuotingPolicy;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IdentifierQuotingPolicyTest {

    /** A dialect we can swap the case-folding rule on per test. */
    static class FoldingDialect extends AbstractJdbcDialect {
        private final IdentifierCaseFolding folding;

        FoldingDialect(IdentifierCaseFolding folding) {
            super(org.eclipse.daanse.sql.dialect.api.DialectInitData.ansiDefaults());
            this.folding = folding;
        }

        @Override
        public IdentifierCaseFolding caseFolding() {
            return folding;
        }

        @Override
        public boolean needsExponent(Object value, String valueString) {
            return false;
        }

        @Override
        public String name() {
            return "test";
        }
    }

    @Nested
    class AlwaysPolicyKeepsHistoricalBehaviour {
        FoldingDialect d = new FoldingDialect(IdentifierCaseFolding.UPPER);

        @Test
        void identifierIsAlwaysQuoted() {
            assertThat(d.quoteIdentifier("EMPLOYEES").toString()).isEqualTo("\"EMPLOYEES\"");
        }

        @Test
        void mixedCaseIsAlwaysQuoted() {
            assertThat(d.quoteIdentifier("firstName").toString()).isEqualTo("\"firstName\"");
        }

        @Test
        void reservedWordIsAlwaysQuoted() {
            assertThat(d.quoteIdentifier("ORDER").toString()).isEqualTo("\"ORDER\"");
        }
    }

    @Nested
    class WhenNeededPolicyOnUpperFoldingDialect {
        FoldingDialect d;

        WhenNeededPolicyOnUpperFoldingDialect() {
            d = new FoldingDialect(IdentifierCaseFolding.UPPER);
            d.setQuotingPolicy(IdentifierQuotingPolicy.WHEN_NEEDED);
        }

        @Test
        void canonicalUpperCaseIsEmittedUnquoted() {
            assertThat(d.quoteIdentifier("EMPLOYEES").toString()).isEqualTo("EMPLOYEES");
        }

        @Test
        void mixedCaseRequiresQuoting() {
            assertThat(d.quoteIdentifier("firstName").toString()).isEqualTo("\"firstName\"");
        }

        @Test
        void reservedWordRequiresQuoting() {
            // ORDER is canonical UPPER but it is reserved → must be quoted
            assertThat(d.quoteIdentifier("ORDER").toString()).isEqualTo("\"ORDER\"");
        }

        @Test
        void nonTrivialCharsRequireQuoting() {
            assertThat(d.quoteIdentifier("with-dash").toString()).isEqualTo("\"with-dash\"");
        }

        @Test
        void emptyNameQuoted() {
            assertThat(d.quoteIdentifier("").toString()).isEqualTo("\"\"");
        }
    }

    @Nested
    class WhenNeededPolicyOnLowerFoldingDialect {
        FoldingDialect d;

        WhenNeededPolicyOnLowerFoldingDialect() {
            d = new FoldingDialect(IdentifierCaseFolding.LOWER);
            d.setQuotingPolicy(IdentifierQuotingPolicy.WHEN_NEEDED);
        }

        @Test
        void canonicalLowerCaseIsEmittedUnquoted() {
            assertThat(d.quoteIdentifier("employees").toString()).isEqualTo("employees");
        }

        @Test
        void upperCaseRequiresQuoting() {
            // 'EMPLOYEES' is non-canonical for a LOWER-folding dialect
            assertThat(d.quoteIdentifier("EMPLOYEES").toString()).isEqualTo("\"EMPLOYEES\"");
        }
    }

    @Nested
    class WhenNeededPolicyOnPreserveFoldingDialect {
        FoldingDialect d;

        WhenNeededPolicyOnPreserveFoldingDialect() {
            d = new FoldingDialect(IdentifierCaseFolding.PRESERVE);
            d.setQuotingPolicy(IdentifierQuotingPolicy.WHEN_NEEDED);
        }

        @Test
        void mixedCaseIsAlwaysSafeUnquotedOnPreserveDialect() {
            assertThat(d.quoteIdentifier("firstName").toString()).isEqualTo("firstName");
        }

        @Test
        void reservedWordStillRequiresQuoting() {
            assertThat(d.quoteIdentifier("ORDER").toString()).isEqualTo("\"ORDER\"");
        }
    }

    @Nested
    class NeverPolicyEmitsVerbatim {
        FoldingDialect d;

        NeverPolicyEmitsVerbatim() {
            d = new FoldingDialect(IdentifierCaseFolding.UPPER);
            d.setQuotingPolicy(IdentifierQuotingPolicy.NEVER);
        }

        @Test
        void emitsCanonicalNameUnquoted() {
            assertThat(d.quoteIdentifier("EMPLOYEES").toString()).isEqualTo("EMPLOYEES");
        }

        @Test
        void emitsMixedCaseVerbatim_consumerResponsibilityToMatchCatalog() {
            assertThat(d.quoteIdentifier("firstName").toString()).isEqualTo("firstName");
        }

        @Test
        void emitsReservedWordVerbatim_consumerResponsibilityToAvoid() {
            assertThat(d.quoteIdentifier("ORDER").toString()).isEqualTo("ORDER");
        }
    }

    @Nested
    class PerCallOverrideDoesNotMutateDialect {
        FoldingDialect d = new FoldingDialect(IdentifierCaseFolding.UPPER);

        @Test
        void alwaysPolicyOnDialect_overrideToWhenNeeded_yieldsUnquoted() {
            // dialect default is ALWAYS — would normally quote
            assertThat(d.quoteIdentifier("EMPLOYEES").toString()).isEqualTo("\"EMPLOYEES\"");

            // per-call override does not change the dialect's policy
            assertThat(d.quoteIdentifierWith("EMPLOYEES", IdentifierQuotingPolicy.WHEN_NEEDED).toString())
                    .isEqualTo("EMPLOYEES");

            // dialect default still ALWAYS — next normal call still quotes
            assertThat(d.quoteIdentifier("EMPLOYEES").toString()).isEqualTo("\"EMPLOYEES\"");
            assertThat(d.quotingPolicy()).isEqualTo(IdentifierQuotingPolicy.ALWAYS);
        }

        @Test
        void perCallNeverEmitsVerbatim() {
            assertThat(d.quoteIdentifierWith("firstName", IdentifierQuotingPolicy.NEVER).toString())
                    .isEqualTo("firstName");
        }

        @Test
        void perCallAlwaysOnANeverPolicyDialect_quotes() {
            d.setQuotingPolicy(IdentifierQuotingPolicy.NEVER);
            assertThat(d.quoteIdentifier("EMPLOYEES").toString()).isEqualTo("EMPLOYEES");
            assertThat(d.quoteIdentifierWith("EMPLOYEES", IdentifierQuotingPolicy.ALWAYS).toString())
                    .isEqualTo("\"EMPLOYEES\"");
            // dialect-level state preserved
            assertThat(d.quotingPolicy()).isEqualTo(IdentifierQuotingPolicy.NEVER);
        }

        @Test
        void nullPolicyTreatedAsAlways() {
            assertThat(d.quoteIdentifierWith("EMPLOYEES", null).toString()).isEqualTo("\"EMPLOYEES\"");
        }
    }

    @Nested
    class CaseFoldingHelper {
        @Test
        void upperFoldsToUpper() {
            assertThat(IdentifierCaseFolding.UPPER.fold("Foo")).isEqualTo("FOO");
            assertThat(IdentifierCaseFolding.UPPER.isCanonical("FOO")).isTrue();
            assertThat(IdentifierCaseFolding.UPPER.isCanonical("Foo")).isFalse();
        }

        @Test
        void lowerFoldsToLower() {
            assertThat(IdentifierCaseFolding.LOWER.fold("Foo")).isEqualTo("foo");
            assertThat(IdentifierCaseFolding.LOWER.isCanonical("foo")).isTrue();
            assertThat(IdentifierCaseFolding.LOWER.isCanonical("Foo")).isFalse();
        }

        @Test
        void preservePreserves() {
            assertThat(IdentifierCaseFolding.PRESERVE.fold("Foo")).isEqualTo("Foo");
            assertThat(IdentifierCaseFolding.PRESERVE.isCanonical("Foo")).isTrue();
            assertThat(IdentifierCaseFolding.PRESERVE.isCanonical("FOO")).isTrue();
        }

        @Test
        void nullSafe() {
            assertThat(IdentifierCaseFolding.UPPER.fold(null)).isNull();
            assertThat(IdentifierCaseFolding.UPPER.isCanonical(null)).isFalse();
        }
    }
}
