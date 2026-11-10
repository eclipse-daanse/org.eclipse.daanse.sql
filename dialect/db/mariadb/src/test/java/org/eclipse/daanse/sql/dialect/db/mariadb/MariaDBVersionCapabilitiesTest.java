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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.junit.jupiter.api.Test;

/**
 * MariaDB reports two-digit major versions ("10.x"/"11.x"). The inherited MySQL
 * capability checks must compare numerically — a lexicographic productVersion
 * comparison classifies "11.4" &lt; "5.7"/"8.0" and flips the capabilities.
 */
class MariaDBVersionCapabilitiesTest {

    private static MariaDBDialect dialect(int major, int minor) {
        return new MariaDBDialect(DialectInitData.ansiDefaults()
                .withQuoteIdentifierString("`").withVersion(major, minor));
    }

    @Test
    void twoDigitMajorIsAtLeast57ForOrderByAlias() {
        assertTrue(dialect(11, 4).requiresOrderByAlias());
        assertTrue(dialect(10, 6).requiresOrderByAlias());
    }

    @Test
    void twoDigitMajorSupportsPercentiles() {
        assertTrue(dialect(11, 4).supportsPercentileDisc());
        assertTrue(dialect(11, 4).supportsPercentileCont());
        assertTrue(dialect(10, 6).supportsPercentileDisc());
    }

    @Test
    void twoDigitMajorAllowsFromQuery() {
        assertTrue(dialect(11, 4).allowsFromQuery());
    }

    @Test
    void oldMySqlStyleVersionsStillGateCorrectly() {
        assertFalse(dialect(5, 6).requiresOrderByAlias());
        assertTrue(dialect(5, 7).requiresOrderByAlias());
        assertFalse(dialect(5, 7).supportsPercentileDisc());
        assertTrue(dialect(8, 0).supportsPercentileDisc());
    }
}
