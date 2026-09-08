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

import liquibase.ChecksumVersion
import liquibase.changelog.ChangeSet
import liquibase.database.Database
import liquibase.ext.starrocks.database.StarRocksDatabase
import liquibase.sql.Sql
import liquibase.sql.UnparsedSql
import liquibase.sqlgenerator.SqlGeneratorChain
import liquibase.sqlgenerator.core.UpdateChangeSetChecksumGenerator
import liquibase.statement.core.UpdateChangeSetChecksumStatement

/**
 * StarRocks Update Change Set Checksum Generator
 */
class UpdateChangeSetChecksumStarRocks : UpdateChangeSetChecksumGenerator() {

    override fun getPriority(): Int = PRIORITY_DATABASE

    override fun supports(statement: UpdateChangeSetChecksumStatement, database: Database): Boolean =
        database is StarRocksDatabase

    @Suppress("ACCIDENTAL_OVERRIDE")
    override fun generateSql(
        statement: UpdateChangeSetChecksumStatement,
        database: Database,
        sqlGeneratorChain: SqlGeneratorChain<UpdateChangeSetChecksumStatement>
    ): Array<Sql> {
        val tableName = database.escapeTableName(
            database.liquibaseCatalogName, database.liquibaseSchemaName, database.databaseChangeLogTableName
        )
        val changeSet: ChangeSet = statement.changeSet

        val newChecksum = changeSet.generateCheckSum(ChecksumVersion.latest()).toString()
        val id = database.escapeStringForDatabase(changeSet.id)
        val author = database.escapeStringForDatabase(changeSet.author)
        val filePath = database.escapeStringForDatabase(
            changeSet.storedFilePath?.takeIf { it.isNotBlank() } ?: changeSet.filePath
        )

        // Use standard UPDATE syntax for StarRocks
        val updateQuery = """
            UPDATE $tableName
            SET MD5SUM = '$newChecksum'
            WHERE ID = '$id'
            AND AUTHOR = '$author'
            AND FILENAME = '$filePath'
        """.trimIndent()

        return arrayOf(UnparsedSql(updateQuery))
    }
}
