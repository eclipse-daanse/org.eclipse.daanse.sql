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
 *   SmartCity Jena, Stefan Bischof - initial
 *
 */
package org.eclipse.daanse.sql.dialect.api;

import java.sql.SQLException;

@SuppressWarnings("serial")
public class DialectException extends RuntimeException {

    /**
     * Wrap a {@link SQLException} from JDBC metadata access with a contextual
     * message.
     */
    public DialectException(String msg, SQLException e) {
        super(msg, e);
    }

    /**
     * Wrap any underlying exception so callers see a single dialect-typed
     * throwable.
     */
    public DialectException(Exception e) {
        super(e);
    }
}
