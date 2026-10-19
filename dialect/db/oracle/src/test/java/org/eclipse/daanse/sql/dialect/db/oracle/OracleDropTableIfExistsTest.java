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
 */
package org.eclipse.daanse.sql.dialect.db.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.junit.jupiter.api.Test;

/**
 * Oracle drops a table only if it exists from version 23 on: before, the plain {@code DROP TABLE} of a missing table
 * fails with ORA-00942, which aborted every CSV fixture of the ROLAP suite on Oracle Free 23ai (database run
 * 18.10.2026).
 */
class OracleDropTableIfExistsTest {

    private static OracleDialect dialect(int major) {
        return new OracleDialect(DialectInitData.ansiDefaults().withVersion(major, 0));
    }

    @Test
    void oracle23DropsATableIfItExists() {
        assertThat(dialect(23).supportsDropTableIfExists()).isTrue();
        assertThat(dialect(23).dropTable("S", "T", true)).isEqualTo("DROP TABLE IF EXISTS \"S\".\"T\"");
    }

    @Test
    void oracle19AndADialectWithoutAVersionDropPlainly() {
        assertThat(dialect(19).supportsDropTableIfExists()).isFalse();
        assertThat(new OracleDialect().supportsDropTableIfExists()).isFalse();
    }
}
