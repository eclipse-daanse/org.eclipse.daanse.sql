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
package org.eclipse.daanse.sql.jdbc.metadata;

import java.sql.Connection;
import java.sql.SQLException;

import org.h2.api.Trigger;

public class TestAuditTrigger implements Trigger {

    @Override
    public void init(Connection conn, String schemaName, String triggerName, String tableName, boolean before, int type)
            throws SQLException {
        // no-op
    }

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        // no-op
    }

    @Override
    public void close() throws SQLException {
        // no-op
    }

    @Override
    public void remove() throws SQLException {
        // no-op
    }
}
