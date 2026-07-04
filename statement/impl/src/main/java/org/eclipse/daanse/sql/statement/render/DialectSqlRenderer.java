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
package org.eclipse.daanse.sql.statement.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.daanse.jdbc.db.api.schema.SchemaReference;
import org.eclipse.daanse.jdbc.db.api.schema.TableReference;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType;
import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.eclipse.daanse.sql.statement.api.model.DeleteStatement;
import org.eclipse.daanse.sql.statement.api.model.FromClause;
import org.eclipse.daanse.sql.statement.api.model.GroupBy;
import org.eclipse.daanse.sql.statement.api.model.InsertStatement;
import org.eclipse.daanse.sql.statement.api.model.JoinKind;
import org.eclipse.daanse.sql.statement.api.model.OrderKey;
import org.eclipse.daanse.sql.statement.api.model.Projection;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.SetOperation;
import org.eclipse.daanse.sql.statement.api.model.SortSpec;
import org.eclipse.daanse.sql.statement.api.model.Statement;
import org.eclipse.daanse.sql.statement.api.model.UpdateStatement;
import org.eclipse.daanse.sql.statement.api.model.WithStatement;
import org.eclipse.daanse.sql.statement.api.model.NullOrder;
import org.eclipse.daanse.sql.statement.api.model.SortDirection;
import org.eclipse.daanse.sql.statement.api.render.BoundParameter;
import org.eclipse.daanse.sql.statement.api.render.RenderOptions;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;
import org.eclipse.daanse.sql.statement.api.render.SqlRenderer;

/**
 * The {@link SqlRenderer} implementation. The single dialect-aware component: it turns the
 * dialect-free query model into SQL, taking every spelling decision (identifier/literal
 * quoting, the {@code AS} keyword, join style, group-by alias-vs-expression, grouping-set
 * support, pagination placement, null ordering) from the {@link Dialect}.
 * <p>
 * Output layout follows {@link RenderOptions}: compact renders the statement on one line;
 * formatted ({@link RenderOptions#multiLine()}) breaks the top-level {@code SELECT} onto
 * indented clause lines (see {@link #fmtKw}). Nested contexts — CTE bodies, derived tables
 * ({@code FromSubquery} / {@code FromSet}), set-operation inputs, {@code EXISTS} subqueries and
 * scalar subqueries — render compact (and without comments), whatever the top-level options
 * say, with ONE exception: in the diagnostic combination (formatted AND comments on), derived
 * tables in FROM ({@code FromSubquery} / {@code FromSet}, including the set's inputs) render
 * FORMATTED too, with the enclosing comment style and their whole block indented one level
 * deeper (indent stacking), so the nesting reads clearly; the inner statement's header renders
 * inside the parentheses, its footer stays suppressed (footers are top-of-statement-only, see
 * {@link #render}). {@code EXISTS} / scalar subqueries and CTE bodies have no options in scope
 * and stay always-compact. Executed SQL (comments off) is byte-identical either way. Nested
 * bind parameters still accumulate in placeholder order with the enclosing statement's.
 * <p>
 * Statement-level hints ({@code SelectStatement.statementHints()}) are ALWAYS emitted — they are
 * <em>not</em> gated by {@link RenderOptions#comments()}: unlike explanatory comments they are
 * semantic for the DBMS (its optimizer reads them). The dialect's {@code HintGenerator} chooses
 * the placement/spelling: an optimizer block directly after the {@code SELECT} keyword
 * ({@code selectHint}, MySQL/Oracle) and/or a trailing clause ({@code statementOption}, SQL
 * Server) — the trailing clause is placed after the pagination suffix and before the footer
 * comment, which stays last.
 */
public final class DialectSqlRenderer implements SqlRenderer {

    private final Dialect dialect;

    /**
     * Bind parameters accumulated during a single {@link #render} call, in placeholder order.
     * Not thread-safe: render one statement at a time per instance (renderers are cheap).
     */
    private List<BoundParameter> parameters = new ArrayList<>();

    /**
     * The current SELECT's projections while rendering its {@code HAVING}, or {@code null} elsewhere. Lets a
     * {@link Predicate.Regexp} resolve its source to the matching SELECT alias when the dialect
     * {@code requiresHavingAlias()} (e.g. MySQL renders {@code UPPER(c5) REGEXP …}, not the column). Scoped
     * (saved/restored) around the HAVING render so nested selects don't clobber it.
     */
    private List<org.eclipse.daanse.sql.statement.api.model.Projection> havingAliasProjections;

    public DialectSqlRenderer(Dialect dialect) {
        this.dialect = Objects.requireNonNull(dialect, "dialect");
    }

    @Override
    public RenderedSql render(Statement statement, RenderOptions options) {
        this.parameters = new ArrayList<>();
        RenderedSql body = renderInternal(statement, options);
        String sql = body.sql();
        // The footer comment goes at the very end of the whole rendered statement, always on its own
        // line (compact mode included). Comments off => byte-identical output (cache-key stability).
        if (options.comments() && statement.footerComment().isPresent()) {
            sql = sql + System.lineSeparator() + "/* " + escapeBlockCommentEnd(statement.footerComment().get())
                    + " */";
        }
        return new RenderedSql(sql, body.columnTypes(), List.copyOf(parameters));
    }

