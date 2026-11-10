/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.sql.dialect.db.mysql;

import java.util.function.Function;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.api.DialectFactory;
import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.sql.dialect.api.DialectName;
import org.eclipse.daanse.sql.dialect.db.common.AbstractDialectFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

@Component(service = DialectFactory.class, scope = ServiceScope.SINGLETON)
@DialectName("MYSQL")
public class MySqlDialectFactory extends AbstractDialectFactory<MySqlDialect> {

    @Override
    public Dialect createDialect(DialectInitData init) {
        if (MySqlDialect.looksLikeInfobright(init)) {
            throw new IllegalStateException("Snapshot looks like Infobright (productVersion=" + init.productVersion()
                    + "); use InfobrightDialectFactory");
        }
        return super.createDialect(init);
    }

    @Override
    public Function<DialectInitData, MySqlDialect> getConstructorFunction() {
        return MySqlDialect::new;
    }

}
