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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.sql.statement.api.model;

/**
 * A table/relation alias used in the {@code FROM} clause (its raw, unquoted
 * name).
 *
 * @param name the alias name
 */
public record TableAlias(String name) {

    public static TableAlias of(String name) {
        return new TableAlias(name);
    }
}