    /** Dispatch without resetting the parameter accumulator (used for nested statements). */
    private RenderedSql renderInternal(Statement statement, RenderOptions options) {
        if (statement instanceof SelectStatement select) {
            return renderSelect(select, options);
        }
        if (statement instanceof SetOperation set) {
            return renderSet(set, options);
        }
        if (statement instanceof InsertStatement insert) {
            return renderInsert(insert);
        }
        if (statement instanceof UpdateStatement update) {
            return renderUpdate(update);
        }
        if (statement instanceof DeleteStatement delete) {
            return renderDelete(delete);
        }
        if (statement instanceof WithStatement with) {
            return renderWith(with);
        }
        throw new IllegalArgumentException("unsupported statement: " + statement);
    }

    private RenderedSql renderWith(WithStatement with) {
        // CTE bodies render first, so their bind parameters precede the body's, in order.
        List<org.eclipse.daanse.jdbc.db.dialect.api.generator.CteGenerator.Cte> ctes = new ArrayList<>();
        for (org.eclipse.daanse.sql.statement.api.model.CommonTableExpression cte : with.ctes()) {
            String body = renderInternal(cte.query(), RenderOptions.compact()).sql();
            // Quote the CTE name (so body references via From.table(name, ...) match the casing);
            // append an explicit quoted column list when given (required for recursive CTEs on H2).
            String name = dialect.quoteIdentifier(cte.name());
            if (!cte.columns().isEmpty()) {
                name += "(" + cte.columns().stream().map(dialect::quoteIdentifier).collect(Collectors.joining(", "))
                        + ")";
            }
            ctes.add(new org.eclipse.daanse.jdbc.db.dialect.api.generator.CteGenerator.Cte(name, body));
        }
        String withClause = dialect.cteGenerator().withClause(ctes, with.recursive());
        RenderedSql body = renderInternal(with.body(), RenderOptions.compact());
        return RenderedSql.of(withClause + body.sql(), body.columnTypes());
    }

    // ---- DML -------------------------------------------------------------------

    private void appendQualifiedTable(StringBuilder sb, TableReference table) {
        String schema = table.schema().map(SchemaReference::name).orElse(null);
        sb.append(dialect.quoteIdentifier(schema, table.name()));
    }

    private RenderedSql renderInsert(InsertStatement ins) {
        StringBuilder sb = new StringBuilder("insert into ");
        appendQualifiedTable(sb, ins.table());
        if (!ins.columns().isEmpty()) {
            sb.append(" (")
                    .append(ins.columns().stream().map(c -> dialect.quoteIdentifier(c))
                            .collect(Collectors.joining(", ")))
                    .append(")");
        }
        if (ins.source().isPresent()) {
            sb.append(' ').append(renderInternal(ins.source().get(), RenderOptions.compact()).sql());
        } else {
            sb.append(" values ");
            sb.append(ins.rows().stream()
                    .map(row -> "(" + row.stream().map(this::renderExpression).collect(Collectors.joining(", ")) + ")")
                    .collect(Collectors.joining(", ")));
        }
        return RenderedSql.of(sb.toString(), List.of());
    }

    private RenderedSql renderUpdate(UpdateStatement upd) {
        StringBuilder sb = new StringBuilder("update ");
        appendQualifiedTable(sb, upd.table());
        sb.append(" set ");
        sb.append(upd.assignments().stream()
                .map(a -> dialect.quoteIdentifier(a.column()) + " = " + renderExpression(a.value()))
                .collect(Collectors.joining(", ")));
        if (!upd.filters().isEmpty()) {
            sb.append(" where ").append(renderPredicateList(upd.filters(), " and "));
        }
        return RenderedSql.of(sb.toString(), List.of());
    }

    private RenderedSql renderDelete(DeleteStatement del) {
        StringBuilder sb = new StringBuilder("delete from ");
        appendQualifiedTable(sb, del.table());
        if (!del.filters().isEmpty()) {
            sb.append(" where ").append(renderPredicateList(del.filters(), " and "));
        }
        return RenderedSql.of(sb.toString(), List.of());
    }

    // ---- SELECT ----------------------------------------------------------------

    /**
     * Formats a clause keyword/separator for the requested mode, byte-compatible with the legacy
     * {@code SqlQuery.ClauseList.formatClauseKeyword}: compact returns {@code s} unchanged; formatted maps a
     * leading-space keyword (e.g. {@code " from "}, {@code " and "}) to {@code NL + keyword.trimLeading} and a
     * trailing-space separator (e.g. {@code ", "}) to {@code keyword.trimTrailing + NL + indent} (a keyword
     * with both, like {@code " from "}, gets both → {@code "\nfrom\n<indent>"}); a keyword ending in
     * {@code "("} gets {@code "(" + NL + indent}. Top-level prefix is empty.
     */
    private static String fmtKw(String s, RenderOptions options) {
        if (!options.formatted()) {
            return s;
        }
        String nl = System.lineSeparator();
        String r = s;
        if (r.startsWith(" ")) {
            r = nl + r.substring(1);
        }
        if (r.endsWith(" ")) {
            r = r.substring(0, r.length() - 1) + nl + options.indent();
        } else if (r.endsWith("(")) {
            r = r + nl + options.indent();
        }
        return r;
    }

    /**
     * The one {@code *}{@code /}-escape implementation: neutralize any {@code *}{@code /} in arbitrary
     * text so it cannot terminate an enclosing block comment early. Newlines are kept — used for the
     * footer comment, where multi-line text stays multi-line inside the block.
     */
    private static String escapeBlockCommentEnd(String text) {
        return text.replace("*/", "* /");
    }

    /**
     * Make arbitrary text safe to embed inside an <em>inline</em> {@code /* ... *}{@code /} block
     * comment: {@link #escapeBlockCommentEnd} plus folding newlines to spaces so the comment stays on
     * one line.
     */
    private static String safeBlockComment(String text) {
        return escapeBlockCommentEnd(text).replace('\n', ' ').replace('\r', ' ');
    }

