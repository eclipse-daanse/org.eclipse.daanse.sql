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
package org.eclipse.daanse.sql.model.schema;

import java.util.List;
import java.util.Optional;

public interface Trigger extends Named {

    TriggerReference reference();

    @Override
    default String name() {
        return reference().name();
    }

    /** Convenience: the table this trigger fires on. */
    default TableReference table() {
        return reference().table();
    }

    TriggerTiming timing();

    TriggerEvent event();

    /**
     * All DML events this trigger fires on, in catalog order. Multi-event
     * triggers ({@code BEFORE INSERT OR UPDATE}) list every event here while
     * {@link #event()} keeps returning the first one.
     *
     * @return the events, never empty
     */
    default List<TriggerEvent> events() {
        return List.of(event());
    }

    /**
     * @return the {@code WHEN} condition guarding the trigger action (without
     *         the {@code WHEN} keyword and outer parentheses), or empty if the
     *         trigger is unconditional or the provider cannot read it
     */
    default Optional<String> whenCondition() {
        return Optional.empty();
    }

    /** @return the trigger body, or empty if not available */
    Optional<String> body();

    /** @return the full CREATE TRIGGER definition, or empty if not available */
    Optional<String> fullDefinition();

    /** @return "ROW" or "STATEMENT", or empty if not applicable */
    Optional<String> orientation();

    /** When the trigger body fires relative to the triggering DML. */
    enum TriggerTiming {
        /** Fires before the DML applies to the table. */
        BEFORE,
        /** Fires after the DML applies to the table. */
        AFTER,
        /** Fires in place of the DML; commonly used for views. */
        INSTEAD_OF
    }

    /** Which DML statement kind activates the trigger. */
    enum TriggerEvent {
        /** Fires on {@code INSERT}. */
        INSERT,
        /** Fires on {@code UPDATE}. */
        UPDATE,
        /** Fires on {@code DELETE}. */
        DELETE
    }

    /** Whether the trigger fires once per statement or once per affected row. */
    enum TriggerScope {
        /** Fires exactly once for the whole statement. */
        STATEMENT,
        /** Fires once for every row the statement touches. */
        ROW;

        /**
         * @return SQL keyword form ({@code "FOR EACH ROW"} or
         *         {@code "FOR EACH STATEMENT"})
         */
        public String forEachClause() {
            return this == ROW ? "FOR EACH ROW" : "FOR EACH STATEMENT";
        }
    }
}
