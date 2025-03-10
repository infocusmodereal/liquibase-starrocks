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
package liquibase.ext.starrocks.sqlgenerator

import liquibase.database.Database
import liquibase.ext.starrocks.database.StarRocksDatabase
import liquibase.sql.Sql
import liquibase.sql.UnparsedSql
import liquibase.sqlgenerator.SqlGeneratorChain
import liquibase.sqlgenerator.core.LockDatabaseChangeLogGenerator
import liquibase.statement.core.LockDatabaseChangeLogStatement

/**
 * StarRocks Lock Database Change Log Generator
 */
class LockDatabaseChangeLogStarRocks : LockDatabaseChangeLogGenerator() {

    override fun getPriority(): Int = PRIORITY_DATABASE

    override fun supports(statement: LockDatabaseChangeLogStatement, database: Database): Boolean =
        database is StarRocksDatabase

    @Suppress("ACCIDENTAL_OVERRIDE")
    override fun generateSql(
        statement: LockDatabaseChangeLogStatement,
        database: Database,
        sqlGeneratorChain: SqlGeneratorChain<LockDatabaseChangeLogStatement>
    ): Array<Sql> {
        val tableName = database.databaseChangeLogLockTableName
        val schemaName = database.defaultSchemaName

        // Format the host information
        val host = "$hostname $hostDescription ($hostaddress)"

        // Use standard UPDATE syntax for StarRocks
        val lockQuery = """
            UPDATE `$schemaName`.$tableName 
            SET LOCKED = true, 
                LOCKEDBY = '$host', 
                LOCKGRANTED = ${StarRocksDatabase.CURRENT_DATE_TIME_FUNCTION} 
            WHERE ID = 1 AND LOCKED = false
        """.trimIndent()

        return arrayOf(UnparsedSql(lockQuery))
    }
}
