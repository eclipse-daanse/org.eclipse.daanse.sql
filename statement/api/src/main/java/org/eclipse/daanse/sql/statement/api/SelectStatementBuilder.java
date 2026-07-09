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
package org.eclipse.daanse.sql.statement.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.dialect.api.generator.StatementHint;
import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.eclipse.daanse.sql.statement.api.model.ColumnAlias;
import org.eclipse.daanse.sql.statement.api.model.FromClause;
import org.eclipse.daanse.sql.statement.api.model.GroupBy;
import org.eclipse.daanse.sql.statement.api.model.JoinKind;
import org.eclipse.daanse.sql.statement.api.model.OrderKey;
import org.eclipse.daanse.sql.statement.api.model.Projection;
import org.eclipse.daanse.sql.statement.api.model.ProjectionRef;
import org.eclipse.daanse.sql.statement.api.model.RowLimit;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.SortSpec;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;

/**
 * Mutable, <em>dialect-free</em> builder for a {@link SelectStatement}.
 * <p>
 * Consumers accumulate clauses with intent methods ({@code project},
 * {@code where}, {@code groupOn}, {@code orderOn}, {@code from}/{@code join},
 * {@code rowLimit}) and call {@link #build()} to obtain the immutable model.
 * There is deliberately no {@code Dialect} here: alias generation and
 * alias-vs-expression decisions are made later by the renderer, which is the
 * only dialect-aware component.
 * <p>
 * {@link #project(SqlExpression, BestFitColumnType)} returns a
 * {@link ProjectionRef} handle; pass it to {@link #groupOn(ProjectionRef)} /
 * {@link #orderOn(ProjectionRef, SortSpec)} instead of capturing and threading
 * an alias string.
 * <p>
 * Not thread-safe; use one instance per query being built.
 */
public final class SelectStatementBuilder {

    private boolean distinct;
    private final List<Projection> projections = new ArrayList<>();
    private FromClause from;
    private final List<Predicate> filters = new ArrayList<>();
    private final List<GroupBy.GroupKey> groupKeys = new ArrayList<>();
    private final List<GroupBy.GroupingSet> groupingSets = new ArrayList<>();
    private final List<GroupBy.GroupingFunction> groupingFunctions = new ArrayList<>();
    private boolean completeNonAggregatesGroupBy;
    private final java.util.Set<Integer> groupByCompletionExempt = new java.util.HashSet<>();
    private final List<Predicate> having = new ArrayList<>();
    private final List<OrderKey> orderKeys = new ArrayList<>();
    private RowLimit rowLimit;
    private String headerComment;
    private String footerComment;
    private final List<StatementHint> statementHints = new ArrayList<>();
    /**
     * Identity-keyed so structurally-equal predicates (and the pushed-down join
     * ONs) don't collide.
     */
    private final java.util.Map<Predicate, String> filterComments = new java.util.IdentityHashMap<>();

    private SelectStatementBuilder() {
    }

    /**
     * Sets an optional statement-level explanatory comment (emitted only when
     * comments are on).
     */
    public SelectStatementBuilder header(String comment) {
        this.headerComment = comment;
        return this;
    }

    /**
     * Sets an optional trailing explanatory comment, appended at the very end
     * (emitted only when comments are on).
     */
    public SelectStatementBuilder footerComment(String comment) {
        this.footerComment = comment;
        return this;
    }

    /**
     * Adds a statement-level optimizer hint, spelled (or silently ignored) by the
     * dialect at render time. Unlike comments, hints are semantic for the DBMS and
     * are always emitted.
     */
    public SelectStatementBuilder statementHint(StatementHint hint) {
        this.statementHints.add(Objects.requireNonNull(hint, "hint"));
        return this;
    }

    /** Creates an empty assembler. */
    public static SelectStatementBuilder create() {
        return new SelectStatementBuilder();
    }

    public SelectStatementBuilder distinct(boolean value) {
        this.distinct = value;
        return this;
    }

    /** Adds an expression to the {@code SELECT} list and returns a handle to it. */
    public ProjectionRef project(SqlExpression expression, BestFitColumnType columnType) {
        return project(expression, columnType, null);
    }

    /** Adds an expression to the {@code SELECT} list with an explicit alias. */
    public ProjectionRef project(SqlExpression expression, BestFitColumnType columnType, ColumnAlias alias) {
        return project(expression, columnType, alias, null);
    }

    /**
     * Adds an expression to the {@code SELECT} list with an explicit alias and an
     * explanatory comment.
     */
    public ProjectionRef project(SqlExpression expression, BestFitColumnType columnType, ColumnAlias alias,
            String comment) {
        int ordinal = projections.size();
        Optional<ColumnAlias> a = Optional.ofNullable(alias);
        projections.add(new Projection(expression, columnType, a, Optional.ofNullable(comment)));
        return new ProjectionRef(ordinal, a);
    }

    /** Convenience: project an expression and group by it. */
    public ProjectionRef projectAndGroup(SqlExpression expression, BestFitColumnType columnType) {
        ProjectionRef ref = project(expression, columnType);
        groupOn(ref);
        return ref;
    }

    public SelectStatementBuilder groupOn(ProjectionRef ref) {
        groupKeys.add(new GroupBy.GroupKey.Ref(ref));
        return this;
    }

    public SelectStatementBuilder groupOn(SqlExpression expression) {
        groupKeys.add(new GroupBy.GroupKey.Expr(expression));
        return this;
    }