    /** A {@code --} line comment cannot span lines: fold any newline to a space. */
    private static String safeLineComment(String text) {
        return text.replace('\n', ' ').replace('\r', ' ');
    }

    private boolean lineComments(RenderOptions o) {
        return o.comments() && o.effectiveCommentStyle() == RenderOptions.CommentStyle.LINE;
    }

    private boolean blockComments(RenderOptions o) {
        return o.comments() && o.effectiveCommentStyle() == RenderOptions.CommentStyle.BLOCK;
    }

    /** LINE style: the comment on its own line BEFORE an element — {@code "-- text\n<indent>"} — or "". */
    private String lineCommentBefore(java.util.Optional<String> c, RenderOptions o, String indent) {
        return (lineComments(o) && c.isPresent())
                ? "-- " + safeLineComment(c.get()) + System.lineSeparator() + indent : "";
    }

    /** BLOCK style: the comment inline AFTER an element — {@code " /* text *}{@code /"} — or "". */
    private String blockCommentAfter(java.util.Optional<String> c, RenderOptions o) {
        return (blockComments(o) && c.isPresent()) ? " /* " + safeBlockComment(c.get()) + " */" : "";
    }

    private RenderedSql renderSelect(SelectStatement s, RenderOptions options) {
        final String itemSep = fmtKw(", ", options);
        StringBuilder sb = new StringBuilder();
        List<BestFitColumnType> types = new ArrayList<>();

        // WHERE accumulates the explicit filters plus anything pushed down while rendering
        // the FROM clause (non-ANSI comma joins, per-table filters).
        List<Predicate> where = new ArrayList<>(s.filters());
        String fromSql = s.from().map(f -> renderFrom(f, where, options)).orElse(null);

        if (options.comments()) {
            s.headerComment().ifPresent(h -> {
                for (String line : h.split("\\R")) {
                    sb.append("-- ").append(line).append(System.lineSeparator());
                }
            });
        }

        // Statement hints are always emitted (never comment-gated: the DBMS optimizer reads them).
        // The selectHint block goes directly after the SELECT keyword, before DISTINCT; when the
        // dialect emits none (the default), the keyword renders byte-identically to a hint-free build.
        String selectHint = s.statementHints().isEmpty() ? ""
                : dialect.hintGenerator().selectHint(s.statementHints()).toString();
        if (selectHint.isEmpty()) {
            sb.append(fmtKw(s.distinct() ? "select distinct " : "select ", options));
        } else {
            sb.append(fmtKw("select " + selectHint + (s.distinct() ? "distinct " : ""), options));
        }
        s.rowLimit().ifPresent(rl -> dialect.paginationGenerator().selectPrefix(rl.maxRows(), rl.offset())
                .ifPresent(prefix -> sb.append(prefix).append(' ')));

        boolean first = true;
        for (int i = 0; i < s.projections().size(); i++) {
            Projection p = s.projections().get(i);
            if (!first) {
                sb.append(itemSep);
            }
            first = false;
            sb.append(lineCommentBefore(p.comment(), options, options.indent()));
            String exprSql = renderExpression(p.expression());
            sb.append(exprSql);
            String alias = effectiveAlias(p, i);
            // A whole-row projection can never be aliased ("select * as c0" is invalid SQL): suppress for
            // the structured Star node and — kept for byte-compat with existing consumers — a Raw("*").
            if (alias != null && !(p.expression() instanceof SqlExpression.Star) && !"*".equals(exprSql)) {
                sb.append(" as ").append(dialect.quoteIdentifier(alias));
            }
            sb.append(blockCommentAfter(p.comment(), options));
            types.add(p.columnType());
        }
        // GROUPING(...) super-aggregate columns are appended as extra select items.
        int g = 0;
        for (GroupBy.GroupingFunction gf : s.groupBy().groupingFunctions()) {
            sb.append(itemSep).append(dialect.functionGenerator().generateGrouping(renderExpression(gf.argument())));
            sb.append(" as ").append(dialect.quoteIdentifier("g" + g++));
            types.add(null);
        }

        if (fromSql != null) {
            sb.append(fmtKw(" from ", options)).append(fromSql);
        }
        if (!where.isEmpty()) {
            sb.append(fmtKw(" where ", options)).append(renderWhere(where, options, s.filterComments()));
        }
        renderGroupBy(s.groupBy(), s.projections(), sb, options);
        if (!s.having().isEmpty()) {
            // HAVING shares the filterComments map (keyed by predicate identity) so each conjunct can carry
            // its provenance comment (native Filter measure condition); byte-identical when comments are off.
            // Expose the projections so a Regexp source resolves to its SELECT alias (requiresHavingAlias).
            List<org.eclipse.daanse.sql.statement.api.model.Projection> prevHaving = havingAliasProjections;
            havingAliasProjections = s.projections();
            sb.append(fmtKw(" having ", options)).append(renderWhere(s.having(), options, s.filterComments()));
            havingAliasProjections = prevHaving;
        }
        if (!s.orderKeys().isEmpty()) {
            sb.append(fmtKw(" order by ", options));
            boolean firstOrder = true;
            for (OrderKey k : s.orderKeys()) {
                if (!firstOrder) {
                    sb.append(itemSep);
                }
                firstOrder = false;
                java.util.Optional<String> oc = orderKeyComment(k, s.projections());
                sb.append(lineCommentBefore(oc, options, options.indent()));
                sb.append(renderOrderKey(k, s.projections()));
                sb.append(blockCommentAfter(oc, options));
            }
        }
        s.rowLimit().ifPresent(rl -> sb.append(dialect.paginationGenerator().paginate(rl.maxRows(), rl.offset())));
        // Trailing statement option (SQL Server OPTION (...)) goes after the pagination suffix; the
        // footer comment (appended by render()) stays last.
        if (!s.statementHints().isEmpty()) {
            sb.append(dialect.hintGenerator().statementOption(s.statementHints()));
        }

        return RenderedSql.of(sb.toString(), Collections.unmodifiableList(types));
    }

