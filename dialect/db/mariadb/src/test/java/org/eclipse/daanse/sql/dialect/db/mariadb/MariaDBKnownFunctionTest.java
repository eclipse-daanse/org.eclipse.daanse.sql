/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.mariadb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.daanse.sql.dialect.api.generator.KnownFunction;
import org.junit.jupiter.api.Test;

/** MariaDB extends MySqlDialect; it must inherit the MySQL KnownFunction spellings. */
class MariaDBKnownFunctionTest {

    private final MariaDBDialect d = new MariaDBDialect();

    @Test
    void inherits_mysql_known_function_overrides() {
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.CONCAT, List.of("a", "b")))
                .hasToString("CONCAT(a, b)");
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.INDEX_OF, List.of("n", "h")))
                .hasToString("LOCATE(n, h)");
        assertThat(d.functionGenerator().generateKnownFunction(KnownFunction.LENGTH, List.of("x")))
                .hasToString("CHAR_LENGTH(x)");
    }
}
