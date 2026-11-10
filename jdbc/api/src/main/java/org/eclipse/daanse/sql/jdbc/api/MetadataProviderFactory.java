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
package org.eclipse.daanse.sql.jdbc.api;

/**
 * Creates the engine-specific {@link MetadataProvider} for a database product — the reading-side
 * counterpart of the SQL dialect factories. Implementations are published as OSGi services, one
 * per engine; a consumer selects by {@link #supports(String)} against
 * {@code DatabaseMetaData.getDatabaseProductName()}.
 */
public interface MetadataProviderFactory {

    /**
     * Whether this factory serves the given database product
     * ({@code DatabaseMetaData.getDatabaseProductName()}); implementations match
     * case-insensitively and must disambiguate lookalike products (e.g. MariaDB reporting as
     * "MySQL").
     */
    boolean supports(String databaseProductName);

    MetadataProvider createProvider();
}
