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
package org.eclipse.daanse.sql.statement.compare;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.eclipse.daanse.sql.statement.api.model.FromClause;
import org.eclipse.daanse.sql.statement.api.model.GroupBy;
import org.eclipse.daanse.sql.statement.api.model.OrderKey;
import org.eclipse.daanse.sql.statement.api.model.Projection;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;

/**
 * Structural equivalence of two {@link SelectStatement} trees, up to the differences that do NOT
 * change the rows a query returns. This is the relaxation a byte-equal comparison cannot offer:
 * a byte-level gate rejects <em>any</em> whitespace difference, including ones where statement B
 * is merely <em>cosmetically</em> different from (or better than) statement A — so correct paths
 * stall on clause-order / comma-vs-ANSI-join noise. This comparator treats those as equivalent
 * while still catching a genuinely different query.
 *
 * <p><b>Why it renders fragments rather than comparing AST nodes.</b> One statement may be
 * <em>Raw-string</em> based (built from pre-rendered SQL fragments) while the other is
 * <em>structured</em> ({@code Column}, {@code Comparison}, …). Their trees never match even when
 * they render identically, so each comparable fragment is rendered to its dialect SQL
 * (whitespace-normalised) and the SQL strings are compared. That makes
 * {@code Raw("`c`.`x` = 1")} ≡ {@code Comparison(Column, EQ, Literal)}.
 *
 * <p><b>Defined equivalences</b> (the benign classes):
 * <ul>
 *   <li><b>WHERE / JOIN conjunct order</b> — the top-level {@code WHERE} predicates and every
 *       {@code FromJoin} {@code ON} are rendered and compared as a <em>set</em>, so order is
 *       irrelevant and a comma-product join-in-{@code WHERE} ≡ an ANSI join-in-{@code ON}.</li>
 *   <li><b>table-filter placement</b> — a {@link FromClause.FromTable}'s own filter joins the set.</li>
 * </ul>
 *
 * <p><b>Structural</b> (NOT equivalent): a table present on one side only (over-join / missing
 * non-empty fact join); a conjunct present on one side only (dropped/extra restriction);
 * {@code UPPER(x)=UPPER(y)} vs {@code x=y} (different rendered fragments); any projection /
 * {@code GROUP BY} / {@code ORDER BY} difference.
 *
 * <p><b>Out of scope</b> (conservatively NOT equivalent): a redundant all-member {@code IN} that
 * one side drops as a no-op — proving it redundant needs the level's member count, not in the
 * tree; and a query where the two sides group the {@code WHERE} conjuncts into different
 * parenthesised {@code AND} groups (rendered as different fragments) — conservatively treated as
 * structural.
 */
public final class StatementEquivalence {

    private StatementEquivalence() {
    }

    /** A named, human-readable first structural difference (empty {@link #describe} ⇒ equivalent). */
    public record Diff(String reason) {
        @Override
        public String toString() {
            return reason;
        }
    }

    /** True when {@code a} and {@code b} return the same rows up to the defined equivalences. */
    public static boolean equivalent(SelectStatement a, SelectStatement b, Dialect dialect) {
        return describeAll(a, b, dialect).isEmpty();
    }

