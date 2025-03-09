/*-
 * #%L
 * Liquibase extension for StarRocks
 * %%
 * Copyright (C) 2023 - 2024
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package liquibase.ext.starrocks.lockservice

import liquibase.Scope
import liquibase.database.Database
import liquibase.exception.DatabaseException
import liquibase.exception.LiquibaseException
import liquibase.exception.UnexpectedLiquibaseException
import liquibase.executor.Executor
import liquibase.executor.ExecutorService
import liquibase.ext.starrocks.database.StarRocksDatabase
import liquibase.lockservice.StandardLockService
import liquibase.logging.Logger
import liquibase.statement.core.RawSqlStatement

/**
 * StarRocks implementation of LockService
 */
class StarRocksLockService : StandardLockService() {

    private var isLockTableInitialized: Boolean = false

    override fun getPriority(): Int = PRIORITY_DATABASE

    override fun supports(database: Database): Boolean = database is StarRocksDatabase

    override fun isDatabaseChangeLogLockTableInitialized(tableJustCreated: Boolean): Boolean {
        if (!isLockTableInitialized) {
            try {
                val query = String.format(
                    "SELECT COUNT(*) FROM `%s`.%s",
                    database.defaultSchemaName, database.databaseChangeLogLockTableName
                )
                val nbRows = getExecutor().queryForInt(RawSqlStatement(query))
                isLockTableInitialized = nbRows > 0
            } catch (e: LiquibaseException) {
                if (getExecutor().updatesDatabase()) {
                    throw UnexpectedLiquibaseException(e)
                } else {
                    isLockTableInitialized = !tableJustCreated
                }
            }
        }
        return isLockTableInitialized
    }

    override fun hasDatabaseChangeLogLockTable(): Boolean {
        var hasTable = false
        try {
            val query = String.format(
                "SELECT ID FROM `%s`.%s LIMIT 1",
                database.defaultSchemaName, database.databaseChangeLogLockTableName
            )
            getExecutor().execute(RawSqlStatement(query))
            hasTable = true
        } catch (e: DatabaseException) {
            getLogger().info(
                String.format("No %s table available", database.databaseChangeLogLockTableName)
            )
        }
        return hasTable
    }

    private fun getExecutor(): Executor {
        return Scope.getCurrentScope()
            .getSingleton(ExecutorService::class.java)
            .getExecutor("jdbc", database)
    }

    private fun getLogger(): Logger {
        return Scope.getCurrentScope().getLog(StarRocksLockService::class.java)
    }
}