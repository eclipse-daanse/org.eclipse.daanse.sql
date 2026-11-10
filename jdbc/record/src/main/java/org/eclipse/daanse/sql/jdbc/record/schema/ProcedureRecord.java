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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.jdbc.api.schema.Procedure;
import org.eclipse.daanse.sql.jdbc.api.schema.ProcedureColumn;
import org.eclipse.daanse.sql.jdbc.api.schema.ProcedureReference;

public record ProcedureRecord(
        ProcedureReference reference,
        ProcedureType procedureType,
        Optional<String> remarks,
        List<ProcedureColumn> columns,
        Optional<String> body,
        Optional<String> fullDefinition,
        Optional<Instant> lastModified) implements Procedure {
}
