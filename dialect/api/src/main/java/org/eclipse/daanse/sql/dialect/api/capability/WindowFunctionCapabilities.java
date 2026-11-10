/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.sql.dialect.api.capability;

/**
 * @param percentileDisc      whether the dialect supports PERCENTILE_DISC
 *                            function
 * @param percentileCont      whether the dialect supports PERCENTILE_CONT
 *                            function
 * @param listAgg             whether the dialect supports list aggregation
 *                            (LISTAGG, GROUP_CONCAT, STRING_AGG)
 * @param nthValue            whether the dialect supports NTH_VALUE window
 *                            function
 * @param nthValueIgnoreNulls whether the NTH_VALUE function supports IGNORE
 *                            NULLS / RESPECT NULLS syntax
 */
public record WindowFunctionCapabilities(boolean percentileDisc, boolean percentileCont, boolean listAgg,
        boolean nthValue, boolean nthValueIgnoreNulls) {

    /** @return WindowFunctionCapabilities with all features enabled */
    public static WindowFunctionCapabilities full() {
        return new WindowFunctionCapabilities(true, // percentileDisc
                true, // percentileCont
                true, // listAgg
                true, // nthValue
                true // nthValueIgnoreNulls
        );
    }

    /** @return WindowFunctionCapabilities with minimal features */
    public static WindowFunctionCapabilities minimal() {
        return new WindowFunctionCapabilities(false, // percentileDisc
                false, // percentileCont
                false, // listAgg
                false, // nthValue
                false // nthValueIgnoreNulls
        );
    }

    /** @return WindowFunctionCapabilities with all features disabled */
    public static WindowFunctionCapabilities none() {
        return new WindowFunctionCapabilities(false, false, false, false, false);
    }
}
