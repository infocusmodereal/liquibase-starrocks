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
open class StarRocksLockService : StandardLockService() {

    private var isLockTableInitialized: Boolean = false
    private var ownsMutex: Boolean = false

    private fun mutexName(): String = database.escapeTableName(
        database.liquibaseCatalogName, database.liquibaseSchemaName,
        database.databaseChangeLogLockTableName + "_MUTEX"
    )

    // StarRocks UPDATE predicates are not a compare-and-swap primitive. Reserve
    // a unique catalog object before reading/updating the usual Liquibase row.
    // CREATE VIEW without IF NOT EXISTS gives exactly one creator; it needs no tablets.
    private fun reserveMutex(): Boolean {
        if (ownsMutex) return true
        try {
            getExecutor().execute(RawSqlStatement("CREATE VIEW ${mutexName()} AS SELECT 1 AS ID"))
            ownsMutex = true
            return true
        } catch (e: DatabaseException) {
            var cause: Throwable? = e
            while (cause != null) {
                if (cause is java.sql.SQLException) {
                    if (cause.errorCode == 1050) return false
                    // StarRocks 3.1 uses generic 1064 for this specific catalog collision.
                    // Do not swallow unrelated syntax or authorization failures.
                    val duplicate = "Table '${database.databaseChangeLogLockTableName}_MUTEX' already exists"
                    if (cause.errorCode == 1064 && cause.message?.endsWith(duplicate) == true) return false
                }
                cause = cause.cause
            }
            throw e
        }
    }

    private fun dropMutex() {
        getExecutor().execute(RawSqlStatement("DROP VIEW IF EXISTS ${mutexName()}"))
        ownsMutex = false
    }

    override fun init() {
        if (!getExecutor().updatesDatabase() || ownsMutex) {
            super.init()
            return
        }
        // A different owner initializes the row while holding the same reservation.
        // acquireLock retries through core's bounded waiting policy.
        if (!reserveMutex()) return
        try {
            super.init()
        } finally {
            dropMutex()
        }
    }

    override fun acquireLock(): Boolean {
        if (hasChangeLogLock()) return true
        if (!getExecutor().updatesDatabase()) return super.acquireLock()
        try {
            if (!reserveMutex()) return false
            val acquired = super.acquireLock()
            if (!acquired) dropMutex()
            return acquired
        } catch (e: Exception) {
            if (ownsMutex) {
                try { dropMutex() } catch (cleanup: Exception) { e.addSuppressed(cleanup) }
            }
            throw LockException(e)
        }
    }

    override fun getPriority(): Int = PRIORITY_DATABASE

    override fun supports(database: Database): Boolean = database is StarRocksDatabase

    // An up-to-date update may release without ever acquiring a lock.
    @Throws(LockException::class)
    override fun releaseLock() {
        if (!hasChangeLogLock()) {
            return
        }
        super.releaseLock()
        // Keep the reservation if releasing the row fails; explicit recovery is required.
        if (ownsMutex) {
            try { dropMutex() } catch (e: DatabaseException) { throw LockException(e) }
        }
    }

    // The explicit recovery command must bypass the normal ownership guard.
    @Throws(LockException::class, DatabaseException::class)
    override fun forceReleaseLock() {
        // This is an explicit administrative recovery operation. It must also
        // recover a reservation left before the lock row was initialized.
        initializeForRecovery()
        val locked = getExecutor().queryForObject(
            liquibase.statement.core.SelectFromDatabaseChangeLogLockStatement("LOCKED"),
            Boolean::class.javaObjectType
        )
        if (locked == true) super.releaseLock()
        else hasChangeLogLock = false
        dropMutex()
    }

    protected open fun initializeForRecovery() = super.init()

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
