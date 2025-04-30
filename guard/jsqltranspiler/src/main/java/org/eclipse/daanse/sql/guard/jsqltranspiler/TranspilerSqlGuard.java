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

package org.eclipse.daanse.sql.guard.jsqltranspiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.sql.guard.api.SqlGuard;
import org.eclipse.daanse.sql.guard.api.elements.DatabaseCatalog;
import org.eclipse.daanse.sql.guard.api.elements.DatabaseSchema;
import org.eclipse.daanse.sql.guard.api.elements.DatabaseTable;
import org.eclipse.daanse.sql.guard.api.exception.EmptyStatementGuardException;
import org.eclipse.daanse.sql.guard.api.exception.GuardException;
import org.eclipse.daanse.sql.guard.api.exception.UnallowedStatementTypeGuardException;
import org.eclipse.daanse.sql.guard.api.exception.UnparsableStatementGuardException;
import org.eclipse.daanse.sql.guard.api.exception.UnresolvableObjectsGuardException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.starlake.transpiler.CatalogNotFoundException;
import ai.starlake.transpiler.ColumnNotFoundException;
import ai.starlake.transpiler.JSQLColumResolver;
import ai.starlake.transpiler.JSQLResolver;
import ai.starlake.transpiler.SchemaNotFoundException;
import ai.starlake.transpiler.TableNotDeclaredException;
import ai.starlake.transpiler.TableNotFoundException;
import ai.starlake.transpiler.schema.JdbcColumn;
import ai.starlake.transpiler.schema.JdbcMetaData;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.deparser.StatementDeParser;

public class TranspilerSqlGuard implements SqlGuard {

    private static final String DELETE_IS_NOT_PERMITTED = "DELETE is not permitted.";
    private static final String UPDATE_IS_NOT_PERMITTED = "UPDATE is not permitted.";
    private static final String INSERT_IS_NOT_PERMITTED = "INSERT is not permitted.";
    private static final String NOTHING_WAS_SELECTED = "Nothing was selected.";
    private static final String QUERY_HAS_DISAllOWED_FUNCTIONS = "Query has disallowed functions.";
    private static final Logger LOGGER = LoggerFactory.getLogger(TranspilerSqlGuard.class);
    private JdbcMetaData jdbcMetaDataToCopy;
    private List<String> whitelistFunctionsPatterns = new ArrayList<String>();
    private Dialect dialect;

    public TranspilerSqlGuard(String currentCatalogName, String currentSchemaName, DatabaseCatalog databaseCatalog, List<String> whitelistFunctionsPatterns, Dialect dialect) {
        this.whitelistFunctionsPatterns = whitelistFunctionsPatterns;
        jdbcMetaDataToCopy = calculateMetaData(currentCatalogName, currentSchemaName, databaseCatalog);
        this.dialect = dialect;

    }