    /**
     * Marks the group by as needing per-dialect completion: on dialects that do not allow
     * non-aggregate select columns outside {@code GROUP BY}, the renderer appends every
     * non-aggregate projection not already a key. Set this when the query genuinely groups
     * (i.e. a member-enumeration read that placed keys/ordinals as group keys); leave it off
     * for a query whose GROUP BY is incidental (e.g. a single non-level-dependent property on a
     * non-grouping read), so the renderer does not over-group.
     */
    public SelectStatementBuilder completeNonAggregatesGroupBy() {
        this.completeNonAggregatesGroupBy = true;
        return this;
    }

    /**
     * Exempts one projection from the dialect GROUP-BY completion (see
     * {@link #completeNonAggregatesGroupBy()}). Use for a projection that is semantically an
     * aggregate the renderer cannot recognise structurally — an arithmetic expression WRAPPING
     * aggregates (e.g. a native TopCount/Order measure {@code (sum(a)-sum(b))/sum(b)}) — which
     * must not be added to GROUP BY on restrictive dialects. No effect on permissive dialects.
     */
    public SelectStatementBuilder excludeFromGroupByCompletion(ProjectionRef ref) {
        groupByCompletionExempt.add(ref.ordinal());
        return this;
    }

    public SelectStatementBuilder addGroupingSet(List<SqlExpression> keys) {
        groupingSets.add(new GroupBy.GroupingSet(List.copyOf(keys)));
        return this;
    }

    public SelectStatementBuilder addGroupingFunction(SqlExpression argument) {
        groupingFunctions.add(new GroupBy.GroupingFunction(argument));
        return this;
    }

    public SelectStatementBuilder where(Predicate predicate) {
        filters.add(predicate);
        return this;
    }

    /**
     * Adds a {@code WHERE} predicate carrying an explanatory comment (emitted only
     * when comments are on).
     */
    public SelectStatementBuilder where(Predicate predicate, String comment) {
        filters.add(predicate);
        if (comment != null) {
            filterComments.put(predicate, comment);
        }
        return this;
    }

    public SelectStatementBuilder having(Predicate predicate) {
        having.add(predicate);
        return this;
    }

    /**
     * Adds a {@code HAVING} predicate carrying an explanatory comment (emitted only
     * when comments are on).
     */
    public SelectStatementBuilder having(Predicate predicate, String comment) {
        having.add(predicate);
        if (comment != null) {
            filterComments.put(predicate, comment);
        }
        return this;
    }

    public SelectStatementBuilder orderOn(SqlExpression expression, SortSpec spec) {
        addOrder(new OrderKey(expression, Optional.empty(), spec), spec);
        return this;
    }

    public SelectStatementBuilder orderOn(ProjectionRef ref, SortSpec spec) {
        SqlExpression expression = projections.get(ref.ordinal()).expression();
        addOrder(new OrderKey(expression, Optional.of(ref), spec), spec);
        return this;
    }

    private void addOrder(OrderKey key, SortSpec spec) {
        if (spec.prepend()) {
            orderKeys.add(0, key);
        } else {
            orderKeys.add(key);
        }
    }

    /** Sets the {@code FROM} clause (table, sub-query, or join tree). */
    public SelectStatementBuilder from(FromClause clause) {
        this.from = clause;
        return this;
    }

    /** Sets the {@code FROM} to a sub-query built by another assembler. */
    public SelectStatementBuilder fromSubquery(SelectStatementBuilder inner, TableAlias alias) {
        this.from = From.subquery(inner.build(), alias);
        return this;
    }

    /** Joins the current {@code FROM} with {@code right} on {@code on}. */
    public SelectStatementBuilder join(JoinKind kind, FromClause right, Predicate on) {
        return join(kind, right, on, null);
    }

    /**
     * Joins the current {@code FROM} with {@code right} on {@code on}, carrying an
     * explanatory comment.
     */
    public SelectStatementBuilder join(JoinKind kind, FromClause right, Predicate on, String comment) {
        Objects.requireNonNull(from, "set a FROM before joining");
        this.from = new FromClause.FromJoin(from, kind, right, on, Optional.ofNullable(comment));
        return this;
    }

    public SelectStatementBuilder innerJoin(FromClause right, Predicate on) {
        return join(JoinKind.INNER, right, on);
    }

    public SelectStatementBuilder leftJoin(FromClause right, Predicate on) {
        return join(JoinKind.LEFT, right, on);
    }

    public SelectStatementBuilder rowLimit(long maxRows) {
        this.rowLimit = RowLimit.of(maxRows);
        return this;
    }

    public SelectStatementBuilder rowLimit(long maxRows, long offset) {
        this.rowLimit = RowLimit.of(maxRows, offset);
        return this;
    }

    public int projectionCount() {
        return projections.size();
    }

    /** Produces the immutable {@link SelectStatement}. */
    public SelectStatement build() {
        GroupBy groupBy = new GroupBy(List.copyOf(groupKeys), List.copyOf(groupingSets),
                List.copyOf(groupingFunctions), completeNonAggregatesGroupBy);
        List<Projection> finalProjections = projections;
        if (!groupByCompletionExempt.isEmpty()) {
            finalProjections = new java.util.ArrayList<>(projections.size());
            for (int i = 0; i < projections.size(); i++) {
                Projection p = projections.get(i);
                finalProjections.add(groupByCompletionExempt.contains(i)
                        ? new Projection(p.expression(), p.columnType(), p.alias(), p.comment(), true)
                        : p);
            }
        }
        return new SelectStatement(distinct, List.copyOf(finalProjections), Optional.ofNullable(from), List.copyOf(filters),
                groupBy, List.copyOf(having), List.copyOf(orderKeys), Optional.ofNullable(rowLimit),
                Optional.ofNullable(headerComment), new java.util.IdentityHashMap<>(filterComments),
                Optional.ofNullable(footerComment), List.copyOf(statementHints));
    }
}
