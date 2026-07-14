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
package org.eclipse.daanse.sql.jdbc.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;
import org.eclipse.daanse.sql.jdbc.api.MetadataProviderFactory;

/**
 * Static resolver over the engine {@link MetadataProviderFactory} implementations for
 * non-OSGi consumers (e.g. CWM snapshot code): picks the engine-specific
 * {@link MetadataProvider} by database product name. MariaDB is checked BEFORE MySQL —
 * a MariaDB server on the MySQL driver may report "MySQL", the reverse never happens.
 */
public final class MetadataProviders {

    private static final List<MetadataProviderFactory> FACTORIES = List.of(
        new MariaDbMetadataProviderFactory(),
        new MySqlMetadataProviderFactory(),
        new PostgreSqlMetadataProviderFactory(),
        new MicrosoftSqlServerMetadataProviderFactory(),
        new OracleMetadataProviderFactory(),
        new H2MetadataProviderFactory());

    private MetadataProviders() {
    }

    public static Optional<MetadataProvider> forProductName(String databaseProductName) {
        return FACTORIES.stream()
            .filter(f -> f.supports(databaseProductName))
            .findFirst()
            .map(MetadataProviderFactory::createProvider);
    }

    /** Resolves via {@code connection.getMetaData().getDatabaseProductName()}. */
    public static Optional<MetadataProvider> forConnection(Connection connection) throws SQLException {
        return forProductName(connection.getMetaData().getDatabaseProductName());
    }
}
