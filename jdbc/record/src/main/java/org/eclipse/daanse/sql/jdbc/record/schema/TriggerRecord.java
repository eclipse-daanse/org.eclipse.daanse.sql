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

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.model.schema.Trigger;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming;
import org.eclipse.daanse.sql.model.schema.TriggerReference;

public record TriggerRecord(
        TriggerReference reference,
        TriggerTiming timing,
        List<TriggerEvent> events,
        Optional<String> whenCondition,
        Optional<String> body,
        Optional<String> fullDefinition,
        Optional<String> orientation) implements Trigger {

    public TriggerRecord {
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("events must not be empty");
        }
        events = List.copyOf(events);
    }

    /** Single-event trigger without a {@code WHEN} condition. */
    public TriggerRecord(TriggerReference reference, TriggerTiming timing, TriggerEvent event, Optional<String> body,
            Optional<String> fullDefinition, Optional<String> orientation) {
        this(reference, timing, List.of(event), Optional.empty(), body, fullDefinition, orientation);
    }

    @Override
    public TriggerEvent event() {
        return events.get(0);
    }
}
