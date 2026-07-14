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
package org.eclipse.daanse.sql.dialect.api;

/**
 * When the SQL generator should wrap identifiers in the engine's quote
 * characters. Trades safety against readability of emitted SQL.
 */
public enum IdentifierQuotingPolicy {

    /** Always quote — safe default; preserves case and accepts any character. */
    ALWAYS,

    /**
     * Quote only when needed — reserved word, mixed case, or special characters.
     */
    WHEN_NEEDED,

    /**
     * Never quote — caller guarantees identifiers are safe; smallest emitted SQL.
     */
    NEVER
}