    @Override
    public String guard(String sqlStr) throws GuardException {

        LOGGER.atInfo().log("guard incoming: %s", sqlStr);
        StringBuilder builder = new StringBuilder();
        StatementDeParser deParser = new StatementDeParser(builder);

        JSQLResolver resolver = new JSQLResolver(jdbcMetaDataToCopy);

        // this does not really work, when there are comments
        // @todo: apply a Regex for SQL Comments
        if (sqlStr == null || sqlStr.trim().isEmpty()) {
            throw new EmptyStatementGuardException();
        }

        try {
            Statement st = CCJSqlParserUtil.parse(sqlStr);

            // we can test for SELECT, though in practise it won't protect us from harmful statements
            if (st instanceof Select) {
                resolver.resolve(st);

                // select columns should not be empty
                final List<JdbcColumn> selectColumns = resolver.getSelectColumns();
                if (selectColumns.isEmpty()) {
                    LOGGER.atInfo().log(NOTHING_WAS_SELECTED);
                    throw new GuardException(NOTHING_WAS_SELECTED);
                }

                // any delete columns must be empty
                final List<JdbcColumn> deleteColumns = resolver.getDeleteColumns();
                if (!deleteColumns.isEmpty()) {
                    LOGGER.atInfo().log(DELETE_IS_NOT_PERMITTED);
                    throw new GuardException(DELETE_IS_NOT_PERMITTED);
                }

                // any update columns must be empty
                final List<JdbcColumn> updateColumns = resolver.getUpdateColumns();
                if (!updateColumns.isEmpty()) {
                    LOGGER.atInfo().log(UPDATE_IS_NOT_PERMITTED);
                    throw new GuardException(UPDATE_IS_NOT_PERMITTED);
                }

                // any insert columns must be empty
                final List<JdbcColumn> insertColumns = resolver.getInsertColumns();
                if (!insertColumns.isEmpty()) {
                    LOGGER.atInfo().log(INSERT_IS_NOT_PERMITTED);
                    throw new GuardException(INSERT_IS_NOT_PERMITTED);
                }

                // check functions
                final List<Expression> functions = resolver.getFunctions();
                final Set<String> functionNames = resolver.getFlatFunctionNames();

                List<String> allowedFunctions = new ArrayList<>();
                List<String> disallowedFunctions = new ArrayList<>();

                for (String function : functionNames) {
                    boolean isAllowed = false;
                    for (String pattern : whitelistFunctionsPatterns) {
                        if (Pattern.matches(pattern, function)) {
                            isAllowed = true;
                            break;
                        }
                    }
                    if (isAllowed) {
                        allowedFunctions.add(function);
                    } else {
                        disallowedFunctions.add(function);
                    }
                }
                if (!disallowedFunctions.isEmpty()) {
                    LOGGER.atInfo().log(QUERY_HAS_DISAllOWED_FUNCTIONS);
                    throw new GuardException(QUERY_HAS_DISAllOWED_FUNCTIONS);
                }

                // we can finally resolve for the actually returned columns
                JSQLColumResolver columResolver = new DaanseJSQLColumResolver(jdbcMetaDataToCopy, dialect);
                columResolver.setCommentFlag(false);
                columResolver.setErrorMode(JdbcMetaData.ErrorMode.STRICT);

                String rewritten = columResolver.getResolvedStatementText(sqlStr);
                // TODO: get it as object and access to AST that we do not have to reparse
                Statement stResolveds = CCJSqlParserUtil.parse(rewritten);

                // TODO: check the count of functions and deepnes and size of statement. we should be able check on
                // this variables if we allow statement or if it calls to much.

                deParser.visit((Select) st);// or rewritten

                LOGGER.atInfo().log("guard outgoin: %s", rewritten);

                return rewritten;

            } else {
                throw new UnallowedStatementTypeGuardException(
                    st.getClass().getSimpleName().toUpperCase() + " is not permitted.");
            }
        } catch (JSQLParserException ex) {
            throw new UnparsableStatementGuardException();
        } catch (CatalogNotFoundException | ColumnNotFoundException | SchemaNotFoundException
            | TableNotDeclaredException | TableNotFoundException ex) {
            throw new UnresolvableObjectsGuardException(ex.getMessage());
        }

    }

    private static JdbcMetaData calculateMetaData(String currentCatalogName, String currentSchemaName,
            DatabaseCatalog databaseCatalog) {
        JdbcMetaData jdbcMetaData = new JdbcMetaData(currentCatalogName, currentSchemaName);

        for (DatabaseSchema schema : databaseCatalog.getDatabaseSchemas()) {
            for (DatabaseTable table : schema.getDatabaseTables()) {

                List<JdbcColumn> jdbcColumns = table.getDatabaseColumns().parallelStream()
                    .map(c -> new JdbcColumn(c.getName())).toList();
                jdbcMetaData.addTable(schema.getName(), table.getName(), jdbcColumns);
            }
        }
        return jdbcMetaData;
    }

}
