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
package liquibase.ext.starrocks.sqlgenerator

import liquibase.database.Database
import liquibase.ext.starrocks.database.StarRocksDatabase
import liquibase.sql.Sql
import liquibase.sql.UnparsedSql
import liquibase.sqlgenerator.SqlGeneratorChain
import liquibase.sqlgenerator.core.InitializeDatabaseChangeLogLockTableGenerator
import liquibase.statement.core.InitializeDatabaseChangeLogLockTableStatement

/**
 * StarRocks Initialize Database Change Log Lock Generator
 */
class InitializeDatabaseChangeLogLockStarRocks : InitializeDatabaseChangeLogLockTableGenerator() {

    override fun getPriority(): Int = PRIORITY_DATABASE

    override fun supports(statement: InitializeDatabaseChangeLogLockTableStatement, database: Database): Boolean =
        database is StarRocksDatabase

    @Suppress("ACCIDENTAL_OVERRIDE")
    override fun generateSql(
        statement: InitializeDatabaseChangeLogLockTableStatement,
        database: Database,
        sqlGeneratorChain: SqlGeneratorChain<InitializeDatabaseChangeLogLockTableStatement>
    ): Array<Sql> {
        val tableName = database.databaseChangeLogLockTableName
        val schemaName = database.defaultSchemaName

        // Clear the table first - StarRocks requires a WHERE clause for DELETE
        val clearDatabaseQuery = "DELETE FROM `$schemaName`.$tableName WHERE ID IS NOT NULL"

        // Initialize with a single row
        val initLockQuery = "INSERT INTO `$schemaName`.$tableName (ID, LOCKED) VALUES (1, false)"

        return arrayOf(
            UnparsedSql(clearDatabaseQuery),
            UnparsedSql(initLockQuery)
        )
    }
}