    private String effectiveAlias(Projection p, int ordinal) {
        if (p.alias().isPresent()) {
            return p.alias().get().name();
        }
        if (dialect.allowsFieldAlias()) {
            return "c" + ordinal;
        }
        return null;
    }

    // ---- FROM ------------------------------------------------------------------

    private String renderFrom(FromClause from, List<Predicate> whereSink, RenderOptions options) {
        if (from instanceof FromClause.FromTable t) {
            StringBuilder b = new StringBuilder();
            // Provenance comment for a table carrying one (the FROM's BASE table — joined tables carry
            // theirs on the FromJoin): LINE style on its own line before the table, BLOCK style inline
            // before it. Byte-neutral when comments are off or the table carries none.
            if (t.comment().isPresent()) {
                if (lineComments(options)) {
                    b.append("-- ").append(safeLineComment(t.comment().get()))
                            .append(System.lineSeparator()).append(options.indent());
                } else if (blockComments(options)) {
                    b.append("/* ").append(safeBlockComment(t.comment().get())).append(" */ ");
                }
            }
            appendQualifiedTable(b, t.table());
            b.append(dialect.allowsFromAlias() ? " as " : " ").append(dialect.quoteIdentifier(t.alias().name()));
            if (!t.hints().isEmpty()) {
                dialect.hintGenerator().appendHintsAfterFromClause(b, t.hints());
            }
            t.filter().ifPresent(whereSink::add);
            return b.toString();
        }
        if (from instanceof FromClause.FromSubquery sq) {
            String inner = renderSelect(sq.query(), nestedOptions(options)).sql();
            return derivedTable(inner, sq.alias().name(), options);
        }
        if (from instanceof FromClause.FromSet fs) {
            // Internal renderSet (not render): nested bind parameters accumulate in placeholder order.
            String inner = renderSet(fs.set(), nestedOptions(options)).sql();
            return derivedTable(inner, fs.alias().name(), options);
        }
        if (from instanceof FromClause.FromRaw r) {
            return "(" + r.sql() + ")" + (dialect.allowsFromAlias() ? " as " : " ")
                    + dialect.quoteIdentifier(r.alias().name());
        }
        if (from instanceof FromClause.FromVariant v) {
            // Resolve the per-dialect map here (the one render-time pick), then render exactly like FromRaw.
            return "(" + chooseVariant(v.byDialectName()) + ")" + (dialect.allowsFromAlias() ? " as " : " ")
                    + dialect.quoteIdentifier(v.alias().name());
        }
        if (from instanceof FromClause.FromInline inl) {
            // Generate the inline-table VALUES SQL for the live dialect here (the one render-time dialect touch),
            // then wrap like a derived table — the same generateInline call the legacy build path made.
            return "(" + dialect.sqlGenerator().generateInline(inl.columnNames(), inl.columnTypes(), inl.rows())
                    + ")" + (dialect.allowsFromAlias() ? " as " : " ") + dialect.quoteIdentifier(inl.alias().name());
        }
        if (from instanceof FromClause.FromProduct prod) {
            // Comma product: no predicate of its own — the caller put any join conditions in WHERE.
            return prod.items().stream().map(item -> renderFrom(item, whereSink, options))
                    .collect(Collectors.joining(fmtKw(", ", options)));
        }
        if (from instanceof FromClause.FromJoin j) {
            String left = renderFrom(j.left(), whereSink, options);
            String right = renderFrom(j.right(), whereSink, options);
            if (j.kind() == JoinKind.CROSS) {
                return left + " cross join " + right;
            }
            // Outer joins (LEFT/RIGHT/FULL) must use ANSI ON; only INNER may fall back to comma + WHERE.
            boolean ansi = dialect.allowsJoinOn() || j.kind() != JoinKind.INNER;
            if (ansi) {
                String kw = switch (j.kind()) {
                    case LEFT -> "left join ";
                    case RIGHT -> "right join ";
                    case FULL -> "full join ";
                    default -> "join ";
                };
                // Parenthesize a nested join on the RIGHT so a non-left-deep tree renders unambiguously
                // (`a join (b join c on …) on …`); a left-deep tree's right side is a leaf and needs none.
                String rightSql = j.right() instanceof FromClause.FromJoin ? "(" + right + ")" : right;
                String onSql = " on " + renderPredicate(j.on());
                if (lineComments(options)) {
                    // LINE style: one join per line, with any comment on its own line above the join.
                    String ind = options.indent();
                    String cmt = j.comment().isPresent()
                            ? "-- " + safeLineComment(j.comment().get()) + System.lineSeparator() + ind : "";
                    return left + System.lineSeparator() + ind + cmt + kw + rightSql + onSql;
                }
                String joinComment = blockComments(options) && j.comment().isPresent()
                        ? "/* " + safeBlockComment(j.comment().get()) + " */ "
                        : "";
                return left + " " + kw + joinComment + rightSql + onSql;
            }
            whereSink.add(j.on());
            return left + fmtKw(", ", options) + right;
        }
        throw new IllegalArgumentException("unsupported from clause: " + from);
    }

