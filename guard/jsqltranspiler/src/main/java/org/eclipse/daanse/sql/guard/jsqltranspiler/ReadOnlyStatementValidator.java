/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
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

package org.eclipse.daanse.sql.guard.jsqltranspiler;

import java.util.Optional;

import net.sf.jsqlparser.statement.ParenthesedStatement;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.statement.delete.ParenthesedDelete;
import net.sf.jsqlparser.statement.insert.ParenthesedInsert;
import net.sf.jsqlparser.statement.update.ParenthesedUpdate;
import net.sf.jsqlparser.util.TablesNamesFinder;

/**
 * Walks a parsed {@link Select} tree and rejects statements that parse as a
 * {@code Select} yet carry a side effect. Several SQL constructs pass the naive
 * {@code instanceof Select} gate but are not read-only:
 * <ul>
 * <li>a data-modifying CTE, e.g. {@code WITH x AS (UPDATE foo ...) SELECT ...} —
 * on PostgreSQL/MariaDB the wrapped {@code UPDATE}/{@code INSERT}/{@code DELETE}
 * executes. The upstream resolver does not populate its update/insert column
 * lists for a {@code WithItem}, so it cannot be relied on to detect this;</li>
 * <li>{@code SELECT ... INTO newtable} — creates/writes a table
 * (PostgreSQL, SQL Server);</li>
 * <li>{@code SELECT ... FOR UPDATE}/{@code FOR SHARE} — takes row locks.</li>
 * </ul>
 * The validator reuses {@link TablesNamesFinder}'s full recursive traversal
 * (subqueries, joins, set operations, nested {@code WITH} clauses). A
 * {@code WithItem} is only permitted to wrap a {@link ParenthesedSelect}; a
 * {@code SELECT INTO} or a {@code FOR UPDATE}/{@code FOR SHARE} clause anywhere
 * in the tree is a violation.
 */
class ReadOnlyStatementValidator extends TablesNamesFinder<Void> {

    static final String DELETE_IS_NOT_PERMITTED = "DELETE is not permitted.";
    static final String UPDATE_IS_NOT_PERMITTED = "UPDATE is not permitted.";
    static final String INSERT_IS_NOT_PERMITTED = "INSERT is not permitted.";
    static final String STATEMENT_IS_NOT_PERMITTED = "Statement is not permitted.";
    static final String SELECT_INTO_IS_NOT_PERMITTED = "SELECT INTO is not permitted.";
    static final String ROW_LOCKING_IS_NOT_PERMITTED = "Row locking (FOR UPDATE/SHARE) is not permitted.";

    private String violation;

    /**
     * Runs the read-only traversal over the given select and returns the first
     * violation message, or an empty optional when the statement is read-only.
     */
    Optional<String> firstViolation(Select select) {
        violation = null;
        // drives the recursive traversal; the returned table set is irrelevant
        getTables((Statement) select);
        return Optional.ofNullable(violation);
    }

    private void record(String message) {
        if (violation == null) {
            violation = message;
        }
    }

    /** A FOR UPDATE / FOR SHARE clause can hang off any Select node. */
    private void checkRowLocking(Select select) {
        if (select.getForMode() != null || select.getForUpdateTable() != null) {
            record(ROW_LOCKING_IS_NOT_PERMITTED);
        }
    }

    @Override
    public <S> Void visit(PlainSelect plainSelect, S context) {
        if (plainSelect.getIntoTables() != null && !plainSelect.getIntoTables().isEmpty()
                || plainSelect.getIntoTempTable() != null) {
            record(SELECT_INTO_IS_NOT_PERMITTED);
        }
        checkRowLocking(plainSelect);
        return super.visit(plainSelect, context);
    }

    @Override
    public <S> Void visit(SetOperationList setOpList, S context) {
        checkRowLocking(setOpList);
        return super.visit(setOpList, context);
    }

    @Override
    public <S> Void visit(WithItem<?> withItem, S context) {
        ParenthesedStatement statement = withItem.getParenthesedStatement();
        if (statement instanceof ParenthesedSelect) {
            // read-only CTE body: keep descending so nested WITH clauses are inspected too.
            // The base implementation resolves getSelect(), which is only valid here.
            return super.visit(withItem, context);
        }
        // Anything other than a ParenthesedSelect is a data-modifying (or otherwise
        // unexpected) CTE. Record it and do NOT descend: getSelect() would throw.
        if (violation == null) {
            if (statement instanceof ParenthesedUpdate) {
                violation = UPDATE_IS_NOT_PERMITTED;
            } else if (statement instanceof ParenthesedInsert) {
                violation = INSERT_IS_NOT_PERMITTED;
            } else if (statement instanceof ParenthesedDelete) {
                violation = DELETE_IS_NOT_PERMITTED;
            } else {
                violation = STATEMENT_IS_NOT_PERMITTED;
            }
        }
        return null;
    }
}
