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
import liquibase.sqlgenerator.core.UnlockDatabaseChangeLogGenerator
import liquibase.statement.core.UnlockDatabaseChangeLogStatement

/**
 * StarRocks Unlock Database Change Log Generator
 */
class UnlockDatabaseChangelogStarRocks : UnlockDatabaseChangeLogGenerator() {

    override fun getPriority(): Int = PRIORITY_DATABASE

    override fun supports(statement: UnlockDatabaseChangeLogStatement, database: Database): Boolean =
        database is StarRocksDatabase

    @Suppress("ACCIDENTAL_OVERRIDE")
    override fun generateSql(
        statement: UnlockDatabaseChangeLogStatement,
        database: Database,
        sqlGeneratorChain: SqlGeneratorChain<UnlockDatabaseChangeLogStatement>
    ): Array<Sql> {
        val tableName = database.databaseChangeLogLockTableName
        val schemaName = database.defaultSchemaName

        // Use standard UPDATE syntax for StarRocks
        val unlockQuery = """
            UPDATE `$schemaName`.$tableName 
            SET LOCKED = false, 
                LOCKEDBY = null, 
                LOCKGRANTED = null 
            WHERE ID = 1 AND LOCKED = true
        """.trimIndent()

        return arrayOf(UnparsedSql(unlockQuery))
    }
}
