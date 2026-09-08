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
import liquibase.datatype.DataTypeFactory
import liquibase.ext.starrocks.database.StarRocksDatabase
import liquibase.sql.Sql
import liquibase.sql.UnparsedSql
import liquibase.sqlgenerator.SqlGeneratorChain
import liquibase.sqlgenerator.core.TagDatabaseGenerator
import liquibase.statement.core.TagDatabaseStatement
import liquibase.structure.core.Column

/**
 * StarRocks Tag Database Generator
 */
class TagDatabaseGeneratorStarRocks : TagDatabaseGenerator() {

    override fun getPriority(): Int = PRIORITY_DATABASE

    override fun supports(statement: TagDatabaseStatement, database: Database): Boolean =
        database is StarRocksDatabase

    @Suppress("ACCIDENTAL_OVERRIDE")
    override fun generateSql(
        statement: TagDatabaseStatement,
        database: Database,
        sqlGeneratorChain: SqlGeneratorChain<TagDatabaseStatement>
    ): Array<Sql> {
        val tableNameEscaped = database.escapeTableName(
            database.liquibaseCatalogName,
            database.liquibaseSchemaName,
            database.databaseChangeLogTableName
        )

        val orderColumnNameEscaped = database.escapeObjectName("ORDEREXECUTED", Column::class.java)
        val dateColumnNameEscaped = database.escapeObjectName("DATEEXECUTED", Column::class.java)

        val tagEscaped = DataTypeFactory.getInstance()
            .fromObject(statement.tag, database)
            .objectToSql(statement.tag, database)

        // ORDEREXECUTED can repeat across deployments and DATETIME has second precision.
        // Select one complete identity with a stable tie-break, rather than tagging
        // every row sharing the last timestamp/order pair.
        val ordering = "$dateColumnNameEscaped DESC, $orderColumnNameEscaped DESC, ID DESC, AUTHOR DESC, FILENAME DESC"
        val identity = listOf("ID", "AUTHOR", "FILENAME").joinToString(" AND ") { column ->
            "$column = (SELECT $column FROM $tableNameEscaped ORDER BY $ordering LIMIT 1)"
        }
        val updateQuery = "UPDATE $tableNameEscaped SET TAG = $tagEscaped WHERE $identity"

        return arrayOf(UnparsedSql(updateQuery))
    }
}