    /**
     * True in the diagnostic combination — formatted AND comments on — where derived tables in FROM
     * render formatted (with comments) instead of the default compact-without-comments. Never true
     * for executed SQL (comments off), so the executed bytes are unchanged.
     */
    private static boolean nestedFormatted(RenderOptions o) {
        return o.formatted() && o.comments();
    }

    /**
     * The render options for a derived table's inner statement: the enclosing options in diagnostic
     * mode (formatted + comments propagate, keeping the comment style), plain compact otherwise.
     */
    private static RenderOptions nestedOptions(RenderOptions enclosing) {
        return nestedFormatted(enclosing) ? enclosing : RenderOptions.compact();
    }

    /**
     * Wraps a rendered inner statement as an aliased derived table. Compact: {@code (<inner>) as
     * <alias>} on one line, byte-identical to the historical output. Diagnostic mode
     * ({@link #nestedFormatted}): the parentheses go on their own lines and every inner line is
     * prefixed with two indent units (one for the paren level the enclosing FROM already opened, one
     * for the content) so the nesting reads clearly — indent stacking, applied per nesting depth
     * because inner derived tables run through this same wrapper again.
     */
    private String derivedTable(String innerSql, String aliasName, RenderOptions options) {
        String aliasSql = (dialect.allowsFromAlias() ? " as " : " ") + dialect.quoteIdentifier(aliasName);
        if (!nestedFormatted(options)) {
            return "(" + innerSql + ")" + aliasSql;
        }
        String nl = System.lineSeparator();
        String contentIndent = options.indent() + options.indent();
        String indented = innerSql.lines().map(l -> l.isEmpty() ? l : contentIndent + l)
                .collect(Collectors.joining(nl));
        return "(" + nl + indented + nl + options.indent() + ")" + aliasSql;
    }

    /**
     * Resolve a per-dialect SQL-fragment map to the fragment for this renderer's dialect. The one render-time
     * dialect pick for {@link FromClause.FromVariant} (and any future expression variant) — a 1:1 lift of the
     * legacy {@code ViewCodeSet.chooseQuery} fallback: the live dialect's entry, else {@code "generic"}, else
     * an error.
     */
    private String chooseVariant(java.util.Map<String, String> byDialectName) {
        String picked = byDialectName.get(dialect.name());
        if (picked != null) {
            return picked;
        }
        String generic = byDialectName.get("generic");
        if (generic == null) {
            throw new IllegalArgumentException(
                    "no SQL variant for dialect '" + dialect.name() + "' and no 'generic' fallback");
        }
        return generic;
    }

    // ---- GROUP BY --------------------------------------------------------------

    private void renderGroupBy(GroupBy gb, List<Projection> projections, StringBuilder sb, RenderOptions options) {
        if (gb.isEmpty()) {
            return;
        }
        if (!gb.groupingSets().isEmpty() && dialect.supportsGroupingSets()) {
            sb.append(fmtKw(" group by grouping sets (", options));
            sb.append(gb.groupingSets().stream()
                    .map(set -> "(" + set.keys().stream().map(this::renderExpression)
                            .collect(Collectors.joining(", ")) + ")")
                    .collect(Collectors.joining(", ")));
            sb.append(")");
            return;
        }
        // Plain GROUP BY: explicit keys, plus (when grouping sets are unsupported) the distinct
        // union of all grouping-set keys as a best-effort fallback. Dedup on the rendered key string,
        // keeping each key's comment (reused from the projection it references).
        java.util.LinkedHashMap<String, java.util.Optional<String>> keyed = new java.util.LinkedHashMap<>();
        for (GroupBy.GroupKey key : gb.keys()) {
            keyed.putIfAbsent(renderGroupKey(key, projections), groupKeyComment(key, projections));
        }
        for (GroupBy.GroupingSet set : gb.groupingSets()) {
            for (SqlExpression e : set.keys()) {
                keyed.putIfAbsent(renderExpression(e), java.util.Optional.empty());
            }
        }
        if (!keyed.isEmpty()) {
            sb.append(fmtKw(" group by ", options));
            String sep = fmtKw(", ", options);
            boolean firstKey = true;
            for (var entry : keyed.entrySet()) {
                if (!firstKey) {
                    sb.append(sep);
                }
                firstKey = false;
                sb.append(lineCommentBefore(entry.getValue(), options, options.indent()));
                sb.append(entry.getKey());
                sb.append(blockCommentAfter(entry.getValue(), options));
            }
        }
    }

