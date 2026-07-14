/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.api.generator;

import org.eclipse.daanse.sql.model.type.Datatype;

public interface OrderByGenerator extends LiteralQuoter {

    String CASE_WHEN = "CASE WHEN ";
    String DESC = " DESC";
    String ASC = " ASC";

    /**
     * @param nullable         whether the expression can be null
     * @param ascending        true for ASC, false for DESC
     * @param collateNullsLast true to put NULLs last, false for NULLs first
     */
    default StringBuilder generateOrderItem(CharSequence expr, boolean nullable, boolean ascending,
            boolean collateNullsLast) {
        if (nullable) {
            return generateOrderByNulls(expr, ascending, collateNullsLast);
        }
        return new StringBuilder(expr).append(ascending ? ASC : DESC);
    }

    /**
     * @param orderValue       sentinel value (quoted via {@link #quote})
     * @param datatype         datatype of {@code orderValue} for proper quoting
     * @param ascending        true for ASC, false for DESC
     * @param collateNullsLast true to put the sentinel last, false for first
     */
    default StringBuilder generateOrderItemForOrderValue(CharSequence expr, String orderValue, Datatype datatype,
            boolean ascending, boolean collateNullsLast) {
        StringBuilder sb = new StringBuilder(CASE_WHEN).append(expr).append(" = ");
        quote(sb, orderValue, datatype);
        sb.append(collateNullsLast ? " THEN 1 ELSE 0 END, " : " THEN 0 ELSE 1 END, ").append(expr);
        return sb.append(ascending ? ASC : DESC);
    }

    default StringBuilder generateOrderByNulls(CharSequence expr, boolean ascending, boolean collateNullsLast) {
        StringBuilder sb = new StringBuilder(CASE_WHEN).append(expr)
                .append(collateNullsLast ? " IS NULL THEN 1 ELSE 0 END, " : " IS NULL THEN 0 ELSE 1 END, ")
                .append(expr);
        return sb.append(ascending ? ASC : DESC);
    }

    default StringBuilder generateOrderByNullsAnsi(CharSequence expr, boolean ascending, boolean collateNullsLast) {
        return new StringBuilder(expr).append(ascending ? ASC : DESC)
                .append(collateNullsLast ? " NULLS LAST" : " NULLS FIRST");
    }
}
