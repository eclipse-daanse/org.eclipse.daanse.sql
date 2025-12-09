/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
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

package org.eclipse.daanse.sql.guard.jsqltranspiler.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.sql.guard.api.SqlGuard;
import org.eclipse.daanse.sql.guard.api.SqlGuardFactory;
import org.eclipse.daanse.sql.guard.api.elements.DatabaseCatalog;
import org.eclipse.daanse.sql.guard.api.elements.DatabaseColumn;
import org.eclipse.daanse.sql.guard.api.elements.DatabaseSchema;
import org.eclipse.daanse.sql.guard.api.elements.DatabaseTable;
import org.eclipse.daanse.sql.guard.api.exception.GuardException;
import org.eclipse.daanse.sql.guard.api.exception.UnresolvableObjectsGuardException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.osgi.test.common.annotation.InjectService;

public class SqlGuardTest {

    private static final String FOO_FACT = "fooFact";

    private static final String VALUE = "value";

    private static final String FOO = "foo";

    private static final String NAME = "name";

    private static final String ID = "id";

    private static final String SCH = "sch";

    private static final String SQL_WITH_FUNCTION_WRONG_COLUMN = "select trim(foo.name1)  from foo";

    private static final String SQL_WITH_FUNCTION_WRONG_TABLE = "select trim(foo1.name)  from foo";

    private static final String SQL_WITH_FUNCTION = "select trim(foo.name)  from foo";

    private static final String SQL_WITH_ALLOWED_FUNCTION = "select %s(foo.name) from foo";

    private static final String SQL_WITH_ALLOWED_FUNCTION_IN_WHERE = "select foo.name from foo where %s(foo.name) = 1";

    private static final String SQL_WITH_ALLOWED_FUNCTION_IN_HAVING = "select foo.name from foo group by foo.name HAVING %s(foo.id) > 5";

    private static final String SQL_WITH_FUNCTION_EXPECTED = "SELECT Trim( foo.name ) FROM sch.foo";

    private static final String SQL_WITH_ALLOWED_FUNCTION_EXPECTED = "SELECT %s(foo.name) FROM sch.foo";

    private static final String SQL_WITH_ALLOWED_FUNCTION__IN_WHERE_EXPECTED = "SELECT foo.name FROM sch.foo WHERE %s(foo.name) = 1";

    private static final String SQL_WITH_ALLOWED_FUNCTION__IN_HAVING_EXPECTED = "SELECT foo.name FROM sch.foo GROUP BY foo.name HAVING %s(foo.id) > 5";

    private static final String SQL_WITH_HAVING_WRONG_COLUMN = """
        select %s(foo.id) from foo group by foo.name having foo.name1 = 'tets'""";

    private static final String SQL_WITH_HAVING_WRONG_TABLE1 = """
        select %s(foo.id) from foo group by foo.name having foo1.name = 'tets'""";

    private static final String SQL_WITH_HAVING1 = """
        select %s(foo.id) from foo group by foo.name having foo.name = 'tets'""";

    private static final String SQL_WITH_HAVING1_EXPECTED = """
        SELECT %s(foo.id) FROM sch.foo GROUP BY foo.name HAVING foo.name = 'tets'""";

    private static final String SQL_WITH_HAVING = """
        select %s(foo.id) from foo group by foo.name having %s(foo.id) > 5""";

    private static final String SQL_WITH_HAVING_EXPECTED = """
        SELECT %s(foo.id) FROM sch.foo GROUP BY foo.name HAVING %s(foo.id) > 5""";

    private static final String SQL_WITH_HAVING_WRONG_TABLE = """
        select %s(foo.id) from foo group by foo.name having %s(foo1.id) > 5""";

    private static final String SQL_WITH_AGG_WITH_WRONG_TABLE = """
        select %s(foo1.id) from foo group by foo.name""";

    private static final String SQL_WITH_AGG = """
        select %s(foo.id)  from foo group by foo.name""";

