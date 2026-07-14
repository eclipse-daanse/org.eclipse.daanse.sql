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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@code FromRaw} derived-table contract: the renderer ALWAYS emits the alias
 * (spelled {@code as} per {@code allowsFromAlias}), so producers never consult
 * {@code requiresAliasForFromQuery} — the rolap statistics probes rely on this.
 */
class FromRawAliasingTest {

    @Test
    void fromRawIsAlwaysAliased() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.project(Expressions.countStar(), null);
        q.from(From.raw("select * from foo", TableAlias.of("init")));
        String sql = new DialectSqlRenderer(new AnsiDialect()).render(q.build()).sql();
        assertEquals("select COUNT(*) as \"c0\" from (select * from foo) as \"init\"", sql);
    }

    @Test
    void fromRawAliasWithoutAsKeywordWhenDialectForbidsFromAlias() {
        AnsiDialect noAs = new AnsiDialect() {
            @Override
            public boolean allowsFromAlias() {
                return false;
            }
        };
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.project(Expressions.countStar(), null);
        q.from(From.raw("select * from foo", TableAlias.of("init")));
        String sql = new DialectSqlRenderer(noAs).render(q.build()).sql();
        assertEquals("select COUNT(*) as \"c0\" from (select * from foo) \"init\"", sql);
    }
}
