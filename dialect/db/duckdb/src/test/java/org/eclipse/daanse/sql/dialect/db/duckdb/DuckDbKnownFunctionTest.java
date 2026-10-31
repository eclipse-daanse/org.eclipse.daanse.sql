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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.sql.dialect.db.duckdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.daanse.sql.dialect.api.generator.KnownFunction;
import org.junit.jupiter.api.Test;

/** The functions a data source view's expressions need, in DuckDB's spelling. */
class DuckDbKnownFunctionTest {

    private final DuckDbDialect d = new DuckDbDialect();

    @Test
    void knownFunctionsOfADataSourceView() {
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.MONTH_NAME, List.of("x")))
                .hasToString("monthname(x)");
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.TO_TEXT, List.of("x")))
                .hasToString("CAST(x AS VARCHAR)");
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.PAD_RIGHT, List.of("x", "2")))
                .hasToString("RPAD(x, 2, ' ')");
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.DATE_FROM_PARTS, List.of("y", "m", "d")))
                .hasToString("make_date(y, m, d)");
    }
}
