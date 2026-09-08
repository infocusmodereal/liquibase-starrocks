/*-
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
 */
package liquibase.ext.starrocks.lockservice

import liquibase.Scope
import liquibase.database.Database
import liquibase.exception.DatabaseException
import liquibase.exception.LiquibaseException
import liquibase.exception.LockException
import liquibase.exception.UnexpectedLiquibaseException
import liquibase.executor.Executor
import liquibase.executor.ExecutorService
import liquibase.ext.starrocks.database.StarRocksDatabase
import liquibase.lockservice.StandardLockService
import liquibase.statement.core.RawSqlStatement

/**
 * StarRocks implementation of LockService
 */
class StarRocksLockService : StandardLockService() {

    private var isLockTableInitialized: Boolean = false

    override fun getPriority(): Int = PRIORITY_DATABASE

    override fun supports(database: Database): Boolean = database is StarRocksDatabase

    // An up-to-date update may release without ever acquiring a lock.
    @Throws(LockException::class)
    override fun releaseLock() {
        if (!hasChangeLogLock()) {
            return
        }
        super.releaseLock()
    }

    // The explicit recovery command must bypass the normal ownership guard.
    @Throws(LockException::class, DatabaseException::class)
    override fun forceReleaseLock() {
        init()
        val locked = getExecutor().queryForObject(
            liquibase.statement.core.SelectFromDatabaseChangeLogLockStatement("LOCKED"),
            Boolean::class.javaObjectType
        )
        if (locked == true) super.releaseLock()
    }

    override fun isDatabaseChangeLogLockTableInitialized(tableJustCreated: Boolean): Boolean {
        if (!isLockTableInitialized) {
            try {
                val table = database.escapeTableName(
                    database.liquibaseCatalogName, database.liquibaseSchemaName,
                    database.databaseChangeLogLockTableName
                )
                val query = "SELECT COUNT(*) FROM $table WHERE ID = 1"
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

    override fun reset() {
        isLockTableInitialized = false
        super.reset()
    }

    override fun setDatabase(database: Database) {
        isLockTableInitialized = false
        super.setDatabase(database)
    }

    private fun getExecutor(): Executor {
        return Scope.getCurrentScope()
            .getSingleton(ExecutorService::class.java)
            .getExecutor("jdbc", database)
    }

}
