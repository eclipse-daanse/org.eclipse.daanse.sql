/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.RoutineDefinition;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.RoutineParameter;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.RoutineSignature;
import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;
import org.eclipse.daanse.sql.jdbc.api.schema.Function;
import org.eclipse.daanse.sql.jdbc.api.schema.FunctionColumn;
import org.eclipse.daanse.sql.jdbc.api.schema.Procedure;
import org.eclipse.daanse.sql.jdbc.api.schema.ProcedureColumn;

/**
 * A function and a procedure on a database as a dialect writes them: created (and created again,
 * where the dialect can replace one), the function called, both read back by the metadata provider
 * with their parameters, language and body, and dropped by their signature. Names are compared
 * without regard to case, as some databases fold them.
 */
public final class RoutineRoundTrip {

    private RoutineRoundTrip() {
        // static helpers only
    }

    /**
     * @param call               a query that calls the function and returns {@code expected}
     * @param procedureModes     the procedure's parameters as the provider reports them,
     *                           {@code name MODE} in order: SQL Server reports an INOUT parameter as
     *                           OUT, as its OUTPUT parameters are
     * @param functionBodyPart   a part of the function's body the provider reads back
     * @param procedureBodyPart  a part of the procedure's body the provider reads back
     */
    public static void assertRoutineRoundTrip(Connection conn, DdlGenerator ddl, MetadataProvider provider,
            RoutineDefinition function, RoutineDefinition procedure, String call, int expected,
            List<String> procedureModes, String functionBodyPart, String procedureBodyPart) throws SQLException {
        String schema = function.schemaName();
        try (Statement s = conn.createStatement()) {
            executeTwiceWhereReplaced(s, ddl.createFunction(function, false).orElseThrow(),
                    ddl.createFunction(function, true).orElseThrow());
            executeTwiceWhereReplaced(s, ddl.createProcedure(procedure, false).orElseThrow(),
                    ddl.createProcedure(procedure, true).orElseThrow());
            try (ResultSet rs = s.executeQuery(call)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).as("the function's result").isEqualTo(expected);
            }

            assertThat(provider.getAllFunctions(conn, null, schema))
                    .filteredOn(f -> f.reference().name().equalsIgnoreCase(function.name())).singleElement()
                    .satisfies(f -> {
                        assertThat(functionParameters(f)).containsExactlyElementsOf(names(function.parameters()));
                        assertThat(f.language()).hasValueSatisfying(
                                language -> assertThat(language).isEqualToIgnoringCase(function.language()));
                        assertThat(f.body().or(f::fullDefinition).orElseThrow())
                                .containsIgnoringWhitespaces(functionBodyPart);
                    });
            assertThat(provider.getAllProcedures(conn, null, schema))
                    .filteredOn(p -> p.reference().name().equalsIgnoreCase(procedure.name())).singleElement()
                    .satisfies(p -> {
                        assertThat(procedureParameters(p)).containsExactlyElementsOf(procedureModes);
                        assertThat(p.language()).hasValueSatisfying(
                                language -> assertThat(language).isEqualToIgnoringCase(procedure.language()));
                        assertThat(p.body().or(p::fullDefinition).orElseThrow())
                                .containsIgnoringWhitespaces(procedureBodyPart);
                    });

            RoutineSignature functionSignature = new RoutineSignature(schema, function.name(),
                    function.parameters().stream().map(RoutineParameter::type).toList());
            RoutineSignature procedureSignature = new RoutineSignature(schema, procedure.name(),
                    procedure.parameters().stream().map(RoutineParameter::type).toList());
            executeTwiceWhereReplaced(s, ddl.dropFunction(functionSignature, false).orElseThrow(),
                    ddl.dropFunction(functionSignature, true).orElseThrow());
            executeTwiceWhereReplaced(s, ddl.dropProcedure(procedureSignature, false).orElseThrow(),
                    ddl.dropProcedure(procedureSignature, true).orElseThrow());
            assertThat(provider.getAllFunctions(conn, null, schema))
                    .noneMatch(f -> f.reference().name().equalsIgnoreCase(function.name()));
            assertThat(provider.getAllProcedures(conn, null, schema))
                    .noneMatch(p -> p.reference().name().equalsIgnoreCase(procedure.name()));
        }
    }

    /** {@code first}, and {@code second} too where it is another statement, as one that replaces. */
    private static void executeTwiceWhereReplaced(Statement s, String first, String second) throws SQLException {
        s.execute(first);
        if (!second.equals(first)) {
            s.execute(second);
        }
    }

    /** The parameters, {@code name MODE}, without a function's return value. */
    private static List<String> functionParameters(Function function) {
        return function.columns().stream().filter(c -> !c.name().isBlank())
                .filter(c -> c.columnType() == FunctionColumn.ColumnType.IN
                        || c.columnType() == FunctionColumn.ColumnType.OUT
                        || c.columnType() == FunctionColumn.ColumnType.INOUT)
                .sorted(Comparator.comparingInt(FunctionColumn::ordinalPosition))
                .map(c -> c.name().toLowerCase(Locale.ROOT) + " " + c.columnType()).toList();
    }

    private static List<String> procedureParameters(Procedure procedure) {
        return procedure.columns().stream().filter(c -> !c.name().isBlank())
                .sorted(Comparator.comparingInt(ProcedureColumn::ordinalPosition))
                .map(c -> c.name().toLowerCase(Locale.ROOT) + " " + c.columnType()).toList();
    }

    private static List<String> names(List<RoutineParameter> parameters) {
        return parameters.stream().map(p -> p.name().toLowerCase(Locale.ROOT) + " " + p.mode()).toList();
    }
}
