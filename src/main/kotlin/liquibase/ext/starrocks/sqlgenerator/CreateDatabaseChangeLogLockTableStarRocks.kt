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
import liquibase.ext.starrocks.params.StarRocksTableParams
import liquibase.sql.Sql
import liquibase.sql.UnparsedSql
import liquibase.sqlgenerator.SqlGeneratorChain
import liquibase.sqlgenerator.core.CreateDatabaseChangeLogLockTableGenerator
import liquibase.statement.core.CreateDatabaseChangeLogLockTableStatement
import java.util.*

/**
 * StarRocks Create Database Change Log Lock Table Generator
 */
class CreateDatabaseChangeLogLockTableStarRocks : CreateDatabaseChangeLogLockTableGenerator() {

    override fun getPriority(): Int = PRIORITY_DATABASE

    override fun supports(statement: CreateDatabaseChangeLogLockTableStatement, database: Database): Boolean =
        database is StarRocksDatabase

    @Suppress("ACCIDENTAL_OVERRIDE")
    override fun generateSql(
        statement: CreateDatabaseChangeLogLockTableStatement,
        database: Database,
        sqlGeneratorChain: SqlGeneratorChain<CreateDatabaseChangeLogLockTableStatement>
    ): Array<Sql> {
        val tableName = database.databaseChangeLogLockTableName
        val tableParams = StarRocksTableParams()
        tableParams.engine = "OLAP"
        tableParams.key_desc = "ID"
        tableParams.properties = mapOf("replication_num" to "1")

        // StarRocks syntax for creating a table with a primary key
        // See: https://docs.starrocks.io/docs/sql-reference/sql-statements/table_bucket_part_index/CREATE_TABLE/
        val createTableQuery = """
            CREATE TABLE IF NOT EXISTS `${database.defaultSchemaName}`.${tableName} (
                ID INT NOT NULL,
                LOCKED TINYINT NOT NULL,
                LOCKGRANTED DATETIME,
                LOCKEDBY VARCHAR(255)
            )
            ${tableParams.generateSql()}
        """.trimIndent()

        return arrayOf(UnparsedSql(createTableQuery))
    }
}
