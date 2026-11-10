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

import org.eclipse.daanse.sql.jdbc.api.schema.Sequence;
import org.eclipse.daanse.sql.jdbc.api.schema.SequenceReference;

public record SequenceRecord(
        SequenceReference reference,
        long startValue,
        long incrementBy,
        Optional<Long> minValue,
        Optional<Long> maxValue,
        boolean cycle,
        Optional<Long> cacheSize,
        Optional<String> dataType) implements Sequence {

}
