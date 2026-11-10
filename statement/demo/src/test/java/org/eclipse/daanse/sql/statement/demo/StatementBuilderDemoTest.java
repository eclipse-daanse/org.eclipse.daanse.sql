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
package org.eclipse.daanse.sql.statement.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.dialect.db.mssqlserver.MicrosoftSqlServerDialect;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Golden-SQL tests: the same dialect-free query model renders to dialect-specific SQL. These
 * are the behavioural proof that the assembler + renderer work end-to-end, and that the new
 * dialect pagination placement ({@code TOP} vs {@code FETCH}) is honoured.
 */
class StatementBuilderDemoTest {

    private final Dialect ansi = new AnsiDialect();

    @Test
    void salesByProduct_ansi() {
        String sql = new DialectSqlRenderer(ansi).render(StatementBuilderDemo.salesByProduct()).sql();
        assertEquals("""
                select "p"."name" as "c0", COUNT(*) as "cnt" \
                from "sales" as "s" join "product" as "p" on "s"."product_id" = "p"."id" \
                where "s"."amount" > 100 \
                group by "p"."name" order by "p"."name" ASC\
                """, sql);
    }

    @Test
    void topProducts_ansi_usesTrailingFetch() {
        String sql = new DialectSqlRenderer(ansi).render(StatementBuilderDemo.topProducts()).sql();
        assertEquals("""
                select "p"."name" as "c0" from "product" as "p" \
                order by "p"."name" ASC FETCH NEXT 10 ROWS ONLY\
                """, sql);
    }

    @Test
    void topProducts_sqlServer_usesLeadingTop() {
        String sql = new DialectSqlRenderer(new MicrosoftSqlServerDialect())
                .render(StatementBuilderDemo.topProducts()).sql();
        assertEquals("""
                select TOP 10 "p"."name" as "c0" from "product" as "p" \
                order by "p"."name" ASC\
                """, sql);
    }

    @Test
    void productNameUnion_ansi() {
        String sql = new DialectSqlRenderer(ansi).render(StatementBuilderDemo.productNameUnion()).sql();
        assertEquals("""
                select "a"."name" as "c0" from "product_a" as "a" \
                union all select "b"."name" as "c0" from "product_b" as "b"\
                """, sql);
    }
}
