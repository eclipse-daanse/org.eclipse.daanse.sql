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

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

public interface PaginationGenerator {

    /**
     * Limit fragment that must be placed immediately after {@code SELECT [DISTINCT]}, used by
     * dialects that express row limits as a leading clause (e.g. SQL Server / Sybase
     * {@code TOP n}) rather than a trailing clause.
     * <p>
     * The default is {@link Optional#empty()}, meaning the dialect uses the trailing
     * {@link #paginate(OptionalLong, OptionalLong)} form instead. A dialect must implement
     * <em>either</em> this leading form <em>or</em> the trailing form, never both for the
     * same query.
     *
     * @param limit  maximum rows to return (must be ≥ 0 if present)
     * @param offset rows to skip (must be ≥ 0 if present)
     * @return the {@code SELECT}-prefix fragment (without trailing space), or empty when the
     *         dialect paginates with a trailing clause
     */
    default Optional<String> selectPrefix(OptionalLong limit, OptionalLong offset) {
        return Optional.empty();
    }

    /**
     * @param limit  maximum rows to return (must be ≥ 0 if present)
     * @param offset rows to skip (must be ≥ 0 if present)
     * @return suffix to append after {@code ORDER BY …} (may be empty, never null)
     */
    default String paginate(OptionalLong limit, OptionalLong offset) {
        StringBuilder sb = new StringBuilder();
        offset.ifPresent(o -> {
            if (o < 0)
                throw new IllegalArgumentException("offset must be >= 0");
            sb.append(" OFFSET ").append(o).append(" ROWS");
        });
        limit.ifPresent(l -> {
            if (l < 0)
                throw new IllegalArgumentException("limit must be >= 0");
            sb.append(" FETCH NEXT ").append(l).append(" ROWS ONLY");
        });
        return sb.toString();
    }

    default boolean supportsOffset() {
        return true;
    }

    /** Convenience for callers that always want to paginate. */
    default Optional<String> paginate(long limit) {
        return Optional.of(paginate(OptionalLong.of(limit), OptionalLong.empty()));
    }

    /** Paging clause with placeholders; the returned order lists which parameter comes first. */
    default PreparedPaging paginatePrepared(boolean withLimit, boolean withOffset) {
        if (withLimit && withOffset) {
            return new PreparedPaging(
                "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                List.of(PagingParam.OFFSET, PagingParam.LIMIT)
            );
        } else if (withLimit) {
            return new PreparedPaging(
                "FETCH NEXT ? ROWS ONLY",
                List.of(PagingParam.LIMIT)
            );
        } else if (withOffset) {
            return new PreparedPaging(
                "OFFSET ? ROWS",
                List.of(PagingParam.OFFSET)
            );
        } else {
            return new PreparedPaging("", List.of());
        }
    }

    record PreparedPaging(String clause, List<PagingParam> order) {}
    enum PagingParam { LIMIT, OFFSET }
}
