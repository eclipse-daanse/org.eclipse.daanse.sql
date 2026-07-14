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
package org.eclipse.daanse.sql.dialect.db.common;

import java.util.Locale;
import java.util.function.Function;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.api.DialectFactory;
import org.eclipse.daanse.sql.dialect.api.DialectInitData;

public abstract class AbstractDialectFactory<T extends Dialect> implements DialectFactory {

    @Override
    public Dialect createDialect(DialectInitData init) {
        return getConstructorFunction().apply(init);
    }

    /**
     * @param productSubstring lower-cased substring to look for in
     *                         {@link DialectInitData#productName()}
     * @param init             captured snapshot
     * @return true if the substring is contained in the (case-insensitive) product
     *         name
     */
    protected static boolean isDatabase(String productSubstring, DialectInitData init) {
        String name = init.productName();
        if (name == null)
            return false;
        return name.toLowerCase(Locale.ROOT).contains(productSubstring.toLowerCase(Locale.ROOT));
    }

    public abstract Function<DialectInitData, T> getConstructorFunction();
}