    private static final String SQL_WITH_AGG_EXPECTED = """
        SELECT %s(foo.id) FROM sch.foo GROUP BY foo.name""";

    private static final String SQL_WITH_GROUP = "select * from foo group by foo.id, foo.name";

    private static final String SQL_WITH_GROUP_EXPECTED = """
        SELECT sch.foo.id, sch.foo.name FROM sch.foo GROUP BY foo.id, foo.name""";

    private static final String TABLE_FOO1_DOES_NOT_EXIST_IN_THE_GIVEN_SCHEMA_SCH = "Table foo1 does not exist in the given Schema sch";

    private static final String SIMPLE_SQL_WITH_WRONG_TABLE = "select * from foo1";

    private static final String SQL_WITH_WRONG_TABLE = """
        select * from foo where foo.id in (select fooFact1.id from fooFact1)
            """;

    private static final String SQL_WITH_CUSTOM_COLUMN = """
        select *, 5 as testColumn from foo where foo.id  = 10""";

    private static final String SQL_WITH_CUSTOM_COLUMN_EXPECTED = """
        SELECT sch.foo.id, sch.foo.name, 5 AS testColumn FROM sch.foo WHERE foo.id = 10""";

    private static final String SQL_WITH_IN = """
        select * from foo where foo.id in (select fooFact.id from fooFact)""";

    private static final String SQL_WITH_IN_EXPECTED = """
        SELECT sch.foo.id, sch.foo.name FROM sch.foo WHERE foo.id IN (SELECT fooFact.id FROM fooFact)""";

    private static final String TRIPLE_SELECT_SQL = """
        SELECT * FROM ( SELECT * FROM ( SELECT * FROM foo inner join fooFact on foo.id = fooFact.id ) a ) b""";

    private static final String TRIPLE_SELECT_SQL_EXPECTED = """
        SELECT sch.b.id, sch.b.name, sch.b.id_1, sch.b.value FROM (SELECT sch.a.id, sch.a.name, sch.a.id_1, sch.a.value FROM (SELECT sch.foo.id, sch.foo.name, sch.fooFact.id, sch.fooFact.value FROM sch.foo INNER JOIN sch.fooFact ON foo.id = fooFact.id) a) b""";

    private static final String SELECT_INNER_JOIN_C_D = """
        SELECT * FROM ((SELECT * FROM foo) c inner join fooFact on c.id = fooFact.id ) d""";

    private static final String SELECT_INNER_JOIN_C_D_EXPECTED = """
        SELECT sch.d.id, sch.d.name, sch.d.id_1, sch.d.value FROM ((SELECT sch.foo.id, sch.foo.name FROM sch.foo) c INNER JOIN sch.fooFact ON c.id = fooFact.id) d""";

    private static final String SELECT_INNER_JOIN_D = """
        SELECT * FROM ( SELECT * FROM foo inner join fooFact on foo.id = fooFact.id ) d""";

    private static final String SELECT_INNER_JOIN_D_EXPECTED = """
        SELECT sch.d.id, sch.d.name, sch.d.id_1, sch.d.value FROM (SELECT sch.foo.id, sch.foo.name, sch.fooFact.id, sch.fooFact.value FROM sch.foo INNER JOIN sch.fooFact ON foo.id = fooFact.id) d""";

    private static final String SELECT_INNER_JOIN = """
        select * from foo inner join fooFact on foo.id = fooFact.id""";

    private static final String SELECT_INNER_JOIN_EXPECTED = """
        SELECT sch.foo.id, sch.foo.name, sch.fooFact.id, sch.fooFact.value FROM sch.foo INNER JOIN sch.fooFact ON foo.id = fooFact.id""";

    private static final String SELECT_FROM_FOO = "select * from foo";

    private static final String SELECT_FROM_FOO_RESULT = """
        SELECT sch.foo.id, sch.foo.name FROM sch.foo""";

