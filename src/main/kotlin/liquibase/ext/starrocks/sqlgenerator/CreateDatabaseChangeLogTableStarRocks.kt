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
import liquibase.ext.starrocks.params.StarRocksTableParams
import liquibase.sql.Sql
import liquibase.sql.UnparsedSql
import liquibase.sqlgenerator.SqlGeneratorChain
import liquibase.sqlgenerator.core.CreateDatabaseChangeLogTableGenerator
import liquibase.statement.core.CreateDatabaseChangeLogTableStatement
import java.util.*

/**
 * StarRocks Create Database Change Log Table Generator
 */
class CreateDatabaseChangeLogTableStarRocks : CreateDatabaseChangeLogTableGenerator() {

    override fun getPriority(): Int = PRIORITY_DATABASE

    override fun supports(statement: CreateDatabaseChangeLogTableStatement, database: Database): Boolean =
        database is StarRocksDatabase

    @Suppress("ACCIDENTAL_OVERRIDE")
    override fun generateSql(
        statement: CreateDatabaseChangeLogTableStatement,
        database: Database,
        sqlGeneratorChain: SqlGeneratorChain<CreateDatabaseChangeLogTableStatement>
    ): Array<Sql> {
        val tableName = database.databaseChangeLogTableName
        val tableParams = StarRocksTableParams()
        tableParams.engine = "OLAP"
        tableParams.key_desc = "ID, AUTHOR, FILENAME"
        tableParams.properties = mapOf("replication_num" to "1")

        // StarRocks syntax for creating a table with a composite primary key
        // See: https://docs.starrocks.io/docs/sql-reference/sql-statements/table_bucket_part_index/CREATE_TABLE/
        val createTableQuery = """
            CREATE TABLE IF NOT EXISTS `${database.defaultSchemaName}`.${tableName} (
                ID VARCHAR(255) NOT NULL,
                AUTHOR VARCHAR(255) NOT NULL,
                FILENAME VARCHAR(255) NOT NULL,
                DATEEXECUTED DATETIME,
                ORDEREXECUTED BIGINT,
                EXECTYPE VARCHAR(10),
                MD5SUM VARCHAR(35),
                DESCRIPTION VARCHAR(255),
                COMMENTS VARCHAR(255),
                TAG VARCHAR(255),
                LIQUIBASE VARCHAR(20),
                CONTEXTS VARCHAR(255),
                LABELS VARCHAR(255),
                DEPLOYMENT_ID VARCHAR(10)
            )
            ${tableParams.generateSql()}
        """.trimIndent()

        return arrayOf(UnparsedSql(createTableQuery))
    }
}