    private java.util.Optional<String> groupKeyComment(GroupBy.GroupKey key, List<Projection> projections) {
        if (key instanceof GroupBy.GroupKey.Ref ref) {
            return projections.get(ref.projection().ordinal()).comment();
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<String> orderKeyComment(OrderKey key, List<Projection> projections) {
        return key.projectionRef().map(r -> projections.get(r.ordinal()).comment())
                .orElse(java.util.Optional.empty());
    }

    private String renderGroupKey(GroupBy.GroupKey key, List<Projection> projections) {
        if (key instanceof GroupBy.GroupKey.Ref ref) {
            int ordinal = ref.projection().ordinal();
            Projection p = projections.get(ordinal);
            if (dialect.requiresGroupByAlias()) {
                String alias = effectiveAlias(p, ordinal);
                if (alias != null) {
                    return dialect.quoteIdentifier(alias);
                }
            }
            return renderExpression(p.expression());
        }
        GroupBy.GroupKey.Expr expr = (GroupBy.GroupKey.Expr) key;
        return renderExpression(expr.expression());
    }

    // ---- ORDER BY --------------------------------------------------------------

    private String renderOrderKey(OrderKey key, List<Projection> projections) {
        String exprSql;
        if (key.projectionRef().isPresent() && dialect.requiresOrderByAlias()) {
            int ordinal = key.projectionRef().get().ordinal();
            String alias = effectiveAlias(projections.get(ordinal), ordinal);
            exprSql = alias != null ? dialect.quoteIdentifier(alias) : renderExpression(key.expression());
        } else {
            exprSql = renderExpression(key.expression());
        }
        SortSpec spec = key.sort();
        if (spec.collation().isPresent()) {
            // COLLATE binds to the key expression (not the direction); the name is a
            // dialect-specific identifier the caller chose - emitted verbatim (SortSpec javadoc).
            exprSql = exprSql + " collate " + spec.collation().get();
        }
        boolean ascending = spec.direction() == SortDirection.ASC;
        boolean collateNullsLast = spec.nullOrder() != NullOrder.FIRST;
        if (spec.nullSortValue() != null) {
            // Order nulls as if they held nullSortValue (e.g. a parent-child hierarchy nullParentValue).
            return dialect.orderByGenerator().generateOrderItemForOrderValue(
                    exprSql, spec.nullSortValue(), spec.nullSortDatatype(), ascending, collateNullsLast).toString();
        }
        return dialect.orderByGenerator().generateOrderItem(exprSql, spec.nullable(), ascending, collateNullsLast)
                .toString();
    }

    // ---- expressions & predicates ----------------------------------------------

    /** Renders a single scalar expression to its dialect SQL (e.g. a native-SQL column fragment). */
    public String renderExpression(SqlExpression e) {
        if (e instanceof SqlExpression.Column c) {
            if (c.name() == null) {
                // Degenerate key-less join column: render just the (quoted) qualifier.
                return c.tableQualifier().map(dialect::quoteIdentifier).orElse("");
            }
            String name = dialect.quoteIdentifier(c.name());
            return c.tableQualifier().map(q -> dialect.quoteIdentifier(q) + "." + name).orElse(name);
        }
        if (e instanceof SqlExpression.Literal l) {
            StringBuilder b = new StringBuilder();
            dialect.quote(b, l.value(), l.datatype());
            return b.toString();
        }
        if (e instanceof SqlExpression.Call c) {
            boolean distinct = c instanceof SqlExpression.Aggregate a && a.distinct();
            return c.name() + "(" + (distinct ? "distinct " : "")
                    + c.arguments().stream().map(this::renderExpression).collect(Collectors.joining(", ")) + ")";
        }
        if (e instanceof SqlExpression.Binary b) {
            String rendered = renderExpression(b.left()) + " " + b.operator().symbol() + " "
                    + renderExpression(b.right());
            return b.parenthesized() ? "(" + rendered + ")" : rendered;
        }
        if (e instanceof SqlExpression.Case c) {
            // The single-when if-then-else form delegates to the dialect's FunctionGenerator — the ONE
            // home of that spelling (default 'CASE WHEN … THEN … ELSE … END'; Access spells IIF(c,a,b)).
            // This makes a Case node render byte-identically to producers that historically called
            // wrapIntoSqlIfThenElseFunction directly (the ROLAP native IIF compiler).
            if (c.whens().size() == 1 && c.elseResult().isPresent()) {
                SqlExpression.Case.WhenClause w = c.whens().get(0);
                return dialect.functionGenerator().wrapIntoSqlIfThenElseFunction(
                        renderPredicate(w.condition()),
                        renderExpression(w.result()),
                        renderExpression(c.elseResult().get())).toString();
            }
            // Multi-when / no-else: generic lowercase form (no dialect generator covers it; no producer
            // emits this shape yet).
            StringBuilder sb = new StringBuilder("case");
            for (SqlExpression.Case.WhenClause w : c.whens()) {
                sb.append(" when ").append(renderPredicate(w.condition())).append(" then ")
                        .append(renderExpression(w.result()));
            }
            c.elseResult().ifPresent(er -> sb.append(" else ").append(renderExpression(er)));
            return sb.append(" end").toString();
        }
        if (e instanceof SqlExpression.Param pm) {
            parameters.add(new BoundParameter(pm.value(), pm.bound(), pm.datatype()));
            return dialect.parameterPlaceholderGenerator().placeholder(parameters.size());
        }
        if (e instanceof SqlExpression.ScalarSubquery sq) {
            // Compact like every nested context; nested bind parameters accumulate in placeholder order.
            return "(" + renderInternal(sq.query(), RenderOptions.compact()).sql() + ")";
        }
        if (e instanceof SqlExpression.Star st) {
            return st.tableQualifier().map(q -> dialect.quoteIdentifier(q) + ".*").orElse("*");
        }
        if (e instanceof SqlExpression.Ordinal o) {
            return Integer.toString(o.position());
        }
        if (e instanceof SqlExpression.Raw r) {
            return r.sql();
        }
        if (e instanceof SqlExpression.RawVariant v) {
            return chooseVariant(v.byDialectName());
        }
        if (e instanceof SqlExpression.ExtraAggregate ea) {
            return renderExtraAggregate(ea);
        }
        if (e instanceof SqlExpression.CaseFold cf) {
            return dialect.functionGenerator().wrapIntoSqlUpperCaseFunction(renderExpression(cf.inner())).toString();
        }
        if (e instanceof SqlExpression.KnownCall k) {
            // The dialect's FunctionGenerator picks the spelling for the portable function intent.
            List<String> renderedArgs = k.arguments().stream().map(this::renderExpression).toList();
            return dialect.functionGenerator().generateKnownFunction(k.function(), renderedArgs).toString();
        }
        throw new IllegalArgumentException("unsupported expression: " + e);
    }

    /**
     * Generates the SQL for a dialect-generated extra aggregate at render time via
     * {@code dialect.aggregationGenerator()} — the one dialect touch, dispatched per kind. The generators
     * return {@code Optional.empty()} when the dialect does not support the aggregate; that is an error here
     * (the legacy code returned {@code null} for the same case).
     */
    private String renderExtraAggregate(SqlExpression.ExtraAggregate ea) {
        org.eclipse.daanse.jdbc.db.dialect.api.generator.AggregationGenerator g = dialect.aggregationGenerator();
        CharSequence operand = ea.operand().map(this::renderExpression).orElse(null);
        SqlExpression.ExtraAggregate.Spec spec = ea.spec();
        java.util.Optional<String> sql;
        if (spec instanceof SqlExpression.ExtraAggregate.Spec.Percentile p) {
            sql = p.continuous()
                    ? g.generatePercentileCont(p.fraction(), p.descending(), p.tableName(), p.columnName())
                    : g.generatePercentileDisc(p.fraction(), p.descending(), p.tableName(), p.columnName());
        } else if (spec instanceof SqlExpression.ExtraAggregate.Spec.ListAgg l) {
            sql = g.generateListAgg(operand, l.distinct(), l.separator(), l.coalesce(), l.onOverflowTruncate(),
                    l.columns());
        } else if (spec instanceof SqlExpression.ExtraAggregate.Spec.BitAggregation b) {
            sql = g.generateBitAggregation(b.operation(), operand);
        } else if (spec instanceof SqlExpression.ExtraAggregate.Spec.NthValue nv) {
            sql = g.generateNthValueAgg(operand, nv.ignoreNulls(), nv.n(), nv.columns());
        } else {
            throw new IllegalArgumentException("unsupported extra-aggregate spec: " + spec);
        }
        return sql.orElseThrow(() -> new IllegalStateException(
                "dialect '" + dialect.name() + "' does not support extra aggregate: " + spec));
    }

    private String renderPredicateList(List<Predicate> predicates, String separator) {
        return predicates.stream().map(this::renderPredicate).collect(Collectors.joining(separator));
    }

    /**
     * WHERE-specific rendering: like {@link #renderPredicateList} but emits a per-conjunct comment (member
     * path / slicer / role-access) when comments are on. Byte-identical to {@code renderPredicateList} when
     * comments are off or no filter carries a comment (the common case).
     */
    private String renderWhere(List<Predicate> preds, RenderOptions options, java.util.Map<Predicate, String> comments) {
        if (!options.comments() || comments.isEmpty()) {
            return renderPredicateList(preds, fmtKw(" and ", options));
        }
        String sep = fmtKw(" and ", options);
        StringBuilder b = new StringBuilder();
        boolean first = true;
        for (Predicate p : preds) {
            if (!first) {
                b.append(sep);
            }
            first = false;
            java.util.Optional<String> c = java.util.Optional.ofNullable(comments.get(p));
            b.append(lineCommentBefore(c, options, options.indent()));
            b.append(renderPredicate(p));
            b.append(blockCommentAfter(c, options));
        }
        return b.toString();
    }

    /**
     * Renders a single predicate to its dialect SQL fragment (no surrounding SELECT context). Used by
     * callers that need a standalone predicate string, e.g. segment-cache-key construction.
     */
    public String renderPredicate(Predicate p) {
        if (p instanceof Predicate.Comparison c) {
            return renderExpression(c.left()) + " " + symbol(c.operator()) + " " + renderExpression(c.right());
        }
        if (p instanceof Predicate.In in) {
            return renderExpression(in.expression()) + " in ("
                    + in.values().stream().map(this::renderExpression).collect(Collectors.joining(", ")) + ")";
        }
        if (p instanceof Predicate.InTuple it) {
            if (!dialect.supportsMultiValueInExpr()) {
                // The dialect cannot evaluate a multi-column row-value IN (e.g. H2 mis-unifies the row
                // types). Degrade to the equivalent OR-of-ANDs — the same shape the legacy
                // AndPredicate.checkInList fallback produced when it cleared the IN-list bit key:
                // ((c1 = v11 and c2 = v12) or (c1 = v21 and c2 = v22) or ...).
                List<String> colSql = it.columns().stream().map(this::renderExpression).toList();
                String ors = it.rows().stream()
                        .map(row -> {
                            StringBuilder b = new StringBuilder("(");
                            for (int i = 0; i < colSql.size(); i++) {
                                if (i > 0) {
                                    b.append(" and ");
                                }
                                b.append(colSql.get(i)).append(" = ").append(renderExpression(row.get(i)));
                            }
                            return b.append(")").toString();
                        })
                        .collect(Collectors.joining(" or "));
                return "(" + ors + ")";
            }
            String cols = it.columns().stream().map(this::renderExpression).collect(Collectors.joining(", "));
            String rows = it.rows().stream()
                    .map(row -> "(" + row.stream().map(this::renderExpression).collect(Collectors.joining(", ")) + ")")
                    .collect(Collectors.joining(", "));
            return "(" + cols + ") in (" + rows + ")";
        }
        if (p instanceof Predicate.IsNull n) {
            return renderExpression(n.expression()) + (n.negated() ? " is not null" : " is null");
        }
        if (p instanceof Predicate.Like l) {
            String base = renderExpression(l.expression()) + (l.negated() ? " not like " : " like ")
                    + renderExpression(l.pattern());
            return base + l.escape().map(c -> " escape '" + c + "'").orElse("");
        }
        if (p instanceof Predicate.Between b) {
            return renderExpression(b.expression()) + (b.negated() ? " not between " : " between ")
                    + renderExpression(b.low()) + " and " + renderExpression(b.high());
        }
        if (p instanceof Predicate.Not not) {
            return "not (" + renderPredicate(not.operand()) + ")";
        }
        if (p instanceof Predicate.Exists ex) {
            // Compact like every nested context; nested bind parameters accumulate in placeholder order.
            return (ex.negated() ? "not exists (" : "exists (")
                    + renderInternal(ex.query(), RenderOptions.compact()).sql() + ")";
        }
        if (p instanceof Predicate.Constant c) {
            return constantPredicate(c.value());
        }
        if (p instanceof Predicate.Connective conn) {
            boolean and = conn instanceof Predicate.And;
            if (conn.operands().isEmpty()) {
                return constantPredicate(and);
            }
            return "(" + renderPredicateList(conn.operands(), and ? " and " : " or ") + ")";
        }
        if (p instanceof Predicate.Raw r) {
            return r.sql();
        }
        if (p instanceof Predicate.Regexp re) {
            // The dialect produces the whole fragment (null-guard + optional UPPER + regex operator). Use the
            // SELECT alias (c5) for the source ONLY when the dialect requiresHavingAlias (MySQL family):
            // most dialects (PostgreSQL, Oracle, MSSQL, Derby) do NOT resolve select-list aliases in HAVING
            // — an unconditional alias swap for computed (RawVariant) sources rendered `cast(c5 as text) ~ …`
            // and failed at execution there. A computed or plain-column source re-inlines its expression
            // (which is part of GROUP BY, so it is legal in HAVING on every dialect), matching the legacy
            // string channel (MatchingSqlCompiler.compile: alias iff requiresHavingAlias). MySQL byte-neutral:
            // requiresHavingAlias=true keeps the alias arm.
            String alias = dialect.requiresHavingAlias()
                ? aliasForHavingSource(re.source()) : null;
            String srcSql = alias != null ? alias : renderExpression(re.source());
            String frag = dialect.regexGenerator().generateRegularExpression(srcSql, re.pattern())
                    .orElseThrow(() -> new IllegalArgumentException("dialect has no regular-expression support"));
            // Legacy RolapNativeSql.MatchingSqlCompiler wraps a negated match as NOT(<frag>) — uppercase, no
            // space — so match that exactly (a lowercase `not (` diverges from the legacy HAVING string).
            return re.negated() ? "NOT(" + frag + ")" : frag;
        }
        throw new IllegalArgumentException("unsupported predicate: " + p);
    }

    /**
     * The SELECT alias of the projection whose expression equals {@code source}, or {@code null} when not in
     * a HAVING render or no projection matches (the caller then renders the source expression directly).
     */
    private String aliasForHavingSource(SqlExpression source) {
        if (havingAliasProjections == null) {
            return null;
        }
        for (int i = 0; i < havingAliasProjections.size(); i++) {
            org.eclipse.daanse.sql.statement.api.model.Projection p = havingAliasProjections.get(i);
            if (source.equals(p.expression())) {
                return effectiveAlias(p, i);
            }
        }
        return null;
    }

    /**
     * The one spelling of a constant truth value — {@code 1 = 1} (true) / {@code 1 = 0} (false), no
     * parentheses. Used by {@link Predicate.Constant} and by an empty {@link Predicate.Connective}.
     */
    private static String constantPredicate(boolean value) {
        return value ? "1 = 1" : "1 = 0";
    }

    private static String symbol(ComparisonOperator op) {
        return op.symbol();
    }

    // ---- set operations --------------------------------------------------------

    private RenderedSql renderSet(SetOperation so, RenderOptions options) {
        if (so.inputs().size() < 2) {
            throw new IllegalArgumentException("set operation needs at least two inputs");
        }
        // Render each input exactly once (so its parameters accumulate once, in order). Inputs are
        // nested contexts: compact, except in diagnostic mode (formatted + comments) where they render
        // formatted with comments and the set keyword sits on its own line between them.
        List<RenderedSql> rendered = new ArrayList<>();
        for (Statement in : so.inputs()) {
            rendered.add(renderInternal(in, nestedOptions(options)));
        }
        // The plain (duplicate-eliminating) UNION spelling is a dialect decision:
        // ClickHouse requires the explicit "union distinct" (EXPECTED_ALL_OR_DISTINCT);
        // every other dialect keeps the byte-identical bare "union".
        String keyword = so.op() == SetOperation.SetOp.UNION
                ? dialect.unionDistinctKeyword()
                : so.op().keyword();
        String sep = nestedFormatted(options)
                ? System.lineSeparator() + keyword + System.lineSeparator()
                : " " + keyword + " ";
        StringBuilder sb = new StringBuilder(rendered.stream().map(RenderedSql::sql)
                .collect(Collectors.joining(sep)));
        if (!so.orderKeys().isEmpty()) {
            List<Projection> none = List.of();
            sb.append(" order by ").append(
                    so.orderKeys().stream().map(k -> renderOrderKey(k, none)).collect(Collectors.joining(", ")));
        }
        so.rowLimit().ifPresent(rl -> sb.append(dialect.paginationGenerator().paginate(rl.maxRows(), rl.offset())));
        // Column types are those of the first input.
        return RenderedSql.of(sb.toString(), rendered.get(0).columnTypes());
    }
}