    private static final List<String> AGGREGATIONS = List.of("sum", "count", "distinctcount", "avg");

    private static final List<String> ALLOWED_FUNCTIONS = List.of("DeleteAll", "InsertAll", "UpdateAll", "Modify", "deleteAll", "insertAll", "updateAll", "modify");

    private static final List<String> NOT_ALLOWED_FUNCTIONS = List.of("NotDeleteAll", "NotInsertAll", "NotUpdateAll", "NotModify", "notDeleteAll", "notInsertAll", "notUpdateAll", "notModify");

    private static Dialect dialect;

    @BeforeAll
    public static void setUp() {
        dialect = mock(Dialect.class);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation)
                throws Throwable {
                Object[] arguments = invocation.getArguments();

                if (arguments != null
                    &&
                    arguments.length > 0
                    &&
                    arguments[0] != null && arguments[1] != null && arguments[1] != null) {
                    StringBuilder sb = (StringBuilder) arguments[0];
                    String s1 = (String) arguments[1];
                    String s2 = (String) arguments[2];
                    sb.append(s1).append(".").append(s2);
                }
                return null;
            }
        }).when(dialect).quoteIdentifier(any(StringBuilder.class), any(String.class));
    }

    @Test
    void testName(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
        DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
        SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);

        String result = guard.guard(SELECT_FROM_FOO);

        assertEquals(SELECT_FROM_FOO_RESULT, result);
    }

    @Test
    void testInnerJoin(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
        DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
        SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);

        String result = guard.guard(SELECT_INNER_JOIN);
        assertEquals(SELECT_INNER_JOIN_EXPECTED, result);
    }

    @Test
    void testInnerJoin1(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
        DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
        SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);

        String result = guard.guard(SELECT_INNER_JOIN_C_D);

        assertEquals(SELECT_INNER_JOIN_C_D_EXPECTED, result);
    }

    @Test
    void testInnerJoin2(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
        DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
        SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);

        String result = guard.guard(SELECT_INNER_JOIN_D);

        assertEquals(SELECT_INNER_JOIN_D_EXPECTED, result);
    }

    @Test
    void testTripleSelect(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
        DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
        SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);

        String result = guard.guard(TRIPLE_SELECT_SQL);

        assertEquals(TRIPLE_SELECT_SQL_EXPECTED, result);
    }

    @Test
    void testWhere(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
        DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
        SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);

        String result = guard.guard(SQL_WITH_IN);

        assertEquals(SQL_WITH_IN_EXPECTED, result);
    }

    @Test
    @Disabled("https://github.com/JSQLParser/JSqlParser/issues/2291")
    void testAdditionalColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
        DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
        SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
        String result = guard.guard(SQL_WITH_CUSTOM_COLUMN);

        assertEquals(SQL_WITH_CUSTOM_COLUMN_EXPECTED, result);
    }

    @Test
    void testUndefinedTable(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
        DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
        SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);

        assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_WITH_WRONG_TABLE));

    }

    @Test
    void test(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
        DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
        SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> guard.guard(SIMPLE_SQL_WITH_WRONG_TABLE));
        assertEquals(TABLE_FOO1_DOES_NOT_EXIST_IN_THE_GIVEN_SCHEMA_SCH, thrown.getMessage());
    }

    @Test
    void testGroup(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
        DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();

        SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);

        String result = guard.guard(SQL_WITH_GROUP);

        assertEquals(SQL_WITH_GROUP_EXPECTED, result);
    }

    @Test
    void testGroupAggregation(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {

        DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
        SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, AGGREGATIONS, dialect);


        for (String agg : AGGREGATIONS) {
            String result = guard.guard(String.format(SQL_WITH_AGG, agg));
            assertEquals(String.format(SQL_WITH_AGG_EXPECTED, agg), result);

            assertThrows(UnresolvableObjectsGuardException.class,
                () -> guard.guard(String.format(SQL_WITH_AGG_WITH_WRONG_TABLE, agg)));

            result = guard.guard(String.format(SQL_WITH_HAVING, agg, agg));
            assertEquals(String.format(SQL_WITH_HAVING_EXPECTED, agg, agg), result);

            assertThrows(UnresolvableObjectsGuardException.class,
                () -> guard.guard(String.format(SQL_WITH_HAVING_WRONG_TABLE, agg, agg)));

            result = guard.guard(String.format(SQL_WITH_HAVING1, agg));
            assertEquals(String.format(SQL_WITH_HAVING1_EXPECTED, agg), result);

            assertThrows(UnresolvableObjectsGuardException.class,
                () -> guard.guard(String.format(SQL_WITH_HAVING_WRONG_TABLE1, agg)));

            assertThrows(UnresolvableObjectsGuardException.class, () ->

                guard.guard(String.format(SQL_WITH_HAVING_WRONG_COLUMN, agg)));

        }
    }

    @Test
    void testAllowedFunctions(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {

        DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
        SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, ALLOWED_FUNCTIONS, dialect);
        for (String fun : ALLOWED_FUNCTIONS) {
            String result = guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION, fun));

            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION_EXPECTED, fun), result);

            result = guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_WHERE, fun));

            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION__IN_WHERE_EXPECTED, fun), result);

            result = guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_HAVING, fun));

            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION__IN_HAVING_EXPECTED, fun), result);
        }
        //allowed all
        guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(".*"), dialect);
        for (String fun : ALLOWED_FUNCTIONS) {
            String result = guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION, fun));

            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION_EXPECTED, fun), result);

            result = guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_WHERE, fun));

            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION__IN_WHERE_EXPECTED, fun), result);

            result = guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_HAVING, fun));

            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION__IN_HAVING_EXPECTED, fun), result);
        }
        guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of("Delete.*", "Insert.*", "UpdateAll", "Modify.*", "delete.*", "insert.*", "update.*", "modify"), dialect);
        for (String fun : ALLOWED_FUNCTIONS) {
            String result = guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION, fun));

            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION_EXPECTED, fun), result);

            result = guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_WHERE, fun));

            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION__IN_WHERE_EXPECTED, fun), result);

            result = guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_HAVING, fun));

            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION__IN_HAVING_EXPECTED, fun), result);
        }
    }

    @Test
    void testDeleteAllFunctionsNotAllowed(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
        DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
        //all function not allowed
        final SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
        for (String fun : ALLOWED_FUNCTIONS) {
            assertThrows(GuardException.class, () -> guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION, fun)));
            assertThrows(GuardException.class, () -> guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_WHERE, fun)));
            assertThrows(GuardException.class, () -> guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_HAVING, fun)));
        }
        //all functions with wrong names are allowed only . all good functions are not allowed
        final SqlGuard guard1 = sqlGuardFactory.create("", SCH, databaseCatalog, List.of("Dalete.*", "Iinsert.*", "UupdateAll", "Moodify.*", "ddelete.*", "insertt.*", "uppdate.*", "moodify"), dialect);
        for (String fun : ALLOWED_FUNCTIONS) {
            assertThrows(GuardException.class, () -> guard1.guard(String.format(SQL_WITH_ALLOWED_FUNCTION, fun)));
            assertThrows(GuardException.class, () -> guard1.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_WHERE, fun)));
            assertThrows(GuardException.class, () -> guard1.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_HAVING, fun)));
        }
        //all functions with wrong names are allowed only . all good functions are not allowed
        final SqlGuard guard2 = sqlGuardFactory.create("", SCH, databaseCatalog, ALLOWED_FUNCTIONS, dialect);
        for (String fun : NOT_ALLOWED_FUNCTIONS) {
            assertThrows(GuardException.class, () -> guard2.guard(String.format(SQL_WITH_ALLOWED_FUNCTION, fun)));
            assertThrows(GuardException.class, () -> guard2.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_WHERE, fun)));
            assertThrows(GuardException.class, () -> guard2.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_HAVING, fun)));
        }
        for (String fun : ALLOWED_FUNCTIONS) {
            String result = guard2.guard(String.format(SQL_WITH_ALLOWED_FUNCTION, fun));

            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION_EXPECTED, fun), result);

            result = guard2.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_WHERE, fun));

            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION__IN_WHERE_EXPECTED, fun), result);

            result = guard2.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_HAVING, fun));

            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION__IN_HAVING_EXPECTED, fun), result);
        }
    }

    @Test
    void testFunctions(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
        DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();

        SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);

        String result = guard.guard(SQL_WITH_FUNCTION);

        assertEquals(SQL_WITH_FUNCTION_EXPECTED, result);

        assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_WITH_FUNCTION_WRONG_TABLE));

        assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_WITH_FUNCTION_WRONG_COLUMN));
    }

    private DatabaseCatalog schemaWithOneTable2Col() {
        DatabaseColumn colIdFooTable = mock(DatabaseColumn.class);
        when(colIdFooTable.getName()).thenReturn(ID);

        DatabaseColumn colNameFooTable = mock(DatabaseColumn.class);
        when(colNameFooTable.getName()).thenReturn(NAME);

        DatabaseTable fooTable = mock(DatabaseTable.class);
        when(fooTable.getName()).thenReturn(FOO);
        when(fooTable.getDatabaseColumns()).thenReturn(List.of(colIdFooTable, colNameFooTable));

        DatabaseSchema databaseSchema = mock(DatabaseSchema.class);
        when(databaseSchema.getName()).thenReturn(SCH);
        when(databaseSchema.getDatabaseTables()).thenReturn(List.of(fooTable));

        DatabaseCatalog databaseCatalog = mock(DatabaseCatalog.class);
        when(databaseCatalog.getName()).thenReturn(null);
        when(databaseCatalog.getDatabaseSchemas()).thenReturn(List.of(databaseSchema));
        return databaseCatalog;
    }

    private DatabaseCatalog schemaWithTwoTableTwoCol() {
        DatabaseColumn colIdFooTable = mock(DatabaseColumn.class);
        when(colIdFooTable.getName()).thenReturn(ID);

        DatabaseColumn colNameFooTable = mock(DatabaseColumn.class);
        when(colNameFooTable.getName()).thenReturn(NAME);

        DatabaseTable fooTable = mock(DatabaseTable.class);
        when(fooTable.getName()).thenReturn(FOO);
        when(fooTable.getDatabaseColumns()).thenReturn(List.of(colIdFooTable, colNameFooTable));

        DatabaseColumn colIdFooFactTable = mock(DatabaseColumn.class);
        when(colIdFooFactTable.getName()).thenReturn(ID);

        DatabaseColumn colValueFooFactTable = mock(DatabaseColumn.class);
        when(colValueFooFactTable.getName()).thenReturn(VALUE);

        DatabaseTable fooTableFact = mock(DatabaseTable.class);
        when(fooTableFact.getName()).thenReturn(FOO_FACT);
        when(fooTableFact.getDatabaseColumns()).thenReturn(List.of(colIdFooFactTable, colValueFooFactTable));

        DatabaseSchema databaseSchema = mock(DatabaseSchema.class);
        when(databaseSchema.getName()).thenReturn(SCH);
        when(databaseSchema.getDatabaseTables()).thenReturn(List.of(fooTable, fooTableFact));

        DatabaseCatalog databaseCatalog = mock(DatabaseCatalog.class);
        when(databaseCatalog.getName()).thenReturn(null);
        when(databaseCatalog.getDatabaseSchemas()).thenReturn(List.of(databaseSchema));
        return databaseCatalog;
    }

}
