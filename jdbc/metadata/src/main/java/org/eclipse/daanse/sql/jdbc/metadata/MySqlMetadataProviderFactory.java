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

import java.util.Locale;

import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;
import org.eclipse.daanse.sql.jdbc.api.MetadataProviderFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

/** The MySQL (excluding MariaDB, which reports its own product name on the MariaDB driver) {@link MetadataProviderFactory} (product-name substring match). */
@Component(service = MetadataProviderFactory.class, scope = ServiceScope.SINGLETON)
public class MySqlMetadataProviderFactory implements MetadataProviderFactory {

    @Override
    public boolean supports(String databaseProductName) {
        if (databaseProductName == null) {
            return false;
        }
        String lower = databaseProductName.toLowerCase(Locale.ROOT);
        return lower.contains("mysql") && !lower.contains("mariadb");
    }

    @Override
    public MetadataProvider createProvider() {
        return new MySqlMetadataProvider();
    }
}
