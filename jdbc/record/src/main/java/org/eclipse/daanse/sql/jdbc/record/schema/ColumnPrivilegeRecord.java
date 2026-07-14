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
package org.eclipse.daanse.sql.jdbc.record.schema;

import java.util.Optional;

import org.eclipse.daanse.sql.jdbc.api.schema.ColumnPrivilege;
import org.eclipse.daanse.sql.model.schema.ColumnReference;

public record ColumnPrivilegeRecord(
        ColumnReference column,
        Optional<String> grantor,
        String grantee,
        String privilege,
        Optional<String> isGrantable) implements ColumnPrivilege {
}
