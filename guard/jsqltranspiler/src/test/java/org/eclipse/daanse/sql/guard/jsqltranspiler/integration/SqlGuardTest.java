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
import org.mockito.ArgumentMatchers;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;
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

    private static final List<String> ALLOWED_FUNCTIONS = List.of("DeleteAll", "InsertAll", "UpdateAll", "Modify",
            "deleteAll", "insertAll", "updateAll", "modify");

    private static final List<String> NOT_ALLOWED_FUNCTIONS = List.of("NotDeleteAll", "NotInsertAll", "NotUpdateAll",
            "NotModify", "notDeleteAll", "notInsertAll", "notUpdateAll", "notModify");

    // ========== Subselect tests ==========
    private static final String SQL_SUBSELECT_IN_SELECT_WRONG_COLUMN = """
            SELECT (SELECT foo1.wrongCol FROM foo) AS subCol FROM foo""";

    private static final String SQL_SUBSELECT_IN_SELECT_WRONG_TABLE = """
            SELECT (SELECT wrongTable.id FROM wrongTable) AS subCol FROM foo""";

    private static final String SQL_SUBSELECT_IN_WHERE_WRONG_COLUMN = """
            SELECT foo.id FROM foo WHERE foo.id = (SELECT foo.wrongCol FROM foo LIMIT 1)""";

    private static final String SQL_SUBSELECT_IN_WHERE_WRONG_TABLE = """
            SELECT foo.id FROM foo WHERE foo.id = (SELECT wrongTable.id FROM wrongTable LIMIT 1)""";

    private static final String SQL_EXISTS_WRONG_COLUMN = """
            SELECT foo.id FROM foo WHERE EXISTS (SELECT 1 FROM fooFact WHERE fooFact.wrongCol = foo.id)""";

    private static final String SQL_EXISTS_WRONG_TABLE = """
            SELECT foo.id FROM foo WHERE EXISTS (SELECT 1 FROM wrongTable WHERE wrongTable.id = foo.id)""";

    private static final String SQL_IN_SUBSELECT_WRONG_COLUMN = """
            SELECT foo.id FROM foo WHERE foo.id IN (SELECT fooFact.wrongCol FROM fooFact)""";

    private static final String SQL_NOT_IN_SUBSELECT_WRONG_TABLE = """
            SELECT foo.id FROM foo WHERE foo.id NOT IN (SELECT wrongTable.id FROM wrongTable)""";

    // ========== ORDER BY tests ==========
    private static final String SQL_ORDER_BY_WRONG_COLUMN = """
            SELECT foo.id, foo.name FROM foo ORDER BY foo.wrongCol""";

    private static final String SQL_ORDER_BY_WRONG_TABLE = """
            SELECT foo.id, foo.name FROM foo ORDER BY wrongTable.id""";

    private static final String SQL_ORDER_BY_EXPRESSION_WRONG_COLUMN = """
            SELECT foo.id FROM foo ORDER BY foo.wrongCol + 1""";

    // ========== CASE expression tests ==========
    private static final String SQL_CASE_WHEN_WRONG_COLUMN = """
            SELECT CASE WHEN foo.wrongCol = 1 THEN 'one' ELSE 'other' END FROM foo""";

    private static final String SQL_CASE_THEN_WRONG_COLUMN = """
            SELECT CASE WHEN foo.id = 1 THEN foo.wrongCol ELSE foo.name END FROM foo""";

    private static final String SQL_CASE_ELSE_WRONG_COLUMN = """
            SELECT CASE WHEN foo.id = 1 THEN foo.name ELSE foo.wrongCol END FROM foo""";

    private static final String SQL_CASE_SWITCH_WRONG_COLUMN = """
            SELECT CASE foo.wrongCol WHEN 1 THEN 'one' WHEN 2 THEN 'two' END FROM foo""";

    // ========== UNION/INTERSECT/EXCEPT tests ==========
    private static final String SQL_UNION_WRONG_COLUMN = """
            SELECT foo.id FROM foo UNION SELECT fooFact.wrongCol FROM fooFact""";

    private static final String SQL_UNION_WRONG_TABLE = """
            SELECT foo.id FROM foo UNION SELECT wrongTable.id FROM wrongTable""";

    private static final String SQL_UNION_ALL_WRONG_COLUMN = """
            SELECT foo.id FROM foo UNION ALL SELECT fooFact.wrongCol FROM fooFact""";

    private static final String SQL_INTERSECT_WRONG_COLUMN = """
            SELECT foo.id FROM foo INTERSECT SELECT fooFact.wrongCol FROM fooFact""";

    private static final String SQL_EXCEPT_WRONG_COLUMN = """
            SELECT foo.id FROM foo EXCEPT SELECT fooFact.wrongCol FROM fooFact""";

    private static final String SQL_MINUS_WRONG_COLUMN = """
            SELECT foo.id FROM foo MINUS SELECT fooFact.wrongCol FROM fooFact""";

    private static final String SQL_MINUS_WRONG_TABLE = """
            SELECT foo.id FROM foo MINUS SELECT wrongTable.id FROM wrongTable""";

    // ========== CTE (WITH clause) tests ==========
    private static final String SQL_CTE_WRONG_COLUMN = """
            WITH cte AS (SELECT foo.wrongCol FROM foo) SELECT * FROM cte""";

    private static final String SQL_CTE_WRONG_TABLE = """
            WITH cte AS (SELECT wrongTable.id FROM wrongTable) SELECT * FROM cte""";

    private static final String SQL_CTE_REFERENCE_WRONG_COLUMN = """
            WITH cte AS (SELECT foo.id, foo.name FROM foo) SELECT cte.wrongCol FROM cte""";

    private static final String SQL_MULTIPLE_CTE_WRONG_COLUMN = """
            WITH cte1 AS (SELECT foo.id FROM foo),
                 cte2 AS (SELECT fooFact.wrongCol FROM fooFact)
            SELECT * FROM cte1, cte2""";

    // ========== JOIN tests ==========
    private static final String SQL_LEFT_JOIN_ON_WRONG_COLUMN = """
            SELECT foo.id FROM foo LEFT JOIN fooFact ON foo.wrongCol = fooFact.id""";

    private static final String SQL_RIGHT_JOIN_ON_WRONG_COLUMN = """
            SELECT foo.id FROM foo RIGHT JOIN fooFact ON foo.id = fooFact.wrongCol""";

    private static final String SQL_CROSS_JOIN_WHERE_WRONG_COLUMN = """
            SELECT foo.id FROM foo CROSS JOIN fooFact WHERE foo.wrongCol = 1""";

    private static final String SQL_FULL_JOIN_ON_WRONG_TABLE = """
            SELECT foo.id FROM foo FULL JOIN wrongTable ON foo.id = wrongTable.id""";

    private static final String SQL_JOIN_SUBSELECT_WRONG_COLUMN = """
            SELECT foo.id FROM foo INNER JOIN (SELECT fooFact.wrongCol FROM fooFact) sub ON foo.id = sub.wrongCol""";

    // ========== Expression tests (BETWEEN, LIKE, arithmetic) ==========
    private static final String SQL_BETWEEN_WRONG_COLUMN = """
            SELECT foo.id FROM foo WHERE foo.wrongCol BETWEEN 1 AND 10""";

    private static final String SQL_LIKE_WRONG_COLUMN = """
            SELECT foo.id FROM foo WHERE foo.wrongCol LIKE '%test%'""";

    private static final String SQL_ARITHMETIC_WRONG_COLUMN = """
            SELECT foo.id + foo.wrongCol FROM foo""";

    private static final String SQL_COMPARISON_WRONG_COLUMN = """
            SELECT foo.id FROM foo WHERE foo.wrongCol > 10""";

    private static final String SQL_AND_WRONG_COLUMN = """
            SELECT foo.id FROM foo WHERE foo.id = 1 AND foo.wrongCol = 2""";

    private static final String SQL_OR_WRONG_COLUMN = """
            SELECT foo.id FROM foo WHERE foo.id = 1 OR foo.wrongCol = 2""";

    private static final String SQL_NOT_WRONG_COLUMN = """
            SELECT foo.id FROM foo WHERE NOT foo.wrongCol = 1""";

    private static final String SQL_IS_NULL_WRONG_COLUMN = """
            SELECT foo.id FROM foo WHERE foo.wrongCol IS NULL""";

    private static final String SQL_IS_NOT_NULL_WRONG_COLUMN = """
            SELECT foo.id FROM foo WHERE foo.wrongCol IS NOT NULL""";

    private static final String SQL_IN_LIST_WRONG_COLUMN = """
            SELECT foo.id FROM foo WHERE foo.wrongCol IN (1, 2, 3)""";

    private static final String SQL_COALESCE_WRONG_COLUMN = """
            SELECT COALESCE(foo.wrongCol, foo.name) FROM foo""";

    private static final String SQL_NULLIF_WRONG_COLUMN = """
            SELECT NULLIF(foo.wrongCol, foo.name) FROM foo""";

    // ========== Nested and complex queries ==========
    private static final String SQL_DEEPLY_NESTED_WRONG_COLUMN = """
            SELECT * FROM (SELECT * FROM (SELECT foo.wrongCol FROM foo) a) b""";

    private static final String SQL_CORRELATED_SUBQUERY_WRONG_COLUMN = """
            SELECT foo.id FROM foo WHERE foo.id = (SELECT MAX(fooFact.wrongCol) FROM fooFact WHERE fooFact.id = foo.id)""";

    private static final String SQL_MULTIPLE_SUBQUERIES_WRONG_COLUMN = """
            SELECT foo.id FROM foo
            WHERE foo.id IN (SELECT fooFact.id FROM fooFact)
            AND foo.name IN (SELECT fooFact.wrongCol FROM fooFact)""";

    // ========== Function parameter tests ==========
    private static final String SQL_FUNCTION_NESTED_WRONG_COLUMN = """
            SELECT UPPER(TRIM(foo.wrongCol)) FROM foo""";

    private static final String SQL_FUNCTION_MULTIPLE_PARAMS_WRONG_COLUMN = """
            SELECT CONCAT(foo.name, foo.wrongCol) FROM foo""";

    private static final String SQL_AGGREGATE_IN_SUBSELECT_WRONG_COLUMN = """
            SELECT foo.id FROM foo WHERE foo.id > (SELECT AVG(fooFact.wrongCol) FROM fooFact)""";

    // ========== DISTINCT and ALL tests ==========
    private static final String SQL_DISTINCT_WRONG_COLUMN = """
            SELECT DISTINCT foo.wrongCol FROM foo""";

    private static final String SQL_ALL_WRONG_COLUMN = """
            SELECT ALL foo.wrongCol FROM foo""";

    private static final String SQL_COUNT_DISTINCT_WRONG_COLUMN = """
            SELECT COUNT(DISTINCT foo.wrongCol) FROM foo""";

    // ========== Valid queries for positive tests ==========
    private static final String SQL_VALID_SUBSELECT = """
            SELECT foo.id FROM foo WHERE foo.id IN (SELECT fooFact.id FROM fooFact)""";

    private static final String SQL_VALID_CTE = """
            WITH cte AS (SELECT foo.id, foo.name FROM foo) SELECT cte.id FROM cte""";

    private static final String SQL_VALID_UNION = """
            SELECT foo.id FROM foo UNION SELECT fooFact.id FROM fooFact""";

    private static final String SQL_VALID_LEFT_JOIN = """
            SELECT foo.id, fooFact.value FROM foo LEFT JOIN fooFact ON foo.id = fooFact.id""";

    private static final String SQL_VALID_CASE = """
            SELECT CASE WHEN foo.id = 1 THEN foo.name ELSE 'unknown' END FROM foo""";

    private static final String SQL_VALID_ORDER_BY = """
            SELECT foo.id, foo.name FROM foo ORDER BY foo.id DESC, foo.name ASC""";

    private static Dialect dialect;

    @BeforeAll
    public static void setUp() {
        dialect = mock(Dialect.class);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] arguments = invocation.getArguments();

                if (arguments != null && arguments.length >= 2 && arguments[0] != null) {
                    StringBuilder sb = (StringBuilder) arguments[0];
                    // Handle varargs - remaining arguments are the identifier parts
                    boolean first = true;
                    for (int i = 1; i < arguments.length; i++) {
                        if (arguments[i] != null) {
                            if (!first) {
                                sb.append(".");
                            }
                            sb.append((String) arguments[i]);
                            first = false;
                        }
                    }
                }
                return null;
            }
        }).when(dialect).quoteIdentifier(any(StringBuilder.class), ArgumentMatchers.<String>any());

        // Mock quoteNumericLiteral to just append the value
        doAnswer(invocation -> {
            StringBuilder sb = (StringBuilder) invocation.getArgument(0);
            String value = (String) invocation.getArgument(1);
            sb.append(value);
            return null;
        }).when(dialect).quoteNumericLiteral(any(StringBuilder.class), any(String.class));

        // Mock quoteStringLiteral to append quoted value
        doAnswer(invocation -> {
            StringBuilder sb = (StringBuilder) invocation.getArgument(0);
            String value = (String) invocation.getArgument(1);
            sb.append("'").append(value).append("'");
            return null;
        }).when(dialect).quoteStringLiteral(any(StringBuilder.class), any(String.class));

        // Mock quoteBooleanLiteral
        doAnswer(invocation -> {
            StringBuilder sb = (StringBuilder) invocation.getArgument(0);
            String value = (String) invocation.getArgument(1);
            sb.append(value);
            return null;
        }).when(dialect).quoteBooleanLiteral(any(StringBuilder.class), any(String.class));
    }

    @Nested
    @DisplayName("Basic SQL Guard Tests")
    class BasicTests {

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
            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> guard.guard(SIMPLE_SQL_WITH_WRONG_TABLE));
            assertEquals(TABLE_FOO1_DOES_NOT_EXIST_IN_THE_GIVEN_SCHEMA_SCH, thrown.getMessage());
        }

        @Test
        void testGroup(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            String result = guard.guard(SQL_WITH_GROUP);
            assertEquals(SQL_WITH_GROUP_EXPECTED, result);
        }

        @ParameterizedTest(name = "aggregation {0}")
        @ValueSource(strings = { "sum", "count", "distinctcount", "avg" })
        void testAggregationBasic(String agg, @InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, AGGREGATIONS, dialect);
            String result = guard.guard(String.format(SQL_WITH_AGG, agg));
            assertEquals(String.format(SQL_WITH_AGG_EXPECTED, agg), result);
        }

        @ParameterizedTest(name = "aggregation {0} with wrong table")
        @ValueSource(strings = { "sum", "count", "distinctcount", "avg" })
        void testAggregationWrongTable(String agg, @InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, AGGREGATIONS, dialect);
            assertThrows(UnresolvableObjectsGuardException.class,
                    () -> guard.guard(String.format(SQL_WITH_AGG_WITH_WRONG_TABLE, agg)));
        }

        @ParameterizedTest(name = "aggregation {0} in HAVING clause")
        @ValueSource(strings = { "sum", "count", "distinctcount", "avg" })
        void testAggregationHaving(String agg, @InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, AGGREGATIONS, dialect);
            String result = guard.guard(String.format(SQL_WITH_HAVING, agg, agg));
            assertEquals(String.format(SQL_WITH_HAVING_EXPECTED, agg, agg), result);
        }

        @ParameterizedTest(name = "aggregation {0} in HAVING with wrong table")
        @ValueSource(strings = { "sum", "count", "distinctcount", "avg" })
        void testAggregationHavingWrongTable(String agg, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, AGGREGATIONS, dialect);
            assertThrows(UnresolvableObjectsGuardException.class,
                    () -> guard.guard(String.format(SQL_WITH_HAVING_WRONG_TABLE, agg, agg)));
        }

        @ParameterizedTest(name = "aggregation {0} in HAVING with simple condition")
        @ValueSource(strings = { "sum", "count", "distinctcount", "avg" })
        void testAggregationHavingSimple(String agg, @InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, AGGREGATIONS, dialect);
            String result = guard.guard(String.format(SQL_WITH_HAVING1, agg));
            assertEquals(String.format(SQL_WITH_HAVING1_EXPECTED, agg), result);
        }

        @ParameterizedTest(name = "aggregation {0} in HAVING with wrong table variant")
        @ValueSource(strings = { "sum", "count", "distinctcount", "avg" })
        void testAggregationHavingWrongTableVariant(String agg, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, AGGREGATIONS, dialect);
            assertThrows(UnresolvableObjectsGuardException.class,
                    () -> guard.guard(String.format(SQL_WITH_HAVING_WRONG_TABLE1, agg)));
        }

        @ParameterizedTest(name = "aggregation {0} in HAVING with wrong column")
        @ValueSource(strings = { "sum", "count", "distinctcount", "avg" })
        void testAggregationHavingWrongColumn(String agg, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, AGGREGATIONS, dialect);
            assertThrows(UnresolvableObjectsGuardException.class,
                    () -> guard.guard(String.format(SQL_WITH_HAVING_WRONG_COLUMN, agg)));
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
    }

    @Nested
    @DisplayName("Function Whitelist Tests")
    class FunctionWhitelistTests {

        static Stream<String> allowedFunctions() {
            return ALLOWED_FUNCTIONS.stream();
        }

        static Stream<String> notAllowedFunctions() {
            return NOT_ALLOWED_FUNCTIONS.stream();
        }

        @ParameterizedTest(name = "function {0} in SELECT with exact whitelist")
        @MethodSource("allowedFunctions")
        void testAllowedFunctionInSelectExact(String fun, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, ALLOWED_FUNCTIONS, dialect);
            String result = guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION, fun));
            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION_EXPECTED, fun), result);
        }

        @ParameterizedTest(name = "function {0} in WHERE with exact whitelist")
        @MethodSource("allowedFunctions")
        void testAllowedFunctionInWhereExact(String fun, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, ALLOWED_FUNCTIONS, dialect);
            String result = guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_WHERE, fun));
            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION__IN_WHERE_EXPECTED, fun), result);
        }

        @ParameterizedTest(name = "function {0} in HAVING with exact whitelist")
        @MethodSource("allowedFunctions")
        void testAllowedFunctionInHavingExact(String fun, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, ALLOWED_FUNCTIONS, dialect);
            String result = guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_HAVING, fun));
            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION__IN_HAVING_EXPECTED, fun), result);
        }

        @ParameterizedTest(name = "function {0} in SELECT with wildcard whitelist")
        @MethodSource("allowedFunctions")
        void testAllowedFunctionInSelectWildcard(String fun, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(".*"), dialect);
            String result = guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION, fun));
            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION_EXPECTED, fun), result);
        }

        @ParameterizedTest(name = "function {0} in WHERE with wildcard whitelist")
        @MethodSource("allowedFunctions")
        void testAllowedFunctionInWhereWildcard(String fun, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(".*"), dialect);
            String result = guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_WHERE, fun));
            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION__IN_WHERE_EXPECTED, fun), result);
        }

        @ParameterizedTest(name = "function {0} in HAVING with wildcard whitelist")
        @MethodSource("allowedFunctions")
        void testAllowedFunctionInHavingWildcard(String fun, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(".*"), dialect);
            String result = guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_HAVING, fun));
            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION__IN_HAVING_EXPECTED, fun), result);
        }

        @ParameterizedTest(name = "function {0} in SELECT with regex whitelist")
        @MethodSource("allowedFunctions")
        void testAllowedFunctionInSelectRegex(String fun, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of("Delete.*", "Insert.*",
                    "UpdateAll", "Modify.*", "delete.*", "insert.*", "update.*", "modify"), dialect);
            String result = guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION, fun));
            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION_EXPECTED, fun), result);
        }

        @ParameterizedTest(name = "function {0} in WHERE with regex whitelist")
        @MethodSource("allowedFunctions")
        void testAllowedFunctionInWhereRegex(String fun, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of("Delete.*", "Insert.*",
                    "UpdateAll", "Modify.*", "delete.*", "insert.*", "update.*", "modify"), dialect);
            String result = guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_WHERE, fun));
            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION__IN_WHERE_EXPECTED, fun), result);
        }

        @ParameterizedTest(name = "function {0} in HAVING with regex whitelist")
        @MethodSource("allowedFunctions")
        void testAllowedFunctionInHavingRegex(String fun, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of("Delete.*", "Insert.*",
                    "UpdateAll", "Modify.*", "delete.*", "insert.*", "update.*", "modify"), dialect);
            String result = guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_HAVING, fun));
            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION__IN_HAVING_EXPECTED, fun), result);
        }

        @ParameterizedTest(name = "function {0} blocked with empty whitelist in SELECT")
        @MethodSource("allowedFunctions")
        void testFunctionBlockedEmptyWhitelistSelect(String fun, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(GuardException.class, () -> guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION, fun)));
        }

        @ParameterizedTest(name = "function {0} blocked with empty whitelist in WHERE")
        @MethodSource("allowedFunctions")
        void testFunctionBlockedEmptyWhitelistWhere(String fun, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(GuardException.class,
                    () -> guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_WHERE, fun)));
        }

        @ParameterizedTest(name = "function {0} blocked with empty whitelist in HAVING")
        @MethodSource("allowedFunctions")
        void testFunctionBlockedEmptyWhitelistHaving(String fun, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(GuardException.class,
                    () -> guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_HAVING, fun)));
        }

        @ParameterizedTest(name = "function {0} blocked with wrong patterns in SELECT")
        @MethodSource("allowedFunctions")
        void testFunctionBlockedWrongPatternsSelect(String fun, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of("Dalete.*", "Iinsert.*",
                    "UupdateAll", "Moodify.*", "ddelete.*", "insertt.*", "uppdate.*", "moodify"), dialect);
            assertThrows(GuardException.class, () -> guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION, fun)));
        }

        @ParameterizedTest(name = "function {0} blocked with wrong patterns in WHERE")
        @MethodSource("allowedFunctions")
        void testFunctionBlockedWrongPatternsWhere(String fun, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of("Dalete.*", "Iinsert.*",
                    "UupdateAll", "Moodify.*", "ddelete.*", "insertt.*", "uppdate.*", "moodify"), dialect);
            assertThrows(GuardException.class,
                    () -> guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_WHERE, fun)));
        }

        @ParameterizedTest(name = "function {0} blocked with wrong patterns in HAVING")
        @MethodSource("allowedFunctions")
        void testFunctionBlockedWrongPatternsHaving(String fun, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of("Dalete.*", "Iinsert.*",
                    "UupdateAll", "Moodify.*", "ddelete.*", "insertt.*", "uppdate.*", "moodify"), dialect);
            assertThrows(GuardException.class,
                    () -> guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_HAVING, fun)));
        }

        @ParameterizedTest(name = "not-allowed function {0} blocked in SELECT")
        @MethodSource("notAllowedFunctions")
        void testNotAllowedFunctionBlockedSelect(String fun, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, ALLOWED_FUNCTIONS, dialect);
            assertThrows(GuardException.class, () -> guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION, fun)));
        }

        @ParameterizedTest(name = "not-allowed function {0} blocked in WHERE")
        @MethodSource("notAllowedFunctions")
        void testNotAllowedFunctionBlockedWhere(String fun, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, ALLOWED_FUNCTIONS, dialect);
            assertThrows(GuardException.class,
                    () -> guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_WHERE, fun)));
        }

        @ParameterizedTest(name = "not-allowed function {0} blocked in HAVING")
        @MethodSource("notAllowedFunctions")
        void testNotAllowedFunctionBlockedHaving(String fun, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, ALLOWED_FUNCTIONS, dialect);
            assertThrows(GuardException.class,
                    () -> guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_HAVING, fun)));
        }

        @ParameterizedTest(name = "allowed function {0} passes with specific whitelist in SELECT")
        @MethodSource("allowedFunctions")
        void testAllowedFunctionPassesWithWhitelistSelect(String fun, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, ALLOWED_FUNCTIONS, dialect);
            String result = guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION, fun));
            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION_EXPECTED, fun), result);
        }

        @ParameterizedTest(name = "allowed function {0} passes with specific whitelist in WHERE")
        @MethodSource("allowedFunctions")
        void testAllowedFunctionPassesWithWhitelistWhere(String fun, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, ALLOWED_FUNCTIONS, dialect);
            String result = guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_WHERE, fun));
            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION__IN_WHERE_EXPECTED, fun), result);
        }

        @ParameterizedTest(name = "allowed function {0} passes with specific whitelist in HAVING")
        @MethodSource("allowedFunctions")
        void testAllowedFunctionPassesWithWhitelistHaving(String fun, @InjectService SqlGuardFactory sqlGuardFactory)
                throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, ALLOWED_FUNCTIONS, dialect);
            String result = guard.guard(String.format(SQL_WITH_ALLOWED_FUNCTION_IN_HAVING, fun));
            assertEquals(String.format(SQL_WITH_ALLOWED_FUNCTION__IN_HAVING_EXPECTED, fun), result);
        }
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

    @Nested
    @DisplayName("Subselect Security Tests")
    class SubselectSecurityTests {

        @Test
        void testSubselectInSelectWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class,
                    () -> guard.guard(SQL_SUBSELECT_IN_SELECT_WRONG_COLUMN));
        }

        @Test
        void testSubselectInSelectWrongTable(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class,
                    () -> guard.guard(SQL_SUBSELECT_IN_SELECT_WRONG_TABLE));
        }

        @Test
        void testSubselectInWhereWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class,
                    () -> guard.guard(SQL_SUBSELECT_IN_WHERE_WRONG_COLUMN));
        }

        @Test
        void testSubselectInWhereWrongTable(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class,
                    () -> guard.guard(SQL_SUBSELECT_IN_WHERE_WRONG_TABLE));
        }

        @Test
        @Disabled("jsqltranspiler bug: NPE when resolving EXISTS with wrong columns - https://github.com/JSQLParser/JSqlParser/issues/2291")
        void testExistsWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_EXISTS_WRONG_COLUMN));
        }

        @Test
        @Disabled("jsqltranspiler bug: NPE when resolving EXISTS - https://github.com/JSQLParser/JSqlParser/issues/2291")
        void testExistsWrongTable(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_EXISTS_WRONG_TABLE));
        }

        @Test
        void testInSubselectWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_IN_SUBSELECT_WRONG_COLUMN));
        }

        @Test
        void testNotInSubselectWrongTable(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_NOT_IN_SUBSELECT_WRONG_TABLE));
        }
    }

    @Nested
    @DisplayName("ORDER BY Security Tests")
    class OrderBySecurityTests {

        @Test
        void testOrderByWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_ORDER_BY_WRONG_COLUMN));
        }

        @Test
        void testOrderByWrongTable(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_ORDER_BY_WRONG_TABLE));
        }

        @Test
        void testOrderByExpressionWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class,
                    () -> guard.guard(SQL_ORDER_BY_EXPRESSION_WRONG_COLUMN));
        }
    }

    @Nested
    @DisplayName("CASE Expression Security Tests")
    class CaseExpressionSecurityTests {

        @Test
        void testCaseWhenWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_CASE_WHEN_WRONG_COLUMN));
        }

        @Test
        void testCaseThenWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_CASE_THEN_WRONG_COLUMN));
        }

        @Test
        void testCaseElseWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_CASE_ELSE_WRONG_COLUMN));
        }

        @Test
        void testCaseSwitchWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_CASE_SWITCH_WRONG_COLUMN));
        }
    }

    @Nested
    @DisplayName("Set Operations Security Tests (UNION/INTERSECT/EXCEPT/MINUS)")
    class SetOperationsSecurityTests {

        @Test
        @Disabled("jsqltranspiler limitation: wrong columns in UNION second query not detected")
        void testUnionWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrowsOrShowResult(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_UNION_WRONG_COLUMN),
                    SQL_UNION_WRONG_COLUMN);
        }

        @Test
        @Disabled("jsqltranspiler limitation: wrong tables in UNION second query not detected")
        void testUnionWrongTable(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrowsOrShowResult(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_UNION_WRONG_TABLE),
                    SQL_UNION_WRONG_TABLE);
        }

        @Test
        @Disabled("jsqltranspiler limitation: wrong columns in UNION ALL second query not detected")
        void testUnionAllWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrowsOrShowResult(UnresolvableObjectsGuardException.class,
                    () -> guard.guard(SQL_UNION_ALL_WRONG_COLUMN), SQL_UNION_ALL_WRONG_COLUMN);
        }

        @Test
        @Disabled("jsqltranspiler limitation: wrong columns in INTERSECT second query not detected")
        void testIntersectWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrowsOrShowResult(UnresolvableObjectsGuardException.class,
                    () -> guard.guard(SQL_INTERSECT_WRONG_COLUMN), SQL_INTERSECT_WRONG_COLUMN);
        }

        @Test
        @Disabled("jsqltranspiler limitation: wrong columns in EXCEPT second query not detected")
        void testExceptWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrowsOrShowResult(UnresolvableObjectsGuardException.class,
                    () -> guard.guard(SQL_EXCEPT_WRONG_COLUMN), SQL_EXCEPT_WRONG_COLUMN);
        }

        @Test
        @Disabled("jsqltranspiler limitation: wrong columns in MINUS second query not detected")
        void testMinusWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrowsOrShowResult(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_MINUS_WRONG_COLUMN),
                    SQL_MINUS_WRONG_COLUMN);
        }

        @Test
        @Disabled("jsqltranspiler limitation: wrong tables in MINUS second query not detected")
        void testMinusWrongTable(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrowsOrShowResult(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_MINUS_WRONG_TABLE),
                    SQL_MINUS_WRONG_TABLE);
        }
    }

    private static <T extends Throwable> void assertThrowsOrShowResult(Class<T> expectedType,
            ThrowingSupplier<String> supplier, String inputSql) {
        try {
            String result = supplier.get();
            throw new AssertionError("Expected " + expectedType.getSimpleName() + " but got result.\n" + "Input:  "
                    + inputSql.replace("\n", " ") + "\n" + "Output: " + result);
        } catch (Throwable t) {
            if (expectedType.isInstance(t)) {
                return; // expected exception thrown
            }
            if (t instanceof AssertionError) {
                throw (AssertionError) t;
            }
            throw new AssertionError("Expected " + expectedType.getSimpleName() + " but got "
                    + t.getClass().getSimpleName() + ": " + t.getMessage(), t);
        }
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Throwable;
    }

    @Nested
    @DisplayName("CTE (WITH Clause) Security Tests")
    class CteSecurityTests {

        @Test
        void testCteWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_CTE_WRONG_COLUMN));
        }

        @Test
        void testCteWrongTable(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_CTE_WRONG_TABLE));
        }

        @Test
        void testCteReferenceWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_CTE_REFERENCE_WRONG_COLUMN));
        }

        @Test
        void testMultipleCteWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_MULTIPLE_CTE_WRONG_COLUMN));
        }
    }

    @Nested
    @DisplayName("JOIN Security Tests")
    class JoinSecurityTests {

        @Test
        void testLeftJoinOnWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_LEFT_JOIN_ON_WRONG_COLUMN));
        }

        @Test
        void testRightJoinOnWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_RIGHT_JOIN_ON_WRONG_COLUMN));
        }

        @Test
        void testCrossJoinWhereWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_CROSS_JOIN_WHERE_WRONG_COLUMN));
        }

        @Test
        void testFullJoinOnWrongTable(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_FULL_JOIN_ON_WRONG_TABLE));
        }

        @Test
        void testJoinSubselectWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_JOIN_SUBSELECT_WRONG_COLUMN));
        }
    }

    @Nested
    @DisplayName("Expression Security Tests")
    class ExpressionSecurityTests {

        @Test
        void testBetweenWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_BETWEEN_WRONG_COLUMN));
        }

        @Test
        void testLikeWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_LIKE_WRONG_COLUMN));
        }

        @Test
        void testArithmeticWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_ARITHMETIC_WRONG_COLUMN));
        }

        @Test
        void testComparisonWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_COMPARISON_WRONG_COLUMN));
        }

        @Test
        void testAndWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_AND_WRONG_COLUMN));
        }

        @Test
        void testOrWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_OR_WRONG_COLUMN));
        }

        @Test
        void testNotWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_NOT_WRONG_COLUMN));
        }

        @Test
        void testIsNullWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_IS_NULL_WRONG_COLUMN));
        }

        @Test
        void testIsNotNullWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_IS_NOT_NULL_WRONG_COLUMN));
        }

        @Test
        void testInListWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_IN_LIST_WRONG_COLUMN));
        }

        @Test
        void testCoalesceWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_COALESCE_WRONG_COLUMN));
        }

        @Test
        void testNullifWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_NULLIF_WRONG_COLUMN));
        }
    }

    @Nested
    @DisplayName("Nested and Complex Query Security Tests")
    class NestedQuerySecurityTests {

        @Test
        void testDeeplyNestedWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_DEEPLY_NESTED_WRONG_COLUMN));
        }

        @Test
        void testCorrelatedSubqueryWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of("MAX", "AVG"), dialect);
            assertThrows(UnresolvableObjectsGuardException.class,
                    () -> guard.guard(SQL_CORRELATED_SUBQUERY_WRONG_COLUMN));
        }

        @Test
        void testMultipleSubqueriesWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class,
                    () -> guard.guard(SQL_MULTIPLE_SUBQUERIES_WRONG_COLUMN));
        }
    }

    @Nested
    @DisplayName("Function Parameter Security Tests")
    class FunctionParameterSecurityTests {

        @Test
        void testFunctionNestedWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_FUNCTION_NESTED_WRONG_COLUMN));
        }

        @Test
        void testFunctionMultipleParamsWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class,
                    () -> guard.guard(SQL_FUNCTION_MULTIPLE_PARAMS_WRONG_COLUMN));
        }

        @Test
        void testAggregateInSubselectWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of("AVG"), dialect);
            assertThrows(UnresolvableObjectsGuardException.class,
                    () -> guard.guard(SQL_AGGREGATE_IN_SUBSELECT_WRONG_COLUMN));
        }
    }

    @Nested
    @DisplayName("DISTINCT and ALL Security Tests")
    class DistinctAllSecurityTests {

        @Test
        void testDistinctWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_DISTINCT_WRONG_COLUMN));
        }

        @Test
        void testAllWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_ALL_WRONG_COLUMN));
        }

        @Test
        void testCountDistinctWrongColumn(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of("count"), dialect);
            assertThrows(UnresolvableObjectsGuardException.class, () -> guard.guard(SQL_COUNT_DISTINCT_WRONG_COLUMN));
        }
    }

    @Nested
    @DisplayName("Positive Tests - Valid Queries Should Pass")
    class PositiveTests {

        @Test
        void testValidSubselect(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            String result = guard.guard(SQL_VALID_SUBSELECT);
            assertNotNull(result);
        }

        @Test
        void testValidCte(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            String result = guard.guard(SQL_VALID_CTE);
            assertNotNull(result);
        }

        @Test
        void testValidUnion(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            String result = guard.guard(SQL_VALID_UNION);
            assertNotNull(result);
        }

        @Test
        void testValidLeftJoin(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithTwoTableTwoCol();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            String result = guard.guard(SQL_VALID_LEFT_JOIN);
            assertNotNull(result);
        }

        @Test
        void testValidCase(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            String result = guard.guard(SQL_VALID_CASE);
            assertNotNull(result);
        }

        @Test
        void testValidOrderBy(@InjectService SqlGuardFactory sqlGuardFactory) throws Exception {
            DatabaseCatalog databaseCatalog = schemaWithOneTable2Col();
            SqlGuard guard = sqlGuardFactory.create("", SCH, databaseCatalog, List.of(), dialect);
            String result = guard.guard(SQL_VALID_ORDER_BY);
            assertNotNull(result);
        }
    }

    private static void assertNotNull(Object obj) {
        if (obj == null) {
            throw new AssertionError("Expected non-null value");
        }
    }

}