    /** The first structural difference between {@code a} and {@code b}, or empty when equivalent. */
    public static Optional<Diff> describe(SelectStatement a, SelectStatement b, Dialect dialect) {
        List<Diff> all = describeAll(a, b, dialect);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    /**
     * ALL structural differences between {@code a} and {@code b} (empty ⇒ equivalent). One entry per
     * differing clause — a first-diff-only report misattributes multi-clause divergences (a duplicated
     * FROM table used to surface as a spurious ORDER-BY diff), so verify-mode logging reports every
     * clause that differs.
     */
    public static List<Diff> describeAll(SelectStatement a, SelectStatement b, Dialect dialect) {
        List<Diff> out = new ArrayList<>();
        if (a.distinct() != b.distinct()) {
            out.add(new Diff("DISTINCT: " + a.distinct() + " vs " + b.distinct()));
        }
        List<String> pa = projections(a, dialect);
        List<String> pb = projections(b, dialect);
        if (!pa.equals(pb)) {
            out.add(new Diff("projections: " + pa + " vs " + pb));
        }
        // FROM tables are a MULTISET: a duplicate table (`from store, store`) is a real structural
        // difference a set-compare hides (it produces a cross-product of the table with itself).
        List<String> ta = new ArrayList<>();
        List<String> tb = new ArrayList<>();
        Set<String> ca = new HashSet<>();
        Set<String> cb = new HashSet<>();
        a.from().ifPresent(f -> collect(f, ta, ca, dialect));
        b.from().ifPresent(f -> collect(f, tb, cb, dialect));
        for (Predicate p : a.filters()) {
            ca.add(render(p, dialect));
        }
        for (Predicate p : b.filters()) {
            cb.add(render(p, dialect));
        }
        List<String> sta = new ArrayList<>(ta);
        List<String> stb = new ArrayList<>(tb);
        java.util.Collections.sort(sta);
        java.util.Collections.sort(stb);
        if (!sta.equals(stb)) {
            out.add(new Diff("FROM tables: " + symmetricMultiset(ta, tb)));
        }
        // WHERE/HAVING conjuncts and GROUP keys compare as SETS: AND is idempotent and group keys
        // dedupe, so a duplicate conjunct/key is result-neutral (unlike a duplicate FROM table).
        if (!ca.equals(cb)) {
            out.add(new Diff("WHERE/JOIN conjuncts: " + symmetric(ca, cb)));
        }
        Set<String> ga = groupKeys(a, dialect);
        Set<String> gb = groupKeys(b, dialect);
        if (!ga.equals(gb)) {
            out.add(new Diff("GROUP BY: " + symmetric(ga, gb)));
        }
        Set<String> ha = new HashSet<>();
        Set<String> hb = new HashSet<>();
        for (Predicate p : a.having()) {
            ha.add(render(p, dialect));
        }
        for (Predicate p : b.having()) {
            hb.add(render(p, dialect));
        }
        if (!ha.equals(hb)) {
            out.add(new Diff("HAVING: " + symmetric(ha, hb)));
        }
        List<String> oa = orderKeys(a, dialect);
        List<String> ob = orderKeys(b, dialect);
        if (!oa.equals(ob)) {
            out.add(new Diff("ORDER BY: " + oa + " vs " + ob));
        }
        if (!a.rowLimit().equals(b.rowLimit())) {
            out.add(new Diff("rowLimit differ"));
        }
        return out;
    }

    private static String render(Predicate p, Dialect dialect) {
        return SqlTextNormalizer.normalizeWhitespace(new DialectSqlRenderer(dialect).renderPredicate(p));
    }

    private static String render(SqlExpression e, Dialect dialect) {
        return SqlTextNormalizer.normalizeWhitespace(new DialectSqlRenderer(dialect).renderExpression(e));
    }

    /** Ordered, rendered SELECT-list expressions (projection ORDER is referenced by ordinals). */
    private static List<String> projections(SelectStatement s, Dialect dialect) {
        List<String> out = new ArrayList<>();
        for (Projection p : s.projections()) {
            out.add(render(p.expression(), dialect));
        }
        return out;
    }

    /** The GROUP BY keys as a rendered set (group order is not row-significant). */
    private static Set<String> groupKeys(SelectStatement s, Dialect dialect) {
        Set<String> out = new HashSet<>();
        for (GroupBy.GroupKey k : s.groupBy().keys()) {
            SqlExpression e;
            if (k instanceof GroupBy.GroupKey.Ref r) {
                e = s.projections().get(r.projection().ordinal()).expression();
            } else if (k instanceof GroupBy.GroupKey.Expr ex) {
                e = ex.expression();
            } else {
                throw new IllegalStateException("unknown GROUP BY key: " + k);
            }
            out.add(render(e, dialect));
        }
        return out;
    }

    /** Ordered, rendered ORDER BY keys (with sort spec — order IS row-significant). */
    private static List<String> orderKeys(SelectStatement s, Dialect dialect) {
        List<String> out = new ArrayList<>();
        for (OrderKey k : s.orderKeys()) {
            out.add(render(k.expression(), dialect) + " " + k.sort());
        }
        return out;
    }

    /**
     * The NAMED accepted-equivalence classes a divergence needed, or empty when the two statements
     * are NOT equivalent. Empty SET = byte-level-identical structure (no relaxation needed).
     *
     * <p><b>Stable contract:</b> the class-name strings below are a closed catalog referenced
     * verbatim by consumer allowlists (promotion gates) and their ledger documentation — they MUST
     * NOT be renamed. A consumer promotes a divergence only when EVERY needed class is allowed.
     * <ul>
     *   <li>{@code join-order} — the FROM table SEQUENCE differs (multiset equal; inner-join order
     *       is result-neutral).</li>
     *   <li>{@code where-conjunct-set} — the WHERE/ON conjunct ORDER differs (set equal; AND is
     *       commutative).</li>
     *   <li>{@code join-style} — a conjunct moved between ON and WHERE (comma-product ↔ ANSI join;
     *       equivalent for inner joins).</li>
     * </ul>
     */
    public static Optional<Set<String>> equivalenceClasses(SelectStatement a, SelectStatement b, Dialect dialect) {
        if (!describeAll(a, b, dialect).isEmpty()) {
            return Optional.empty();
        }
        Set<String> classes = new java.util.LinkedHashSet<>();
        List<String> ta = new ArrayList<>();
        List<String> tb = new ArrayList<>();
        List<String> onA = new ArrayList<>();
        List<String> onB = new ArrayList<>();
        a.from().ifPresent(f -> collectOrdered(f, ta, onA, dialect));
        b.from().ifPresent(f -> collectOrdered(f, tb, onB, dialect));
        if (!ta.equals(tb)) {
            classes.add("join-order");
        }
        // WHERE-only conjunct SETS differing (while the ON+WHERE union is equal — guaranteed by
        // describeAll) = a conjunct moved between ON and WHERE.
        Set<String> wa = new HashSet<>();
        Set<String> wb = new HashSet<>();
        for (Predicate p : a.filters()) {
            wa.add(render(p, dialect));
        }
        for (Predicate p : b.filters()) {
            wb.add(render(p, dialect));
        }
        if (!wa.equals(wb)) {
            classes.add("join-style");
        }
        // Conjunct ORDER: only the WHERE filters' own sequence. The ON sequence follows the join
        // order (an ON is attached to its join), so an ON reorder is ALREADY named by join-order —
        // counting it here would over-name a pure join reorder and block its promotion.
        List<String> fseqA = new ArrayList<>();
        List<String> fseqB = new ArrayList<>();
        for (Predicate p : a.filters()) {
            fseqA.add(render(p, dialect));
        }
        for (Predicate p : b.filters()) {
            fseqB.add(render(p, dialect));
        }
        if (!fseqA.equals(fseqB) && wa.equals(wb)) {
            classes.add("where-conjunct-set");
        }
        return Optional.of(classes);
    }

    /** In-order table aliases + in-order JOIN-ON/table-filter fragments (sequence-preserving twin
     * of {@link #collect}). */
    private static void collectOrdered(FromClause f, List<String> tables, List<String> conjuncts, Dialect dialect) {
        if (f instanceof FromClause.FromJoin j) {
            collectOrdered(j.left(), tables, conjuncts, dialect);
            collectOrdered(j.right(), tables, conjuncts, dialect);
            conjuncts.add(render(j.on(), dialect));
        } else if (f instanceof FromClause.FromProduct p) {
            for (FromClause item : p.items()) {
                collectOrdered(item, tables, conjuncts, dialect);
            }
        } else if (f instanceof FromClause.FromTable t) {
            tables.add("t:" + t.alias());
            t.filter().ifPresent(pr -> conjuncts.add(render(pr, dialect)));
        } else if (f instanceof FromClause.FromSubquery sq) {
            tables.add("sub:" + sq.alias());
        } else if (f instanceof FromClause.FromSet fs) {
            // Opaque leaf by alias, like FromSubquery (structured bodies compare by alias; only the
            // pre-rendered FromRaw carries its text).
            tables.add("set:" + fs.alias());
        } else if (f instanceof FromClause.FromRaw r) {
            tables.add("raw:" + SqlTextNormalizer.normalizeWhitespace(r.sql()) + "#" + r.alias());
        } else if (f instanceof FromClause.FromVariant v) {
            tables.add("var:" + v.alias());
        } else if (f instanceof FromClause.FromInline in) {
            tables.add("inline:" + in.alias());
        }
    }

    /** Hoists every FROM-tree table (by alias) into {@code tables} (a multiset — duplicates are
     * meaningful), every JOIN-ON / table-filter into {@code conjuncts}. */
    private static void collect(FromClause f, List<String> tables, Set<String> conjuncts, Dialect dialect) {
        if (f instanceof FromClause.FromJoin j) {
            collect(j.left(), tables, conjuncts, dialect);
            collect(j.right(), tables, conjuncts, dialect);
            conjuncts.add(render(j.on(), dialect));
        } else if (f instanceof FromClause.FromProduct p) {
            for (FromClause item : p.items()) {
                collect(item, tables, conjuncts, dialect);
            }
        } else if (f instanceof FromClause.FromTable t) {
            tables.add("t:" + t.alias());
            t.filter().ifPresent(pr -> conjuncts.add(render(pr, dialect)));
        } else if (f instanceof FromClause.FromSubquery sq) {
            tables.add("sub:" + sq.alias());
        } else if (f instanceof FromClause.FromSet fs) {
            // Opaque leaf by alias, like FromSubquery (structured bodies compare by alias; only the
            // pre-rendered FromRaw carries its text).
            tables.add("set:" + fs.alias());
        } else if (f instanceof FromClause.FromRaw r) {
            tables.add("raw:" + SqlTextNormalizer.normalizeWhitespace(r.sql()) + "#" + r.alias());
        } else if (f instanceof FromClause.FromVariant v) {
            tables.add("var:" + v.alias());
        } else if (f instanceof FromClause.FromInline in) {
            tables.add("inline:" + in.alias());
        }
    }

    /** The elements in exactly one of the two sets (for a readable diff message; {@code left} is
     * the first statement argument, {@code right} the second). */
    private static String symmetric(Set<String> a, Set<String> b) {
        List<String> onlyA = new ArrayList<>();
        for (String x : a) {
            if (!b.contains(x)) {
                onlyA.add(x);
            }
        }
        List<String> onlyB = new ArrayList<>();
        for (String x : b) {
            if (!a.contains(x)) {
                onlyB.add(x);
            }
        }
        return "onlyLeft=" + onlyA + " onlyRight=" + onlyB;
    }

    /** Multiset difference by occurrence count (a duplicated table reports as {@code table×2 vs ×1}). */
    private static String symmetricMultiset(List<String> a, List<String> b) {
        java.util.Map<String, Integer> countA = counts(a);
        java.util.Map<String, Integer> countB = counts(b);
        List<String> onlyA = new ArrayList<>();
        List<String> onlyB = new ArrayList<>();
        Set<String> keys = new HashSet<>(countA.keySet());
        keys.addAll(countB.keySet());
        for (String k : keys) {
            int na = countA.getOrDefault(k, 0);
            int nb = countB.getOrDefault(k, 0);
            if (na > nb) {
                onlyA.add(nb == 0 ? k : k + "×" + na + " vs ×" + nb);
            } else if (nb > na) {
                onlyB.add(na == 0 ? k : k + "×" + nb + " vs ×" + na);
            }
        }
        return "onlyLeft=" + onlyA + " onlyRight=" + onlyB;
    }

    private static java.util.Map<String, Integer> counts(List<String> xs) {
        java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
        for (String x : xs) {
            out.merge(x, 1, Integer::sum);
        }
        return out;
    }
}
