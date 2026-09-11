/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.h2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** CANARY ONLY - deliberately failing, to prove the matrix gates the build. */
class CanaryGateTest {

    @Test
    void deliberateFailureProvesTheGate() {
        assertThat("canary").isEqualTo("this assertion must fail");
    }
}
